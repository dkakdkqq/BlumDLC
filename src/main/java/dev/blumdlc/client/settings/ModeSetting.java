package dev.blumdlc.client.settings;

import java.util.List;

public final class ModeSetting extends Setting<String> {

	public final List<String> modes;

	/**
	 * When true, the ClickGUI draws this setting as an always-visible
	 * radio list instead of a collapsed dropdown selector.
	 */
	public final boolean expanded;

	public ModeSetting(String name, String defaultValue, String... modes) {
		this(name, defaultValue, false, modes);
	}

	public ModeSetting(String name, String defaultValue, boolean expanded, String... modes) {
		super(name, defaultValue);
		this.modes = List.of(modes);
		this.expanded = expanded;
	}

	public boolean is(String mode) {
		return mode.equals(this.value);
	}

	public void cycle() {
		int i = (modes.indexOf(value) + 1) % modes.size();
		this.value = modes.get(i);
	}

}
