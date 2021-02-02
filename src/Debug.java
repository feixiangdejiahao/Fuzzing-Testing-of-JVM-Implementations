import java.util.HashMap;
import java.util.List;

public class Debug {
    static HashMap<MutateClass, List<String>> classPureInstructionFlowMap = new HashMap<>();



    //查看该mutateclass中执行路径是否发生改变
    public static void debug(MutateClass m, List<String> classPureInstructionFlow) {
        if(classPureInstructionFlowMap.containsKey(m)){
            boolean same = true;
            List<String> flow = classPureInstructionFlowMap.get(m);
            if(classPureInstructionFlow.size()!=flow.size())
                same = false;
            else{
                for(int i = 0; i < flow.size();i++){
                    if(!flow.get(i).equals(classPureInstructionFlow.get(i)))
                        same =false;
                }
            }
            if(!same){
                System.out.println("classPureInstructionFlow has changed!!!");
            }
        }else{
            classPureInstructionFlowMap.put(m, classPureInstructionFlow);
        }
    }
}
