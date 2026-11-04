"""Inspect current installed APK's candidate audio decoder entry guards, without execution."""
import hashlib
import json
import sys
import zipfile
from pathlib import Path
from inspect_dex import Dex, instructions, uleb

EXPECTED = 'b6e6c4fed044416cd8c8b232ebda6bdca8b6c2626637a400ffc19d8800026d60'
HOOKS = {
 'Lr8/ne7;->z0(Lr8/fv3;Lr8/wf4;)I': (21, 270),
 'Lr8/ne7;->y0(Lr8/wf4;)Z': (6, 52),
 'Lr8/ne7;->Q(Lr8/fv3;Lr8/wf4;Z)Ljava/util/ArrayList;': (7, 72),
 'Lr8/ne7;->e0(Lr8/ge3;)Lr8/zs2;': (6, 29),
 'Lr8/nu0;->f(Ljava/lang/Exception;Lr8/wf4;ZI)Lr8/ex3;': (15, 52),
}

def inspect(apk):
    if hashlib.sha256(apk.read_bytes()).hexdigest() != EXPECTED: raise ValueError('wrong APK')
    with zipfile.ZipFile(apk) as z: d = Dex(z.read('classes.dex'))
    fields = []
    for pos in d.table(80, 8):
        fields.append(d.type(d.u16(pos)) + '->' + d.string(d.u32(pos+4)).decode('utf-8') + ':' + d.type(d.u16(pos+2)))
    result = {}
    for pos in d.table(96,32):
        offset = d.u32(pos+24)
        if not offset: continue
        counts = []
        for _ in range(4):
            n,offset = uleb(d.data,offset);counts.append(n)
        for _ in range(counts[0]+counts[1]):
            _,offset=uleb(d.data,offset);_,offset=uleb(d.data,offset)
        for count in counts[2:]:
            idx=0
            for _ in range(count):
                delta,offset=uleb(d.data,offset);flags,offset=uleb(d.data,offset);codepos,offset=uleb(d.data,offset);idx+=delta
                sig=d.methods[idx]
                if sig not in HOOKS: continue
                if sig in result or not codepos: raise ValueError('duplicate/missing code')
                regs=d.u16(codepos);tries=d.u16(codepos+6); size=d.u32(codepos+12); start=codepos+16
                d.bounds(start,size*2);code=d.data[start:start+size*2];units=memoryview(code).cast('H')
                if (regs,size)!=HOOKS[sig] or flags!=17 or (sig.startswith('Lr8/ne7;') and tries!=0): raise ValueError('candidate shape mismatch')
                if sig.startswith('Lr8/ne7;->e0('):
                    if len(units)<4 or units[0] != 0x5054 or units[2] != 0x001f or d.type(units[3]) != 'Lr8/wf4;':
                        raise ValueError('format holder entry layout mismatch')
                frefs=[];mrefs=[]
                for off,op,width,ref in instructions(code):
                    if 0x52<=op<=0x6d: frefs.append(fields[units[off+1]])
                    if ref and ref[0]=='method': mrefs.append(d.methods[ref[1]])
                result[sig]={'registers':regs,'code_units':size,'tries':tries,'code_sha256':hashlib.sha256(code).hexdigest(), 'fields':sorted(set(frefs)), 'selected_calls':sorted(x for x in set(mrefs) if x.startswith('Lr8/ne7;->'))}
    if set(result)!=set(HOOKS): raise ValueError('missing candidate')
    mime='Lr8/wf4;->o:Ljava/lang/String;'
    for sig in (list(HOOKS)[0],list(HOOKS)[2]):
        if mime not in result[sig]['fields']: raise ValueError('parsed MIME field missing')
    if 'Lr8/ne7;->D0(Lr8/wf4;)I' not in result[list(HOOKS)[1]]['selected_calls']: raise ValueError('bypass helper missing')
    if 'Lr8/ge3;->z:Ljava/lang/Object;' not in result[list(HOOKS)[3]]['fields']: raise ValueError('format holder field missing')
    return {'apk_sha256':EXPECTED,'hooks':result, 'runtime_verified':False}

if __name__ == '__main__': print(json.dumps(inspect(Path(sys.argv[1])),indent=2))
