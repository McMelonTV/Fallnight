# Fallnight Beta Worldgen

Standalone Kotlin subproject for regenerating a clean Fallnight public-beta baseline without modifying the main Java server code.

## What it generates

The generator rewrites the runtime baseline under `Minestom-new/`:

- `application.yml`
- world folders for:
    - `data/spawn/`
    - `data/PvPMine/`
    - `data/plots/` directory preparation only
- baseline runtime data for:
    - `data/mines/`
    - `data/mineranks/`
    - `data/ranks.yml`
    - `data/tags.yml`
    - `data/prices.yml`
    - `data/broadcast.yml`
    - `data/koth.yml`
    - `data/pvpzones.yml`

It also deletes closed-beta live/runtime state such as users, vaults, gangs, auction state, bans, warnings, lottery state, and vote-party state before regenerating the baseline.

## Important behavior

- The generator is **destructive/regenerative**.
- Generated files are intended to be overwritten on reruns.
- `plots/` is **not** terrain-generated here; the main server still owns plot terrain generation at runtime.
- The mine ladder is generated for the **spawn** world to match the current Java server runtime wiring.

## Requirements

- Java 23 toolchain for this subproject
- existing repo-local Gradle wrapper from `Minestom-new/`

This subproject intentionally does **not** include its own Gradle wrapper. Run it through the existing wrapper in `Minestom-new/`.

## Run the generator

From this directory:

```bash
../Minestom-new/gradlew -p . run --args='generate'
```

That regenerates the baseline for the current repo root.

To target a specific repo root explicitly:

```bash
../Minestom-new/gradlew -p . run --args='generate /absolute/path/to/Fallnight'
```

## Run tests

```bash
../Minestom-new/gradlew -p . test
```

## Expected summary output

Successful runs print a short summary like:

```text
Worldgen complete
- repoRoot: ../..
- deleted targets: ...
- created targets: ...
- mines written: 26
- mine ranks written: 26
- worlds: spawn, PvPMine, plots
```

## Typical workflow

1. Run the generator.
2. Start the Java server from `Minestom-new/`.
3. Verify the generated baseline behaves as expected.

Example:

```bash
cd server-beta-setup-tool-minestom
../Minestom-new/gradlew -p . run --args='generate'
cd ../Minestom-new
./gradlew run
```
