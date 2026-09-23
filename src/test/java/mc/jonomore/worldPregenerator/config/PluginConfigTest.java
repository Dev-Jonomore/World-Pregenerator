package mc.jonomore.worldPregenerator.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PluginConfigTest {

  @TempDir
  Path tempDir;

  private static PluginConfig load(String yaml) throws IOException {
    return YamlConfigurationLoader.builder()
        .source(() -> new BufferedReader(new java.io.StringReader(yaml)))
        .build()
        .load()
        .get(PluginConfig.class);
  }

  /** The bundled config.yml and the PluginConfig field defaults are maintained separately; keep them in sync. */
  @Test
  void bundledConfigMatchesDefaults() throws IOException {
    PluginConfig bundled;
    try (InputStream in = Objects.requireNonNull(getClass().getResourceAsStream("/config.yml"))) {
      bundled = YamlConfigurationLoader.builder()
          .source(() -> new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
          .build()
          .load()
          .get(PluginConfig.class);
    }
    PluginConfig defaults = new PluginConfig();

    assertEquals(defaults.generationRadius, bundled.generationRadius);
    assertEquals(defaults.generationArea, bundled.generationArea);
    assertEquals(defaults.spawnPointsMargin, bundled.spawnPointsMargin);
    assertEquals(defaults.serverId, bundled.serverId);
    assertEquals(defaults.exportPath, bundled.exportPath);
    assertEquals(defaults.seedsFile, bundled.seedsFile);
    assertEquals(defaults.worldDelayTicks, bundled.worldDelayTicks);
    assertEquals(defaults.batchSettings.worldsPerBatch, bundled.batchSettings.worldsPerBatch);
    assertEquals(defaults.batchSettings.pauseBetweenBatches, bundled.batchSettings.pauseBetweenBatches);
    assertEquals(defaults.structureFinder.whitelist, bundled.structureFinder.whitelist);
    assertEquals(defaults.structureFinder.searchRadius, bundled.structureFinder.searchRadius);
    assertEquals(defaults.structureFinder.structures, bundled.structureFinder.structures);
    assertEquals(defaults.spawnVerification.enabled, bundled.spawnVerification.enabled);
    assertEquals(defaults.spawnVerification.respawnRadius, bundled.spawnVerification.respawnRadius);
    assertEquals(defaults.spawnVerification.minValidFraction, bundled.spawnVerification.minValidFraction);
    assertEquals(defaults.spawnVerification.snapRadius, bundled.spawnVerification.snapRadius);
  }

  @Test
  void emptyFileUsesDefaults() throws IOException {
    PluginConfig cfg = load("");
    PluginConfig defaults = new PluginConfig();

    assertEquals(defaults.generationRadius, cfg.generationRadius);
    assertEquals(defaults.structureFinder.structures, cfg.structureFinder.structures);
    assertEquals(defaults.batchSettings.worldsPerBatch, cfg.batchSettings.worldsPerBatch);
  }

  @Test
  void partialFileKeepsGivenValuesAndDefaultsTheRest() throws IOException {
    PluginConfig cfg = load("""
        generation-radius: 1500
        structure-finder:
          whitelist: false
          structures:
            - mineshaft
        """);

    assertEquals(1500, cfg.generationRadius);
    assertFalse(cfg.structureFinder.whitelist);
    assertEquals(List.of("mineshaft"), cfg.structureFinder.structures);
    assertEquals(new PluginConfig().structureFinder.searchRadius, cfg.structureFinder.searchRadius);
    assertEquals(new PluginConfig().serverId, cfg.serverId);
  }

  /** {@code /wp config} stores values as strings; they must still load into typed fields. */
  @Test
  void stringValuesConvertToFieldTypes() throws IOException {
    Path file = tempDir.resolve("config.yml");
    YamlConfigurationLoader loader = YamlConfigurationLoader.builder().path(file).build();
    CommentedConfigurationNode root = loader.load();
    root.node("generation-radius").set("2000");
    root.node("structure-finder", "whitelist").set("false");
    root.node("batch-settings", "pause-between-batches").set("5");
    loader.save(root);

    PluginConfig cfg = load(Files.readString(file));

    assertEquals(2000, cfg.generationRadius);
    assertFalse(cfg.structureFinder.whitelist);
    assertEquals(5L, cfg.batchSettings.pauseBetweenBatches);
  }
}
