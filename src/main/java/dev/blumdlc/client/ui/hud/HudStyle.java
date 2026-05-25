package dev.blumdlc.client.ui.hud;

import org.joml.Matrix4f;

import dev.blumdlc.client.ui.ClientTheme;
import dev.blumdlc.client.ui.UIRender;
import dev.blumdlc.client.ui.util.ColorUtil;

/**
 * Shared card chrome for the Minced-style HUDs (Watermark, KeybindsHud,
 * PotionsHud, StaffHud, TargetHud).
 *
 * <p>Aesthetic targets:
 * <ul>
 *   <li>compact dark card with a subtle vertical gradient,</li>
 *   <li>1 px hairline border + 1 px inner highlight for depth,</li>
 *   <li>2 px accent strip on the left edge,</li>
 *   <li>soft drop shadow underneath.</li>
 * </ul>
 *
 * <p>Every helper takes an {@code alpha} multiplier (0..1) so callers can
 * fade cards in / out without touching the colour palette. Callers that
 * need a custom accent (red for staff alerts, effect colour for potion
 * rows) should use the {@code -Colored} variants instead of relying on
 * the active {@link ClientTheme}.
 */
public final class HudStyle {

	/** Standard corner radius for HUD cards. */
	public static final float RADIUS    = 4.0f;
	/** Width of the accent strip on the card's left edge. */
	public static final float ACCENT_W  = 2.0f;
	/** Horizontal inset from the card edge to inner content. */
	public static final float PAD_X     = 8.0f;
	/** Vertical inset from the card edge to inner content. */
	public static final float PAD_Y     = 6.0f;

	// =========================================================================
	//  Palette (multiplied by per-call alpha at render time)
	// =========================================================================

	private static final int BG_TOP   = 0xEE12141C;
	private static final int BG_BOT   = 0xEE0A0C12;
	private static final int BORDER   = 0xFFFFFFFF; // multiplied by 0.20 alpha
	private static final int INNER    = 0xFFFFFFFF; // multiplied by 0.06 alpha
	private static final int SHADOW   = 0xFF000000; // multiplied by 0.32 alpha
	private static final int DIVIDER  = 0xFFFFFFFF; // multiplied by 0.06 alpha
	private static final int TRACK_BG = 0xFF22252E;

	// =========================================================================
	//  Card chrome
	// =========================================================================

	/**
	 * Paints the standard Minced card chrome — drop shadow, gradient body,
	 * hairline border and inner highlight, plus a left-edge accent strip
	 * pulled from the active {@link ClientTheme}. Returns immediately if
	 * the size is non-positive.
	 */
	public static void card(Matrix4f m, float x, float y, float w, float h, float alpha) {
		card(m, x, y, w, h, alpha, ClientTheme.accent());
	}

	/**
	 * Same as {@link #card(Matrix4f, float, float, float, float, float)} but
	 * uses a custom accent strip colour. Useful for staff alerts (red) or
	 * per-effect potion strips.
	 */
	public static void card(Matrix4f m, float x, float y, float w, float h,
			float alpha, int accentColor) {
		if (w <= 0.0f || h <= 0.0f || alpha <= 0.0f) return;
		// Drop shadow
		UIRender.rect(m, x + 1.0f, y + 2.0f, w, h, RADIUS,
			ColorUtil.multiplyAlpha(SHADOW, 0.32f * alpha));
		// Gradient body
		UIRender.rectGradientV(m, x, y, w, h, RADIUS,
			ColorUtil.multiplyAlpha(BG_TOP, alpha),
			ColorUtil.multiplyAlpha(BG_BOT, alpha));
		// Outer hairline
		UIRender.border(m, x, y, w, h, RADIUS, 0.8f,
			ColorUtil.multiplyAlpha(BORDER, 0.20f * alpha));
		// Inner highlight
		UIRender.border(m, x + 1.0f, y + 1.0f, w - 2.0f, h - 2.0f, Math.max(0.0f, RADIUS - 1.0f), 0.6f,
			ColorUtil.multiplyAlpha(INNER, 0.06f * alpha));
		// Accent strip
		accentStrip(m, x, y, h, alpha, accentColor);
	}

	/** Paints just the 2 px accent strip with the active {@link ClientTheme}. */
	public static void accentStrip(Matrix4f m, float cardX, float cardY, float cardH, float alpha) {
		accentStrip(m, cardX, cardY, cardH, alpha, ClientTheme.accent());
	}

	/** Paints a 2 px accent strip with a custom colour. */
	public static void accentStrip(Matrix4f m, float cardX, float cardY, float cardH,
			float alpha, int color) {
		// Inset slightly so the strip sits inside the rounded body.
		UIRender.rect(m, cardX + 1.5f, cardY + 2.5f, ACCENT_W, Math.max(0.0f, cardH - 5.0f),
			ACCENT_W * 0.5f,
			ColorUtil.multiplyAlpha(color, 0.95f * alpha));
	}

	// =========================================================================
	//  Inner pieces
	// =========================================================================

	/** Subtle 1 px horizontal divider used between rows in stacked panels. */
	public static void divider(Matrix4f m, float x, float y, float w, float alpha) {
		if (w <= 0.0f) return;
		UIRender.rect(m, x, y, w, 1.0f, 0.0f,
			ColorUtil.multiplyAlpha(DIVIDER, 0.06f * alpha));
	}

	/**
	 * Thin horizontal progress bar with a track + accent fill. Used inside
	 * potion rows and the target HP bar. Pass any fill colour you like;
	 * the track is a fixed dark grey.
	 */
	public static void progressBar(Matrix4f m, float x, float y, float w, float h,
			float fraction, int fillColor, float alpha) {
		if (w <= 0.0f || h <= 0.0f) return;
		fraction = Math.max(0.0f, Math.min(1.0f, fraction));
		UIRender.rect(m, x, y, w, h, h * 0.5f,
			ColorUtil.multiplyAlpha(TRACK_BG, alpha));
		if (fraction > 0.0f) {
			UIRender.rect(m, x, y, w * fraction, h, h * 0.5f,
				ColorUtil.multiplyAlpha(fillColor, alpha));
		}
	}

	private HudStyle() {
	}

}
