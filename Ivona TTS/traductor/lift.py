import struct,sys,re,collections,subprocess,bisect

class Unsup(Exception): pass
PFX='fn_'

CEXP={0:'Z',1:'!Z',2:'C',3:'!C',4:'N',5:'!N',6:'V',7:'!V',8:'(C&&!Z)',9:'(!C||Z)',10:'(N==V)',11:'(N!=V)',12:'(!Z&&N==V)',13:'(Z||N!=V)',14:None}
def R(n): return 'r%d'%n

class Lib:
    def __init__(self,path,name):
        self.name=name; self.data=open(path,'rb').read()
        d=subprocess.run(['arm-linux-gnueabihf-readelf','-S','-W',path],capture_output=True,text=True).stdout
        self.text=None
        for l in d.splitlines():
            m=re.search(r'\]\s+\.text\s+\S+\s+([0-9a-f]+)\s+([0-9a-f]+)\s+([0-9a-f]+)',l)
            if m: self.text=(int(m.group(1),16),int(m.group(2),16),int(m.group(3),16))
        self.plt={}
        dis=subprocess.run(['arm-linux-gnueabihf-objdump','-d','-j','.plt',path],capture_output=True,text=True).stdout
        for m in re.finditer(r'^([0-9a-f]+) <([A-Za-z0-9_@.\-+]+)>:',dis,re.M):
            n=m.group(2)
            if n.endswith('@plt'): self.plt[int(m.group(1),16)]=n[:-4]
    def word(self,a):
        base,off,size=self.text
        if base<=a<base+size: return struct.unpack_from('<I',self.data,off+a-base)[0]
        raise Unsup('fuera de texto %x'%a)

def op2(w,a,needc):
    pre=[]
    if (w>>25)&1:
        imm=w&255; rot=((w>>8)&15)*2
        v=((imm>>rot)|(imm<<(32-rot)))&0xffffffff if rot else imm
        c=str((v>>31)&1) if rot else 'C'
        return pre,'0x%xu'%v,c
    rm=w&15; typ=(w>>5)&3
    base='(B+0x%xu)'%(a+8) if rm==15 else R(rm)
    if (w>>4)&1:
        if rm==15: raise Unsup('pc en desplazamiento por registro')
        rs=(w>>8)&15
        if rs==15: raise Unsup('rs pc')
        pre.append('u32 sh_c=C; u32 sh_v=%s;'%['sh_lsl(%s,%s,&sh_c)','sh_lsr(%s,%s,&sh_c)','sh_asr(%s,%s,&sh_c)','sh_ror(%s,%s,&sh_c)'][typ]%(base,R(rs)))
        return pre,'sh_v','sh_c'
    n=(w>>7)&31
    if typ==0:
        if n==0: return pre,base,'C'
        return pre,'(%s<<%d)'%(base,n),'((%s>>%d)&1)'%(base,32-n)
    if typ==1:
        if n==0: return pre,'0u','(%s>>31)'%base
        return pre,'(%s>>%d)'%(base,n),'((%s>>%d)&1)'%(base,n-1)
    if typ==2:
        if n==0: return pre,'((u32)((s32)%s>>31))'%base,'(%s>>31)'%base
        return pre,'((u32)((s32)%s>>%d))'%(base,n),'((%s>>%d)&1)'%(base,n-1)
    if n==0:
        return pre,'((C<<31)|(%s>>1))'%base,'(%s&1)'%base
    return pre,'ror32(%s,%d)'%(base,n),'((%s>>%d)&1)'%(base,n-1)

def regv(n,a): return '(B+0x%xu)'%(a+8) if n==15 else R(n)

def decode(lib,w,a,entries):
    cond=w>>28
    if cond==15:
        if (w&0xfd70f000)==0xf550f000: return ('plain',[])
        raise Unsup('incondicional %08x'%w)
    ce=CEXP[cond] if cond<15 else None
    if (w&0x0ffffff0)==0x012fff10:
        rm=w&15
        if rm==14: return ('ret',[],ce)
        raise Unsup('bx no lr')
    if (w&0x0ffffff0)==0x012fff30:
        return ('callind',[],ce,w&15)
    if (w&0x0fff0ff0)==0x016f0f10:
        return ('plain',['r%d=r%d?__builtin_clz(r%d):32u;'%((w>>12)&15,w&15,w&15)],ce)
    top=(w>>25)&7
    if top==0 and (w&0xf0)==0x90:
        if ((w>>23)&7)==0:
            rd=(w>>16)&15;rn=(w>>12)&15;rs=(w>>8)&15;rm=w&15;A=(w>>21)&1;S=(w>>20)&1
            if 15 in (rd,rn,rs,rm): raise Unsup('mul pc')
            e='r%d*r%d'%(rm,rs)
            if A: e+='+r%d'%rn
            st=['{u32 res=%s; r%d=res;'%(e,rd)]
            if S: st.append('N=res>>31;Z=(res==0);')
            st.append('}')
            return ('plain',st,ce)
        if ((w>>23)&7)==1:
            hi=(w>>16)&15;lo=(w>>12)&15;rs=(w>>8)&15;rm=w&15;sg=(w>>22)&1;A=(w>>21)&1;S=(w>>20)&1
            if 15 in (hi,lo,rs,rm): raise Unsup('mull pc')
            if sg: t='(u64)((s64)(s32)r%d*(s64)(s32)r%d)'%(rm,rs)
            else: t='((u64)r%d*(u64)r%d)'%(rm,rs)
            st=['{u64 t=%s;'%t]
            if A: st.append('t+=((u64)r%d<<32)|r%d;'%(hi,lo))
            st.append('r%d=(u32)t; r%d=(u32)(t>>32);'%(lo,hi))
            if S: st.append('N=(u32)(t>>63);Z=(t==0);')
            st.append('}')
            return ('plain',st,ce)
        raise Unsup('swp/otro')
    if top==0 and (w&0x90)==0x90 and (w&0x60)!=0:
        SH=(w>>5)&3;P=(w>>24)&1;U=(w>>23)&1;I=(w>>22)&1;W=(w>>21)&1;L=(w>>20)&1
        rn=(w>>16)&15;rd=(w>>12)&15
        if rd==15: raise Unsup('rd pc extra')
        off=str(((w>>8)&15)<<4|(w&15)) if I else R(w&15)
        if not I and (w&15)==15: raise Unsup('rm pc')
        sign='+' if U else '-'
        basev=regv(rn,a)
        st=['{u32 nb=%s%s%s; u32 ea=%s;'%(basev,sign,off,'nb' if P else basev)]
        wb=(W or not P)
        if wb and rn==15: raise Unsup('wb pc')
        if L and wb: st.append('r%d=nb;'%rn)
        if SH==1:
            st.append('r%d=L16(ea);'%rd if L else 'S16(ea,r%d);'%rd)
        elif SH==2:
            if L: st.append('r%d=(u32)(s32)(int8_t)L8(ea);'%rd)
            else:
                if rd&1 or rd==14: raise Unsup('ldrd')
                st.append('r%d=L32(ea);r%d=L32(ea+4);'%(rd,rd+1))
        else:
            if L: st.append('r%d=(u32)(s32)(int16_t)L16(ea);'%rd)
            else:
                if rd&1 or rd==14: raise Unsup('strd')
                st.append('S32(ea,r%d);S32(ea+4,r%d);'%(rd,rd+1))
        if (not L) and wb: st.append('r%d=nb;'%rn)
        st.append('}')
        return ('plain',st,ce)
    if top==0 or top==1:
        if top==0 and (w&0x90)==0x90: raise Unsup('extra/swp')
        op=(w>>21)&15;S=(w>>20)&1;rn=(w>>16)&15;rd=(w>>12)&15
        if (op&12)==8 and not S: raise Unsup('misc dp')
        test=(op&12)==8
        logical=op in (0,1,8,9,12,13,14,15)
        pre,v,c=op2(w,a,S and logical)
        if rd==15 and not test:
            if op==13 and (w&0xfff)==14 and not S and not (w>>25)&1: return ('ret',[],ce)
            raise Unsup('rd pc dp')
        st=['{']+pre
        st.append('u32 a_=%s; u32 b_=%s;'%(regv(rn,a) if op not in (13,15) else '0u',v))
        arith={2:('a_','~b_','1u'),3:('b_','~a_','1u'),4:('a_','b_','0u'),5:('a_','b_','C'),6:('a_','~b_','C'),7:('b_','~a_','C'),10:('a_','~b_','1u'),11:('a_','b_','0u')}
        if op in arith:
            x,y,ci=arith[op]
            st.append('u64 t_=(u64)(%s)+(u64)(%s)+(u64)(%s); u32 res=(u32)t_;'%(x,y,ci))
            if S or test:
                st.append('{u32 xx=%s,yy=%s; C=(u32)(t_>>32)&1; V=((~(xx^yy))&(xx^res))>>31;}'%(x,y))
        else:
            e={0:'a_&b_',1:'a_^b_',8:'a_&b_',9:'a_^b_',12:'a_|b_',13:'b_',14:'a_&~b_',15:'~b_'}[op]
            st.append('u32 res=%s;'%e)
            if S or test: st.append('C=%s;'%c)
        if S or test: st.append('N=res>>31;Z=(res==0);')
        if not test: st.append('r%d=res;'%rd)
        st.append('}')
        return ('plain',st,ce)
    if top==2 or top==3:
        I=(w>>25)&1;P=(w>>24)&1;U=(w>>23)&1;B=(w>>22)&1;W=(w>>21)&1;L=(w>>20)&1
        rn=(w>>16)&15;rd=(w>>12)&15
        sign='+' if U else '-'
        if I:
            if (w>>4)&1: raise Unsup('media')
            pre,v,c=op2((w&~(1<<25))|0,a,False)
            if (w&15)==15: raise Unsup('rm pc')
            off=v
        else: off='%du'%(w&0xfff)
        basev=regv(rn,a)
        if rd==15:
            if L and not B and rn==13 and not P and U and not I and (w&0xfff)>=4 and ((w&0xfff)%4)==0: return ('ret_pop',['r13=r13+%du;'%(w&0xfff)],ce)
            raise Unsup('ldr pc')
        wb=(W or not P)
        if wb and rn==15: raise Unsup('wb pc')
        st=['{']
        if I: st+=pre
        st.append('u32 nb=%s%s%s; u32 ea=%s;'%(basev,sign,off,'nb' if P else basev))
        if L and wb: st.append('r%d=nb;'%rn)
        if L: st.append('r%d=%s;'%(rd,'L8(ea)' if B else 'L32(ea)'))
        else: st.append('%s;'%('S8(ea,r%d)'%rd if B else 'S32(ea,r%d)'%rd))
        if (not L) and wb: st.append('r%d=nb;'%rn)
        st.append('}')
        return ('plain',st,ce)
    if top==4:
        P=(w>>24)&1;U=(w>>23)&1;S=(w>>22)&1;W=(w>>21)&1;L=(w>>20)&1;rn=(w>>16)&15;lst=w&0xffff
        if S: raise Unsup('ldm ^')
        if rn==15: raise Unsup('ldm pc base')
        regs=[i for i in range(16) if lst>>i&1]
        n=len(regs)
        if n==0: raise Unsup('lista vacia')
        if U: start='r%d%s'%(rn,'+4' if P else '')
        else: start='r%d-%d%s'%(rn,4*n,'' if P else '')
        if not U and not P: start='r%d-%d'%(rn,4*n-4)
        newb='r%d%s%d'%(rn,'+' if U else '-',4*n)
        st=['{u32 ea=(u32)(%s);'%start]
        pcin=False
        for i,r in enumerate(regs):
            if L:
                if r==15: pcin=True; continue
                st.append('r%d=L32(ea+%d);'%(r,4*i))
            else:
                if r==15: raise Unsup('stm pc')
                st.append('S32(ea+%d,r%d);'%(4*i,r))
        if W:
            if L and rn in regs: pass
            else: st.append('r%d=%s;'%(rn,newb))
        st.append('}')
        if pcin: return ('ret_pop',st,ce)
        return ('plain',st,ce)
    if top==5:
        off=w&0xffffff
        if off&0x800000: off-=1<<24
        t=a+8+off*4
        if (w>>24)&1: return ('call',[],ce,t)
        return ('jump',[],ce,t)
    raise Unsup('clase %08x'%w)

def lift_function(lib,entry,entries):
    insns={}
    work=[entry];tails=set();calls=set()
    while work:
        a=work.pop()
        while True:
            if a in insns: break
            w=lib.word(a)
            d=decode(lib,w,a,entries)
            insns[a]=(w,d)
            k=d[0]
            if k=='jump':
                t=d[3]
                if t!=entry and t in entries and t not in insns: tails.add(t)
                elif t not in insns: work.append(t)
                if d[2] is None: break
            elif k=='call':
                calls.add(d[3])
            elif k in ('ret','ret_pop'):
                if d[2] is None: break
            a+=4
            if a>=lib.text[0]+lib.text[2]: raise Unsup('sin fin')
    return insns,tails,calls

INTR={'strcmp','strlen','memcpy','memset','memmove','memcmp','strncmp','strcpy','strncpy','strcat','strchr','strrchr'}

def emit_function(lib,entry,insns,tails,calls,liftable,name,entries_g=frozenset()):
    out=[]
    out.append('static void %s(u32* R,u32 B){'%name)
    out.append(' u32 '+','.join('r%d=R[%d]'%(i,i) for i in range(15))+';')
    out.append(' u32 N=(HOT_F>>31)&1,Z=(HOT_F>>30)&1,C=(HOT_F>>29)&1,V=(HOT_F>>28)&1; (void)N;(void)Z;(void)C;(void)V;')
    labels=set([entry])
    out.append(' goto L_%x;'%entry)
    for a,(w,d) in insns.items():
        if d[0]=='jump' and d[3] in insns: labels.add(d[3])
    store=' '.join('R[%d]=r%d;'%(i,i) for i in range(15))+' HOT_F=(N<<31)|(Z<<30)|(C<<29)|(V<<28);'
    load=' '.join('r%d=R[%d];'%(i,i) for i in range(15))+' N=(HOT_F>>31)&1;Z=(HOT_F>>30)&1;C=(HOT_F>>29)&1;V=(HOT_F>>28)&1;'
    for a in sorted(insns):
        w,d=insns[a]
        if a in labels: out.append('L_%x:'%a)
        k=d[0];ce=d[2] if len(d)>2 else None
        body=[]
        if k=='plain': body=d[1]
        elif k=='ret': body=['goto L_ret;']
        elif k=='ret_pop': body=d[1]+['goto L_ret;']
        elif k=='jump':
            t=d[3]
            if t in insns and not (t!=entry and t in entries_g): body=['goto L_%x;'%t]
            else:
                if t in liftable: body=['{%s %s%x(R,B); return;}'%(store,PFX,t)]
                else: body=['{%s call_guest(R,B+0x%xu); return;}'%(store,t)]
        elif k=='call':
            t=d[3]
            ret=a+4
            pre='r14=B+0x%xu; %s '%(ret,store)
            if t in lib.plt and lib.plt[t] in INTR:
                body=['{u32 rv=intr_%s(r0,r1,r2); r0=rv; r14=B+0x%xu;}'%(lib.plt[t],ret)]
            elif t in liftable:
                body=['{%s %s%x(R,B); %s}'%(pre,PFX,t,load)]
            else:
                body=['{%s call_guest(R,B+0x%xu); %s}'%(pre,t,load)]
        elif k=='callind':
            rm=d[3]
            if rm==15 : raise Unsup('blx pc')
            ret=a+4
            body=['{u32 tgt=r%d; r14=B+0x%xu; %s call_guest(R,tgt); %s}'%(rm,ret,store,load)]
        else: raise Unsup('tipo')
        line=' '.join(body)
        if ce: line='if(%s){%s}'%(ce,line)
        else: line='{%s}'%line
        out.append(' '+line)
    out.append('L_ret: '+store+' return;')
    out.append('}')
    return '\n'.join(out)
