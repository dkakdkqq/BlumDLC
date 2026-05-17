package dev.blumdlc.client.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

	public void registerDefaults() {
		// Combat (matches the Celestial-style screenshot)
		register(new Module("Auto Explosion", "Detonates crystals after placement",        Category.COMBAT));
		register(new Module("Friend Protect",  "Prevents damage to your friends",           Category.COMBAT));
		register(new Module("Auto GApple",     "Eats golden apples when fighting",          Category.COMBAT));
		register(new Module("Attack Aura",     "Auto-attacks nearby targets in range",      Category.COMBAT));
		register(new Module("Auto Totem",      "Switches a totem into off-hand on damage",  Category.COMBAT).defaultOn());
		register(new Module("Keep Sprint",     "Keeps sprinting through hits",              Category.COMBAT));
		register(new Module("Tape Mouse",      "Holds left click while attack key is down", Category.COMBAT));
		register(new Module("Trigger Bot",     "Auto-attacks when crosshair is on a target",Category.COMBAT));
		register(new Module("Auto Swap",       "Swaps to a weapon on first hit",            Category.COMBAT).defaultOn());
		register(new Module("Hit Sounds",      "Plays a sound on every hit",                Category.COMBAT).defaultOn());
		register(new Module("Super Bow",       "Boosts the damage of charged shots",        Category.COMBAT));
		register(new Module("Bow Spam",        "Spams arrows with reduced charge time",     Category.COMBAT));
		register(new Module("Fast Swap",       "Eliminates main-hand swap delay",           Category.COMBAT));
		register(new Module("Auto Pot",        "Drinks healing potions when at low HP",     Category.COMBAT));

		// Movement
		register(new Module("Sprint",          "Auto-sprint while moving forward",          Category.MOVEMENT).defaultOn());
		register(new Module("No Slow",         "Removes movement slowdown effects",         Category.MOVEMENT));
		register(new Module("Speed",           "Increases walking speed",                   Category.MOVEMENT));
		register(new Module("Long Jump",       "Jumps further on input",                    Category.MOVEMENT));
		register(new Module("Step",            "Walks up full blocks without jumping",      Category.MOVEMENT));

		// Render
		register(new Module("Full Bright",     "Maximum brightness everywhere",             Category.RENDER));
		register(new Module("HUD",             "On-screen overlay with active modules",     Category.RENDER).defaultOn());
		register(new Module("View Model",      "Customizes first-person item model",        Category.RENDER));
		register(new Module("Blur",            "Adds a blur behind in-game menus",          Category.RENDER).defaultOn());
		register(new Module("No Hurt Cam",     "Removes the screen shake when hurt",        Category.RENDER));

		// Player
		register(new Module("Anti Knockback",  "Reduces incoming knockback",                Category.PLAYER));
		register(new Module("Auto Eat",        "Eats food when your hunger is low",         Category.PLAYER));
		register(new Module("Auto Respawn",    "Auto-clicks respawn after death",           Category.PLAYER));
		register(new Module("Inventory Move",  "Lets you walk while inventory is open",     Category.PLAYER));

		// Util
		register(new Module("Auto Reconnect",  "Reconnects to the last server on kick",     Category.UTIL));
		register(new Module("Click GUI",       "Opens this menu",                           Category.UTIL).defaultOn());
		register(new Module("Notifications",   "Pop-ups when modules toggle",               Category.UTIL).defaultOn());
		register(new Module("Discord RPC",     "Shows the client in your Discord status",   Category.UTIL));
	}

}
