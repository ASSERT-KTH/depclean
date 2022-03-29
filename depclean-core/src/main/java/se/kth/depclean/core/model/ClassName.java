package se.kth.depclean.core.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a class to be analysed.
 */
@Getter
@EqualsAndHashCode
public class ClassName implements Comparable<ClassName> {
  private final String value;

  /**
   * Creates a class representation by its name, and rename it in a defined format.
   *
   * @param name the class name
   */
  public ClassName(String name) {
    String className = name.replace('/', '.');
    if (className.endsWith(".class")) {
      className = className.substring(0, className.length() - ".class".length());
    }
    this.value = className;
  }

  @Override
  public String toString() {
    return value;
  }

  @Override
  public int compareTo(@NotNull ClassName cn) {
    return value.compareTo(cn.value);
  }
}
