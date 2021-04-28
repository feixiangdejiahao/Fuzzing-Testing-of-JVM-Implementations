public class MethodCounter implements Comparable {//用于进行方法排序
    private String signature;
    private Integer count;
    private Integer instructionCount;

    public MethodCounter(String signature, int count, int instructionCount) {
        this.signature = signature;
        this.count = count;
        this.instructionCount = instructionCount;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getInstructionCount() {
        return instructionCount;
    }

    @Override
    public int compareTo(Object o) {
        return this.getCount().compareTo(((MethodCounter)o).getCount());
    }
}
