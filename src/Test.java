public class Test{
    public static void main(String[] args){
        int i = 1;
        Person camel = new Person("jiahaoxiang");
        camel.setName("jiahaoxiang");
        System.out.println(camel.getName());
        //System.out.println(System.getProperty("java.class.path"));
    }

}

class Person {
    private String name;
    static int id = 1;

    public Person(){

    }
    public Person(String name){

        this.name = name;
    }
    public void setName(String name){
        this.name = name;
    }
    public String getName(){
        return this.name;
    }
}