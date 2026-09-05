package net.mezzdev.config.api.value.serializer;

import java.util.Map;

/**
 * Lets config editors treat a mod-specific value as separately editable key and value components.
 * <p>
 * This is useful for map-like entries in an ordered list, where changing the Java type to {@link Map} would lose entry
 * order or other domain data. Each component serializer defines its own editor and validation behavior; rebuilding an
 * unchanged entry must produce an equal value. Every accepted entry must have valid components, and
 * {@link #createEntry(Object, Object)} must accept every component pair allowed by the component serializers.
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
