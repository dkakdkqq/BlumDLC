package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/**
 * AutoTotem — automatically moves a Totem of Undying to the offhand.
 */
public final class AutoTotem extends Module {

	public AutoTotem() {
		super("AutoTotem", "Keeps a totem in your offhand", Category.COMBAT);
	}

	@Override
	public void onTick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		if (player == null || mc.interactionManager == null) return;

		// Check if offhand already has totem
		if (player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) return;

		// Find totem in inventory
		int totemSlot = -1;
		for (int i = 0; i < 36; i++) {
			if (player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
				totemSlot = i;
				break;
			}
		}

		if (totemSlot == -1) return;

		// Move totem to offhand (slot 45)
		int syncId = player.currentScreenHandler.syncId;
		// Convert player inventory slot to screen handler slot
		int screenSlot = totemSlot < 9 ? totemSlot + 36 : totemSlot;
		mc.interactionManager.clickSlot(syncId, screenSlot, 0, SlotActionType.PICKUP, player);
		mc.interactionManager.clickSlot(syncId, 45, 0, SlotActionType.PICKUP, player);
		mc.interactionManager.clickSlot(syncId, screenSlot, 0, SlotActionType.PICKUP, player);
	}
}
