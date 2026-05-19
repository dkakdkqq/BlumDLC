package dev.blumdlc.client.modules.impl;

import java.util.ArrayList;
import java.util.List;

import dev.blumdlc.client.BlumDLC;
import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.HudModule;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.BooleanSetting;

/**
 * A "meta-module" that lives in the Visual category and exposes a toggle
 * (BooleanSetting) for every registered {@link HudModule}.
 *
 * <p>This lets the user open the ClickGUI, navigate to Visual, right-click
 * "HUD Modules" and individually enable/disable each overlay (Watermark,
 * Keybinds, Potions, Staff) without needing to find them one-by-one.
 *
 * <p>The module is always-on (toggle does nothing meaningful) — it purely
 * acts as a settings container. On each tick it syncs the BooleanSetting
 * values into the actual HudModule.enabled flags.
 */
public final class HudSettings extends Module {

	private final List<HudEntry> entries = new ArrayList<>();

	public HudSettings() {
		super("HUD Modules", "Toggle which HUD overlays are visible", Category.RENDER);
		this.enabled = true; // always active
	}

	/**
	 * Called once after all modules have been registered so we can discover
	 * every HudModule and create a toggle for it.
	 */
	public void populate() {
		entries.clear();
		settings.clear();
		for (Module m : BlumDLC.MODULES.all()) {
			if (m instanceof HudModule hud) {
				BooleanSetting toggle = new BooleanSetting(hud.name, hud.enabled);
				addSetting(toggle);
				entries.add(new HudEntry(hud, toggle));
			}
		}
	}

	@Override
	public void onTick() {
		// Sync toggle state → actual HudModule.enabled every tick.
		for (HudEntry e : entries) {
			if (e.hud.enabled != e.toggle.get()) {
				e.hud.setEnabled(e.toggle.get());
			}
		}
	}

	// Prevent turning off from the card toggle — this module is always-on.
	@Override
	public void toggle() {
		// no-op
	}

	private record HudEntry(HudModule hud, BooleanSetting toggle) { }
}
