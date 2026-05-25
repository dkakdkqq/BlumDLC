package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * NoSlow — prevents slowdown from using items (eating, blocking).
 * Uses input injection approach.
 */
public final class NoSlow extends Module {

	public final BooleanSetting items;
	public final BooleanSetting soulSand;

	public NoSlow() {
		super("NoSlow", "Prevents slowdown from actions", Category.MOVEMENT);
		this.items = new BooleanSetting("Items", true);
		this.soulSand = new BooleanSetting("Soul Sand", true);
		addSetting(items);
		addSetting(soulSand);
	}

	@Override
	public void onTick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		if (player == null) return;

		// The actual anti-slowdown is applied via the tick input override;
		// for Fabric 1.21.4, we re-apply sprint if player is using item
		if (items.get() && player.isUsingItem() && player.input.movementForward > 0) {
			player.setSprinting(true);
		}
	}
}
