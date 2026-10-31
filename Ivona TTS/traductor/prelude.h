#include <stdint.h>
#include <string.h>
typedef uint32_t u32; typedef int32_t s32; typedef uint64_t u64; typedef int64_t s64; typedef uint16_t u16;
extern thread_local u32 HOT_F;
extern "C" { extern uint8_t* HOT_MEM; void hot_call_guest(u32* R, u32 addr); }
#define call_guest hot_call_guest
#define MEM HOT_MEM
#define L32(a) (*(u32*)(MEM+(u32)(a)))
#define L16(a) ((u32)*(u16*)(MEM+(u32)(a)))
#define L8(a) ((u32)*(uint8_t*)(MEM+(u32)(a)))
#define S32(a,v) (*(u32*)(MEM+(u32)(a))=(u32)(v))
#define S16(a,v) (*(u16*)(MEM+(u32)(a))=(u16)(v))
#define S8(a,v) (*(uint8_t*)(MEM+(u32)(a))=(uint8_t)(v))
static inline u32 ror32(u32 x,u32 n){ n&=31; return n?((x>>n)|(x<<(32-n))):x; }
static inline u32 sh_lsl(u32 v,u32 s,u32* c){ s&=255; if(s==0) return v; if(s<32){*c=(v>>(32-s))&1; return v<<s;} if(s==32){*c=v&1; return 0;} *c=0; return 0; }
static inline u32 sh_lsr(u32 v,u32 s,u32* c){ s&=255; if(s==0) return v; if(s<32){*c=(v>>(s-1))&1; return v>>s;} if(s==32){*c=v>>31; return 0;} *c=0; return 0; }
static inline u32 sh_asr(u32 v,u32 s,u32* c){ s&=255; if(s==0) return v; if(s<32){*c=(v>>(s-1))&1; return (u32)((s32)v>>s);} *c=v>>31; return (u32)((s32)v>>31); }
static inline u32 sh_ror(u32 v,u32 s,u32* c){ s&=255; if(s==0) return v; s&=31; if(s==0){*c=v>>31; return v;} *c=(v>>(s-1))&1; return ror32(v,s); }
static inline u32 intr_strcmp(u32 a,u32 b,u32){ const uint8_t*p=MEM+a;const uint8_t*q=MEM+b; while(*p&&*p==*q){p++;q++;} return (u32)((int)*p-(int)*q); }
static inline u32 intr_strncmp(u32 a,u32 b,u32 n){ const uint8_t*p=MEM+a;const uint8_t*q=MEM+b; while(n&&*p&&*p==*q){p++;q++;n--;} return n?(u32)((int)*p-(int)*q):0; }
static inline u32 intr_strlen(u32 a,u32,u32){ return (u32)strlen((const char*)(MEM+a)); }
static inline u32 intr_memcpy(u32 d,u32 s,u32 n){ memmove(MEM+d,MEM+s,n); return d; }
static inline u32 intr_memmove(u32 d,u32 s,u32 n){ memmove(MEM+d,MEM+s,n); return d; }
static inline u32 intr_memset(u32 d,u32 c,u32 n){ memset(MEM+d,(int)c,n); return d; }
static inline u32 intr_memcmp(u32 a,u32 b,u32 n){ const uint8_t*p=MEM+a;const uint8_t*q=MEM+b; for(;n;n--,p++,q++) if(*p!=*q) return (u32)((int)*p-(int)*q); return 0; }
static inline u32 intr_strcpy(u32 d,u32 s,u32){ strcpy((char*)(MEM+d),(const char*)(MEM+s)); return d; }
static inline u32 intr_strncpy(u32 d,u32 s,u32 n){ strncpy((char*)(MEM+d),(const char*)(MEM+s),n); return d; }
static inline u32 intr_strcat(u32 d,u32 s,u32){ strcat((char*)(MEM+d),(const char*)(MEM+s)); return d; }
static inline u32 intr_strchr(u32 a,u32 c,u32){ const char*r=strchr((const char*)(MEM+a),(int)(c&255)); return r?(u32)(r-(const char*)MEM):0; }
static inline u32 intr_strrchr(u32 a,u32 c,u32){ const char*r=strrchr((const char*)(MEM+a),(int)(c&255)); return r?(u32)(r-(const char*)MEM):0; }
