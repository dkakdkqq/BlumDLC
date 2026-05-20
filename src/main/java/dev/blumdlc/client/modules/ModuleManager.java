package dev.blumdlc.client.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.joml.Matrix4f;

import dev.blumdlc.client.modules.impl.AntiAFK;
import dev.blumdlc.client.modules.impl.ArrayListHud;
import dev.blumdlc.client.modules.impl.AttackAura;
import dev.blumdlc.client.modules.impl.AutoEat;
import dev.blumdlc.client.modules.impl.AutoRespawn;
import dev.blumdlc.client.modules.impl.AutoTotem;
import dev.blumdlc.client.modules.impl.AutoWalk;
import dev.blumdlc.client.modules.impl.CoordsHud;
import dev.blumdlc.client.modules.impl.EntityESP;
import dev.blumdlc.client.modules.impl.FullBright;
import dev.blumdlc.client.modules.impl.HudSettings;
import dev.blumdlc.client.modules.impl.KeybindsHud;
import dev.blumdlc.client.modules.impl.NoFall;
import dev.blumdlc.client.modules.impl.PotionsHud;
import dev.blumdlc.client.modules.impl.Sprint;
import dev.blumdlc.client.modules.impl.StaffHud;
import dev.blumdlc.client.modules.impl.TargetESP;
import dev.blumdlc.client.modules.impl.TargetHud;
import dev.blumdlc.client.modules.impl.Themes;
import dev.blumdlc.client.modules.impl.Tracers;
import dev.blumdlc.client.modules.impl.Trails;
import dev.blumdlc.client.modules.impl.Watermark;

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

		// Movement
		register(new Sprint());
		register(new AutoWalk());
		register(new AntiAFK());
		register(new NoFall());

		// Player
		register(new AutoTotem());
		register(new AutoEat());
		register(new AutoRespawn());

		// Visual
		register(new TargetESP(aura));
		register(new TargetHud(aura));
		register(new EntityESP());
		register(new Tracers());
		register(new FullBright());
		register(new Watermark());
		register(new KeybindsHud());
		register(new PotionsHud());
		register(new StaffHud());
		register(new ArrayListHud());
		register(new CoordsHud());
		register(new Trails());

		// Visual — HUD toggle panel (must be registered after all HudModules)
		HudSettings hudSettings = new HudSettings();
		register(hudSettings);
		hudSettings.populate();

		// Util
		register(new Module("ClickGUI", "Opens this menu", Category.UTIL).defaultOn());

		// Themes — picker for the client accent colour
		register(new Themes());
	}

}
