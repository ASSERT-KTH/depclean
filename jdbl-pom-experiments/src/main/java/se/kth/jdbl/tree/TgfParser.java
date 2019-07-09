package se.kth.jdbl.tree;

import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

public class TgfParser extends AbstractLineBasedParser {

    private static enum ParsePhase {
        NODE, EDGE
    }

    private Map<String, Node> nodes = new HashMap<String, Node>();

    private Node root;

    private ParsePhase phase = ParsePhase.NODE;

    public Node parse(Reader reader) throws ParseException {

        try {
            this.lines = splitLines(reader);
        } catch (IOException e) {
            throw new ParseException(e);
        }

        if (lines.isEmpty()) {
            return null;
        }

        for (; lineIndex < this.lines.size(); lineIndex++) {
            this.parseLine();
        }

        return root;

    }

    /**
     * sample line structure:
     * <pre>
     * -1437430659 com.ibm:mqjms:jar:6.0.0:runtime
     * #
     * 1770590530 96632433 compile
     * </pre>
     */
    private void parseLine() {
        String line = this.lines.get(this.lineIndex);
        if ("#".equals(line)) {
            this.phase = ParsePhase.EDGE;
        } else if (this.phase == ParsePhase.NODE) {
            String id = StringUtils.substringBefore(line, " ");
            String artifact;
            if (line.contains("active project artifact:")) {
                artifact = extractActiveProjectArtifact();
            } else {
                artifact = StringUtils.substringAfter(line, " ");
            }
            Node node = parseArtifactString(artifact);
            if (root == null) {
                this.root = node;
            }
            nodes.put(id, node);
        } else {
            String parentId = StringUtils.substringBefore(line, " ");
            String childId = StringUtils.substringBetween(line, " ");
            Node parent = nodes.get(parentId);
            Node child = nodes.get(childId);
            parent.addChildNode(child);
        }
    }

}
