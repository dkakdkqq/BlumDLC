package dev.blumdlc.client.ui;

import org.joml.Matrix4f;

import dev.blumdlc.client.builders.Builder;
import dev.blumdlc.client.builders.states.QuadColorState;
import dev.blumdlc.client.builders.states.QuadRadiusState;
import dev.blumdlc.client.builders.states.SizeState;
import dev.blumdlc.client.msdf.MsdfFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;

/**
 * Thin facade around the existing builders so the GUI never touches vanilla draw helpers.
 *
 * <p>Everything here boils down to a single {@code Builder.*} call (or a
 * sequence of them, in the case of vector glyphs) — the rest of the codebase
 * is expected to go through this class instead of poking the builders
 * directly.
 */
public final class UIRender {

	// =========================================================================
	//  Rectangles
	// =========================================================================

	public static void rect(Matrix4f m, float x, float y, float w, float h, float radius, int color) {
		Builder.rectangle()
			.size(new SizeState(w, h))
			.radius(new QuadRadiusState(radius))
			.color(new QuadColorState(color))
			.smoothness(1.0f)
			.build()
			.render(m, x, y);
	}

	public static void rectGradient(Matrix4f m, float x, float y, float w, float h, float radius,
			int c1, int c2, int c3, int c4) {
		Builder.rectangle()
			.size(new SizeState(w, h))
			.radius(new QuadRadiusState(radius))
			.color(new QuadColorState(c1, c2, c3, c4))
			.smoothness(1.0f)
			.build()
			.render(m, x, y);
	}

	public static void rectGradientH(Matrix4f m, float x, float y, float w, float h, float radius,
			int left, int right) {
		// QuadColorState corners: c1=top-left, c2=bottom-left, c3=bottom-right, c4=top-right
		rectGradient(m, x, y, w, h, radius, left, left, right, right);
	}

	public static void rectGradientV(Matrix4f m, float x, float y, float w, float h, float radius,
			int top, int bottom) {
		rectGradient(m, x, y, w, h, radius, top, bottom, bottom, top);
	}

	public static void border(Matrix4f m, float x, float y, float w, float h, float radius,
			float thickness, int color) {
		Builder.border()
			.size(new SizeState(w, h))
			.radius(new QuadRadiusState(radius))
			.color(new QuadColorState(color))
			.thickness(thickness)
			.smoothness(1.0f, 1.0f)
			.build()
			.render(m, x, y);
	}

	public static void blur(Matrix4f m, float x, float y, float w, float h, float radius,
			float blurRadius, int tint) {
		Builder.blur()
			.size(new SizeState(w, h))
			.radius(new QuadRadiusState(radius))
			.color(new QuadColorState(tint))
			.blurRadius(blurRadius)
			.smoothness(1.0f)
			.build()
			.render(m, x, y);
	}

	// =========================================================================
	//  Text
	// =========================================================================

	public static void text(Matrix4f m, MsdfFont font, String s, float x, float y, float size, int color) {
		text(m, font, s, x, y, size, color, 0.05f);
	}

	public static void text(Matrix4f m, MsdfFont font, String s, float x, float y, float size, int color,
			float thickness) {
		Builder.text()
			.font(font)
			.text(s)
			.size(size)
			.thickness(thickness)
			.color(color)
			.build()
			.render(m, x, y);
	}

	public static float textWidth(MsdfFont font, String s, float size) {
		return font.getWidth(s, size);
	}

	/**
	 * Truncates {@code s} with a trailing ellipsis if its rendered width
	 * exceeds {@code maxW}. Returns the original string if it fits, an
	 * empty string if {@code maxW <= 0}, and a best-fit string with
	 * {@code "..."} suffix otherwise.
	 */
	public static String ellipsize(MsdfFont font, String s, float size, float maxW) {
		if (s == null || s.isEmpty()) return "";
		if (maxW <= 0.0f) return "";
		if (textWidth(font, s, size) <= maxW) return s;
		String dots = "...";
		float dw = textWidth(font, dots, size);
		if (dw >= maxW) return ""; // not enough room even for the ellipsis
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			sb.append(s.charAt(i));
			if (textWidth(font, sb.toString(), size) + dw > maxW) {
				if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
				break;
			}
		}
		return sb.append(dots).toString();
	}

	// =========================================================================
	//  Textures
	// =========================================================================

	/**
	 * Draws the entire texture identified by {@code id} into {@code (x,y,w,h)},
	 * tinted with {@code color} ({@code 0xFFFFFFFF} = untinted).
	 */
	public static void texture(Matrix4f m, Identifier id, float x, float y, float w, float h, int color) {
		texture(m, id, x, y, w, h, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, color);
	}

	/**
	 * Draws a UV sub-region {@code (u,v,uW,vH)} (all in 0..1 normalized
	 * coordinates) of the texture identified by {@code id}, with optional
	 * corner radius for rounded clipping.
	 */
	public static void texture(Matrix4f m, Identifier id,
			float x, float y, float w, float h, float radius,
			float u, float v, float uW, float vH, int color) {
		AbstractTexture tex = MinecraftClient.getInstance().getTextureManager().getTexture(id);
		if (tex == null) {
			return;
		}
		Builder.texture()
			.size(new SizeState(w, h))
			.radius(new QuadRadiusState(radius))
			.color(new QuadColorState(color))
			.smoothness(1.0f)
			.texture(u, v, uW, vH, tex)
			.build()
			.render(m, x, y);
	}

	// =========================================================================
	//  Vector glyphs
	//
	//  Drawn from primitives so they don't depend on any glyph living in
	//  the MSDF atlas — the bundled `biko` font is missing several common
	//  ASCII chars (^, ', ~) and would crash on Android with an empty
	//  vertex buffer. See BuiltText.render for the guard.
	// =========================================================================

	/**
	 * A capped 1-px-edge straight line from {@code (x0,y0)} to {@code (x1,y1)},
	 * approximated by overlapping rounded squares of side {@code thickness}.
	 *
	 * <p>Subpixel-stepped (~one square per half-thickness) so the line reads
	 * smooth; perfectly fine for short HUD glyph strokes.
	 */
	public static void line(Matrix4f m, float x0, float y0, float x1, float y1,
			float thickness, int color) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len <= 0.0001f) {
			rect(m, x0 - thickness * 0.5f, y0 - thickness * 0.5f, thickness, thickness,
				thickness * 0.5f, color);
			return;
		}
		int steps = Math.max(2, (int) Math.ceil(len / Math.max(0.4f, thickness * 0.45f)));
		for (int i = 0; i <= steps; i++) {
			float t = (float) i / steps;
			float cx = x0 + dx * t - thickness * 0.5f;
			float cy = y0 + dy * t - thickness * 0.5f;
			rect(m, cx, cy, thickness, thickness, thickness * 0.5f, color);
		}
	}

	/**
	 * Draws a checkmark glyph fitting an {@code size × size} cell anchored
	 * at {@code (x, y)} in the standard top-left convention. Uses two short
	 * diagonal {@link #line line} strokes.
	 *
	 * <p>The stroke is auto-scaled from {@code size} to keep the check
	 * looking proportional from ~6 px (compact ClickGUI rows) up to ~16 px
	 * (popup settings).
	 */
	public static void checkmark(Matrix4f m, float x, float y, float size, int color) {
		float thickness = Math.max(1.0f, size * 0.18f);
		// Three control points of the ✓ inside the cell.
		float ax = x + size * 0.16f, ay = y + size * 0.52f;
		float bx = x + size * 0.42f, by = y + size * 0.78f;
		float cx = x + size * 0.86f, cy = y + size * 0.20f;
		line(m, ax, ay, bx, by, thickness, color);
		line(m, bx, by, cx, cy, thickness, color);
	}

	/**
	 * Draws a small downward-pointing chevron (▾) in an {@code size × size}
	 * cell anchored at {@code (x, y)}. {@code up=true} flips it to ▴.
	 */
	public static void caret(Matrix4f m, float x, float y, float size, boolean up, int color) {
		float thickness = Math.max(1.0f, size * 0.16f);
		if (up) {
			float ax = x + size * 0.15f, ay = y + size * 0.70f;
			float bx = x + size * 0.50f, by = y + size * 0.30f;
			float cxp = x + size * 0.85f, cy = y + size * 0.70f;
			line(m, ax, ay, bx, by, thickness, color);
			line(m, bx, by, cxp, cy, thickness, color);
		} else {
			float ax = x + size * 0.15f, ay = y + size * 0.30f;
			float bx = x + size * 0.50f, by = y + size * 0.70f;
			float cxp = x + size * 0.85f, cy = y + size * 0.30f;
			line(m, ax, ay, bx, by, thickness, color);
			line(m, bx, by, cxp, cy, thickness, color);
		}
	}

	private UIRender() {
	}

}
