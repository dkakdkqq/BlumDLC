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
import dev.blumdlc.client.settings.NumberSetting;
import dev.blumdlc.client.ui.ClientTheme;
import dev.blumdlc.client.ui.util.ColorUtil;
import dev.blumdlc.client.util.Projection;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Trails — smooth gradient ribbon behind the player, popular visual client style.
 * Single elegant mode: wide smooth ribbon from feet to shoulders with accent
 * colour gradient, alpha fade over time, thickness taper. No mode selection —
 * just clean premium visuals.
 */
public final class Trails extends Module {

	public final NumberSetting width;
	public final NumberSetting length;
	public final NumberSetting fadeTime;
	public final BooleanSetting rainbow;
	public final BooleanSetting onlyMoving;

	private final Deque<Sample> samples = new ArrayDeque<>();
	private long lastSampleMs = 0L;

	public Trails() {
		super("Trails", "Smooth ribbon trail behind the player", Category.RENDER);

		this.width       = new NumberSetting("Width",     2.0,  0.5,  6.0, 0.1);
		this.length      = new NumberSetting("Length",   50.0, 10.0, 150.0, 1.0);
		this.fadeTime    = new NumberSetting("Fade (s)",  2.5,  0.5,   5.0, 0.1);
		this.rainbow     = new BooleanSetting("Rainbow", false);
		this.onlyMoving  = new BooleanSetting("Only Moving", true);

		addSetting(this.width);
		addSetting(this.length);
		addSetting(this.fadeTime);
		addSetting(this.rainbow);
		addSetting(this.onlyMoving);
	}

	@Override
	protected void onDisable() {
		samples.clear();
		lastSampleMs = 0L;
	}

	// =========================================================================
	//  Sampling
	// =========================================================================

	@Override
	public void onTick() {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null) return;

		if (onlyMoving.get()) {
			Vec3d v = player.getVelocity();
			if (v.x * v.x + v.z * v.z < 0.0004) return;
		}

		long now = System.currentTimeMillis();
		if (now - lastSampleMs < 35L) return; // ~28 Hz sampling

		Vec3d pos = player.getPos();

		// Reset on teleport (jump > 8 blocks)
		Sample last = samples.peekLast();
		if (last != null) {
			double dx = pos.x - last.x;
			double dz = pos.z - last.z;
			if (dx * dx + dz * dz > 64.0) samples.clear();
		}

		samples.addLast(new Sample(pos.x, pos.y, pos.z, player.getHeight(), now));
		lastSampleMs = now;

		int max = Math.max(2, (int) length.get().doubleValue());
		while (samples.size() > max) samples.pollFirst();
	}

	// =========================================================================
	//  Rendering
	// =========================================================================

	@Override
	public void onRender(Matrix4f matrix, float tickDelta) {
		if (samples.size() < 2) return;

		long now = System.currentTimeMillis();
		long maxAgeMs = (long) (fadeTime.get() * 1000.0);
		if (maxAgeMs <= 0L) return;

		// Remove aged-out samples
		Iterator<Sample> it = samples.iterator();
		while (it.hasNext()) {
			if (now - it.next().t > maxAgeMs) it.remove();
			else break;
		}
		if (samples.size() < 2) return;

		Sample[] arr = samples.toArray(new Sample[0]);
		Projection.Result[] foot = new Projection.Result[arr.length];
		Projection.Result[] back = new Projection.Result[arr.length];

		for (int i = 0; i < arr.length; i++) {
			Sample s = arr[i];
			foot[i] = Projection.project(s.x, s.y + 0.05, s.z);
			back[i] = Projection.project(s.x, s.y + s.height * 0.92, s.z);
		}

		float baseThick = width.getFloat();
		int strands = 6; // smooth ribbon coverage
		float phase = (now % 100000L) / 1000.0f;

		for (int i = 0; i < arr.length - 1; i++) {
			if (!foot[i].onScreen() || !back[i].onScreen()) continue;
			if (!foot[i + 1].onScreen() || !back[i + 1].onScreen()) continue;

			// Age-based fade
			float age = (now - arr[i].t) / (float) maxAgeMs;
			age = Math.max(0, Math.min(1, age));
			float fade = 1.0f - age;
			int alpha = Math.round(230 * fade);
			if (alpha <= 5) continue;

			// Adaptive thickness to cover the projected span
			float spanA = dist(foot[i], back[i]);
			float spanB = dist(foot[i + 1], back[i + 1]);
			float avgSpan = (spanA + spanB) * 0.5f;
			float thick = Math.max(baseThick, avgSpan / Math.max(1, strands - 1) * 1.3f);
			thick *= 0.4f + 0.6f * fade; // taper with age
			if (thick < 0.3f) continue;

			for (int s = 0; s < strands; s++) {
				float t = strands == 1 ? 0.5f : s / (float) (strands - 1);

				float ax = lerp(foot[i].x(), back[i].x(), t);
				float ay = lerp(foot[i].y(), back[i].y(), t);
				float bx = lerp(foot[i + 1].x(), back[i + 1].x(), t);
				float by = lerp(foot[i + 1].y(), back[i + 1].y(), t);

				int color = computeColor(i, arr.length, s, strands, phase, alpha);
				drawSegment(matrix, ax, ay, bx, by, thick, color);
			}
		}
	}

	// =========================================================================
	//  Color
	// =========================================================================

	private int computeColor(int sampleIdx, int sampleCount, int strandIdx, int strandCount,
			float phase, int alpha) {
		float along = sampleCount <= 1 ? 0 : sampleIdx / (float) (sampleCount - 1);
		float across = strandCount <= 1 ? 0.5f : strandIdx / (float) (strandCount - 1);

		if (rainbow.get()) {
			float h = (along * 0.6f + across * 0.15f + phase * 0.3f) % 1.0f;
			if (h < 0) h += 1.0f;
			return withAlpha(hsv(h, 0.9f, 1.0f), alpha);
		}

		// Use client theme accent gradient
		int from = ClientTheme.from();
		int to = ClientTheme.to();
		float t = (float) (0.5 + 0.5 * Math.sin(phase * 1.8 + along * 3.5 + across * 2.0));
		int blended = ColorUtil.lerp(from, to, t);
		return withAlpha(blended, alpha);
	}

	// =========================================================================
	//  Drawing
	// =========================================================================

	private static void drawSegment(Matrix4f base, float x1, float y1, float x2, float y2,
			float thickness, int argb) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 0.05f || thickness < 0.05f) return;

		float angle = (float) Math.atan2(dy, dx);
		Matrix4f m = new Matrix4f(base).translate(x1, y1, 0.0f).rotateZ(angle);

		float halfT = thickness * 0.5f;
		Builder.rectangle()
			.size(new SizeState(len, thickness))
			.radius(new QuadRadiusState(halfT))
			.color(new QuadColorState(argb))
			.smoothness(1.0f)
			.build()
			.render(m, 0.0f, -halfT);
	}

	// =========================================================================
	//  Helpers
	// =========================================================================

	private static float dist(Projection.Result a, Projection.Result b) {
		return (float) Math.hypot(b.x() - a.x(), b.y() - a.y());
	}

	private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

	private static int withAlpha(int argb, int alpha) {
		return ((alpha & 0xFF) << 24) | (argb & 0x00FFFFFF);
	}

	private static int hsv(float h, float s, float v) {
		float i = (float) Math.floor(h * 6.0f);
		float f = h * 6.0f - i;
		float p = v * (1 - s), q = v * (1 - f * s), t2 = v * (1 - (1 - f) * s);
		float r, g, b;
		switch (((int) i) % 6) {
			case 0: r=v; g=t2; b=p; break;
			case 1: r=q; g=v;  b=p; break;
			case 2: r=p; g=v;  b=t2; break;
			case 3: r=p; g=q;  b=v; break;
			case 4: r=t2; g=p; b=v; break;
			default: r=v; g=p; b=q; break;
		}
		return 0xFF000000
			| (Math.round(r * 255) << 16)
			| (Math.round(g * 255) << 8)
			| Math.round(b * 255);
	}

	private record Sample(double x, double y, double z, float height, long t) {}
}
