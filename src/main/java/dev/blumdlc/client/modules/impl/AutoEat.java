package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.BooleanSetting;
import dev.blumdlc.client.settings.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

/**
 * AutoEat — when food drops below {@code Min Hunger}, holds the use-key on
 * the best food in the hotbar (or offhand if the corresponding option is on)
 * until the player is full again.
 *
 * <p>The previous selected hotbar slot is restored when eating completes so
 * the user's tool stays in hand.
 */
public final class AutoEat extends Module {

	public final NumberSetting hungerThreshold;
	public final BooleanSetting useOffhand;

	private int  prevSlot = -1;
	private boolean eating = false;

	public AutoEat() {
		super("AutoEat", "Auto-eats when hungry", Category.PLAYER);
		hungerThreshold = new NumberSetting("Min Hunger", 17.0, 1.0, 19.0, 1.0);
		useOffhand = new BooleanSetting("Prefer offhand", true);
		addSetting(hungerThreshold);
		addSetting(useOffhand);
	}


	@Override
	public void onTick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity p = mc.player;
		if (p == null) { stop(mc); return; }
		if (mc.currentScreen != null) { stop(mc); return; }
		if (p.getHungerManager().getFoodLevel() > hungerThreshold.get()) { stop(mc); return; }

		// Offhand first
		if (useOffhand.get() && isFood(p.getOffHandStack())) {
			press(mc, true);
			eating = true;
			return;
		}

		PlayerInventory inv = p.getInventory();
		for (int i = 0; i < 9; i++) {
			if (isFood(inv.getStack(i))) {
				if (!eating) prevSlot = inv.selectedSlot;
				inv.selectedSlot = i;
				press(mc, true);
				eating = true;
				return;
			}
		}
		stop(mc);
	}

	private void stop(MinecraftClient mc) {
		if (!eating) return;
		press(mc, false);
		ClientPlayerEntity p = mc.player;
		if (p != null && prevSlot >= 0 && !useOffhand.get()) {
			p.getInventory().selectedSlot = prevSlot;
		}
		prevSlot = -1;
		eating = false;
	}

	private void press(MinecraftClient mc, boolean down) {
		mc.options.useKey.setPressed(down);
	}

	private static boolean isFood(ItemStack s) {
		if (s == null || s.isEmpty()) return false;
		FoodComponent fc = s.get(DataComponentTypes.FOOD);
		return fc != null;
	}

	@Override
	protected void onDisable() {
		stop(MinecraftClient.getInstance());
	}
}
