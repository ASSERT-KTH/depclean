package se.kth.depclean.gradle.dt;

public enum InputType {

    TEXT {
        @Override
        public Parser newParser() {
            return new TextParser();
        }
    };

    public abstract Parser newParser();

}
