package net.mezzdev.config.api.value.serializer;

import net.mezzdev.config.api.schema.builder.IConfigCategoryBuilder;

import java.util.List;

/**
 * Describes a list whose elements config editors can validate, add, remove, or reorder individually.
 * <p>
 * Built-in list helpers already provide this metadata. Implement it for custom list validation or when declaring a list
 * through {@link IConfigCategoryBuilder#addValue(String, Object, IConfigValueSerializer)}. Element serializers may also
 * implement {@link IConfigKeyValueSerializer} to present entries as editable key-value rows.
 * <p>
 * MezzConfig stores the list element-by-element with {@link #getElementSerializer()}; the inherited container-level text
 * representation is not used for structured file storage. Returned list values are unmodifiable snapshots, and their
 * elements must follow the immutable value contract.
 *
 * @since 0.1.0
 */
public interface IConfigListValueSerializer<T> extends IConfigValueSerializer<List<T>> {
	/**
	 * Tell config editors whether reordering entries changes the setting's meaning.
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
	 * Get the serializer config editors should use for individual elements.
	 *
	 * @since 0.1.0
	 */
	IConfigValueSerializer<T> getElementSerializer();
}
