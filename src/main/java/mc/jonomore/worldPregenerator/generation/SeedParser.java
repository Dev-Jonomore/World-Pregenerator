package mc.jonomore.worldPregenerator.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses lines of the seeds file.
 *
 * <p>Format: {@code seed/x1,y1,z1/x2,y2,z2/...} where each {@code x,y,z} group is a spawn point,
 * e.g. {@code 12345/100,64,200/-50,70,30/0,80,-120}. At least one spawn point is required.
 */
public final class SeedParser {

  private static final Pattern LINE = Pattern.compile("^(-?\\d+)(?:\\s*/\\s*-?\\d+\\s*,\\s*-?\\d+\\s*,\\s*-?\\d+)+$");
  private static final Pattern POINT = Pattern.compile("/\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)");

  private SeedParser() {}

  /**
   * @param line a line from the seeds file; surrounding whitespace is ignored
   * @return the parsed seed and spawn points
   * @throws IllegalArgumentException if the line doesn't match the format or a number is out of range
   */
  public static SeedEntry parse(String line) {
    String trimmed = line.trim();
    Matcher matcher = LINE.matcher(trimmed);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Line does not match seed pattern: " + trimmed);
    }
    try {
      long seed = Long.parseLong(matcher.group(1));
      List<SpawnPoint> spawnPoints = new ArrayList<>();
      Matcher pointMatcher = POINT.matcher(trimmed);
      while (pointMatcher.find()) {
        spawnPoints.add(new SpawnPoint(
            Integer.parseInt(pointMatcher.group(1)),
            Integer.parseInt(pointMatcher.group(2)),
            Integer.parseInt(pointMatcher.group(3))
        ));
      }
      return new SeedEntry(seed, spawnPoints);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Failed to parse numbers in line: " + trimmed, e);
    }
  }
}
