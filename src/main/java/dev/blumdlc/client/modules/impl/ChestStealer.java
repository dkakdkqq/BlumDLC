package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.ItemStack;

/**
 * ChestStealer — automatically takes items from containers.
 */
public final class ChestStealer extends Module {

	public final NumberSetting delay;
	private int tickCounter = 0;

	public ChestStealer() {
		super("ChestStealer", "Steals items from containers", Category.PLAYER);
		this.delay = new NumberSetting("Delay (ticks)", 2.0, 0.0, 10.0, 1.0);
		addSetting(delay);
	}

	@Override
	public void onTick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.interactionManager == null) return;
		if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;

		if (tickCounter++ < (int) delay.get().doubleValue()) return;
		tickCounter = 0;

		GenericContainerScreenHandler handler = screen.getScreenHandler();
		int containerSlots = handler.getRows() * 9;

		for (int i = 0; i < containerSlots; i++) {
			ItemStack stack = handler.getSlot(i).getStack();
			if (!stack.isEmpty()) {
				mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
				return; // one item per delay tick
			}
		}
	}
}
