package dev.blumdlc.client.settings;

public final class NumberSetting extends Setting<Double> {

	public final double min, max, step;

	public NumberSetting(String name, double defaultValue, double min, double max, double step) {
		super(name, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step;
	}

	public float getFloat() {
		return value.floatValue();
	}

}
