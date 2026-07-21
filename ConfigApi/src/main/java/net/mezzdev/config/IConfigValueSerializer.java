package net.mezzdev.config;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Serialization and validation helper for config values.
 * Get an instance from {@link IConfigValue#getSerializer()}
 *
 * Note that if this value is a {@link List}
 * it should implement {@link IConfigListValueSerializer} as well.
 *
 * @since 19.39.0
 */
public interface IConfigValueSerializer<T> {
	/**
	 * Serialize the config value to a string.
	 *
	 * @since 19.39.0
	 */
	String serialize(T value);

	/**
	 * Deserialize the config value from a string.
	 *
	 * @since 19.39.0
	 */
	IDeserializeResult<T> deserialize(String string);

	/**
	 * Check if a given value is valid for this config value.
	 *
	 * @since 19.39.0
	 */
	boolean isValid(T value);

	/**
	 * If this config value only has a limited number of valid values,
	 * this returns them all.
	 *
	 * If there are many or unlimited valid values, this will return
	 * {@link Optional#empty()}
	 *
	 * @since 19.39.0
	 */
	@Unmodifiable
	Optional<Collection<T>> getAllValidValues();

	/**
	 * Get the kind of editor that config screens should use for values serialized by this helper.
	 *
	 * @since 19.39.0
	 */
	default ConfigValueEditorType<T> getEditorType() {
		if (getAllValidValues().isPresent()) {
			return ConfigValueEditorTypes.selection();
		}
		return ConfigValueEditorTypes.unsupported();
	}

	/**
	 * Get the translated name component for a value option serialized by this helper.
	 *
	 * @param configValueLocalizationKey the translation key for the owning config value's name
	 *
	 * @since 19.39.0
	 */
	Component getLocalizedValueName(String configValueLocalizationKey, T value);

	/**
	 * Get the translated description component for a value option serialized by this helper.
	 *
	 * @param configValueLocalizationKey the translation key for the owning config value's name
	 *
	 * @since 19.39.0
	 */
	default Optional<Component> getLocalizedValueDescription(String configValueLocalizationKey, T value) {
		return Optional.empty();
	}

	/**
	 * Get the sprite icon for a value option serialized by this helper.
	 *
	 * @since 19.39.0
	 */
	default Optional<ResourceLocation> getValueIcon(T value) {
		return Optional.empty();
	}

	/**
	 * Get the description of what values are valid for this config value.
	 *
	 * @since 19.39.0
	 */
	String getValidValuesDescription();

	/**
	 * The result of {@link #deserialize}.
	 * If deserialization is successful, {@link #getResult()} will return a value.
	 * Otherwise, a list of errors can be fetched from {@link #getErrors()}.
	 *
	 * @since 19.39.0
	 */
	interface IDeserializeResult<T> {
		/**
		 * The successful deserialization result,
		 * or {@link Optional#empty()} if deserialzation failed.
		 *
		 * @since 19.39.0
		 */
		Optional<T> getResult();

		/**
		 * A list of errors during deserialization, if it failed.
		 * On successful deserialization this will be an empty list.
		 *
		 * @since 19.39.0
		 */
		List<String> getErrors();
	}
}
