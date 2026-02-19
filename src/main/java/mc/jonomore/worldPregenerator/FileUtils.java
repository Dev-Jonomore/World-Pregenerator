package mc.jonomore.worldPregenerator;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileUtils {
  private static final int MAX_RETRIES = 3;
  private static final long INITIAL_BACKOFF = 1000; // 1 second

  public static void deleteDirectory(File directory) throws IOException {
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

  public static void deleteDirectoryWithRetry(File directory, Logger logger, Consumer<Boolean> callback) {
    int attempts = 0;
    long backoff = INITIAL_BACKOFF;

    while (attempts < MAX_RETRIES) {
      try {
        deleteDirectory(directory);
        if (callback != null) callback.accept(true);
        return;
      } catch (IOException e) {
        attempts++;
        if (attempts >= MAX_RETRIES) {
          logger.log(Level.SEVERE, "Failed to delete directory after " + MAX_RETRIES + " attempts: " + directory.getAbsolutePath(), e);
          if (callback != null) callback.accept(false);
          return;
        }
        logger.log(Level.WARNING, "Failed to delete directory (attempt " + attempts + "): " + directory.getAbsolutePath() + ". Retrying in " + backoff + "ms...");
        try {
          Thread.sleep(backoff);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          if (callback != null) callback.accept(false);
          return;
        }
        backoff *= 2;
      }
    }
  }
}