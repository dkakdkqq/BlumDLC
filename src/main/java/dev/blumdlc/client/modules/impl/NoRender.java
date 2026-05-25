package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.BooleanSetting;

/**
 * NoRender — disables various rendering effects for clarity/performance.
 * Actual effect application requires hooks, this stores the config state.
 */
public final class NoRender extends Module {

	public final BooleanSetting hurtCam;
	public final BooleanSetting fire;
	public final BooleanSetting pumpkin;
	public final BooleanSetting fog;
	public final BooleanSetting particles;

	public NoRender() {
		super("NoRender", "Removes unwanted visual effects", Category.RENDER);
		this.hurtCam = new BooleanSetting("No HurtCam", true);
		this.fire = new BooleanSetting("No Fire", true);
		this.pumpkin = new BooleanSetting("No Pumpkin", true);
		this.fog = new BooleanSetting("No Fog", true);
		this.particles = new BooleanSetting("No Particles", false);
		addSetting(hurtCam);
		addSetting(fire);
		addSetting(pumpkin);
		addSetting(fog);
		addSetting(particles);
	}
}
