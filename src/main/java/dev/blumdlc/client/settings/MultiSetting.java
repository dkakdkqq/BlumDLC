package dev.blumdlc.client.settings;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A setting that holds a subset of options selected from a fixed list.
 * Used for things like "Targets: players, animals, mobs" where the user can
 * tick more than one item.
 */
public final class MultiSetting extends Setting<Set<String>> {

	public final List<String> options;

	public MultiSetting(String name, List<String> options, String... defaults) {
		super(name, new LinkedHashSet<>());
		this.options = List.copyOf(options);
		for (String d : defaults) {
			this.value.add(d);
		}
	}

	public boolean isSelected(String option) {
		return this.value.contains(option);
	}

	public void toggle(String option) {
		if (!this.options.contains(option)) {
			return;
		}
		if (this.value.contains(option)) {
			this.value.remove(option);
		} else {
			this.value.add(option);
		}
	}

}
