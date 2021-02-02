 import java.io.IOException;

public class ClassmingEntry {
    public static void process(String className, int interationCount, String[] args, String classPath, String dependencies, String jvmOptions)throws IOException {
        if (classPath != null && !classPath.isEmpty()){
            Main.setGenerated(classPath);
        }
        if(dependencies!=null&&dependencies.isEmpty()){
            Main.setDependencies(dependencies);
        }
        MutateClass mutateClass = new MutateClass();
        Main.initialize(args);
        mutateClass.initialize(className, args, null, jvmOptions);
    }



    public static void main(String[] args) throws IOException {
        long startTime = System.currentTimeMillis();
        process("org.sunflow.Benchmark", 2000,
                new String[]{"-bench","2","256"},
                "./sootOutput/sunflow-0.07.2/",
                "dependencies/janino-2.5.15.jar","");
        long endTime = System.currentTimeMillis();
        System.out.println("Program used time: "+(endTime-startTime)+"ms");//输出运行时间
    }
}
