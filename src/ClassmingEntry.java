 import java.io.IOException;
 import java.util.ArrayList;
 import java.util.List;
 import java.util.Random;

 public class ClassmingEntry {
     public static void process(String className, int iterationCount, String[] args, String classPath, String dependencies, String jvmOptions) throws IOException {
         if (classPath != null && !classPath.isEmpty()) {
             Main.setGenerated(classPath);
         }
         if (dependencies != null && dependencies.isEmpty()) {
             Main.setDependencies(dependencies);
         }
         MutateClass mutateClass = new MutateClass();
         Main.initialize(args);
         mutateClass.initialize(className, args, null, jvmOptions);
         List<MutateClass> mutateAcceptHistory = new ArrayList<>();
         List<MutateClass> mutateRejectHistory = new ArrayList<>();
         List<Double> averageDistance = new ArrayList<>();
         Random random = new Random();
         mutateAcceptHistory.add(mutateClass);
         mutateClass.saveCurrentClass();
         for (int i = 0; i < iterationCount; i++) {
             System.out.println("Current size is : " + (mutateAcceptHistory.size() + mutateRejectHistory.size()) + ", iteration is :" + i + ", average distance is " + MathTool.mean(averageDistance));
             MutateClass newOne = randomMutation(mutateClass);//对mutateClass进行突变
         }
     }

     private static MutateClass randomMutation(MutateClass target) throws IOException {
         Random random = new Random();
         int randomAction = random.nextInt(3);
         switch (randomAction) {
             case 0:
                 return target.gotoIteration();
             case 1:
                 return target.lookUpSwitchIteration();
             case 2:
                 return target.returnIteration();
         }
         return null;
     }

     public static void main (String[]args) throws IOException {
         long startTime = System.currentTimeMillis();
         process("org.sunflow.Benchmark", 2000,
                 new String[]{"-bench", "2", "256"},// "  -bench [threads] [resolution] Run a single iteration of the benchmark using the specified thread count and image resolution"
                 "./sootOutput/sunflow-0.07.2/",
                 "dependencies/janino-2.5.15.jar", "");
         long endTime = System.currentTimeMillis();
         System.out.println("Program used time: " + (endTime - startTime) + "ms");//输出运行时间
     }
 }