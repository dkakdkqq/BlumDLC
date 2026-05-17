package dev.blumdlc.client.settings;

public final class BooleanSetting extends Setting<Boolean> {

	public BooleanSetting(String name, boolean defaultValue) {
		super(name, defaultValue);
	}

	public void toggle() {
		this.value = !this.value;
	}

}
