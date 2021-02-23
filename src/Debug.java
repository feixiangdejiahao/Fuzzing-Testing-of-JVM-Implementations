import java.util.HashMap;
import java.util.List;
import java.util.Set;

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

    public static boolean equals(Set<?> set1, Set<?> set2){
        if(set1 == null || set2 ==null){//null就直接不比了
            return false;
        }
        if(set1.size()!=set2.size()){//大小不同也不用比了
            return false;
        }
        return set1.containsAll(set2);//最后比containsAll
    }
}
