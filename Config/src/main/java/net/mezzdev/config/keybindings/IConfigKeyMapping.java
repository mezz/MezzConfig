package net.mezzdev.config.keybindings;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * A key mapping that can be edited by a config screen.
 */
public interface IConfigKeyMapping {
	/**
	 * Get the stable name of this key mapping.
	 */
	String getName();

	/**
	 * Get the localized display name for this key mapping.
	 */
	Component getLocalizedName();

	/**
	 * Get a localized description of where this key mapping is active.
	 */
	Component getLocalizedContext();

	/**
	 * Get the localized description for this key mapping.
	 */
	Component getLocalizedDescription();

	/**
	 * Get the current binding value.
	 */
	ConfigKeyBinding getValue();

	/**
	 * Get the default binding value.
	 */
	ConfigKeyBinding getDefaultValue();

	/**
	 * Normalize a binding before display or storage.
	 */
	ConfigKeyBinding normalize(ConfigKeyBinding value);

	/**
	 * Set the binding value.
	 */
	void set(ConfigKeyBinding value);

	/**
	 * Get the localized display name for a binding value.
	 */
	Component getValueName(ConfigKeyBinding value);

	/**
	 * Get the modifier represented by the given key, if any.
	 */
	ConfigKeyModifier getKeyModifier(String keyName);

	/**
	 * Get display information for key mappings that conflict with the given value.
	 */
	@Unmodifiable
	List<ConfigKeyMappingConflict> getConflicts(ConfigKeyBinding value);
}
