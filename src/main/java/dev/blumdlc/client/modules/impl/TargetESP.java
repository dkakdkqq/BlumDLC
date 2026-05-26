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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * TargetESP — visualises the entity {@link AttackAura} is currently locked
 * onto. Three visual modes are exposed:
 *
 * <ul>
 *   <li><b>Reticle</b> — original behaviour. A rotating
 *       {@code assets/blumdlc/textures/target.png} reticle anchored at the
 *       target's body center.</li>
 *   <li><b>Cube</b> — a 3D wireframe cube traced around the target's
 *       bounding box. The 8 corners are rotated around the entity's
 *       vertical axis over time so the cube slowly spins, then projected
 *       to screen and connected with thin gradient edges. Top edges use
 *       the palette's primary stop, bottom edges the secondary stop, and
 *       the four vertical edges blend between them.</li>
 *   <li><b>Label</b> — uses Cube's projection logic (lerped hitbox, project
 *       all 8 corners) to compute the screen-space AABB of the target,
 *       then stretches {@code assets/blumdlc/textures/target2.png} to
 *       fit inside that box. The texture sits "inside" the player as a
 *       billboarded label that scales correctly at every distance.</li>
 * </ul>
 *
 * <p>All modes go through the project's existing {@link Builder} API — no
 * vanilla draw helpers are touched. Rendering happens in the HUD pass
 * (post-world), so the visual is always drawn on top regardless of
 * occluding geometry; that's the same trade-off as {@link ESP}.
 */
public final class TargetESP extends Module {

	private static final Identifier TEXTURE_RETICLE = Identifier.of("blumdlc", "textures/target.png");
	private static final Identifier TEXTURE_LABEL   = Identifier.of("blumdlc", "textures/target2.png");

	private final AttackAura attackAura;

	public final ModeSetting   mode;
	public final NumberSetting size;
	public final NumberSetting speed;
	public final NumberSetting brightness;
	public final ModeSetting   color;
	/** Cube edge thickness (px). Hidden in non-Cube modes. */
	public final NumberSetting thickness;

	public TargetESP(AttackAura attackAura) {
		super("TargetESP", "Marks the entity AttackAura is locked onto", Category.RENDER);
		this.attackAura = attackAura;

		this.mode       = new ModeSetting("Mode", "Reticle", "Reticle", "Cube", "Label");
		this.size       = new NumberSetting("Size",        45.0,  10.0, 140.0, 1.0);
		this.speed      = new NumberSetting("Speed",        3.0,   0.5,   9.0, 0.1);
		this.brightness = new NumberSetting("Brightness", 220.0,  20.0, 255.0, 1.0);
		this.color      = new ModeSetting("Color", "Magenta",
			"Magenta", "Cyan", "Crimson", "Lime", "Gold", "Rainbow");
		this.thickness  = new NumberSetting("Thickness",   1.4,  0.5,   4.0, 0.1);

		this.thickness.visibleWhen(() -> mode.is("Cube"));

		addSetting(this.mode);
		addSetting(this.size);
		addSetting(this.speed);
		addSetting(this.brightness);
		addSetting(this.color);
		addSetting(this.thickness);
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
			case "Cube"  -> renderCube(matrix, target, tickDelta);
			case "Label" -> renderLabel(matrix, target, tickDelta);
			default      -> renderReticle(matrix, target, tickDelta); // "Reticle" + safety net
		}
	}

	// =========================================================================
	// Reticle (legacy mode — unchanged behaviour)
	// =========================================================================

	private void renderReticle(Matrix4f matrix, LivingEntity target, float tickDelta) {
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
		AbstractTexture tex = MinecraftClient.getInstance().getTextureManager().getTexture(TEXTURE_RETICLE);
		if (tex == null) {
			return;
		}

		// 3. Compose a rotated matrix around the projected anchor point.
		float boxSize = size.getFloat();
		float angle = currentAngle();

		// Clamp the anchor to the visible scaled-screen rectangle so the
		// reticle stays fully on screen even when the target drifts to the
		// edge of (or past) the viewport. We inset by half the texture
		// size on each axis so the quad — which is centred on (px, py) —
		// never has a half hanging off the side. Without this clamp the
		// projected (x, y) can be far outside [0, scaledW] when the
		// target is in front of the camera but outside the viewport,
		// which let the reticle "fly off" / disappear at edges.
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

	// =========================================================================
	// Cube (3D wireframe around hitbox, slowly spinning around vertical axis)
	// =========================================================================

	private void renderCube(Matrix4f matrix, LivingEntity target, float tickDelta) {
		// Use a lerped bounding box for smooth motion between ticks.
		Box box = lerpedHitbox(target, tickDelta);

		double cx = (box.minX + box.maxX) * 0.5;
		double cz = (box.minZ + box.maxZ) * 0.5;

		// Spin the four XZ corners around the entity's vertical axis over
		// time. We rotate the local-space corner offsets by the current
		// angle, then translate them back to world space so projection
		// remains consistent with the rest of the world.
		float angle = currentAngle();
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);

		// Local corner offsets (XZ) ordered: 0=(-,-) 1=(+,-) 2=(-,+) 3=(+,+).
		double hx = (box.maxX - box.minX) * 0.5;
		double hz = (box.maxZ - box.minZ) * 0.5;
		double[][] localXZ = {
			{ -hx, -hz },
			{  hx, -hz },
			{ -hx,  hz },
			{  hx,  hz },
		};
		double[] xs = new double[8];
		double[] ys = new double[8];
		double[] zs = new double[8];
		for (int i = 0; i < 4; i++) {
			double lx = localXZ[i][0];
			double lz = localXZ[i][1];
			double rx = lx * cos - lz * sin;
			double rz = lx * sin + lz * cos;
			// Bottom ring (i = 0..3) at minY, top ring (i = 4..7) at maxY.
			xs[i]     = cx + rx;
			ys[i]     = box.minY;
			zs[i]     = cz + rz;
			xs[i + 4] = cx + rx;
			ys[i + 4] = box.maxY;
			zs[i + 4] = cz + rz;
		}

		// Project all 8 corners. If any is behind the camera we bail —
		// drawing edges with one endpoint behind the near plane projects
		// them to the wrong half of the screen.
		Projection.Result[] projected = new Projection.Result[8];
		for (int i = 0; i < 8; i++) {
			projected[i] = Projection.project(xs[i], ys[i], zs[i]);
			if (!projected[i].onScreen()) {
				return;
			}
		}

		int alpha = clampAlpha((int) brightness.get().doubleValue());
		// Time-shifted phase so Rainbow/breathing palettes animate.
		int[] palette = paletteColorsFor(color.get(), angle * 0.25f, alpha);
		int colorBot = palette[0];
		int colorTop = palette[1];
		float thick = thickness.getFloat();

		// Edge topology. Bottom ring and top ring each form a quad (4 edges).
		// Vertical edges connect i (bottom) to i+4 (top).
		int[][] bottomEdges   = { {0,1}, {1,3}, {3,2}, {2,0} };
		int[][] topEdges      = { {4,5}, {5,7}, {7,6}, {6,4} };
		int[][] verticalEdges = { {0,4}, {1,5}, {2,6}, {3,7} };

		for (int[] e : bottomEdges) {
			drawLine(matrix,
				projected[e[0]].x(), projected[e[0]].y(),
				projected[e[1]].x(), projected[e[1]].y(),
				thick, colorBot, colorBot);
		}
		for (int[] e : topEdges) {
			drawLine(matrix,
				projected[e[0]].x(), projected[e[0]].y(),
				projected[e[1]].x(), projected[e[1]].y(),
				thick, colorTop, colorTop);
		}
		for (int[] e : verticalEdges) {
			// e[0] = bottom corner, e[1] = top corner. Gradient bottom→top.
			drawLine(matrix,
				projected[e[0]].x(), projected[e[0]].y(),
				projected[e[1]].x(), projected[e[1]].y(),
				thick, colorBot, colorTop);
		}
	}

	// =========================================================================
	// Label (target2.png stretched to fit the entity's screen-space AABB)
	// =========================================================================

	/**
	 * Project the same 8 hitbox corners that {@link #renderCube} uses,
	 * collapse them to a screen-space AABB, then stretch the label
	 * texture to cover that box. The result is a billboarded sprite that
	 * sits inside the player's silhouette and follows them through space
	 * by exactly the same projection pipeline as Cube.
	 *
	 * <p>No corner-rotation around the vertical axis here on purpose:
	 * the label is supposed to <em>stick</em> to the target like a logo,
	 * not pulse with the cube's spin. The {@link #size} slider provides a
	 * uniform scale around the AABB centre (50 = exact fit).
	 */
	private void renderLabel(Matrix4f matrix, LivingEntity target, float tickDelta) {
		Box box = lerpedHitbox(target, tickDelta);

		double[][] corners = {
			{ box.minX, box.minY, box.minZ }, { box.maxX, box.minY, box.minZ },
			{ box.minX, box.maxY, box.minZ }, { box.maxX, box.maxY, box.minZ },
			{ box.minX, box.minY, box.maxZ }, { box.maxX, box.minY, box.maxZ },
			{ box.minX, box.maxY, box.maxZ }, { box.maxX, box.maxY, box.maxZ },
		};

		float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
		for (double[] c : corners) {
			Projection.Result r = Projection.project(c[0], c[1], c[2]);
			if (!r.onScreen()) {
				return;
			}
			if (r.x() < minX) minX = r.x();
			if (r.y() < minY) minY = r.y();
			if (r.x() > maxX) maxX = r.x();
			if (r.y() > maxY) maxY = r.y();
		}

		float boxW = maxX - minX;
		float boxH = maxY - minY;
		if (boxW < 2.0f || boxH < 2.0f) {
			return;
		}

		AbstractTexture tex = MinecraftClient.getInstance().getTextureManager().getTexture(TEXTURE_LABEL);
		if (tex == null) {
			return;
		}

		// Uniform scale around the AABB centre. Default 45 → 0.9× (sits
		// just inside the player), 50 = exact fit, 140 = ~2.8× for a
		// poster-sized overlay.
		float scale = size.getFloat() / 50.0f;
		float cx = (minX + maxX) * 0.5f;
		float cy = (minY + maxY) * 0.5f;
		float w = boxW * scale;
		float h = boxH * scale;

		// Same clamping idea as renderReticle: keep the label centre at
		// least half-width inside the visible scaled-screen rectangle so
		// it never partially hangs off the side. When the target drifts
		// just past the viewport edge the projected AABB centre walks
		// off the screen — without clamping the label would slide off
		// with it. With clamping it stays anchored to the nearest edge.
		// Falls back to mid-screen if the label is bigger than the
		// viewport on an axis (clamp() handles inverted bounds).
		Window window = MinecraftClient.getInstance().getWindow();
		float sw = window.getScaledWidth();
		float sh = window.getScaledHeight();
		cx = clamp(cx, w * 0.5f, sw - w * 0.5f);
		cy = clamp(cy, h * 0.5f, sh - h * 0.5f);

		int alpha = clampAlpha((int) brightness.get().doubleValue());
		QuadColorState quadColors = paletteFor(color.get(), currentAngle(), alpha);

		Builder.texture()
			.size(new SizeState(w, h))
			.radius(QuadRadiusState.NO_ROUND)
			.color(quadColors)
			.smoothness(1.0f)
			.texture(0.0f, 0.0f, 1.0f, 1.0f, tex)
			.build()
			.render(matrix, cx - w * 0.5f, cy - h * 0.5f);
	}

	// =========================================================================
	// Drawing helpers
	// =========================================================================

	/**
	 * Return {@code target}'s bounding box offset into its lerp-interpolated
	 * position, so frames between ticks render the box at the visually
	 * correct spot rather than the last full-tick snapshot.
	 */
	private static Box lerpedHitbox(LivingEntity target, float tickDelta) {
		Vec3d lerped = target.getLerpedPos(tickDelta);
		double dx = lerped.x - target.getX();
		double dy = lerped.y - target.getY();
		double dz = lerped.z - target.getZ();
		return target.getBoundingBox().offset(dx, dy, dz);
	}

	/**
	 * Draw a thin line from {@code (x1,y1)} to {@code (x2,y2)} as a rotated
	 * rectangle. The rectangle's long axis is centred on the segment's
	 * midpoint and rotated by atan2 of the delta — same trick the reticle
	 * uses for its rotation, just scoped per-edge.
	 *
	 * @param colorStart colour of the vertices at {@code (x1,y1)}
	 * @param colorEnd   colour of the vertices at {@code (x2,y2)}
	 */
	private static void drawLine(Matrix4f matrix,
			float x1, float y1, float x2, float y2,
			float thickness, int colorStart, int colorEnd) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		float length = (float) Math.sqrt(dx * dx + dy * dy);
		if (length < 0.5f) {
			return;
		}
		float angle = (float) Math.atan2(dy, dx);
		float midX = (x1 + x2) * 0.5f;
		float midY = (y1 + y2) * 0.5f;

		Matrix4f rotated = new Matrix4f(matrix)
			.translate(midX, midY, 0.0f)
			.rotateZ(angle)
			.translate(-midX, -midY, 0.0f);

		// QuadColorState corners: (TL, BL, BR, TR). For a thin quad whose
		// long axis runs from start to end after rotation, both "left"
		// vertices are the start and both "right" vertices are the end —
		// so the gradient goes start → end along the line.
		QuadColorState colors = new QuadColorState(colorStart, colorStart, colorEnd, colorEnd);

		Builder.rectangle()
			.size(new SizeState(length, thickness))
			.radius(QuadRadiusState.NO_ROUND)
			.color(colors)
			.smoothness(1.0f)
			.build()
			.render(rotated, midX - length * 0.5f, midY - thickness * 0.5f);
	}

	// =========================================================================
	// Time / palette helpers
	// =========================================================================

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

	/**
	 * Two-colour palette pull used by the Cube mode (top/bottom stops). For
	 * Rainbow we sample two opposite hues from the current phase so the
	 * cube reads as a vertical gradient that animates over time.
	 */
	private static int[] paletteColorsFor(String mode, float phase, int alpha) {
		switch (mode) {
			case "Cyan":     return new int[] { withAlpha(0xFF60A5FA, alpha), withAlpha(0xFF22D3EE, alpha) };
			case "Crimson":  return new int[] { withAlpha(0xFFB91C1C, alpha), withAlpha(0xFFEF4444, alpha) };
			case "Lime":     return new int[] { withAlpha(0xFF22C55E, alpha), withAlpha(0xFFA3E635, alpha) };
			case "Gold":     return new int[] { withAlpha(0xFFF59E0B, alpha), withAlpha(0xFFFCD34D, alpha) };
			case "Rainbow":  {
				float h = ((phase % 1.0f) + 1.0f) % 1.0f;
				int c1 = withAlpha(hsv(h, 0.85f, 1.0f), alpha);
				int c2 = withAlpha(hsv((h + 0.5f) % 1.0f, 0.85f, 1.0f), alpha);
				return new int[] { c1, c2 };
			}
			case "Magenta":
			default:         return new int[] { withAlpha(0xFFC44CD8, alpha), withAlpha(0xFFEF6CFB, alpha) };
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
