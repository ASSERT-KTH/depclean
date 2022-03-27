package se.kth.depclean.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import se.kth.depclean.core.model.ClassName;

class ClassNameTest {

  public static final String CLASS_NAME_RESULT = "org.my.Class";

  @Test
  void shouldNormalizeName() {
    assertThat(new ClassName("org/my/Class").getValue()).isEqualTo(CLASS_NAME_RESULT);
    assertThat(new ClassName("org/my/Class.class").getValue()).isEqualTo(CLASS_NAME_RESULT);
    assertThat(new ClassName("org.my.Class").getValue()).isEqualTo(CLASS_NAME_RESULT);
    assertThat(new ClassName("org.my.Class.class").getValue()).isEqualTo(CLASS_NAME_RESULT);
  }
}