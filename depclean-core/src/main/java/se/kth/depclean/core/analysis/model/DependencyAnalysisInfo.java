package se.kth.depclean.core.analysis.model;

import java.util.SortedSet;

/** The result of a dependency analysis. */
public record DependencyAnalysisInfo(
    String status,
    String type,
    Long size,
    SortedSet<String> allTypes,
    SortedSet<String> usedTypes) {}
