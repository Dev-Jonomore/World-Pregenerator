package mc.jonomore.worldPregenerator.logic;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.util.BoundingBox;
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
   * @param location  center of the structure in x/z, with y at the ground or ocean floor there
   * @param direction compass direction from the search origin to the structure
   */
  public record Result(String type, Location location, String direction) {}

  private final Logger logger;
  private final List<String> structureNames;
  private final boolean whitelist;
  private final int searchRadiusBlocks;
  private final int searchRadiusChunks;
  private Map<NamespacedKey, Structure> candidates = null;

  public StructureFinder(Logger logger, List<String> structureNames, boolean whitelist, int searchRadiusBlocks) {
    this.logger = logger;
    this.structureNames = List.copyOf(structureNames);
    this.whitelist = whitelist;
    this.searchRadiusBlocks = searchRadiusBlocks;
    this.searchRadiusChunks = Math.ceilDiv(searchRadiusBlocks, 16);
  }

  /**
   * Finds the nearest allowed structure to {@code origin} within the search radius (horizontal
   * distance). Must be called on the main thread; generates the chunk at the structure it returns.
   *
   * @return the nearest structure, or {@code null} if none of the allowed structures are within range
   */
  public Result findNearest(World world, Location origin) {
    String nearestType = null;
    Location nearest = null;
    double nearestDistanceSq = (double) searchRadiusBlocks * searchRadiusBlocks;

    for (Map.Entry<NamespacedKey, Structure> candidate : getCandidates().entrySet()) {
      Structure structure = candidate.getValue();
      // The radius only roughly bounds the search (for spread-out structures vanilla counts it in
      // placement regions, not chunks), so results can lie far outside it; they are filtered below
      StructureSearchResult result = world.locateNearestStructure(origin, structure, searchRadiusChunks, false);
      if (result == null) continue;

      Location location = structureCenter(world, structure, result.getLocation());
      double dx = location.getX() - origin.getX();
      double dz = location.getZ() - origin.getZ();
      double distanceSq = dx * dx + dz * dz;
      if (distanceSq <= nearestDistanceSq) {
        nearestDistanceSq = distanceSq;
        nearestType = candidate.getKey().toString();
        nearest = location;
      }
    }
    if (nearest == null) return null;

    nearest.setY(world.getHighestBlockYAt(nearest.getBlockX(), nearest.getBlockZ(), HeightMap.OCEAN_FLOOR));
    return new Result(nearestType, nearest, direction(origin.getX(), origin.getZ(), nearest.getX(), nearest.getZ()));
  }

  /**
   * {@code locateNearestStructure} only reports the structure's starting chunk. Look up the
   * structure in that chunk to get the center of its bounding box, falling back to the chunk.
   *
   * <p>Only x/z are used: some structures (shipwrecks, desert pyramids, swamp huts, ...) keep a
   * placeholder y in their bounding box even after they have been placed.
   */
  private static Location structureCenter(World world, Structure structure, Location chunkLocation) {
    int chunkX = chunkLocation.getBlockX() >> 4;
    int chunkZ = chunkLocation.getBlockZ() >> 4;
    for (GeneratedStructure generated : world.getStructures(chunkX, chunkZ, structure)) {
      BoundingBox box = generated.getBoundingBox();
      return new Location(world, box.getCenterX(), 0, box.getCenterZ());
    }
    return chunkLocation;
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
