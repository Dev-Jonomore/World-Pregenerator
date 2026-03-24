package mc.jonomore.worldPregenerator.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileUtils {
  private static final int MAX_RETRIES = 3;
  private static final long INITIAL_BACKOFF = 1000; // 1 second

  public static void zipDirectory(File sourceDir, File zipFile, Set<String> exclusions) throws IOException {
    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
      zip(sourceDir, sourceDir, zos, exclusions);
    }
  }

  private static void zip(File rootDir, File sourceFile, ZipOutputStream zos, Set<String> exclusions) throws IOException {
    if (exclusions != null && exclusions.contains(sourceFile.getName())) {
      return;
    }

    if (sourceFile.isDirectory()) {
      File[] files = sourceFile.listFiles();
      if (files != null) {
        for (File file : files) {
          zip(rootDir, file, zos, exclusions);
        }
      }
    } else {
      String entryName = rootDir.toPath().relativize(sourceFile.toPath()).toString();
      ZipEntry zipEntry = new ZipEntry(entryName);
      zos.putNextEntry(zipEntry);
      try (FileInputStream fis = new FileInputStream(sourceFile)) {
        byte[] buffer = new byte[8192];
        int length;
        while ((length = fis.read(buffer)) >= 0) {
          zos.write(buffer, 0, length);
        }
      }
      zos.closeEntry();
    }
  }

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