package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

/**
 * AutoArmor — automatically equips the best armor from the inventory.
 *
 * <p>1.21.4 dropped {@code ArmorItem.getProtection()} / {@code getSlotType()}.
 * Protection is now expressed as an {@link AttributeModifiersComponent}
 * targeting the {@link EntityAttributes#ARMOR} attribute, and the target
 * equipment slot is resolved via {@link LivingEntity#getPreferredEquipmentSlot(ItemStack)}.
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
			double bestProtection = current.getItem() instanceof ArmorItem
				? armorValue(current) : 0.0;

			for (int i = 0; i < 36; i++) {
				ItemStack stack = player.getInventory().getStack(i);
				if (!(stack.getItem() instanceof ArmorItem)) continue;

				EquipmentSlot eq = LivingEntity.getPreferredEquipmentSlot(stack);
				if (!eq.isArmorSlot()) continue;
				if (eq.getEntitySlotId() != armorSlot) continue;

				double protection = armorValue(stack);
				if (protection > bestProtection) {
					bestProtection = protection;
					bestSlot = i;
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

	/**
	 * Sums all {@code ADD_VALUE} modifiers for the {@link EntityAttributes#ARMOR}
	 * attribute on the given stack, scoped to the slot the item would naturally
	 * be equipped in. This mirrors what vanilla {@code ArmorItem.getProtection()}
	 * used to expose pre-1.21.4.
	 */
	private static double armorValue(ItemStack stack) {
		AttributeModifiersComponent comp = stack.getOrDefault(
			DataComponentTypes.ATTRIBUTE_MODIFIERS,
			AttributeModifiersComponent.DEFAULT);

		EquipmentSlot slot = LivingEntity.getPreferredEquipmentSlot(stack);
		final double[] sum = {0.0};
		comp.applyModifiers(slot, (attribute, modifier) -> {
			if (attribute == EntityAttributes.ARMOR
				&& modifier.operation() == EntityAttributeModifier.Operation.ADD_VALUE) {
				sum[0] += modifier.value();
			}
		});
		return sum[0];
	}
}
