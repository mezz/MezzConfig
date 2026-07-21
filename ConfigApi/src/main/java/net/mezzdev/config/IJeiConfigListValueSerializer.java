package net.mezzdev.config;

import java.util.List;

/**
 * Serialization and validation helper for JEI config values.
 *
 * @since 12.1.1
 */
public interface IJeiConfigListValueSerializer<T> extends IJeiConfigValueSerializer<List<T>> {
	@Override
	default ConfigValueEditorType<List<T>> getEditorType() {
		return ConfigValueEditorTypes.list();
	}

	/**
	 * Get the serializer for each value in the list.
	 *
	 * @since 12.1.1
	 */
	IJeiConfigValueSerializer<T> getListValueSerializer();
}
