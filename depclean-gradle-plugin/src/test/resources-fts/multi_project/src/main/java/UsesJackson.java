import com.fasterxml.jackson.databind.ObjectMapper;

/** The root project uses jackson-databind, so it is a used dependency here. */
public class UsesJackson {
  private static final ObjectMapper CONVERTER = new ObjectMapper();

  public String convert(Object value) throws Exception {
    return CONVERTER.writeValueAsString(value);
  }
}
