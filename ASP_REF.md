---
title: AdvancedSlimePaperAPI Technical Documentation
version: 4.1.0
api_package: com.infernalsuite.asp.api
purpose: AI Agent Reference - Gemini CLI Optimized
last_updated: 2025-02-07
last_modified: 2026-02-08
sources:
  - https://infernalsuite.com/docs/asp
  - https://docs.infernalsuite.com/
  - https://github.com/InfernalSuite/AdvancedSlimePaper
---

# AdvancedSlimePaperAPI Technical Documentation

## API Overview

**AdvancedSlimePaperAPI** is a specialized API for creating and managing Minecraft worlds using the Slime Region Format (SRF). The API is embedded directly in the AdvancedSlimePaper server fork (based on Paper/Purpur/Pufferfish), providing native support for the slime world format.

### Core Capabilities

The API provides three primary capabilities:

1. **Create `.slime` files** - Generate new slime world files from scratch or from existing vanilla worlds
2. **Load vanilla worlds** - Import standard Minecraft anvil format worlds into the slime format
3. **Convert worlds** - Transform vanilla worlds to the more efficient SRF format with compression and optimization

### Key Benefits

- **Performance**: Worlds load faster using Zstd compression instead of zlib
- **Storage Efficiency**: Significant disk space reduction compared to vanilla format
- **Multi-source Support**: Load worlds from file system, MySQL, MongoDB, Redis, or custom loaders
- **Optimized for Minigames**: Designed for small worlds like minigame maps, lobbies, and per-player worlds

### Important Notes

- **Not suitable for large survival worlds** - Optimized for smaller worlds only
- **Locking system removed in v4.0** - Manual world locking must be implemented by developers
- **Thread safety** - Most methods can be called asynchronously except where noted

---

## Core Components

### Main API Class

**`AdvancedSlimePaperAPI`** (Interface)
- **Package**: `com.infernalsuite.asp.api`
- **Purpose**: Main entry point for all slime world operations
- **Access**: `AdvancedSlimePaperAPI.instance()`

### Key Interfaces and Classes

#### World Representation
- **`SlimeWorld`**: In-memory representation of a SRF world (immutable structure)
- **`SlimeWorldInstance`**: Live representation of a loaded world on the server
- **`SlimeChunk`**: In-memory representation of a SRF chunk
- **`SlimeChunkSection`**: In-memory representation of a SRF chunk section

#### Data Loading
- **`SlimeLoader`**: Interface for loading worlds from data sources
- **`SlimeSerializationAdapter`**: Handles serialization/deserialization of slime worlds
- **`UpdatableLoader`**: Extended loader interface for custom implementations

#### Properties
- **`SlimePropertyMap`**: Container for world properties (spawn, difficulty, environment, etc.)
- **`SlimeProperties`**: Static property definitions

#### Data Converters
- **`SlimeDataConverter`**: Converts between different data formats
- **`SlimeNMSBridge`**: Bridge to NMS (Net Minecraft Server) internals

---

## Key Methods/Endpoints

### Getting the API Instance

```java
AdvancedSlimePaperAPI asp = AdvancedSlimePaperAPI.instance();
```

### Creating Empty Worlds

**Method**: `createEmptyWorld(String worldName, boolean readOnly, SlimePropertyMap propertyMap, @Nullable SlimeLoader loader)`

- **Purpose**: Creates a new empty slime world
- **Thread Safety**: Can be called asynchronously
- **Returns**: `SlimeWorld` (in-memory representation)
- **Note**: Does NOT load or save the world automatically

**Parameters**:
- `worldName`: Name of the world
- `readOnly`: If true, changes won't be stored
- `propertyMap`: World properties (spawn, difficulty, etc.)
- `loader`: SlimeLoader for storage, or null for temporary worlds

### Converting Vanilla Worlds to Slime Format

**Method**: `readVanillaWorld(File worldDir, String worldName, @Nullable SlimeLoader loader)`

- **Purpose**: Reads a vanilla (anvil format) world and converts to SRF
- **Thread Safety**: Should be called asynchronously (I/O operation)
- **Returns**: `SlimeWorld` (in-memory representation)
- **Note**: Does NOT load or save the world automatically

**Throws**:
- `InvalidWorldException`: World directory doesn't contain valid world
- `WorldLoadedException`: World is currently loaded on server
- `WorldTooBigException`: World exceeds size limits
- `IOException`: I/O error during reading
- `WorldAlreadyExistsException`: World already exists in loader

**Parameters**:
- `worldDir`: Directory containing the world
- `worldName`: Name of the world to read
- `loader`: SlimeLoader for future storage, or null for read-only

### Reading Existing Slime Worlds

**Method**: `readWorld(SlimeLoader loader, String worldName, boolean readOnly, SlimePropertyMap propertyMap)`

- **Purpose**: Reads an existing slime world from a loader
- **Thread Safety**: Should be called asynchronously (I/O operation)
- **Returns**: `SlimeWorld` (in-memory representation)

**Throws**:
- `UnknownWorldException`: World cannot be found
- `IOException`: Cannot obtain world from data source
- `CorruptedWorldException`: World cannot be parsed
- `NewerFormatException`: World uses newer SRF version

**Parameters**:
- `loader`: SlimeLoader to read from
- `worldName`: Name of the world
- `readOnly`: Whether to enable read-only mode
- `propertyMap`: Properties to apply to the world

### Loading Worlds into Server

**Method**: `loadWorld(SlimeWorld world, boolean callWorldLoadEvent)`

- **Purpose**: Loads a SlimeWorld into the server's world list
- **Thread Safety**: MUST be called synchronously (server thread)
- **Returns**: `SlimeWorldInstance` (live world representation)

**Parameters**:
- `world`: The deserialized world to load
- `callWorldLoadEvent`: Whether to fire the Bukkit WorldLoadEvent

**Getting Bukkit World**:
```java
SlimeWorldInstance worldInstance = asp.loadWorld(world, true);
World bukkitWorld = worldInstance.getBukkitWorld();
```

### Saving Worlds

**Method**: `saveWorld(SlimeWorld world)`

- **Purpose**: Saves a SlimeWorld to its associated loader
- **Thread Safety**: Should be called asynchronously (I/O operation)
- **Note**: Blocks until save completes
- **Recommended**: Use this instead of `World.save()` for slime worlds

### Migrating Worlds Between Loaders

**Method**: `migrateWorld(SlimeWorld world, SlimeLoader targetLoader)`

- **Purpose**: Migrates a world from one data source to another
- **Thread Safety**: Should be called asynchronously (I/O operation)

### Getting Loaded Worlds

**Method**: `getLoadedWorld(String worldName)`
- **Returns**: `SlimeWorldInstance` or null if not loaded

**Method**: `getLoadedWorlds()`
- **Returns**: Immutable `List<SlimeWorldInstance>` of all loaded slime worlds

### Checking World Status

**Method**: `isWorldLoaded(String worldName)`
- **Returns**: boolean indicating if world is loaded

---

## Usage Patterns

### Pattern 1: Creating and Loading an Empty World

```java
// Get API instance
AdvancedSlimePaperAPI asp = AdvancedSlimePaperAPI.instance();

// Async: Create empty world
SlimePropertyMap properties = new SlimePropertyMap();
properties.setValue(SlimeProperties.SPAWN_X, 0);
properties.setValue(SlimeProperties.SPAWN_Y, 64);
properties.setValue(SlimeProperties.SPAWN_Z, 0);
properties.setValue(SlimeProperties.DIFFICULTY, "peaceful");
properties.setValue(SlimeProperties.ENVIRONMENT, "normal");

SlimeWorld world = asp.createEmptyWorld("my_world", false, properties, loader);

// Async: Save world
asp.saveWorld(world);

// Sync: Load into server
Bukkit.getScheduler().runTask(plugin, () -> {
    SlimeWorldInstance instance = asp.loadWorld(world, true);
    World bukkitWorld = instance.getBukkitWorld();
    // World is now accessible
});
```

### Pattern 2: Converting a Vanilla World to Slime Format

```java
AdvancedSlimePaperAPI asp = AdvancedSlimePaperAPI.instance();
SlimeLoader loader = new FileLoader("slime_worlds");

// Async: Read vanilla world and convert
Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
    try {
        SlimeWorld world = asp.readVanillaWorld(
            new File("."), 
            "world", 
            loader
        );
        
        // Save to loader
        asp.saveWorld(world);
        
        // Sync: Load into server
        Bukkit.getScheduler().runTask(plugin, () -> {
            SlimeWorldInstance instance = asp.loadWorld(world, true);
            World bukkitWorld = instance.getBukkitWorld();
        });
    } catch (Exception e) {
        e.printStackTrace();
    }
});
```

### Pattern 3: Loading an Existing Slime World

```java
AdvancedSlimePaperAPI asp = AdvancedSlimePaperAPI.instance();
SlimeLoader loader = new FileLoader("slime_worlds");

// Async: Read world from loader
Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
    try {
        SlimePropertyMap properties = new SlimePropertyMap();
        properties.setValue(SlimeProperties.DIFFICULTY, "normal");
        properties.setValue(SlimeProperties.PVP, false);
        
        SlimeWorld world = asp.readWorld(loader, "world", false, properties);
        
        // Sync: Load into server
        Bukkit.getScheduler().runTask(plugin, () -> {
            SlimeWorldInstance instance = asp.loadWorld(world, true);
            World bukkitWorld = instance.getBukkitWorld();
        });
    } catch (Exception e) {
        e.printStackTrace();
    }
});
```

### Pattern 4: Cloning a World

```java
// Clone with new loader (world will be writable)
SlimeWorld clonedWorld = originalWorld.clone("world_copy", newLoader);

// Clone without loader (read-only)
SlimeWorld readOnlyClone = originalWorld.clone("world_copy");
```

### Pattern 5: Using Different Loaders

```java
// File Loader
SlimeLoader fileLoader = new FileLoader("slime_worlds");

// MySQL Loader (requires configuration)
SlimeLoader mysqlLoader = new MySQLLoader(
    "127.0.0.1", 3306, 
    "database_name", 
    "username", "password"
);

// MongoDB Loader (requires configuration)
SlimeLoader mongoLoader = new MongoDBLoader(
    "127.0.0.1", 27017,
    "database_name", "collection_name",
    "username", "password", "admin"
);

// Redis Loader
SlimeLoader redisLoader = new RedisLoader(/* parameters */);
```

---

## Dependencies

### Maven Configuration

Add the repository and dependency:

```xml
<repositories>
    <repository>
        <id>infernalsuite-snapshots</id>
        <url>https://repo.infernalsuite.com/repository/maven-snapshots/</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.infernalsuite.aswm</groupId>
        <artifactId>api</artifactId>
        <version>4.0.0-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### Gradle Configuration

```gradle
repositories {
    maven {
        url = 'https://repo.infernalsuite.com/repository/maven-snapshots/'
    }
}

dependencies {
    compileOnly 'com.infernalsuite.aswm:api:4.0.0-SNAPSHOT'
}
```

### Loader Dependencies

If using reference loaders, add specific loader dependencies:

```gradle
dependencies {
    implementation 'com.infernalsuite.asp:file-loader:4.0.0-SNAPSHOT'
    implementation 'com.infernalsuite.asp:mysql-loader:4.0.0-SNAPSHOT'
    implementation 'com.infernalsuite.asp:mongo-loader:4.0.0-SNAPSHOT'
    implementation 'com.infernalsuite.asp:redis-loader:4.0.0-SNAPSHOT'
    // Or all loaders:
    implementation 'com.infernalsuite.asp:loaders:4.0.0-SNAPSHOT'
}
```

### Required Environment

- **Server**: AdvancedSlimePaper, ASPufferfish, or AdvancedSlimePurpur
- **Java**: 17 or higher
- **Minecraft Version**: Depends on ASP build (currently supports 1.21+)
- **NBT Library**: Kyori NBT (automatically included)

---

## Code Examples

### Complete Plugin Example

```java
public class MySlimePlugin extends JavaPlugin {
    private SlimeLoader loader;
    private final AdvancedSlimePaperAPI asp = AdvancedSlimePaperAPI.instance();

    @Override
    public void onEnable() {
        // Initialize loader
        loader = new FileLoader("slime_worlds");
        
        // Load world on startup
        loadMyWorld();
    }

    private void loadMyWorld() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                // Configure properties
                SlimePropertyMap properties = new SlimePropertyMap();
                properties.setValue(SlimeProperties.SPAWN_X, 0);
                properties.setValue(SlimeProperties.SPAWN_Y, 64);
                properties.setValue(SlimeProperties.SPAWN_Z, 0);
                properties.setValue(SlimeProperties.DIFFICULTY, "normal");
                properties.setValue(SlimeProperties.ALLOW_MONSTERS, false);
                properties.setValue(SlimeProperties.ALLOW_ANIMALS, false);
                properties.setValue(SlimeProperties.PVP, false);
                properties.setValue(SlimeProperties.ENVIRONMENT, "normal");
                properties.setValue(SlimeProperties.WORLD_TYPE, "DEFAULT");
                
                // Read world from loader
                SlimeWorld world = asp.readWorld(
                    loader, 
                    "game_world", 
                    false, 
                    properties
                );
                
                // Load on main thread
                Bukkit.getScheduler().runTask(this, () -> {
                    SlimeWorldInstance instance = asp.loadWorld(world, true);
                    World bukkitWorld = instance.getBukkitWorld();
                    getLogger().info("World loaded: " + bukkitWorld.getName());
                });
                
            } catch (UnknownWorldException e) {
                getLogger().warning("World not found, creating new one...");
                createNewWorld();
            } catch (Exception e) {
                getLogger().severe("Failed to load world: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void createNewWorld() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                SlimePropertyMap properties = new SlimePropertyMap();
                properties.setValue(SlimeProperties.SPAWN_X, 0);
                properties.setValue(SlimeProperties.SPAWN_Y, 64);
                properties.setValue(SlimeProperties.SPAWN_Z, 0);
                
                SlimeWorld world = asp.createEmptyWorld(
                    "game_world", 
                    false, 
                    properties, 
                    loader
                );
                
                // Save world
                asp.saveWorld(world);
                
                // Load into server
                Bukkit.getScheduler().runTask(this, () -> {
                    SlimeWorldInstance instance = asp.loadWorld(world, true);
                    getLogger().info("New world created and loaded!");
                });
                
            } catch (Exception e) {
                getLogger().severe("Failed to create world: " + e.getMessage());
            }
        });
    }
}
```

### Import Vanilla World Example

```java
public void importVanillaWorld(File worldDirectory, String worldName) {
    AdvancedSlimePaperAPI asp = AdvancedSlimePaperAPI.instance();
    SlimeLoader loader = new FileLoader("slime_worlds");
    
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        try {
            // Step 1: Read vanilla world and convert to slime format
            getLogger().info("Converting vanilla world to slime format...");
            SlimeWorld world = asp.readVanillaWorld(
                worldDirectory, 
                worldName, 
                loader
            );
            
            // Step 2: Save to loader
            getLogger().info("Saving slime world...");
            asp.saveWorld(world);
            
            // Step 3: Load into server
            getLogger().info("Loading world into server...");
            Bukkit.getScheduler().runTask(plugin, () -> {
                SlimeWorldInstance instance = asp.loadWorld(world, true);
                World bukkitWorld = instance.getBukkitWorld();
                getLogger().info("World successfully imported: " + bukkitWorld.getName());
            });
            
        } catch (InvalidWorldException e) {
            getLogger().severe("Invalid world directory: " + e.getMessage());
        } catch (WorldTooBigException e) {
            getLogger().severe("World is too big for slime format: " + e.getMessage());
        } catch (IOException e) {
            getLogger().severe("I/O error: " + e.getMessage());
        } catch (Exception e) {
            getLogger().severe("Failed to import world: " + e.getMessage());
            e.printStackTrace();
        }
    });
}
```

---

## Common Issues

### Issue 1: World Not Found

**Problem**: `UnknownWorldException` when trying to load a world

**Solutions**:
- Verify the world exists in the specified loader
- Check world name spelling
- Ensure loader is properly configured
- For file loader, check the directory path

### Issue 2: Concurrent Modification

**Problem**: World corruption or errors when multiple servers access same world

**Solutions**:
- Use read-only mode for shared worlds: `readWorld(loader, name, true, properties)`
- Implement manual locking system (removed in v4.0)
- Ensure only one server writes to a world at a time

### Issue 3: Loading Large Worlds

**Problem**: `WorldTooBigException` when importing vanilla worlds

**Solutions**:
- AdvancedSlimePaper is not designed for large survival worlds
- Use only for small worlds (minigame maps, lobbies, arenas)
- Consider splitting large worlds into smaller regions

### Issue 4: Async/Sync Threading Issues

**Problem**: `IllegalStateException` or server crashes

**Solutions**:
- Always call I/O operations asynchronously (readWorld, saveWorld, readVanillaWorld)
- Always call server operations synchronously (loadWorld)
- Use `Bukkit.getScheduler().runTask()` for sync operations
- Use `Bukkit.getScheduler().runTaskAsynchronously()` for async operations

### Issue 5: NBT Compatibility Issues

**Problem**: Errors with NBT data after migrating from v3.0

**Solutions**:
- Update imports from FlowNBT to Kyori NBT
- Kyori NBT is immutable - create new instances when modifying
- Update package names from `com.infernalsuite.aswm.api` to `com.infernalsuite.asp.api`

### Issue 6: Read-only World Changes

**Problem**: Changes to read-only worlds are not saved

**Expected Behavior**: This is intentional
- Set `readOnly` to `false` when reading/creating world
- Ensure loader is provided (not null)
- Call `saveWorld()` after modifications

### Issue 7: World Cloning Behavior Changed

**Problem**: Cloned worlds behave differently in v4.0

**Solution**: 
- In v4.0, cloned worlds are writable by default (readOnly = false)
- Only clones without a loader are read-only
- This is a change from previous versions

---

## Version Information

### Current Version: 4.1.0

**API Version**: 4.1.0  
**Server Fork**: AdvancedSlimePaper (Paper/Purpur/Pufferfish based)  
**Minecraft Compatibility**: 1.21+ (varies by build)

### Version History

- **v4.0.0**: Major API rework
  - Package rename: `com.infernalsuite.aswm.api` → `com.infernalsuite.asp.api`
  - NBT library change: FlowNBT → Kyori NBT
  - Locking system removed
  - Main class renamed: `SlimePlugin` → `AdvancedSlimePaperAPI`
  - Loader separation into individual packages
  - `loadWorld()` now returns `SlimeWorldInstance`
  - Clone behavior change: default to writable

- **v3.0.0**: Previous stable version
  - Different package structure
  - Included locking system
  - FlowNBT library

### Migration Notes

When upgrading from v3.0 to v4.0+:

1. Update package imports
2. Replace FlowNBT with Kyori NBT
3. Remove locking system dependencies
4. Update `loadWorld()` call to use returned `SlimeWorldInstance`
5. Review clone operations if used
6. Update loader imports to specific packages

---

## Resource Links

### Official Documentation
- Main Documentation: https://infernalsuite.com/docs/asp
- API Docs: https://infernalsuite.com/docs/asp/api/
- Setup Guide: https://infernalsuite.com/docs/asp/setup
- FAQ: https://infernalsuite.com/docs/asp/faq

### JavaDocs
- API JavaDocs: https://docs.infernalsuite.com/
- Main API Class: https://docs.infernalsuite.com/com/infernalsuite/asp/api/AdvancedSlimePaperAPI.html

### Source Code
- GitHub Repository: https://github.com/InfernalSuite/AdvancedSlimePaper
- Pufferfish Fork: https://github.com/InfernalSuite/ASPufferfish
- Purpur Fork: https://github.com/InfernalSuite/AdvancedSlimePurpur

### Downloads
- Download API: https://api.infernalsuite.com/swagger/index.html
- Builds: https://infernalsuite.com/download/asp

### Community
- Discord: https://discord.infernalsuite.com/
- SpigotMC: https://www.spigotmc.org/resources/authors/infernalsuite.1524369/

---

## Additional Technical Details

### Slime File Format (*.slime)

Slime files use the Slime Region Format (SRF) which:
- Uses Zstd compression instead of zlib
- Removes unnecessary data from vanilla format
- Optimized for quick loading and small storage
- Binary format designed by Hypixel development team

### Thread Safety Model

**Asynchronous Operations** (I/O bound):
- `readWorld()` - Reading from data source
- `readVanillaWorld()` - Converting vanilla world
- `saveWorld()` - Writing to data source
- `migrateWorld()` - Moving between data sources

**Synchronous Operations** (Server thread):
- `loadWorld()` - Adding world to server's world list
- All Bukkit world interactions

### Property System

`SlimePropertyMap` supports the following properties:

**Spawn Properties**:
- `SPAWN_X` (int)
- `SPAWN_Y` (int)
- `SPAWN_Z` (int)

**Gameplay Properties**:
- `DIFFICULTY` (String: "peaceful", "easy", "normal", "hard")
- `ALLOW_MONSTERS` (boolean)
- `ALLOW_ANIMALS` (boolean)
- `PVP` (boolean)
- `DRAGON_BATTLE` (boolean)

**World Properties**:
- `ENVIRONMENT` (String: "normal", "nether", "the_end")
- `WORLD_TYPE` (String: "DEFAULT", "FLAT", "LARGE_BIOMES", "AMPLIFIED")
- `DEFAULT_BIOME` (String: "minecraft:plains", etc.)

### Custom Loader Implementation

To create a custom loader, implement the `SlimeLoader` interface:

```java
public class CustomLoader implements SlimeLoader {
    @Override
    public byte[] loadWorld(String worldName) throws UnknownWorldException, IOException {
        // Load world data from your data source
    }
    
    @Override
    public void saveWorld(String worldName, byte[] serializedWorld) throws IOException {
        // Save world data to your data source
    }
    
    @Override
    public boolean worldExists(String worldName) {
        // Check if world exists in your data source
    }
    
    @Override
    public List<String> listWorlds() {
        // List all available worlds
    }
    
    @Override
    public void deleteWorld(String worldName) throws UnknownWorldException, IOException {
        // Delete world from your data source
    }
}
```

---

## Best Practices for AI Agents

When using this API programmatically:

1. **Always check world existence** before loading
2. **Handle exceptions gracefully** - multiple exception types possible
3. **Use proper threading** - I/O async, server operations sync
4. **Create property maps explicitly** - don't rely on defaults
5. **Save worlds after modifications** - changes are in-memory only
6. **Close resources properly** - especially with database loaders
7. **Test with small worlds first** - verify behavior before production
8. **Monitor memory usage** - in-memory representation can be large
9. **Use read-only mode for templates** - prevents accidental modifications
10. **Implement proper error logging** - aids in debugging issues

---

**End of Documentation**
