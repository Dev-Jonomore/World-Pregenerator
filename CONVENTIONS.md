# Coding Conventions

This file defines the coding standards, patterns, and practices used in this codebase. Follow these conventions when making changes or additions.

## Project Structure
```
mc.jonomore.worldPregenerator/
├── WorldPregenerator.java       # Main plugin class
├── GenerationTask.java          # Core generation logic
├── ConfigManager.java           # Config caching
├── SpawnAdjuster.java          # BFS spawn finder
├── CageBuilder.java            # Structure builder
├── WPCommands.java             # Command definitions
└── Util.java                   # Static utilities
```

## Naming Rules

**Classes**: PascalCase, descriptive nouns indicating purpose
- `GenerationTask`, `SpawnAdjuster`, `CageBuilder` ✓
- `Manager`, `Handler`, `Utils` ✗ (too generic)

**Variables and Methods**: camelCase
- `currentIndex`, `findSafeSpawn()`, `buildCage()` ✓
- `current_index`, `FindSafeSpawn()` ✗

**Configuration Keys**: `kebab-case` in `config.yml`.
- `generation-radius`, `cage-material`, `max-search-radius` ✓
- `generationRadius`, `cage_material` ✗

**Configuration Fields**: `camelCase` in `ConfigManager.java`.
- `generationRadius`, `cageMaterial`, `maxSearchRadius` ✓
- `generation_radius`, `cage_material` ✗

**Boolean Flags**: Use descriptive prefixes
- `interrupted`, `running`, `isSafeSpawn()` ✓
- `flag`, `check()` ✗ (not clear what they represent)

**Constants**: Descriptive names, not SCREAMING_CASE
- `maxVerticalScan`, `maxSearchRadius` ✓
- `MAX_VERTICAL_SCAN`, `SEARCH_RADIUS` ✗

## Code Organization

### Field Declaration Order
Always organize fields in this exact order:
```java
public class Example {
    // 1. Dependencies (final, injected)
    private final PluginType plugin;
    private final ConfigType config;
    
    // 2. Cached configuration values
    private int cacheValue;
    private String exportPath;
    
    // 3. Mutable state
    private int currentState = 0;
    private boolean flag = false;
    
    // 4. Nullable references (explicit = null)
    private BukkitTask task = null;
    private String worldName = null;
}
```

### Method Organization
1. Constructor
2. Public API methods
3. Private implementation methods
4. Static utility methods (if any)

## Logging Standards

**REQUIRED**: Use appropriate log levels for all operations.

```java
// INFO: Normal flow, state changes, completion
logger.info("Processing seed " + seed + " (" + (currentIndex + 1) + "/" + seeds.size() + ")");
logger.info("Chunk generation completed for " + worldName);

// WARNING: Recoverable issues, fallback behavior
logger.warning("Could not find safe spawn location, using default");
logger.warning("Invalid cage material: '" + name + "', using default");

// SEVERE: Errors that prevent operation, always include exception
logger.log(Level.SEVERE, "Error creating world for seed " + seed, exception);
logger.log(Level.SEVERE, "Failed to delete world folder", ioException);
```

**Rules**:
- Never log without context (don't just log "Error" - say what failed)
- Always include exception object for SEVERE logs
- Use descriptive messages that help debugging
- Don't spam INFO logs in tight loops

## Async Operations (CRITICAL)

### Callback Pattern - MANDATORY
All async operations MUST use `Runnable` callbacks and ALWAYS invoke them, even on error.

**Template**:
```java
private void asyncOperation(World world, Runnable onComplete) {
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        try {
            // Do async work
            performHeavyOperation();
            
            // Return to main thread for Bukkit API
            Bukkit.getScheduler().runTask(plugin, onComplete);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Operation failed", e);
            
            // CRITICAL: ALWAYS call callback even on failure
            Bukkit.getScheduler().runTask(plugin, onComplete);
        }
    });
}
```

**Why this matters**: Failing to call the callback will freeze the generation state machine. The next world will never be processed.

### DO NOT DO THIS:
```java
// WRONG - callback not called on error
try {
    performOperation();
    Bukkit.getScheduler().runTask(plugin, onComplete);
} catch (Exception e) {
    logger.log(Level.SEVERE, "Failed", e);
    // Missing callback! State machine now stuck!
}
```

## Exception Handling

### Multi-Catch for Expected Exceptions
When an operation can throw multiple known exceptions, catch them all explicitly:

```java
// DO THIS
try {
    slimeAPI.readVanillaWorld(...);
} catch (IOException | 
         RuntimeException | 
         InvalidWorldException | 
         WorldTooBigException | 
         WorldAlreadyExistsException | 
         WorldLoadedException e) {
    logger.log(Level.SEVERE, "Failed to convert world for seed " + seed, e);
    Bukkit.getScheduler().runTask(plugin, onComplete); // Remember callback!
}
```

### Resilient Method Bodies
Wrap entire method bodies in try-catch when the method is part of a critical flow:

```java
private void processNext() {
    if (interrupted || currentIndex >= seeds.size()) {
        plugin.running = false;
        return;
    }
    
    long seed = seeds.get(currentIndex);
    
    try {
        World world = createWorld(seed);
        generateChunks(world, () -> { /* ... */ });
    } catch (Exception e) {
        logger.log(Level.SEVERE, "Error processing seed " + seed, e);
        moveOn(); // Continue to next seed instead of crashing
    }
}
```

### DON'T Swallow Exceptions
```java
// WRONG - silent failure
try {
    deleteWorld();
} catch (Exception e) {
    // Nothing - user has no idea it failed
}

// CORRECT - log and handle
try {
    deleteWorld();
} catch (Exception e) {
    logger.log(Level.SEVERE, "Failed to delete world", e);
    // Decide: retry? skip? abort?
}
```

## Bukkit API Patterns

### Scheduler Usage Rules

**Delays**: Always use ticks (20 ticks = 1 second)
```java
// Delay between operations (40 ticks = 2 seconds)
Bukkit.getScheduler().runTaskLater(plugin, this::processNext, 40L);
```

**Async File I/O**: REQUIRED for any disk operations
```java
Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
    Util.deleteDirectory(worldFolder);  // Heavy I/O
});
```

**Main Thread Return**: REQUIRED before calling Bukkit API
```java
Bukkit.getScheduler().runTask(plugin, () -> {
    world.setSpawnLocation(location);  // Bukkit API calls only on main thread
});
```

### World Operations - Required Order
```java
// 1. Always save before unloading
world.save();

// 2. Unload without re-saving (false = don't save again)
Bukkit.unloadWorld(world, false);

// 3. Delete files only after unloading (async)
Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
    Util.deleteDirectory(world.getWorldFolder());
});
```

### Location Handling
```java
// Block coordinates
int x = location.getBlockX();
int y = location.getBlockY();
int z = location.getBlockZ();

// Centered locations (spawn points)
new Location(world, x + 0.5, y + 1, z + 0.5);
```

### Material Checks
```java
// Type checks
material.isAir()
Tag.LEAVES.isTagged(material)
material == Material.WATER

// Collision/passability
block.isPassable()
!block.getCollisionShape().getBoundingBoxes().isEmpty()
```

## Command Pattern (Brigadier)

Paper uses Mojang's Brigadier command system:

```java
Commands.literal("worldpregenerator")
    .then(Commands.literal("subcommand")
        .executes(ctx -> {
            // Command logic
            return Command.SINGLE_SUCCESS;
        })
    )
    .then(Commands.literal("witharg")
        .then(Commands.argument("name", StringArgumentType.word())
            .executes(ctx -> {
                String arg = StringArgumentType.getString(ctx, "name");
                // Use arg
                return Command.SINGLE_SUCCESS;
            })
        )
    )
    .build();
```

Message sending with MiniMessage:
```java
ctx.getSource().getSender().sendMessage(
    MiniMessage.miniMessage().deserialize(
        "<gray>[<yellow>Tag<gray>] <red>Message with <click:run_command:/cmd>clickable</click> text"
    )
);
```

## Configuration Access - RULE

**NEVER access plugin.getConfig() during runtime**. Always use ConfigManager's cached values.

```java
// WRONG - bypasses cache, reads file every time
int radius = plugin.getConfig().getInt("generation-radius");

// CORRECT - uses cached value
int radius = config.getRadius();
```

**When to reload**:
```java
public void loadConfig() {
    plugin.reloadConfig();  // Read from disk
    
    // Cache ALL values
    generation_radius = plugin.getConfig().getInt("generation-radius");
    exportPath = plugin.getConfig().getString("export-path");
    // ... etc
}
```

Call `config.loadConfig()` only when:
- Plugin enables
- User runs `/wp reload` command  
- Never during normal operation

## State Management Rules

### Plugin-Level State
```java
boolean running = false;      // Is generation active?
GenerationTask task = null;   // Current task instance (null = no task)
```

### Task-Level State  
```java
private int currentIndex = 0;           // Position in seed list
private boolean interrupted = false;    // Stop requested?
private String currentWorldName = null; // Active world (null = between worlds)
```

### State Transitions
```
IDLE (running=false, task=null)
  ↓ /wp start
RUNNING (running=true, task exists, interrupted=false)
  ↓ /wp stop
STOPPED (running=false, task exists, interrupted=true)
  ↓ /wp start
RESUMING (running=true, task exists, interrupted=false)
  ↓ /wp reset
IDLE (running=false, task=null, currentIndex=0)
```

### Null Safety - Explicit Initialization
Always initialize nullable fields to null explicitly:
```java
// DO THIS
private BukkitTask scheduledTask = null;
private String currentWorldName = null;

// NOT THIS (ambiguous)
private BukkitTask scheduledTask;
private String currentWorldName;
```

Before using, always check:
```java
if (scheduledTask != null) {
    scheduledTask.cancel();
    scheduledTask = null;  // Clear after use
}
```

## File Organization

### Utility Classes
Static methods only, no instantiation:
```java
public class Util {  // lowercase class name for utilities
    static void deleteDirectory(File directory) throws IOException {
        // ...
    }
}
```

### Builder Pattern (minimal)
CageBuilder shows constructor injection pattern:
```java
public class CageBuilder {
    private final Material cageMaterial;  // Immutable config
    private final int cageRadius;
    private final int cageHeight;

    public CageBuilder(Material cageMaterial, int cageRadius, int cageHeight) {
        this.cageMaterial = cageMaterial;
        this.cageRadius = cageRadius;
        this.cageHeight = cageHeight;
    }

    public void buildCage(org.bukkit.World world) { /* ... */ }
}
```

## Performance Optimization Rules

### Memory Efficiency - Iterate, Don't Collect
When processing large coordinate ranges, iterate and process immediately instead of collecting coordinates:

```java
// GOOD - O(1) memory
for (int x = minX; x <= maxX; ++x) {
    for (int y = floorY; y <= ceilingY; y += 2) {  // Skip middle rows
        for (int z = minZ; z <= maxZ; ++z) {
            if (shouldPlace) {
                world.getBlockAt(x, y, z).setType(material, false);
            }
        }
    }
}

// BAD - O(n) memory, stores all coordinates first
List<Location> toPlace = new ArrayList<>();
for (int x = minX; x <= maxX; ++x) {
    for (int y = floorY; y <= ceilingY; ++y) {
        for (int z = minZ; z <= maxZ; ++z) {
            if (shouldPlace) {
                toPlace.add(new Location(world, x, y, z));
            }
        }
    }
}
toPlace.forEach(loc -> loc.getBlock().setType(material));
```

### Bit Packing for Coordinate Storage
Use bit operations to pack two ints into one long when storing coordinates:

```java
// Pack two 32-bit ints into one 64-bit long
static long pack(int x, int z) {
    return (((long) x) << 32) | (z & 0xffffffffL);
}

// Unpack
static int unpackX(long packed) { 
    return (int) (packed >> 32); 
}

static int unpackZ(long packed) { 
    return (int) packed; 
}
```

Use this for BFS/search algorithms where you need to track visited coordinates.

### Block Placement Optimization
Check before setting to avoid unnecessary operations:

```java
// Check if change is needed
if (block.getType() != cageMaterial && passable) {
    block.setType(cageMaterial, false);  // false = skip physics updates
}

// Never blindly set
block.setType(cageMaterial);  // Slower, triggers physics
```
