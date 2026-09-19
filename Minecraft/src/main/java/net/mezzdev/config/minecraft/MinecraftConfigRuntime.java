package net.mezzdev.config.minecraft;

import net.mezzdev.config.server.ServerConfigRuntime;
import net.mezzdev.config.util.ErrorUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Adapts game lifecycle and players to the Minecraft-independent config runtime. */
public final class MinecraftConfigRuntime {
	private static volatile @Nullable Sender sender;

	private MinecraftConfigRuntime() {}

	public static void setSender(Sender sender) {
		MinecraftConfigRuntime.sender = ErrorUtil.checkNotNull(sender, "sender");
	}

	public static void onServerStarted(MinecraftServer server) {
		ServerConfigRuntime.onServerStarted(new ServerAdapter(server));
	}

	public static void onPlayerJoin(ServerPlayer player) {
		ServerConfigRuntime.onPlayerJoin(new PlayerAdapter(player));
	}

	public interface Sender {
		boolean sendIdentity(ServerPlayer player, UUID serverId);
		boolean sendSync(ServerPlayer player, byte[] data);
	}

	private record ServerAdapter(MinecraftServer server) implements ServerConfigRuntime.Server {
		@Override
		public Path getWorldRoot() {
			return server.getWorldPath(LevelResource.ROOT);
		}

		@Override
		public void execute(Runnable task) {
			server.execute(task);
		}

		@Override
		public List<PlayerAdapter> getPlayers() {
			return server.getPlayerList().getPlayers().stream().map(PlayerAdapter::new).toList();
		}
	}

	private record PlayerAdapter(ServerPlayer player) implements ServerConfigRuntime.Player {
		@Override
		public String getName() {
			return player.getName().getString();
		}

		@Override
		public boolean sendIdentity(UUID serverId) {
			Sender current = sender;
			return current != null && current.sendIdentity(player, serverId);
		}

		@Override
		public boolean sendSync(byte[] data) {
			Sender current = sender;
			return current != null && current.sendSync(player, data);
		}
	}
}
