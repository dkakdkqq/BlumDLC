package dev.blumdlc.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.blumdlc.client.modules.ModuleManager;
import net.fabricmc.api.ModInitializer;

public final class BlumDLC implements ModInitializer {

	public static final String MOD_ID = "blumdlc";
	public static final String NAME = "Blum";
	public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

	public static final ModuleManager MODULES = new ModuleManager();

	@Override
	public void onInitialize() {
		MODULES.registerDefaults();
		LOGGER.info("Loaded {} modules", MODULES.all().size());
	}

}
