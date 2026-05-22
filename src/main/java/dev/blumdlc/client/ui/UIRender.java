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
 */
public final class UIRender {

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
	 * Draws a texture identified by {@code id}, tinted with {@code color}
	 * (use {@code 0xFFFFFFFF} for an untinted draw). The texture manager
	 * auto-registers a {@code ResourceTexture} on first call; if the asset
	 * is missing this becomes a no-op.
	 */
	public static void texture(Matrix4f m, Identifier id, float x, float y, float w, float h, int color) {
		AbstractTexture tex = MinecraftClient.getInstance().getTextureManager().getTexture(id);
		if (tex == null) {
			return;
		}
		Builder.texture()
			.size(new SizeState(w, h))
			.radius(QuadRadiusState.NO_ROUND)
			.color(new QuadColorState(color))
			.smoothness(1.0f)
			.texture(0.0f, 0.0f, 1.0f, 1.0f, tex)
			.build()
			.render(m, x, y);
	}

	private UIRender() {
	}

}
