package se.kth.depclean.core.analysis.model;

import java.util.TreeSet;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * The result of a dependency analysis.
 */
@Getter
@ToString
@EqualsAndHashCode
@AllArgsConstructor
public class DependencyAnalysisInfo {
  private final String status;
  private final String type;
  private final Long size;
  private final TreeSet<String> allTypes;
  private final TreeSet<String> usedTypes;
}
