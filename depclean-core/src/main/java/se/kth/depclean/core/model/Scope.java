package se.kth.depclean.core.model;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Represents a dependency scope. */
public class Scope {
  private final String value;

  public Scope(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Scope scope = (Scope) o;
    return Objects.equals(value, scope.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  public String toString() {
    return "Scope(value=" + value + ")";
  }
}
