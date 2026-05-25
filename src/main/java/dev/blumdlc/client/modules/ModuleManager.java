package dev.blumdlc.client.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.joml.Matrix4f;

import dev.blumdlc.client.modules.impl.*;

public final class ModuleManager {

	private final List<Module> modules = new ArrayList<>();

	public Module register(Module module) {
		this.modules.add(module);
		return module;
	}

	public List<Module> all() {
		return this.modules;
	}

	public List<Module> byCategory(Category category) {
		return this.modules.stream()
			.filter(m -> m.category == category && !m.hidden)
			.collect(Collectors.toList());
	}

	public List<Module> search(Category category, String query) {
		List<Module> base = byCategory(category);
		if (query == null || query.isBlank()) {
			return base;
		}
		String low = query.toLowerCase();
		return base.stream()
			.filter(m -> m.name.toLowerCase().contains(low))
			.collect(Collectors.toList());
	}

	public void tick() {
		for (Module m : this.modules) {
			if (m.enabled) {
				m.onTick();
			}
		}
	}

	public void render(Matrix4f matrix, float tickDelta) {
		for (Module m : this.modules) {
			if (m.enabled) {
				m.onRender(matrix, tickDelta);
			}
		}
	}

	/**
	 * Registers all modules.
	 */
	public void registerDefaults() {
		// === Combat ===
		AttackAura aura = new AttackAura();
		register(aura);
		register(new Velocity());
		register(new AutoTotem());
		register(new Criticals());
		register(new Reach());

		// === Movement ===
		register(new Sprint());
		register(new Speed());
		register(new Fly());
		register(new NoSlow());
		register(new Eagle());

		// === Render / Visual ===
		register(new TargetESP(aura));
		register(new TargetHud(aura));
		register(new Watermark());
		register(new KeybindsHud());
		register(new PotionsHud());
		register(new StaffHud());
		register(new Trails());
		register(new ESP());
		register(new FullBright());
		register(new NameTags());
		register(new NoRender());
		register(new Freecam());

		// === Player ===
		register(new NoFall());
		register(new AutoArmor());
		register(new ChestStealer());
		register(new AutoHeal());
		register(new AutoFish());
		register(new ClickTP());

		// === Util ===
		register(new Timer());
		register(new Module("ClickGUI", "Opens this menu", Category.UTIL).defaultOn());

		// === Visual — HUD toggle panel (must be after HudModules) ===
		HudSettings hudSettings = new HudSettings();
		register(hudSettings);
		hudSettings.populate();

		// === Themes ===
		register(new Themes());
	}
}
