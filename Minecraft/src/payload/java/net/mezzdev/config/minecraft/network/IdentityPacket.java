package net.mezzdev.config.minecraft.network;

import net.mezzdev.config.server.ServerIdentityPayload;

import net.mezzdev.config.util.ErrorUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public record IdentityPacket(UUID serverId) implements CustomPacketPayload {
	public static final Type<IdentityPacket> TYPE = new Type<>(
		PacketIds.create("server_identity")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, IdentityPacket> STREAM_CODEC = StreamCodec.ofMember(IdentityPacket::write, IdentityPacket::new);

	public IdentityPacket {
		serverId = ErrorUtil.checkNotNull(serverId, "serverId");
	}

	private IdentityPacket(RegistryFriendlyByteBuf buffer) {
		this(buffer.readUUID());
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeUUID(serverId);
	}

	public ServerIdentityPayload toPayload() {
		return new ServerIdentityPayload(serverId);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
