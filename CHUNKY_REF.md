# Chunky Minecraft Plugin API Documentation

**Version:** 1.4.28
**Last Updated:** February 7, 2026
**Last Modified:** February 8, 2026
**Target Audience:** Intermediate Plugin Developers
**Optimized For:** AI Agent Parsing (Gemini CLI)

---

## Table of Contents

1. [Overview](#overview)
2. [Quick Start Guide](#quick-start-guide)
3. [API Methods Reference](#api-methods-reference)
4. [Authentication](#authentication)
5. [Rate Limits](#rate-limits)
6. [Code Examples](#code-examples)
7. [Event System](#event-system)
8. [Troubleshooting](#troubleshooting)
9. [Additional Resources](#additional-resources)

---

## Overview

Chunky is a high-performance Minecraft chunk pre-generation plugin that supports multiple server platforms including **Spigot**, **Paper**, **Fabric**, **Forge**, **NeoForge**, **Folia**, and **Sponge**. The plugin provides a developer API for programmatically managing chunk generation tasks, monitoring progress, and integrating with other plugins.

### Key Features

- **Multi-platform support**: Works across Bukkit/Spigot, Paper, Fabric, Forge, NeoForge, Folia, and Sponge
- **Efficient chunk generation**: Optimized algorithms for fast, safe chunk pre-generation
- **Flexible shapes**: Support for square, rectangle, circle, ellipse, pentagon, hexagon, octagon, and star patterns
- **Multiple generation patterns**: Concentric, loop, and spiral patterns available
- **Event system**: Listen to generation task lifecycle events
- **Thread-safe**: Safe for concurrent operations
- **Progress tracking**: Real-time progress monitoring and reporting

### Important Note

> **Name Collision Warning:** There are TWO different projects called "Chunky":
> 1. **Chunky by pop4959** (THIS documentation) - Minecraft server plugin for chunk pre-generation
> 2. **Chunky by chunky-dev** - Standalone rendering tool for creating photorealistic Minecraft images
>
> This documentation covers the **pop4959/Chunky** plugin API for chunk pre-generation.

---

## Quick Start Guide

### Installation

#### Step 1: Download the Plugin

Download Chunky from one of the official sources:

- **SpigotMC**: https://www.spigotmc.org/resources/chunky.81534/
- **GitHub Releases**: https://github.com/pop4959/Chunky/releases
- **PaperMC**: Search for "Chunky" on the PaperMC forums
- **Sponge**: https://ore.spongepowered.org/pop4959/Chunky

#### Step 2: Install on Server

1. Place the downloaded JAR file in your server's `plugins/` folder (Bukkit/Spigot/Paper) or `mods/` folder (Fabric/Forge/NeoForge)
2. Restart your server
3. The plugin will generate a configuration file in `plugins/Chunky/config.yml`

### Setup for API Development

#### Maven Setup

Add the CodeMC repository and Chunky dependency to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>codemc</id>
        <url>https://repo.codemc.io/repository/maven-public/</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>org.popcraft</groupId>
        <artifactId>chunky-common</artifactId>
        <version>1.4.28</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

#### Gradle Setup (Kotlin DSL)

Add to your `build.gradle.kts`:

```kotlin
repositories {
    maven("https://repo.codemc.io/repository/maven-public/")
}

dependencies {
    compileOnly(group = "org.popcraft", name = "chunky-common", version = "1.4.28")
}
```

#### Gradle Setup (Groovy DSL)

Add to your `build.gradle`:

```groovy
repositories {
    maven {
        url 'https://repo.codemc.io/repository/maven-public/'
    }
}

dependencies {
    compileOnly 'org.popcraft:chunky-common:1.4.28'
}
```

### First API Call Example

Here's a minimal example to get started with the Chunky API:

```java
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.popcraft.chunky.api.ChunkyAPI;

public class MyPlugin extends JavaPlugin {
    
    private ChunkyAPI chunkyAPI;
    
    @Override
    public void onEnable() {
        // Get ChunkyAPI instance via Bukkit ServicesManager
        chunkyAPI = Bukkit.getServer()
            .getServicesManager()
            .load(ChunkyAPI.class);
        
        if (chunkyAPI == null) {
            getLogger().severe("Chunky not found! Please install Chunky plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Check API version for compatibility
        if (chunkyAPI.version() != 0) {
            getLogger().warning("Unexpected Chunky API version: " + chunkyAPI.version());
        }
        
        // Your integration code here
        getLogger().info("Successfully hooked into Chunky API!");
    }
}
```

### Basic Configuration

The `config.yml` file (located in `plugins/Chunky/`) contains important settings:

```yaml
# Load settings
max-concurrent-tasks: 1        # Maximum concurrent generation tasks
chunks-per-tick: 5             # Chunks generated per server tick
continue-on-restart: false     # Auto-resume tasks after server restart

# Update settings
update-interval-seconds: 1     # Seconds between progress updates
silent: false                  # Disable update messages

# World settings
world-options:
  world:
    pattern: concentric        # Generation pattern
    shape: square              # Region shape
```

---

## API Methods Reference

### Core Interface: ChunkyAPI

**Package**: `org.popcraft.chunky.api`  
**Access**: Via Bukkit ServicesManager

#### Getting the API Instance

```java
ChunkyAPI chunkyAPI = Bukkit.getServer()
    .getServicesManager()
    .load(ChunkyAPI.class);
```

---

### Method: `version()`

**Signature**: `int version()`

**Description**: Returns the current API version number. Used to check for breaking changes.

**Returns**: `int` - API version (currently `0`)

**Thread Safety**: Thread-safe

**Example**:
```java
int apiVersion = chunkyAPI.version();
if (apiVersion != 0) {
    getLogger().warning("Unexpected API version: " + apiVersion);
    // Disable integration or handle incompatibility
}
```

**Notes**:
- API version is bumped for breaking changes
- Check this value before using other API methods
- Current API version is `0` (initial release)

---

### Method: `startTask()`

**Signature**: 
```java
void startTask(
    String world,
    String shape,
    double centerX,
    double centerZ,
    double radiusX,
    double radiusZ,
    String pattern
)
```

**Description**: Starts a new chunk generation task for the specified world with given parameters.

**Parameters**:

| Parameter | Type | Description | Valid Values |
|-----------|------|-------------|--------------|
| `world` | String | World name to generate chunks in | Any loaded world name |
| `shape` | String | Shape of generation region | `"square"`, `"rectangle"`, `"circle"`, `"ellipse"`, `"pentagon"`, `"hexagon"`, `"octagon"`, `"star"` |
| `centerX` | double | X coordinate of region center | Any valid coordinate |
| `centerZ` | double | Z coordinate of region center | Any valid coordinate |
| `radiusX` | double | Radius in blocks (X direction) | Positive number |
| `radiusZ` | double | Radius in blocks (Z direction) | Positive number (same as radiusX for symmetric shapes) |
| `pattern` | String | Generation pattern | `"concentric"`, `"loop"`, `"spiral"` |

**Thread Safety**: Can be called from any thread

**Throws**: No explicit exceptions (logs errors internally)

**Example**:
```java
// Start a square generation centered at 0,0 with 5000 block radius
chunkyAPI.startTask(
    "world",      // world name
    "square",     // shape
    0,            // centerX
    0,            // centerZ
    5000,         // radiusX
    5000,         // radiusZ
    "concentric"  // pattern
);
```

**Advanced Examples**:
```java
// Circular generation
chunkyAPI.startTask("world", "circle", 0, 0, 3000, 3000, "concentric");

// Rectangular generation with different X/Z radius
chunkyAPI.startTask("world", "rectangle", 100, 200, 5000, 3000, "loop");

// Hexagonal generation
chunkyAPI.startTask("world_nether", "hexagon", 0, 0, 2000, 2000, "spiral");
```

**Notes**:
- Only one task per world can run at a time
- If a task already exists for the world, it will be cancelled and replaced
- Use `isRunning()` to check if a task is active before starting
- Default pattern is `"concentric"` (generates from center outward)

---

### Method: `cancelTask()`

**Signature**: `void cancelTask(String world)`

**Description**: Stops and deletes the generation task for the specified world. Task cannot be resumed after cancellation.

**Parameters**:

| Parameter | Type | Description |
|-----------|------|-------------|
| `world` | String | World name whose task should be cancelled |

**Returns**: `void`

**Thread Safety**: Thread-safe

**Example**:
```java
// Cancel generation task for "world"
chunkyAPI.cancelTask("world");
```

**Notes**:
- Cancelled tasks cannot be resumed with `continueTask()`
- Already generated chunks are NOT deleted (use `trim` command for chunk deletion)
- Safe to call even if no task exists for the world
- Task progress is lost upon cancellation

---

### Method: `pauseTask()`

**Signature**: `void pauseTask(String world)`

**Description**: Pauses the generation task for the specified world, saving progress. Can be resumed with `continueTask()`.

**Parameters**:

| Parameter | Type | Description |
|-----------|------|-------------|
| `world` | String | World name whose task should be paused |

**Returns**: `void`

**Thread Safety**: Thread-safe

**Example**:
```java
// Pause generation for "world"
chunkyAPI.pauseTask("world");

// Later... resume the task
chunkyAPI.continueTask("world");
```

**Notes**:
- Progress is saved to disk
- Task can be resumed even after server restart
- Safe to call multiple times
- No-op if task is already paused

---

### Method: `continueTask()`

**Signature**: `void continueTask(String world)`

**Description**: Resumes a paused or saved generation task for the specified world.

**Parameters**:

| Parameter | Type | Description |
|-----------|------|-------------|
| `world` | String | World name whose task should be continued |

**Returns**: `void`

**Thread Safety**: Thread-safe

**Example**:
```java
// Continue/resume generation for "world"
chunkyAPI.continueTask("world");
```

**Notes**:
- Works for paused tasks or tasks from previous server sessions
- Continues from last saved progress
- Safe to call even if task is already running
- No-op if no saved task exists for the world

---

### Method: `isRunning()`

**Signature**: `boolean isRunning(String world)`

**Description**: Checks if a generation task is currently running for the specified world.

**Parameters**:

| Parameter | Type | Description |
|-----------|------|-------------|
| `world` | String | World name to check |

**Returns**: `boolean` - `true` if task is running, `false` otherwise

**Thread Safety**: Thread-safe

**Example**:
```java
if (chunkyAPI.isRunning("world")) {
    getLogger().info("Generation is active for world");
} else {
    getLogger().info("No active generation for world");
}

// Conditional task start
if (!chunkyAPI.isRunning("world")) {
    chunkyAPI.startTask("world", "square", 0, 0, 5000, 5000, "concentric");
}
```

**Notes**:
- Returns `false` for paused tasks
- Returns `false` if world has no task
- Useful for preventing duplicate task starts

---

### Method: `getTasks()`

**Signature**: `Map<String, GenerationTask> getTasks()`

**Description**: Retrieves all current generation tasks (both running and paused).

**Parameters**: None

**Returns**: `Map<String, GenerationTask>` - Map of world names to their generation tasks

**Thread Safety**: Thread-safe

**Example**:
```java
Map<String, GenerationTask> tasks = chunkyAPI.getTasks();

for (Map.Entry<String, GenerationTask> entry : tasks.entrySet()) {
    String worldName = entry.getKey();
    GenerationTask task = entry.getValue();
    
    getLogger().info("World: " + worldName);
    getLogger().info("  Running: " + task.isRunning());
    getLogger().info("  Progress: " + task.getProgress() + "%");
}
```

**GenerationTask Methods**:
```java
// Check if task is running
boolean isRunning = task.isRunning();

// Get progress percentage (0.0 to 100.0)
double progress = task.getProgress();

// Get total chunks to generate
long totalChunks = task.getTotalChunks();

// Get chunks already generated
long completedChunks = task.getCompletedChunks();

// Get shape name
String shape = task.getShape();

// Get pattern name
String pattern = task.getPattern();
```

**Notes**:
- Map includes both active and paused tasks
- Returns empty map if no tasks exist
- Map is a snapshot; changes don't affect actual tasks

---

### Method: `onGenerationComplete()`

**Signature**: `void onGenerationComplete(Consumer<GenerationCompleteEvent> eventConsumer)`

**Description**: Registers a callback to be executed when any generation task completes.

**Parameters**:

| Parameter | Type | Description |
|-----------|------|-------------|
| `eventConsumer` | Consumer<GenerationCompleteEvent> | Callback function to handle completion events |

**Returns**: `void`

**Thread Safety**: Callbacks executed on main server thread

**Example**:
```java
// Register completion listener
chunkyAPI.onGenerationComplete(event -> {
    String world = event.world();
    long chunksGenerated = event.chunksGenerated();
    long duration = event.duration();
    
    getLogger().info("Generation completed for " + world);
    getLogger().info("Chunks generated: " + chunksGenerated);
    getLogger().info("Duration: " + duration + " ms");
    
    // Perform post-generation tasks
    performBackup(world);
});
```

**GenerationCompleteEvent Methods**:

| Method | Return Type | Description |
|--------|-------------|-------------|
| `world()` | String | Name of world that completed |
| `chunksGenerated()` | long | Total chunks generated |
| `duration()` | long | Generation duration in milliseconds |
| `cancelled()` | boolean | Whether task was cancelled |

**Notes**:
- Multiple listeners can be registered
- Callback is executed AFTER generation completes
- NOT called if task is cancelled (unless `cancelled()` returns true)
- Callback runs on main server thread

---

## Authentication

### No API Keys Required

The Chunky API does **not** require API keys, authentication tokens, or credentials. Access control is managed through:

1. **Bukkit ServicesManager**: Only plugins loaded by the server can access the API
2. **Plugin Permissions**: Server operators control which plugins are loaded
3. **No External Access**: API is not exposed externally; only available to server plugins

### Permission System

While the API itself doesn't require authentication, **Chunky commands** use Bukkit's permission system:

#### Player/Console Permissions

| Permission | Command(s) | Description |
|------------|-----------|-------------|
| `chunky.command.start` | `/chunky start` | Start generation tasks |
| `chunky.command.pause` | `/chunky pause` | Pause running tasks |
| `chunky.command.continue` | `/chunky continue` | Resume paused tasks |
| `chunky.command.cancel` | `/chunky cancel` | Cancel tasks |
| `chunky.command.world` | `/chunky world` | Select world |
| `chunky.command.shape` | `/chunky shape` | Select shape |
| `chunky.command.center` | `/chunky center` | Set center coordinates |
| `chunky.command.radius` | `/chunky radius` | Set radius |
| `chunky.command.pattern` | `/chunky pattern` | Set generation pattern |
| `chunky.command.progress` | `/chunky progress` | View progress |
| `chunky.command.reload` | `/chunky reload` | Reload configuration |

**Default**: All permissions default to `OP` (server operators only)

### Security Best Practices

> **Programmatic Access**: When using the API programmatically, ensure your plugin:
> - Validates world names before starting tasks
> - Limits generation sizes to prevent server overload
> - Checks `isRunning()` before starting new tasks
> - Implements rate limiting for automated task creation
> - Logs all API operations for audit trails

---

## Rate Limits

### No Hard Rate Limits

The Chunky API **does not enforce rate limits** on method calls. However, operational constraints exist:

### Operational Limits

1. **One Task Per World**: Only one generation task can run per world at a time
   - Attempting to start a second task will cancel the first
   - Use `isRunning()` to check before starting tasks

2. **Concurrent Task Limit**: Configurable maximum concurrent tasks (default: 1)
   - Set in `config.yml` via `max-concurrent-tasks`
   - Recommended: `1-4` depending on server hardware
   - Higher values increase CPU/memory usage

3. **Chunks Per Tick**: Configurable generation speed (default: 5)
   - Set in `config.yml` via `chunks-per-tick`
   - Higher values = faster generation but more server load
   - Recommended: `1-10` for production servers

### Configuration Example

```yaml
# config.yml
load:
  max-concurrent-tasks: 2    # Run up to 2 tasks simultaneously
  chunks-per-tick: 3         # Generate 3 chunks per tick
```

### Best Practices for High-Volume Usage

```java
// BAD: Rapidly starting/stopping tasks
for (int i = 0; i < 100; i++) {
    chunkyAPI.startTask("world", "square", 0, 0, 1000, 1000, "concentric");
    chunkyAPI.cancelTask("world");  // Wasteful, causes rapid start/stop
}

// GOOD: Check before starting, avoid rapid changes
if (!chunkyAPI.isRunning("world")) {
    chunkyAPI.startTask("world", "square", 0, 0, 1000, 1000, "concentric");
}

// GOOD: Use event listener for sequential tasks
chunkyAPI.onGenerationComplete(event -> {
    if (event.world().equals("world")) {
        // Start next task after current completes
        chunkyAPI.startTask("world_nether", "circle", 0, 0, 2000, 2000, "concentric");
    }
});
```

### Resource Management

> **Warning**: Large generation tasks can:
> - Consume significant CPU and memory
> - Slow down server TPS (ticks per second)
> - Cause lag for players
>
> **Recommendations**:
> - Generate during off-peak hours
> - Monitor server TPS with `/tps` command
> - Start with smaller radii for testing
> - Pause tasks if TPS drops below 18

---

## Code Examples

### Example 1: Basic Generation Task

```java
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.popcraft.chunky.api.ChunkyAPI;

public class BasicGenerationExample extends JavaPlugin {
    
    private ChunkyAPI chunkyAPI;
    
    @Override
    public void onEnable() {
        // Get API instance
        chunkyAPI = Bukkit.getServer()
            .getServicesManager()
            .load(ChunkyAPI.class);
        
        if (chunkyAPI == null) {
            getLogger().severe("Chunky not found!");
            return;
        }
        
        // Check API version
        if (chunkyAPI.version() != 0) {
            getLogger().warning("Unexpected Chunky API version");
        }
        
        // Start a simple square generation
        startGeneration();
    }
    
    private void startGeneration() {
        String world = "world";
        
        // Check if already running
        if (chunkyAPI.isRunning(world)) {
            getLogger().info("Generation already running for " + world);
            return;
        }
        
        // Start generation task
        chunkyAPI.startTask(
            world,        // world name
            "square",     // shape
            0,            // center X
            0,            // center Z
            5000,         // radius X (blocks)
            5000,         // radius Z (blocks)
            "concentric"  // pattern
        );
        
        getLogger().info("Started generation for " + world);
    }
}
```

### Example 2: Progress Monitoring

```java
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.popcraft.chunky.api.ChunkyAPI;
import org.popcraft.chunky.api.GenerationTask;

public class ProgressMonitorExample extends JavaPlugin {
    
    private ChunkyAPI chunkyAPI;
    
    @Override
    public void onEnable() {
        chunkyAPI = Bukkit.getServer()
            .getServicesManager()
            .load(ChunkyAPI.class);
        
        if (chunkyAPI == null) {
            getLogger().severe("Chunky not found!");
            return;
        }
        
        // Start generation
        chunkyAPI.startTask("world", "circle", 0, 0, 3000, 3000, "concentric");
        
        // Monitor progress every 30 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                monitorProgress();
            }
        }.runTaskTimer(this, 0L, 20L * 30);  // 30 seconds
    }
    
    private void monitorProgress() {
        Map<String, GenerationTask> tasks = chunkyAPI.getTasks();
        
        for (Map.Entry<String, GenerationTask> entry : tasks.entrySet()) {
            String worldName = entry.getKey();
            GenerationTask task = entry.getValue();
            
            if (task.isRunning()) {
                double progress = task.getProgress();
                long completed = task.getCompletedChunks();
                long total = task.getTotalChunks();
                
                getLogger().info(String.format(
                    "World: %s | Progress: %.2f%% | Chunks: %d/%d",
                    worldName, progress, completed, total
                ));
            }
        }
    }
}
```

### Example 3: Sequential World Generation

```java
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.popcraft.chunky.api.ChunkyAPI;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

public class SequentialGenerationExample extends JavaPlugin {
    
    private ChunkyAPI chunkyAPI;
    private Queue<String> worldQueue;
    
    @Override
    public void onEnable() {
        chunkyAPI = Bukkit.getServer()
            .getServicesManager()
            .load(ChunkyAPI.class);
        
        if (chunkyAPI == null) {
            getLogger().severe("Chunky not found!");
            return;
        }
        
        // Queue worlds for generation
        worldQueue = new LinkedList<>(Arrays.asList(
            "world",
            "world_nether",
            "world_the_end"
        ));
        
        // Register completion listener
        chunkyAPI.onGenerationComplete(event -> {
            getLogger().info("Completed generation for " + event.world());
            getLogger().info("Chunks generated: " + event.chunksGenerated());
            getLogger().info("Duration: " + (event.duration() / 1000) + " seconds");
            
            // Start next world
            processNextWorld();
        });
        
        // Start first world
        processNextWorld();
    }
    
    private void processNextWorld() {
        if (worldQueue.isEmpty()) {
            getLogger().info("All worlds generated!");
            return;
        }
        
        String world = worldQueue.poll();
        getLogger().info("Starting generation for " + world);
        
        // Different settings per dimension
        switch (world) {
            case "world":
                chunkyAPI.startTask(world, "square", 0, 0, 5000, 5000, "concentric");
                break;
            case "world_nether":
                chunkyAPI.startTask(world, "circle", 0, 0, 3000, 3000, "concentric");
                break;
            case "world_the_end":
                chunkyAPI.startTask(world, "square", 0, 0, 2000, 2000, "concentric");
                break;
        }
    }
}
```

### Example 4: Scheduled Generation

```java
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.popcraft.chunky.api.ChunkyAPI;

import java.time.LocalTime;

public class ScheduledGenerationExample extends JavaPlugin {
    
    private ChunkyAPI chunkyAPI;
    private boolean generationActive = false;
    
    @Override
    public void onEnable() {
        chunkyAPI = Bukkit.getServer()
            .getServicesManager()
            .load(ChunkyAPI.class);
        
        if (chunkyAPI == null) {
            getLogger().severe("Chunky not found!");
            return;
        }
        
        // Check every minute for schedule
        new BukkitRunnable() {
            @Override
            public void run() {
                checkSchedule();
            }
        }.runTaskTimer(this, 0L, 20L * 60);  // Every 60 seconds
    }
    
    private void checkSchedule() {
        LocalTime now = LocalTime.now();
        int hour = now.getHour();
        
        // Generate from 2 AM to 6 AM (off-peak hours)
        boolean shouldGenerate = (hour >= 2 && hour < 6);
        
        if (shouldGenerate && !generationActive) {
            startScheduledGeneration();
        } else if (!shouldGenerate && generationActive) {
            stopScheduledGeneration();
        }
    }
    
    private void startScheduledGeneration() {
        getLogger().info("Starting scheduled generation (off-peak hours)");
        chunkyAPI.startTask("world", "square", 0, 0, 10000, 10000, "concentric");
        generationActive = true;
    }
    
    private void stopScheduledGeneration() {
        getLogger().info("Pausing scheduled generation (peak hours)");
        chunkyAPI.pauseTask("world");
        generationActive = false;
    }
}
```

### Example 5: Conditional Generation with World Border

```java
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.plugin.java.JavaPlugin;
import org.popcraft.chunky.api.ChunkyAPI;

public class WorldBorderGenerationExample extends JavaPlugin {
    
    private ChunkyAPI chunkyAPI;
    
    @Override
    public void onEnable() {
        chunkyAPI = Bukkit.getServer()
            .getServicesManager()
            .load(ChunkyAPI.class);
        
        if (chunkyAPI == null) {
            getLogger().severe("Chunky not found!");
            return;
        }
        
        // Generate chunks within world border
        generateWithinWorldBorder("world");
    }
    
    private void generateWithinWorldBorder(String worldName) {
        World world = Bukkit.getWorld(worldName);
        
        if (world == null) {
            getLogger().warning("World not found: " + worldName);
            return;
        }
        
        WorldBorder border = world.getWorldBorder();
        
        // Get border properties
        double centerX = border.getCenter().getX();
        double centerZ = border.getCenter().getZ();
        double size = border.getSize();
        double radius = size / 2.0;
        
        getLogger().info(String.format(
            "Generating within world border: center=(%.0f, %.0f), radius=%.0f",
            centerX, centerZ, radius
        ));
        
        // Start generation matching world border
        chunkyAPI.startTask(
            worldName,
            border.getShape() == WorldBorder.Shape.SQUARE ? "square" : "circle",
            centerX,
            centerZ,
            radius,
            radius,
            "concentric"
        );
    }
}
```

### Example 6: Multi-Shape Generation

```java
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.popcraft.chunky.api.ChunkyAPI;

public class MultiShapeExample extends JavaPlugin {
    
    private ChunkyAPI chunkyAPI;
    
    @Override
    public void onEnable() {
        chunkyAPI = Bukkit.getServer()
            .getServicesManager()
            .load(ChunkyAPI.class);
        
        if (chunkyAPI == null) {
            getLogger().severe("Chunky not found!");
            return;
        }
        
        // Demonstrate different shapes
        demonstrateShapes();
    }
    
    private void demonstrateShapes() {
        // Square: Equal X and Z radius
        chunkyAPI.startTask("world", "square", 0, 0, 5000, 5000, "concentric");
        
        // Rectangle: Different X and Z radius
        chunkyAPI.startTask("world", "rectangle", 0, 0, 5000, 3000, "concentric");
        
        // Circle: Equal X and Z radius, circular boundary
        chunkyAPI.startTask("world", "circle", 0, 0, 4000, 4000, "concentric");
        
        // Ellipse: Different X and Z radius, elliptical boundary
        chunkyAPI.startTask("world", "ellipse", 0, 0, 6000, 4000, "concentric");
        
        // Pentagon: Five-sided polygon
        chunkyAPI.startTask("world", "pentagon", 0, 0, 3000, 3000, "concentric");
        
        // Hexagon: Six-sided polygon
        chunkyAPI.startTask("world", "hexagon", 0, 0, 3000, 3000, "concentric");
        
        // Octagon: Eight-sided polygon
        chunkyAPI.startTask("world", "octagon", 0, 0, 3000, 3000, "concentric");
        
        // Star: Star-shaped polygon
        chunkyAPI.startTask("world", "star", 0, 0, 3000, 3000, "concentric");
    }
}
```

---

## Event System

### GenerationCompleteEvent

The `GenerationCompleteEvent` is fired when a generation task completes (either successfully or by cancellation).

#### Event Properties

```java
public interface GenerationCompleteEvent {
    
    /**
     * Get the world name that completed generation
     *
     * @return World name
     */
    String world();
    
    /**
     * Get the number of chunks generated
     *
     * @return Number of chunks generated
     */
    long chunksGenerated();
    
    /**
     * Get the duration of generation in milliseconds
     *
     * @return Duration in milliseconds
     */
    long duration();
    
    /**
     * Check if the task was cancelled
     *
     * @return true if cancelled, false if completed normally
     */
    boolean cancelled();
}
```

#### Registering Event Listeners

```java
// Simple listener
chunkyAPI.onGenerationComplete(event -> {
    getLogger().info("Generation completed for " + event.world());
});

// Detailed listener
chunkyAPI.onGenerationComplete(event -> {
    if (event.cancelled()) {
        getLogger().warning("Generation cancelled for " + event.world());
    } else {
        getLogger().info("Successfully generated " + event.chunksGenerated() + " chunks");
        getLogger().info("Duration: " + (event.duration() / 1000) + " seconds");
        
        // Calculate chunks per second
        double chunksPerSecond = (double) event.chunksGenerated() / (event.duration() / 1000.0);
        getLogger().info("Rate: " + String.format("%.2f", chunksPerSecond) + " chunks/second");
    }
});
```

#### Multiple Listeners

```java
// Listener 1: Logging
chunkyAPI.onGenerationComplete(event -> {
    getLogger().info("[LOG] Generation completed: " + event.world());
});

// Listener 2: Database recording
chunkyAPI.onGenerationComplete(event -> {
    recordToDatabase(event.world(), event.chunksGenerated(), event.duration());
});

// Listener 3: Player notification
chunkyAPI.onGenerationComplete(event -> {
    Bukkit.broadcastMessage("§aWorld '" + event.world() + "' has been pre-generated!");
});
```

---

## Troubleshooting

### Common Issues and Solutions

#### Issue 1: ChunkyAPI is Null

**Problem**: `chunkyAPI` instance is `null` after calling `ServicesManager.load()`

**Cause**: Chunky plugin is not installed or not loaded yet

**Solutions**:
```java
// Solution 1: Check and handle null
ChunkyAPI chunkyAPI = Bukkit.getServer()
    .getServicesManager()
    .load(ChunkyAPI.class);

if (chunkyAPI == null) {
    getLogger().severe("Chunky is not installed or not loaded!");
    getLogger().severe("Please install Chunky from: https://www.spigotmc.org/resources/chunky.81534/");
    getServer().getPluginManager().disablePlugin(this);
    return;
}

// Solution 2: Add Chunky as soft dependency in plugin.yml
// plugin.yml:
// softdepend: [Chunky]

// Solution 3: Delay API access until after server startup
@Override
public void onEnable() {
    Bukkit.getScheduler().runTask(this, () -> {
        chunkyAPI = Bukkit.getServer().getServicesManager().load(ChunkyAPI.class);
        if (chunkyAPI != null) {
            // Use API here
        }
    });
}
```

#### Issue 2: Task Not Starting

**Problem**: Calling `startTask()` doesn't start generation

**Possible Causes**:
1. Task already running for that world
2. Invalid world name
3. Invalid parameters

**Solutions**:
```java
// Check if task is already running
if (chunkyAPI.isRunning("world")) {
    getLogger().warning("Task already running for world");
    chunkyAPI.cancelTask("world");  // Cancel existing task
}

// Verify world exists
World world = Bukkit.getWorld("world");
if (world == null) {
    getLogger().severe("World 'world' does not exist!");
    return;
}

// Use valid parameters
chunkyAPI.startTask(
    "world",      // Exact world name (case-sensitive)
    "square",     // Valid shape
    0,            // Valid X coordinate
    0,            // Valid Z coordinate
    5000,         // Positive radius
    5000,         // Positive radius
    "concentric"  // Valid pattern
);
```

#### Issue 3: Server Lag During Generation

**Problem**: Server TPS drops significantly during chunk generation

**Solutions**:
```java
// 1. Reduce chunks-per-tick in config.yml
// config.yml:
// load:
//   chunks-per-tick: 2  # Lower value = slower but less lag

// 2. Pause generation during peak hours
LocalTime now = LocalTime.now();
int hour = now.getHour();

if (hour >= 12 && hour < 22) {  // Peak hours
    chunkyAPI.pauseTask("world");
} else {
    chunkyAPI.continueTask("world");
}

// 3. Monitor TPS and auto-pause
double tps = Bukkit.getTPS()[0];  // 1-minute average
if (tps < 18.0 && chunkyAPI.isRunning("world")) {
    chunkyAPI.pauseTask("world");
    getLogger().warning("Auto-paused generation due to low TPS: " + tps);
}
```

#### Issue 4: API Version Mismatch

**Problem**: API version check fails

**Cause**: Chunky plugin updated with breaking API changes

**Solution**:
```java
int expectedVersion = 0;
int actualVersion = chunkyAPI.version();

if (actualVersion != expectedVersion) {
    getLogger().severe("Chunky API version mismatch!");
    getLogger().severe("Expected: " + expectedVersion + ", Found: " + actualVersion);
    getLogger().severe("Please update your plugin to support API version " + actualVersion);
    
    // Disable integration
    getServer().getPluginManager().disablePlugin(this);
    return;
}
```

#### Issue 5: Events Not Firing

**Problem**: `onGenerationComplete()` listener not called

**Possible Causes**:
1. Task was cancelled (check `cancelled()` in event)
2. Listener registered after task completed
3. Exception in listener code

**Solutions**:
```java
// Register listener BEFORE starting task
chunkyAPI.onGenerationComplete(event -> {
    try {
        getLogger().info("Completed: " + event.world());
        
        if (event.cancelled()) {
            getLogger().info("Task was cancelled");
        }
    } catch (Exception e) {
        // Catch exceptions to prevent silent failures
        getLogger().severe("Error in completion listener: " + e.getMessage());
        e.printStackTrace();
    }
});

// Then start task
chunkyAPI.startTask("world", "square", 0, 0, 5000, 5000, "concentric");
```

#### Issue 6: Memory Issues

**Problem**: OutOfMemoryError during large generation tasks

**Solutions**:
```java
// 1. Increase JVM heap size
// startup script: java -Xmx4G -Xms4G -jar server.jar

// 2. Generate in smaller chunks
int totalRadius = 20000;
int chunkSize = 5000;

for (int i = 0; i < totalRadius; i += chunkSize) {
    int radius = Math.min(i + chunkSize, totalRadius);
    chunkyAPI.startTask("world", "square", 0, 0, radius, radius, "concentric");
    
    // Wait for completion before continuing
    chunkyAPI.onGenerationComplete(event -> {
        // Next iteration...
    });
}

// 3. Monitor memory usage
Runtime runtime = Runtime.getRuntime();
long maxMemory = runtime.maxMemory();
long usedMemory = runtime.totalMemory() - runtime.freeMemory();
double memoryUsage = (double) usedMemory / maxMemory * 100;

if (memoryUsage > 90) {
    chunkyAPI.pauseTask("world");
    getLogger().warning("Paused generation due to high memory usage: " + memoryUsage + "%");
}
```

### Debugging Techniques

#### Enable Debug Logging

```java
// Set Chunky to silent mode to reduce console spam
chunkyAPI.setQuiet(10);  // Update every 10 seconds

// Then use custom logging
new BukkitRunnable() {
    @Override
    public void run() {
        Map<String, GenerationTask> tasks = chunkyAPI.getTasks();
        for (Map.Entry<String, GenerationTask> entry : tasks.entrySet()) {
            GenerationTask task = entry.getValue();
            if (task.isRunning()) {
                getLogger().info(String.format(
                    "[DEBUG] %s: %.2f%% (%d/%d chunks)",
                    entry.getKey(),
                    task.getProgress(),
                    task.getCompletedChunks(),
                    task.getTotalChunks()
                ));
            }
        }
    }
}.runTaskTimer(this, 0L, 20L * 30);  // Every 30 seconds
```

#### Validate API State

```java
public void validateChunkyState() {
    ChunkyAPI api = Bukkit.getServer().getServicesManager().load(ChunkyAPI.class);
    
    getLogger().info("=== Chunky API State ===");
    getLogger().info("API Available: " + (api != null));
    
    if (api != null) {
        getLogger().info("API Version: " + api.version());
        
        Map<String, GenerationTask> tasks = api.getTasks();
        getLogger().info("Active Tasks: " + tasks.size());
        
        for (Map.Entry<String, GenerationTask> entry : tasks.entrySet()) {
            GenerationTask task = entry.getValue();
            getLogger().info(String.format(
                "  - %s: %s (%.2f%%)",
                entry.getKey(),
                task.isRunning() ? "RUNNING" : "PAUSED",
                task.getProgress()
            ));
        }
    }
    
    getLogger().info("========================");
}
```

### Performance Optimization Tips

#### Tip 1: Choose Appropriate Generation Pattern

```java
// Concentric: Best for most cases, generates from center outward
chunkyAPI.startTask("world", "square", 0, 0, 5000, 5000, "concentric");

// Loop: Generates in rows, can be faster for some world types
chunkyAPI.startTask("world", "square", 0, 0, 5000, 5000, "loop");

// Spiral: Alternative pattern, may reduce memory fragmentation
chunkyAPI.startTask("world", "square", 0, 0, 5000, 5000, "spiral");
```

#### Tip 2: Optimize Configuration

```yaml
# Optimal settings for most servers
load:
  max-concurrent-tasks: 1      # Single task prevents resource conflicts
  chunks-per-tick: 3           # Balance between speed and performance
  
update:
  update-interval-seconds: 10  # Reduce console spam

world-options:
  world:
    pattern: concentric
    shape: square
```

#### Tip 3: Schedule During Off-Peak Hours

```java
// Generate during low-activity periods
LocalTime now = LocalTime.now();
int hour = now.getHour();
int playerCount = Bukkit.getOnlinePlayers().size();

// Only generate if time is between 2-6 AM OR less than 5 players online
if ((hour >= 2 && hour < 6) || playerCount < 5) {
    if (!chunkyAPI.isRunning("world")) {
        chunkyAPI.startTask("world", "square", 0, 0, 10000, 10000, "concentric");
    }
} else {
    if (chunkyAPI.isRunning("world")) {
        chunkyAPI.pauseTask("world");
    }
}
```

### Compatibility Issues

#### Folia Compatibility

> **Note**: Chunky has special support for Folia (Paper fork with regional threading). When using Chunky on Folia:
> - API methods are thread-safe
> - Generation tasks run on appropriate regions
> - No special handling required in plugin code

#### Version Compatibility

| Chunky Version | Minecraft Version | API Version |
|----------------|-------------------|-------------|
| 1.4.28 | 1.21+ | 0 |
| 1.4.x | 1.20-1.21 | 0 |
| 1.3.x | 1.19-1.20 | 0 |
| 1.2.x | 1.18-1.19 | 0 |

> **Important**: Always use the latest Chunky version compatible with your Minecraft version for best performance and bug fixes.

---

## Additional Resources

### Official Documentation

- **GitHub Repository**: https://github.com/pop4959/Chunky
- **Wiki**: https://github.com/pop4959/Chunky/wiki
- **Developer API**: https://github.com/pop4959/Chunky/wiki/Developer-API
- **Commands Reference**: https://github.com/pop4959/Chunky/wiki/Commands
- **Configuration Guide**: https://github.com/pop4959/Chunky/wiki/Configuration

### JavaDocs

- **API JavaDocs**: https://pop4959.github.io/Chunky/chunky/javadoc/
- **Main Package**: `org.popcraft.chunky.api`
- **Event Package**: `org.popcraft.chunky.api.event.task`

### Download Sources

- **SpigotMC**: https://www.spigotmc.org/resources/chunky.81534/
- **GitHub Releases**: https://github.com/pop4959/Chunky/releases
- **Sponge Ore**: https://ore.spongepowered.org/pop4959/Chunky

### Maven Repository

- **Repository URL**: https://repo.codemc.io/repository/maven-public/
- **Group ID**: `org.popcraft`
- **Artifact ID**: `chunky-common`
- **Latest Version**: `1.4.28`

### Community Support

- **Discord Server**: https://discord.gg/ZwVJukcNQG
  - #chunky channel for user support
  - #development for API questions

### Related Tools

- **ChunkyBorder**: World border plugin with Chunky integration
- **Dynmap Integration**: Pre-generate chunks for Dynmap rendering
- **BlueMap Integration**: Pre-generate chunks for BlueMap rendering
- **Squaremap Integration**: Pre-generate chunks for Squaremap rendering

### Additional Examples

For more examples and use cases, see:
- **Wiki How-To's**: https://github.com/pop4959/Chunky/wiki/How-To's
- **Pregeneration Guide**: https://github.com/pop4959/Chunky/wiki/Pregeneration
- **World Borders**: https://github.com/pop4959/Chunky/wiki/World-borders

---

## Appendix

### Supported Shapes

| Shape | Description | Parameters |
|-------|-------------|------------|
| `square` | Square region | Same radiusX and radiusZ |
| `rectangle` | Rectangular region | Different radiusX and radiusZ |
| `circle` | Circular region | Same radiusX and radiusZ |
| `ellipse` | Elliptical region | Different radiusX and radiusZ |
| `pentagon` | Five-sided polygon | Same radiusX and radiusZ |
| `hexagon` | Six-sided polygon | Same radiusX and radiusZ |
| `octagon` | Eight-sided polygon | Same radiusX and radiusZ |
| `star` | Star-shaped polygon | Same radiusX and radiusZ |

### Supported Patterns

| Pattern | Description | Use Case |
|---------|-------------|----------|
| `concentric` | Generates from center outward in concentric rings | Most common, balanced approach |
| `loop` | Generates in rows from one side to the other | Can be faster for some world types |
| `spiral` | Generates in a spiral pattern from center | Alternative to concentric, may reduce fragmentation |

### Radius Format Examples

```java
// All equivalent ways to specify 10,000 blocks
chunkyAPI.startTask("world", "square", 0, 0, 10000, 10000, "concentric");
chunkyAPI.startTask("world", "square", 0, 0, 10k, 10k, "concentric");  // k = thousand
chunkyAPI.startTask("world", "square", 0, 0, 625c, 625c, "concentric"); // c = chunks (1 chunk = 16 blocks)
```

### Configuration File Reference

Full `config.yml` structure:

```yaml
# Load settings
load:
  max-concurrent-tasks: 1       # Maximum concurrent generation tasks
  chunks-per-tick: 5            # Chunks generated per tick
  force-load-existing-chunks: false  # Force load already generated chunks

# Update settings
update:
  update-interval-seconds: 1    # Seconds between progress updates
  silent: false                 # Disable all update messages

# Miscellaneous
continue-on-restart: false      # Auto-resume tasks after server restart

# Per-world settings
world-options:
  world:
    pattern: concentric
    shape: square
    center-x: 0
    center-z: 0
    radius-x: 500
    radius-z: 500
```

---

**End of Documentation**

For the latest updates and information, always refer to the official GitHub repository at https://github.com/pop4959/Chunky
