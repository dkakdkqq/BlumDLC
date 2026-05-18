package dev.blumdlc.client.modules.impl;

import org.joml.Matrix4f;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.msdf.MsdfFont;
import dev.blumdlc.client.settings.ModeSetting;
import dev.blumdlc.client.settings.NumberSetting;
import dev.blumdlc.client.ui.Fonts;
import dev.blumdlc.client.ui.Theme;
import dev.blumdlc.client.ui.UIRender;
import dev.blumdlc.client.ui.util.ColorUtil;
import net.minecraft.client.MinecraftClient;

/**
 * Top-left brand chip with the client name + the current player's nickname.
 *
 * <p>Inspired by Minced/Celestial-style HUDs: a rounded pill, soft glow on
 * the left, a dot icon, a large brand label and a smaller secondary label.
 */
public final class Watermark extends Module {

	public final ModeSetting style;
	public final NumberSetting fontSize;

	public Watermark() {
		super("Watermark", "Top-left brand chip", Category.RENDER);
		this.style    = new ModeSetting("Style",    "Pill",   "Pill", "Minimal");
		this.fontSize = new NumberSetting("Font Size", 9.5,  6.0, 14.0, 0.5);
		addSetting(style);
		addSetting(fontSize);
		this.enabled = true;
	}

	@Override
	public void onRender(Matrix4f matrix, float tickDelta) {
		MsdfFont font = Fonts.BIKO.get();
		String brand = "Blum";
		String suffix;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			suffix = client.player.getGameProfile().getName();
		} else {
			suffix = "v1.0";
		}

		float fs = fontSize.getFloat();
		float padX = 8.0f;
		float padY = 5.0f;
		float gap = 6.0f;

		float brandW = UIRender.textWidth(font, brand, fs);
		float suffixW = UIRender.textWidth(font, suffix, fs * 0.85f);
		float w = padX * 2 + brandW + gap + suffixW + 14.0f;
		float h = fs + padY * 2 + 2.0f;
		float x = 6.0f, y = 6.0f;

		if (style.is("Pill")) {
			// Drop shadow
			UIRender.rect(matrix, x + 1, y + 2, w, h, h * 0.5f, 0x44000000);
			// Body
			UIRender.rectGradientH(matrix, x, y, w, h, h * 0.5f,
				0xF01A1A24, 0xF023232F);
			UIRender.border(matrix, x, y, w, h, h * 0.5f, 1.0f, Theme.PANEL_BORDER);

			// Accent dot
			float dotR = h - padY * 2 - 2.0f;
			float dotX = x + padX - 1.0f;
			float dotY = y + (h - dotR) * 0.5f;
			UIRender.rect(matrix, dotX, dotY, dotR, dotR, dotR * 0.5f, Theme.ACCENT);
			UIRender.rect(matrix, dotX + dotR * 0.25f, dotY + dotR * 0.2f,
				dotR * 0.5f, dotR * 0.35f, dotR * 0.25f, ColorUtil.withAlpha(0xFFFFFFFF, 0.55f));

			// Brand text
			float textY = y + (h - fs) * 0.5f - 0.5f;
			UIRender.text(matrix, font, brand,
				dotX + dotR + gap, textY, fs, Theme.TEXT_PRIMARY, 0.06f);
			// Separator + suffix
			float sepX = dotX + dotR + gap + brandW + gap;
			UIRender.rect(matrix, sepX, y + 6.0f, 1.0f, h - 12.0f, 0.0f, Theme.DIVIDER);
			UIRender.text(matrix, font, suffix,
				sepX + 5.0f, textY + 0.5f, fs * 0.85f, Theme.TEXT_SECONDARY, 0.05f);
		} else {
			// Minimal: just the brand text in accent + suffix dimmer.
			UIRender.text(matrix, font, brand, x + 4.0f, y + 4.0f, fs, Theme.ACCENT, 0.07f);
			UIRender.text(matrix, font, suffix,
				x + 4.0f + UIRender.textWidth(font, brand, fs) + gap, y + 4.0f,
				fs * 0.85f, Theme.TEXT_SECONDARY, 0.05f);
		}
	}
}
