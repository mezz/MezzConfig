package net.mezzdev.config.minecraft;

import net.mezzdev.config.registration.ConfigPhysicalSideProvider;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.Optional;

/** Minecraft services shared by the loader-specific environment providers. */
public abstract class MinecraftConfigEnvironment implements ConfigPhysicalSideProvider {
	@Override
	public final Optional<Path> getClientWorldPath(Path configDirectory) {
		if (!isPhysicalClient()) {
			return Optional.empty();
		}
		return MinecraftClientWorldPath.getWorldPath(configDirectory);
	}

	@Override
	public final boolean hasTranslation(String key) {
		return Language.getInstance().has(key);
	}

	@Override
	public final String translate(String key, Object... arguments) {
		return Component.translatable(key, arguments).getString();
	}
}
