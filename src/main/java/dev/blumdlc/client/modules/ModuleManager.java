package dev.blumdlc.client.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import dev.blumdlc.client.modules.impl.AttackAura;

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
	 * Registers the modules that actually exist in code today.
	 * Anything without a real implementation is intentionally not listed here.
	 */
	public void registerDefaults() {
		// Combat
		register(new AttackAura());

		// Util
		register(new Module("ClickGUI", "Opens this menu", Category.UTIL).defaultOn());
	}

}
