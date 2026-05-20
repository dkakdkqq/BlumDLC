package dev.blumdlc.client.modules;

public enum Category {

	COMBAT("Combat"),
	MOVEMENT("Movement"),
	RENDER("Visual"),
	PLAYER("Player"),
	UTIL("Util"),
	THEMES("Themes");

	public final String displayName;

	Category(String displayName) {
		this.displayName = displayName;
	}

}
