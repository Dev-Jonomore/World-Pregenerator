package mc.jonomore.worldPregenerator;

import java.io.File;
import java.io.IOException;

public class Util {
  static void deleteDirectory(File directory) throws IOException {
    if (directory.exists()) {
      File[] files = directory.listFiles();
      if (files != null) {
        for (File file : files) {
          if (file.isDirectory()) {
            deleteDirectory(file);
          } else {
            if (!file.delete()) {
              throw new IOException("Failed to delete file: " + file);
            }
          }
        }
      }
      if (!directory.delete()) {
        throw new IOException("Failed to delete directory: " + directory);
      }
    }
  }
}
