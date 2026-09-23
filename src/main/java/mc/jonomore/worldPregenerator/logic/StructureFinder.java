package mc.jonomore.worldPregenerator.logic;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.generator.structure.Structure;
import org.bukkit.util.StructureSearchResult;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Locates the structure nearest to a point (e.g. a spawn point), restricted to a configured
 * whitelist or blacklist of structure keys (e.g. {@code village_plains}).
 */
public class StructureFinder {

  /**
   * A located structure.
   *
   * @param type      namespaced key of the structure, e.g. {@code minecraft:village_plains}
   * @param location  where the structure was found
   * @param direction compass direction from the search origin to the structure
   */
  public record Result(String type, Location location, String direction) {}

  private final Logger logger;
  private final List<String> structureNames;
  private final boolean whitelist;
  private final int searchRadiusChunks;
  private Map<NamespacedKey, Structure> candidates = null;

  public StructureFinder(Logger logger, List<String> structureNames, boolean whitelist, int searchRadiusBlocks) {
    this.logger = logger;
    this.structureNames = List.copyOf(structureNames);
    this.whitelist = whitelist;
    this.searchRadiusChunks = Math.ceilDiv(searchRadiusBlocks, 16);
  }

  /**
   * Finds the nearest allowed structure to {@code origin}. Must be called on the main thread.
   *
   * @return the nearest structure, or {@code null} if none of the allowed structures are within range
   */
  public Result findNearest(World world, Location origin) {
    Result nearest = null;
    double nearestDistance = Double.MAX_VALUE;

    for (Map.Entry<NamespacedKey, Structure> candidate : getCandidates().entrySet()) {
      Structure structure = candidate.getValue();
      StructureSearchResult result = world.locateNearestStructure(origin, structure, searchRadiusChunks, false);
      if (result == null) continue;

      Location location = result.getLocation();
      double dx = location.getX() - origin.getX();
      double dz = location.getZ() - origin.getZ();
      double distance = dx * dx + dz * dz;
      if (distance < nearestDistance) {
        nearestDistance = distance;
        nearest = new Result(candidate.getKey().toString(), location, direction(origin.getX(), origin.getZ(), location.getX(), location.getZ()));
      }
    }
    return nearest;
  }

  /**
   * Resolves the configured names against the structure registry. Done lazily because
   * worldgen registries are not guaranteed to be populated when the config is loaded.
   */
  private Map<NamespacedKey, Structure> getCandidates() {
    if (candidates != null) return candidates;

    Registry<Structure> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
    Set<NamespacedKey> listed = new HashSet<>();
    for (String name : structureNames) {
      NamespacedKey key = NamespacedKey.fromString(name.toLowerCase());
      if (key == null || registry.get(key) == null) {
        logger.warning("Unknown structure in structure-finder.structures: '" + name + "'");
        continue;
      }
      listed.add(key);
    }

    candidates = new LinkedHashMap<>();
    for (Structure structure : registry) {
      NamespacedKey key = registry.getKeyOrThrow(structure);
      if (listed.contains(key) == whitelist) {
        candidates.put(key, structure);
      }
    }
    if (candidates.isEmpty()) {
      logger.warning("structure-finder has no structures to search for; check structures/whitelist in config.yml");
    }
    return candidates;
  }

  /**
   * Compass direction from one point to another in Minecraft coordinates (+X east, +Z south).
   */
  static String direction(double fromX, double fromZ, double toX, double toZ) {
    double deltaX = toX - fromX;
    double deltaZ = toZ - fromZ;
    double angle = Math.toDegrees(Math.atan2(deltaZ, deltaX));
    if (angle < 0) angle += 360;
    if (angle >= 337.5 || angle < 22.5) return "EAST";
    if (angle < 67.5) return "SOUTHEAST";
    if (angle < 112.5) return "SOUTH";
    if (angle < 157.5) return "SOUTHWEST";
    if (angle < 202.5) return "WEST";
    if (angle < 247.5) return "NORTHWEST";
    if (angle < 292.5) return "NORTH";
    return "NORTHEAST";
  }
}
