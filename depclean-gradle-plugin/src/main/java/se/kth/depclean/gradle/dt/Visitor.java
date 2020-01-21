package se.kth.depclean.gradle.dt;



public interface Visitor {

    public void visit(Node tree) throws VisitException;

}
