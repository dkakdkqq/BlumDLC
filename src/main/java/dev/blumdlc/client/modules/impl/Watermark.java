package dev.blumdlc.client.modules.impl;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.joml.Matrix4f;

import dev.blumdlc.client.modules.HudModule;
import dev.blumdlc.client.msdf.MsdfFont;
import dev.blumdlc.client.settings.ModeSetting;
import dev.blumdlc.client.ui.ClientTheme;
import dev.blumdlc.client.ui.Fonts;
import dev.blumdlc.client.ui.Theme;
import dev.blumdlc.client.ui.UIRender;
import dev.blumdlc.client.ui.util.ColorUtil;
import net.minecraft.client.MinecraftClient;


/**
 * Minced-style watermark — a sleek pill with segmented info blocks:
 *
 *   [ logo | Blum build | user icon username | ping/fps ]
 *
 * Background is a rounded rectangle with gradient accent border on the
 * bottom edge. Text uses the client font colour, build name gets a
 * two-tone gradient using the active theme colours.
 */
public final class Watermark extends HudModule {

	public final ModeSetting info;

	private float lastW = 140.0f;
	private float lastH = 18.0f;

	private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

	public Watermark() {
		super("Watermark", "Minced-style brand bar");
		this.info = new ModeSetting("Right Info", "FPS",
			"FPS", "Time", "Server", "Off");
		addSetting(info);
		this.enabled = true;
	}

	@Override public float hudWidth()  { return lastW; }
	@Override public float hudHeight() { return lastH; }

	@Override
	protected void computeDefaultPosition(int sw, int sh) {
		this.x = 8.0f;
		this.y = 8.0f;
	}


	@Override
	protected void renderHud(Matrix4f matrix, float tickDelta, boolean editing) {
		MsdfFont font = Fonts.BIKO.get();
		MinecraftClient mc = MinecraftClient.getInstance();

		float fs = 8.0f;
		float padX = 6.0f;
		float padY = 4.0f;
		float sep = 5.0f;  // separator gap

		// Segments
		String brand = "Blum";
		String build = "v1.0";
		String username = mc.player != null
			? mc.player.getGameProfile().getName() : "Player";
		String rightText = computeRight(mc);

		// Measure
		float brandW = UIRender.textWidth(font, brand, fs);
		float buildW = UIRender.textWidth(font, build, fs * 0.85f);
		float userW  = UIRender.textWidth(font, username, fs * 0.85f);
		float rightW = rightText.isEmpty() ? 0 : UIRender.textWidth(font, rightText, fs * 0.85f);

		// Total width
		float w = padX + brandW + sep + buildW + sep + userW
			+ (rightW > 0 ? sep + rightW : 0) + padX;
		float h = fs + padY * 2;

		lastW = w;
		lastH = h;
		float px = this.x, py = this.y;

		// Background — dark rounded rect
		UIRender.rect(matrix, px + 0.5f, py + 1.0f, w, h, 4.0f, 0x30000000); // shadow
		UIRender.rect(matrix, px, py, w, h, 4.0f, 0xF00E1018);
		UIRender.border(matrix, px, py, w, h, 4.0f, 0.8f, Theme.PANEL_BORDER);


		// Gradient accent line at bottom
		UIRender.rectGradientH(matrix, px + 4, py + h - 1.5f, w - 8, 1.5f, 0.75f,
			ColorUtil.multiplyAlpha(ClientTheme.from(), 0.8f),
			ColorUtil.multiplyAlpha(ClientTheme.to(), 0.8f));

		// Render segments
		float tx = px + padX;
		float ty = py + padY;

		// Brand — gradient coloured
		UIRender.text(matrix, font, brand, tx, ty, fs,
			ClientTheme.accent(), 0.06f);
		tx += brandW + sep;

		// Divider
		UIRender.rect(matrix, tx - 2.5f, py + 3.0f, 0.8f, h - 6.0f, 0.0f, Theme.DIVIDER);

		// Build — muted
		UIRender.text(matrix, font, build, tx, ty + 0.5f, fs * 0.85f,
			Theme.TEXT_SECONDARY, 0.04f);
		tx += buildW + sep;

		// Divider
		UIRender.rect(matrix, tx - 2.5f, py + 3.0f, 0.8f, h - 6.0f, 0.0f, Theme.DIVIDER);

		// Username
		UIRender.text(matrix, font, username, tx, ty + 0.5f, fs * 0.85f,
			Theme.TEXT_PRIMARY, 0.04f);
		tx += userW + sep;

		// Right info
		if (!rightText.isEmpty()) {
			UIRender.rect(matrix, tx - 2.5f, py + 3.0f, 0.8f, h - 6.0f, 0.0f, Theme.DIVIDER);
			UIRender.text(matrix, font, rightText, tx, ty + 0.5f, fs * 0.85f,
				Theme.TEXT_MUTED, 0.04f);
		}
	}


	private String computeRight(MinecraftClient mc) {
		switch (info.get()) {
			case "FPS":    return mc.getCurrentFps() + " fps";
			case "Time":   return LocalTime.now().format(HHMM);
			case "Server":
				if (mc.getCurrentServerEntry() != null) {
					return mc.getCurrentServerEntry().address;
				}
				return "singleplayer";
			case "Off":
			default:       return "";
		}
	}
}
