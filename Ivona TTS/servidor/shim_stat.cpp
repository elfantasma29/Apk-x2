#include <sys/stat.h>
#include <string.h>
static void conv(const struct stat64* s, void* b){
    unsigned char* o = (unsigned char*)b;
    memset(o, 0, 104);
    *(unsigned long long*)(o + 0)  = s->st_dev;
    *(unsigned int*)(o + 12)       = (unsigned int)s->st_ino;
    *(unsigned int*)(o + 16)       = s->st_mode;
    *(unsigned int*)(o + 20)       = s->st_nlink;
    *(unsigned int*)(o + 24)       = s->st_uid;
    *(unsigned int*)(o + 28)       = s->st_gid;
    *(unsigned long long*)(o + 32) = s->st_rdev;
    *(long long*)(o + 48)          = s->st_size;
    *(unsigned int*)(o + 56)       = (unsigned int)s->st_blksize;
    *(unsigned long long*)(o + 64) = s->st_blocks;
    *(unsigned int*)(o + 72)       = (unsigned int)s->st_atim.tv_sec;
    *(unsigned int*)(o + 76)       = (unsigned int)s->st_atim.tv_nsec;
    *(unsigned int*)(o + 80)       = (unsigned int)s->st_mtim.tv_sec;
    *(unsigned int*)(o + 84)       = (unsigned int)s->st_mtim.tv_nsec;
    *(unsigned int*)(o + 88)       = (unsigned int)s->st_ctim.tv_sec;
    *(unsigned int*)(o + 92)       = (unsigned int)s->st_ctim.tv_nsec;
    *(unsigned long long*)(o + 96) = s->st_ino;
}
extern "C" {
int bi_fstat(int fd, void* b) __asm__("fstat");
int bi_fstat(int fd, void* b){ struct stat64 t; int r = fstat64(fd, &t); if(!r) conv(&t, b); return r; }
int bi_stat(const char* p, void* b) __asm__("stat");
int bi_stat(const char* p, void* b){ struct stat64 t; int r = stat64(p, &t); if(!r) conv(&t, b); return r; }
int bi_lstat(const char* p, void* b) __asm__("lstat");
int bi_lstat(const char* p, void* b){ struct stat64 t; int r = lstat64(p, &t); if(!r) conv(&t, b); return r; }
}
