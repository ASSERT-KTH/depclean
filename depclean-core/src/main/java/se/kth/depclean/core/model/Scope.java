package se.kth.depclean.core.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Represents a dependency scope.
 */
@Getter
@ToString
@EqualsAndHashCode
@AllArgsConstructor
public class Scope {
  private final String value;
}
