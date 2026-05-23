# Minecraft Mod - Claude Context

## Project Structure

This mod is part of a private monorepo that includes a web frontend and backend. The mod directory is synced to a **public repository** for open-source distribution using `git subtree`.

## Entry Points

- `com.dyetracker.DyeTrackerMod` (`ModInitializer`, `main`): config + commands + sync handlers (common init).
- `com.dyetracker.client.DyeTrackerModClient` (`ClientModInitializer`, `client`): starts the UI-toolkit HUD host, registers the GIF overlay feature, the `G` edit-mode keybind, and startup re-hydration of persisted GIF overlays.

## UI Toolkit (`com.dyetracker.ui`)

A reusable in-game UI component toolkit (PBI 30) for building HUD widgets and edit screens from shared primitives instead of hand-rolled `DrawContext` calls. The GIF/image overlay (PBI 28) is built on it and is the reference consumer.

Packages:

- `ui.core` — `Widget` contract (`measure(): Size` + `draw(ctx, x, y)`; `RenderContext` carries the `DrawContext` + a pause-aware wall clock); geometry (`Size`, `Insets`, `Alignment`, `Rect`); `UiDraw` primitives (`fillRect`, `strokeRect`/`filledBox`, alignment/scale-aware `drawText`, non-tiling `blitStretched`); `WidgetPlacement` (read: id + fractional center x/y + scale + visible) and `PlacementEditor` (write).
- `ui.theme` — `UiTheme` style tokens (`Colors`, `Spacing`, `Sizing`). No raw ARGB/px literals in widgets.
- `ui.texture` — `ImageTextureManager` (feature-agnostic GPU texture + frame-animation backend; `upload`/`release`/`currentFrameIdentifier`/`widthOf`/`heightOf`) consuming `ImageFrames`.
- `ui.components` — `TextWidget`, `PanelWidget`, `SpriteWidget`.
- `ui.layout` — `Row`, `Column`, `Stack` containers (spacing, padding, alignment; nest recursively).
- `ui.hud` — `HudWidgetHost` (one Fabric HUD element before vanilla chat; F1-hide, pause-freeze, shared wall-clock anim; positions each entry from fractional center + measured size, scaling the whole tree via the GUI matrix) and `HudWidgetRegistry` (`HudWidgetEntry`, `HudWidgetProvider`).
- `ui.persist` — `PlacementStore<T : WidgetPlacement>`: generic id-keyed add/remove/update/`updateTransient`/`flush` over a config-list slice (transient-then-flush avoids a disk write per drag/scale frame).
- `ui.edit` — `WidgetEditScreen` (widget-agnostic drag-to-move / scroll-to-scale / `E` visibility / `Delete` remove / focus border + label / ESC-save), `EditScreenAction` + `EditScreenActionRegistry` (pluggable per-feature panels), `EditModeKeybind` (`G`).

### How to add a HUD widget

1. Define a placement config implementing `WidgetPlacement` (`@Serializable`, fractional center coords) and add a `List<It>` slice to `ModConfig`.
2. Create a `PlacementStore` over that slice in `ConfigManager` (mirror `gifPlacements`).
3. Build the widget from toolkit components (`Panel`/`Row`/`Column`/`Text`/`Sprite`). For images, `ImageTextureManager.upload(id, ImageFrames)` then draw with `SpriteWidget(id)`.
4. Register a `HudWidgetProvider` with `HudWidgetRegistry` mapping your configs → `HudWidgetEntry(placement, widget, editor)`; supply a `PlacementEditor` for edit support.
5. (Optional) Contribute an `EditScreenAction` to `EditScreenActionRegistry` for add/config affordances in the edit screen.

Reference implementation (GIF overlay): `overlay/GifHudFeature`, `GifOverlayConfig`, `GifPlacementEditor`, `GifAddAction`, plus the download/decode pipeline (`OverlayDownloader`, `OverlayDecoder`, `OverlayAddPipeline`).

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

### Running unit tests

Pure-JVM unit tests (JUnit 5 + kotlin-test) live in the shared `src/test/kotlin/` tree. Run them
against a registered version subproject (there is no root `test` task):

```bash
./gradlew :1.21.10:test
./gradlew :1.21.11:test
```

See `docs/delivery/33/33-2-junit-guide.md` for the wiring rationale.

Switch the active version (controls which preprocessor branches Stonecutter writes to disk in `src/`):

```bash
./gradlew "Set active project to 1.21.11"
./gradlew "Reset active project"   # restore VCS version before committing
```

Always run `"Reset active project"` before committing to avoid Stonecutter rewriting `src/` files in the diff.

### Adding a new Minecraft version

See the public-facing instructions in `README.md` (section: "Adding a new Minecraft version"). Summary: create `versions/<new-ver>/gradle.properties`, add `"<new-ver>"` to `settings.gradle.kts`, run `:<new-ver>:build`, fix any source-level diff with `//? if >=<new-ver> { ... //?}` blocks or named swaps in `stonecutter.gradle.kts`.