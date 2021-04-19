import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class Test{
    public static void main(String[] args) throws IOException {
        int i = 10;
        do{

            System.out.println("test");
            i--;
        }while(i > 0);
        System.out.println("end");
    }
}

//class Person {
//    private String name;
//    static int id = 1;
//
//    public Person(){
//
//    }
//    public Person(String name){
//
//        this.name = name;
//    }
//    public void setName(String name){
//        this.name = name;
//    }
//    public String getName(){
//        return this.name;
//    }
//}