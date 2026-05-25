package dev.blumdlc.client.settings;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A setting that holds a subset of options selected from a fixed list.
 * Used for things like "Targets: players, animals, mobs" where the user can
 * tick more than one item.
 *
 * <p>Rendered as a single dropdown trigger row by the GUI (the dropdown
 * overlay shows every option with a checkmark, so any number of them can be
 * toggled in one go without the surrounding settings list jumping in
 * height).
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

	/**
	 * Short human-readable description of the current selection, suitable for
	 * use as the dropdown trigger's label. Keeps the row compact so 7+ option
	 * settings (e.g. AttackAura's Extras) don't blow up to a wall of names.
	 *
	 * <ul>
	 *   <li>nothing selected   → {@code "None"}</li>
	 *   <li>everything selected → {@code "All"}</li>
	 *   <li>1-2 selected        → comma-joined names</li>
	 *   <li>3+ selected         → {@code "N selected"}</li>
	 * </ul>
	 */
	public String summary() {
		int n = this.value.size();
		if (n == 0) return "None";
		if (n == this.options.size()) return "All";
		if (n <= 2) return String.join(", ", this.value);
		return n + " selected";
	}

}
