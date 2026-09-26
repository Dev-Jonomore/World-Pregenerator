# WorldPregenerator

A Paper plugin that batch-generates Minecraft worlds from a list of seeds, pregenerates their
chunks with Chunky, verifies and annotates their spawn points, and exports each one as a
self-contained `.zip` ready to be dropped into a world pool.

Built for **Paper 26.3**. It is a build-time tool, not a gameplay plugin: you run it on a
dedicated pregeneration server, and the zips it produces are consumed elsewhere.

## Requirements

* **Paper 26.3** (`api-version: 26.3`). Worlds generated on 26.3 cannot be loaded by older
  servers, so the consuming servers must be on 26.3 too.
* **Java 25.**
* **[Chunky](https://modrinth.com/plugin/chunky) 1.5.3+** (Bukkit build). Required dependency —
  the plugin will not run without it.
* Enough RAM for one world at a time. A 3 GB heap is comfortable; see [Performance](#performance).
* Enough disk for the output. A 1,200-radius world is roughly **175–215 MB** zipped, and the
  server needs room for the raw world folder (~300 MB) while it works.

## Installation

1. Drop `Chunky` and `WorldPregenerator.jar` into `plugins/`.
2. Start the server once to generate `plugins/WorldPregenerator/config.yml`.
3. Set `export-path` and `seeds-file` in the config (the defaults are absolute Linux paths and
   almost certainly wrong for your setup).
4. Create your seeds file, then run `/wp dryrun` to check everything before committing to a run.

## The seeds file

One seed per line, each with one or more candidate spawn points:

```
seed/x1,y1,z1/x2,y2,z2/x3,y3,z3
```

For example:

```
12345/100,64,200/-50,70,30/0,80,-120
-8837423/512,71,-64/103,96,410/-900,63,88
```

At least one spawn point is required. Spaces around the numbers are ignored, blank lines are
skipped, and lines that don't match the format are reported and skipped rather than aborting the
run. Choosing good candidate points is the job of whatever produces this file; the plugin only
validates that a player can actually land on them (see
[Spawn verification](#spawn-verification)).

> The seeds file is read when a new generation task is created — on `/wp start` with no task in
> progress. Editing it mid-run has no effect until the current task finishes or the server
> restarts.

## What it produces

One zip per seed, named `<seed>.zip`, in `export-path`:

```
12345.zip
├── world_7/            the overworld folder, under the pregenerator's internal name
│   ├── region/
│   ├── level.dat
│   └── ...
└── manhunt.yml
```

`manhunt.yml` sits at the root of the zip and describes the world:

```yaml
seed: 12345
pregen-radius: 1200
spawn-points:
  - x: 100
    y: 64
    z: 200
    nearest-structure:
      type: minecraft:village_plains
      x: 248
      z: 136
      direction: NE
  - x: -50
    y: 70
    z: 30
    verification:
      status: ADJUSTED
      original:
        x: -50
        y: 63
        z: 88
worlds:
  overworld: world_7
```

* `nearest-structure` is written only when a matching structure was found in range. It carries no
  `y` — structure bounding boxes report placeholder heights, so x/z plus a compass direction is
  what's reliable.
* `verification` is written only when spawn verification **moved** a point, and records where it
  originally was.
* Only the overworld is generated and shipped. The consumer is expected to create the nether and
  end fresh from the same seed.

Each zip is verified after writing: it must open as a valid archive and contain a `manhunt.yml`
with at least one spawn point. A partial zip from a failed run is deleted, and an existing zip
that still validates is left alone and the seed skipped, so re-running is cheap.

## Quick start

```
/wp dryrun
/wp test-one
/wp start
/wp status
```

`dryrun` validates config, seeds and paths without generating anything. `test-one` runs the whole
pipeline for a single seed and prints timings and the resulting zip size — do this before
starting a run of thousands.

## Commands

The base command is `/worldpregenerator`, aliased `/wp` and `/worldpregen`.

| Command | Description |
| --- | --- |
| `/wp help` | List the commands. |
| `/wp start` | Start a new run, or resume the saved one. |
| `/wp stop` | Stop the run, cancelling the in-flight Chunky task. Progress is kept. |
| `/wp status` | Step, progress, success/failure counts, estimated time remaining. |
| `/wp failed` | The seeds that failed, with the reason for each. |
| `/wp retry` | Start a fresh run over only the failed seeds. |
| `/wp dryrun` | Validate config, seeds file, export path and Chunky; estimate disk usage. |
| `/wp test-one [seed]` | Run the full pipeline for one seed and report timings and zip size. Uses the given seed, or the first in the seeds file. |
| `/wp reset` | Stop, then clear all progress. Asks for confirmation. |
| `/wp confirm` | Confirm a `reset` within 30 seconds of requesting it. |
| `/wp reload` | Reload `config.yml` from disk. |
| `/wp info` | Print the active config values **to the server console**. |
| `/wp config <setting> <value>` | Set one value in `config.yml` and reload. |

Notes:

* `/wp reset` deletes `state.json`, `completed.txt` and the partially generated world it was
  working on. It does **not** delete zips that have already been exported — clear `export-path`
  yourself if that's what you want.
* `/wp config` takes a dot-separated path, e.g. `/wp config spawn-verification.snap-radius 60`.
  Configurate rewrites the file on save, which **strips the comments** out of `config.yml`.
* Commands are currently unrestricted — there is no permission node. Run this plugin on a private
  pregeneration server, not one players can join.

## Configuration

Any key you leave out falls back to its default.

### Generation

* `generation-radius` — radius in blocks that Chunky pregenerates around the world spawn.
  Range 100–10000, default `1200`.
* `generation-area` — what to pregenerate.
  * `world-spawn` (default): a square of `generation-radius` around the world spawn.
  * `spawn-points`: the smallest rectangle containing that seed's spawn points, plus
    `spawn-points-margin` on every side. Much faster and far smaller (~680 chunks vs ~22,800),
    but only useful if players never travel beyond the points.
* `spawn-points-margin` — blocks added on each side in `spawn-points` mode. Range 16–2000,
  default `128`. It needs to cover the respawn radius and the snap radius.
* `export-path` — directory the `.zip` files are written to. Created if missing.
* `seeds-file` — path to the seeds file.
* `world-delay-ticks` — idle delay between worlds, default `40` (2 seconds). The delay runs after
  cleanup has finished and each world gets a fresh name, so `0` is safe and saves real time over
  a large run.

### Batching

* `batch-settings.worlds-per-batch` — worlds to do before pausing. Default `10`.
* `batch-settings.pause-between-batches` — pause length in seconds. Default `60`.

### Spawn verification

Before the structure search, each spawn point is checked the way vanilla places a respawning
player: every column within `respawn-radius` of the point is tested, and the player is put on the
first one with solid, dry ground. If too few columns qualify — a point in the ocean, where the
player would be left floating — the point is moved to the nearest spot within `snap-radius` that
does. Points that can't be fixed are dropped, and a seed with no points left is skipped and shows
up in `/wp failed`.

* `enabled` — default `true`.
* `respawn-radius` — must match the `respawn_radius` game rule on the servers that will use these
  worlds. Range 0–15, default `10`, giving a 21×21 = 441 column check.
* `min-valid-fraction` — share of those columns that must be valid landing spots. Range above 0
  up to 1, default `0.5`. This is the verifier's own safety margin, **not** a vanilla rule —
  vanilla only needs one valid column. It was raised from 0.25 after playtesting found spawns on
  thin coastlines.
* `snap-radius` — how far a failing point may be moved, in blocks. Range 0–256, default `80`,
  matching the 5-chunk search vanilla uses when picking its own world spawn.

Two caveats:

* Players respawning in **Adventure mode** are placed at the exact coordinates instead of being
  re-found, which this check doesn't model.
* For a point that passes unchanged, the `y` written to `manhunt.yml` is the one from the seeds
  file, which may not be the real ground height. Survival respawn ignores `y`, so this is
  harmless unless something places players at the literal coordinates.

### Structure finder

After chunk generation, the nearest matching structure to each spawn point is recorded in
`manhunt.yml`.

* `whitelist` — `true` (default) searches only for the listed structures; `false` searches for
  everything except them.
* `search-radius` — how far from each spawn point to search, in blocks. Range 16–10000,
  default `200`. Results are filtered by real horizontal distance, because vanilla's structure
  search counts in placement regions and will happily return a village 3,000 blocks away.
* `structures` — structure keys, with or without the namespace: `village_plains`,
  `minecraft:desert_pyramid`.

## Resuming

Progress is written to `plugins/WorldPregenerator/state.json` after every pipeline step, and every
finished seed is appended to `completed.txt`. After a crash or restart, `/wp start` picks up where
it left off; a half-generated world from the previous session is cleaned up automatically on
enable. Seeds listed in `completed.txt` are skipped when a new run starts.

If `state.json` can't be parsed it's renamed to `state.json.corrupt` and the plugin starts clean.

## Performance

Measured on 8 cores / 12 GB (WSL), radius 1200, 22,801 chunks per world:

| Setup | 7 seeds |
| --- | --- |
| 1 server, Paper defaults | 1748 s |
| 1 server, 6 worker threads | 743 s |
| 2 servers, defaults | 1071 s |
| 3 servers, defaults | 860 s |

**Paper's default worker thread count is the bottleneck.** It is `cores / 2`, halved again above
4 — just 2 threads on an 8-core box. Raise it in `config/paper-global.yml`:

```yaml
chunk-system:
  worker-threads: 6
```

Use roughly `cores - 2`, and leave `io-threads` at `-1`.

Throughput depends on the *total* number of worker threads, not on how many servers you spread
them over: one server with 6 threads (627 s) matched two servers with 3 threads each (625 s) at
twice the memory. Run one server, tuned.

Other findings:

* Budget ≈ 2.5 minutes per world at radius 1200. About 7.8 days and ~855 GB for 4,500 seeds on
  this hardware.
* Memory settles at roughly heap + 0.7–0.8 GB of process RSS. A 3 GB heap in a 4.5 GB container
  works well. A 100-world soak test showed ~100 KB of heap growth per world and a flat class
  count.
* On Pterodactyl, Paper counts the container's *visible* cores, not the panel's CPU limit — give
  the limit room for the threads you asked for (~700–800% for 6). If the egg sizes the heap with
  `-XX:MaxRAMPercentage`, set `-Xmx` explicitly instead.

In `spawn-points` mode, per-world overhead dominates instead (~5 s of world creation,
verification, structure search and zipping), so there extra servers *do* help.

## Troubleshooting

**"Chunky API not found!"** — Chunky isn't installed, failed to load, or is too old. `/wp dryrun`
reports the version it found.

**Generation starts but never finishes a world** — check that Chunky is actually running with
`/chunky status`. Chunky identifies worlds by name, not by namespaced key.

**A seed keeps failing** — `/wp failed` shows the reason. "No valid spawn points" means every
candidate point for that seed was in water or otherwise unlandable and couldn't be snapped to
anywhere valid within `snap-radius`; that's a problem with the input points, not the world.

**IO errors are retried** up to 3 times per seed, then the seed is skipped. Out-of-space and
permission-denied errors stop the run immediately rather than burning through the whole seed list.

**Disk fills up** — `/wp dryrun` estimates the space needed. Note that this estimate assumes
`world-spawn` mode and will be wildly high if you're using `spawn-points`.

## Building

```bash
./gradlew build
```

Produces the shaded jar in `build/libs/`. Requires a Java 25 toolchain. `./gradlew runServer`
starts a Paper 26.3 test server with Chunky already installed.
