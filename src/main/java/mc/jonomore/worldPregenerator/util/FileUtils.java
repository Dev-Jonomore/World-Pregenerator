package mc.jonomore.worldPregenerator.util;

import org.jspecify.annotations.NonNull;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class FileUtils {
  private static final int MAX_RETRIES = 3;
  private static final long INITIAL_BACKOFF = 1000; // 1 second

  public static void zipDirectory(File sourceDir, File zipFile, Set<String> exclusions) throws IOException {
    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
      zip(sourceDir, sourceDir, zos, exclusions);
    } catch (IOException e) {
      if (zipFile.exists()) {
        if (zipFile.delete()) {
          System.err.println("Zip operation failed, removing corrupt zip file: " + zipFile.getAbsolutePath());
        }
      }
      throw e;
    }
  }

  public static boolean validateZipFile(File zipFile) {
    if (!zipFile.exists() || !zipFile.isFile()) return false;
    try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zipFile)) {
      java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zf.entries();
      while (entries.hasMoreElements()) {
        java.util.zip.ZipEntry entry = entries.nextElement();
        try (java.io.InputStream is = zf.getInputStream(entry)) {
          // Just read one byte to verify accessibility
          is.read();
        }
      }
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Alternative verification logic — confirms the file is readable and a valid non-empty zip.
   */
  public static Path verifyZip(Path zipPath) throws IOException {
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

    return zipPath;
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

  /**
   * Compresses a directory into a zip archive.
   *
   * <p>Exclusions use a hybrid approach:
   * <ul>
   *   <li>{@code dirExclusions} — absolute {@link Path} objects matched against directories;
   *       matching directories are pruned entirely (no traversal of children).</li>
   *   <li>{@code extensionExclusions} — strings like {@code ".json"} or {@code ".log"} matched
   *       against filenames; safe for extension filtering without substring collision risk.</li>
   * </ul>
   *
   * @param sourceDir           the directory to compress
   * @param outputZip           the output zip path (created or overwritten)
   * @param dirExclusions       absolute paths of directories to exclude entirely; may be null or empty
   * @param extensionExclusions file extensions or exact filenames to exclude (e.g. {@code ".log"},
   *                            {@code "session.lock"}); may be null or empty
   * @return the path of the created zip file
   * @throws IllegalArgumentException if required inputs are null
   * @throws IOException if the source is invalid, writing fails, or the archive is empty/corrupt
   */
  public static Path zipDirectory(
      Path sourceDir,
      Path outputZip,
      Set<Path> dirExclusions,
      Set<String> extensionExclusions
  ) throws IOException {
    Objects.requireNonNull(sourceDir, "Source directory cannot be null");
    Objects.requireNonNull(outputZip, "Output zip path cannot be null");

    if (!Files.isDirectory(sourceDir)) {
      throw new IOException("Source is not a directory or does not exist: " + sourceDir);
    }

    Path sourceDirNormalized = sourceDir.toAbsolutePath().normalize();

    // Normalize dir exclusions once up front
    Set<Path> normalizedDirExclusions = dirExclusions == null ? Set.of() : dirExclusions.stream()
        .map(p -> p.toAbsolutePath().normalize())
        .collect(Collectors.toUnmodifiableSet());

    Set<String> normalizedExtExclusions = extensionExclusions == null ? Set.of() : extensionExclusions;

    Files.createDirectories(outputZip.getParent());

    // int[] to allow mutation inside SimpleFileVisitor
    int[] filesAdded = {0};

    try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(outputZip)))) {
      Files.walkFileTree(sourceDirNormalized, new SimpleFileVisitor<>() {

        @Override
        public @NonNull FileVisitResult preVisitDirectory(@NonNull Path dir, @NonNull BasicFileAttributes attrs) throws IOException {
          Path normalized = dir.toAbsolutePath().normalize();

          // SKIP_SUBTREE actually stops traversal — unlike `continue` with Files.walk
          if (normalizedDirExclusions.contains(normalized)) {
            return FileVisitResult.SKIP_SUBTREE;
          }

          // Write explicit directory entry (except the root itself)
          if (!normalized.equals(sourceDirNormalized)) {
            String entryName = sourceDirNormalized.relativize(normalized).toString().replace('\\', '/') + "/";
            zos.putNextEntry(new ZipEntry(entryName));
            zos.closeEntry();
          }

          return FileVisitResult.CONTINUE;
        }

        @Override
        public @NonNull FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) throws IOException {
          String fileName = file.getFileName().toString();

          // Skip files matching exact name or extension (e.g. "session.lock", ".log")
          if (normalizedExtExclusions.stream().anyMatch(fileName::endsWith)) {
            return FileVisitResult.CONTINUE;
          }

          String entryName = sourceDirNormalized.relativize(file.toAbsolutePath().normalize())
              .toString().replace('\\', '/');

          zos.putNextEntry(new ZipEntry(entryName));
          try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            in.transferTo(zos);
          }
          zos.closeEntry();
          filesAdded[0]++;

          return FileVisitResult.CONTINUE;
        }

        @Override
        public @NonNull FileVisitResult visitFileFailed(@NonNull Path file, @NonNull IOException exc) throws IOException {
          throw exc; // don't silently skip unreadable files
        }
      });
    }

    if (filesAdded[0] == 0) {
      Files.deleteIfExists(outputZip);
      throw new IOException("Zip archive is empty (directory was empty or all files excluded): " + outputZip);
    }

    // Structural verify — reopen and confirm entry count is non-zero
    try (ZipFile verify = new ZipFile(outputZip.toFile())) {
      if (verify.size() == 0) {
        throw new IOException("Zip verification failed — archive contains no entries: " + outputZip);
      }
    }

    return outputZip;
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

  public static Path resolveDimensionFolder(Path worldRoot, String dimension) {
    return worldRoot.resolve("dimensions").resolve("minecraft").resolve(dimension);
  }
}