package se.kth.depclean.core.model;

import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Represents a class to be analysed. */
public final class ClassName implements Comparable<ClassName> {
  private final String value;

  /**
   * Creates a class representation by its name, and rename it in a defined format.
   *
   * @param name the class name
   */
  public ClassName(@NonNull String name) {
    String className = name.replace('/', '.');
    if (className.endsWith(".class")) {
      className = className.substring(0, className.length() - ".class".length());
    }
    this.value = className;
  }

  public String getValue() {
    return value;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ClassName)) {
      return false;
    }
    ClassName className = (ClassName) o;
    return Objects.equals(value, className.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  @NonNull
  public String toString() {
    return value;
  }

  @Override
  public int compareTo(@NonNull ClassName cn) {
    return value.compareTo(cn.value);
  }
}
