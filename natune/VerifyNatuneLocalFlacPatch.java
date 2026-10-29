import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.android.tools.smali.dexlib2.immutable.instruction.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;
import java.io.File;
import java.util.*;

/** Independent exact APK guard admission, then complete original program comparison. */
public final class VerifyNatuneLocalFlacPatch {
    record Hook(String key, int registers, int format, int prefix, int refusal) {}
    static final List<Hook> HOOKS = List.of(
        new Hook("Lr8/ge7;->z0(Lr8/hv3;Lr8/yf4;)I",21,20,8,1),
        new Hook("Lr8/ge7;->y0(Lr8/yf4;)Z",6,5,8,0),
        new Hook("Lr8/ge7;->Q(Lr8/hv3;Lr8/yf4;Z)Ljava/util/ArrayList;",7,5,9,-1),
        new Hook("Lr8/ge7;->e0(Lr8/ie3;)Lr8/bt2;",6,5,15,-2)
    );
    static void need(boolean ok, String why) { VerifyNatuneMobilePatch.need(ok,why); }
    static void op(Instruction i, Opcode opcode) { need(i.getOpcode()==opcode,"Wrong guard opcode"); }
    static void reg(Instruction i,int a) { need(i instanceof OneRegisterInstruction r && r.getRegisterA()==a,"Wrong guard register"); }
    static String ref(Instruction i) { return ((ReferenceInstruction)i).getReference().toString(); }
    static void call(Instruction i,Opcode code,String method,int... registers) {
        op(i,code);need(ref(i).equals(method),"Wrong guard callee");
        need(i instanceof FiveRegisterInstruction,"Expected short invoke");
        var r=(FiveRegisterInstruction)i;int[] actual={r.getRegisterC(),r.getRegisterD(),r.getRegisterE(),r.getRegisterF(),r.getRegisterG()};
        need(r.getRegisterCount()==registers.length,"Wrong guard argument count");
        for(int n=0;n<registers.length;n++)need(actual[n]==registers[n],"Wrong guard argument");
    }
    static void guard(List<Instruction> x,Hook h) {
        need(x.size()>h.prefix,"Missing original method body");
        if(h.refusal==-2) { lifecycleGuard(x,h); return; }
        op(x.get(0),Opcode.MOVE_OBJECT_FROM16);reg(x.get(0),0);
        need(((TwoRegisterInstruction)x.get(0)).getRegisterB()==h.format,"Wrong Format parameter");
        op(x.get(1),Opcode.IGET_OBJECT);reg(x.get(1),0);
        need(((TwoRegisterInstruction)x.get(1)).getRegisterB()==0 && ref(x.get(1)).equals("Lr8/yf4;->o:Ljava/lang/String;"),"Wrong parsed MIME field");
        need(x.get(2).getOpcode()==Opcode.CONST_STRING || x.get(2).getOpcode()==Opcode.CONST_STRING_JUMBO,"Wrong MIME constant opcode");reg(x.get(2),1);
        need(((StringReference)((ReferenceInstruction)x.get(2)).getReference()).getString().equals("audio/flac"),"Wrong admitted codec");
        call(x.get(3),Opcode.INVOKE_VIRTUAL,"Ljava/lang/String;->equals(Ljava/lang/Object;)Z",1,0);
        op(x.get(4),Opcode.MOVE_RESULT);reg(x.get(4),0);
        op(x.get(5),Opcode.IF_NEZ);reg(x.get(5),0);
        int jump=0;for(int n=5;n<h.prefix;n++)jump+=x.get(n).getCodeUnits();
        need(((OffsetInstruction)x.get(5)).getCodeOffset()==jump,"FLAC branch must enter exact original first instruction");
        if(h.refusal<0) {
            op(x.get(6),Opcode.NEW_INSTANCE);reg(x.get(6),0);need(ref(x.get(6)).equals("Ljava/util/ArrayList;"),"Wrong covariant refusal type");
            call(x.get(7),Opcode.INVOKE_DIRECT,"Ljava/util/ArrayList;-><init>()V",0);
            op(x.get(8),Opcode.RETURN_OBJECT);reg(x.get(8),0);
        } else {
            op(x.get(6),Opcode.CONST_4);reg(x.get(6),0);
            need(((WideLiteralInstruction)x.get(6)).getWideLiteral()==h.refusal,"Wrong refusal value");
            op(x.get(7),Opcode.RETURN);reg(x.get(7),0);
        }
    }
    static void text(Instruction i,int register,String text) {
        need(i.getOpcode()==Opcode.CONST_STRING || i.getOpcode()==Opcode.CONST_STRING_JUMBO,"Wrong string opcode");reg(i,register);
        need(((StringReference)((ReferenceInstruction)i).getReference()).getString().equals(text),"Wrong guard text");
    }
    static void lifecycleGuard(List<Instruction> x,Hook h) {
        op(x.get(0),Opcode.IGET_OBJECT);reg(x.get(0),0);
        need(((TwoRegisterInstruction)x.get(0)).getRegisterB()==5 && ref(x.get(0)).equals("Lr8/ie3;->z:Ljava/lang/Object;"),"Wrong original FormatHolder");
        op(x.get(1),Opcode.CHECK_CAST);reg(x.get(1),0);need(ref(x.get(1)).equals("Lr8/yf4;"),"Wrong Format cast");
        op(x.get(2),Opcode.IGET_OBJECT);reg(x.get(2),1);
        need(((TwoRegisterInstruction)x.get(2)).getRegisterB()==0 && ref(x.get(2)).equals("Lr8/yf4;->o:Ljava/lang/String;"),"Wrong lifecycle parsed MIME");
        text(x.get(3),2,"audio/flac");
        call(x.get(4),Opcode.INVOKE_VIRTUAL,"Ljava/lang/String;->equals(Ljava/lang/Object;)Z",2,1);
        op(x.get(5),Opcode.MOVE_RESULT);reg(x.get(5),1);
        op(x.get(6),Opcode.IF_NEZ);reg(x.get(6),1);
        int jump=0;for(int n=6;n<h.prefix;n++)jump+=x.get(n).getCodeUnits();
        need(((OffsetInstruction)x.get(6)).getCodeOffset()==jump,"Lifecycle branch skips original entry");
        op(x.get(7),Opcode.NEW_INSTANCE);reg(x.get(7),1);need(ref(x.get(7)).equals("Ljava/io/IOException;"),"Wrong error cause type");
        text(x.get(8),2,"FLAC audio is required for local playback.");
        call(x.get(9),Opcode.INVOKE_DIRECT,"Ljava/io/IOException;-><init>(Ljava/lang/String;)V",1,2);
        op(x.get(10),Opcode.CONST_4);reg(x.get(10),2);need(((WideLiteralInstruction)x.get(10)).getWideLiteral()==0,"Recoverable policy error refused");
        op(x.get(11),Opcode.CONST_16);reg(x.get(11),3);need(((WideLiteralInstruction)x.get(11)).getWideLiteral()==4005,"Wrong original error code");
        call(x.get(12),Opcode.INVOKE_VIRTUAL,"Lr8/mu0;->f(Ljava/lang/Exception;Lr8/yf4;ZI)Lr8/gx3;",4,1,0,2,3);
        op(x.get(13),Opcode.MOVE_RESULT_OBJECT);reg(x.get(13),0);
        op(x.get(14),Opcode.THROW);reg(x.get(14),0);
    }
    record RendererFailure(Object renderer,Object format,Object cause,boolean recoverable,int code) {}
    static Object lifecycleInterpret(List<Instruction> x,Hook h,Map<String,Object> format,Object renderer) {
        Object[] r=new Object[6];r[4]=renderer;r[5]=Map.of("z",format);Object result=null;
        for(int pc=0;pc<h.prefix;pc++) {
            var i=x.get(pc);int a=i instanceof OneRegisterInstruction q?q.getRegisterA():-1;
            switch(i.getOpcode()) {
                case IGET_OBJECT -> r[a]=((Map<?,?>)r[((TwoRegisterInstruction)i).getRegisterB()]).get(((FieldReference)((ReferenceInstruction)i).getReference()).getName());
                case CHECK_CAST -> need(r[a] instanceof Map,"Invalid fixture Format");
                case CONST_STRING,CONST_STRING_JUMBO -> r[a]=((StringReference)((ReferenceInstruction)i).getReference()).getString();
                case INVOKE_VIRTUAL -> {
                    var q=(FiveRegisterInstruction)i;
                    if(ref(i).startsWith("Ljava/lang/String;"))result=((String)r[q.getRegisterC()]).equals(r[q.getRegisterD()]);
                    else result=new RendererFailure(r[q.getRegisterC()],r[q.getRegisterE()],r[q.getRegisterD()],((Number)r[q.getRegisterF()]).intValue()!=0,((Number)r[q.getRegisterG()]).intValue());
                }
                case MOVE_RESULT,MOVE_RESULT_OBJECT -> r[a]=result;
                case IF_NEZ -> {if(Boolean.TRUE.equals(r[a]))return "ORIGINAL";}
                case NEW_INSTANCE -> r[a]=new Object();
                case INVOKE_DIRECT -> {var q=(FiveRegisterInstruction)i;r[q.getRegisterC()]=new java.io.IOException((String)r[q.getRegisterD()]);}
                case CONST_4,CONST_16 -> r[a]=((WideLiteralInstruction)i).getWideLiteral();
                case THROW -> {return r[a];}
                default -> throw new IllegalStateException("Unsupported lifecycle prefix instruction");
            }
        }
        throw new IllegalStateException("Lifecycle guard fell through");
    }
    static int lifecycleControls(List<Instruction> x,Hook h) {
        int passed=0;Object renderer=new Object();
        // Repeated transitions, including return to FLAC, retain each actual
        // input object; no sticky decision for a previous track is permitted.
        for(String mime:Arrays.asList("audio/flac","audio/aac","audio/flac","audio/opus","audio/raw",null,"audio/mpeg","audio/FLAC")) {
            var format=new HashMap<String,Object>();format.put("o",mime);
            Object result=lifecycleInterpret(x,h,format,renderer);
            if("audio/flac".equals(mime))need("ORIGINAL".equals(result),"FLAC lifecycle was intercepted");
            else {
                need(result instanceof RendererFailure,"Lossy lifecycle did not fail");var f=(RendererFailure)result;
                need(f.renderer==renderer && f.format==format && !f.recoverable && f.code==4005 &&
                    f.cause instanceof java.io.IOException e && e.getMessage().equals("FLAC audio is required for local playback."),"Original renderer/format/error binding lost");
            }
            passed++;
        }
        var changed=new ArrayList<Instruction>(x);changed.set(0,new ImmutableInstruction22c(Opcode.IGET_OBJECT,0,5,new ImmutableFieldReference("Lr8/ie3;","wrong","Ljava/lang/Object;")));refused(changed,h);passed++;
        changed=new ArrayList<>(x);changed.set(3,new ImmutableInstruction21c(Opcode.CONST_STRING,2,new ImmutableStringReference("audio/aac")));refused(changed,h);passed++;
        changed=new ArrayList<>(x);changed.set(6,new ImmutableInstruction21t(Opcode.IF_NEZ,1,2));refused(changed,h);passed++;
        changed=new ArrayList<>(x);changed.set(10,new ImmutableInstruction11n(Opcode.CONST_4,2,1));refused(changed,h);passed++;
        changed=new ArrayList<>(x);changed.set(11,new ImmutableInstruction21s(Opcode.CONST_16,3,4002));refused(changed,h);passed++;
        changed=new ArrayList<>(x);changed.set(12,new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL,5,4,1,5,2,3,new ImmutableMethodReference("Lr8/mu0;","f",List.of("Ljava/lang/Exception;","Lr8/yf4;","Z","I"),"Lr8/gx3;")));refused(changed,h);passed++;
        changed=new ArrayList<>(x);changed.set(14,new ImmutableInstruction11x(Opcode.RETURN_OBJECT,0));refused(changed,h);passed++;
        return passed;
    }
    // Tiny interpreter of the ACTUAL emitted prefix. It stops before the old
    // body, so this proves dispatch/refusal, not an Android decoder's behavior.
    static Object interpret(List<Instruction> x,Hook h,String mime) {
        Object[] r=new Object[h.registers];r[h.format]=new HashMap<String,Object>();
        ((Map<String,Object>)r[h.format]).put("o",mime);
        Object result=null;int pc=0,steps=0;
        while(pc<h.prefix && ++steps<24) {
            var i=x.get(pc);int a=i instanceof OneRegisterInstruction q?q.getRegisterA():-1;
            switch(i.getOpcode()) {
                case MOVE_OBJECT_FROM16 -> r[a]=r[((TwoRegisterInstruction)i).getRegisterB()];
                case IGET_OBJECT -> r[a]=((Map<?,?>)r[((TwoRegisterInstruction)i).getRegisterB()]).get(((FieldReference)((ReferenceInstruction)i).getReference()).getName());
                case CONST_STRING,CONST_STRING_JUMBO -> r[a]=((StringReference)((ReferenceInstruction)i).getReference()).getString();
                case INVOKE_VIRTUAL -> {var q=(FiveRegisterInstruction)i;result=((String)r[q.getRegisterC()]).equals(r[q.getRegisterD()]);}
                case MOVE_RESULT -> r[a]=result;
                case IF_NEZ -> {if(Boolean.TRUE.equals(r[a]))return "ORIGINAL";}
                case CONST_4 -> r[a]=((WideLiteralInstruction)i).getWideLiteral();
                case NEW_INSTANCE -> r[a]=new ArrayList<>();
                case INVOKE_DIRECT -> { /* validated ArrayList zero-argument constructor */ }
                case RETURN,RETURN_OBJECT -> {return r[a];}
                default -> throw new IllegalStateException("Unsupported guard interpreter opcode");
            }
            pc++;
        }
        throw new IllegalStateException("Guard did not return/enter original");
    }
    static void refused(List<Instruction> x,Hook h) {
        boolean rejected=false;try{guard(x,h);}catch(IllegalStateException expected){rejected=true;}
        need(rejected,"Corrupt guard was accepted");
    }
    static int controls(List<Instruction> x,Hook h) {
        if(h.refusal==-2)return lifecycleControls(x,h);
        int count=0;
        for(String mime:Arrays.asList("audio/flac","audio/aac","audio/mpeg","audio/opus","audio/raw","video/avc","audio/FLAC",null)) {
            Object actual=interpret(x,h,mime);
            boolean expected="audio/flac".equals(mime);
            need(expected ? "ORIGINAL".equals(actual) : h.refusal<0 ? actual instanceof ArrayList<?> list && list.isEmpty() : Objects.equals(actual,(long)h.refusal),"Wrong guard decision");count++;
        }
        var changed=new ArrayList<Instruction>(x);
        changed.set(2,new ImmutableInstruction21c(Opcode.CONST_STRING,1,new ImmutableStringReference("audio/aac")));refused(changed,h);count++;
        changed=new ArrayList<>(x);changed.set(1,new ImmutableInstruction22c(Opcode.IGET_OBJECT,0,0,new ImmutableFieldReference("Lr8/yf4;","n","Ljava/lang/String;")));refused(changed,h);count++;
        changed=new ArrayList<>(x);changed.set(5,new ImmutableInstruction21t(Opcode.IF_NEZ,0,2));refused(changed,h);count++;
        changed=new ArrayList<>(x);changed.set(0,new ImmutableInstruction22x(Opcode.MOVE_OBJECT_FROM16,0,h.format-1));refused(changed,h);count++;
        changed=new ArrayList<>(x);
        if(h.refusal<0)changed.set(6,new ImmutableInstruction21c(Opcode.NEW_INSTANCE,0,new ImmutableTypeReference("Ljava/lang/Object;")));
        else changed.set(6,new ImmutableInstruction11n(Opcode.CONST_4,0,1-h.refusal));
        refused(changed,h);count++;
        return count;
    }
    public static void main(String[] args) throws Exception {
        need(args.length==2,"Expected original and patched APK");
        var selected=new HashMap<String,Method>();var dex=DexFileFactory.loadDexContainer(new File(args[1]),Opcodes.getDefault());
        for(String name:dex.getDexEntryNames())for(var c:dex.getEntry(name).getDexFile().getClasses())for(var m:c.getMethods())
            if(HOOKS.stream().anyMatch(h->h.key.equals(VerifyNatuneMobilePatch.signature(m))))need(selected.put(VerifyNatuneMobilePatch.signature(m),m)==null,"Duplicate guard method");
        var prefixes=new HashMap<String,Integer>();int passed=0;
        for(var hook:HOOKS) {
            var m=selected.get(hook.key);need(m!=null && m.getAccessFlags()==17,"Missing/changed guarded method");
            var body=m.getImplementation();need(body!=null && body.getRegisterCount()==hook.registers && body.getTryBlocks().isEmpty(),"Guarded method shape changed");
            var x=new ArrayList<Instruction>();body.getInstructions().forEach(x::add);guard(x,hook);passed+=controls(x,hook);prefixes.put(hook.key,hook.prefix);
        }
        VerifyNatuneMobilePatch.initializerControls();
        var expected=VerifyNatuneMobilePatch.snapshot(new File(args[0]),true);
        var actual=VerifyNatuneMobilePatch.snapshot(new File(args[1]),false,prefixes);
        VerifyNatuneMobilePatch.compare(expected,actual,args,"Verified five mobile-data overrides and four exact local FLAC guards; every original remaining method/body/resource preserved.");
        System.out.println("LOCAL_FLAC_GUARD_CONTROLS passed="+passed+" actual_patched_bytecode=true android_decoder_acceptance=false full_download_admission=false");
    }
}
