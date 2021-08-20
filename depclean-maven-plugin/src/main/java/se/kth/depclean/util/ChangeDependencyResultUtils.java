package se.kth.depclean.util;

import java.util.Objects;

/**
 * This will help in changing the status of the dependencies involved in
 * dependent modules of a multi-module java project.
 */
public class ChangeDependencyResultUtils {

  private final String dependencyCoordinate;
  private final String module;
  private final String type;

  /**
   * Ctor.
   *
   * @param dependencyCoordinate Target dependency.
   * @param module Target module.
   * @param type Debloat status.
   */
  public ChangeDependencyResultUtils(final String dependencyCoordinate,
                                     final String module,
                                     final String type) {
    this.dependencyCoordinate = dependencyCoordinate;
    this.module = module;
    this.type = type;
  }

  // Getters -------------------------------------------------------------
  public String getDependencyCoordinate() {
    return dependencyCoordinate;
  }

  public String getModule() {
    return module;
  }

  public String getType() {
    return type;
  }

  /**
   * Return the new type (status) of the dependency.
   *
   * @return New type
   */
  public String getNewType() {
    // Changing the status of debloat.
    String newType;
    if (Objects.equals(type, "unusedDirect")) {
      newType = "usedDirect";
    } else if (Objects.equals(type, "unusedTransitive")) {
      newType = "usedTransitive";
    } else {
      newType = "usedInherited";
    }
    return newType;
  }
}
