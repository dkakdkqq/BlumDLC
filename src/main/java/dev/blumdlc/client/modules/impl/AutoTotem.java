package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/**
 * AutoTotem — keeps a Totem of Undying in the offhand slot so a fatal hit
 * resurrects the player. Triggered by the configured HP threshold; one
 * exchange per tick to keep server-rate-limit-friendly.
 */
public final class AutoTotem extends Module {

	public final NumberSetting healthThreshold;

	public AutoTotem() {
		super("AutoTotem", "Keeps a totem in the offhand", Category.PLAYER);
		healthThreshold = new NumberSetting("Min HP", 36.0, 1.0, 36.0, 1.0);
		addSetting(healthThreshold);
	}

	@Override
	public void onTick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity p = mc.player;
		if (p == null || mc.interactionManager == null) return;
		if (mc.currentScreen != null) return;
		if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;

		float hp = p.getHealth() + p.getAbsorptionAmount();
		if (hp > healthThreshold.get()) return;

		PlayerInventory inv = p.getInventory();
		int totemSlot = -1;
		for (int i = 0; i < 36; i++) {
			if (inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) { totemSlot = i; break; }
		}
		if (totemSlot == -1) return;

		int rawSrc = totemSlot < 9 ? totemSlot + 36 : totemSlot;
		int sync = p.currentScreenHandler.syncId;
		mc.interactionManager.clickSlot(sync, rawSrc, 0, SlotActionType.PICKUP, p);
		mc.interactionManager.clickSlot(sync, 45,     0, SlotActionType.PICKUP, p);
		ItemStack cursor = p.currentScreenHandler.getCursorStack();
		if (!cursor.isEmpty()) {
			mc.interactionManager.clickSlot(sync, rawSrc, 0, SlotActionType.PICKUP, p);
		}
	}
}
