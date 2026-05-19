package dev.blumdlc.client.modules.impl;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.joml.Matrix4f;

import dev.blumdlc.client.modules.HudModule;
import dev.blumdlc.client.msdf.MsdfFont;
import dev.blumdlc.client.settings.ModeSetting;
import dev.blumdlc.client.settings.NumberSetting;
import dev.blumdlc.client.ui.Fonts;
import dev.blumdlc.client.ui.Theme;
import dev.blumdlc.client.ui.UIRender;
import dev.blumdlc.client.ui.util.ColorUtil;
import net.minecraft.client.MinecraftClient;

/**
 * Top-left brand chip with the client name and a configurable secondary
 * line (player nick / FPS / time / mix). Movable via the chat-screen HUD
 * editor.
 *
 * <p>Visuals:
 * <ul>
 *   <li>{@code Pill}     — the default rounded chip with gradient body, soft
 *                          drop shadow and a pulsing accent dot.</li>
 *   <li>{@code Glow}     — same pill plus a blurred coloured halo behind it.</li>
 *   <li>{@code Compact}  — a small pill with just the dot + brand.</li>
 *   <li>{@code Minimal}  — text-only, no background, accent-coloured brand.</li>
 * </ul>
 */
public final class Watermark extends HudModule {

	public final ModeSetting   style;
	public final ModeSetting   color;
	public final ModeSetting   info;
	public final NumberSetting fontSize;

	private float lastWidth = 90.0f;
	private float lastHeight = 22.0f;

	private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

	public Watermark() {
		super("Watermark", "Top-left brand chip");
		this.style    = new ModeSetting("Style",    "Pill",
			"Pill", "Glow", "Compact", "Minimal");
		this.color    = new ModeSetting("Color",    "Magenta",
			"Magenta", "Cyan", "Lime", "Gold", "Crimson", "Rainbow");
		this.info     = new ModeSetting("Info",     "Player",
			"Player", "FPS", "Time", "FPS+Time", "Off");
		this.fontSize = new NumberSetting("Font Size", 9.5,  6.0, 14.0, 0.5);

		addSetting(style);
		addSetting(color);
		addSetting(info);
		addSetting(fontSize);
		this.enabled = true;
	}

	@Override public float hudWidth()  { return lastWidth; }
	@Override public float hudHeight() { return lastHeight; }

	@Override
	protected void computeDefaultPosition(int sw, int sh) {
		this.x = 6.0f;
		this.y = 6.0f;
	}

	@Override
	protected void renderHud(Matrix4f matrix, float tickDelta, boolean editing) {
		MsdfFont font = Fonts.BIKO.get();
		String brand = "Blum";
		String suffix = computeSuffix();
		float fs = fontSize.getFloat();

		long now = System.currentTimeMillis();
		float pulse = 0.5f + 0.5f * (float) Math.sin(now * 0.004);
		int accent = paletteAccent(color.get(), now);
		int accentDark = paletteAccentDark(color.get(), now);

		switch (style.get()) {
			case "Glow":     drawGlow(matrix, font, brand, suffix, fs, pulse, accent, accentDark); break;
			case "Compact":  drawCompact(matrix, font, brand, fs, pulse, accent); break;
			case "Minimal":  drawMinimal(matrix, font, brand, suffix, fs, accent); break;
			case "Pill":
			default:         drawPill(matrix, font, brand, suffix, fs, pulse, accent, accentDark, false); break;
		}
	}

	// --------------------------------------------------------------------
	//  styles
	// --------------------------------------------------------------------

	private void drawPill(Matrix4f matrix, MsdfFont font,
			String brand, String suffix, float fs, float pulse,
			int accent, int accentDark, boolean withGlow) {

		float padX = 8.0f;
		float padY = 5.0f;
		float gap = 6.0f;

		float brandW  = UIRender.textWidth(font, brand,  fs);
		float suffixW = suffix.isEmpty() ? 0.0f : UIRender.textWidth(font, suffix, fs * 0.85f);
		float dotR = fs * 0.55f + 2.0f;

		float w = padX * 2 + dotR + gap + brandW
			+ (suffix.isEmpty() ? 0.0f : (gap + 1.0f + 5.0f + suffixW))
			+ 2.0f;
		float h = fs + padY * 2 + 2.0f;

		this.lastWidth = w;
		this.lastHeight = h;
		float x = this.x;
		float y = this.y;

		if (withGlow) {
			// Soft halo behind the pill via the blur renderer (project's own builder).
			float halo = 14.0f;
			UIRender.blur(matrix, x - halo, y - halo, w + halo * 2, h + halo * 2,
				h * 0.5f + halo, halo,
				ColorUtil.multiplyAlpha(accent, 0.25f + 0.15f * pulse));
		}

		// Drop shadow.
		UIRender.rect(matrix, x + 1.0f, y + 2.0f, w, h, h * 0.5f, 0x44000000);
		// Body gradient.
		UIRender.rectGradientH(matrix, x, y, w, h, h * 0.5f, 0xF01A1A24, 0xF023232F);
		// Subtle accent overlay (warmer left edge).
		UIRender.rectGradientH(matrix, x, y, w * 0.55f, h, h * 0.5f,
			ColorUtil.withAlpha(accent, 0.10f + 0.05f * pulse),
			ColorUtil.withAlpha(accent, 0.0f));
		UIRender.border(matrix, x, y, w, h, h * 0.5f, 1.0f, Theme.PANEL_BORDER);

		// Pulsing accent dot.
		float dotX = x + padX - 1.0f;
		float dotY = y + (h - dotR) * 0.5f;
		float dotPulseR = dotR + 1.5f + pulse * 1.0f;
		float dotPulseX = dotX - (dotPulseR - dotR) * 0.5f;
		float dotPulseY = dotY - (dotPulseR - dotR) * 0.5f;
		UIRender.rect(matrix, dotPulseX, dotPulseY, dotPulseR, dotPulseR, dotPulseR * 0.5f,
			ColorUtil.withAlpha(accent, 0.18f + 0.18f * pulse));
		UIRender.rect(matrix, dotX, dotY, dotR, dotR, dotR * 0.5f, accent);
		// Inner highlight.
		UIRender.rect(matrix, dotX + dotR * 0.25f, dotY + dotR * 0.20f,
			dotR * 0.50f, dotR * 0.35f, dotR * 0.25f,
			ColorUtil.withAlpha(0xFFFFFFFF, 0.55f));

		// Brand text.
		float textY = y + (h - fs) * 0.5f - 0.5f;
		UIRender.text(matrix, font, brand,
			dotX + dotR + gap, textY, fs, Theme.TEXT_PRIMARY, 0.06f);

		if (!suffix.isEmpty()) {
			float sepX = dotX + dotR + gap + brandW + gap;
			UIRender.rect(matrix, sepX, y + 6.0f, 1.0f, h - 12.0f, 0.0f, Theme.DIVIDER);
			UIRender.text(matrix, font, suffix,
				sepX + 5.0f, textY + 0.5f, fs * 0.85f, Theme.TEXT_SECONDARY, 0.05f);
		}
	}

	private void drawGlow(Matrix4f matrix, MsdfFont font,
			String brand, String suffix, float fs, float pulse,
			int accent, int accentDark) {
		drawPill(matrix, font, brand, suffix, fs, pulse, accent, accentDark, true);
	}

	private void drawCompact(Matrix4f matrix, MsdfFont font,
			String brand, float fs, float pulse, int accent) {
		float padX = 7.0f;
		float padY = 4.0f;
		float gap = 5.0f;
		float dotR = fs * 0.55f + 1.0f;
		float brandW = UIRender.textWidth(font, brand, fs);

		float w = padX * 2 + dotR + gap + brandW;
		float h = fs + padY * 2;

		this.lastWidth = w;
		this.lastHeight = h;
		float x = this.x;
		float y = this.y;

		UIRender.rect(matrix, x + 1.0f, y + 1.0f, w, h, h * 0.5f, 0x44000000);
		UIRender.rectGradientH(matrix, x, y, w, h, h * 0.5f, 0xF01A1A24, 0xF023232F);
		UIRender.border(matrix, x, y, w, h, h * 0.5f, 1.0f, Theme.PANEL_BORDER);

		float dotX = x + padX;
		float dotY = y + (h - dotR) * 0.5f;
		UIRender.rect(matrix, dotX, dotY, dotR, dotR, dotR * 0.5f,
			ColorUtil.lerp(accent, 0xFFFFFFFF, pulse * 0.25f));

		UIRender.text(matrix, font, brand,
			dotX + dotR + gap, y + (h - fs) * 0.5f - 0.5f,
			fs, Theme.TEXT_PRIMARY, 0.06f);
	}

	private void drawMinimal(Matrix4f matrix, MsdfFont font,
			String brand, String suffix, float fs, int accent) {
		float gap = 6.0f;
		float brandW = UIRender.textWidth(font, brand, fs);
		float suffixW = suffix.isEmpty() ? 0.0f : UIRender.textWidth(font, suffix, fs * 0.85f);
		float w = brandW + (suffix.isEmpty() ? 0.0f : (gap + suffixW)) + 8.0f;
		float h = fs + 8.0f;

		this.lastWidth = w;
		this.lastHeight = h;
		float x = this.x;
		float y = this.y;

		UIRender.text(matrix, font, brand, x + 4.0f, y + 4.0f, fs, accent, 0.07f);
		if (!suffix.isEmpty()) {
			UIRender.text(matrix, font, suffix,
				x + 4.0f + brandW + gap, y + 4.0f,
				fs * 0.85f, Theme.TEXT_SECONDARY, 0.05f);
		}
	}

	// --------------------------------------------------------------------
	//  helpers
	// --------------------------------------------------------------------

	private String computeSuffix() {
		MinecraftClient client = MinecraftClient.getInstance();
		switch (info.get()) {
			case "FPS":      return client.getCurrentFps() + " FPS";
			case "Time":     return LocalTime.now().format(HHMM);
			case "FPS+Time": return client.getCurrentFps() + " FPS · " + LocalTime.now().format(HHMM);
			case "Off":      return "";
			case "Player":
			default:
				return (client.player != null)
					? client.player.getGameProfile().getName()
					: "v1.0";
		}
	}

	/** Primary accent colour for the dot and accent overlay. */
	private static int paletteAccent(String palette, long now) {
		switch (palette) {
			case "Cyan":     return 0xFF22D3EE;
			case "Lime":     return 0xFFA3E635;
			case "Gold":     return 0xFFFCD34D;
			case "Crimson":  return 0xFFEF4444;
			case "Rainbow":  return rainbow(now, 0.0f);
			case "Magenta":
			default:         return 0xFFEF6CFB;
		}
	}

	/** Slightly darker tone used as the trailing edge in some gradients. */
	private static int paletteAccentDark(String palette, long now) {
		switch (palette) {
			case "Cyan":     return 0xFF60A5FA;
			case "Lime":     return 0xFF22C55E;
			case "Gold":     return 0xFFF59E0B;
			case "Crimson":  return 0xFFB91C1C;
			case "Rainbow":  return rainbow(now, 0.12f);
			case "Magenta":
			default:         return 0xFFC44CD8;
		}
	}

	private static int rainbow(long now, float phaseOffset) {
		float h = ((now % 6000L) / 6000.0f + phaseOffset) % 1.0f;
		if (h < 0.0f) h += 1.0f;
		return hsv(h, 0.85f, 1.0f);
	}

	private static int hsv(float h, float s, float v) {
		float r, g, b;
		float i = (float) Math.floor(h * 6.0f);
		float f = h * 6.0f - i;
		float p = v * (1.0f - s);
		float q = v * (1.0f - f * s);
		float t = v * (1.0f - (1.0f - f) * s);
		switch (((int) i) % 6) {
			case 0:  r = v; g = t; b = p; break;
			case 1:  r = q; g = v; b = p; break;
			case 2:  r = p; g = v; b = t; break;
			case 3:  r = p; g = q; b = v; break;
			case 4:  r = t; g = p; b = v; break;
			default: r = v; g = p; b = q; break;
		}
		int ri = clamp255(Math.round(r * 255.0f));
		int gi = clamp255(Math.round(g * 255.0f));
		int bi = clamp255(Math.round(b * 255.0f));
		return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
	}

	private static int clamp255(int v) {
		return v < 0 ? 0 : (v > 255 ? 255 : v);
	}
}
