# DyeTracker Mod

A Fabric mod for Minecraft that integrates with the [DyeTracker](https://dyetracker.xyz) website to track your Hypixel SkyBlock dye collection progress.

## Features

- Link your Minecraft account to DyeTracker securely via Mojang session verification
- Automatically sync your player stats with the website
- No API keys or passwords required - uses Minecraft's built-in authentication

## Requirements

- Minecraft **1.21.10** or **1.21.11** (a separate mod jar is published for each — see [Installation](#installation))
- [Fabric Loader](https://fabricmc.net/) 0.16.0+
- [Fabric API](https://modrinth.com/mod/fabric-api) (the build matching your Minecraft version)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) 1.12.3+

## Installation

1. Install Fabric Loader for your Minecraft version (1.21.10 or 1.21.11).
2. Download the required dependencies (Fabric API + Fabric Language Kotlin) for your version.
3. Download the DyeTracker mod jar that matches your Minecraft version from [Releases](https://github.com/stwalsh4118/DyeTrackerMod/releases):
   - For MC 1.21.10 → `dyetracker-<modver>+1.21.10.jar`
   - For MC 1.21.11 → `dyetracker-<modver>+1.21.11.jar`
   The version suffix after the `+` indicates which Minecraft version the jar targets. Pick the one that matches your game.
4. Place all `.jar` files in your `.minecraft/mods` folder.
5. Launch Minecraft.

## Usage

### Linking Your Account

1. Go to [DyeTracker](https://dyetracker.xyz) and click "Link Account"
2. You'll receive an 8-character code
3. In Minecraft, run: `/dyetracker link <code>`
4. Your account will be verified and linked automatically

### Commands

| Command | Description |
|---------|-------------|
| `/dyetracker link <code>` | Link your account using a code from the website |
| `/dyetracker status` | Check your current link status |
| `/dyetracker unlink` | Unlink your account |

## Building from Source

The build is multi-version: a single `src/` tree compiles against every Minecraft version registered in `settings.gradle.kts`, producing one jar per version. This is wired with the [Stonecutter](https://stonecutter.kikugie.dev/) Gradle plugin.

### Prerequisites

- JDK 21 or higher
- Git

### Build Steps

```bash
# Clone the repository
git clone https://github.com/stwalsh4118/DyeTrackerMod.git
cd DyeTrackerMod

# Build every registered Minecraft version (produces both jars)
./gradlew chiseledBuild

# Or build only a single version:
./gradlew :1.21.10:build
./gradlew :1.21.11:build
```

Per-version jars land at `versions/<mc-version>/build/libs/dyetracker-<modver>+<mc-version>.jar`. For example:
- `versions/1.21.10/build/libs/dyetracker-1.1.0+1.21.10.jar`
- `versions/1.21.11/build/libs/dyetracker-1.1.0+1.21.11.jar`

### Development

```bash
# Run a Minecraft dev client for a specific version with the mod loaded
./gradlew :1.21.10:runClient
./gradlew :1.21.11:runClient

# Switch the "active" version (controls which preprocessor branches are written to src/)
./gradlew "Set active project to 1.21.11"
./gradlew "Reset active project"   # restores the VCS version before committing
```

The active-version setting is rewritten by Stonecutter on switch — always run `"Reset active project"` before committing to keep `git status` clean.

For IntelliJ IDEA users, install the [Stonecutter plugin](https://plugins.jetbrains.com/) (search for "Stonecutter") to get syntax highlighting for `//?` preprocessor comments and a one-click version switcher in the run-configurations dropdown.

### Adding a new Minecraft version

When a new Minecraft patch (e.g. 1.21.12) ships:

1. **Create `versions/<new-ver>/gradle.properties`** with the new coordinates. Copy any existing `versions/<ver>/gradle.properties` as a starting point and update:
   - `yarn_mappings=<new-ver>+build.<latest>` — pull the latest non-pre/non-rc build from `https://maven.fabricmc.net/net/fabricmc/yarn/`
   - `fabric_version=<latest>+<new-ver>` — pull from `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/`
   - `loader_version` and `fabric_kotlin_version` rarely change between Minecraft patches; leave them at the values from any existing `versions/<ver>/gradle.properties` unless a bump is needed.
2. **Register the version in `settings.gradle.kts`**: add `"<new-ver>"` to the `versions(...)` call inside `stonecutter.create(...)`.
3. **(Optional) Create `versions/<new-ver>/build.gradle.kts`** — only if the new version needs version-specific Gradle logic that can't be expressed via `if (sc.current.parsed >= ...)` inside the root `build.gradle.kts`. For adjacent patch versions this is virtually never needed; the root build script is evaluated once per registered version and inherits any per-version `gradle.properties` automatically.
4. **Build and verify**:
   ```bash
   ./gradlew :<new-ver>:build
   ```
   If the build fails due to source-level API differences between Minecraft versions, fix the affected call sites using Stonecutter preprocessor comments — `//? if >=<new-ver> { ... //?}` blocks for multi-line code, or named swaps registered in `stonecutter.gradle.kts` for one-liners. See the [Stonecutter docs](https://stonecutter.kikugie.dev/wiki/start/comments) for the full syntax.

That's it — no source duplication, no branch fork, no manual `gradle.properties` swaps. In practice steps 1 and 2 are all that's needed (a two-file change); step 3 is a documented escape hatch for the rare case a future version needs its own Gradle logic.

## Configuration

The mod stores its configuration in `.minecraft/config/dyetracker.json`. This includes your linked account credentials (stored locally and securely).

## Privacy & Security

### What this mod accesses

| Data | Used For | Sent To |
|------|----------|---------|
| Username | Account linking | DyeTracker API |
| UUID | Account identification | DyeTracker API |
| Session Token | Prove account ownership | Mojang only (never DyeTracker) |

Your Minecraft session token is **never** sent to DyeTracker servers - only to Mojang's official authentication servers.

### How verification works

The mod uses Minecraft's standard server authentication flow - the same method every Minecraft server uses when you join:

1. You enter a link code from the website
2. The mod calls Mojang's official `sessionserver.mojang.com` to prove you own your account
3. DyeTracker's server verifies with Mojang that the authentication succeeded
4. A session token is saved locally for future API calls

**Your Minecraft session token is only ever sent to Mojang's official servers, never to DyeTracker.** This is the same secure flow used by every Minecraft server you've ever joined.

### Data storage

- Credentials are stored locally in `.minecraft/config/dyetracker.json`
- You can unlink your account at any time with `/dyetracker unlink`
- Unlinking deletes all stored credentials

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Links

- [DyeTracker Website](https://dyetracker.xyz)
- [Issue Tracker](https://github.com/stwalsh4118/DyeTrackerMod/issues)
