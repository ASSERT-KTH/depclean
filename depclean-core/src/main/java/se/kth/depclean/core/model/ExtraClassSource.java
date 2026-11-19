package se.kth.depclean.core.model;

/**
 * Where the analysis found a class that the project uses although its bytecode does not reference
 * it. Such classes are starting points of the analysis, next to the classes of the project.
 */
public enum ExtraClassSource {

  /** An annotation processor configured in the build. */
  ANNOTATION_PROCESSOR,

  /**
   * An import in the sources of the project. It covers what the compiler inlines or erases, such as
   * constants and source-retention annotations.
   */
  IMPORT,

  /** A class named in an XML resource, e.g. a Spring XML configuration or a web.xml. */
  XML_RESOURCE
}
