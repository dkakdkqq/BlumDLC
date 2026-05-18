package dev.blumdlc.client.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.joml.Matrix4f;

import dev.blumdlc.client.modules.impl.AttackAura;
import dev.blumdlc.client.modules.impl.TargetESP;

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
			.filter(m -> m.category == category)
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

	/**
	 * Per-frame HUD render hook: lets enabled modules draw screen-space
	 * overlays (e.g. TargetESP) using the project's Builder API.
	 */
	public void render(Matrix4f matrix, float tickDelta) {
		for (Module m : this.modules) {
			if (m.enabled) {
				m.onRender(matrix, tickDelta);
			}
		}
	}

	/**
	 * Registers the modules that actually exist in code today.
	 * Anything without a real implementation is intentionally not listed here.
	 */
	public void registerDefaults() {
		// Combat
		AttackAura aura = new AttackAura();
		register(aura);

		// Visual
		register(new TargetESP(aura));

		// Util
		register(new Module("ClickGUI", "Opens this menu", Category.UTIL).defaultOn());
	}

}
