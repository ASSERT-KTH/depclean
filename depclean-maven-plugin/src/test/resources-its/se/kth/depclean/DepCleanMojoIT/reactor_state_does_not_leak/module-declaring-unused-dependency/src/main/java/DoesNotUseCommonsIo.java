/**
 * This module declares commons-io but never uses it, so commons-io must be reported as unused
 * here, even though the module analyzed before this one does use it.
 */
public class DoesNotUseCommonsIo {

  public String hello() {
    return "hello";
  }
}
