package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

/**
 * AutoArmor — automatically equips the best armor from inventory.
 */
public final class AutoArmor extends Module {

	private int tickDelay = 0;

	public AutoArmor() {
		super("AutoArmor", "Equips best armor automatically", Category.PLAYER);
	}

	@Override
	public void onTick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		if (player == null || mc.interactionManager == null) return;
		if (mc.currentScreen != null) return; // Don't act in inventories

		if (tickDelay > 0) { tickDelay--; return; }

		// Check each armor slot
		for (int armorSlot = 0; armorSlot < 4; armorSlot++) {
			ItemStack current = player.getInventory().getArmorStack(armorSlot);
			int bestSlot = -1;
			int bestProtection = current.getItem() instanceof ArmorItem ai
				? ai.getProtection() : 0;

			for (int i = 0; i < 36; i++) {
				ItemStack stack = player.getInventory().getStack(i);
				if (stack.getItem() instanceof ArmorItem ai) {
					if (ai.getSlotType().getEntitySlotId() == armorSlot && ai.getProtection() > bestProtection) {
						bestProtection = ai.getProtection();
						bestSlot = i;
					}
				}
			}

			if (bestSlot != -1) {
				int syncId = player.currentScreenHandler.syncId;
				int screenSlot = bestSlot < 9 ? bestSlot + 36 : bestSlot;
				int armorScreenSlot = 8 - armorSlot; // Armor slots are 5-8 in screen handler
				mc.interactionManager.clickSlot(syncId, screenSlot, 0, SlotActionType.PICKUP, player);
				mc.interactionManager.clickSlot(syncId, armorScreenSlot, 0, SlotActionType.PICKUP, player);
				mc.interactionManager.clickSlot(syncId, screenSlot, 0, SlotActionType.PICKUP, player);
				tickDelay = 3;
				return;
			}
		}
	}
}
