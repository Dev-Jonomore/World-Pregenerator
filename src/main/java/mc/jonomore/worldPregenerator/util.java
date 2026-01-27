package mc.jonomore.worldPregenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class util {
  static void copyDirectory(File src, File dst) throws IOException {
    if (!dst.exists()) {
      dst.mkdirs();
    }

    File[] files = src.listFiles();
    if (files != null) {
      for (File file : files) {
        File dstFile = new File(dst, file.getName());

        if (file.isDirectory()) {
          copyDirectory(file, dstFile);
        } else {
          Files.copy(file.toPath(), dstFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

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
