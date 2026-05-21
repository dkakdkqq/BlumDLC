package dev.blumdlc.client.modules.impl;

import org.joml.Matrix4f;

import dev.blumdlc.client.modules.HudModule;
import dev.blumdlc.client.msdf.MsdfFont;
import dev.blumdlc.client.settings.ModeSetting;
import dev.blumdlc.client.settings.NumberSetting;
import dev.blumdlc.client.ui.ClientTheme;
import dev.blumdlc.client.ui.Fonts;
import dev.blumdlc.client.ui.Theme;
import dev.blumdlc.client.ui.UIRender;
import dev.blumdlc.client.ui.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Coords HUD — compact bottom-left display of player position. Detailed
 * mode also shows facing cardinal direction.
 */
public final class CoordsHud extends HudModule {

	public final ModeSetting   style;
	public final NumberSetting fontSize;

	private float lastW = 100.0f;
	private float lastH = 14.0f;

	public CoordsHud() {
		super("Coords", "Player coordinate display");
		style    = new ModeSetting("Style", "Compact", "Compact", "Detailed");
		fontSize = new NumberSetting("Font Size", 8.0, 6.0, 12.0, 0.5);
		addSetting(style);
		addSetting(fontSize);
	}

	@Override public float hudWidth()  { return lastW; }
	@Override public float hudHeight() { return lastH; }

	@Override
	protected void computeDefaultPosition(int sw, int sh) {
		this.x = 6.0f;
		this.y = sh - lastH - 6.0f;
	}


	@Override
	protected void renderHud(Matrix4f m, float tickDelta, boolean editing) {
		MsdfFont font = Fonts.BIKO.get();
		ClientPlayerEntity p = MinecraftClient.getInstance().player;

		String text;
		if (p == null) {
			text = "X: ?  Y: ?  Z: ?";
		} else {
			int x = (int) Math.floor(p.getX());
			int y = (int) Math.floor(p.getY());
			int z = (int) Math.floor(p.getZ());
			text = style.is("Detailed")
				? String.format("%d, %d, %d  (%s)", x, y, z, dirName(p.getYaw()))
				: String.format("%d, %d, %d", x, y, z);
		}

		float fs = fontSize.getFloat();
		float padX = 6.0f, padY = 3.0f;
		float tw = UIRender.textWidth(font, text, fs);
		lastW = tw + padX * 2 + 6.0f;
		lastH = fs + padY * 2;

		UIRender.rect(m, this.x, this.y, lastW, lastH, 4.0f, 0xC0141720);
		UIRender.border(m, this.x, this.y, lastW, lastH, 4.0f, 0.7f, Theme.PANEL_BORDER);
		// Accent stripe on the left
		UIRender.rect(m, this.x, this.y, 2.0f, lastH, 1.0f,
			ColorUtil.multiplyAlpha(ClientTheme.accent(), 0.9f));

		UIRender.text(m, font, text, this.x + padX + 3.0f, this.y + padY, fs,
			Theme.TEXT_PRIMARY, 0.05f);
	}

	private static String dirName(float yaw) {
		yaw = ((yaw % 360.0f) + 360.0f) % 360.0f;
		if (yaw < 22.5f || yaw >= 337.5f) return "S";
		if (yaw < 67.5f)  return "SW";
		if (yaw < 112.5f) return "W";
		if (yaw < 157.5f) return "NW";
		if (yaw < 202.5f) return "N";
		if (yaw < 247.5f) return "NE";
		if (yaw < 292.5f) return "E";
		return "SE";
	}
}
