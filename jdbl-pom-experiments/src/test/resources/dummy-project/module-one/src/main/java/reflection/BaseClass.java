package reflection;

public class BaseClass {

    public int baseInt;

    private static void method3() {
        System.out.println("Method3");
    }

    public String  method7(String input) {
        System.out.println(input);
        return  input;
    }

    public static int method5() {
        System.out.println("Method5");
        return 0;
    }

    private static void method6() {
        System.out.println("Method6");
    }

    // inner public class
    public class BaseClassInnerClass {
    }

    //member public enum
    public enum BaseClassMemberEnum {
    }

}
