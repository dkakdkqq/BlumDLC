package dev.blumdlc.client.ui.util;

public final class ColorUtil {

	public static int lerp(int a, int b, float t) {
		t = clamp01(t);
		int aa = (a >> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		int ra = Math.round(aa + (ba - aa) * t);
		int rr = Math.round(ar + (br - ar) * t);
		int rg = Math.round(ag + (bg - ag) * t);
		int rb = Math.round(ab + (bb - ab) * t);
		return (ra << 24) | (rr << 16) | (rg << 8) | rb;
	}

	public static int withAlpha(int color, float alpha) {
		int a = Math.round(0xFF * clamp01(alpha));
		return (a << 24) | (color & 0xFFFFFF);
	}

	public static int multiplyAlpha(int color, float multiplier) {
		int a = (color >>> 24) & 0xFF;
		a = Math.round(a * clamp01(multiplier));
		return (a << 24) | (color & 0xFFFFFF);
	}

	private static float clamp01(float v) {
		return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
	}

	private ColorUtil() {
	}

}
