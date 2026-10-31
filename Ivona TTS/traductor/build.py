import sys,re,struct,bisect,collections
sys.path.insert(0,'/home/claude/dyn/lift')
import lift
S='/tmp/claude-0/-home-claude/b9d058b9-b75d-5392-bcd2-a9313b879551/scratchpad/'
MINPCT=float(sys.argv[1]) if len(sys.argv)>1 else 0.05
MAXFN=int(sys.argv[2]) if len(sys.argv)>2 else 400
libs=[('voice','libvoice_es_conchita.so'),('eng','libttsivonavoice.so')]
perf=collections.defaultdict(collections.Counter)
for l in open(S+'prof.txt'):
    a=l.split()
    if a[0]=='TOTAL': continue
    perf[' '.join(a[:-2]).split('/')[-1]][int(a[-2],16)]+=int(a[-1])
T=sum(sum(c.values()) for c in perf.values())
PRE=open('/home/claude/dyn/lift/prelude.h').read()
out=[PRE]
tabla=[]
resumen=[]
for lid,fn in libs:
    lib=lift.Lib(S+'simapp/voces/conchita/lib/'+fn,fn)
    lift.PFX='fn_%s_'%lid
    base,off,size=lib.text
    entries=set()
    for a in range(base,base+size,4):
        w=lib.word(a)
        if (w>>28)!=15 and (w&0x0f000000)==0x0b000000:
            o=w&0xffffff
            if o&0x800000: o-=1<<24
            t=a+8+o*4
            if base<=t<base+size: entries.add(t)
        if (w&0xffffc000)==0xe92d4000: entries.add(a)
    el=sorted(entries)
    cnt=collections.Counter()
    for o,n in perf[fn].items():
        i=bisect.bisect_right(el,o)-1
        if i>=0: cnt[el[i]]+=n
    hot=[e for e,n in cnt.most_common() if 100*n/T>=MINPCT]
    queue=list(hot);seen=set(queue);ok={};fail={}
    while queue and len(ok)<MAXFN:
        e=queue.pop(0)
        try:
            insns,tails,calls=lift.lift_function(lib,e,entries)
            ok[e]=(insns,tails,calls)
            for t in list(calls)+list(tails):
                if t not in seen and base<=t<base+size and t not in lib.plt:
                    seen.add(t);queue.append(t)
        except lift.Unsup as ex:
            fail[e]=str(ex)
        except Exception as ex:
            fail[e]='error '+repr(ex)
    liftable=set(ok)
    bodies=[];names=[]
    for e,(insns,tails,calls) in ok.items():
        try:
            bodies.append(lift.emit_function(lib,e,insns,tails,calls,liftable,'%s%x'%(lift.PFX,e),entries))
            names.append(e)
        except lift.Unsup as ex:
            fail[e]='emit '+str(ex); liftable.discard(e)
    decl=['static void %s%x(u32*,u32);'%(lift.PFX,e) for e in ok]
    out+= decl+bodies
    for e in names: tabla.append((fn,e,'%s%x'%(lift.PFX,e)))
    cov=sum(cnt[e] for e in names)
    resumen.append('%s: funciones elevadas %d, fallidas %d, cobertura de muestras %.1f%% (de %.1f%% caliente)'%(fn,len(names),len(fail),100*cov/T,100*sum(n for e,n in cnt.items())/T))
    for e,r in list(fail.items())[:15]: resumen.append('   fallo 0x%x: %s (%.2f%%)'%(e,r,100*cnt.get(e,0)/T))
out.append('struct HotFn{const char* lib; u32 off; void(*fn)(u32*,u32);};')
out.append('extern "C" const HotFn hot_table[]={')
for fn,e,n in tabla: out.append('{"%s",0x%x,%s},'%(fn,e,n))
out.append('{0,0,0}};')
open('/home/claude/dyn/lift/hot_gen.cpp','w').write('\n'.join(out)+'\n')
print('\n'.join(resumen)); print('total funciones',len(tabla))
