#include <errno.h>
#include <stddef.h>
#include <string.h>
extern "C" {
char __sF[3*512] __attribute__((aligned(16)));
int* __errno(void){ return &errno; }
void android_set_abort_message(const char*){}
size_t __strlen_chk(const char* s, size_t n){ return strlen(s); }
}
__asm__(
".text\n.arm\n.global setjmp\n.type setjmp,%function\nsetjmp:\n"
" stmia r0!, {r4-r11, sp, lr}\n vstmia r0!, {d8-d15}\n mov r0, #0\n bx lr\n"
".global longjmp\n.type longjmp,%function\nlongjmp:\n"
" ldmia r0!, {r4-r11, sp, lr}\n vldmia r0!, {d8-d15}\n movs r0, r1\n moveq r0, #1\n bx lr\n");
#include <math.h>
extern "C" {
int __isfinite(double x){ return std::isfinite(x); }
}
#include <stdio.h>
#include <stdlib.h>
extern "C" {
void __assert2(const char* f,int l,const char* fn,const char* e){ fprintf(stderr,"assert %s:%d %s %s\n",f,l,fn,e); abort(); }
}
#include <dirent.h>
extern "C" {
struct dirent64* readdir_bionic(DIR* d) __asm__("readdir");
struct dirent64* readdir_bionic(DIR* d){ return readdir64(d); }
}
#include <pthread.h>
#include <stdint.h>
#include <dlfcn.h>
static int __real_lock(pthread_mutex_t* m){ static int(*f)(pthread_mutex_t*)=(int(*)(pthread_mutex_t*))dlsym(RTLD_NEXT,"pthread_mutex_lock"); return f(m);} 
static int __real_unlock(pthread_mutex_t* m){ static int(*f)(pthread_mutex_t*)=(int(*)(pthread_mutex_t*))dlsym(RTLD_NEXT,"pthread_mutex_unlock"); return f(m);}
static pthread_mutex_t g_mtx[4096];
static pthread_once_t g_mo=PTHREAD_ONCE_INIT;
static void mtx_init_all(){ pthread_mutexattr_t a; pthread_mutexattr_init(&a); pthread_mutexattr_settype(&a,PTHREAD_MUTEX_RECURSIVE); for(int i=0;i<4096;i++) pthread_mutex_init(&g_mtx[i],&a); }
static pthread_mutex_t* mtx_of(void* p){ pthread_once(&g_mo,mtx_init_all); uintptr_t v=(uintptr_t)p; v=(v>>2)*2654435761u; return &g_mtx[(v>>8)&4095]; }
extern "C" {
int bi_mutex_init(void* m,const void* a) __asm__("pthread_mutex_init");
int bi_mutex_init(void* m,const void* a){ *(int*)m=0; return 0; }
int bi_mutex_destroy(void* m) __asm__("pthread_mutex_destroy");
int bi_mutex_destroy(void* m){ return 0; }
int bi_mutex_lock(void* m) __asm__("pthread_mutex_lock");
int bi_mutex_lock(void* m){ return __real_lock(mtx_of(m)); }
int bi_mutex_unlock(void* m) __asm__("pthread_mutex_unlock");
int bi_mutex_unlock(void* m){ return __real_unlock(mtx_of(m)); }
}
static pthread_cond_t g_cv[4096];
static pthread_once_t g_co=PTHREAD_ONCE_INIT;
static void cv_init_all(){ for(int i=0;i<4096;i++) pthread_cond_init(&g_cv[i],nullptr); }
static pthread_cond_t* cv_of(void* p){ pthread_once(&g_co,cv_init_all); uintptr_t v=(uintptr_t)p; v=(v>>2)*2654435761u; return &g_cv[(v>>8)&4095]; }
static int __real_cwait(pthread_cond_t* c,pthread_mutex_t* m){ static int(*f)(pthread_cond_t*,pthread_mutex_t*)=(int(*)(pthread_cond_t*,pthread_mutex_t*))dlsym(RTLD_NEXT,"pthread_cond_wait"); return f(c,m);}
static int __real_csig(pthread_cond_t* c){ static int(*f)(pthread_cond_t*)=(int(*)(pthread_cond_t*))dlsym(RTLD_NEXT,"pthread_cond_signal"); return f(c);}
static int __real_cbc(pthread_cond_t* c){ static int(*f)(pthread_cond_t*)=(int(*)(pthread_cond_t*))dlsym(RTLD_NEXT,"pthread_cond_broadcast"); return f(c);}
extern "C" {
int bi_cond_wait(void* c,void* m) __asm__("pthread_cond_wait");
int bi_cond_wait(void* c,void* m){ return __real_cwait(cv_of(c),mtx_of(m)); }
int bi_cond_signal(void* c) __asm__("pthread_cond_signal");
int bi_cond_signal(void* c){ return __real_csig(cv_of(c)); }
int bi_cond_broadcast(void* c) __asm__("pthread_cond_broadcast");
int bi_cond_broadcast(void* c){ return __real_cbc(cv_of(c)); }
int bi_cond_destroy(void* c) __asm__("pthread_cond_destroy");
int bi_cond_destroy(void* c){ return 0; }
int bi_cond_init(void* c,const void* a) __asm__("pthread_cond_init");
int bi_cond_init(void* c,const void* a){ *(int*)c=0; return 0; }
}
