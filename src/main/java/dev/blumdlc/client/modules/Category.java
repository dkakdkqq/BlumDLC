package dev.blumdlc.client.modules;

public enum Category {

	COMBAT("Combat"),
	MOVEMENT("Movement"),
	RENDER("Render"),
	PLAYER("Player"),
	UTIL("Util");

	public final String displayName;

	Category(String displayName) {
		this.displayName = displayName;
	}

}
