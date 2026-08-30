package net.mezzdev.config.api.value;

import net.mezzdev.config.api.schema.IConfigCategoryBuilder;

import java.util.List;

/**
 * Serialization and validation helper for list config values.
 * <p>
 * Use this when code needs to understand the list elements individually, for example to render each element,
 * validate newly-added elements, or reorder values while preserving the list's storage format.
 * <p>
 * Pass your serializer to {@link IConfigCategoryBuilder#addValue(String, Object, IConfigValueSerializer)} for custom
 * list validation and editor metadata. Values created with
 * {@link IConfigCategoryBuilder#addList(String, List, IConfigValueSerializer)} use a list serializer that exposes the
 * element serializer this way.
 * Element serializers may implement {@link IConfigKeyValueSerializer} to expose map-style rows without changing the
 * list to a map.
 * <p>
 * MezzConfig persists and synchronizes lists as structured arrays by applying {@link #getElementSerializer()} to each
 * element recursively. It calls {@link IConfigValueSerializer#isValid(Object)} on the reconstructed complete list. The
 * container-level {@link IConfigValueSerializer#serialize(Object)} representation is not used for structured storage.
 * <p>
 * MezzConfig stores list containers as unmodifiable snapshots. Each element type must still satisfy the effectively
 * immutable value contract from {@link IConfigValueSerializer}.
 *
 * @since 0.1.0
 */
public interface IConfigListValueSerializer<T> extends IConfigValueSerializer<List<T>> {
	/**
	 * Get whether the order of entries changes the meaning of this config value.
	 * <p>
	 * MezzConfig preserves the physical order in the config file either way. Integrations can use this metadata to
	 * decide whether reordering controls are useful.
	 *
	 * @return ordering semantics for this list
	 *
	 * @since 0.1.0
	 */
	default ConfigListOrdering getOrdering() {
		return ConfigListOrdering.ORDERED;
	}

	/**
	 * Get the serializer for each list element.
	 *
	 * @since 0.1.0
	 */
	IConfigValueSerializer<T> getElementSerializer();
}
