---
inclusion: always
---

# Rendering Pipeline — Use Only the Project's Own Renderers

This repository ships its own 2D GPU rendering pipeline (the original
"DLC" library — custom GLSL shaders + builders + MSDF fonts). **All UI,
HUD and overlay drawing must go through it.** Do not use Minecraft's
vanilla `DrawContext` helpers (`fill`, `drawText`, `drawTexture`,
`drawHorizontalLine`, etc.) or any other built-in renderer.

## What to use

| Need | Use |
|---|---|
| Filled rectangle / rounded card | `Builder.rectangle()` → `BuiltRectangle.render(...)` or `UIRender.rect(...)` / `UIRender.rectGradientH/V(...)` |
| Outline | `Builder.border()` → `BuiltBorder` or `UIRender.border(...)` |
| Sampled texture quad | `Builder.texture()` → `BuiltTexture` or `UIRender.texture(...)` |
| Text | `Builder.text()` → `BuiltText` or `UIRender.text(...)`, font via `Fonts.BIKO.get()` (MSDF) |
| Frosted-glass / blur | `Builder.blur()` → `BuiltBlur` or `UIRender.blur(...)` |

`UIRender` is a thin facade on top of `Builder`; prefer it from screens
and HUDs unless you need a builder-specific feature (smoothness, custom
quad colour state, custom UV, etc.).

## What NOT to use

* `DrawContext.fill / fillGradient / drawText / drawTextWithShadow /
  drawTexture / drawGuiTexture` — bypass the project's shaders, won't
  match the visual style, will look out of place next to existing UI.
* `TextRenderer` / `MinecraftClient.getInstance().textRenderer` for any
  visible text — must use MSDF (`Fonts.BIKO`) so glyphs match the rest
  of the UI.
* Any direct GL calls or `RenderSystem.drawElements` / immediate-mode
  buffers outside of the existing `Built*` records.

`DrawContext` is still allowed **only** for `enableScissor` /
`disableScissor` (no equivalent in the custom pipeline yet) — see
`SettingsPopup` for an example.

## Adding new shapes

If a new visual primitive is genuinely needed (e.g. a circle, a
polygon strip), extend the pipeline rather than reaching for vanilla:

1. Add a `*.vsh` / `*.fsh` / `*.json` triple under
   `src/main/resources/assets/blumdlc/shaders/core/` (re-use
   `include/common.glsl` for the SDF helpers).
2. Add a `Built*` record in `client/renderers/impl/` that loads the
   shader via `ShaderProgramKey` and emits the quad through
   `Tessellator` + `BufferRenderer.drawWithGlobalProgram`.
3. Add a matching `*Builder` in `client/builders/impl/` and expose it
   from `Builder`.
4. Optionally add a convenience method to `UIRender`.

## Why this matters

The custom pipeline gives the project its premium visual identity:
SDF-driven anti-aliased rounded rectangles with per-corner radii,
gradient quads, blur-with-rounded-corners, MSDF text with outlines.
Mixing in vanilla draw calls produces inconsistent edge AA, wrong
colour blending and mismatched font metrics — defeats the point of
having the pipeline at all.
