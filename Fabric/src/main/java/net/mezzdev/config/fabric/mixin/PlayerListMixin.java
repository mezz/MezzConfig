package net.mezzdev.config.fabric.mixin;

import com.mojang.authlib.GameProfile;
import net.mezzdev.config.server.ServerConfigRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
	@Shadow
	public abstract @Nullable ServerPlayer getPlayer(UUID playerId);

	@Inject(
		method = {"op", "deop"},
		at = @At("RETURN")
	)
	private void onPermissionsChanged(GameProfile profile, CallbackInfo callbackInfo) {
		ServerPlayer player = getPlayer(profile.getId());
		if (player != null) {
			ServerConfigRuntime.onPlayerPermissionsChanged(player);
		}
	}
}
