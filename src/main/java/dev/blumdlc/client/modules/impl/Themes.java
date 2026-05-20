package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.BlumDLC;
import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.ModeSetting;
import dev.blumdlc.client.ui.ClientTheme;

/**
 * Lives in {@link Category#THEMES} and is the user-facing way to pick the
 * client's accent colour. The selected preset is pushed straight into
 * {@link ClientTheme}, which the rest of the GUI / HUD pulls from each frame.
 *
 * <p>The dropdown is rendered as an always-expanded radio list (see
 * {@link ModeSetting}'s {@code expanded} flag) so picking a colour is one
 * click. Changes are picked up by {@link #syncAll()}, called once per frame
 * by the GUI render loop and the HUD render callback — that way the theme
 * applies the moment the user clicks an option, no extra "apply" toggle
 * needed.
 *
 * <p>Toggling the card on still re-applies the theme as a convenient way
 * to force a refresh; the module immediately turns itself off again so the
 * card stays "stateless" in feel.
 */
public final class Themes extends Module {

	public final ModeSetting palette;

	public Themes() {
		super("Client Color", "Pick the client accent palette", Category.THEMES);

		// Build the dropdown from ClientTheme.PRESETS so adding a preset in
		// one place is enough.
		String[] names = new String[ClientTheme.PRESETS.size()];
		for (int i = 0; i < ClientTheme.PRESETS.size(); i++) {
			names[i] = ClientTheme.PRESETS.get(i).name;
		}
		this.palette = new ModeSetting("Palette", ClientTheme.active().name, true, names);
		addSetting(this.palette);
	}

	@Override
	protected void onEnable() {
		// Toggling the card "applies" the theme and bounces back off so the
		// card behaves like a momentary button rather than a sticky toggle.
		ClientTheme.apply(palette.get());
		this.enabled = false;
	}

	/**
	 * Pushes the selected palette into {@link ClientTheme} if it differs
	 * from the active one. Cheap (one string compare).
	 */
	public void apply() {
		if (!palette.get().equalsIgnoreCase(ClientTheme.active().name)) {
			ClientTheme.apply(palette.get());
		}
	}

	/**
	 * Walks every registered module once and applies whichever {@link Themes}
	 * instance it finds. Safe to call from any frame hook — does nothing if
	 * the picked palette is already active.
	 */
	public static void syncAll() {
		for (Module m : BlumDLC.MODULES.all()) {
			if (m instanceof Themes t) {
				t.apply();
			}
		}
	}
}
