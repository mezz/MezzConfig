package net.mezzdev.config.minecraft.network;

import net.minecraft.resources.ResourceLocation;

final class PacketIds {
	private PacketIds() {}

	static ResourceLocation create(String path) {
		return ResourceLocation.fromNamespaceAndPath("mezz_config", path);
	}
}
