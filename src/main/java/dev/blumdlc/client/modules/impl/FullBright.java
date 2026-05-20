package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;

/**
 * FullBright — pushes the gamma slider far beyond vanilla so dark areas
 * become readable. The original gamma is restored when the module is
 * disabled.
 */
public final class FullBright extends Module {

	public final NumberSetting gamma;

	private double saved = -1.0;

	public FullBright() {
		super("FullBright", "Boosts gamma far above vanilla", Category.RENDER);
		gamma = new NumberSetting("Gamma", 16.0, 1.0, 100.0, 0.5);
		addSetting(gamma);
	}

	@Override
	protected void onEnable() {
		SimpleOption<Double> g = MinecraftClient.getInstance().options.getGamma();
		if (saved < 0.0) saved = g.getValue();
		g.setValue(gamma.get());
	}

	@Override
	protected void onDisable() {
		SimpleOption<Double> g = MinecraftClient.getInstance().options.getGamma();
		if (saved >= 0.0) {
			g.setValue(saved);
			saved = -1.0;
		}
	}

	@Override
	public void onTick() {
		SimpleOption<Double> g = MinecraftClient.getInstance().options.getGamma();
		if (Math.abs(g.getValue() - gamma.get()) > 0.01) {
			g.setValue(gamma.get());
		}
	}
}
