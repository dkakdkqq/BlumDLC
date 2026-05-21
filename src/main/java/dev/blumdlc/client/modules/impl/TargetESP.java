package dev.blumdlc.client.modules.impl;

import org.joml.Matrix4f;

import dev.blumdlc.client.builders.Builder;
import dev.blumdlc.client.builders.states.QuadColorState;
import dev.blumdlc.client.builders.states.QuadRadiusState;
import dev.blumdlc.client.builders.states.SizeState;
import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.ModeSetting;
import dev.blumdlc.client.settings.NumberSetting;
import dev.blumdlc.client.util.Projection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * TargetESP — draws a rotating reticle on the entity that
 * {@link AttackAura} is currently focused on.
 *
 * <p>Two skins selectable via {@link #style}:
 * <ul>
 *   <li>{@code Cube}  — the original chunky target ({@code textures/target.png}),
 *       anchored at the body center;</li>
 *   <li>{@code Point} — a slimmer marker ({@code textures/target2.png}),
 *       drawn smaller and placed above the head so it never blocks combat
 *       readouts.</li>
 * </ul>
 *
 * <p>Both skins use the same render pipeline through this project's
 * {@link Builder} API ({@code Builder.texture()} -> {@code BuiltTexture}); no
 * vanilla draw helpers are used.
 */
public final class TargetESP extends Module {

	/** Original "Cube" skin: thick textured target ring. */
	private static final Identifier TEXTURE_CUBE  = Identifier.of("blumdlc", "textures/target.png");
	/** "Point" skin: alternate marker added by the user. */
	private static final Identifier TEXTURE_POINT = Identifier.of("blumdlc", "textures/target2.png");

	private final AttackAura attackAura;

	public final ModeSetting   style;
	public final NumberSetting size;
	public final NumberSetting speed;
	public final NumberSetting brightness;
	public final ModeSetting   color;

	public TargetESP(AttackAura attackAura) {
		super("TargetESP", "Marks the entity AttackAura is locked onto", Category.RENDER);
		this.attackAura = attackAura;

		this.style      = new ModeSetting("Style", "Cube", "Cube", "Point");
		this.size       = new NumberSetting("Size",        45.0,  10.0, 140.0, 1.0);
		this.speed      = new NumberSetting("Speed",        3.0,   0.5,   9.0, 0.1);
		this.brightness = new NumberSetting("Brightness", 220.0,  20.0, 255.0, 1.0);
		this.color      = new ModeSetting("Color", "Magenta",
			"Magenta", "Cyan", "Crimson", "Lime", "Gold", "Rainbow");

		addSetting(this.style);
		addSetting(this.size);
		addSetting(this.speed);
		addSetting(this.brightness);
		addSetting(this.color);
	}

	@Override
	public void onRender(Matrix4f matrix, float tickDelta) {
		if (!attackAura.enabled) {
			return;
		}
		LivingEntity target = attackAura.getTarget();
		if (target == null || !target.isAlive()) {
			return;
		}

		// 1. Project the target's body center to scaled-screen coords.
		//    Both skins use the same anchor (body center) and full size.
		Vec3d pos = target.getLerpedPos(tickDelta);
		double anchorY = pos.y + target.getHeight() * 0.5;
		Projection.Result projected = Projection.project(pos.x, anchorY, pos.z);
		if (!projected.onScreen()) {
			return;
		}

		// 2. Resolve the texture id based on selected style.
		Identifier id = style.is("Point") ? TEXTURE_POINT : TEXTURE_CUBE;
		AbstractTexture tex = MinecraftClient.getInstance().getTextureManager().getTexture(id);
		if (tex == null) {
			return;
		}

		// 3. Compose a rotated matrix around the projected anchor point.
		float px = projected.x();
		float py = projected.y();
		float boxSize = size.getFloat();
		float angle = currentAngle();

		Matrix4f rotated = new Matrix4f(matrix)
			.translate(px, py, 0.0f)
			.rotateZ(angle)
			.translate(-px, -py, 0.0f);

		// 4. Draw the quad through the existing texture renderer.
		int alpha = clampAlpha((int) brightness.get().doubleValue());
		QuadColorState corners = paletteFor(color.get(), angle, alpha);

		Builder.texture()
			.size(new SizeState(boxSize, boxSize))
			.radius(QuadRadiusState.NO_ROUND)
			.color(corners)
			.smoothness(1.0f)
			.texture(0.0f, 0.0f, 1.0f, 1.0f, tex)
			.build()
			.render(rotated, px - boxSize * 0.5f, py - boxSize * 0.5f);
	}

	// --- helpers ------------------------------------------------------------

	/**
	 * Convert the configured speed (0.5..9) into a smooth radians-per-second
	 * rotation rate, then return the current rotation angle in radians.
	 */
	private float currentAngle() {
		double s = speed.get();              // 0.5 .. 9
		double seconds = System.currentTimeMillis() / 1000.0;
		return (float) (seconds * s);
	}

	private static int clampAlpha(int a) {
		if (a < 0) return 0;
		if (a > 255) return 255;
		return a;
	}

	/**
	 * Build the 4-corner gradient used to tint the texture. We slowly shift
	 * the gradient with the current rotation so the reticle has subtle
	 * "energy flow" instead of a static fill.
	 *
	 * <p>Corner order in {@link QuadColorState} (TL, BL, BR, TR) matches the
	 * vertex order in {@code BuiltTexture#render}.
	 */
	private static QuadColorState paletteFor(String mode, float angle, int alpha) {
		float phase = angle * 0.25f; // slower than rotation -> readable colors

		switch (mode) {
			case "Cyan":     return gradient(0xFF22D3EE, 0xFF60A5FA, alpha, phase);
			case "Crimson":  return gradient(0xFFEF4444, 0xFFB91C1C, alpha, phase);
			case "Lime":     return gradient(0xFFA3E635, 0xFF22C55E, alpha, phase);
			case "Gold":     return gradient(0xFFFCD34D, 0xFFF59E0B, alpha, phase);
			case "Rainbow":  return rainbow(alpha, phase);
			case "Magenta":
			default:         return gradient(0xFFEF6CFB, 0xFFC44CD8, alpha, phase);
		}
	}

	/** Two-stop diagonal gradient with the supplied alpha. */
	private static QuadColorState gradient(int a, int b, int alpha, float phase) {
		// Diagonal gradient: TL/BR = a tinted, BL/TR = b tinted.
		// `phase` mixes a<->b on the off-diagonal so the gradient breathes.
		float t = (float) (0.5 + 0.5 * Math.sin(phase));
		int diag1 = withAlpha(lerp(a, b, t * 0.35f), alpha);
		int diag2 = withAlpha(lerp(b, a, t * 0.35f), alpha);
		return new QuadColorState(diag1, diag2, diag1, diag2);
	}

	private static QuadColorState rainbow(int alpha, float phase) {
		int c1 = withAlpha(hsv((phase            ) % 1.0f, 0.85f, 1.0f), alpha);
		int c2 = withAlpha(hsv((phase + 0.25f    ) % 1.0f, 0.85f, 1.0f), alpha);
		int c3 = withAlpha(hsv((phase + 0.50f    ) % 1.0f, 0.85f, 1.0f), alpha);
		int c4 = withAlpha(hsv((phase + 0.75f    ) % 1.0f, 0.85f, 1.0f), alpha);
		return new QuadColorState(c1, c2, c3, c4);
	}

	// --- color math ---------------------------------------------------------

	private static int withAlpha(int argb, int alpha) {
		return ((alpha & 0xFF) << 24) | (argb & 0x00FFFFFF);
	}

	private static int lerp(int a, int b, float t) {
		t = t < 0.0f ? 0.0f : (t > 1.0f ? 1.0f : t);
		int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		int rr = Math.round(ar + (br - ar) * t);
		int rg = Math.round(ag + (bg - ag) * t);
		int rb = Math.round(ab + (bb - ab) * t);
		return 0xFF000000 | (rr << 16) | (rg << 8) | rb;
	}

	/** Produce a fully-saturated ARGB color from HSV (h,s,v in [0,1]). */
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
		int ri = Math.max(0, Math.min(255, Math.round(r * 255.0f)));
		int gi = Math.max(0, Math.min(255, Math.round(g * 255.0f)));
		int bi = Math.max(0, Math.min(255, Math.round(b * 255.0f)));
		return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
	}
}
