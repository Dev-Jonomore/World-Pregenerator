package mc.jonomore.worldPregenerator.util;

import org.jspecify.annotations.NonNull;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

public class FileUtils {
  private static final int MAX_RETRIES = 3;
  private static final long INITIAL_BACKOFF = 1000; // 1 second

  /**
   * Alternative verification logic — confirms the file is readable and a valid non-empty zip.
   */
  public static void verifyZip(Path zipPath) throws IOException {
    if (!Files.isRegularFile(zipPath)) {
      throw new IOException("Zip file not found or is not a regular file: " + zipPath);
    }
    if (!Files.isReadable(zipPath)) {
      throw new IOException("Zip file is not readable: " + zipPath);
    }

    try (ZipFile zf = new ZipFile(zipPath.toFile())) {
      if (!zf.entries().hasMoreElements()) {
        throw new IOException("Zip file is empty: " + zipPath);
      }
    } catch (IOException e) {
      throw new IOException("Zip file is corrupt or invalid: " + zipPath, e);
    }

    try {
      ManhuntYaml YAML = ManhuntYaml.fromZip(zipPath);
      if (YAML.spawnPoints() == null || YAML.spawnPoints().isEmpty()) {
        throw new IOException("Zip file missing critical world metadata in manhunt.yml: " + zipPath);
      }
    } catch (IOException e) {
      throw new IOException("Zip file is missing manhunt.yml: " + zipPath, e);
    }
  }

  /**
   * Recursively deletes a directory and all its contents.
   * No-op if the directory does not exist.
   *
   * @param directory the directory to delete
   * @throws IllegalArgumentException if input is null
   * @throws IOException if any file or directory cannot be deleted, or the path still exists after deletion
   */
  public static void deleteDirectory(Path directory) throws IOException {
    Objects.requireNonNull(directory, "Directory cannot be null");

    if (!Files.exists(directory)) return;

    if (!Files.isDirectory(directory)) {
      throw new IOException("Path is not a directory: " + directory);
    }

    Files.walkFileTree(directory, new SimpleFileVisitor<>() {
      @Override
      public @NonNull FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public @NonNull FileVisitResult visitFileFailed(@NonNull Path file, @NonNull IOException exc) throws IOException {
        throw exc;
      }

      @Override
      public @NonNull FileVisitResult postVisitDirectory(@NonNull Path dir, IOException exc) throws IOException {
        if (exc != null) throw exc;
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
      }
    });

    if (Files.exists(directory)) {
      throw new IOException("Directory still exists after deletion attempt: " + directory);
    }
  }

  public static void deleteDirectoryWithRetry(Path directory, Logger logger, Consumer<Boolean> callback) {
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
          logger.log(Level.SEVERE, "Failed to delete directory after " + MAX_RETRIES + " attempts: " + directory.toAbsolutePath(), e);
          if (callback != null) callback.accept(false);
          return;
        }
        logger.log(Level.WARNING, "Failed to delete directory (attempt " + attempts + "): " + directory.toAbsolutePath() + ". Retrying in " + backoff + "ms...");
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