#include <sched.h>
#include <pthread.h>
#include <semaphore.h>
#include <signal.h>
#include <unistd.h>
#include <time.h>
#include <jni.h>
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <errno.h>
#include <string>
#include <map>
#include <vector>
#include <mutex>

struct Cls { std::string name; };
struct Obj { Cls* cls; std::map<std::string, jvalue> f; std::string str; std::vector<jobject> arr; std::vector<short> shorts; int kind; };
struct FID { Cls* c; std::string n, s; };
struct MID { Cls* c; std::string n, s; };

static std::map<std::string, Cls*> classes;
static std::mutex mu;
static FILE* pcmout = nullptr;
static long total_samples = 0;
static int audio_calls = 0;
static JavaVM g_vm;
static JNIEnv g_env;
static const char* trace = getenv("JTRACE");

static Cls* getcls(const char* n){ std::lock_guard<std::mutex> l(mu); auto it=classes.find(n); if(it!=classes.end()) return it->second; Cls* c=new Cls{n}; classes[n]=c; return c; }
static Obj* mk(Cls* c,int kind=0){ Obj* o=new Obj(); o->cls=c; o->kind=kind; return o; }

#define LOG(...) do{ if(trace){ fprintf(stderr,"[jni] " __VA_ARGS__); fputc('\n',stderr);} }while(0)

static jclass J_FindClass(JNIEnv*, const char* n){ LOG("FindClass %s",n); return (jclass)getcls(n); }
static jclass J_GetObjectClass(JNIEnv*, jobject o){ return (jclass)((Obj*)o)->cls; }
static jfieldID J_GetFieldID(JNIEnv*, jclass c, const char* n, const char* s){ LOG("GetFieldID %s %s",n,s); return (jfieldID)new FID{(Cls*)c,n,s}; }
static jmethodID J_GetMethodID(JNIEnv*, jclass c, const char* n, const char* s){ LOG("GetMethodID %s.%s %s",((Cls*)c)->name.c_str(),n,s); return (jmethodID)new MID{(Cls*)c,n,s}; }
static jlong J_GetLongField(JNIEnv*, jobject o, jfieldID f){ return ((Obj*)o)->f[((FID*)f)->n].j; }
static void J_SetLongField(JNIEnv*, jobject o, jfieldID f, jlong v){ jvalue x; x.j=v; ((Obj*)o)->f[((FID*)f)->n]=x; }
static jint J_GetIntField(JNIEnv*, jobject o, jfieldID f){ return ((Obj*)o)->f[((FID*)f)->n].i; }
static void J_SetIntField(JNIEnv*, jobject o, jfieldID f, jint v){ jvalue x; x.i=v; ((Obj*)o)->f[((FID*)f)->n]=x; }
static jobject J_GetObjectField(JNIEnv*, jobject o, jfieldID f){ return ((Obj*)o)->f[((FID*)f)->n].l; }
static void J_SetObjectField(JNIEnv*, jobject o, jfieldID f, jobject v){ jvalue x; x.l=v; ((Obj*)o)->f[((FID*)f)->n]=x; }
static jboolean J_GetBooleanField(JNIEnv*, jobject o, jfieldID f){ return ((Obj*)o)->f[((FID*)f)->n].z; }
static void J_SetBooleanField(JNIEnv*, jobject o, jfieldID f, jboolean v){ jvalue x; x.j=0; x.z=v; ((Obj*)o)->f[((FID*)f)->n]=x; }
static jfloat J_GetFloatField(JNIEnv*, jobject o, jfieldID f){ return ((Obj*)o)->f[((FID*)f)->n].f; }
static void J_SetFloatField(JNIEnv*, jobject o, jfieldID f, jfloat v){ jvalue x; x.j=0; x.f=v; ((Obj*)o)->f[((FID*)f)->n]=x; }
static jshort J_GetShortField(JNIEnv*, jobject o, jfieldID f){ return ((Obj*)o)->f[((FID*)f)->n].s; }
static void J_SetShortField(JNIEnv*, jobject o, jfieldID f, jshort v){ jvalue x; x.j=0; x.s=v; ((Obj*)o)->f[((FID*)f)->n]=x; }
static jobject J_AllocObject(JNIEnv*, jclass c){ return (jobject)mk((Cls*)c); }
static jobject J_NewObject(JNIEnv*, jclass c, jmethodID, ...){ return (jobject)mk((Cls*)c); }
static jobject J_NewObjectA(JNIEnv*, jclass c, jmethodID, const jvalue*){ return (jobject)mk((Cls*)c); }
static jobject J_NewObjectV(JNIEnv*, jclass c, jmethodID, va_list){ return (jobject)mk((Cls*)c); }
static jstring J_NewStringUTF(JNIEnv*, const char* s){ Obj* o=mk(getcls("java/lang/String"),1); o->str=s?s:""; return (jstring)o; }
static const char* J_GetStringUTFChars(JNIEnv*, jstring s, jboolean* c){ if(c)*c=JNI_FALSE; return ((Obj*)s)->str.c_str(); }
static void J_ReleaseStringUTFChars(JNIEnv*, jstring, const char*){}
static jsize J_GetStringUTFLength(JNIEnv*, jstring s){ return ((Obj*)s)->str.size(); }
static jsize J_GetStringLength(JNIEnv*, jstring s){ return ((Obj*)s)->str.size(); }
static jobjectArray J_NewObjectArray(JNIEnv*, jsize n, jclass, jobject init){ Obj* o=mk(getcls("[L"),2); o->arr.assign(n,init); return (jobjectArray)o; }
static void J_SetObjectArrayElement(JNIEnv*, jobjectArray a, jsize i, jobject v){ ((Obj*)a)->arr[i]=v; }
static jobject J_GetObjectArrayElement(JNIEnv*, jobjectArray a, jsize i){ return ((Obj*)a)->arr[i]; }
static jsize J_GetArrayLength(JNIEnv*, jarray a){ Obj* o=(Obj*)a; return o->kind==3? o->shorts.size(): o->arr.size(); }
static jshortArray J_NewShortArray(JNIEnv*, jsize n){ Obj* o=mk(getcls("[S"),3); o->shorts.assign(n,0); return (jshortArray)o; }
static void J_SetShortArrayRegion(JNIEnv*, jshortArray a, jsize s, jsize l, const jshort* b){ Obj* o=(Obj*)a; if((size_t)(s+l)>o->shorts.size()) o->shorts.resize(s+l); memcpy(&o->shorts[s],b,l*2); }
static jshort* J_GetShortArrayElements(JNIEnv*, jshortArray a, jboolean* c){ if(c)*c=JNI_FALSE; return ((Obj*)a)->shorts.data(); }
static void J_ReleaseShortArrayElements(JNIEnv*, jshortArray, jshort*, jint){}
static void J_GetShortArrayRegion(JNIEnv*, jshortArray a, jsize s, jsize l, jshort* b){ memcpy(b,&((Obj*)a)->shorts[s],l*2); }
static void J_DeleteLocalRef(JNIEnv*, jobject){}
static jobject J_NewGlobalRef(JNIEnv*, jobject o){ return o; }
static jobject J_NewLocalRef(JNIEnv*, jobject o){ return o; }
static void J_DeleteGlobalRef(JNIEnv*, jobject){}
static jboolean J_ExceptionCheck(JNIEnv*){ return JNI_FALSE; }
static jthrowable J_ExceptionOccurred(JNIEnv*){ return nullptr; }
static void J_ExceptionClear(JNIEnv*){}
static void J_ExceptionDescribe(JNIEnv*){}
static jint J_EnsureLocalCapacity(JNIEnv*, jint){ return 0; }
static jint J_PushLocalFrame(JNIEnv*, jint){ return 0; }
static jobject J_PopLocalFrame(JNIEnv*, jobject r){ return r; }
static jint J_GetJavaVM(JNIEnv*, JavaVM** vm){ *vm=&g_vm; return 0; }
static jboolean J_IsSameObject(JNIEnv*, jobject a, jobject b){ return a==b; }
static jboolean J_IsInstanceOf(JNIEnv*, jobject, jclass){ return JNI_TRUE; }
struct RN{std::string n,s; void* f;}; static std::vector<RN> g_rn;
static jint J_RegisterNatives(JNIEnv*, jclass c, const JNINativeMethod* m, jint n){ LOG("RegisterNatives %s n=%d",((Cls*)c)->name.c_str(),n); for(int i=0;i<n;i++){ g_rn.push_back({m[i].name,m[i].signature,m[i].fnPtr}); LOG("  nativo %s %s",m[i].name,m[i].signature);} return 0; }
static jint J_Throw(JNIEnv*, jthrowable){ fprintf(stderr,"[jni] Throw\n"); return 0; }
static jint J_ThrowNew(JNIEnv*, jclass c, const char* m){ fprintf(stderr,"[jni] ThrowNew %s: %s\n",((Cls*)c)->name.c_str(),m?m:""); return 0; }

static void sink_audio(Obj* arr){
  audio_calls++;
  total_samples += arr->shorts.size();
  if(pcmout && !arr->shorts.empty()) fwrite(arr->shorts.data(),2,arr->shorts.size(),pcmout);
}
static jboolean J_CallBooleanMethod(JNIEnv*, jobject o, jmethodID m, ...){
  MID* mid=(MID*)m; va_list ap; va_start(ap,m);
  jboolean r=JNI_TRUE;
  if(mid->n=="audioSamplesReceived"){ jobject a=va_arg(ap,jobject); sink_audio((Obj*)a); }
  else LOG("CallBooleanMethod %s %s",mid->n.c_str(),mid->s.c_str());
  va_end(ap); return r;
}
static jboolean J_CallBooleanMethodV(JNIEnv*, jobject o, jmethodID m, va_list ap){
  MID* mid=(MID*)m; if(mid->n=="audioSamplesReceived"){ jobject a=va_arg(ap,jobject); sink_audio((Obj*)a);} else LOG("CallBooleanMethodV %s",mid->n.c_str()); return JNI_TRUE; }
static jboolean J_CallBooleanMethodA(JNIEnv*, jobject o, jmethodID m, const jvalue* a){
  MID* mid=(MID*)m; if(mid->n=="audioSamplesReceived") sink_audio((Obj*)a[0].l); else LOG("CallBooleanMethodA %s",mid->n.c_str()); return JNI_TRUE; }
static void J_CallVoidMethod(JNIEnv*, jobject, jmethodID m, ...){ LOG("CallVoidMethod %s",((MID*)m)->n.c_str()); }
static void J_CallVoidMethodV(JNIEnv*, jobject, jmethodID m, va_list){ LOG("CallVoidMethodV %s",((MID*)m)->n.c_str()); }
static void J_CallVoidMethodA(JNIEnv*, jobject, jmethodID m, const jvalue*){ LOG("CallVoidMethodA %s",((MID*)m)->n.c_str()); }
static jint J_CallIntMethod(JNIEnv*, jobject, jmethodID m, ...){ LOG("CallIntMethod %s",((MID*)m)->n.c_str()); return 0; }
static jobject objret(MID* mi){ std::string s=mi->s; size_t p=s.rfind(')'); std::string r=s.substr(p+1); LOG("CallObjectMethod %s.%s %s",mi->c->name.c_str(),mi->n.c_str(),mi->s.c_str()); if(r.size()>2&&r[0]=='L'){ r=r.substr(1,r.size()-2); return (jobject)mk(getcls(r.c_str())); } return nullptr; }
static jobject J_CallObjectMethod(JNIEnv*, jobject, jmethodID m, ...){ return objret((MID*)m); }
static jobject J_CallObjectMethodV(JNIEnv*, jobject, jmethodID m, va_list){ return objret((MID*)m); }
static jobject J_CallObjectMethodA(JNIEnv*, jobject, jmethodID m, const jvalue*){ return objret((MID*)m); }


static jfieldID J_GetStaticFieldID(JNIEnv*, jclass c, const char* n, const char* s){ LOG("GetStaticFieldID %s.%s %s",((Cls*)c)->name.c_str(),n,s); return (jfieldID)new FID{(Cls*)c,n,s}; }
static jobject J_GetStaticObjectField(JNIEnv*, jclass c, jfieldID f){ FID* fi=(FID*)f; LOG("GetStaticObjectField %s %s",fi->n.c_str(),fi->s.c_str()); std::string sg=fi->s; if(sg.size()>2&&sg[0]=='L') sg=sg.substr(1,sg.size()-2); return (jobject)mk(getcls(sg.c_str())); }
static jmethodID J_GetStaticMethodID(JNIEnv*, jclass c, const char* n, const char* s){ LOG("GetStaticMethodID %s.%s %s",((Cls*)c)->name.c_str(),n,s); return (jmethodID)new MID{(Cls*)c,n,s}; }
static jobject J_CallStaticObjectMethod(JNIEnv*, jclass, jmethodID m, ...){ MID* mi=(MID*)m; LOG("CallStaticObjectMethod %s %s",mi->n.c_str(),mi->s.c_str()); return nullptr; }
static jint J_CallStaticIntMethod(JNIEnv*, jclass, jmethodID m, ...){ LOG("CallStaticIntMethod %s",((MID*)m)->n.c_str()); return 0; }
static void J_CallStaticVoidMethod(JNIEnv*, jclass, jmethodID m, ...){ LOG("CallStaticVoidMethod %s",((MID*)m)->n.c_str()); }
static jboolean J_CallStaticBooleanMethod(JNIEnv*, jclass, jmethodID m, ...){ LOG("CallStaticBooleanMethod %s",((MID*)m)->n.c_str()); return JNI_FALSE; }
static const char* JNAMES[]={"reserved0","reserved1","reserved2","reserved3","GetVersion","DefineClass","FindClass","FromReflectedMethod","FromReflectedField","ToReflectedMethod","GetSuperclass","IsAssignableFrom","ToReflectedField","Throw","ThrowNew","ExceptionOccurred","ExceptionDescribe","ExceptionClear","FatalError","PushLocalFrame","PopLocalFrame","NewGlobalRef","DeleteGlobalRef","DeleteLocalRef","IsSameObject","NewLocalRef","EnsureLocalCapacity","AllocObject","NewObject","NewObjectV","NewObjectA","GetObjectClass","IsInstanceOf","GetMethodID","CallObjectMethod","CallObjectMethodV","CallObjectMethodA","CallBooleanMethod","CallBooleanMethodV","CallBooleanMethodA","CallByteMethod","CallByteMethodV","CallByteMethodA","CallCharMethod","CallCharMethodV","CallCharMethodA","CallShortMethod","CallShortMethodV","CallShortMethodA","CallIntMethod","CallIntMethodV","CallIntMethodA","CallLongMethod","CallLongMethodV","CallLongMethodA","CallFloatMethod","CallFloatMethodV","CallFloatMethodA","CallDoubleMethod","CallDoubleMethodV","CallDoubleMethodA","CallVoidMethod","CallVoidMethodV","CallVoidMethodA","CallNonvirtualObjectMethod","CallNonvirtualObjectMethodV","CallNonvirtualObjectMethodA","CallNonvirtualBooleanMethod","CallNonvirtualBooleanMethodV","CallNonvirtualBooleanMethodA","CallNonvirtualByteMethod","CallNonvirtualByteMethodV","CallNonvirtualByteMethodA","CallNonvirtualCharMethod","CallNonvirtualCharMethodV","CallNonvirtualCharMethodA","CallNonvirtualShortMethod","CallNonvirtualShortMethodV","CallNonvirtualShortMethodA","CallNonvirtualIntMethod","CallNonvirtualIntMethodV","CallNonvirtualIntMethodA","CallNonvirtualLongMethod","CallNonvirtualLongMethodV","CallNonvirtualLongMethodA","CallNonvirtualFloatMethod","CallNonvirtualFloatMethodV","CallNonvirtualFloatMethodA","CallNonvirtualDoubleMethod","CallNonvirtualDoubleMethodV","CallNonvirtualDoubleMethodA","CallNonvirtualVoidMethod","CallNonvirtualVoidMethodV","CallNonvirtualVoidMethodA","GetFieldID","GetObjectField","GetBooleanField","GetByteField","GetCharField","GetShortField","GetIntField","GetLongField","GetFloatField","GetDoubleField","SetObjectField","SetBooleanField","SetByteField","SetCharField","SetShortField","SetIntField","SetLongField","SetFloatField","SetDoubleField","GetStaticMethodID","CallStaticObjectMethod","CallStaticObjectMethodV","CallStaticObjectMethodA","CallStaticBooleanMethod","CallStaticBooleanMethodV","CallStaticBooleanMethodA","CallStaticByteMethod","CallStaticByteMethodV","CallStaticByteMethodA","CallStaticCharMethod","CallStaticCharMethodV","CallStaticCharMethodA","CallStaticShortMethod","CallStaticShortMethodV","CallStaticShortMethodA","CallStaticIntMethod","CallStaticIntMethodV","CallStaticIntMethodA","CallStaticLongMethod","CallStaticLongMethodV","CallStaticLongMethodA","CallStaticFloatMethod","CallStaticFloatMethodV","CallStaticFloatMethodA","CallStaticDoubleMethod","CallStaticDoubleMethodV","CallStaticDoubleMethodA","CallStaticVoidMethod","CallStaticVoidMethodV","CallStaticVoidMethodA","GetStaticFieldID","GetStaticObjectField","GetStaticBooleanField","GetStaticByteField","GetStaticCharField","GetStaticShortField","GetStaticIntField","GetStaticLongField","GetStaticFloatField","GetStaticDoubleField","SetStaticObjectField","SetStaticBooleanField","SetStaticByteField","SetStaticCharField","SetStaticShortField","SetStaticIntField","SetStaticLongField","SetStaticFloatField","SetStaticDoubleField","NewString","GetStringLength","GetStringChars","ReleaseStringChars","NewStringUTF","GetStringUTFLength","GetStringUTFChars","ReleaseStringUTFChars","GetArrayLength","NewObjectArray","GetObjectArrayElement","SetObjectArrayElement","NewBooleanArray","NewByteArray","NewCharArray","NewShortArray","NewIntArray","NewLongArray","NewFloatArray","NewDoubleArray","GetBooleanArrayElements","GetByteArrayElements","GetCharArrayElements","GetShortArrayElements","GetIntArrayElements","GetLongArrayElements","GetFloatArrayElements","GetDoubleArrayElements","ReleaseBooleanArrayElements","ReleaseByteArrayElements","ReleaseCharArrayElements","ReleaseShortArrayElements","ReleaseIntArrayElements","ReleaseLongArrayElements","ReleaseFloatArrayElements","ReleaseDoubleArrayElements","GetBooleanArrayRegion","GetByteArrayRegion","GetCharArrayRegion","GetShortArrayRegion","GetIntArrayRegion","GetLongArrayRegion","GetFloatArrayRegion","GetDoubleArrayRegion","SetBooleanArrayRegion","SetByteArrayRegion","SetCharArrayRegion","SetShortArrayRegion","SetIntArrayRegion","SetLongArrayRegion","SetFloatArrayRegion","SetDoubleArrayRegion","RegisterNatives","UnregisterNatives","MonitorEnter","MonitorExit","GetJavaVM","GetStringRegion","GetStringUTFRegion","GetPrimitiveArrayCritical","ReleasePrimitiveArrayCritical","GetStringCritical","ReleaseStringCritical","NewWeakGlobalRef","DeleteWeakGlobalRef","ExceptionCheck","NewDirectByteBuffer","GetDirectBufferAddress","GetDirectBufferCapacity","GetObjectRefType"};
template<int N> static void unimplN(){ fprintf(stderr,"[jni] FUNCION JNI NO IMPLEMENTADA %d %s\n",N,JNAMES[N]); abort(); }
template<int... I> struct Seq{};
template<int N,int... I> struct Gen : Gen<N-1,N-1,I...>{};
template<int... I> struct Gen<0,I...>{ static void fill(void** t){ void* a[]={(void*)unimplN<I>...}; for(size_t i=0;i<sizeof...(I);i++) t[i]=a[i]; } };

static jint V_GetEnv(JavaVM*, void** e, jint){ *e=&g_env; return 0; }
static jint V_Attach(JavaVM*, JNIEnv** e, void*){ *e=&g_env; return 0; }
static jint V_Detach(JavaVM*){ return 0; }
static JNIInvokeInterface vmt;
static JNINativeInterface fnt;


static int g_rate=0;
static jbyteArray J_NewByteArray(JNIEnv*, jsize n){ Obj* o=mk(getcls("[B"),4); o->shorts.assign((n+1)/2,0); o->f["len"].i=n; return (jbyteArray)o; }
static void J_SetByteArrayRegion(JNIEnv*, jbyteArray a, jsize s, jsize l, const jbyte* b){ Obj* o=(Obj*)a; if(o->f["len"].i<s+l){ o->f["len"].i=s+l; o->shorts.resize((s+l+1)/2);} memcpy(((char*)o->shorts.data())+s,b,l); }
static jbyte* J_GetByteArrayElements(JNIEnv*, jbyteArray a, jboolean* c){ if(c)*c=JNI_FALSE; return (jbyte*)((Obj*)a)->shorts.data(); }
static void J_ReleaseByteArrayElements(JNIEnv*, jbyteArray, jbyte*, jint){}
static jsize J_GetArrayLength2(JNIEnv*, jarray a){ Obj* o=(Obj*)a; if(o->kind==4) return o->f["len"].i; if(o->kind==3) return o->shorts.size(); return o->arr.size(); }
static long g_bytes=0; static int g_chunks=0; static int g_done=0;
static FILE* g_pcm=nullptr;

static std::mutex g_out_mu;
static void out_lock(){ g_out_mu.lock(); }
static void out_unlock(){ g_out_mu.unlock(); }
static int g_pfd=1;
static void write_all(const char* p,size_t n){ while(n>0){ ssize_t w=write(g_pfd,p,n); if(w<=0){ if(errno==EINTR) continue; _exit(0);} p+=w; n-=w; } }
static void send_line(const std::string& l){ out_lock(); write_all(l.data(),l.size()); out_unlock(); }
static volatile int g_stopped=0;
static jint cb_int(MID* mi, va_list ap){
  const std::string& n=mi->n;
  if(n=="getMaxBufferSize") return 8192;
  if(n=="start"){ int r=va_arg(ap,int); g_rate=r; char b[64]; snprintf(b,sizeof b,"S\t%d\n",r); send_line(b); return 0; }
  if(n=="audioAvailable"){ Obj* a=va_arg(ap,Obj*); int off=va_arg(ap,int); int len=va_arg(ap,int); if(len<=0) return 0; char b[64]; int hl=snprintf(b,sizeof b,"A\t%d\n",len); out_lock(); write_all(b,hl); write_all(((char*)a->shorts.data())+off,len); out_unlock(); return 0; }
  if(n=="done"){ send_line("D\n"); return 0; }
  if(n=="error"){ send_line("E\n"); return 0; }
  return 0; }
static jint J_CallIntMethodX(JNIEnv*, jobject, jmethodID m, ...){ va_list ap; va_start(ap,m); jint r=cb_int((MID*)m,ap); va_end(ap); return r; }
static jint J_CallIntMethodVX(JNIEnv*, jobject, jmethodID m, va_list ap){ return cb_int((MID*)m,ap); }
static void J_CallVoidMethodX(JNIEnv*, jobject, jmethodID m, ...){ va_list ap; va_start(ap,m); cb_int((MID*)m,ap); va_end(ap); }
static void J_CallVoidMethodVX(JNIEnv*, jobject, jmethodID m, va_list ap){ cb_int((MID*)m,ap); }
static jint J_GetStringRegionDummy(){ return 0; }

#include <codecvt>
#include <locale>
static const jchar* J_GetStringChars(JNIEnv*, jstring s, jboolean* c){ if(c)*c=JNI_TRUE; std::string u=((Obj*)s)->str; std::u16string w; { size_t i=0; while(i<u.size()){ unsigned char ch=u[i]; unsigned cp; int n; if(ch<0x80){cp=ch;n=1;} else if((ch>>5)==6){cp=ch&0x1f;n=2;} else if((ch>>4)==14){cp=ch&0xf;n=3;} else {cp=ch&7;n=4;} for(int k=1;k<n&&i+k<u.size();k++) cp=(cp<<6)|(u[i+k]&0x3f); i+=n; if(cp>=0x10000){cp-=0x10000; w.push_back(0xd800+(cp>>10)); w.push_back(0xdc00+(cp&0x3ff));} else w.push_back((char16_t)cp);} }
  jchar* r=(jchar*)malloc((w.size()+1)*2); memcpy(r,w.data(),w.size()*2); r[w.size()]=0; return r; }
static void J_ReleaseStringChars(JNIEnv*, jstring, const jchar* p){ free((void*)p); }
static jsize J_StrLen16(JNIEnv*, jstring s){ const jchar* p=J_GetStringChars(nullptr,s,nullptr); jsize n=0; while(p[n]) n++; free((void*)p); return n; }
static RN* findrn(const char* n){ for(auto& r:g_rn) if(r.n==n) return &r; fprintf(stderr,"no hay nativo %s\n",n); return nullptr; }


typedef jstring (*gs_t)(JNIEnv*, jobject);
typedef jstring (*gv_t)(JNIEnv*, jobject, jstring);
typedef jint (*il_t)(JNIEnv*, jobject, jstring, jstring, jstring);
typedef jint (*sp_t)(JNIEnv*, jobject, jint, jint);
typedef jint (*st_t)(JNIEnv*, jobject, jstring, jobject);
typedef jint (*d_t)(JNIEnv*, jobject);
typedef jobject (*ga_t)(JNIEnv*, jobject);

struct Cmd { std::string name; std::vector<std::string> args; std::string payload; };
static std::vector<Cmd> g_q;
static std::mutex g_q_mu;
static sem_t g_sem;
static JNIEnv* g_e; static Obj* g_self; static Obj* g_cb;

static void q_push(const Cmd& c){ { std::lock_guard<std::mutex> l(g_q_mu); g_q.push_back(c); } sem_post(&g_sem); }
static bool q_pop(Cmd& c){ sem_wait(&g_sem); std::lock_guard<std::mutex> l(g_q_mu); bool ok=!g_q.empty(); if(ok){ c=g_q.front(); g_q.erase(g_q.begin()); } return ok; }

static std::string rd_line(){ std::string l; char ch; for(;;){ ssize_t r=read(0,&ch,1); if(r==0) _exit(0); if(r<0){ if(errno==EINTR) continue; _exit(0);} if(ch=='\n') break; l.push_back(ch);} return l; }
static std::string rd_bytes(size_t n){ std::string b; b.resize(n); size_t g=0; while(g<n){ ssize_t r=read(0,&b[g],n-g); if(r==0) _exit(0); if(r<0){ if(errno==EINTR) continue; _exit(0);} g+=r; } return b; }
static std::vector<std::string> split_tab(const std::string& s){ std::vector<std::string> v; size_t p=0; for(;;){ size_t q=s.find('\t',p); if(q==std::string::npos){ v.push_back(s.substr(p)); break;} v.push_back(s.substr(p,q-p)); p=q+1; } return v; }

static void* reader(void*){
  for(;;){
    std::string l=rd_line();
    if(l.empty()) continue;
    std::vector<std::string> v=split_tab(l);
    Cmd c; c.name=v[0]; c.args.assign(v.begin()+1,v.end());
    if(c.name=="QUIT") _exit(0);
    if(c.name=="STOP"){ RN* r=findrn("stop"); if(r) ((d_t)r->f)(g_e,(jobject)g_self); continue; }
    if(c.name=="SPEAK"){ size_t n=c.args.empty()?0:strtoul(c.args[0].c_str(),0,10); c.payload=rd_bytes(n); }
    q_push(c);
  }
  return 0;
}

int main(int argc,char**argv){
  if(argc<4){ fprintf(stderr,"uso: %s <proxy.so> <carpeta_libs> <carpeta_datos>\n",argv[0]); return 1; }
  g_pfd=dup(1); dup2(2,1);
  sem_init(&g_sem,0,0);
  void** t=(void**)&fnt; Gen<240>::fill(t);
  fnt.FindClass=J_FindClass; fnt.GetObjectClass=J_GetObjectClass; fnt.GetFieldID=J_GetFieldID; fnt.GetMethodID=J_GetMethodID;
  fnt.GetLongField=J_GetLongField; fnt.SetLongField=J_SetLongField; fnt.GetIntField=J_GetIntField; fnt.SetIntField=J_SetIntField;
  fnt.GetObjectField=J_GetObjectField; fnt.SetObjectField=J_SetObjectField; fnt.GetBooleanField=J_GetBooleanField; fnt.SetBooleanField=J_SetBooleanField;
  fnt.GetFloatField=J_GetFloatField; fnt.SetFloatField=J_SetFloatField; fnt.GetShortField=J_GetShortField; fnt.SetShortField=J_SetShortField;
  fnt.AllocObject=J_AllocObject; fnt.NewObject=J_NewObject; fnt.NewObjectA=J_NewObjectA; fnt.NewObjectV=J_NewObjectV;
  fnt.NewStringUTF=J_NewStringUTF; fnt.GetStringUTFChars=J_GetStringUTFChars; fnt.ReleaseStringUTFChars=J_ReleaseStringUTFChars;
  fnt.GetStringUTFLength=J_GetStringUTFLength;
  fnt.NewObjectArray=J_NewObjectArray; fnt.SetObjectArrayElement=J_SetObjectArrayElement; fnt.GetObjectArrayElement=J_GetObjectArrayElement;
  fnt.GetArrayLength=J_GetArrayLength; fnt.NewShortArray=J_NewShortArray; fnt.SetShortArrayRegion=J_SetShortArrayRegion;
  fnt.GetShortArrayElements=J_GetShortArrayElements; fnt.ReleaseShortArrayElements=J_ReleaseShortArrayElements; fnt.GetShortArrayRegion=J_GetShortArrayRegion;
  fnt.DeleteLocalRef=J_DeleteLocalRef; fnt.NewGlobalRef=J_NewGlobalRef; fnt.NewLocalRef=J_NewLocalRef; fnt.DeleteGlobalRef=J_DeleteGlobalRef;
  fnt.ExceptionCheck=J_ExceptionCheck; fnt.ExceptionOccurred=J_ExceptionOccurred; fnt.ExceptionClear=J_ExceptionClear; fnt.ExceptionDescribe=J_ExceptionDescribe;
  fnt.EnsureLocalCapacity=J_EnsureLocalCapacity; fnt.PushLocalFrame=J_PushLocalFrame; fnt.PopLocalFrame=J_PopLocalFrame;
  fnt.GetJavaVM=J_GetJavaVM; fnt.IsSameObject=J_IsSameObject; fnt.IsInstanceOf=J_IsInstanceOf; fnt.RegisterNatives=J_RegisterNatives;
  fnt.Throw=J_Throw; fnt.ThrowNew=J_ThrowNew;
  fnt.CallBooleanMethod=J_CallBooleanMethod; fnt.CallBooleanMethodV=J_CallBooleanMethodV; fnt.CallBooleanMethodA=J_CallBooleanMethodA;
  fnt.CallVoidMethod=J_CallVoidMethod; fnt.CallVoidMethodV=J_CallVoidMethodV; fnt.CallVoidMethodA=J_CallVoidMethodA;
  fnt.CallIntMethod=J_CallIntMethod; fnt.CallObjectMethod=J_CallObjectMethod; fnt.CallObjectMethodV=J_CallObjectMethodV; fnt.CallObjectMethodA=J_CallObjectMethodA;
  fnt.GetStaticFieldID=J_GetStaticFieldID; fnt.GetStaticObjectField=J_GetStaticObjectField; fnt.GetStaticMethodID=J_GetStaticMethodID; fnt.CallStaticObjectMethod=J_CallStaticObjectMethod; fnt.CallStaticIntMethod=J_CallStaticIntMethod; fnt.CallStaticVoidMethod=J_CallStaticVoidMethod; fnt.CallStaticBooleanMethod=J_CallStaticBooleanMethod;
  g_env.functions=&fnt;
  vmt.GetEnv=V_GetEnv; vmt.AttachCurrentThread=V_Attach; vmt.DetachCurrentThread=V_Detach; vmt.AttachCurrentThreadAsDaemon=V_Attach;
  g_vm.functions=&vmt;
  fnt.NewByteArray=J_NewByteArray; fnt.SetByteArrayRegion=J_SetByteArrayRegion; fnt.GetByteArrayElements=J_GetByteArrayElements; fnt.ReleaseByteArrayElements=J_ReleaseByteArrayElements;
  fnt.GetArrayLength=J_GetArrayLength2; fnt.GetStringChars=J_GetStringChars; fnt.ReleaseStringChars=J_ReleaseStringChars; fnt.GetStringLength=J_StrLen16;
  fnt.CallIntMethod=J_CallIntMethodX; fnt.CallIntMethodV=J_CallIntMethodVX; fnt.CallVoidMethod=J_CallVoidMethodX; fnt.CallVoidMethodV=J_CallVoidMethodVX;

  const char* proxy=argv[1]; const char* libdir=argv[2]; const char* datadir=argv[3];
  void* h=dlopen(proxy,RTLD_NOW|RTLD_GLOBAL);
  if(!h){ std::string e=std::string("R\tERR\tno se pudo cargar el proxy: ")+dlerror()+"\n"; send_line(e); return 3; }
  typedef jint (*onload_t)(JavaVM*, void*);
  onload_t ol=(onload_t)dlsym(h,"JNI_OnLoad");
  if(ol) ol(&g_vm,nullptr);
  typedef jboolean (*init_t)(JNIEnv*, jclass);
  init_t in=(init_t)dlsym(h,"Java_ivona_tts_SynthesisProxy_init");
  g_e=&g_env;
  if(in) in(g_e,(jclass)getcls("ivona/tts/SynthesisProxy"));
  g_self=mk(getcls("ivona/tts/SynthesisProxy"));
  g_self->f["data"].i=0;
  g_cb=mk(getcls("android/speech/tts/SynthesisCallback"));
  RN* r=findrn("create");
  if(!r){ send_line("R\tERR\tel proxy no registro create\n"); return 4; }
  ((d_t)r->f)(g_e,(jobject)g_self);
  r=findrn("loadVoice");
  typedef jint (*lv_t)(JNIEnv*,jobject,jstring,jstring);
  jint lv=r?((lv_t)r->f)(g_e,(jobject)g_self,J_NewStringUTF(g_e,libdir),J_NewStringUTF(g_e,datadir)):-99;
  if(lv!=0){ char b[96]; snprintf(b,sizeof b,"R\tERR\tloadVoice devolvio %d\n",(int)lv); send_line(b); return 5; }
  std::string det;
  r=findrn("getVoiceDetails");
  if(r){ jstring s=((gs_t)r->f)(g_e,(jobject)g_self); if(s) det=((Obj*)s)->str; }
  send_line("R\tREADY\t"+det+"\n");
  pthread_t th; pthread_create(&th,nullptr,reader,nullptr);
  for(;;){
    Cmd c;
    if(!q_pop(c)) continue;
    if(c.name=="SPEAK"){
      RN* s=findrn("synthesizeText");
      jint x=s?((st_t)s->f)(g_e,(jobject)g_self,J_NewStringUTF(g_e,c.payload.c_str()),(jobject)g_cb):-99;
      char b[64]; snprintf(b,sizeof b,"R\tSPEAK\t%d\n",(int)x); send_line(b);
    } else if(c.name=="PING"){ send_line("R\tPONG\n");
    } else if(c.name=="PROP"){
      RN* s=findrn("setVoiceParam");
      jint x=-99;
      if(s && c.args.size()>=2){ typedef jint (*sv_t)(JNIEnv*,jobject,jstring,jint); x=((sv_t)s->f)(g_e,(jobject)g_self,J_NewStringUTF(g_e,c.args[0].c_str()),(jint)atoi(c.args[1].c_str())); }
      char b[96]; snprintf(b,sizeof b,"R\tPROP\t%d\n",(int)x); send_line(b);
    }
  }
  return 0;
}
