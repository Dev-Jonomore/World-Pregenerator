# WorldPregenerator

A Paper/Bukkit plugin that batch-generates Minecraft worlds from a seed list, converts them to SlimeWorld format, and exports them with safe spawn points and protective cages.

## Installation

1.  **Dependencies**: This plugin requires the following plugins to be installed on your server:
    *   [Chunky](https://www.spigotmc.org/resources/chunky.81534/) (v1.3.38+)
    *   The `asp-server.jar`
    *   At least 8 GB of RAM
2.  **Download**: Download the latest release of `WorldPregenerator.jar` from the releases page.
3.  **Install**: Place the `WorldPregenerator.jar` file into your server's `plugins` directory.
4.  **Restart**: Restart your server to generate the default configuration.

## Quick Start

1.  **Configure `config.yml`**: Open `plugins/WorldPregenerator/config.yml` and set `export-path` to your desired output directory for slime worlds.
2.  **Create `seeds.txt`**: Create a file named `seeds.txt` (or as configured in `seeds-file`) and add one seed per line, formatted as `seed/x1,y1,z1/x2,y2,z2/x3,y3,z3` where each `x,y,z` group is a spawn point for that world (e.g. `12345/100,64,200/-50,70,30/0,80,-120`). The spawn points are written to the world's `manhunt.yml` under `spawn-points`.
3.  **Start Generation**: Use the command `/wp start` to begin the generation process.
4.  **Check Progress**: Use `/wp status` to monitor the progress.
5.  **Stop Generation**: Use `/wp stop` to pause the generation.

## Configuration Guide

The `config.yml` file contains the following settings:

Missing keys fall back to their defaults. Note that `/wp config` rewrites the file and removes its comments.

*   `generation-radius`: The radius in blocks for Chunky to pre-generate. (Range: 100-10000, Default: 1200)
*   `generation-area`: `world-spawn` pregenerates a square of `generation-radius` around the world spawn. `spawn-points` pregenerates the smallest rectangle containing the seed's spawn points instead, plus `spawn-points-margin` blocks on each side (Range: 16-2000, Default: 128). (Default: `world-spawn`)
*   `export-path`: The directory where exported `.slime` files will be saved.
*   `seeds-file`: The path to the file containing the list of seeds to generate.
*   `world-delay-ticks`: The delay in ticks between processing each world. (Default: 40)
*   `spawn-adjustment`:
    *   `maxSearchRadius`: The maximum horizontal distance to search for a safe spawn. (Range: 10-256, Default: 100)
    *   `maxVerticalScan`: The maximum vertical distance to scan for ground level. (Range: 10-256, Default: 128)
*   `cage-building`:
    *   `cage-material`: The material to use for the protective cage. (Default: `PURPLE_STAINED_GLASS`)
    *   `cage-radius`: The radius of the cage. (Range: 2-10, Default: 4)
    *   `cage-height`: The height of the cage. Must be an odd number. (Range: 3-9, Default: 3)
*   `structure-finder`: After chunk generation, the nearest matching structure to each spawn point is written to that spawn point's entry in `manhunt.yml` under `nearest-structure` (type, the x/z of the structure's center, and the compass direction from the spawn point).
    *   `whitelist`: If `true`, only the listed structures are searched for; if `false`, every structure except the listed ones is. (Default: `true`)
    *   `search-radius`: How far from each spawn point to search, in blocks. (Range: 16-10000, Default: 200)
    *   `structures`: Structure keys such as `village_plains` or `minecraft:desert_pyramid`.
*   `spawn-verification`: Before the structure search, each spawn point is checked the way vanilla places a respawning player: it tries every column within `respawn-radius` of the world spawn and puts the player on the first one with solid, dry ground. If too few columns qualify (for example, a point in the ocean, where the player would be left floating on the water), the point is moved to the nearest spot within `snap-radius` that does. The original coordinates are kept under `verification` in `manhunt.yml`. Points that can't be fixed are dropped, and a seed with no points left is skipped and shows up in `/wp failed`.
    *   `enabled`: Whether to verify spawn points. (Default: `true`)
    *   `respawn-radius`: Must match the live servers' `respawn_radius` game rule. (Range: 0-15, Default: 10)
    *   `min-valid-fraction`: Share of the columns around a point that must be valid landing spots. (Range: above 0 up to 1, Default: 0.25)
    *   `snap-radius`: How far a point may be moved, in blocks. (Range: 0-256, Default: 80, matching the 5-chunk search vanilla uses for its own world spawn)
    *   Players respawning in Adventure mode are placed exactly on the spawn point instead, which this check doesn't cover.

## Command Reference

*   `/wp help`: Shows a list of these commands.
*   `/wp start`: Starts or resumes the world generation task.
*   `/wp stop`: Stops the current generation task.
*   `/wp retry`: Retries failed seeds.
*   `/wp reset`: Stops generation and deletes all exported worlds. Requires confirmation.
*   `/wp confirm`: Confirms the reset operation.
*   `/wp status`: Shows the current status of the generation task.
*   `/wp failed`: Lists all seeds that failed to generate.
*   `/wp reload`: Reloads the configuration file.
*   `/wp info`: Displays the current configuration values.
*   `/wp config <setting> <value>`: Modifies a configuration value.

## Troubleshooting

*   **"Chunky API not found!"**: Make sure the Chunky plugin is installed and up-to-date.
*   **"AdvancedSlimePaper API not found!"**: Make sure you are running a compatible version of AdvancedSlimePaper.
*   **Generation fails to start**:
    *   Check `seeds.txt` to ensure it exists and contains valid seeds.
    *   Check that the `export-path` is a writable directory.
    *   Look for errors in the server console for more details.
*   **Server crashes or restarts lose progress**: The plugin now persists state in `state.json`. If a crash occurs, the generation can be resumed with `/wp start`. A partially generated world from the previous session will be automatically cleaned up.
