package dev.blumdlc.client.settings;

import java.util.function.Supplier;

public abstract class Setting<T> {

	public final String name;
	protected T value;

	/**
	 * Optional visibility predicate. When non-null and returning false,
	 * the ClickGUI will skip rendering and click-handling for this setting.
	 * Used for conditional settings (e.g. show "Crit mode" only when
	 * "Only criticals" is enabled).
	 */
	private Supplier<Boolean> visibility;

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

	/**
	 * Sets a visibility predicate for this setting. When the supplier
	 * returns false, the setting is hidden from the ClickGUI popup.
	 *
	 * @param visibility supplier that returns true when visible
	 * @return this setting for chaining
	 */
	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S visibleWhen(Supplier<Boolean> visibility) {
		this.visibility = visibility;
		return (S) this;
	}

	/**
	 * Returns the visibility predicate, or null if always visible.
	 */
	public Supplier<Boolean> getVisibility() {
		return visibility;
	}

}
