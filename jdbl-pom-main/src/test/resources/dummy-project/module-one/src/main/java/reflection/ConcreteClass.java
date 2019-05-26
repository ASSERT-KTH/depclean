package reflection;

public class ConcreteClass extends BaseClass implements BaseInterface {

    public int publicInt;

    private String privateString = "private string";

    protected boolean protectedBoolean;

    Object defaultObject;

    @Override
    public void method1() {
        System.out.println("Method1 impl.");
    }

    @Override
    public int method2(String str) {
        System.out.println("Method2 impl.");
        return 0;
    }

    private String method6(String output) {
        return output;
    }

    public int method5(int i) {
        System.out.println("Method4 overriden.");
        return 0;
    }

    // inner classes
    public class ConcreteClassPublicClass {
    }

    private class ConcreteClassPrivateClass {
    }

    protected class ConcreteClassProtectedClass {
    }

    class ConcreteClassDefaultClass {
    }

    //member enum
    enum ConcreteClassDefaultEnum {
    }

    public enum ConcreteClassPublicEnum {}

    //member interface
    public interface ConcreteClassPublicInterface {
    }


}
