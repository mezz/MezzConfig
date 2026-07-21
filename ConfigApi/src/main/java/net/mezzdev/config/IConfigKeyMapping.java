package net.mezzdev.config;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * A key mapping that can be edited by a config screen.
 *
 * @since 19.39.0
 */
public interface IConfigKeyMapping {
	/**
	 * Get the backing Minecraft key mapping.
	 *
	 * @since 19.39.0
	 */
	KeyMapping getKeyMapping();

	/**
	 * Get a localized description of where this key mapping is active.
	 *
	 * @since 19.39.0
	 */
	Component getContext();

	/**
	 * Get the localized description for this key mapping.
	 *
	 * @since 19.39.0
	 */
	Component getDescription();

	/**
	 * Get the current binding value.
	 *
	 * @since 19.39.0
	 */
	ConfigKeyBinding getValue();

	/**
	 * Get the default binding value.
	 *
	 * @since 19.39.0
	 */
	ConfigKeyBinding getDefaultValue();

	/**
	 * Normalize a binding before display or storage.
	 *
	 * @since 19.39.0
	 */
	ConfigKeyBinding normalize(ConfigKeyBinding value);

	/**
	 * Set the binding value.
	 *
	 * @since 19.39.0
	 */
	void set(ConfigKeyBinding value);

	/**
	 * Get the localized display name for a binding value.
	 *
	 * @since 19.39.0
	 */
	Component getValueName(ConfigKeyBinding value);

	/**
	 * Get the modifier represented by the given key, if any.
	 *
	 * @since 19.39.0
	 */
	ConfigKeyModifier getKeyModifier(InputConstants.Key key);

	/**
	 * Get display information for key mappings that conflict with the given value.
	 *
	 * @since 19.39.0
	 */
	@Unmodifiable
	List<ConfigKeyMappingConflict> getConflicts(ConfigKeyBinding value);
}
