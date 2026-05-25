package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderTickCounter;

/**
 * Timer — changes the game tick speed (client-side).
 * Works by modifying the renderTickCounter's tickDelta.
 */
public final class Timer extends Module {

	public final NumberSetting speed;

	public Timer() {
		super("Timer", "Changes game tick speed", Category.UTIL);
		this.speed = new NumberSetting("Speed", 2.0, 0.1, 10.0, 0.1);
		addSetting(speed);
	}

	public float getTimerSpeed() {
		return enabled ? speed.getFloat() : 1.0f;
	}
}
