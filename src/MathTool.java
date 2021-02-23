import java.util.List;

public class MathTool {
    public static double epsilon = 0.05;

    public static double mean(List<Double> data) {
        double mean = 0;
        mean = sum(data) / data.size();
        return mean;
    }

    public static double sum(List<Double> data) {
        double sum = 0;
        for (Double num : data) sum = sum + num;
        return sum;
    }

    public static double realLog(double value, double base) {//用换底公式计算对base求value的底
        return Math.log(value) / Math.log(base);
    }


}
