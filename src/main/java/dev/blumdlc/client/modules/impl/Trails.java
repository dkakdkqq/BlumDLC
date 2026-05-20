package dev.blumdlc.client.modules.impl;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

import org.joml.Matrix4f;

import dev.blumdlc.client.builders.Builder;
import dev.blumdlc.client.builders.states.QuadColorState;
import dev.blumdlc.client.builders.states.QuadRadiusState;
import dev.blumdlc.client.builders.states.SizeState;
import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.BooleanSetting;
import dev.blumdlc.client.settings.ModeSetting;
import dev.blumdlc.client.settings.NumberSetting;
import dev.blumdlc.client.util.Projection;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Trails — leaves an animated ribbon behind the player while running, spanning
 * from the feet up to the back/shoulders.
 *
 * <p>Strategy:
 * <ol>
 *   <li>Every tick, while the player is moving, the world-space position
 *       (and the player's current bounding-box height) is captured into a
 *       bounded history deque.</li>
 *   <li>Each render frame the captured samples are projected to scaled
 *       screen-space via {@link Projection}: one anchor at the feet, another
 *       at the back. Consecutive samples are connected with thin
 *       {@link Builder#rectangle()} quads that are rotated to align with the
 *       path direction.</li>
 *   <li>Multiple parallel "strands" stacked between the feet and back anchors
 *       form a solid feet-to-back ribbon. Older samples fade out via an alpha
 *       and width taper.</li>
 * </ol>
 *
 * <p>All drawing is done exclusively through the project's own 2D renderers
 * ({@code BuiltRectangle} via the {@link Builder} facade). No vanilla draw
 * helpers or raw GL calls are used.
 */
public final class Trails extends Module {

	public final ModeSetting    style;
	public final ModeSetting    color;
	public final NumberSetting  width;
	public final NumberSetting  length;
	public final NumberSetting  fadeSeconds;
	public final NumberSetting  brightness;
	public final BooleanSetting onlyWhenSprinting;

	/** Captured world-space samples, oldest first. */
	private final Deque<Sample> samples = new ArrayDeque<>();
	/** Coalesces sampling so very high tick rates don't spam the deque. */
	private long lastSampleMs = 0L;

	public Trails() {
		super("Trails", "Thin line trail from feet to back while moving", Category.RENDER);

		this.style = new ModeSetting("Style", "Line", true,
			"Line", "Ribbon", "Wave", "Dual");
		this.color = new ModeSetting("Color", "Magenta",
			"Magenta", "Cyan", "Crimson", "Lime", "Gold", "Rainbow");

		this.width        = new NumberSetting("Width",       1.2,   0.3,   6.0, 0.1);
		this.length       = new NumberSetting("Length",     40.0,  10.0, 200.0, 1.0);
		this.fadeSeconds  = new NumberSetting("Fade",        2.0,   0.5,   6.0, 0.1);
		this.brightness   = new NumberSetting("Brightness", 220.0, 40.0, 255.0, 1.0);
		this.onlyWhenSprinting = new BooleanSetting("Only when sprinting", false);

		addSetting(this.style);
		addSetting(this.color);
		addSetting(this.width);
		addSetting(this.length);
		addSetting(this.fadeSeconds);
		addSetting(this.brightness);
		addSetting(this.onlyWhenSprinting);
	}

	@Override
	protected void onDisable() {
		samples.clear();
		lastSampleMs = 0L;
	}

	// --- sampling -----------------------------------------------------------

	@Override
	public void onTick() {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null) {
			return;
		}
		if (onlyWhenSprinting.get() && !player.isSprinting()) {
			// Stop *adding* new samples; existing ones are still allowed to
			// fade out naturally inside onRender.
			return;
		}

		// Skip when the player isn't really moving (standing still)
		Vec3d v = player.getVelocity();
		if (v.x * v.x + v.z * v.z < 0.0004) { // < 0.02 m/tick horizontally
			return;
		}

		long now = System.currentTimeMillis();
		if (now - lastSampleMs < 40L) {
			// Throttle to ~25 Hz: we never want more than that even if the
			// game is ticking abnormally fast (lag spike catch-up).
			return;
		}

		Vec3d pos = player.getPos();

		// Reset the trail on teleport (sudden jump > 8 blocks since last sample).
		Sample last = samples.peekLast();
		if (last != null) {
			double dx = pos.x - last.x;
			double dz = pos.z - last.z;
			if (dx * dx + dz * dz > 64.0) {
				samples.clear();
			}
		}

		samples.addLast(new Sample(pos.x, pos.y, pos.z, player.getHeight(), now));
		lastSampleMs = now;

		int maxSamples = Math.max(2, (int) length.get().doubleValue());
		while (samples.size() > maxSamples) {
			samples.pollFirst();
		}
	}

	// --- rendering ----------------------------------------------------------

	@Override
	public void onRender(Matrix4f matrix, float tickDelta) {
		if (samples.size() < 2) {
			return;
		}

		long now = System.currentTimeMillis();
		long maxAgeMs = (long) (fadeSeconds.get() * 1000.0);
		if (maxAgeMs <= 0L) {
			return;
		}

		// Drop aged-out samples (the deque is time-ordered).
		Iterator<Sample> it = samples.iterator();
		while (it.hasNext()) {
			Sample s = it.next();
			if (now - s.t > maxAgeMs) {
				it.remove();
			} else {
				break;
			}
		}
		if (samples.size() < 2) {
			return;
		}

		Sample[] arr = samples.toArray(new Sample[0]);
		Projection.Result[] foot = new Projection.Result[arr.length];
		Projection.Result[] back = new Projection.Result[arr.length];
		for (int i = 0; i < arr.length; i++) {
			Sample s = arr[i];
			foot[i] = Projection.project(s.x, s.y,                       s.z);
			back[i] = Projection.project(s.x, s.y + s.height * 0.95,     s.z);
		}

		String styleMode   = style.get();
		String paletteMode = color.get();
		int   alphaPeak    = clampInt((int) Math.round(brightness.get()), 0, 255);
		float baseThick    = width.getFloat();
		float wavePhase    = (now % 100000L) / 1000.0f;
		boolean wavy       = "Wave".equals(styleMode);

		int strands;
		switch (styleMode) {
			case "Line":   strands = 1; break;
			case "Dual":   strands = 2; break;
			case "Wave":   strands = 5; break;
			case "Ribbon":
			default:       strands = 7;
		}

		for (int i = 0; i < arr.length - 1; i++) {
			// Both endpoints (and both anchor heights) must be in front of
			// the camera, otherwise the projected screen coords are garbage.
			if (!foot[i].onScreen()     || !back[i].onScreen())     continue;
			if (!foot[i + 1].onScreen() || !back[i + 1].onScreen()) continue;

			// Age fade based on the *older* endpoint (sample i)
			float age = (now - arr[i].t) / (float) maxAgeMs;
			if (age < 0.0f) age = 0.0f;
			if (age > 1.0f) age = 1.0f;
			float fade = 1.0f - age;
			int alphaI = clampInt(Math.round(alphaPeak * fade), 0, 255);

			// Adaptive thickness: in ribbon/wave we widen each strand so the
			// stack covers the whole projected feet→back vertical span with
			// no gaps regardless of camera distance.
			float spanA = (float) Math.hypot(back[i].x()     - foot[i].x(),     back[i].y()     - foot[i].y());
			float spanB = (float) Math.hypot(back[i + 1].x() - foot[i + 1].x(), back[i + 1].y() - foot[i + 1].y());
			float avgSpan = (spanA + spanB) * 0.5f;

			float thick;
			if ("Ribbon".equals(styleMode) || wavy) {
				int stepCount = Math.max(1, strands - 1);
				thick = Math.max(baseThick, avgSpan / stepCount * 1.4f);
			} else {
				thick = baseThick;
			}
			// Width also tapers with age so the trail visually thins as it dies.
			thick *= 0.45f + 0.55f * fade;
			if (thick < 0.4f) thick = 0.4f;

			for (int s = 0; s < strands; s++) {
				float t;
				switch (styleMode) {
					case "Line":  t = 0.55f; break;
					case "Dual":  t = (s == 0) ? 0.05f : 0.85f; break;
					case "Wave":
					case "Ribbon":
					default:      t = strands == 1 ? 0.5f : s / (float) (strands - 1);
				}

				float ax = lerp(foot[i].x(),     back[i].x(),     t);
				float ay = lerp(foot[i].y(),     back[i].y(),     t);
				float bx = lerp(foot[i + 1].x(), back[i + 1].x(), t);
				float by = lerp(foot[i + 1].y(), back[i + 1].y(), t);

				if (wavy) {
					float amp = 2.5f + avgSpan * 0.04f;
					float w1 = (float) Math.sin(wavePhase * 5.0 + i       * 0.55 + t * 6.0) * amp;
					float w2 = (float) Math.sin(wavePhase * 5.0 + (i + 1) * 0.55 + t * 6.0) * amp;
					ay += w1;
					by += w2;
				}

				int strandColor = paletteFor(paletteMode, i, arr.length, s, strands, wavePhase, alphaI);
				drawSegment(matrix, ax, ay, bx, by, thick, strandColor);
			}
		}
	}

	// --- segment drawing ----------------------------------------------------

	/**
	 * Draw a thin rectangle from {@code (x1,y1)} to {@code (x2,y2)} by
	 * rotating the matrix to align local +X with the segment direction, then
	 * rendering an axis-aligned rounded rect through the project's
	 * {@link Builder#rectangle() rectangle builder}.
	 */
	private static void drawSegment(Matrix4f base,
	                                float x1, float y1, float x2, float y2,
	                                float thickness, int argb) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 0.05f || thickness < 0.05f) {
			return;
		}
		float angle = (float) Math.atan2(dy, dx);

		Matrix4f m = new Matrix4f(base)
			.translate(x1, y1, 0.0f)
			.rotateZ(angle);

		float halfT = thickness * 0.5f;
		Builder.rectangle()
			.size(new SizeState(len, thickness))
			.radius(new QuadRadiusState(halfT))
			.color(new QuadColorState(argb))
			.smoothness(1.0f)
			.build()
			.render(m, 0.0f, -halfT);
	}

	// --- color math ---------------------------------------------------------

	private static int paletteFor(String palette,
	                              int sampleIdx, int sampleCount,
	                              int strandIdx, int strandCount,
	                              float phase, int alpha) {
		float along  = (sampleCount <= 1) ? 0.0f : sampleIdx / (float) (sampleCount - 1);
		float across = (strandCount <= 1) ? 0.5f : strandIdx / (float) (strandCount - 1);

		switch (palette) {
			case "Cyan":     return shift(0xFF22D3EE, 0xFF60A5FA, along, across, phase, alpha);
			case "Crimson":  return shift(0xFFEF4444, 0xFFB91C1C, along, across, phase, alpha);
			case "Lime":     return shift(0xFFA3E635, 0xFF22C55E, along, across, phase, alpha);
			case "Gold":     return shift(0xFFFCD34D, 0xFFF59E0B, along, across, phase, alpha);
			case "Rainbow": {
				float h = (along * 0.7f + across * 0.2f + phase * 0.4f) % 1.0f;
				if (h < 0.0f) h += 1.0f;
				return withAlpha(hsv(h, 0.85f, 1.0f), alpha);
			}
			case "Magenta":
			default:         return shift(0xFFEF6CFB, 0xFFC44CD8, along, across, phase, alpha);
		}
	}

	/** Two-stop palette that breathes between {@code a} and {@code b} over time. */
	private static int shift(int a, int b, float along, float across, float phase, int alpha) {
		float t = (float) (0.5 + 0.5 * Math.sin(phase * 2.0 + along * 4.0 + across * 2.0));
		return withAlpha(lerpColor(a, b, t), alpha);
	}

	private static int withAlpha(int argb, int alpha) {
		return ((alpha & 0xFF) << 24) | (argb & 0x00FFFFFF);
	}

	private static int lerpColor(int a, int b, float t) {
		if (t < 0.0f) t = 0.0f;
		if (t > 1.0f) t = 1.0f;
		int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		int rr = Math.round(ar + (br - ar) * t);
		int rg = Math.round(ag + (bg - ag) * t);
		int rb = Math.round(ab + (bb - ab) * t);
		return 0xFF000000 | (rr << 16) | (rg << 8) | rb;
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static int hsv(float h, float s, float v) {
		float r, g, b;
		float i = (float) Math.floor(h * 6.0f);
		float f = h * 6.0f - i;
		float p = v * (1.0f - s);
		float q = v * (1.0f - f * s);
		float ti = v * (1.0f - (1.0f - f) * s);
		switch (((int) i) % 6) {
			case 0:  r = v;  g = ti; b = p;  break;
			case 1:  r = q;  g = v;  b = p;  break;
			case 2:  r = p;  g = v;  b = ti; break;
			case 3:  r = p;  g = q;  b = v;  break;
			case 4:  r = ti; g = p;  b = v;  break;
			default: r = v;  g = p;  b = q;  break;
		}
		int ri = clampInt(Math.round(r * 255.0f), 0, 255);
		int gi = clampInt(Math.round(g * 255.0f), 0, 255);
		int bi = clampInt(Math.round(b * 255.0f), 0, 255);
		return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
	}

	private static int clampInt(int v, int min, int max) {
		return v < min ? min : (v > max ? max : v);
	}

	/** A captured world-space position, recorded once per tick while moving. */
	private record Sample(double x, double y, double z, float height, long t) { }
}
