#include <dlfcn.h>
#include <stdio.h>
extern "C" void* dlopen(const char* f, int m){
    static void*(*real)(const char*, int) = (void*(*)(const char*, int))dlsym(RTLD_NEXT, "dlopen");
    int n = ((m & 1) ? RTLD_LAZY : RTLD_NOW) | ((m & 2) ? RTLD_GLOBAL : 0);
    fprintf(stderr, "[dlopen %s modo=%d -> %d]\n", f ? f : "(null)", m, n);
    return real(f, n);
}
