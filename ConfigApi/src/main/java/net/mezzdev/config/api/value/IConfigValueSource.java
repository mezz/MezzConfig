package net.mezzdev.config.api.value;

import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;

/**
 * A source of config values for a display category.
 * <p>
 * Config includes standard sources for values that already exist and for key mappings. Mods can define custom
 * source types for values that need their own creation logic.
 *
 * @since 19.39.0
 */
public interface IConfigValueSource {
	/**
	 * The type of values provided by this source.
	 *
	 * @since 19.39.0
	 */
	ConfigValueSourceType<?> getType();

	/**
	 * Config values that can be returned without any config screen context.
	 *
	 * @since 19.39.0
	 */
	@Unmodifiable
	default Collection<? extends IConfigValue<?>> getConfigValues() {
		return List.of();
	}
}
