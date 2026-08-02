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
 * list storage formats. Values created with {@link IConfigCategoryBuilder#addList(String, List, IConfigValueSerializer)}
 * use a list serializer that exposes the element serializer this way.
 *
 * @since 0.1.0
 */
public interface IConfigListValueSerializer<T> extends IConfigValueSerializer<List<T>> {
	/**
	 * Get the serializer for each list element.
	 *
	 * @since 0.1.0
	 */
	IConfigValueSerializer<T> getElementSerializer();
}
