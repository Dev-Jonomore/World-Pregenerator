# Known Issues & Development Roadmap

This file documents known bugs, technical debt, planned features, and areas for improvement. When working on this project, consult this file to understand current limitations and avoid known pitfalls.

**Note**: For active task management, use Gemini CLI's built-in todo tool (`gemini todo`). This file serves as project-level documentation of issues that should persist in the repository.

## Priority System
- **P0 (Critical)**: Bugs that cause data loss or generation failures
- **P1 (High)**: Missing features that significantly impact usability
- **P2 (Medium)**: Improvements that enhance functionality
- **P3 (Low)**: Nice-to-have features and optimizations

---

## Known Issues (Fix These)

### P0: No Progress Persistence
**Status**: Closed ✅
**Impact**: Server crash or restart loses all generation progress

**Required Changes**:
1. Create `GenerationState` class with save/load methods
2. Persist state to `plugins/WorldPregenerator/state.json`
3. Load state on plugin enable, resume if valid
4. Delete partial world on load if it exists

**Files to Modify**:
- `GenerationTask.java` - add state persistence
- `WorldPregenerator.java` - load state on enable

---

### P0: Resource Cleanup Race Condition
**Status**: Closed ✅
**Impact**: Failed deletions leave corrupt state, no retry mechanism

**Required Changes**:
1. Add callback parameter to `unloadAndDeleteWorld()`
2. Verify deletion completed before calling callback
3. Add retry logic (max 3 attempts with exponential backoff)
4. Track failed deletions in separate list for manual cleanup

**Files to Modify**:
- `GenerationTask.java` - add deletion callback
- `FileUtils.java` (previously `Util.java`) - add retry wrapper for `deleteDirectory()`

---

### P1: No Error Recovery Strategy
**Status**: Closed ✅
**Impact**: Fatal errors cause endless retry loops, wasting resources

**Required Changes**:
1. Categorize exceptions:
   - Fatal: Missing dependencies, permission errors → STOP generation
   - Recoverable: Network timeouts, temporary I/O → RETRY with limit
   - Skip: Individual world issues → SKIP to next seed
2. Add retry counter per seed (max 3)
3. Add `/wp failed` command to list failed seeds

**Files to Modify**:
- `GenerationTask.java` - add error categorization
- New file: `ErrorHandler.java` - centralize error logic

---

### P1: No Generation Progress Feedback
**Status**: Closed ✅
**Impact**: Users can't check progress without reading logs

**Required Changes**:
1. Add `/wp status` command showing:
   - Current seed being processed
   - Progress: X/Y completed (percentage)
   - Success count, failure count
   - Estimated time remaining (based on average time per world)
   - Current step (generating chunks, adjusting spawn, etc.)
2. Store statistics in GenerationState

**Files to Modify**:
- `WPCommands.java` - add status command
- `GenerationTask.java` - track timing statistics

---

### P2: Config Validation Missing
**Status**: Closed ✅
**Impact**: Invalid config values cause runtime errors

**Required Changes**:
Add validation in `ConfigManager.loadConfig()`:
```java
cage_radius = plugin.getConfig().getInt("cage-building.cage-radius");
if (cage_radius < 0 || cage_radius > 100) {
    logger.warning("cage-radius out of range [0,100]: " + cage_radius);
    cage_radius = 4; // default
}
```

**Validation Rules**:
- `generation-radius`: 100-10000 blocks, or 8-86 chunks
- `cage-radius`: 2-10
- `cage-height`: odd numbers between 3-9
- `maxSearchRadius`: 10-256
- `maxVerticalScan`: 10-256
- `export-path`: must be writable directory
- `seeds-file`: must exist and be readable
- `cage-material`: must be valid Material

**Files to Modify**:
- `ConfigManager.java` - add validation logic

---

### P3: Hardcoded Values
**Status**: Closed ✅
**Impact**: Minor - reduces flexibility

**Items to Move to Config**:
- Delay between world processing (currently 40 ticks)

**Files to Modify**:
- `config.yml` - add new fields
- `ConfigManager.java` - add getters
- `GenerationTask.java` - use config value

---

## Planned Features (Add These)

### P2: Dry Run Mode
**Status**: Closed ✅
**Justification**: Test configuration without generating worlds

**Implementation**:
```
/wp dryrun - Validate everything without generating
```

Checks:
- Config file valid and values in range
- Seed file exists and readable
- Export path writable
- Dependencies present (Chunky, ASP)
- Estimate disk space needed (radius × seeds × ~50MB)

**Files to Modify**:
- `WPCommands.java` - add dryrun command
- New file: `ConfigValidator.java` - validation logic

---

### P2: Batch Configuration
**Status**: Closed ✅
**Justification**: Reduce server load, allow scheduled generation

**Config Addition**:
```yaml
batch-settings:
  worlds-per-batch: 10       # Generate in batches
  pause-between-batches: 60  # Seconds to cool down
  auto-resume: true          # Resume on server restart
```

**Files to Modify**:
- `config.yml` - add batch settings
- `ConfigManager.java` - add batch config getters
- `GenerationTask.java` - implement batch logic

---

### P3: Export Format Options
**Justification**: Not everyone needs SlimeWorld format

**Config Addition**:
```yaml
export-format: SLIME  # SLIME, VANILLA, ZIP
```

Formats:
- SLIME: Current behavior (ASP SlimeWorld)
- VANILLA: Just copy world folder to export directory
- ZIP: Create compressed archive of world folder

**Files to Modify**:
- `config.yml` - add export format
- `GenerationTask.java` - add format-specific export methods

---

## Testing Checklist

**Before Release** - verify all these scenarios:

Configuration:
- [ ] Invalid seed file (missing, unreadable, wrong format)
- [ ] Invalid export path (no permissions, doesn't exist, not writable)
- [ ] Out-of-range config values (negative radius, huge height)
- [ ] Invalid cage material

Runtime:
- [ ] Server shutdown during generation
- [ ] `/wp reset` during active generation
- [ ] `/wp stop` then `/wp start` (resume)
- [ ] Config reload during active generation

Dependencies:
- [ ] Missing Chunky plugin
- [ ] Missing ASP plugin
- [ ] Outdated dependency versions

Edge Cases:
- [ ] Disk full scenario
- [ ] World at y=minHeight boundary
- [ ] World at y=maxHeight boundary
- [ ] Ocean spawn (BFS search)
- [ ] Mountain spawn (vertical scan)
- [ ] Void spawn (no valid ground)


Post-Release Testing:
- [ ] Generate 10+ worlds successfully
- [ ] Verify exported SlimeWorld files load correctly
- [ ] Memory usage stays stable over 50+ worlds
- [ ] No leftover world folders after completion

---

## Refactoring Tasks

### Extract State Management Class
**Status**: Closed ✅
**Justification**: State handling is scattered, makes persistence difficult

**Create**: `GenerationState.java`
```java
public class GenerationState {
    private int currentIndex;
    private final int totalSeeds;
    private int successCount;
    private int failureCount;
    private long startTime;
    private List<Long> failedSeeds;
    
    public void save(File stateFile) { /* JSON serialization */ }
    public static GenerationState load(File stateFile) { /* JSON deserialization */ }
    public double getProgress() { return (double) currentIndex / totalSeeds; }
    public long estimateTimeRemaining() { /* based on average */ }
}
```

**Benefits**: Single source of truth, easy to persist, easier testing

---

### Replace Callback Hell with CompletableFuture
**Status**: Closed ✅
**Justification**: Current nested callbacks are hard to read and maintain

**Current**:
```java
generateChunks(world, () -> {
    checkSpawn(world);
    buildCage(world);
    saveAndExportToSlime(world, seed, () -> {
        unloadAndDeleteWorld(world);
        moveOn();
    });
});
```

**Proposed**:
```java
CompletableFuture.supplyAsync(() -> createWorld(seed))
    .thenCompose(this::generateChunks)
    .thenApply(this::checkAndAdjustSpawn)
    .thenApply(this::buildCage)
    .thenCompose(world -> saveAndExport(world, seed))
    .thenAccept(this::cleanup)
    .thenRun(this::moveOn)
    .exceptionally(this::handleError);
```

**Benefits**: Better error handling, easier to add steps, more readable

**Files to Modify**: `GenerationTask.java`

---

### Centralize Magic Numbers
**Status**: Closed ✅
**Justification**: Hardcoded values scattered throughout code

**Create**: Constants class or move to config
```java
public class GenerationConstants {
    public static final long WORLD_DELAY_TICKS = 40L;  // 2 seconds
    public static final double LOCATION_CENTER_OFFSET = 0.5;
    public static final int CAGE_Y_SKIP = 2;  // Skip every other Y level
}
```

**Files to Modify**: `GenerationTask.java`, `CageBuilder.java`, `SpawnAdjuster.java`

---

## Documentation Needed

### README.md
**Status**: Closed ✅
**Required Sections**:
- Installation (where to get dependencies)
- Quick start (basic setup in 5 steps)
- Configuration guide (explain each config value)
- Command reference (all /wp commands)
- Troubleshooting (common issues and fixes)
- Example workflow (seeds.txt → generated worlds)

### ARCHITECTURE.md  
**Required Sections**:
- State machine diagram (IDLE → RUNNING → STOPPED → etc)
- Async flow diagram (shows callbacks and thread transitions)
- Class relationship diagram
- Generation pipeline flowchart

### API.md (if exposing to other plugins)
**Required Sections**:
- How to listen for generation events
- How to customize spawn adjustment
- How to add custom export formats
- Example plugin integration code

---

## Performance Optimization Opportunities

### Chunk Pre-loading
**Idea**: Pre-load chunks around spawn before generation to reduce disk I/O

**Implementation**: Before calling `chunky.startTask()`, load 16x16 chunks around spawn

**Expected Benefit**: 10-15% faster generation

**Files to Modify**: `GenerationTask.java`

---

### Spawn Search Caching  
**Idea**: Cache spawn results by seed for worlds with same seed but different configs

**Implementation**: `Map<Long, Location> spawnCache` in SpawnAdjuster

**Expected Benefit**: Instant spawn adjustment for duplicate seeds

**Risk**: Memory usage grows with unique seeds

**Files to Modify**: `SpawnAdjuster.java`

---

### Memory Monitoring
**Idea**: Pause generation if heap usage exceeds threshold

**Implementation**:
```java
Runtime runtime = Runtime.getRuntime();
double usage = (runtime.totalMemory() - runtime.freeMemory()) / (double) runtime.maxMemory();
if (usage > 0.85) {
    logger.warning("Memory usage high (" + (int)(usage*100) + "%), pausing");
    pauseGeneration();
}
```

**Expected Benefit**: Prevent OutOfMemoryError on long generation runs

**Files to Modify**: `GenerationTask.java` (check before each world)

---

## Next Sprint (Do Next)

**Goal**: Improve code readability, maintainability, and add core features.

**Estimated Time**: 8-12 hours

**Tasks**:

1.  **Replace Callback Hell with CompletableFuture (P2 Refactoring)**:
    *   **Justification**: Current nested callbacks are hard to read and maintain. CompletableFuture will improve code clarity, error handling, and make it easier to add new steps to the generation pipeline.
    *   **Files to Modify**: `GenerationTask.java`
    *   **Success Criteria**: `GenerationTask.java` uses `CompletableFuture` for its async operations, eliminating deeply nested callbacks.

2.  **Dry Run Mode (P2 Planned Feature)**:
    *   **Justification**: Allows users to validate configuration and dependencies without initiating a full world generation, saving time and resources.
    *   **Implementation**: Add `/wp dryrun` command to `WPCommands.java`. Create a new `ConfigValidator.java` to centralize validation logic.
    *   **Files to Modify**: `WPCommands.java`, New file: `ConfigValidator.java`
    *   **Success Criteria**: `/wp dryrun` command exists and performs all specified checks, providing clear feedback to the user.

3.  **Batch Configuration (P2 Planned Feature)**:
    *   **Justification**: Reduces server load during generation and allows for scheduled or more controlled world generation.
    *   **Implementation**: Add `batch-settings` to `config.yml`, `ConfigManager.java` for getters, and implement batch logic in `GenerationTask.java`.
    *   **Files to Modify**: `config.yml`, `ConfigManager.java`, `GenerationTask.java`
    *   **Success Criteria**: Plugin can generate worlds in batches with configurable pauses and auto-resume capabilities.

---

## Code Organization Tasks

### P3: Reorganize Utility Code
**Status**: Closed ✅
**Justification**: `Util.java` only has one method (`deleteDirectory`), unclear if it should exist as separate class

**Current State**:
- `Util.java` - single static utility class with only `deleteDirectory()`
- Lowercase class name breaks Java conventions
- Could move to `WorldPregenerator.java` as static method OR expand util class with more utilities

**Options**:
1. **Move to main plugin class**: Add `deleteDirectory()` as private static method in `WorldPregenerator.java`, delete `Util.java`
2. **Expand util class**: Rename to `FileUtils.java`, add other file operations if/when needed (proper PascalCase)
3. **Keep as-is**: Single-purpose utility is fine, just rename to `Util.java` (capital U)

**Recommendation**: Analyze codebase for other potential utility methods first:
- Coordinate packing/unpacking from `SpawnAdjuster` could move to `CoordinateUtils.java`
- File operations could go in `FileUtils.java`
- Config validation helpers could go in `ConfigUtils.java`

**Decision needed**: What utilities would make sense to extract? Does a single `deleteDirectory()` method warrant its own file?

**Files to Consider**:
- `Util.java` - current location
- `WorldPregenerator.java` - potential new location
- `SpawnAdjuster.java` - has static coordinate utilities
- Consider creating: `FileUtils.java`, `CoordinateUtils.java`, `ConfigUtils.java`

**Note**: This should be done AFTER analyzing full codebase to see what other utilities naturally emerge, not based on current single-method state.