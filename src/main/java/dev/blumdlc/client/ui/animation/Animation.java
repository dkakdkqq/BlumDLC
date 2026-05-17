package dev.blumdlc.client.ui.animation;

public final class Animation {

	private float from;
	private float to;
	private float current;
	private long startTime;
	private final long duration;
	private final Easing easing;

	public Animation(float initial, long durationMs, Easing easing) {
		this.from = initial;
		this.to = initial;
		this.current = initial;
		this.duration = durationMs;
		this.easing = easing;
		this.startTime = System.currentTimeMillis() - durationMs;
	}

	public void setTarget(float target) {
		setTarget(target, 0L);
	}

	public void setTarget(float target, long delayMs) {
		if (delayMs == 0L && Float.compare(target, this.to) == 0) {
			return;
		}
		float now = getValue();
		this.from = now;
		this.to = target;
		this.startTime = System.currentTimeMillis() + delayMs;
	}

	public void setImmediate(float v) {
		this.from = v;
		this.to = v;
		this.current = v;
		this.startTime = System.currentTimeMillis() - duration;
	}

	public float getValue() {
		long now = System.currentTimeMillis();
		if (now < startTime) {
			return current;
		}
		long elapsed = now - startTime;
		if (elapsed >= duration) {
			current = to;
			return to;
		}
		float t = (float) elapsed / duration;
		current = from + (to - from) * easing.ease(t);
		return current;
	}

	public boolean isFinished() {
		return System.currentTimeMillis() - startTime >= duration;
	}

	public float getTarget() {
		return to;
	}

}
