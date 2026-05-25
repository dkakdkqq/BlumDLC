package dev.blumdlc.client.settings;

import java.util.List;

/**
 * A pick-one-of-many string setting. Always rendered as a collapsed dropdown
 * by the GUI — there is no "expanded radio list" mode any more, the user
 * asked for a uniform dropdown experience across every module.
 */
public final class ModeSetting extends Setting<String> {

	public final List<String> modes;

	public ModeSetting(String name, String defaultValue, String... modes) {
		super(name, defaultValue);
		this.modes = List.of(modes);
	}

	public boolean is(String mode) {
		return mode.equals(this.value);
	}

	public void cycle() {
		int i = (modes.indexOf(value) + 1) % modes.size();
		this.value = modes.get(i);
	}

}
