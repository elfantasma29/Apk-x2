#include <dynarmic/interface/A32/a32.h>
#include <dynarmic/interface/A32/config.h>
#include <dynarmic/interface/A32/coprocessor.h>
#include <dynarmic/interface/exclusive_monitor.h>

#include <atomic>
#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <string>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/time.h>
#include <sys/uio.h>
#include <sys/utsname.h>
#include <sys/random.h>
#include <sys/resource.h>
#include <poll.h>
#include <thread>
#include <time.h>
#include <unistd.h>
#include <vector>
#include <elf.h>
#include <linux/futex.h>
#include <mutex>
#include <map>
#include <algorithm>

using u8 = uint8_t; using u16 = uint16_t; using u32 = uint32_t; using u64 = uint64_t; using s32 = int32_t; using s64 = int64_t;

static u8* arena;
static std::string sysroot;
static std::string exe_path;
static bool trace = false;
static Dynarmic::ExclusiveMonitor* monitor;
static std::atomic<int> next_tid{1000};
static std::atomic<int> next_proc{0};

#define G(a) (arena + (u32)(a))
static inline u32 PG(u32 x) { return x & ~0xFFFu; }
static inline u32 PU(u32 x) { return (x + 0xFFFu) & ~0xFFFu; }

static u32 mmap_next = 0x50000000;
static u32 brk_start = 0, brk_cur = 0;
static const u32 BRK_LIMIT = 0x30000000;
static std::mutex mm_lock;

static int hprot(int p) {
    int r = 0;
    if (p & 1) r |= PROT_READ;
    if (p & 2) r |= PROT_READ | PROT_WRITE;
    if (p & 4) r |= PROT_READ;
    return r;
}

static void map_anon(u32 addr, u32 len, int prot) {
    void* r = mmap(G(addr), len, hprot(prot) ? hprot(prot) : PROT_NONE, MAP_FIXED | MAP_PRIVATE | MAP_ANONYMOUS | MAP_NORESERVE, -1, 0);
    if (r == MAP_FAILED) { perror("map_anon"); abort(); }
}

static std::string translate_path(const char* p) {
    if (p[0] == '/' && !sysroot.empty()) {
        std::string s = sysroot + p;
        if (access(s.c_str(), F_OK) == 0) return s;
    }
    return p;
}

struct Cp15 : Dynarmic::A32::Coprocessor {
    u32 tpidruro = 0, tpidrurw = 0;
    static u64 nop(void*, u32, u32) { return 0; }
    std::optional<Callback> CompileInternalOperation(bool, unsigned, Dynarmic::A32::CoprocReg, Dynarmic::A32::CoprocReg, Dynarmic::A32::CoprocReg, unsigned) override { return std::nullopt; }
    CallbackOrAccessOneWord CompileSendOneWord(bool, unsigned opc1, Dynarmic::A32::CoprocReg CRn, Dynarmic::A32::CoprocReg CRm, unsigned opc2) override {
        using R = Dynarmic::A32::CoprocReg;
        if (opc1 == 0 && CRn == R::C13 && CRm == R::C0 && opc2 == 2) return &tpidrurw;
        if (CRn == R::C7) return Callback{&nop, std::nullopt};
        return std::monostate{};
    }
    CallbackOrAccessTwoWords CompileSendTwoWords(bool, unsigned, Dynarmic::A32::CoprocReg) override { return std::monostate{}; }
    CallbackOrAccessOneWord CompileGetOneWord(bool, unsigned opc1, Dynarmic::A32::CoprocReg CRn, Dynarmic::A32::CoprocReg CRm, unsigned opc2) override {
        using R = Dynarmic::A32::CoprocReg;
        if (opc1 == 0 && CRn == R::C13 && CRm == R::C0 && opc2 == 3) return &tpidruro;
        if (opc1 == 0 && CRn == R::C13 && CRm == R::C0 && opc2 == 2) return &tpidrurw;
        return std::monostate{};
    }
    CallbackOrAccessTwoWords CompileGetTwoWords(bool, unsigned, Dynarmic::A32::CoprocReg) override { return std::monostate{}; }
    std::optional<Callback> CompileLoadWords(bool, bool, Dynarmic::A32::CoprocReg, std::optional<u8>) override { return std::nullopt; }
    std::optional<Callback> CompileStoreWords(bool, bool, Dynarmic::A32::CoprocReg, std::optional<u8>) override { return std::nullopt; }
};

struct Hilo;
static void run_thread(Hilo* h);
struct HotFn { const char* lib; u32 off; void (*fn)(u32*, u32); };
extern "C" const HotFn hot_table[];
thread_local u32 HOT_F = 0;
extern "C" { uint8_t* HOT_MEM; void hot_call_guest(u32* R, u32 addr); }
struct Hook { void (*fn)(u32*, u32); u32 base; };
static std::vector<Hook> hooks;
static std::mutex hook_lock;
static std::map<std::string, u32> hot_bases;
static bool hot_on = false;
static size_t hot_max = (size_t)-1;
static thread_local Hilo* cur_h = nullptr;
static const u32 SENT = 0xFFFF0F00;

struct Hilo : Dynarmic::A32::UserCallbacks {
    std::unique_ptr<Dynarmic::A32::Jit> jit;
    std::shared_ptr<Cp15> cp15;
    int tid = 0;
    u32 clear_tid = 0;
    bool salir = false;
    Hilo* nested = nullptr;
    bool nested_done = false;
    std::atomic<bool> en_sys{false};
    std::atomic<bool> en_hook{false};
    int salida = 0;

    u8 MemoryRead8(u32 a) override { return *G(a); }
    u16 MemoryRead16(u32 a) override { u16 v; memcpy(&v, G(a), 2); return v; }
    u32 MemoryRead32(u32 a) override { u32 v; memcpy(&v, G(a), 4); return v; }
    u64 MemoryRead64(u32 a) override { u64 v; memcpy(&v, G(a), 8); return v; }
    void MemoryWrite8(u32 a, u8 v) override { *G(a) = v; }
    void MemoryWrite16(u32 a, u16 v) override { memcpy(G(a), &v, 2); }
    void MemoryWrite32(u32 a, u32 v) override { memcpy(G(a), &v, 4); }
    void MemoryWrite64(u32 a, u64 v) override { memcpy(G(a), &v, 8); }
    bool MemoryWriteExclusive8(u32 a, u8 v, u8 e) override { return __atomic_compare_exchange_n((u8*)G(a), &e, v, false, __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST); }
    bool MemoryWriteExclusive16(u32 a, u16 v, u16 e) override { return __atomic_compare_exchange_n((u16*)G(a), &e, v, false, __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST); }
    bool MemoryWriteExclusive32(u32 a, u32 v, u32 e) override { return __atomic_compare_exchange_n((u32*)G(a), &e, v, false, __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST); }
    bool MemoryWriteExclusive64(u32 a, u64 v, u64 e) override { return __atomic_compare_exchange_n((u64*)G(a), &e, v, false, __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST); }
    void InterpreterFallback(u32 pc, size_t n) override { fprintf(stderr, "dynrun: InterpreterFallback pc=%08x n=%zu\n", pc, n); abort(); }
    void ExceptionRaised(u32 pc, Dynarmic::A32::Exception e) override {
        if (e == Dynarmic::A32::Exception::PreloadData || e == Dynarmic::A32::Exception::PreloadDataWithIntentToWrite || e == Dynarmic::A32::Exception::PreloadInstruction || e == Dynarmic::A32::Exception::Yield || e == Dynarmic::A32::Exception::SendEvent || e == Dynarmic::A32::Exception::SendEventLocal) return;
        fprintf(stderr, "dynrun: excepcion %d en pc=%08x\n", (int)e, pc);
        for (int i = 0; i < 16; i++) fprintf(stderr, " r%d=%08x", i, jit->Regs()[i]);
        fprintf(stderr, "\n");
        abort();
    }
    void AddTicks(u64) override {}
    u64 GetTicksRemaining() override { return 1ull << 60; }
    void CallSVC(u32 swi) override;
};

static void hot_patch_all();
static std::vector<Hilo*> todos;
static std::mutex todos_lock;
struct Region { u32 addr, len; std::string path; u64 off; };
static std::vector<Region> regions;
static std::mutex reg_lock;
static std::map<std::string, std::map<u32, u64>> perfil;
static std::atomic<bool> perfil_on{false};
static std::atomic<u64> muestras{0};
static void registrar_region(u32 addr, u32 len, int fd, u64 off) {
    char l[64], b[512]; snprintf(l, sizeof l, "/proc/self/fd/%d", fd);
    ssize_t n = readlink(l, b, sizeof b - 1);
    if (n <= 0) return;
    b[n] = 0;
    std::lock_guard<std::mutex> lk(reg_lock);
    regions.push_back({addr, len, b, off});
}
static void volcar_perfil() {
    const char* f = getenv("DYN_PROF");
    if (!f || !perfil_on.exchange(false)) return;
    usleep(5000);
    FILE* o = fopen(f, "w");
    if (!o) return;
    std::lock_guard<std::mutex> lk(reg_lock);
    fprintf(o, "TOTAL %llu\n", (unsigned long long)muestras.load());
    for (auto& m : perfil) for (auto& e : m.second) fprintf(o, "%s %x %llu\n", m.first.c_str(), e.first, (unsigned long long)e.second);
    fclose(o);
}
static void muestrear() {
    std::vector<Hilo*> c;
    { std::lock_guard<std::mutex> lk(todos_lock); c = todos; }
    for (Hilo* h : c) {
        if (h->salir) continue;
        if (h->en_hook) { std::lock_guard<std::mutex> lk(reg_lock); perfil["NATIVO"][0]++; muestras++; continue; }
        if (h->en_sys) continue;
        u32 pc = h->jit->Regs()[15];
        std::string p = "?"; u32 off = pc;
        {
            std::lock_guard<std::mutex> lk(reg_lock);
            for (auto it = regions.rbegin(); it != regions.rend(); ++it)
                if (pc >= it->addr && pc < it->addr + it->len) {
                    u32 base = it->addr - (u32)it->off;
                    p = it->path; off = pc - base; break;
                }
            perfil[p][off & ~3u]++;
        }
        muestras++;
    }
}

static Hilo* nuevo_hilo() {
    Hilo* h = new Hilo();
    h->cp15 = std::make_shared<Cp15>();
    Dynarmic::A32::UserConfig c;
    c.callbacks = h;
    c.processor_id = next_proc++ % 16;
    c.global_monitor = monitor;
    c.arch_version = Dynarmic::A32::ArchVersion::v7;
    c.fastmem_pointer = (uintptr_t)arena;
    c.enable_cycle_counting = false;
    c.define_unpredictable_behaviour = true;
    c.code_cache_size = 64u * 1024 * 1024;
    c.coprocessors[15] = h->cp15;
    h->jit = std::make_unique<Dynarmic::A32::Jit>(c);
    h->tid = next_tid++;
    { std::lock_guard<std::mutex> lk(todos_lock); todos.push_back(h); }
    return h;
}

static void run_thread(Hilo* h) {
    while (!h->salir) {
        auto hr = h->jit->Run();
        (void)hr;
    }
    if (h->clear_tid) {
        u32 z = 0;
        memcpy(G(h->clear_tid), &z, 4);
        syscall(SYS_futex, G(h->clear_tid), FUTEX_WAKE, 0x7fffffff, nullptr, nullptr, 0);
    }
}

static int xlat_open_flags(int f) {
    int r = f & 3;
    if (f & 0x40) r |= O_CREAT;
    if (f & 0x80) r |= O_EXCL;
    if (f & 0x100) r |= O_NOCTTY;
    if (f & 0x200) r |= O_TRUNC;
    if (f & 0x400) r |= O_APPEND;
    if (f & 0x800) r |= O_NONBLOCK;
    if (f & 0x1000) r |= O_DSYNC;
    if (f & 0x4000) r |= O_DIRECTORY;
    if (f & 0x8000) r |= O_NOFOLLOW;
    if (f & 0x10000) r |= O_DIRECT;
    if (f & 0x40000) r |= O_NOATIME;
    if (f & 0x80000) r |= O_CLOEXEC;
    return r;
}

static s32 neg(long r) { return r < 0 ? -errno : (s32)r; }

static void ts32_to_host(u32 p, struct timespec* t) {
    s32 v[2]; memcpy(v, G(p), 8); t->tv_sec = v[0]; t->tv_nsec = v[1];
}

static int do_clone(Hilo* p, u32 flags, u32 stack, u32 ptid, u32 tls, u32 ctid) {
    Hilo* c = nuevo_hilo();
    for (int i = 0; i < 16; i++) c->jit->Regs()[i] = p->jit->Regs()[i];
    c->jit->ExtRegs() = p->jit->ExtRegs();
    c->jit->SetCpsr(p->jit->Cpsr());
    c->jit->SetFpscr(p->jit->Fpscr());
    c->jit->Regs()[0] = 0;
    if (stack) c->jit->Regs()[13] = stack;
    if (flags & 0x80000) c->cp15->tpidruro = tls;
    else c->cp15->tpidruro = p->cp15->tpidruro;
    c->cp15->tpidrurw = p->cp15->tpidrurw;
    if (flags & 0x100000) memcpy(G(ptid), &c->tid, 4);
    if (flags & 0x1000000) memcpy(G(ctid), &c->tid, 4);
    if (flags & 0x200000) c->clear_tid = ctid;
    std::thread([c]() { run_thread(c); }).detach();
    return c->tid;
}

void Hilo::CallSVC(u32 swi) {
    struct G_ { std::atomic<bool>& f; G_(std::atomic<bool>& x) : f(x) { f = true; } ~G_() { f = false; } } g_(en_sys);
    auto& R = jit->Regs();
    if (swi >= 0x700000u) {
        if (swi == 0x7fffffu) { nested_done = true; jit->HaltExecution(); return; }
        Hook hk = hooks[swi - 0x700000u];
        u32 ret = R[14];
        Hilo* prev = cur_h;
        cur_h = this;
        HOT_F = jit->Cpsr() & 0xf0000000u;
        en_hook = true;
        hk.fn(R.data(), hk.base);
        en_hook = false;
        jit->SetCpsr((jit->Cpsr() & 0x0fffffffu) | (HOT_F & 0xf0000000u));
        cur_h = prev;
        R[15] = ret & ~1u;
        return;
    }
    u32 n = R[7], a0 = R[0], a1 = R[1], a2 = R[2], a3 = R[3], a4 = R[4], a5 = R[5];
    long r = -ENOSYS;
    switch (n) {
    case 1: volcar_perfil(); salir = true; salida = a0; jit->HaltExecution(); return;
    case 248: volcar_perfil(); fflush(NULL); _exit(a0);
    case 3: r = neg(read(a0, G(a1), a2)); break;
    case 4: r = neg(write(a0, G(a1), a2)); break;
    case 6: r = neg(close(a0)); break;
    case 41: r = neg(dup(a0)); break;
    case 63: r = neg(dup2(a0, a1)); break;
    case 358: r = neg(dup3(a0, a1, a2)); break;
    case 20: r = getpid(); break;
    case 224: r = tid; break;
    case 199: case 200: case 201: case 202: r = 0; break;
    case 268: r = 0; break;
    case 33: r = neg(access(translate_path((char*)G(a0)).c_str(), a1)); break;
    case 334: r = neg(faccessat((int)a0, translate_path((char*)G(a1)).c_str(), a2, 0)); break;
    case 322: {
        std::string p = translate_path((char*)G(a1));
        r = neg(openat((int)a0, p.c_str(), xlat_open_flags(a2), a3));
        break;
    }
    case 5: {
        std::string p = translate_path((char*)G(a0));
        r = neg(open(p.c_str(), xlat_open_flags(a1), a2));
        break;
    }
    case 397: {
        std::string p = translate_path((char*)G(a1));
        r = neg(syscall(SYS_statx, (int)a0, p.c_str(), a2, a3, G(a4)));
        break;
    }
    case 140: {
        s64 off = ((s64)(u32)a1 << 32) | a2;
        s64 res = lseek(a0, off, a4);
        if (res < 0) r = -errno; else { memcpy(G(a3), &res, 8); r = 0; }
        break;
    }
    case 19: r = neg(lseek(a0, (s32)a1, a2)); break;
    case 180: { s64 off = ((s64)(u32)a5 << 32) | a4; r = neg(pread(a0, G(a1), a2, off)); break; }
    case 217: r = neg(syscall(SYS_getdents64, a0, G(a1), a2)); break;
    case 192: {
        std::lock_guard<std::mutex> lk(mm_lock);
        u32 len = PU(a1);
        u32 addr = a0;
        int fl = a3;
        if (!(fl & 0x10)) {
            addr = mmap_next;
            mmap_next += len + 0x1000;
        }
        int hf = MAP_FIXED | MAP_NORESERVE | ((fl & 1) ? MAP_SHARED : MAP_PRIVATE);
        void* m;
        if (fl & 0x20) {
            m = mmap(G(addr), len, hprot(a2) ? hprot(a2) : PROT_NONE, hf | MAP_ANONYMOUS, -1, 0);
        } else {
            m = mmap(G(addr), len, hprot(a2) ? hprot(a2) : PROT_NONE, hf, (int)a4, (off_t)a5 * 4096);
        }
        r = m == MAP_FAILED ? -errno : (long)(s32)addr;
        if (m != MAP_FAILED && !(fl & 0x20)) {
            registrar_region(addr, len, (int)a4, (u64)a5 * 4096);
            if (hot_on) {
                char l[64], b[512]; snprintf(l, sizeof l, "/proc/self/fd/%d", (int)a4);
                ssize_t nn = readlink(l, b, sizeof b - 1);
                if (nn > 0) {
                    b[nn] = 0; std::string pth = b; size_t sl = pth.rfind('/'); std::string bn = sl == std::string::npos ? pth : pth.substr(sl + 1);
                    bool conocida = false; for (const HotFn* hf = hot_table; hf->lib; hf++) if (bn == hf->lib) { conocida = true; break; }
                    if (a5 == 0 && conocida) { std::lock_guard<std::mutex> lk(hook_lock); hot_bases[bn] = addr; }
                }
            }
        }
        if (hot_on) hot_patch_all();
        break;
    }
    case 91: {
        std::lock_guard<std::mutex> lk(mm_lock);
        void* m = mmap(G(a0), PU(a1), PROT_NONE, MAP_FIXED | MAP_PRIVATE | MAP_ANONYMOUS | MAP_NORESERVE, -1, 0);
        r = m == MAP_FAILED ? -errno : 0;
        break;
    }
    case 125: r = neg(mprotect(G(a0), PU(a1), hprot(a2) ? hprot(a2) : PROT_NONE)); break;
    case 220: r = 0; break;
    case 163: {
        std::lock_guard<std::mutex> lk(mm_lock);
        u32 oldlen = PU(a1), newlen = PU(a2);
        if (newlen <= oldlen) { r = (s32)a0; break; }
        u32 addr = mmap_next; mmap_next += newlen + 0x1000;
        void* m = mmap(G(addr), newlen, PROT_READ | PROT_WRITE, MAP_FIXED | MAP_PRIVATE | MAP_ANONYMOUS | MAP_NORESERVE, -1, 0);
        if (m == MAP_FAILED) { r = -ENOMEM; break; }
        memcpy(G(addr), G(a0), oldlen);
        mmap(G(a0), oldlen, PROT_NONE, MAP_FIXED | MAP_PRIVATE | MAP_ANONYMOUS | MAP_NORESERVE, -1, 0);
        r = (s32)addr;
        break;
    }
    case 45: {
        std::lock_guard<std::mutex> lk(mm_lock);
        if (a0 == 0 || a0 < brk_start || a0 > BRK_LIMIT) { r = (s32)brk_cur; break; }
        u32 nu = PU(a0), cu = PU(brk_cur);
        if (nu > cu) { mprotect(G(cu), nu - cu, PROT_READ | PROT_WRITE); }
        brk_cur = a0;
        r = (s32)brk_cur;
        break;
    }
    case 174: case 175: case 186: case 338: r = 0; break;
    case 256: clear_tid = a0; r = tid; break;
    case 240: case 422: {
        int op = a1 & 0x7f;
        long res;
        if (op == FUTEX_WAIT || op == FUTEX_WAIT_BITSET) {
            struct timespec ts, *pts = nullptr;
            if (a3) {
                if (n == 240) ts32_to_host(a3, &ts); else memcpy(&ts, G(a3), sizeof ts);
                pts = &ts;
            }
            res = syscall(SYS_futex, G(a0), a1, a2, pts, G(a4), a5);
        } else {
            res = syscall(SYS_futex, G(a0), a1, a2, (void*)(uintptr_t)a3, G(a4), a5);
        }
        r = res < 0 ? -errno : res;
        break;
    }
    case 120: r = do_clone(this, a0, a1, a2, a3, a4); break;
    case 263: {
        struct timespec ts; int rc = clock_gettime(a0, &ts);
        if (rc) r = -errno; else { s32 v[2] = {(s32)ts.tv_sec, (s32)ts.tv_nsec}; memcpy(G(a1), v, 8); r = 0; }
        break;
    }
    case 403: r = neg(syscall(SYS_clock_gettime, a0, G(a1))); break;
    case 407: r = neg(syscall(SYS_clock_nanosleep, a0, a1, G(a2), a3 ? G(a3) : nullptr)); break;
    case 162: { struct timespec ts; ts32_to_host(a0, &ts); r = neg(nanosleep(&ts, nullptr)); break; }
    case 158: sched_yield(); r = 0; break;
    case 78: { struct timeval tv; gettimeofday(&tv, nullptr); s32 v[2] = {(s32)tv.tv_sec, (s32)tv.tv_usec}; if (a0) memcpy(G(a0), v, 8); r = 0; break; }
    case 384: r = neg(getrandom(G(a0), a1, a2)); break;
    case 191: { u32 v[2] = {0xFFFFFFFFu, 0xFFFFFFFFu}; if (a0 == 3) { v[0] = 8 << 20; } memcpy(G(a1), v, 8); r = 0; break; }
    case 369: { u64 v[2] = {~0ull, ~0ull}; if (a1 == 3) v[0] = 8 << 20; if (a3) memcpy(G(a3), v, 16); r = 0; break; }
    case 122: {
        char* b = (char*)G(a0); memset(b, 0, 65 * 6);
        strcpy(b, "Linux"); strcpy(b + 65, "localhost"); strcpy(b + 130, "5.10.0"); strcpy(b + 195, "#1"); strcpy(b + 260, "armv7l");
        r = 0; break;
    }
    case 183: { char tmp[4096]; if (getcwd(tmp, sizeof tmp)) { size_t l = strlen(tmp) + 1; if (l <= a1) { memcpy(G(a0), tmp, l); r = l; } else r = -ERANGE; } else r = -errno; break; }
    case 332: {
        std::string p = translate_path((char*)G(a1));
        if (p == "/proc/self/exe") { size_t l = exe_path.size(); if (l > a3) l = a3; memcpy(G(a2), exe_path.data(), l); r = l; }
        else r = neg(readlinkat((int)a0, p.c_str(), (char*)G(a2), a3));
        break;
    }
    case 54: if (a1 == 0x5401) r = -ENOTTY; else r = neg(syscall(SYS_ioctl, a0, a1, G(a2))); break;
    case 55: case 221: r = neg(syscall(SYS_fcntl, a0, a1, a2)); break;
    case 359: { int fds[2]; int rc = pipe2(fds, xlat_open_flags(a1)); if (rc) r = -errno; else { memcpy(G(a0), fds, 8); r = 0; } break; }
    case 42: { int fds[2]; int rc = pipe(fds); if (rc) r = -errno; else { memcpy(G(a0), fds, 8); r = 0; } break; }
    case 168: r = neg(poll((struct pollfd*)G(a0), a1, (s32)a2)); break;
    case 242: { memset(G(a2), 0, a1); G(a2)[0] = 0xff; r = a1 < 8 ? a1 : 8; break; }
    case 146: {
        std::vector<struct iovec> v(a2);
        for (u32 i = 0; i < a2; i++) { u32 e[2]; memcpy(e, G(a1 + 8 * i), 8); v[i].iov_base = G(e[0]); v[i].iov_len = e[1]; }
        r = neg(writev(a0, v.data(), a2)); break;
    }
    case 145: {
        std::vector<struct iovec> v(a2);
        for (u32 i = 0; i < a2; i++) { u32 e[2]; memcpy(e, G(a1 + 8 * i), 8); v[i].iov_base = G(e[0]); v[i].iov_len = e[1]; }
        r = neg(readv(a0, v.data(), a2)); break;
    }
    case 0x0f0005: cp15->tpidruro = a0; r = 0; break;
    case 0x0f0002: jit->InvalidateCacheRange(a0, a1 - a0); r = 0; break;
    case 172: r = 0; break;
    case 398: r = -ENOSYS; break;
    default: {
        static std::atomic<unsigned long long> visto[2048];
        if (n < 2048 && !visto[n].exchange(1)) fprintf(stderr, "dynrun: llamada al sistema sin implementar %u (pc=%08x)\n", n, R[15]);
        r = -ENOSYS;
    }
    }
    R[0] = (u32)(s32)r;
}

static void hot_patch_all() {
    if (!hot_on) return;
    std::lock_guard<std::mutex> lk(hook_lock);
    size_t idx = 0;
    for (const HotFn* f = hot_table; f->lib; f++, idx++) {
        if (idx >= hot_max) break;
        auto it = hot_bases.find(f->lib);
        if (it == hot_bases.end()) continue;
        u32 addr = it->second + f->off;
        u32 word = 0xef000000u | (0x700000u + (u32)idx);
        u32 cur; memcpy(&cur, G(addr), 4);
        if (cur == word) continue;
        if (hooks.size() <= idx) hooks.resize(idx + 1, Hook{nullptr, 0});
        hooks[idx] = Hook{f->fn, it->second};
        u32 pg = PG(addr);
        mprotect(G(pg), 4096, PROT_READ | PROT_WRITE);
        memcpy(G(addr), &word, 4);
        mprotect(G(pg), 4096, PROT_READ);
    }
}

extern "C" void hot_call_guest(u32* R, u32 addr) {
    Hilo* h = cur_h;
    if (!h->nested) { h->nested = nuevo_hilo(); }
    Hilo* n = h->nested;
    auto& NR = n->jit->Regs();
    for (int i = 0; i < 15; i++) NR[i] = R[i];
    NR[14] = SENT;
    NR[15] = addr & ~1u;
    n->jit->SetCpsr(0x10 | ((addr & 1) ? 0x20 : 0) | (HOT_F & 0xf0000000u));
    n->cp15->tpidruro = h->cp15->tpidruro;
    n->cp15->tpidrurw = h->cp15->tpidrurw;
    n->tid = h->tid;
    n->nested_done = false;
    cur_h = n;
    bool was = h->en_hook; h->en_hook = false;
    while (!n->nested_done) n->jit->Run();
    h->en_hook = was;
    cur_h = h;
    for (int i = 0; i < 15; i++) R[i] = NR[i];
    HOT_F = n->jit->Cpsr() & 0xf0000000u;
}

struct Ctx { std::vector<std::string> env; std::vector<std::string> args; };

static u32 cargar(const std::string& path, u32 base, Elf32_Ehdr* eh, u32* phdr_addr, u32* end) {
    FILE* f = fopen(path.c_str(), "rb");
    if (!f) { perror(path.c_str()); exit(1); }
    fread(eh, sizeof *eh, 1, f);
    std::vector<Elf32_Phdr> ph(eh->e_phnum);
    fseek(f, eh->e_phoff, SEEK_SET);
    fread(ph.data(), sizeof(Elf32_Phdr), eh->e_phnum, f);
    int fd = fileno(f);
    u32 maxend = 0;
    bool phfound = false;
    u32 firstv = ~0u, firstoff = 0;
    for (auto& p : ph) {
        if (p.p_type == PT_PHDR) { *phdr_addr = base + p.p_vaddr; phfound = true; }
        if (p.p_type != PT_LOAD) continue;
        if (p.p_vaddr < firstv) { firstv = p.p_vaddr; firstoff = p.p_offset; }
        u32 s = PG(base + p.p_vaddr), e = PU(base + p.p_vaddr + p.p_memsz);
        mprotect(G(s), e - s, PROT_READ | PROT_WRITE);
        if (p.p_offset == 0 || true) {}
        if (pread(fd, G(base + p.p_vaddr), p.p_filesz, p.p_offset) != (ssize_t)p.p_filesz) { perror("pread"); exit(1); }
        int pr = PROT_READ | ((p.p_flags & PF_W) ? PROT_WRITE : 0);
        mprotect(G(s), e - s, pr);
        if (e > maxend) maxend = e;
        registrar_region(base + p.p_vaddr, p.p_memsz, fd, p.p_offset);
    }
    if (!phfound) *phdr_addr = base + firstv - firstoff + eh->e_phoff;
    *end = maxend;
    fclose(f);
    return base + eh->e_entry;
}

static int difftest(int argc, char** argv) {
    std::string path = argv[2], name = argv[3];
    int ntr = argc > 4 ? atoi(argv[4]) : 100;
    size_t tam = (1ull << 32) + (1ull << 20);
    arena = (u8*)mmap(nullptr, tam, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS | MAP_NORESERVE, -1, 0);
    HOT_MEM = arena;
    monitor = new Dynarmic::ExclusiveMonitor(16);
    mmap(G(0xFFFF0000), 0x10000, PROT_READ | PROT_WRITE, MAP_FIXED | MAP_PRIVATE | MAP_ANONYMOUS | MAP_NORESERVE, -1, 0);
    u32 sv = 0xef7fffffu; memcpy(G(SENT), &sv, 4);
    Elf32_Ehdr eh{}; u32 ph = 0, end = 0;
    const u32 B = 0x10000000;
    cargar(path, B, &eh, &ph, &end);
    mprotect(G(B), PU(end) - B, PROT_READ | PROT_WRITE | PROT_EXEC);
    const u32 STK = 0x30000000, STKN = 0x10000, SCR = 0x20000000, SCRN = 0x10000;
    Hilo* h = nuevo_hilo();
    cur_h = h;
    std::vector<u8> stk0(STKN), scr0(SCRN);
    srand(12345);
    auto rnd32 = []() { u32 r = ((u32)rand() << 16) ^ (u32)rand(); return r; };
    int total_bad = 0;
    for (const HotFn* f = hot_table; f->lib; f++) {
        if (name != f->lib) continue;
        if (getenv("DT_ONLY")) { char b[32]; snprintf(b, sizeof b, "%x", f->off); std::string o = std::string(",") + getenv("DT_ONLY") + ","; if (o.find(std::string(",") + b + ",") == std::string::npos) continue; }
        int bad = 0, done = 0; fprintf(stderr, "probando 0x%x\n", f->off);
        for (int t = 0; t < ntr; t++) {
            for (auto& b : scr0) b = rand();
            for (auto& b : stk0) b = rand();
            u32 in[16];
            int tipo = t % 3;
            for (int i = 0; i < 13; i++) in[i] = rnd32();
            for (int i = 0; i < 4; i++) {
                if (tipo == 1) in[i] = SCR + ((rnd32() % (SCRN - 256)) & ~3u);
                else if (tipo == 2) in[i] = (t & 4) ? (rnd32() % 200) : (rnd32() & 0x7fffffff);
            }
            in[13] = STK + STKN - 0x200; in[14] = SENT; in[15] = 0;
            u32 outE[16], outN[16];
            std::vector<u8> stkE(STKN), scrE(SCRN), stkN(STKN), scrN(SCRN);
            memcpy(G(STK), stk0.data(), STKN); memcpy(G(SCR), scr0.data(), SCRN);
            auto& R = h->jit->Regs();
            for (int i = 0; i < 16; i++) R[i] = in[i];
            R[15] = B + f->off;
            h->jit->SetCpsr(0x10);
            h->nested_done = false;
            std::thread wd([&]() { for (int i = 0; i < 300 && !h->nested_done; i++) usleep(1000); if (!h->nested_done) h->jit->HaltExecution(); });
            while (!h->nested_done) { auto hr = h->jit->Run(); if (!h->nested_done && R[15] != SENT) { break; } }
            h->nested_done = true;
            wd.join();
            bool ok_e = R[15] == SENT || true;
            for (int i = 0; i < 16; i++) outE[i] = R[i];
            memcpy(stkE.data(), G(STK), STKN); memcpy(scrE.data(), G(SCR), SCRN);
            memcpy(G(STK), stk0.data(), STKN); memcpy(G(SCR), scr0.data(), SCRN);
            u32 RN[16]; for (int i = 0; i < 16; i++) RN[i] = in[i];
            f->fn(RN, B);
            for (int i = 0; i < 16; i++) outN[i] = RN[i];
            memcpy(stkN.data(), G(STK), STKN); memcpy(scrN.data(), G(SCR), SCRN);
            done++;
            bool diff = false; std::string why;
            for (int i = 0; i < 14; i++) if (outE[i] != outN[i]) { diff = true; char b[64]; snprintf(b, sizeof b, " r%d(emu=%08x nat=%08x)", i, outE[i], outN[i]); why += b; }
            if (stkE != stkN) { diff = true; why += " pila"; }
            if (scrE != scrN) { diff = true; why += " memoria"; }
            if (diff) {
                bad++;
                if (bad <= 2) { fprintf(stderr, "  DIFF fn 0x%x tipo %d:%s  in:", f->off, tipo, why.c_str()); for (int i = 0; i < 4; i++) fprintf(stderr, " %08x", in[i]); fprintf(stderr, "\n"); }
            }
        }
        total_bad += bad ? 1 : 0;
        printf("fn 0x%x: %d/%d pruebas distintas\n", f->off, bad, done);
        fflush(stdout);
    }
    printf("funciones con diferencias: %d\n", total_bad);
    return 0;
}

int main(int argc, char** argv) {
    if (argc > 3 && std::string(argv[1]) == "--difftest") return difftest(argc, argv);
    std::vector<std::string> env;
    int i = 1;
    for (; i < argc; i++) {
        std::string a = argv[i];
        if (a == "-L") sysroot = argv[++i];
        else if (a == "-E") env.push_back(argv[++i]);
        else break;
    }
    if (i >= argc) { fprintf(stderr, "uso: dynrun -L sysroot [-E K=V] programa args\n"); return 1; }
    trace = getenv("DYN_TRACE") != nullptr;
    exe_path = argv[i];
    std::vector<std::string> args(argv + i, argv + argc);

    size_t tam = (1ull << 32) + (1ull << 20);
    arena = (u8*)mmap(nullptr, tam, PROT_NONE, MAP_PRIVATE | MAP_ANONYMOUS | MAP_NORESERVE, -1, 0);
    if (arena == MAP_FAILED) { perror("arena"); return 1; }
    HOT_MEM = arena;
    hot_on = !getenv("DYN_HOT") || atoi(getenv("DYN_HOT")) > 0;
    if (getenv("DYN_HOT_MAX")) hot_max = (size_t)atoi(getenv("DYN_HOT_MAX"));
    monitor = new Dynarmic::ExclusiveMonitor(16);

    Elf32_Ehdr eh{}, ih{};
    u32 phdr = 0, ephdr = 0, end = 0, iend = 0;
    FILE* f0 = fopen(exe_path.c_str(), "rb");
    if (!f0) { perror("programa"); return 1; }
    fread(&eh, sizeof eh, 1, f0);
    std::vector<Elf32_Phdr> ph(eh.e_phnum);
    fseek(f0, eh.e_phoff, SEEK_SET);
    fread(ph.data(), sizeof(Elf32_Phdr), eh.e_phnum, f0);
    std::string interp;
    for (auto& p : ph) if (p.p_type == PT_INTERP) {
        std::vector<char> b(p.p_filesz + 1, 0);
        fseek(f0, p.p_offset, SEEK_SET);
        fread(b.data(), 1, p.p_filesz, f0);
        interp = b.data();
    }
    fclose(f0);
    u32 ebase = eh.e_type == ET_DYN ? 0x00400000 : 0;
    u32 entry = cargar(exe_path, ebase, &eh, &phdr, &end);
    u32 ibase = 0x40000000, ientry = entry;
    if (!interp.empty()) {
        std::string ip = sysroot + interp;
        ientry = cargar(ip, ibase, &ih, &ephdr, &iend);
    }
    brk_start = brk_cur = PU(end) + 0x100000;
    u32 stack_top = 0xBF000000, stack_size = 8u << 20;
    map_anon(stack_top - stack_size, stack_size, 3);
    {
        void* m = mmap(G(0xFFFF0000), 0x10000, PROT_READ | PROT_WRITE, MAP_FIXED | MAP_PRIVATE | MAP_ANONYMOUS | MAP_NORESERVE, -1, 0);
        if (m != MAP_FAILED) { u32 sv = 0xef7fffffu; memcpy(G(SENT), &sv, 4); u32 c[2] = {0xee1d0f70, 0xe12fff1e}; memcpy(G(0xFFFF0FE0), c, 8); u32 x[7] = {0xe1923f9f, 0xe0533000, 0x01823f91, 0x03330001, 0x0afffffa, 0xe2730000, 0xe12fff1e}; memcpy(G(0xFFFF0FC0), x, 28); u32 b[2] = {0xf57ff05b, 0xe12fff1e}; memcpy(G(0xFFFF0FA0), b, 8); }
    }

    u32 sp = stack_top - 4096;
    auto push_str = [&](const std::string& s) { sp -= s.size() + 1; memcpy(G(sp), s.c_str(), s.size() + 1); return sp; };
    u32 execfn = push_str(exe_path);
    std::vector<u32> envp, argvp;
    for (auto& e : env) envp.push_back(push_str(e));
    for (auto& a : args) argvp.push_back(push_str(a));
    u32 plat = push_str("v7l");
    sp -= 16; u32 rnd = sp; getrandom(G(rnd), 16, 0);
    sp &= ~15u;
    std::vector<u32> aux = {
        3, phdr, 4, 32, 5, eh.e_phnum, 6, 4096, 7, interp.empty() ? 0 : ibase, 8, 0, 9, entry,
        11, 1000, 12, 1000, 13, 1000, 14, 1000, 15, plat, 16, (1u << 1) | (1u << 2) | (1u << 4) | (1u << 6) | (1u << 7) | (1u << 13) | (1u << 15) | (1u << 17) | (1u << 18),
        17, 100, 23, 0, 25, rnd, 26, 0, 31, execfn, 0, 0 };
    size_t words = 1 + argvp.size() + 1 + envp.size() + 1 + aux.size();
    sp -= words * 4;
    sp &= ~15u;
    u32 w = sp;
    auto put = [&](u32 v) { memcpy(G(w), &v, 4); w += 4; };
    put(argvp.size());
    for (u32 a : argvp) put(a);
    put(0);
    for (u32 e : envp) put(e);
    put(0);
    for (u32 a : aux) put(a);

    perfil_on = getenv("DYN_PROF") != nullptr;
    if (perfil_on) std::thread([]() { for (;;) { usleep(400); if (perfil_on) muestrear(); } }).detach();
    Hilo* h = nuevo_hilo();
    h->jit->Regs()[13] = sp;
    h->jit->Regs()[15] = ientry & ~1u;
    h->jit->SetCpsr(0x10 | ((ientry & 1) ? 0x20 : 0));
    if (getenv("DYN_PC")) std::thread([h]() { for (;;) { sleep(1); fprintf(stderr, "pc=%08x lr=%08x sp=%08x r0=%08x r1=%08x r2=%08x\n", h->jit->Regs()[15], h->jit->Regs()[14], h->jit->Regs()[13], h->jit->Regs()[0], h->jit->Regs()[1], h->jit->Regs()[2]); } }).detach();
    run_thread(h);
    fflush(NULL);
    return h->salida;
}
