package se.kth.depclean.core;

import java.io.IOException;

/**
 * Writes the debloated configuration file of a build tool.
 *
 * <p>This is the non-generic view of {@link AbstractDebloater} that the core needs: callers only
 * ever ask a debloater to write itself out, and they do not know which build-tool-specific
 * dependency type it works with.
 */
public interface Debloater {

  /** Writes the debloated config file down. */
  void write() throws IOException;
}
