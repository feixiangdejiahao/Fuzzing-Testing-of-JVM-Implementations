import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JGotoStmt;
import soot.jimple.internal.JIfStmt;
import soot.jimple.internal.JLookupSwitchStmt;
import soot.jimple.internal.JReturnStmt;
import soot.options.Options;

import java.io.IOException;
import java.util.*;

public class MutateClass {
    private String className;
    private List<MethodCounter> mutationCounter = new ArrayList<>();
    private Map<String, Body> methodLiveBody = new HashMap<String, Body>();//存放method和其对应body
    private List<String> classPureInstructionFlow;//存放带signature的instruction
    private String[] activeArgs;
    private static String jvmOptions = "";
    private SootClass sootClass;
    private Set<String> mainLiveStmt;
    private List<SootMethod> liveMethod;//存放live的method
    private Map<String, List<Stmt>> methodOriginalStmtList = new HashMap<>();//存放method对应的signature和其对应的带signature的instruction
    private Map<String, SootMethod> methodMap = new HashMap<String, SootMethod>();//存放method名称和其对应的signature
    private Map<String, Set<String>> methodLiveQuery = new HashMap<>();//存放method和对应的live stmt
    private Map<String, List<Stmt>> methodLiveCode = new HashMap<>();//存放method的signature和对应的live stmt
    private Map<String, List<String>> methodOriginalStmtStringList = new HashMap<>();//将methodOriginalStmtList中instruction改为string存放
    private Map<String, List<String>> methodLiveCodeString = new HashMap<>();//将methodLiveCode中live stmt转为string存放
    private String backPath;
    private MethodCounter currentMethod;
    private static boolean shouldRandom = true;


    private static int gotoVarCount = 1;
    private static int loopLimit = 5;
    private static int jitLimit = 100000;
    private static boolean noBegin = false;

    public void initialize(String className, String[] args, List<MethodCounter> previousMutationCounter, String jvmOptions)throws IOException{
        this.activeArgs = args;
        MutateClass.jvmOptions = jvmOptions;
        this.className = className;
        this.sootClass = Main.loadTargetClass(className);//加载class并完成初始化
        initializeSootClass(previousMutationCounter);
    }

    private void initializeSootClass(List<MethodCounter> previousMutationCounter) throws IOException{
        Set<String> classPureInstructionFlowSet= new HashSet<>();
        Main.outputClassFile(this.sootClass);//生成插装后的class文件,替换原始/上一版本的class文件
        for (SootMethod method : this.sootClass.getMethods()) {//将method村放入hashmap中,由signature进行索引
            this.methodLiveBody.put(method.getSignature(), method.retrieveActiveBody());
        }
        this.classPureInstructionFlow = Main.getPureInstructionsFlow(className, activeArgs, jvmOptions);//获取带signature的instruction
//        System.out.println(this.classPureInstructionFlow);
        Debug.debug(this, classPureInstructionFlow);
        this.mainLiveStmt = Main.getExecutedLiveInstructions(className, Main.MAIN_SIGN, activeArgs, jvmOptions);//找main方法下的livecode
        for(String s: classPureInstructionFlow){
            String[] elements = s.split("[*]+");
            String currentStmt = elements[3].trim();
            classPureInstructionFlowSet.add(currentStmt);
        }
        this.liveMethod = Main.getLiveMethod(classPureInstructionFlowSet, classPureInstructionFlow, this.sootClass.getMethods());
        int counter = 0;
        for (SootMethod method : this.liveMethod) {
            methodOriginalStmtList.put(method.getSignature(), Main.getAllStatementsList(method));//统计method对应的size
            methodMap.put(method.getSignature(), method);
            Set<String> usedStmt = Main.getExecutedLiveInstructions(className, method.getSignature(), activeArgs, jvmOptions);
            List<Stmt> liveStmt = Main.getActiveInstructions(usedStmt, this.sootClass, method.getSignature(), activeArgs);
            methodLiveQuery.put(method.getSignature(), changeListToSet(liveStmt));
            UsedStatementHelper.addClassMethodUsedStmt(className, method.getSignature(), usedStmt);
            methodLiveCode.put(method.getSignature(), liveStmt);
            int callCount = previousMutationCounter == null ? 1 : previousMutationCounter.get(counter++).getCount();//统计方法被调用的次数
            int instructionCount = instructionCounter(Main.getAllStatementsList(method));//用于记录每个method中goto，return，switch等改变控制流语句的数量
            mutationCounter.add(new MethodCounter(method.getSignature(), callCount, instructionCount));
        }
//        System.out.println(methodLiveCode.size());
        transformStmtToString(methodOriginalStmtList, methodOriginalStmtStringList);
        transformStmtToStringAdvanced(methodLiveCode, methodLiveCodeString);
    }

    private int instructionCounter(List<Stmt> allStatementsList) {//用于记录每个method中goto，return，switch等改变控制流语句的数量
        int count = 0;
        for (Stmt stmt : allStatementsList) {
            if (stmt.toString().contains("goto") | stmt.toString().contains("return") | stmt.toString().contains("switch")){
                count++;
            }
        }
        return count;
    }

    private Set<String> changeListToSet(List<Stmt> target) {
        if (target == null) {
            return null;
        }
        Set<String> result = new HashSet<>();
        for (Stmt object: target) {
            result.add(object.toString());
        }
        return result;
    }

    public static void transformStmtToString(Map<String, List<Stmt>> from, Map<String, List<String>> to) {
        for (String key: from.keySet()) {
            List<Stmt> current = from.get(key);
            List<String> currentString = new ArrayList<>();
            for (Stmt stmt: current) {
                currentString.add(stmt.toString());
            }
            to.put(key, currentString);
        }
    }

    public static void transformStmtToStringAdvanced(Map<String, List<Stmt>> from, Map<String, List<String>> to) {//将stmt转为string
        for (String key: from.keySet()) {
            List<Stmt> current = from.get(key);
            List<String> currentString = new ArrayList<>();
            for (Stmt stmt: current) {
                currentString.add(UsedStatementHelper.getMappingStdoutStmtString(key, stmt.toString()));
            }
            to.put(key, currentString);
        }
    }

    public void saveCurrentClass() {//保存class记录
        String path = Main.temporaryOutput(sootClass, "./tmp", System.currentTimeMillis() + ".");
        this.setBackPath(path);
    }

    private void setBackPath(String backPath) {
        this.backPath = backPath;
    }

    public MethodCounter getMethodToMutate() {//对方法进行选择
        double rand = Math.random();
        int index = (int) (MathTool.realLog(Math.pow(1 - rand, mutationCounter.size()), MathTool.epsilon));
        if (index >= mutationCounter.size()) {
            index = mutationCounter.size() - 1;
        }
//        System.out.println(this.mutationCounter.size());
//        System.out.println(index);
        MethodCounter method = this.mutationCounter.get(index);
        method.setCount(method.getCount() + 1);
        sortByPotential();
        return method;
    }

    public void sortByPotential() {//对方法进行排序，排序方法：(方法的size)/(方法被调用的次数) --> (size*0.7 + instructions*0.3)/usedtime
        this.mutationCounter.sort((o1, o2) -> ( methodLiveCode.get(o2.getSignature()).size()*7 + o2.getInstructionCount()*3 ) / o2.getCount() * 10 - (methodLiveCode.get(o1.getSignature()).size()*7 + o1.getInstructionCount()*3) / o1.getCount() * 10);
    }

    public int selectHookingPoint(String signature, int candidates) {
        int resultIndex = 0;
        List<Stmt> targetLiveCode = this.methodLiveCode.get(signature);
        Random rand = new Random();
//        if (shouldRandom) {
//            return rand.nextInt(targetLiveCode.size());
//        }
        int[] candidatesIndex = new int[candidates];
        for (int i = 0; i < candidatesIndex.length; i++) {
            candidatesIndex[i] = rand.nextInt(targetLiveCode.size());
        }
        int maxSize = -1;
        for (int index : candidatesIndex) {
            int defUseConflict = computeDefUseSizeByIndex(index, targetLiveCode);//计算从index行中断，破坏的数据依赖有多少
            if (maxSize < defUseConflict) {
                maxSize = defUseConflict;
                resultIndex = index;
            }
        }
        return resultIndex;
    }

    private int computeDefUseSizeByIndex(int split, List<Stmt> targetStmt) {
        Set<String> defInThisMethod = new HashSet<>();
        Set<String> beforeSet = new HashSet<>();
        Set<String> afterSet = new HashSet<>();
        for (Stmt stmt : targetStmt) {//获取该method中所有的def
            List<ValueBox> defVar = stmt.getDefBoxes();
            for (ValueBox box : defVar) {
                defInThisMethod.add(box.getValue().toString());
            }
        }
        finddatadependency(0, split + 1, defInThisMethod, beforeSet, targetStmt);
        finddatadependency(split + 1, targetStmt.size(), defInThisMethod, afterSet, targetStmt);
        Set<String> resulSet = new HashSet<>(beforeSet);
        resulSet.containsAll(afterSet);//取两个set中交集，如果前后两个set都包含某一value，无论是use还是def，都可以构成论文中关系：def-use, def-def, use-use, and use-def
        return resulSet.size();
    }

    private void finddatadependency(int start, int end, Set<String> defInThisMethod, Set<String> resultSet, List<Stmt> targetStmt) {
        for (int i = start; i<end; i++){
            Stmt stmt = targetStmt.get(i);
            List<ValueBox> defVar = stmt.getUseAndDefBoxes();
            for (ValueBox box : defVar){
                if (defInThisMethod.contains(box.getValue().toString())){
                    resultSet.add(box.getValue().toString());
                }
            }
        }
    }

    public Stmt selectTargetPoints(String signature) {
        Random random = new Random();
        while (true) {
            int tpIndex = random.nextInt(this.methodOriginalStmtList.get(signature).size() - 1) + 1;
            double rand = random.nextDouble();
            Stmt nextStmt = this.methodOriginalStmtList.get(signature).get(tpIndex);
            if (shouldRandom) {
                return nextStmt;
            }
            if (!UsedStatementHelper.queryIfHasInstructionsAlready(className, signature, nextStmt.toString())) {//判断nextStmt没有被执行过,也就是没有被存放在hashmap中
                return nextStmt;
            }
            Set<String> previousLive = this.methodLiveQuery.get(signature);
            if (!previousLive.contains(nextStmt.toString())) {//nextStmt没有在上个mutant中执行过
                if (rand <= 0.8) {
                    return nextStmt;
                }
            }
            rand = random.nextDouble();
            if (rand <= 0.2) {
                return nextStmt;
            }
        }
    }

    public MutateClass gotoIteration() throws IOException {
        MethodCounter current = this.getMethodToMutate();
        this.setCurrentMethod(current);
        this.saveCurrentClass();
        try {
            this.gotoMutation(current.getSignature());
            return deepCopy();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void setCurrentMethod(MethodCounter currentMethod) {//记录当前变异的方法
        this.currentMethod = currentMethod;
    }


    public void gotoMutation(String signature) throws IOException {
        System.out.println("one round start in goto.================================================================================");
        List<Stmt> liveCode = methodLiveCode.get(signature);
        int hookingPoint = this.selectHookingPoint(signature, 2);
        Stmt targetPoint = selectTargetPoints(signature);
//        System.out.println(targetPoint);
//        System.out.println(liveCode.get(hookingPoint));
        Stmt nop = Jimple.v().newNopStmt();
        Body body = this.methodLiveBody.get(signature);
        UnitPatchingChain units = body.getUnits();
        Local newVar = Jimple.v().newLocal("_M" + (gotoVarCount++), IntType.v());//给新局部变量newVar设定名称为"_M"+gotoVarCount，类型为int
        body.getLocals().add(newVar);
        Value rightValue = IntConstant.v(loopLimit);// rightValue : 5
        AssignStmt assign = Jimple.v().newAssignStmt(newVar, rightValue);// assign : newVar = rightValue
        SubExpr sub = Jimple.v().newSubExpr(newVar, IntConstant.v(1));//sub : newVar - 1
        ConditionExpr cond = Jimple.v().newGeExpr(newVar, IntConstant.v(0));//cond : newVar >= 0
        AssignStmt substmt = Jimple.v().newAssignStmt(newVar, sub);//substmt : newVar = sub
        IfStmt ifGoto = Jimple.v().newIfStmt(cond, targetPoint);//ifGoto : if (cond) target point;
        units.insertBefore(assign, getTargetStmt(units, liveCode.get(0)));// 将 newVar = 5 放在最前面
//        units.insertBeforeNoRedirect(nop, getTargetStmt(units, targetPoint));//将nop放在targetpoint前，也就是待跳转位置
        Stmt printStmt = (Stmt)units.getSuccOf(getTargetStmt(units, liveCode.get(hookingPoint)));
        units.insertAfter(ifGoto, printStmt);
        units.insertAfter(substmt, printStmt);
    }

    private Stmt getTargetStmt(UnitPatchingChain units, Stmt target){
        Iterator<Unit> iter = units.snapshotIterator();
        while (iter.hasNext()) {
            Stmt current = (Stmt)iter.next();
            if (current.toString().equals(target.toString())) { // because soot will rename variable
                return current;
            }
        }
        System.err.println("Can not find insert point: " + target.toString());
        return target;
    }

    public MutateClass deepCopy() throws IOException {//将整个变异后的sootclass整体保存在result中。
        MutateClass result = new MutateClass();
        result.setActiveArgs(activeArgs);
        setJvmOptions(jvmOptions);
        result.setClassName(className);
        result.setSootClass(sootClass);
        result.setCurrentMethod(this.getCurrentMethod());
        result.initializeSootClass(mutationCounter);//更新sootclass数据到result下
        if (result.getClassPureInstructionFlow().size() == 0 || result.getMethodLiveCode(result.getCurrentMethod().getSignature()).size() == 0) {
            Main.temporaryOutput(result.getSootClass(), "./nolivecode/", System.currentTimeMillis() + ".");
            System.out.println("nolivecode");
            return null;
        }
        return result;
    }

    private SootClass getSootClass() {
        return sootClass;
    }

    private List<Stmt> getMethodLiveCode(String signature) {
        return methodLiveCode.get(signature);
    }

    private List<String> getClassPureInstructionFlow() {
        return classPureInstructionFlow;
    }

    public MethodCounter getCurrentMethod() {
        return currentMethod;
    }

    private void setSootClass(SootClass sootClass) {
        this.sootClass = sootClass;
    }

    private void setClassName(String className) {
        this.className = className;
    }

    private static void setJvmOptions(String jvmOptions) {
        MutateClass.jvmOptions = jvmOptions;
    }

    private void setActiveArgs(String[] activeArgs) {
        this.activeArgs = activeArgs;
    }


    public MutateClass lookUpSwitchIteration() {
        MethodCounter current = getMethodToMutate();
        setCurrentMethod(current);
        saveCurrentClass();
        try {
            this.lookUpSwitchMutation(current.getSignature()); // change current topology
            return this.deepCopy(); // applied change to new class
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void lookUpSwitchMutation(String signature) throws IOException{
        System.out.println("one round start in lookup.================================================================================");
        List<Stmt> liveCode = methodLiveCode.get(signature);
        int hookingPoint = this.selectHookingPoint(signature, 2);
        Body body = this.methodLiveBody.get(signature);
        UnitPatchingChain units = body.getUnits();
        Local newVar = Jimple.v().newLocal("_M" + (gotoVarCount++), IntType.v());
        body.getLocals().add(newVar);
        Random rand = new Random();
        int caseNum = rand.nextInt(3) + 1;//设置switch num（1~3,也就是case的数量
        List<IntConstant> lookUpValues = new ArrayList<IntConstant>();
        List<Stmt> labels = new ArrayList<Stmt>();  // switch跳转的几个label
        List<Stmt> selectedTargetPoints = new ArrayList<Stmt>();  // targets for lookUp values
        int gotoVarCountCopy = loopLimit;
        for (int i =0; i<caseNum;i++){
            lookUpValues.add(IntConstant.v(--gotoVarCountCopy));
            Stmt tempTargetPoint = selectTargetPoints(signature);
            int selectTimes = 0;
            while(selectedTargetPoints.contains(tempTargetPoint)){  // make sure target is different
                if (selectTimes>=5){
                    break;
                }
                tempTargetPoint = selectTargetPoints(signature);
                selectTimes++;
            }
            selectedTargetPoints.add(tempTargetPoint);
            Stmt nop = Jimple.v().newNopStmt();
            units.insertBeforeNoRedirect(nop, getTargetStmt(units, tempTargetPoint));
            labels.add(nop);
        }
        Stmt defaultTargetPoint = selectTargetPoints(signature);//放置default语句
        int selectTimes = 0;
        while(selectedTargetPoints.contains(defaultTargetPoint)){
            if (selectTimes>=5){
                break;
            }
            defaultTargetPoint = selectTargetPoints(signature);
            selectTimes++;
        }
        Stmt defaultNop = Jimple.v().newNopStmt();
        units.insertBeforeNoRedirect(defaultNop, getTargetStmt(units, defaultTargetPoint));
        JLookupSwitchStmt switchStmt = new JLookupSwitchStmt(newVar, lookUpValues, labels, defaultNop);//构造switch语句
        Stmt skipSwitch = Jimple.v().newNopStmt();
        Value rightValue = IntConstant.v(loopLimit);
        AssignStmt assign = Jimple.v().newAssignStmt(newVar, rightValue);
        SubExpr sub = Jimple.v().newSubExpr(newVar, IntConstant.v(1));
        ConditionExpr cond = Jimple.v().newLeExpr(newVar, IntConstant.v(0));  // <= then skip switch
        AssignStmt substmt = Jimple.v().newAssignStmt(newVar, sub);
        IfStmt ifGoto = Jimple.v().newIfStmt(cond, skipSwitch);
        units.insertBefore(assign, getTargetStmt(units, getTargetStmt(units, liveCode.get(0))));
        Stmt printStmt = (Stmt)units.getSuccOf(getTargetStmt(units, liveCode.get(hookingPoint)));
        units.insertAfter(skipSwitch, printStmt);
        units.insertAfter(switchStmt, printStmt);
        units.insertAfter(ifGoto, printStmt);
        units.insertAfter(substmt, printStmt);
    }

    public MutateClass returnIteration() throws IOException{
        MethodCounter current = this.getMethodToMutate();
        this.setCurrentMethod(current);
        this.saveCurrentClass(); // save current class
        try {
            this.returnMutation(current.getSignature()); // change current topology
            return this.deepCopy(); // applied change to new class
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void returnMutation(String signature) {
        System.out.println("one round start in return.================================================================================");
        List<Stmt> liveCode = methodLiveCode.get(signature);
        int hookingPoint = this.selectHookingPoint(signature, 2);
        Body body = this.methodLiveBody.get(signature);
        UnitPatchingChain units = body.getUnits();
        Stmt printStmt = (Stmt)units.getSuccOf(getTargetStmt(units, liveCode.get(hookingPoint)));
        Stmt targetReturnStmt = getReturnStmt(signature);
        Stmt nop = Jimple.v().newNopStmt();
        Local newVar = Jimple.v().newLocal("_M" + (gotoVarCount++), IntType.v());
        body.getLocals().add(newVar);
        AssignStmt assign = Jimple.v().newAssignStmt(newVar, IntConstant.v(1)); // _M = 1
        SubExpr sub = Jimple.v().newSubExpr(newVar, IntConstant.v(1)); // _M-1
        AssignStmt substmt = Jimple.v().newAssignStmt(newVar, sub); // _M = _M-1
        ConditionExpr cond = Jimple.v().newLeExpr(newVar, IntConstant.v(0)); // if _M <= 0
        IfStmt ifGoto = Jimple.v().newIfStmt(cond, nop); // if _M <= 0 goto nop
        units.insertBefore(assign, getTargetStmt(units, liveCode.get(0)));
        units.insertAfter(ifGoto, printStmt);
        units.insertAfter(substmt, printStmt);
        for(Unit unit: units){
            if (unit.toString().equals(targetReturnStmt.toString())){
                units.insertBeforeNoRedirect(nop, unit);
                return;
            }
        }
        // create return stmt
        units.insertAfter(targetReturnStmt, units.getLast());
        units.insertBeforeNoRedirect(nop, targetReturnStmt);
    }

    private Stmt getReturnStmt(String signature) {
        List<String> originStmtString = methodOriginalStmtStringList.get(signature);
        List<Stmt> originStmt = methodOriginalStmtList.get(signature);
//        System.out.println(originStmtString);
        List<Stmt> foundReturnStmt = new ArrayList<Stmt>();
        for (int i = 0; i < originStmtString.size(); i++){
            String stmtString = originStmtString.get(i);
            if(stmtString.contains("return")){
                // the return stmt can be "if i1 != 0 goto return 0"
                if(stmtString.contains("goto")){
                    JGotoStmt ifReturnStmt = (JGotoStmt)originStmt.get(i);
                    Stmt returnStmt = (Stmt)ifReturnStmt.getTargetBox().getUnit();
                    foundReturnStmt.add(returnStmt);
                }else{
                    Stmt returnStmt = originStmt.get(i);
                    foundReturnStmt.add(returnStmt);
                }
            }
        }
        // create return stmt if it has multiple return stmts
        if(foundReturnStmt.size() == 1){
            return foundReturnStmt.get(0);
        }else {
            if(signature.contains("void")){
                return Jimple.v().newReturnVoidStmt();
            }else{
                return Jimple.v().newReturnStmt(NullConstant.v());//感觉这里可以进行修改或优化
            }
        }
    }

    public List<String> getMethodLiveCodeString(String signature) {
        return this.methodLiveCodeString.get(signature);
    }

    public List<String> getMethodOriginalStmtListString(String signature) {
        return methodOriginalStmtStringList.get(signature);
    }

    public String getBackPath() {
        return backPath;
    }

    public MutateClass JITIteration() {
        MethodCounter current = this.getMethodToMutate();
        this.setCurrentMethod(current);
        this.saveCurrentClass(); // save current class
        try {
            this.JITMutation(current.getSignature()); // change current topology
            return this.deepCopy(); // applied change to new class
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void JITMutation(String signature) {
        System.out.println("one round start in JIT.================================================================================");
        List<Stmt> liveCode = methodLiveCode.get(signature);
        int hookingPoint = this.selectHookingPoint(signature, 2);
//        Stmt targetPoint = selectTargetPoints(signature);
        Body body = this.methodLiveBody.get(signature);
        UnitPatchingChain units = body.getUnits();
        Local newVar = Jimple.v().newLocal("_M" + (gotoVarCount++), IntType.v());//给新局部变量newVar设定名称为"_M"+gotoVarCount，类型为int
        body.getLocals().add(newVar);
        Value rightValue = IntConstant.v(jitLimit);// rightValue : 100000
        AssignStmt assign = Jimple.v().newAssignStmt(newVar, rightValue);// assign : newVar = rightValue
        SubExpr sub = Jimple.v().newSubExpr(newVar, IntConstant.v(1));//sub : newVar - 1
        ConditionExpr cond = Jimple.v().newGeExpr(newVar, IntConstant.v(0));//cond : newVar >= 0
        AssignStmt substmt = Jimple.v().newAssignStmt(newVar, sub);//substmt : newVar = sub
        IfStmt ifGoto = Jimple.v().newIfStmt(cond, liveCode.get(1));//ifGoto : if (cond), 跳转到第一条语句，增加JIT出错的概率。
        units.insertBefore(assign, getTargetStmt(units, liveCode.get(0)));// 将 newVar = 100000 放在最前面
//        units.insertBeforeNoRedirect(nop, getTargetStmt(units, liveCode.get(hookingPoint)));//将nop放在hooking point前，也就是待跳转位置
        Stmt printStmt = (Stmt)units.getSuccOf(getTargetStmt(units, liveCode.get(hookingPoint)));//循环判断条件放在hooking point后面，当减一后仍满足>=0条件时，返回最上方接着进行循环
        units.insertAfter(ifGoto, printStmt);
        units.insertAfter(substmt, printStmt);
    }
}
