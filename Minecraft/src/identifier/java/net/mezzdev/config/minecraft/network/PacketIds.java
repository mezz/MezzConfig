package net.mezzdev.config.minecraft.network;

import net.minecraft.resources.Identifier;

final class PacketIds {
	private PacketIds() {}

	static Identifier create(String path) {
		return Identifier.fromNamespaceAndPath("mezz_config", path);
	}
}
