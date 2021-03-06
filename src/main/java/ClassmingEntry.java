 import java.io.File;
 import java.io.IOException;
 import java.nio.file.Files;
 import java.util.ArrayList;
 import java.util.List;
 import java.util.Random;

 public class ClassmingEntry {
     public static String cpSeparator = ":";
     public static void process(String className, int iterationCount, String[] args, String classPath, String dependencies, String jvmOptions) throws IOException {
         MutateClass base  = new MutateClass();//用于保存最后一个成功接收的mutateClass
         if (classPath != null && !classPath.isEmpty()) {
             Main.setGenerated(classPath);
         }
         if (dependencies != null && !dependencies.isEmpty()) {
             Main.setDependencies(dependencies);
         }
         MutateClass mutateClass = new MutateClass();
         Main.initialize(args);
         mutateClass.initialize(className, args, null, jvmOptions);
         List<MutateClass> mutateAcceptHistory = new ArrayList<>();
         List<MutateClass> mutateRejectHistory = new ArrayList<>();
         Random random = new Random();
         mutateAcceptHistory.add(mutateClass);//添加原始的mutateClass文件
         base = mutateClass;
         mutateClass.saveCurrentClass();
         for (int i = 0; i < iterationCount; i++) {
             System.out.println("Current size is : " + (mutateAcceptHistory.size() +  mutateRejectHistory.size()) + ", iteration is :" + i);
             mutateClass = base;
             MutateClass newOne = randomMutation(mutateClass);//对mutateClass进行突变，对于nolivecode放在了deepcopy中。也就是在mutate之后直接进行判断，如果其livecode为0，则覆盖率也是为0的。
             if (newOne != null) {
                 newOne.saveCurrentClass();
                 MutateClass previousClass = mutateAcceptHistory.get(mutateAcceptHistory.size() - 1);
                 MethodCounter current = newOne.getCurrentMethod();
//                 List<String> currentLiveCode = newOne.getMethodLiveCodeString(current.getSignature());
                 List<String> originalCode = previousClass.getMethodOriginalStmtListString(current.getSignature());
                 double covScore = calculateCovScore(newOne);
                 double rand = random.nextDouble();
                 double fitnessScore = fitness(calculateCovScore(mutateClass), covScore, originalCode.size());
                 if(rand < fitnessScore) {
                     System.out.println(covScore);
                     mutateAcceptHistory.add(newOne);
                     dumpSingleMutateClass(newOne, "./AcceptHistory/");
                     base = newOne;
                 }
                 else {
                     newOne.saveCurrentClass();
                     mutateRejectHistory.add(newOne);
                     dumpSingleMutateClass(newOne, "./RejectHistory/");
                 }
             }
         }
         System.out.println("Accept size is " + mutateAcceptHistory.size());
     }

     private static void dumpSingleMutateClass(MutateClass newOne, String targetDirectory) {//将newone保存到目标文件夹
         if (newOne.getBackPath()==null || newOne.getBackPath().equals("")){
             System.err.println("ClassmingEntry.dumpSingleMutateClass(): mutateClass's backpath = "+newOne.getBackPath());
         }
         String backPath = newOne.getBackPath();
         File source = new File(backPath);
         File dest = new File(backPath.replace("./tmp/", targetDirectory));
         try {
             Files.copy(source.toPath(), dest.toPath());
         } catch (Exception e) {
             e.printStackTrace();
         }
     }

     private static double fitness(double previousCov, double currentCov, int originalCodeSize) {
         double result = Math.exp(0.08 * originalCodeSize * (previousCov - currentCov));
         return Math.min(1.0, result);
     }

     private static double calculateCovScore(MutateClass mutateClass) {
         MethodCounter current = mutateClass.getCurrentMethod();
         List<String> currentLiveCode = mutateClass.getMethodLiveCodeString(current.getSignature());
         List<String> originalCode = mutateClass.getMethodOriginalStmtListString(current.getSignature());
         return currentLiveCode.size() / (double)originalCode.size();
     }

     private static MutateClass randomMutation(MutateClass target) throws IOException {
         Random random = new Random();
         int randomAction = random.nextInt(4);
         switch (randomAction) {
             case 0:
                 return target.gotoIteration();
             case 1:
                 return target.lookUpSwitchIteration();
             case 2:
                 return target.returnIteration();
             case 3:
                 return target.JITIteration();
         }
         return null;
     }

     public static void main (String[]args) throws IOException {
         long startTime = System.currentTimeMillis();
         String home_path = "/Users/feixiangdejiahao/jvm-fuzzing/";
         process("avrora.Main", 3000,
                 new String[]{"-action=cfg","sootOutput/avrora-cvs-20091224/example.asm"},
                 "./sootOutput/avrora-cvs-20091224/",null, "");
        process("org.apache.batik.apps.rasterizer.Main", 400,null,
                "./sootOutput/batik-all/",null, "");
         process("org.eclipse.core.runtime.adaptor.EclipseStarter", 3,
                 new String[]{"-debug"}, "./sootOutput/eclipse/", null, "");
         process("org.sunflow.Benchmark", 5,
                 new String[]{"-bench","2","256"},
                 "./sootOutput/sunflow-0.07.2/",
                 "dependencies/janino-2.5.15.jar", "");
         process("org.apache.fop.cli.Main", 3000,
                 new String[]{"-xml","sootOutput/fop/name.xml","-xsl","sootOutput/fop/name2fo.xsl","-pdf","sootOutput/fop/name.pdf"},
                 "./sootOutput/fop/",
                 "dependencies/xmlgraphics-commons-1.3.1.jar" + cpSeparator +
                         "dependencies/commons-logging.jar" + cpSeparator +
                         "dependencies/avalon-framework-4.2.0.jar" + cpSeparator +
                         "dependencies/batik-all.jar" + cpSeparator +
                         "dependencies/commons-io-1.3.1.jar", "");
         process("org.python.util.jython", 6000,
                 new String[]{"sootOutput/jython/hello.py"},
                 "./sootOutput/jython/",
                 "dependencies/guava-r07.jar" + cpSeparator +
                         "dependencies/constantine.jar" + cpSeparator +
                         "dependencies/jnr-posix.jar" + cpSeparator +
                         "dependencies/jaffl.jar" + cpSeparator +
                         "dependencies/jline-0.9.95-SNAPSHOT.jar" + cpSeparator +
                         "dependencies/antlr-3.1.3.jar" + cpSeparator +
                         "dependencies/asm-3.1.jar", "");
        process("net.sourceforge.pmd.PMD", 50,
                new String[]{"sootOutput/pmd-4.2.5/Hello.java","text","unusedcode"},
                "./sootOutput/pmd-4.2.5/",
                "dependencies/jaxen-1.1.1.jar" + cpSeparator +
                        "dependencies/asm-3.1.jar", "");
         process("Test",5,new String[]{},"./sootOutput/test/","","");
         long endTime = System.currentTimeMillis();
         System.out.println("Program used time: " + (endTime - startTime) + "ms");//输出运行时间
     }
 }