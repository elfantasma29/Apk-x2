#include <stdio.h>
#include <stdarg.h>
#include <unistd.h>
#include <sys/syscall.h>
static char g_ct[257];
static short g_lo[257];
static short g_up[257];
extern "C" {
const char* _ctype_ = g_ct;
const short* _tolower_tab_ = g_lo;
const short* _toupper_tab_ = g_up;
unsigned int __page_size = 4096;
__attribute__((constructor)) static void init_ctype(void){
    for(int i=0;i<256;i++){
        unsigned char f=0;
        if(i<32||i==127) f|=0x20;
        if(i>=9&&i<=13) f|=0x08;
        if(i==32) f|=0x08|0x80;
        if((i>=33&&i<=47)||(i>=58&&i<=64)||(i>=91&&i<=96)||(i>=123&&i<=126)) f|=0x10;
        if(i>=48&&i<=57) f|=0x04;
        if(i>=65&&i<=90) f|=0x01;
        if(i>=97&&i<=122) f|=0x02;
        if((i>=65&&i<=70)||(i>=97&&i<=102)||(i>=48&&i<=57)) f|=0x40;
        g_ct[i+1]=(char)f;
    }
    g_ct[0]=0;
    g_lo[0]=-1;
    g_up[0]=-1;
    for(int i=0;i<256;i++){
        g_lo[i+1]=(i>=65&&i<=90)?i+32:i;
        g_up[i+1]=(i>=97&&i<=122)?i-32:i;
    }
}
int __android_log_print(int p,const char*t,const char*f,...){
    va_list a; va_start(a,f); fprintf(stderr,"[log %s] ",t?t:""); vfprintf(stderr,f,a); fputc('\n',stderr); va_end(a); return 0;
}
int __android_log_vprint(int p,const char*t,const char*f,va_list a){
    fprintf(stderr,"[log %s] ",t?t:""); vfprintf(stderr,f,a); fputc('\n',stderr); return 0;
}
int cacheflush(long s,long e,long fl){ return (int)syscall(0xf0002,s,e,fl); }
}
__asm__(
".text\n.arm\n.global sigsetjmp\n.type sigsetjmp,%function\nsigsetjmp:\n"
" stmia r0!, {r4-r11, sp, lr}\n vstmia r0!, {d8-d15}\n mov r0, #0\n bx lr\n"
".global siglongjmp\n.type siglongjmp,%function\nsiglongjmp:\n"
" ldmia r0!, {r4-r11, sp, lr}\n vldmia r0!, {d8-d15}\n movs r0, r1\n moveq r0, #1\n bx lr\n");
