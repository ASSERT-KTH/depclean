package se.kth.jdbl.tree;

public enum InputType {

    TEXT {
        @Override
        public Parser newParser() {
            return new TextParser();
        }
    },

    DOT {
        @Override
        public Parser newParser() {
            return new DotParser();
        }
    },

    GRAPHML {
        @Override
        public Parser newParser() {
            return new GraphmlParser();
        }
    },

    TGF {
        @Override
        public Parser newParser() {
            return new TgfParser();
        }
    };

    public abstract Parser newParser();

}
