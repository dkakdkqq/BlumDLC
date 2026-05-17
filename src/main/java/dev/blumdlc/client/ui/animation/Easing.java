package dev.blumdlc.client.ui.animation;

@FunctionalInterface
public interface Easing {

	float ease(float t);

	Easing LINEAR           = t -> t;
	Easing EASE_OUT_CUBIC   = t -> 1.0f - (float) Math.pow(1.0f - t, 3);
	Easing EASE_OUT_QUART   = t -> 1.0f - (float) Math.pow(1.0f - t, 4);
	Easing EASE_OUT_QUINT   = t -> 1.0f - (float) Math.pow(1.0f - t, 5);
	Easing EASE_IN_OUT_CUBIC = t -> t < 0.5f
		? 4.0f * t * t * t
		: 1.0f - (float) Math.pow(-2.0f * t + 2.0f, 3) / 2.0f;
	Easing EASE_OUT_EXPO    = t -> t >= 1.0f ? 1.0f : 1.0f - (float) Math.pow(2.0f, -10.0f * t);
	Easing EASE_OUT_BACK    = t -> {
		float c1 = 1.70158f;
		float c3 = c1 + 1.0f;
		float k = t - 1.0f;
		return 1.0f + c3 * k * k * k + c1 * k * k;
	};
	Easing EASE_OUT_ELASTIC = t -> {
		if (t == 0.0f) return 0.0f;
		if (t == 1.0f) return 1.0f;
		float c4 = (2.0f * (float) Math.PI) / 3.0f;
		return (float) Math.pow(2.0, -10.0 * t)
			* (float) Math.sin((t * 10.0f - 0.75f) * c4) + 1.0f;
	};

}
