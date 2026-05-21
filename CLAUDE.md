# Minecraft Mod - Claude Context

## Project Structure

This mod is part of a private monorepo that includes a web frontend and backend. The mod directory is synced to a **public repository** for open-source distribution using `git subtree`.

## Entry Points

- `com.dyetracker.DyeTrackerMod` (`ModInitializer`, `main`): config + commands + sync handlers (common init).
- `com.dyetracker.client.DyeTrackerModClient` (`ClientModInitializer`, `client`): HUD overlay renderer, `G` keybind, and startup re-hydration of persisted GIF overlays.

## Repository Setup

- **Private monorepo**: Contains mod + frontend + backend (where you're working now)
- **Public repo**: Contains only the mod code, synced on releases

## Release Workflow

When releasing a new version of the mod:

1. Ensure all mod changes are committed to the monorepo
2. Update the mod version number in the appropriate config files
3. From the **monorepo root** (not this directory), run:

```bash
git subtree push --prefix=path/to/mod origin-public main
```

Or if using tags for releases:

```bash
git tag v1.x.x
git push origin v1.x.x  # This triggers the GitHub Action to sync
```

## Important Guidelines

### Commit Hygiene
- Keep mod-related commits focused on mod changes only when possible
- Write clear commit messages—they will appear in the public repo's history
- Avoid referencing private backend/frontend details in commit messages for mod changes

### Sensitive Information
- **Never** include API keys, server URLs, or credentials in this directory
- Any configuration that references the private backend should use environment variables or config files that are gitignored
- The public repo should be fully functional as a standalone mod

### File Organization
- Keep all mod source files self-contained within this directory
- Shared types/utilities used by both mod and backend should be duplicated or extracted to a separate shared package, not referenced via relative imports outside this directory

### Documentation
- README.md in this directory should be written for public consumers
- Include build instructions that work standalone (not dependent on monorepo setup)
- Document any configuration options users need to set

## What NOT to Sync

These patterns should be in .gitignore or excluded from the public repo:
- Build artifacts
- IDE configurations
- Local development configs with private URLs
- Any test fixtures containing real data

## Building the Mod

The build is multi-version-managed via [Stonecutter](https://stonecutter.kikugie.dev/). A single shared `src/` tree is compiled against each Minecraft version registered in `settings.gradle.kts` (currently 1.21.10 and 1.21.11). Per-version dependency coordinates live under `versions/<mc-version>/gradle.properties`.

```bash
# Build all registered versions (produces one jar per version)
./gradlew chiseledBuild

# Build a single version
./gradlew :1.21.10:build
./gradlew :1.21.11:build

# Run a per-version dev client
./gradlew :1.21.10:runClient
./gradlew :1.21.11:runClient
```

Per-version jars land at `versions/<mc-version>/build/libs/dyetracker-<modver>+<mc-version>.jar` (e.g. `versions/1.21.11/build/libs/dyetracker-1.1.0+1.21.11.jar`).

Switch the active version (controls which preprocessor branches Stonecutter writes to disk in `src/`):

```bash
./gradlew "Set active project to 1.21.11"
./gradlew "Reset active project"   # restore VCS version before committing
```

Always run `"Reset active project"` before committing to avoid Stonecutter rewriting `src/` files in the diff.

### Adding a new Minecraft version

See the public-facing instructions in `README.md` (section: "Adding a new Minecraft version"). Summary: create `versions/<new-ver>/gradle.properties`, add `"<new-ver>"` to `settings.gradle.kts`, run `:<new-ver>:build`, fix any source-level diff with `//? if >=<new-ver> { ... //?}` blocks or named swaps in `stonecutter.gradle.kts`.