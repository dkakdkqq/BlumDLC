package dev.blumdlc.client.modules.impl;

import org.joml.Matrix4f;

import dev.blumdlc.client.modules.HudModule;
import dev.blumdlc.client.msdf.MsdfFont;
import dev.blumdlc.client.ui.ClientTheme;
import dev.blumdlc.client.ui.Fonts;
import dev.blumdlc.client.ui.UIRender;
import dev.blumdlc.client.ui.hud.HudStyle;
import dev.blumdlc.client.ui.util.ColorUtil;
import net.minecraft.util.Identifier;

/**
 * Minced-style "Bloom" watermark — compact dark card with the official logo
 * and the brand wordmark in {@link ClientTheme} accent.
 *
 * <p>One layer of card chrome from {@link HudStyle}, no multi-layer glow
 * stack: this is intentional. The previous flashy build was visually loud
 * and tended to clash with other HUDs; the Minced look is calm, monochrome
 * and lets the accent colour do the heavy lifting.
 */
public final class Watermark extends HudModule {

	/** The official Bloom logo bundled in resources. */
	private static final Identifier LOGO_TEX =
		Identifier.of("blumdlc", "textures/logo/logo.png");

	private float lastW = 92.0f;
	private float lastH = 22.0f;

	public Watermark() {
		super("Watermark", "Compact Bloom badge with logo and wordmark");
		this.enabled = true;
	}

	@Override public float hudWidth()  { return lastW; }
	@Override public float hudHeight() { return lastH; }

	@Override
	protected void computeDefaultPosition(int sw, int sh) {
		this.x = 6.0f;
		this.y = 6.0f;
	}

	@Override
	protected void renderHud(Matrix4f matrix, float tickDelta, boolean editing) {
		MsdfFont font = Fonts.BIKO.get();

		String brand = "Bloom";
		float brandFs = 9.5f;

		float logoSize = 12.0f;
		float padX = HudStyle.PAD_X;
		float gap = 6.0f;

		float brandW = UIRender.textWidth(font, brand, brandFs);

		// Card width fits: accent strip + small inset + logo + gap + brand + padding.
		float w = HudStyle.ACCENT_W + 4.0f + logoSize + gap + brandW + padX;
		float h = 22.0f;
		this.lastW = w;
		this.lastH = h;

		float px = this.x;
		float py = this.y;
		float a = 1.0f;

		// Card chrome (drop shadow + gradient body + accent strip)
		HudStyle.card(matrix, px, py, w, h, a);

		// Logo — sits just to the right of the accent strip.
		float logoX = px + HudStyle.ACCENT_W + 4.0f;
		float logoY = py + (h - logoSize) * 0.5f;
		UIRender.texture(matrix, LOGO_TEX, logoX, logoY, logoSize, logoSize, 0xFFFFFFFF);

		// Brand wordmark — accent colour, slightly thicker glyphs for clarity.
		float brandX = logoX + logoSize + gap;
		float brandY = py + (h - brandFs) * 0.5f;
		UIRender.text(matrix, font, brand, brandX, brandY, brandFs,
			ColorUtil.lerp(ClientTheme.from(), ClientTheme.to(), 0.35f), 0.07f);
	}
}
