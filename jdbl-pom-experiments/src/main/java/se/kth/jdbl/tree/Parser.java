package se.kth.jdbl.tree;


import java.io.Reader;


public interface Parser {

    Node parse(Reader reader) throws ParseException;

}
