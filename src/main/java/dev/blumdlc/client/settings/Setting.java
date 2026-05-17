package dev.blumdlc.client.settings;

public abstract class Setting<T> {

	public final String name;
	protected T value;

	protected Setting(String name, T defaultValue) {
		this.name = name;
		this.value = defaultValue;
	}

	public T get() {
		return value;
	}

	public void set(T value) {
		this.value = value;
	}

}
