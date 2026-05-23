package dev.blumdlc.client.modules.impl;

import org.joml.Matrix4f;

import dev.blumdlc.client.modules.HudModule;
import dev.blumdlc.client.msdf.MsdfFont;
import dev.blumdlc.client.ui.ClientTheme;
import dev.blumdlc.client.ui.Fonts;
import dev.blumdlc.client.ui.Theme;
import dev.blumdlc.client.ui.UIRender;
import dev.blumdlc.client.ui.util.ColorUtil;


/**
 * Premium "Bloom" watermark — a luxurious floating glass badge with multi-layer
 * blur backdrop, animated glow ring, gradient text, shimmer highlight and
 * ambient breathing pulse. No netherite icon, just pure typography excellence.
 */
public final class Watermark extends HudModule {

	private float lastW = 90.0f;
	private float lastH = 28.0f;

	/** Subtle breathing animation phase (radians). */
	private float breathPhase = 0.0f;

	public Watermark() {
		super("Watermark", "Premium Bloom badge with glow and blur");
		this.enabled = true;
	}

	@Override public float hudWidth()  { return lastW; }
	@Override public float hudHeight() { return lastH; }

	@Override
	protected void computeDefaultPosition(int sw, int sh) {
		this.x = 10.0f;
		this.y = 10.0f;
	}

	@Override
	protected void renderHud(Matrix4f matrix, float tickDelta, boolean editing) {
		MsdfFont font = Fonts.BIKO.get();

		// --- Configuration ---
		String brandText = "Bloom";
		float fontSize = 10.0f;
		float padX = 14.0f;
		float padY = 8.0f;

		// Measure text
		float textW = UIRender.textWidth(font, brandText, fontSize);
		float w = textW + padX * 2.0f;
		float h = fontSize + padY * 2.0f;
		lastW = w;
		lastH = h;

		float px = this.x;
		float py = this.y;

		// Breathing animation
		breathPhase += tickDelta * 0.04f;
		if (breathPhase > (float)(Math.PI * 2.0)) breathPhase -= (float)(Math.PI * 2.0);
		float breathVal = (float)(Math.sin(breathPhase) * 0.5 + 0.5); // 0..1

		// Get theme colours
		int accentFrom = ClientTheme.from();
		int accentTo = ClientTheme.to();
		int accent = ClientTheme.accent();

		// =====================================================================
		// Layer 1: Outer glow (large soft shadow with accent tint)
		// =====================================================================
		float glowExpand = 6.0f + breathVal * 2.0f;
		float glowAlpha = 0.25f + breathVal * 0.1f;
		int glowColor = ColorUtil.withAlpha(accent, glowAlpha);
		UIRender.rect(matrix,
			px - glowExpand, py - glowExpand,
			w + glowExpand * 2.0f, h + glowExpand * 2.0f,
			12.0f, glowColor);

		// =====================================================================
		// Layer 2: Heavy blur backdrop (frosted glass effect)
		// =====================================================================
		UIRender.blur(matrix, px, py, w, h, 8.0f, 14.0f,
			ColorUtil.withAlpha(0x000D1117, 0.55f));

		// =====================================================================
		// Layer 3: Dark glass panel with slight transparency
		// =====================================================================
		int panelTop = ColorUtil.withAlpha(0x0D1117, 0.82f);
		int panelBot = ColorUtil.withAlpha(0x080B10, 0.90f);
		UIRender.rectGradientV(matrix, px, py, w, h, 8.0f, panelTop, panelBot);

		// =====================================================================
		// Layer 4: Inner subtle blur for depth (lighter, smaller radius)
		// =====================================================================
		UIRender.blur(matrix, px + 2, py + 2, w - 4, h - 4, 6.0f, 6.0f,
			ColorUtil.withAlpha(0x1A1F2E, 0.3f));

		// =====================================================================
		// Layer 5: Gradient accent border (breathing opacity)
		// =====================================================================
		float borderAlpha = 0.5f + breathVal * 0.25f;
		int borderFrom = ColorUtil.withAlpha(accentFrom, borderAlpha);
		int borderTo = ColorUtil.withAlpha(accentTo, borderAlpha);
		// Top-left to bottom-right gradient border
		UIRender.border(matrix, px, py, w, h, 8.0f, 0.9f,
			ColorUtil.lerp(borderFrom, borderTo, 0.5f));

		// =====================================================================
		// Layer 6: Top highlight shimmer line (moves with time)
		// =====================================================================
		float shimmerPhase = (System.currentTimeMillis() % 4000L) / 4000.0f; // 0..1 over 4 sec
		float shimmerX = px + 4.0f + (w - 8.0f) * shimmerPhase;
		float shimmerW = 24.0f;
		// Clamp shimmer to not exceed badge bounds
		float shimmerEnd = Math.min(shimmerX + shimmerW, px + w - 4.0f);
		float actualShimmerW = shimmerEnd - shimmerX;
		if (actualShimmerW > 2.0f) {
			int shimmerColor = ColorUtil.withAlpha(0xFFFFFF, 0.08f + breathVal * 0.04f);
			UIRender.rect(matrix, shimmerX, py + 1.0f, actualShimmerW, 1.2f, 0.6f, shimmerColor);
		}

		// =====================================================================
		// Layer 7: Bottom gradient accent bar
		// =====================================================================
		float barAlpha = 0.7f + breathVal * 0.15f;
		UIRender.rectGradientH(matrix, px + 6, py + h - 2.0f, w - 12, 1.8f, 0.9f,
			ColorUtil.withAlpha(accentFrom, barAlpha),
			ColorUtil.withAlpha(accentTo, barAlpha));

		// =====================================================================
		// Layer 8: Inner glow (subtle radial-like accent at center-bottom)
		// =====================================================================
		float innerGlowW = w * 0.5f;
		float innerGlowH = h * 0.35f;
		float igx = px + (w - innerGlowW) * 0.5f;
		float igy = py + h - innerGlowH - 1.0f;
		int innerGlowColor = ColorUtil.withAlpha(accent, 0.06f + breathVal * 0.03f);
		UIRender.rect(matrix, igx, igy, innerGlowW, innerGlowH, 4.0f, innerGlowColor);

		// =====================================================================
		// Layer 9: Brand text — gradient coloured with thick MSDF for clarity
		// =====================================================================
		float tx = px + padX;
		float ty = py + padY;

		// Shadow text layer (subtle depth)
		int shadowColor = ColorUtil.withAlpha(0x000000, 0.5f);
		UIRender.text(matrix, font, brandText, tx + 0.5f, ty + 0.8f, fontSize,
			shadowColor, 0.06f);

		// Main text — use accent colour with slight brightness boost
		int textColor = ColorUtil.lerp(accentFrom, accentTo,
			0.3f + breathVal * 0.2f);
		// Brighten text slightly for premium feel
		textColor = ColorUtil.lerp(textColor, 0xFFFFFFFF, 0.25f);
		UIRender.text(matrix, font, brandText, tx, ty, fontSize,
			textColor, 0.07f);

		// =====================================================================
		// Layer 10: Subtle corner accents (premium jewel-like dots)
		// =====================================================================
		float dotSize = 1.8f;
		float dotOff = 3.5f;
		int dotColor = ColorUtil.withAlpha(accent, 0.35f + breathVal * 0.15f);
		// Top-left
		UIRender.rect(matrix, px + dotOff, py + dotOff, dotSize, dotSize, 0.9f, dotColor);
		// Top-right
		UIRender.rect(matrix, px + w - dotOff - dotSize, py + dotOff, dotSize, dotSize, 0.9f, dotColor);
		// Bottom-left
		UIRender.rect(matrix, px + dotOff, py + h - dotOff - dotSize, dotSize, dotSize, 0.9f, dotColor);
		// Bottom-right
		UIRender.rect(matrix, px + w - dotOff - dotSize, py + h - dotOff - dotSize, dotSize, dotSize, 0.9f, dotColor);
	}
}
