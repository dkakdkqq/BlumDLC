package dev.blumdlc.client.ui;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import dev.blumdlc.client.msdf.MsdfFont;

public final class Fonts {

	public static final Supplier<MsdfFont> BIKO = Suppliers.memoize(() ->
		MsdfFont.builder().name("biko").atlas("biko").data("biko").build()
	);

	private Fonts() {
	}

}
