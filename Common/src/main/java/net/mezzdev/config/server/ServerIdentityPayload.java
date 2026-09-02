package net.mezzdev.config.server;

import net.mezzdev.config.util.ErrorUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record ServerIdentityPayload(UUID serverId) implements CustomPacketPayload {
	public static final Type<ServerIdentityPayload> TYPE = new Type<>(
		ResourceLocation.fromNamespaceAndPath("mezz_config", "server_identity")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, ServerIdentityPayload> STREAM_CODEC = StreamCodec.ofMember(ServerIdentityPayload::write, ServerIdentityPayload::new);

	public ServerIdentityPayload {
		serverId = ErrorUtil.checkNotNull(serverId, "serverId");
	}

	private ServerIdentityPayload(RegistryFriendlyByteBuf buffer) {
		this(buffer.readUUID());
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeUUID(serverId);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
