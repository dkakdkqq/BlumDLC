package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public final class FullBright extends Module {

	public FullBright() {
		super("FullBright", "See in the dark with max gamma", Category.RENDER);
	}

	@Override
	public void onTick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) return;
		mc.player.addStatusEffect(new StatusEffectInstance(
			StatusEffects.NIGHT_VISION, 400, 0, false, false, false));
	}

	@Override
	protected void onDisable() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) {
			mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
		}
	}
}
