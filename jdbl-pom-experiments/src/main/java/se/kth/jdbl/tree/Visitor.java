package se.kth.jdbl.tree;

public interface Visitor {

    public void visit(Node tree) throws VisitException;

}
