package se.kth.depclean.gradle.dt;


import java.io.Reader;

public interface Parser {

    Node parse(Reader reader) throws ParseException;

}
