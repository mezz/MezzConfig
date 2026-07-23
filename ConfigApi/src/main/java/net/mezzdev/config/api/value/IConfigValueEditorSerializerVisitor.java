package net.mezzdev.config.api.value;

import java.util.List;

/**
 * Visitor for the built-in editor contracts supported by config value serializers.
 *
 * @param <R> result type returned by the visitor
 *
 * @since 19.39.0
 */
public interface IConfigValueEditorSerializerVisitor<R> {
	/**
	 * Visit a boolean config value serializer.
	 *
	 * @param configValue config value being edited
	 * @param serializer serializer for the config value
	 *
	 * @since 19.39.0
	 */
	R visitBoolean(IConfigValue<Boolean> configValue, IConfigValueEditorSerializer<Boolean> serializer);

	/**
	 * Visit an integer config value serializer.
	 *
	 * @param configValue config value being edited
	 * @param serializer serializer for the config value
	 *
	 * @since 19.39.0
	 */
	R visitInteger(IConfigValue<Integer> configValue, IConfigIntegerValueSerializer serializer);

	/**
	 * Visit a list config value serializer.
	 *
	 * @param configValue config value being edited
	 * @param serializer serializer for the config value
	 * @param <T> element type of the list config value
	 *
	 * @since 19.39.0
	 */
	<T> R visitList(IConfigValue<List<T>> configValue, IConfigListValueSerializer<T> serializer);

	/**
	 * Visit a config value serializer with a finite set of valid values.
	 *
	 * @param configValue config value being edited
	 * @param serializer serializer for the config value
	 * @param <T> value type of the config value
	 *
	 * @since 19.39.0
	 */
	<T> R visitSelection(IConfigValue<T> configValue, IConfigValueEditorSerializer<T> serializer);

	/**
	 * Visit a config value serializer backed by a custom editor type.
	 *
	 * @param configValue config value being edited
	 * @param serializer serializer for the config value
	 * @param <T> value type of the config value
	 *
	 * @since 19.39.0
	 */
	<T> R visitCustom(IConfigValue<T> configValue, IConfigValueEditorSerializer<T> serializer);
}
