package dev.blumdlc.client.settings;

/**
 * A colour setting stored as a hue value (0–360 degrees on the HSV wheel).
 * The saturation and value are fixed at 1.0 so the user always picks a
 * fully-saturated, fully-bright colour from the rainbow strip.
 *
 * <p>Helper methods convert the stored hue to an ARGB int for direct use in
 * rendering code.
 */
public final class ColorSetting extends Setting<Double> {

	public ColorSetting(String name, double defaultHue) {
		super(name, clampHue(defaultHue));
	}

	/** Returns the current hue in degrees (0–360). */
	public double getHue() {
		return value;
	}

	/** Sets the hue (clamped to 0–360). */
	public void setHue(double hue) {
		this.value = clampHue(hue);
	}

	/** Returns the colour as a fully-opaque ARGB int (saturation=1, value=1). */
	public int toArgb() {
		return hsvToArgb(value.floatValue() / 360.0f, 1.0f, 1.0f);
	}

	/** Returns the colour with a custom alpha (0.0–1.0). */
	public int toArgb(float alpha) {
		int rgb = hsvToArgb(value.floatValue() / 360.0f, 1.0f, 1.0f);
		int a = Math.round(Math.max(0.0f, Math.min(1.0f, alpha)) * 255.0f);
		return (a << 24) | (rgb & 0x00FFFFFF);
	}

	// ------------------------------------------------------------------

	private static double clampHue(double h) {
		h = h % 360.0;
		if (h < 0.0) h += 360.0;
		return h;
	}

	private static int hsvToArgb(float h, float s, float v) {
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
		int ri = Math.round(r * 255.0f);
		int gi = Math.round(g * 255.0f);
		int bi = Math.round(b * 255.0f);
		return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
	}
}
