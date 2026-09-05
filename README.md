# Custom Title Screen

A native-rendered (no browser/CEF) main menu for Minecraft 26.2, structurally based on
LiquidBounce's `Title.svelte` layout, built as a standalone Fabric mod.

## Building

Requires JDK 25 (matches this Minecraft version's toolchain).

```bash
./gradlew build
```

The built jar will be in `build/libs/`.

## Running in a dev environment

```bash
./gradlew runClient
```

## Structure

- `CustomTitleScreen.kt` - the screen itself: button layout/animation, real vanilla panorama
  background (`Minecraft.gameRenderer.panorama()`), icon texture blitting.
- `CustomTitleModInitializer.kt` - swaps vanilla's `TitleScreen` for `CustomTitleScreen` via
  `ClientTickEvents.END_CLIENT_TICK`.
- `assets/customtitle/textures/gui/` - button icon placeholders (plain colored circles - swap
  with real art, same filenames).

## Customizing

- Branding/links: `BrandConfig` object at the top of `CustomTitleScreen.kt`.
- Button actions: `MAIN_BUTTONS` / `SECONDARY_BUTTONS` lists in the same file.
- Background: `BACKGROUND_MODE` (`AURORA`, `IMAGE`, `PANORAMA`, `GRADIENT`, or `CUSTOM`). The
  "Toggle Background" button cycles the first four; the "Custom Background" button opens a
  picker for user-supplied images.

## Custom backgrounds

Drop `.png`/`.jpg`/`.jpeg` files into `<game directory>/background/` (created automatically on
first run) and click **Custom Background** on the title screen to pick one. Resolution is read
directly from each file - there's no fixed size requirement. The selection persists across
restarts via a small `selected.txt` file in that same folder. Click **Refresh** in the picker if
you add files while the game is already running.

## Custom fonts (TTF interface)

The mod ships with a drop-in TTF interface:

1. Replace `src/main/resources/assets/customtitle/font/custom.ttf` with any `.ttf` font file
   (keep the filename `custom.ttf`).
2. Rebuild the mod. Both the title screen and the background picker now render all text in that
   font.
3. Characters not covered by the TTF (e.g. CJK) automatically fall back to the vanilla font, and
   if the file is missing or invalid the mod silently keeps the default font.

The font definition lives in `assets/customtitle/font/custom.json` (size/oversample can be tuned
there).

## CI

`.github/workflows/build.yml` builds on every push/PR and uploads the jar as a workflow artifact.
