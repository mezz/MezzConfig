package net.mezzdev.config.forge;

import net.mezzdev.config.server.ServerConfigNetworking;
import net.mezzdev.config.server.ServerConfigRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.NetworkDirection;

public final class ConfigForgeClient {
	private ConfigForgeClient() {

	}

	public static void register(ConfigForgeNetwork network) {
		ServerConfigNetworking.setClientSender(payload -> {
			ClientPacketListener listener = Minecraft.getInstance().getConnection();
			if (listener == null) {
				return false;
			}
			Connection connection = listener.getConnection();
			Channel<?> channel = network.getChannel();
			if (!connection.isConnected() || !channel.isRemotePresent(connection)) {
				return false;
			}
			Packet<?> packet = NetworkDirection.PLAY_TO_SERVER.buildPacket(network.getChannel(), payload);
			listener.send(packet);
			return true;
		});
		MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
			if (event.phase == TickEvent.Phase.END) {
				ServerConfigRuntime.onClientTick();
			}
		});
		MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> ServerConfigRuntime.onClientWorldStarted());
		MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ServerConfigRuntime.onClientDisconnect());
	}
}
