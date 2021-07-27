package se.kth.depclean;

import lombok.SneakyThrows;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;


/**
 * Depclean default and only action.
 */
public class DepCleanGradleAction implements Action<Project> {

  @SneakyThrows
  @Override
  public void execute(@NotNull Project project) {
    // To be continued.
  }
}
