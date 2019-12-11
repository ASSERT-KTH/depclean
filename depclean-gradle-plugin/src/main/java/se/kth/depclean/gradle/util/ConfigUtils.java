package se.kth.depclean.gradle.util;

import java.util.HashMap;
import java.util.Map;

public class ConfigUtils {

    public static final String BASELINE_DESCRIPTION = "depclean.baselineDescription";
    public static final String CONFIG_FILES = "depclean.configFiles";
    public static final String CONFIG_FILE_ENCODING = "depclean.configFileEncoding";

    // Gradle specific
    public static final String CONFIGURATIONS = "fldepcleanyway.configurations";

    /**
     * Converts DepClean-specific environment variables to their matching properties.
     *
     * @return The properties corresponding to the environment variables.
     */
    public static Map<String, String> environmentVariablesToPropertyMap() {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            String convertedKey = convertKey(entry.getKey());
            if (convertedKey != null) {
                // Known environment variable
                result.put(convertKey(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private static String convertKey(String key) {
        if ("FLYWAY_BASELINE_DESCRIPTION".equals(key)) {
            return BASELINE_DESCRIPTION;
        }
        return null;
    }
}
