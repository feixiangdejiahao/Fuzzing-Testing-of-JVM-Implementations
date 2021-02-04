import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JIfStmt;
import soot.jimple.internal.JLookupSwitchStmt;
import soot.jimple.internal.JReturnStmt;
import soot.options.Options;

import javax.naming.Name;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

public class MutateClass {
    private String className;
    private List<MethodCounter> mutationCounter = new ArrayList<>();
    private Map<String, Body> methodLiveBody = new HashMap<String, Body>();
    private List<String> classPureInstructionFlow;
    private String[] activeArgs;
    private static String jvmOptions = "";
    private SootClass sootClass;
    private Set<String> mainLiveStmt;
    private List<SootMethod> liveMethod;
    private Map<String, List<Stmt>> methodOriginalStmtList = new HashMap<>();
    private Map<String, SootMethod> methodMap = new HashMap<String, SootMethod>();


    public void initialize(String className, String[] args, List<MethodCounter> previousMutationCounter, String jvmOptions)throws IOException{
        this.className = className;
        this.sootClass = Main.loadTargetClass(className);//加载class并完成初始化
        initializeSootClass(previousMutationCounter);
    }

    private void initializeSootClass(List<MethodCounter> previousMutationCounter) throws IOException{
        Set<String> classPureInstructionFlowSet= new HashSet<>();


        Main.outputClassFile(this.sootClass);//生成插装后的class文件
        for (SootMethod method : this.sootClass.getMethods()) {//将method村放入hashmap中,由signature进行索引
            this.methodLiveBody.put(method.getSignature(), method.retrieveActiveBody());
        }
        this.classPureInstructionFlow = Main.getPureInstructionsFlow(className, activeArgs, jvmOptions);//获取带signature的instruction
        //System.out.println(this.classPureInstructionFlow);
        Debug.debug(this, classPureInstructionFlow);
        //this.mainLiveStmt = Main.getExecutedLiveInstructions(className, Main.MAIN_SIGN, activeArgs, jvmOptions);//找main方法下的livecode
        for(String s: classPureInstructionFlow){
            String[] elements = s.split("[*]+");
            String currentStmt = elements[3].trim();
            classPureInstructionFlowSet.add(currentStmt);
        }
        this.liveMethod = Main.getLiveMethod(classPureInstructionFlowSet, classPureInstructionFlow, this.sootClass.getMethods());
        int counter = 0;
        for (SootMethod method : this.liveMethod) {
            methodOriginalStmtList.put(method.getSignature(), Main.getAllStatementsList(method));
            methodMap.put(method.getSignature(), method);
            Set<String> usedStmt = Main.getExecutedLiveInstructions(className, method.getSignature(), activeArgs, jvmOptions);
            List<Stmt> liveStmt = Main.getActiveInstructions(usedStmt, this.sootClass, method.getSignature(), activeArgs);
        }

    }
}
