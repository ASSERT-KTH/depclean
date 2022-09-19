import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.commons.io.FileUtils;

/**
 * From https://docs.oracle.com/en/java/javase/16/language/records.html
 */
record Rectangle(double length, double width) {

  // Static field
  static double goldenRatio;

  // Nested record class
  record RotationAngle(double angle) {

    public RotationAngle {
      angle = Math.toRadians(angle);
    }
  }

  public Rectangle {
    if (length <= 0 || width <= 0) {
      throw new IllegalArgumentException(
          String.format("Invalid dimensions: %f, %f", length, width));
    }
  }

  // Static initializer
  static {
    goldenRatio = 1 + Math.sqrt(5) / 2;
  }

  // Static method
  public static Rectangle createGoldenRectangle(double width) {
    return new Rectangle(width, width * goldenRatio);
  }

  // Public instance method
  public Rectangle getRotatedRectangleBoundingBox(double angle) {
    RotationAngle ra = new RotationAngle(angle);
    double x = Math.abs(length * Math.cos(ra.angle())) +
        Math.abs(width * Math.sin(ra.angle()));
    double y = Math.abs(length * Math.sin(ra.angle())) +
        Math.abs(width * Math.cos(ra.angle()));
    return new Rectangle(x, y);
  }

  public void saveToFile(Path path) throws IOException {
    FileUtils.writeStringToFile(new File(path.toUri()), Double.toString(this.length + this.width), "UTF-8");
  }
}