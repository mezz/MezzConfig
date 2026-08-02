package net.mezzdev.config.api.value;

/**
 * Serialization, validation, and editor metadata for config values that can be displayed in config screens.
 *
 * @since 19.39.0
 */
public interface IConfigValueEditorSerializer<T> extends IConfigValueSerializer<T> {
	/**
	 * Get the kind of editor that config screens should use for values serialized by this helper.
	 *
	 * @since 19.39.0
	 */
	ConfigValueEditorType<T> getEditorType();
}
