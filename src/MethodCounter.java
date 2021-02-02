public class MethodCounter implements Comparable {//用于进行方法比较
    private String signature;
    private Integer count;

    public MethodCounter(String signature, int count) {
        this.signature = signature;
        this.count = count;
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

    @Override
    public int compareTo(Object o) {
        return this.getCount().compareTo(((MethodCounter)o).getCount());
    }
}
