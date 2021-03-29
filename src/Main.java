import java.io.*;
import java.util.*;
import java.lang.Throwable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import soot.*;
import soot.jimple.*;
import soot.options.Options;
import soot.util.JasminOutputStream;

public class Main {
    private static boolean initial = false;
    private static boolean vectorInitial = false;
    private static String root = "./out/production/classming/";
    private static String generated = "./sootOutput/";
    private static String dependencies = "";
    private static String target = "./target/";
    private static final String LOG_PREVIOUS = " **** Executed Line: **** ";
    public static final String MAIN_SIGN = "void main(java.lang.String[])";
    private static final String LIMITED_STMT = ":= @";
    private static String extraCp = "";//extra class path
    public static boolean forceResolveFailed = false;






    public static void setGenerated(String classPath) {
        Main.generated = classPath;
    }

    public static String getGenerated() {
        return generated;
    }
    public static void setExtraCp(String extraCp) {
        Main.extraCp = extraCp;
    }

    public static String getDependencies() {
        return dependencies;
    }

    public static void setDependencies(String dependencies) {
        Main.dependencies = dependencies;
    }

    public static String generateClassPath(List<String> newPathes) {
        String pathSep = File.pathSeparator;//这个符号是:
        StringBuilder path = new StringBuilder(System.getProperty("java.class.path"));
        for (String classPath :  newPathes) {
            path.insert(0, classPath + pathSep);
        }
        return path.toString();
    }

    public static void initialize(String[] args) {
        List<String> pathes = new ArrayList<>();
        pathes.add(generated);
        String[] dependencyArr = dependencies.split(File.pathSeparator);
        pathes.addAll(Arrays.asList(dependencyArr));
        if(!extraCp.equals("")){
            pathes.addAll(Arrays.asList(extraCp.substring(1).split(File.pathSeparator)));
        }
        Options.v().set_soot_classpath(generateClassPath(pathes));
        Scene.v().loadNecessaryClasses();
        Options.v().set_keep_line_number(true);
        SootClass c = Scene.v().forceResolve("Print", SootClass.BODIES);
        List<SootMethod> d = c.getMethods();
        for (SootMethod method : d) {
            method.retrieveActiveBody();
        }
    }


    public static SootClass loadTargetClass(String className) {//将class加载为sootclass
        SootClass c = Scene.v().forceResolve(className, SootClass.BODIES);
        List<SootMethod> d = c.getMethods();
        for (SootMethod method : d) {
            Body body = method.retrieveActiveBody();
            UnitPatchingChain units = body.getUnits();
            if (!initial) {//是否已经被初始化
                injectPathCount(units, method.getSignature());
            }
        }
        initial = true;
        return c;
    }

    public static void injectPathCount(UnitPatchingChain units,String signature) {//对每行原始代码进行修改，添加所在方法的signature，并且打上标记log_previous
        List<Stmt> targetStatements = new ArrayList<Stmt>();
        Iterator<Unit> iterator = units.snapshotIterator();
        while (iterator.hasNext()) {
            Stmt stmt = (Stmt)iterator.next();
            targetStatements.add(stmt);
        }
        int currentLine = 0;
        for (int i = 0; i < targetStatements.size(); i ++) {
            if (i + 1 < targetStatements.size()) {
                Stmt next = targetStatements.get(i + 1);
                Stmt current = targetStatements.get(i);
                if (shouldInject(current.toString(), next.toString())) {
                    SootMethod log = Scene.v().getMethod("<Print: void logPrint(java.lang.String)>");
                    StringConstant newSourceValue = StringConstant.v(signature + LOG_PREVIOUS + currentLine + " **** " + current.toString());
//                    System.out.println();
//                    System.out.println(current.toString());
//                    System.out.println(newSourceValue.toString());
//                    System.out.println();
                    StaticInvokeExpr expr = Jimple.v().newStaticInvokeExpr(log.makeRef(), newSourceValue);
                    units.insertAfter(Jimple.v().newInvokeStmt(expr), current);
                }
                if (!current.toString().contains(LOG_PREVIOUS)) {
                    currentLine ++;
                }
            }
        }
    }

    private static boolean shouldInject(String current, String next) {
        //System.out.println(current);
        return !next.contains(LOG_PREVIOUS) && !current.contains(LOG_PREVIOUS) && !current.contains(LIMITED_STMT);
    }

    public static void outputClassFile(SootClass sClass) throws IOException {
        String fileName = SourceLocator.v().getFileNameFor(sClass, Options.output_format_class);
//        System.out.println(fileName);
        fileName = fileName.replace("sootOutput"+File.separator, Main.getGenerated());//替换为各个benchmark文件夹下的路径
//        System.out.println(fileName);
//        System.exit(0);
        File file = new File(fileName);
        String path = file.getParent();
        File folder = new File(path);
        if (!folder.exists()) {
            createNestedFolder(folder);
        }
        OutputStream streamOut = new JasminOutputStream(new FileOutputStream(fileName));
        PrintWriter writerOut = new PrintWriter(new OutputStreamWriter(streamOut));

        JasminClass jasminClass = new soot.jimple.JasminClass(sClass);
        jasminClass.print(writerOut);
        writerOut.flush();
        streamOut.close();
    }

    private static void createNestedFolder(File folder) {
        folder.mkdirs();
    }



    //获取class中stmt的执行顺序
    public static List<String> getPureInstructionsFlow(String className, String[] activeArgs, String jvmOptions) throws IOException{
        Set<String> usedStmt = new HashSet<>();
        List<String> result = new ArrayList<>();
        String cmd;
        cmd = "java -Xbootclasspath/a:" + dependencies + " -classpath " + generated + extraCp + " " + jvmOptions + " " + className;
        if (activeArgs != null && activeArgs.length != 0) {
            for (String arg: activeArgs) {
                cmd += " " + arg + " ";
            }
        }
        System.out.println("getPureInstructionsFlow: Start!");
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            final InputStream is1 = p.getInputStream();
            final InputStream is2 = p.getErrorStream();
            new Thread(() -> {
                BufferedReader br2 = new  BufferedReader(new  InputStreamReader(is2));
                try {
                    String line2 = null ;
                    while ((line2 = br2.readLine()) !=  null ){
//                        System.out.println(line2);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                finally{
                    try {
                        br2.close();
                        is2.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
            BufferedReader br1 = new BufferedReader(new InputStreamReader(is1));
            try {
                String line1 = null;
                while ((line1 = br1.readLine()) != null) {
//                        System.out.println(line1);
                    if (line1.contains(LOG_PREVIOUS) && !usedStmt.contains(line1)) {//是否是第一次被执行
                        usedStmt.add(line1);
                        result.add(line1);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    br1.close();
                    is1.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            p.waitFor();
            p.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("getPureInstructionsFlow: Finish!");
        return result;
    }

    public static Set<String> getExecutedLiveInstructions(String className, String signature, String[] args, String jvmOptions) {//通过signature索引，获取某个method的livecode
        Set<String> usedStmt = new HashSet<>();
        String cmd;
        cmd = "java -Xbootclasspath/a:" + dependencies + " -classpath " + generated + extraCp + " " + jvmOptions + " " + className;
        if (args != null && args.length != 0) {
            for (String arg: args) {
                cmd += " " + arg + " ";
            }
        }
        System.out.println("getExecutedLiveInstructions: Start!");
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            final InputStream is1 = p.getInputStream();
            final InputStream is2 = p.getErrorStream();
            new Thread(() -> {
                BufferedReader br2 = new  BufferedReader(new  InputStreamReader(is2));
                try {
                    String line2 = null ;
                    while ((line2 = br2.readLine()) !=  null ){
//                        System.out.println(line2);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                finally{
                    try {
                        br2.close();
                        is2.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

            BufferedReader br1 = new BufferedReader(new InputStreamReader(is1));
//            String allLines = "";
            try {
                String line1 = null;
                while ((line1 = br1.readLine()) != null) {
//                    allLines += line1 + "\n";
//                    noOutput = false;
//                    System.out.println(line1);
                    if (line1.contains(LOG_PREVIOUS) && line1.contains(signature)) {
                        String[] elements = line1.split("[*]+");
                        String currentStmt = elements[3].trim();
                        if (!usedStmt.contains(currentStmt)) {
                            usedStmt.add(currentStmt);
                        }
                    }
                }
//                System.out.println(allLines);
            } catch (IOException e) {
                e.printStackTrace();
            } finally{
                try {
                    br1.close();
                    is1.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            p.waitFor();
            p.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("getExecutedLiveInstructions: Finish!");
//        if(noOutput){
//            System.out.println("********************Program has no output********************");
//        }
//        System.out.println(usedStmt);
        return usedStmt;
    }

    public static List<SootMethod> getLiveMethod(Set<String> usedStmt, List<String> pureInstructionFlow, List<SootMethod> methods) {
        List<SootMethod> signatures = new ArrayList<SootMethod>();
        Set<String> involvedMethod = new HashSet<>();
        Pattern invokePattern = Pattern.compile("[<][^:]+[:]\\s+[^>]+[>]");//表示首字符为<，随后任何不为:的字符，再包含一个:和任意空白字符，再不包含>字符，以>为结尾
        for (String stmt : usedStmt) {
            if (!stmt.contains(LOG_PREVIOUS) && stmt.contains("invoke")) {
                Matcher matcher = invokePattern.matcher(stmt);
                if (matcher.find()) {
                    String methodName = matcher.group();
                    involvedMethod.add(methodName);
                }
            }
        }
        for (SootMethod method : methods) {//调用的方法哪些是在sootmethod中,将其添加到signatures
            if (involvedMethod.contains(method.getSignature()) || method.getSignature().contains(MAIN_SIGN)) {
                signatures.add(method);
            }
        }
        return signatures;
    }

    public static List<Stmt> getAllStatementsList(SootMethod method) {
        Body body = method.retrieveActiveBody();
        UnitPatchingChain units = body.getUnits();
        Iterator iter = units.snapshotIterator();
        List<Stmt> result = new ArrayList<Stmt>();
        while (iter.hasNext()) {
            Stmt stmt = (Stmt)iter.next();
            if (stmt.toString().contains(LOG_PREVIOUS)) {
                result.add((Stmt) (units.getPredOf(stmt)));//是原始的stmt，不是后来添加的stmt。所以取得是getpredof
            }
//            result.add(stmt.toString().contains(LOG_PREVIOUS) ? null : stmt);
        }
        return result;
    }

    public static List<Stmt> getActiveInstructions(Set<String> usedStmt, SootClass c, String signature, String[] args) throws IOException {
        Map<String, String> mapping = new HashMap<>();//可以用被soot修改后的stmt找到对应的修改前的stmt
        List<Stmt> activeJimpleInstructions = new ArrayList<Stmt>();
        List<SootMethod> d = c.getMethods();
        SootMethod mainMethod = null;
        for (SootMethod method : d) {       //标记当前signature对应的method
            String currentSignature = method.getSignature();
            if (currentSignature.contains(signature)) {
                mainMethod = method;
                break;
            }
        }
        if (mainMethod == null) {
            return null;
        }
        Body body = mainMethod.retrieveActiveBody();
        UnitPatchingChain units = body.getUnits();
        Iterator<Unit> iter = units.snapshotIterator();
        while (iter.hasNext()) {
            Stmt current = (Stmt) iter.next();
            if (current.toString().contains(LOG_PREVIOUS)) {
                String[] elements = current.toString().split("[*]+");
//                System.out.println(Arrays.toString(elements));
                String currentStmt = elements[3].trim().replace("\\", "");  // 去除转义符
//                System.out.println(currentStmt);
                currentStmt = currentStmt.substring(0, currentStmt.length() - 2);//去除末尾的右引号和反括号
                if (usedStmt.contains(currentStmt)) {                            //soot在插装后会改变jimple文件中的变量名,eg:i0 = lengthof r0 -> i1 = lengthof r0
                    Stmt previous = (Stmt) (units.getPredOf(current));
//                    System.out.println(current.toString());
//                    System.out.println(currentStmt);
//                    System.out.println(previous.toString());
//                    System.out.println();
                    mapping.put(previous.toString(), currentStmt);
                    activeJimpleInstructions.add(previous);//将目前的jimple源代码（即不含log_previous的代码）存入
                }
            }
        }
        UsedStatementHelper.addMethodStringToStmt(signature, mapping);
        return activeJimpleInstructions;
    }

    public static String temporaryOutput(SootClass sClass, String tmpRoot, String tmpName) {
        try {
            String fileName = tmpRoot + "/" + tmpName + sClass.getName()+".class";
            OutputStream streamOut = new JasminOutputStream(new FileOutputStream(fileName));
            PrintWriter writerOut = new PrintWriter(new OutputStreamWriter(streamOut));
            JasminClass jasminClass = new soot.jimple.JasminClass(sClass);
            jasminClass.print(writerOut);
            writerOut.flush();
            streamOut.close();
            return fileName;
        } catch (Exception e) {
            e.printStackTrace();
//            System.out.println("should not");
            return null;
        }
    }


}

