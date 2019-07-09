package se.kth.jdbl.tree;

import java.io.IOException;
import java.io.Reader;

public class TextParser extends AbstractLineBasedParser {

    public Node parse(Reader reader) throws ParseException {

        try {
            this.lines = splitLines(reader);
        } catch (IOException e) {
            throw new ParseException(e);
        }

        if (lines.isEmpty()) {
            return null;
        }

        return parseInternal(0);

    }

    private Node parseInternal(final int depth) {

        //current node
        final Node node = this.parseLine();

        this.lineIndex++;

        //children
        while (this.lineIndex < this.lines.size() && this.computeDepth(this.lines.get(this.lineIndex)) > depth) {
            final Node child = this.parseInternal(depth + 1);
            if (node != null) {
                node.addChildNode(child);
            }
        }
        return node;

    }

    private int computeDepth(final String line) {
        return getArtifactIndex(line) / 3;
    }

    /**
     * sample lineIndex structure:
     * <pre>|  |  \- org.apache.activemq:activeio-core:test-jar:tests:3.1.0:compile</pre>
     *
     * @return
     */
    private Node parseLine() {
        String line = this.lines.get(this.lineIndex);
        String artifact;
        if (line.contains("active project artifact:")) {
            artifact = extractActiveProjectArtifact();
        } else {
            artifact = extractArtifact(line);
        }
        return parseArtifactString(artifact);
    }

    private String extractArtifact(String line) {
        return line.substring(getArtifactIndex(line));
    }

    private int getArtifactIndex(final String line) {
        for (int i = 0; i < line.length(); i++) {
            final char c = line.charAt(i);
            switch (c) {
                case ' '://whitespace, standard and extended
                case '|'://standard
                case '+'://standard
                case '\\'://standard
                case '-'://standard
                case '³'://extended
                case 'Ã'://extended
                case 'Ä'://extended
                case 'À'://extended
                    continue;
                default:
                    return i;
            }
        }
        return -1;
    }

}
