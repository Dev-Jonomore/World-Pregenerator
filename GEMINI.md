# Project: World Pregenerator

Paper/Bukkit plugin that batch-generates Minecraft worlds from seed lists, converts them to SlimeWorld format, and exports them with safe spawn points and protective cages.

### Current Issues:

@./ISSUES.md

## General Instructions
- Follow existing code patterns when modifying or extending functionality
- Always handle async operations with proper callbacks to avoid state machine deadlocks
- Check `interrupted` flag in all major operations before proceeding
- Log operations at appropriate levels: info for flow, warning for recoverable issues, severe for errors with exceptions
- When creating new features, consider the generation pipeline flow and where they fit

## Tech Stack
- **Platform**: Paper 1.21 (Minecraft server)
- **Language**: Java 21
- **Build**: Gradle 
- **Dependencies**:
  - Paper API 1.21.11-R0.1-SNAPSHOT
  - Chunky 1.3.38 (chunk generation)
  - AdvancedSlimePaper 4.0.0-SNAPSHOT (world format conversion)

## Architecture

### Core Flow
1. Read seeds from file → 2. Create vanilla world → 3. Generate chunks with Chunky → 4. Adjust spawn point → 5. Build protective cage → 6. Convert to SlimeWorld → 7. Export → 8. Delete vanilla world → 9. Repeat

### Key Classes

**WorldPregenerator** (Main plugin class)
- Lifecycle management
- Command registration via Paper's Brigadier integration
- Single `GenerationTask` instance per batch

**GenerationTask** (State machine coordinator)
- Processes seeds sequentially with async operations
- Tracks: `currentIndex`, `interrupted`, `currentWorldName`
- Coordinates: world creation → chunky generation → spawn adjustment → cage building → slime export → cleanup
- Uses BukkitScheduler for delays and async file operations

**SpawnAdjuster** (BFS spawn finder)
- Implements breadth-first search on 2D (x,z) grid
- Vertical scanning strategy: starts at hint Y, scans down if in air, up if in ground
- Packs coordinates: `long pack(int x, int z)` = `(((long)x) << 32) | (z & 0xffffffffL)`
- Safety checks: passable feet/head, solid ground with collision, not air/water/lava/leaves

**CageBuilder** (Iterative structure builder)
- Builds glass cage at spawn (floor, ceiling, walls)
- O(1) memory - no collections, pure iteration
- Only places blocks where: `isFloorOrCeiling || isWall` and block is passable

**ConfigManager** (Config cache)
- Loads config.yml on init and reload
- Caches all values to avoid repeated file reads
- Material parsing with fallback for invalid values

**WPCommands** (Brigadier command tree)
- Uses Paper's modern command API (not bukkit CommandExecutor)
- Reset command has 30-second confirmation window via scheduled task
- Commands: start, stop, reset, confirm, reload, info, config

## Data Flow

### World Generation Pipeline
```
Seed → WorldCreator (vanilla) → Chunky API (chunk gen) → SpawnAdjuster (BFS) → 
CageBuilder (structure) → SlimeAPI (conversion) → FileLoader (export) → 
Util.deleteDirectory (cleanup)
```

### Async Boundaries
- Chunky generation: async via Chunky's event system
- Slime conversion: `runTaskAsynchronously`
- World deletion: `runTaskAsynchronously`
- Callbacks use `runTask` to return to main thread

## Configuration

**config.yml structure**:
```yaml
generation-radius: 1200  # Chunky radius
export-path: "/path/to/export"
seeds-file: "/path/to/seeds.txt"
spawn-adjustment:
  maxSearchRadius: 100  # BFS horizontal limit
  maxVerticalScan: 128   # Vertical scan range
cage-building:
  cage-material: PURPLE_STAINED_GLASS
  cage-radius: 4
  cage-height: 3
```

**Seeds file format**: One seed per line, parseable as `long`

## Build & Run

```bash
# Build plugin JAR
./gradlew build

# Run test server (via run-paper plugin)
./gradlew runServer

# JAR output
build/libs/worldPregenerator-1.0-SNAPSHOT.jar
```

## Coding Style
- Use camelCase for methods and variables, PascalCase for classes
- Configuration cache fields use snake_case: `generation_radius`, `cage_material`
- Javadoc public methods especially in GenerationTask
- Always invoke callbacks even on error paths to prevent deadlocks
- Prefix boolean flags: `interrupted`, `running`
- Initialize nullable fields explicitly: `= null`
- Use bit-packing for coordinate storage when memory-efficient data structures needed

### Further Reading:

@./CONVENTIONS.md

## Critical Patterns

### Coordinate Packing
SpawnAdjuster uses bit-packing to store 2D coordinates in a single `long`:
```java
static long pack(int x, int z) {
    return (((long) x) << 32) | (z & 0xffffffffL);
}
static int unpackX(long packed) { return (int) (packed >> 32); }
static int unpackZ(long packed) { return (int) packed; }
```

### Async Safety
GenerationTask coordinates async operations with callbacks:
```java
// Wrong: Direct continuation after async call
saveAndExportToSlime(world, seed);
unloadWorld(world); // Runs before export finishes!

// Correct: Callback-based coordination
saveAndExportToSlime(world, seed, () -> {
    unloadWorld(world);
    moveOn();
});
```

### Interruption Handling
All major operations check `interrupted` flag:
```java
private void processNext() {
    if (interrupted || currentIndex >= seeds.size()) {
        // Clean exit
        plugin.running = false;
        return;
    }
    // ... continue
}
```

### Material Validation
ConfigManager validates materials with fallback:
```java
Material parsed = Material.getMaterial(configString);
if (parsed == null) {
    logger.warning("Invalid material, using default");
    material = Material.PURPLE_STAINED_GLASS;
}
```

## Common Pitfalls to Avoid

1. **Chunky events fire for all worlds** - Always check `event.world().equals(worldName)` in listener
2. **World names must be unique** - Use `"world_" + currentIndex` pattern
3. **SlimeWorld conversion blocks** - Always run in `runTaskAsynchronously`
4. **Callbacks are mandatory** - ALWAYS call onComplete callback even in error paths or generation hangs
5. **Config changes need restart** - Reload doesn't affect running generation, must stop/start
6. **Spawn Y is a hint** - SpawnAdjuster scans vertically from this point, not absolute position
7. **BukkitScheduler tasks survive disable** - Cancel all tasks in `onDisable()`
8. **Cage only modifies passable blocks** - Prevents accidentally destroying existing structures

## API Usage Patterns

**Chunky API** (chunk generation):
```java
chunky.startTask(worldName, shape, centerX, centerZ, radiusX, radiusZ, pattern);
chunky.onGenerationComplete(event -> { ... });
chunky.cancelTask(worldName);
```

### Further reading:

@./CHUNKY_REF.md

**AdvancedSlimePaper API** (world conversion):
```java
SlimeLoader loader = new FileLoader(exportDir);
SlimeWorld slime = slimeAPI.readVanillaWorld(worldDir, worldName, loader);
```

### Further reading:

@./ASP_REF.md

**Paper Lifecycle Events** (modern command registration):
```java
getLifecycleManager().registerEventHandler(
    LifecycleEvents.COMMANDS,
    commands -> commands.registrar().register(...)
);
```
