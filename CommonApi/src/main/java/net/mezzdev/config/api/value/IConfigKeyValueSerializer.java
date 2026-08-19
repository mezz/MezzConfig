package net.mezzdev.config.api.value;

import net.mezzdev.config.api.schema.IConfigCategoryBuilder;

import java.util.List;
import java.util.Map;

/**
 * Serialization and validation helper for a config value that represents a key-value entry.
 * <p>
 * Use this when integrations should be able to edit both components independently while preserving the entry's
 * type and its serialized format. For example, an ordered list can use this as its element serializer to expose
 * map-style rows without changing the config value to a {@link Map}.
 * <p>
 * Pass an implementation to {@link IConfigCategoryBuilder#addValue(String, Object, IConfigValueSerializer)} or
 * {@link IConfigCategoryBuilder#addList(String, List, IConfigValueSerializer)}.
 * <p>
 * For every entry accepted by {@link #isValid(Object)}, the extracted key and value must be accepted by their component
 * serializers. Rebuilding an entry from those extracted components must return an equal entry. {@link #createEntry(Object,
 * Object)} must not throw for components accepted by their serializers, and the returned entry must preserve the supplied
 * components; integrations may still use {@link #isValid(Object)} to reject combinations that are not valid together.
 *
 * @param <T> entry type
 * @param <K> key type
 * @param <V> component value type
 *
 * @since 0.1.0
 */
public interface IConfigKeyValueSerializer<T, K, V> extends IConfigValueSerializer<T> {
	/**
	 * Get the serializer for the key.
	 *
	 * @return key serializer
	 *
	 * @since 0.1.0
	 */
	IConfigValueSerializer<K> getKeySerializer();

	/**
	 * Get the serializer for the component value.
	 *
	 * @return component value serializer
	 *
	 * @since 0.1.0
	 */
	IConfigValueSerializer<V> getValueSerializer();

	/**
	 * Get the key from the entry.
	 *
	 * @param entry key-value entry
	 * @return key component
	 *
	 * @since 0.1.0
	 */
	K getKey(T entry);

	/**
	 * Get the component value from the entry.
	 *
	 * @param entry key-value entry
	 * @return value component
	 *
	 * @since 0.1.0
	 */
	V getValue(T entry);

	/**
	 * Create an entry from edited key and value components.
	 * This must return without throwing when both components are accepted by their serializers.
	 *
	 * @param key key component
	 * @param value value component
	 * @return key-value entry
	 *
	 * @since 0.1.0
	 */
	T createEntry(K key, V value);
}
