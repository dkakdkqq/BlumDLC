package dev.blumdlc.client.modules;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import dev.blumdlc.client.settings.Setting;

public class Module {

	public final String name;
	public final String description;
	public final Category category;
	public boolean enabled;
	public final List<Setting<?>> settings = new ArrayList<>();

	public Module(String name, String description, Category category) {
		this.name = name;
		this.description = description;
		this.category = category;
	}

	public Module defaultOn() {
		this.enabled = true;
		return this;
	}

	public Module addSetting(Setting<?> setting) {
		this.settings.add(setting);
		return this;
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	public void setEnabled(boolean value) {
		if (this.enabled == value) {
			return;
		}
		this.enabled = value;
		if (value) {
			onEnable();
		} else {
			onDisable();
		}
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	public void onTick() {
	}

	/**
	 * Called once per frame from the HUD render pass, after the world has
	 * been drawn but before the vanilla HUD. Modules that draw screen-space
	 * overlays (TargetESP, HUDs, ...) implement this.
	 *
	 * @param matrix       the HUD's position matrix (already scaled and at the
	 *                     correct depth)
	 * @param tickDelta    fractional progress of the current tick (0..1)
	 */
	public void onRender(Matrix4f matrix, float tickDelta) {
	}

}
