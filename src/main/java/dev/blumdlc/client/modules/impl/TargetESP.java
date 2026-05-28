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
import net.minecraft.client.util.Window;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * TargetESP — visualises the entity {@link AttackAura} is currently locked
 * onto. Two visual modes are exposed:
 *
 * <ul>
 *   <li><b>Cube</b> — a rotating
 *       {@code assets/blumdlc/textures/target.png} sprite anchored at the
 *       target's body centre. Replaces the old reticle name; pure 2D, no
 *       wireframe.</li>
 *   <li><b>Label</b> — an orbital "ghost trail" effect adapted from the
 *       reference TargetESP design. Many fading copies of
 *       {@code assets/blumdlc/textures/target2.png} sweep around the
 *       target on two interleaved rings — one anchored just above the
 *       body centre, one just above the feet — each rotating in opposite
 *       phase so the trails braid together.</li>
 * </ul>
 *
 * <p>Both modes go through the project's existing {@link Builder} API and
 * clamp their on-screen anchor to the visible scaled-screen rectangle so
 * the visual never slides past the viewport edge.
 */
public final class TargetESP extends Module {

	private static final Identifier TEXTURE_CUBE  = Identifier.of("blumdlc", "textures/target.png");
	private static final Identifier TEXTURE_LABEL = Identifier.of("blumdlc", "textures/target2.png");
	private static final Identifier TEXTURE_SOULS = Identifier.of("blumdlc", "textures/target3.png");

	/** Number of trail copies stacked behind the lead sprite in Label mode. */
	private static final int   LABEL_TRAIL_COUNT  = 40;
	/** XZ-plane orbit radius (in blocks) for the Label trail rings. */
	private static final double LABEL_ORBIT_RADIUS = 0.5;
	/** Vertical bob amplitude (in blocks) so the trails rise/fall instead of sliding flat. */
	private static final double LABEL_BOB_AMPLITUDE = 0.26;
	/** Per-step time delay between consecutive trail copies. */
	private static final double LABEL_TRAIL_STEP    = 0.1;
	/** Per-step alpha fade between consecutive trail copies. */
	private static final int    LABEL_ALPHA_STEP    = 5;
	/** Per-step size shrink factor between consecutive trail copies. */
	private static final float  LABEL_SIZE_STEP     = 0.02f;

	private final AttackAura attackAura;

	public final ModeSetting   mode;
	public final NumberSetting size;
	public final NumberSetting speed;
	public final NumberSetting brightness;
	public final ModeSetting   color;

	public TargetESP(AttackAura attackAura) {
		super("TargetESP", "Marks the entity AttackAura is locked onto", Category.RENDER);
		this.attackAura = attackAura;

		this.mode       = new ModeSetting("Mode", "Cube", "Cube", "Label", "Souls");
		this.size       = new NumberSetting("Size",        45.0,  10.0, 140.0, 1.0);
		this.speed      = new NumberSetting("Speed",        3.0,   0.5,   9.0, 0.1);
		this.brightness = new NumberSetting("Brightness", 220.0,  20.0, 255.0, 1.0);
		this.color      = new ModeSetting("Color", "Magenta",
			"Magenta", "Cyan", "Crimson", "Lime", "Gold", "Violet", "Aqua", "Sunset", "Ice", "Rainbow");

		addSetting(this.mode);
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

		switch (mode.get()) {
			case "Label" -> renderLabel(matrix, target, tickDelta);
			case "Souls" -> renderSouls(matrix, target, tickDelta);
			default      -> renderCube(matrix, target, tickDelta); // "Cube" + safety net
		}
	}

	// =========================================================================
	// Cube — rotating target.png sprite at the target's body centre
	// =========================================================================

	private void renderCube(Matrix4f matrix, LivingEntity target, float tickDelta) {
		// 1. Project the target's body center to scaled-screen coords.
		Vec3d pos = target.getLerpedPos(tickDelta);
		double anchorY = pos.y + target.getHeight() * 0.5;
		Projection.Result projected = Projection.project(pos.x, anchorY, pos.z);
		if (!projected.onScreen()) {
			return;
		}

		// 2. Resolve the texture id (auto-registers as a ResourceTexture
		//    on first call). If the asset is missing the manager hands back
		//    Minecraft's default missing-texture, which is harmless.
		AbstractTexture tex = MinecraftClient.getInstance().getTextureManager().getTexture(TEXTURE_CUBE);
		if (tex == null) {
			return;
		}

		float boxSize = size.getFloat();
		float angle = currentAngle();

		// Clamp the anchor to the visible scaled-screen rectangle so the
		// sprite stays fully on screen even when the target drifts to the
		// edge of (or past) the viewport.
		Window window = MinecraftClient.getInstance().getWindow();
		float sw = window.getScaledWidth();
		float sh = window.getScaledHeight();
		float half = boxSize * 0.5f;
		float px = clamp(projected.x(), half, sw - half);
		float py = clamp(projected.y(), half, sh - half);

		Matrix4f rotated = new Matrix4f(matrix)
			.translate(px, py, 0.0f)
			.rotateZ(angle)
			.translate(-px, -py, 0.0f);

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

	// =========================================================================
	// Label — orbital ghost trails of target2.png around the target
	// =========================================================================

	/**
	 * Adapted from the reference "Призраки" / "Ghosts" pattern: two trail
	 * rings of {@link #LABEL_TRAIL_COUNT} copies orbit on the XZ plane —
	 * one anchored just above the body centre, one just above the feet —
	 * with a vertical sine bob so the trails rise and fall instead of
	 * sliding flat. The rings rotate in opposite phase (offsets are
	 * <code>+cos/+sin</code> vs <code>-cos/-sin</code>) which gives the
	 * braided / "DNA helix" look you can see on cheats that use this
	 * style. Each successive copy is slightly behind in time, slightly
	 * smaller, and slightly more transparent.
	 */
	private void renderLabel(Matrix4f matrix, LivingEntity target, float tickDelta) {
		AbstractTexture tex = MinecraftClient.getInstance().getTextureManager().getTexture(TEXTURE_LABEL);
		if (tex == null) {
			return;
		}

		// Two orbit anchors: just above body centre and just above feet,
		// matching the reference layout. Both use the lerped position so
		// they stay glued to the target between ticks.
		Vec3d lerped = target.getLerpedPos(tickDelta);
		double upperX = lerped.x;
		double upperY = lerped.y + target.getHeight() * 0.5 + 0.5;
		double upperZ = lerped.z;
		double lowerX = lerped.x;
		double lowerY = lerped.y + 0.5;
		double lowerZ = lerped.z;

		// Time accumulator. Speed setting maps 0.5..9 onto a comfortable
		// orbit rate — the /500 divisor mirrors the reference.
		double speedF = speed.get();
		double time = System.currentTimeMillis() / (500.0 / speedF);

		float baseSize = size.getFloat();
		int   baseAlpha = clampAlpha((int) brightness.get().doubleValue());
		String palette = color.get();

		Window window = MinecraftClient.getInstance().getWindow();
		float sw = window.getScaledWidth();
		float sh = window.getScaledHeight();

		for (int j = 0; j < LABEL_TRAIL_COUNT; j++) {
			int trailAlpha = baseAlpha - j * LABEL_ALPHA_STEP;
			if (trailAlpha <= 0) {
				break; // remaining copies are fully transparent
			}
			float trailSize = baseSize * (1.0f - j * LABEL_SIZE_STEP);
			if (trailSize < 1.0f) {
				break; // tiny enough to skip
			}

			double trailTime = time - j * LABEL_TRAIL_STEP;
			double trailSin  = Math.sin(trailTime);
			double trailCos  = Math.cos(trailTime);
			double bobY      = trailSin * LABEL_BOB_AMPLITUDE;
			float  angleOffsetDeg = j * 7.2f;
			float  half = trailSize * 0.5f;

			// Upper ring: orbit centre = upper anchor, offset = +(cos, sin).
			drawTrailSprite(matrix, tex, palette, trailAlpha, trailSize, half, sw, sh,
				upperX + trailCos * LABEL_ORBIT_RADIUS,
				upperY + bobY,
				upperZ + trailSin * LABEL_ORBIT_RADIUS,
				(float) Math.toRadians(trailSin * 360.0 + angleOffsetDeg));

			// Lower ring: orbit centre = lower anchor, offset = -(cos, sin),
			// rotation flipped in sign and shifted by 180° so the two rings
			// counter-rotate and braid together.
			drawTrailSprite(matrix, tex, palette, trailAlpha, trailSize, half, sw, sh,
				lowerX - trailCos * LABEL_ORBIT_RADIUS,
				lowerY + bobY,
				lowerZ - trailSin * LABEL_ORBIT_RADIUS,
				(float) Math.toRadians(-trailSin * 360.0 + 180.0 + angleOffsetDeg));
		}
	}

	// =========================================================================
	// Souls — rotating target3.png sprite at the target's body centre
	// =========================================================================

	/**
	 * Same rotating-texture-on-body-centre approach as Cube, but uses
	 * {@code target3.png}. Gives a distinct "soul" visual alternative.
	 */
	private void renderSouls(Matrix4f matrix, LivingEntity target, float tickDelta) {
		Vec3d pos = target.getLerpedPos(tickDelta);
		double anchorY = pos.y + target.getHeight() * 0.5;
		Projection.Result projected = Projection.project(pos.x, anchorY, pos.z);
		if (!projected.onScreen()) {
			return;
		}

		AbstractTexture tex = MinecraftClient.getInstance().getTextureManager().getTexture(TEXTURE_SOULS);
		if (tex == null) {
			return;
		}

		float boxSize = size.getFloat();
		float angle = currentAngle();

		Window window = MinecraftClient.getInstance().getWindow();
		float sw = window.getScaledWidth();
		float sh = window.getScaledHeight();
		float half = boxSize * 0.5f;
		float px = clamp(projected.x(), half, sw - half);
		float py = clamp(projected.y(), half, sh - half);

		Matrix4f rotated = new Matrix4f(matrix)
			.translate(px, py, 0.0f)
			.rotateZ(angle)
			.translate(-px, -py, 0.0f);

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

	// =========================================================================
	// Trail drawing helper (used by Label)
	// =========================================================================

	/**
	 * Project a single trail world point, clamp the resulting screen
	 * anchor inside the visible rectangle (so the sprite never drifts
	 * past the viewport edge), then draw the rotated quad. Skips the
	 * draw silently if the point is behind the camera.
	 */
	private static void drawTrailSprite(Matrix4f matrix, AbstractTexture tex, String palette,
			int alpha, float spriteSize, float half, float sw, float sh,
			double worldX, double worldY, double worldZ, float rotationRad) {
		Projection.Result p = Projection.project(worldX, worldY, worldZ);
		if (!p.onScreen()) {
			return;
		}

		float gx = clamp(p.x(), half, sw - half);
		float gy = clamp(p.y(), half, sh - half);

		Matrix4f rotated = new Matrix4f(matrix)
			.translate(gx, gy, 0.0f)
			.rotateZ(rotationRad)
			.translate(-gx, -gy, 0.0f);

		QuadColorState corners = paletteFor(palette, rotationRad, alpha);

		Builder.texture()
			.size(new SizeState(spriteSize, spriteSize))
			.radius(QuadRadiusState.NO_ROUND)
			.color(corners)
			.smoothness(1.0f)
			.texture(0.0f, 0.0f, 1.0f, 1.0f, tex)
			.build()
			.render(rotated, gx - half, gy - half);
	}

	// =========================================================================
	// Time / palette helpers
	// =========================================================================

	/**
	 * Convert the configured speed (0.5..9) into a smooth radians-per-second
	 * rotation rate, then return the current rotation angle in radians. Used
	 * by {@link #renderCube} for its in-place spin.
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

	/** Clamp a float to [{@code lo}, {@code hi}], falling back to the midpoint when the bounds are inverted. */
	private static float clamp(float v, float lo, float hi) {
		if (lo > hi) {
			return (lo + hi) * 0.5f;
		}
		if (v < lo) return lo;
		if (v > hi) return hi;
		return v;
	}

	/**
	 * Build the 4-corner gradient used to tint the texture. We slowly shift
	 * the gradient with the current rotation so the sprite has subtle
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
			case "Violet":   return gradient(0xFF8B5CF6, 0xFF6D28D9, alpha, phase);
			case "Aqua":     return gradient(0xFF06B6D4, 0xFF0891B2, alpha, phase);
			case "Sunset":   return gradient(0xFFF97316, 0xFFDB2777, alpha, phase);
			case "Ice":      return gradient(0xFFBAE6FD, 0xFF7DD3FC, alpha, phase);
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
