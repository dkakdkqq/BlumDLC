package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

/**
 * AutoHeal — automatically eats food or uses health potions when low.
 */
public final class AutoHeal extends Module {

	public final NumberSetting healthThreshold;

	public AutoHeal() {
		super("AutoHeal", "Auto-eats food when health is low", Category.PLAYER);
		this.healthThreshold = new NumberSetting("Health", 10.0, 2.0, 18.0, 1.0);
		addSetting(healthThreshold);
	}

	@Override
	public void onTick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		if (player == null || mc.interactionManager == null) return;
		if (player.getHealth() > healthThreshold.get()) return;
		if (player.isUsingItem()) return;

		// Check if holding food
		ItemStack main = player.getMainHandStack();
		FoodComponent food = main.get(DataComponentTypes.FOOD);
		if (food != null && player.getHungerManager().isNotFull()) {
			mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
			return;
		}

		ItemStack off = player.getOffHandStack();
		FoodComponent offFood = off.get(DataComponentTypes.FOOD);
		if (offFood != null && player.getHungerManager().isNotFull()) {
			mc.interactionManager.interactItem(player, Hand.OFF_HAND);
		}
	}
}
