package net.mezzdev.config.value;

import java.util.List;

/**
 * Serialization and validation helper for list config values.
 *
 * @since 19.39.0
 */
public interface IConfigListValueSerializer<T> extends IConfigValueSerializer<List<T>> {
	@Override
	default ConfigValueEditorType<List<T>> getEditorType() {
		return ConfigValueEditorTypes.getList();
	}

	/**
	 * Get the serializer for each value in the list.
	 *
	 * @since 19.39.0
	 */
	IConfigValueSerializer<T> getListValueSerializer();
}
