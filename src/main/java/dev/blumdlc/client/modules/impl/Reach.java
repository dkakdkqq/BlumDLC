package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.NumberSetting;

/**
 * Reach — extends the player's reach distance.
 * Note: In Fabric 1.21.4, this requires either a mixin or attribute modification.
 * This module stores the config; actual application depends on the platform hook.
 */
public final class Reach extends Module {

	public final NumberSetting combatReach;
	public final NumberSetting blockReach;

	public Reach() {
		super("Reach", "Extends combat and block reach", Category.COMBAT);
		this.combatReach = new NumberSetting("Combat", 3.5, 3.0, 6.0, 0.1);
		this.blockReach = new NumberSetting("Block", 4.5, 4.5, 8.0, 0.1);
		addSetting(combatReach);
		addSetting(blockReach);
	}

	public float getCombatReach() {
		return (float) combatReach.get().doubleValue();
	}

	public float getBlockReach() {
		return (float) blockReach.get().doubleValue();
	}
}
