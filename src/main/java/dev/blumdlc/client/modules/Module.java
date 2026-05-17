package dev.blumdlc.client.modules;

import java.util.ArrayList;
import java.util.List;

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

}
