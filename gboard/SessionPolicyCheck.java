import dev.purple.patches.gboard.SessionPolicy;
public class SessionPolicyCheck {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    private static SessionPolicy.TranslationCatalogReader catalog(String[] ids, String[][] efforts) {
        return new SessionPolicy.TranslationCatalogReader() {
            public int size() { return ids.length; }
            public String modelId(int index) { return ids[index]; }
            public String[] efforts(int index) { return efforts[index]; }
        };
    }
    private static void rejectsCatalog(SessionPolicy.TranslationCatalogReader reader) throws Exception {
        try { SessionPolicy.translationCatalog(reader); throw new AssertionError("Accepted invalid catalogue"); }
        catch (IllegalStateException expected) { }
    }
    private static void rejectsBridgeCatalog(java.lang.reflect.Method parse, Object input) throws Exception {
        try { parse.invoke(null,input); throw new AssertionError("Accepted invalid bridge catalogue"); }
        catch (java.lang.reflect.InvocationTargetException failure) {
            check(failure.getCause() instanceof IllegalStateException);
        }
    }
    /** Run the actual bridge catalogue adapter against controlled JVM-only JSON stubs. */
    private static void checkBridgeCatalog() throws Exception {
        java.nio.file.Path temp=java.nio.file.Files.createTempDirectory("purple-catalog-control-");
        try {
            java.nio.file.Path object=temp.resolve("org/json/JSONObject.java");
            java.nio.file.Path array=temp.resolve("org/json/JSONArray.java");
            java.nio.file.Path clock=temp.resolve("android/os/SystemClock.java");
            for (java.nio.file.Path path:new java.nio.file.Path[]{object,array,clock})
                java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.writeString(clock,
                    "package android.os; public class SystemClock { public static long elapsedRealtime() { return 0; } }\n");
            java.nio.file.Files.writeString(array, """
                    package org.json;
                    public class JSONArray {
                        private final java.util.List<Object> values=new java.util.ArrayList<>();
                        public JSONArray put(Object value) { values.add(value); return this; }
                        public int length() { return values.size(); }
                        public Object get(int index) { return values.get(index); }
                        public JSONObject optJSONObject(int index) {
                            Object value=values.get(index); return value instanceof JSONObject ? (JSONObject)value : null;
                        }
                        public JSONObject getJSONObject(int index) {
                            JSONObject value=optJSONObject(index);
                            if (value==null) throw new IllegalStateException("fixture JSON object");
                            return value;
                        }
                    }
                    """);
            java.nio.file.Files.writeString(object, """
                    package org.json;
                    public class JSONObject {
                        private final java.util.Map<String,Object> values=new java.util.HashMap<>();
                        public JSONObject() {}
                        public JSONObject(String ignored) {}
                        public JSONObject put(String key,Object value) { values.put(key,value); return this; }
                        public Object opt(String key) { return values.get(key); }
                        public String optString(String key) {
                            Object value=values.get(key); return value instanceof String ? (String)value : "";
                        }
                        public JSONArray getJSONArray(String key) {
                            Object value=values.get(key);
                            if (!(value instanceof JSONArray)) throw new IllegalStateException("fixture JSON array");
                            return (JSONArray)value;
                        }
                        public String getString(String key) { return (String)values.get(key); }
                        public boolean getBoolean(String key) { return (Boolean)values.get(key); }
                        public int getInt(String key) { return ((Number)values.get(key)).intValue(); }
                    }
                    """);
            javax.tools.JavaCompiler compiler=javax.tools.ToolProvider.getSystemJavaCompiler();
            check(compiler!=null);
            int status=compiler.run(null,null,null,"-classpath",System.getProperty("java.class.path"),
                    "-d",temp.toString(),object.toString(),array.toString(),clock.toString(),
                    "gboard/src/dev/purple/patches/gboard/BridgeClient.java");
            check(status==0);
            try (java.net.URLClassLoader loader=new java.net.URLClassLoader(
                    new java.net.URL[]{temp.toUri().toURL()}, SessionPolicyCheck.class.getClassLoader())) {
                Class<?> obj=loader.loadClass("org.json.JSONObject"), arr=loader.loadClass("org.json.JSONArray");
                Class<?> bridge=loader.loadClass("dev.purple.patches.gboard.BridgeClient");
                var parse=bridge.getDeclaredMethod("translationCatalog",obj); parse.setAccessible(true);
                var put=obj.getMethod("put",String.class,Object.class); var append=arr.getMethod("put",Object.class);
                Object other=obj.getConstructor().newInstance(); put.invoke(other,"id","other");
                put.invoke(other,"efforts","ignored malformed unrelated model");
                Object sol=obj.getConstructor().newInstance(); put.invoke(sol,"id","gpt-6.1-sol");
                Object efforts=arr.getConstructor().newInstance();
                for (String level:new String[]{"max","low","medium","max"}) append.invoke(efforts,level);
                put.invoke(sol,"efforts",efforts);
                Object models=arr.getConstructor().newInstance(); append.invoke(models,other); append.invoke(models,sol);
                Object response=obj.getConstructor().newInstance(); put.invoke(response,"models",models);
                check(java.util.Arrays.equals((String[])parse.invoke(null,response),new String[]{"medium","max"}));

                Object one=arr.getConstructor().newInstance(); append.invoke(one,"max"); put.invoke(sol,"efforts",one);
                check(java.util.Arrays.equals((String[])parse.invoke(null,response),new String[]{"max"}));
                Object duplicate=obj.getConstructor().newInstance(); put.invoke(duplicate,"id","gpt-6.1-sol");
                Object duplicateModels=arr.getConstructor().newInstance(); append.invoke(duplicateModels,sol); append.invoke(duplicateModels,duplicate);
                Object duplicateResponse=obj.getConstructor().newInstance(); put.invoke(duplicateResponse,"models",duplicateModels);
                rejectsBridgeCatalog(parse,duplicateResponse);
                Object missing=obj.getConstructor().newInstance(); put.invoke(missing,"models",arr.getConstructor().newInstance());
                rejectsBridgeCatalog(parse,missing);
                Object wrong=arr.getConstructor().newInstance(); append.invoke(wrong,7); put.invoke(sol,"efforts",wrong);
                rejectsBridgeCatalog(parse,response);
                rejectsBridgeCatalog(parse,obj.getConstructor().newInstance());
            }
        } finally {
            try (java.util.stream.Stream<java.nio.file.Path> paths=java.nio.file.Files.walk(temp)) {
                for (java.nio.file.Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList())
                    java.nio.file.Files.delete(path);
            }
        }
    }
    /** Run the actual permission activity against small JVM-only Android stubs. */
    private static void checkPermissionActivity() throws Exception {
        java.nio.file.Path temp=java.nio.file.Files.createTempDirectory("purple-permission-control-");
        try {
            java.nio.file.Path activity=temp.resolve("android/app/Activity.java");
            java.nio.file.Path intent=temp.resolve("android/content/Intent.java");
            java.nio.file.Path packages=temp.resolve("android/content/pm/PackageManager.java");
            java.nio.file.Path bundle=temp.resolve("android/os/Bundle.java");
            for (java.nio.file.Path path:new java.nio.file.Path[]{activity,intent,packages,bundle})
                java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.writeString(bundle, "package android.os; public class Bundle {}\n");
            java.nio.file.Files.writeString(packages,
                    "package android.content.pm; public class PackageManager { public static final int PERMISSION_GRANTED=0; }\n");
            java.nio.file.Files.writeString(intent, """
                    package android.content;
                    public class Intent {
                        private final java.util.Map<String,String> values=new java.util.HashMap<>();
                        public Intent putExtra(String key,String value) { values.put(key,value); return this; }
                        public String getStringExtra(String key) { return values.get(key); }
                    }
                    """);
            java.nio.file.Files.writeString(activity, """
                    package android.app;
                    public class Activity {
                        public android.content.Intent intent;
                        public boolean granted, failRequest, finished;
                        public String[] requested;
                        public int requestCode;
                        public void onCreate(android.os.Bundle state) {}
                        public android.content.Intent getIntent() { return intent; }
                        public int checkSelfPermission(String permission) { return granted ? 0 : -1; }
                        public void requestPermissions(String[] permissions,int code) {
                            if (failRequest) throw new IllegalStateException("fixture");
                            requested=permissions.clone(); requestCode=code;
                        }
                        public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {}
                        public void finish() { finished=true; }
                    }
                    """);
            javax.tools.JavaCompiler compiler=javax.tools.ToolProvider.getSystemJavaCompiler();
            check(compiler!=null);
            int status=compiler.run(null,null,null,"-classpath",System.getProperty("java.class.path"),
                    "-d",temp.toString(),activity.toString(),intent.toString(),packages.toString(),bundle.toString(),
                    "gboard/src/dev/purple/patches/gboard/PurplePermissionActivity.java");
            check(status==0);
            try (java.net.URLClassLoader loader=new java.net.URLClassLoader(
                    new java.net.URL[]{temp.toUri().toURL()}, SessionPolicyCheck.class.getClassLoader())) {
                Class<?> base=loader.loadClass("android.app.Activity");
                Class<?> intentType=loader.loadClass("android.content.Intent");
                Class<?> bundleType=loader.loadClass("android.os.Bundle");
                Class<?> actual=loader.loadClass("dev.purple.patches.gboard.PurplePermissionActivity");
                java.lang.reflect.Method create=actual.getMethod("onCreate",bundleType);
                java.lang.reflect.Method result=actual.getMethod("onRequestPermissionsResult",int.class,String[].class,int[].class);
                for (String scope:new String[]{"local-tools","microphone"}) {
                    Object instance=actual.getConstructor().newInstance();
                    Object input=intentType.getConstructor().newInstance();
                    intentType.getMethod("putExtra",String.class,String.class).invoke(input,
                            "dev.purple.patches.gboard.permission_scope",scope);
                    base.getField("intent").set(instance,input);
                    create.invoke(instance,(Object)null);
                    String[] requested=(String[])base.getField("requested").get(instance);
                    String expected=scope.equals("local-tools") ? "com.termux.permission.RUN_COMMAND" : "android.permission.RECORD_AUDIO";
                    check(requested!=null && requested.length==1 && requested[0].equals(expected));
                    check(base.getField("requestCode").getInt(instance)==7);
                    check(!base.getField("finished").getBoolean(instance));
                    result.invoke(instance,7,requested,new int[]{-1}); // Refusal closes the activity.
                    check(base.getField("finished").getBoolean(instance));
                }
                for (String scope:new String[]{null,"", "android.permission.CAMERA"}) {
                    Object instance=actual.getConstructor().newInstance();
                    if (scope!=null) {
                        Object input=intentType.getConstructor().newInstance();
                        intentType.getMethod("putExtra",String.class,String.class).invoke(input,
                                "dev.purple.patches.gboard.permission_scope",scope);
                        base.getField("intent").set(instance,input);
                    }
                    create.invoke(instance,(Object)null);
                    check(base.getField("requested").get(instance)==null && base.getField("finished").getBoolean(instance));
                }
                for (String field:new String[]{"granted","failRequest"}) {
                    Object instance=actual.getConstructor().newInstance();
                    Object input=intentType.getConstructor().newInstance();
                    intentType.getMethod("putExtra",String.class,String.class).invoke(input,
                            "dev.purple.patches.gboard.permission_scope","microphone");
                    base.getField("intent").set(instance,input); base.getField(field).setBoolean(instance,true);
                    create.invoke(instance,(Object)null);
                    check(base.getField("requested").get(instance)==null && base.getField("finished").getBoolean(instance));
                }
            }
        } finally {
            try (java.util.stream.Stream<java.nio.file.Path> paths=java.nio.file.Files.walk(temp)) {
                for (java.nio.file.Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList())
                    java.nio.file.Files.delete(path);
            }
        }
    }
    private static final class Reader implements SessionPolicy.EditorReader {
        SessionPolicy.EditorPosition first=new SessionPolicy.EditorPosition(3,5,0), last=first;
        CharSequence selected="ok", before="left", after="right";
        int positions;
        boolean mutateSelected, failBefore;
        public SessionPolicy.EditorPosition position() { return positions++==0 ? first : last; }
        public CharSequence selected() { return selected; }
        public CharSequence before() {
            if (failBefore) throw new IllegalStateException("editor unavailable");
            if (mutateSelected) ((StringBuilder)selected).append('!');
            return before;
        }
        public CharSequence after() { return after; }
    }
    public static void main(String[] args) throws Exception {
        for (int value:new int[]{0,1|128,1|144,1|224,2|16}) check(SessionPolicy.sensitive(value,0));
        check(SessionPolicy.sensitive(1,0x1000000));check(!SessionPolicy.sensitive(1,0));
        check(SessionPolicy.localEndpoint("http://127.0.0.1:12345"));
        for (String value:new String[]{"http://localhost:1","https://127.0.0.1:1","http://127.0.0.1:1/evil","http://127.0.0.1:1?q=x","http://user@127.0.0.1:1","http://127.0.0.1:65536","http://192.168.1.1:3"}) check(!SessionPolicy.localEndpoint(value));
        check(SessionPolicy.sameAnchor(1,1,"a","b","c","a","b","c"));
        check(!SessionPolicy.sameAnchor(1,2,"a","b","c","a","b","c"));
        check(!SessionPolicy.sameAnchor(1,1,"a","b","c","a","b","changed"));
        check(!SessionPolicy.sameAnchor(1,1,null,"b","c",null,"b","c"));
        check(SessionPolicy.validEditorRead(4,4,0,"left","right",null));
        check(SessionPolicy.validEditorRead(3,5,0,"left","right","\ud83d\ude00"));
        check(SessionPolicy.validEditorRead(5,3,0,"left","right","\ud83d\ude00"));
        check(!SessionPolicy.validEditorRead(3,5,0,"left","right",null));
        check(!SessionPolicy.validEditorRead(3,5,0,"left","right","x"));
        check(!SessionPolicy.validEditorRead(3,3,0,null,"right",null));
        check(!SessionPolicy.validEditorRead(3,3,0,"left",null,null));
        check(!SessionPolicy.validEditorRead(-1,-1,0,"left","right",null));
        check(!SessionPolicy.validEditorRead(3,3,-1,"left","right",null));
        check(!SessionPolicy.validEditorRead(1,1,Integer.MAX_VALUE,"left","right",null));
        Reader valid=new Reader(); SessionPolicy.EditorSnapshot snapshot=SessionPolicy.captureEditor(valid);
        check(snapshot!=null && snapshot.start==3 && snapshot.end==5 && snapshot.selected.equals("ok"));
        Reader moved=new Reader(); moved.last=new SessionPolicy.EditorPosition(4,6,0);
        check(SessionPolicy.captureEditor(moved)==null);
        Reader offsetMoved=new Reader(); offsetMoved.last=new SessionPolicy.EditorPosition(3,5,1);
        check(SessionPolicy.captureEditor(offsetMoved)==null);
        Reader missingPosition=new Reader(); missingPosition.last=null; check(SessionPolicy.captureEditor(missingPosition)==null);
        Reader failure=new Reader(); failure.failBefore=true; check(SessionPolicy.captureEditor(failure)==null);
        Reader nullAfter=new Reader(); nullAfter.after=null; check(SessionPolicy.captureEditor(nullAfter)==null);
        Reader missingSelection=new Reader(); missingSelection.selected=null; check(SessionPolicy.captureEditor(missingSelection)==null);
        Reader collapsed=new Reader(); collapsed.first=collapsed.last=new SessionPolicy.EditorPosition(4,4,0); collapsed.selected=null;
        check(SessionPolicy.captureEditor(collapsed).selected.equals(""));
        Reader mutable=new Reader(); mutable.selected=new StringBuilder("ok"); mutable.mutateSelected=true;
        SessionPolicy.EditorSnapshot frozen=SessionPolicy.captureEditor(mutable);
        check(frozen!=null && frozen.selected.equals("ok") && !frozen.selected.contentEquals(mutable.selected));
        Reader oversized=new Reader(); oversized.before="x".repeat(65); check(SessionPolicy.captureEditor(oversized)==null);
        Reader multibyte=new Reader(); multibyte.first=multibyte.last=new SessionPolicy.EditorPosition(0,3000,0);
        multibyte.selected="界".repeat(3000); check(SessionPolicy.captureEditor(multibyte)==null);
        check(SessionPolicy.bootstrapSuccess(0,-1));
        check(SessionPolicy.bridgeHealth(true,1,"memory-only-v1"));
        check(!SessionPolicy.bridgeHealth(false,1,"memory-only-v1"));
        check(!SessionPolicy.bridgeHealth(true,2,"memory-only-v1"));
        check(!SessionPolicy.bridgeHealth(true,"1","memory-only-v1"));
        check(!SessionPolicy.bridgeHealth(null,1,"memory-only-v1"));
        check(!SessionPolicy.bridgeHealth(true,1,"other"));
        Object owner=new Object();
        check(SessionPolicy.bootstrapOfferCurrent(1,1,owner,owner));
        check(!SessionPolicy.bootstrapOfferCurrent(1,2,owner,owner));
        check(!SessionPolicy.bootstrapOfferCurrent(1,1,owner,new Object()));
        check(!SessionPolicy.bootstrapOfferCurrent(1,1,null,null));
        var bootstrap=new SessionPolicy.BootstrapAttempt();
        check(!bootstrap.consume(1,true,"first",20));
        bootstrap.begin(1,"first",30);
        check(!bootstrap.consume(1,true,"wrong",20) && bootstrap.pending());
        check(bootstrap.consume(1,true,"first",30));
        check(!bootstrap.consume(1,true,"first",30) && !bootstrap.pending());
        bootstrap.begin(1,"first",30);
        check(!bootstrap.consume(1,false,"first",20) && !bootstrap.pending());
        bootstrap.begin(1,"first",30);
        check(!bootstrap.consume(2,true,"first",20) && !bootstrap.pending());
        bootstrap.begin(1,"first",30);
        check(!bootstrap.consume(1,true,"first",31) && !bootstrap.pending());
        bootstrap.begin(1,"first",30);
        bootstrap.begin(1,"second",30);
        check(!bootstrap.consume(1,true,"first",20) && bootstrap.pending());
        check(bootstrap.consume(1,true,"second",20));
        bootstrap.begin(1,"first",30); bootstrap.invalidate();
        check(!bootstrap.consume(1,true,"first",20));
        check(SessionPolicy.whisperPreflightSupported("full-v3-pin-engine-sha-v1"));
        for (String value : new String[]{null,"","full-v3-pin-engine-sha-v0","memory-only-v1"})
            check(!SessionPolicy.whisperPreflightSupported(value));
        check(SessionPolicy.translationEffortAllowed("medium"));
        check(SessionPolicy.translationEffortAllowed("max"));
        for (String value : new String[]{null,"","low","high","MAX","ultra"})
            check(!SessionPolicy.translationEffortAllowed(value));
        check(java.util.Arrays.equals(SessionPolicy.translationEffortChoices(null), new String[]{}));
        check(java.util.Arrays.equals(SessionPolicy.translationEffortChoices(new String[]{"low",null,"MAX"}), new String[]{}));
        check(java.util.Arrays.equals(SessionPolicy.translationEffortChoices(new String[]{"medium"}), new String[]{"medium"}));
        check(java.util.Arrays.equals(SessionPolicy.translationEffortChoices(new String[]{"max","max"}), new String[]{"max"}));
        check(java.util.Arrays.equals(SessionPolicy.translationEffortChoices(new String[]{"max","low","medium","max"}), new String[]{"medium","max"}));
        check(java.util.Arrays.equals(SessionPolicy.translationCatalog(catalog(
                new String[]{"other","gpt-6.1-sol"},new String[][]{{"max"},{"max","medium"}})),
                new String[]{"medium","max"}));
        check(java.util.Arrays.equals(SessionPolicy.translationCatalog(catalog(
                new String[]{"gpt-6.1-sol"},new String[][]{{"max"}})),new String[]{"max"}));
        rejectsCatalog(null);
        rejectsCatalog(catalog(new String[]{"other"},new String[][]{{"medium"}}));
        rejectsCatalog(catalog(new String[]{"gpt-6.1-sol"},new String[][]{{"high"}}));
        rejectsCatalog(catalog(new String[]{"gpt-6.1-sol","gpt-6.1-sol"},
                new String[][]{{"medium"},{"max"}}));
        check(SessionPolicy.previewDisposition(true,false,true,true)==SessionPolicy.PreviewDisposition.INSERTABLE);
        check(SessionPolicy.previewDisposition(true,false,true,false)==SessionPolicy.PreviewDisposition.COPY_ONLY);
        check(SessionPolicy.previewDisposition(false,false,true,true)==SessionPolicy.PreviewDisposition.REFUSE);
        check(SessionPolicy.previewDisposition(true,true,true,true)==SessionPolicy.PreviewDisposition.REFUSE);
        check(SessionPolicy.previewDisposition(true,false,false,false)==SessionPolicy.PreviewDisposition.REFUSE);
        String[] tools = SessionPolicy.permissionsFor(SessionPolicy.PERMISSION_LOCAL_TOOLS);
        check(tools.length == 1 && tools[0].equals("com.termux.permission.RUN_COMMAND"));
        String[] microphone = SessionPolicy.permissionsFor(SessionPolicy.PERMISSION_MICROPHONE);
        check(microphone.length == 1 && microphone[0].equals("android.permission.RECORD_AUDIO"));
        for (String value : new String[]{null,"","unknown","android.permission.CAMERA", "local-tools,microphone"})
            check(SessionPolicy.permissionsFor(value).length == 0);
        String companion = java.nio.file.Files.readString(java.nio.file.Path.of("companion/src/whisper.rs"));
        check(companion.contains("pub const PREFLIGHT_CAPABILITY: &str = \"" + SessionPolicy.WHISPER_PREFLIGHT + "\";"));
        for (Object err:new Object[]{null,0,1,"-1"}) check(!SessionPolicy.bootstrapSuccess(0,err));
        for (Object exit:new Object[]{null,1,-1,"0"}) check(!SessionPolicy.bootstrapSuccess(exit,-1));
        int[] sent={0};
        var success=new SessionPolicy.InsertionAttempt();
        check(success.attempt(() -> true, () -> { sent[0]++; return true; }) == SessionPolicy.InsertionOutcome.REQUEST_SENT);
        check(success.attempt(() -> true, () -> { sent[0]++; return true; }) == SessionPolicy.InsertionOutcome.ALREADY_ATTEMPTED);
        check(sent[0]==1);
        var changed=new SessionPolicy.InsertionAttempt();
        check(changed.attempt(() -> false, () -> { sent[0]++; return true; }) == SessionPolicy.InsertionOutcome.EDITOR_CHANGED);
        check(sent[0]==1);
        var missing=new SessionPolicy.InsertionAttempt();
        check(missing.attempt(() -> { throw new IllegalStateException("editor lost"); },
                () -> { sent[0]++; return true; }) == SessionPolicy.InsertionOutcome.EDITOR_CHANGED);
        check(sent[0]==1);
        var refused=new SessionPolicy.InsertionAttempt();
        check(refused.attempt(() -> true, () -> { sent[0]++; return false; }) == SessionPolicy.InsertionOutcome.UNKNOWN);
        check(refused.attempt(() -> true, () -> { sent[0]++; return true; }) == SessionPolicy.InsertionOutcome.ALREADY_ATTEMPTED);
        var uncertain=new SessionPolicy.InsertionAttempt();
        check(uncertain.attempt(() -> true, () -> { sent[0]++; throw new IllegalStateException("connection lost"); }) == SessionPolicy.InsertionOutcome.UNKNOWN);
        check(uncertain.attempt(() -> true, () -> { sent[0]++; return true; }) == SessionPolicy.InsertionOutcome.ALREADY_ATTEMPTED);
        check(sent[0]==3);
        var reentrant=new SessionPolicy.InsertionAttempt();
        check(reentrant.attempt(() -> true, () -> {
            sent[0]++;
            check(reentrant.attempt(() -> true, () -> { sent[0]++; return true; }) == SessionPolicy.InsertionOutcome.ALREADY_ATTEMPTED);
            return true;
        }) == SessionPolicy.InsertionOutcome.REQUEST_SENT);
        check(sent[0]==4);
        check(SessionPolicy.bounded("hello"));check(!SessionPolicy.bounded(" "));check(!SessionPolicy.bounded("界".repeat(3000)));
        checkPermissionActivity();
        checkBridgeCatalog();
        System.out.println("Editor privacy, localhost, stale selection and UTF-8 limits passed");
        System.out.println("One-shot insertion request and uncertain-result controls passed");
        System.out.println("Same-session changed-anchor copy-only preview controls passed");
        System.out.println("Bootstrap session, deadline, replacement and one-shot controls passed");
        System.out.println("Scoped permission activity JVM fixture passed");
        System.out.println("Actual bridge catalogue adapter with controlled JSON stubs passed");
    }
}
