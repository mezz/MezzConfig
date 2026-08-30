/**
 * Define, read, update, validate, and observe individual config values.
 * <p>
 * Prefer the built-in value types on {@link net.mezzdev.config.api.schema.IConfigCategoryBuilder}. Implement
 * {@link net.mezzdev.config.api.value.IConfigValueSerializer} only when a mod needs to store its own value type.
 */
@NullMarked
package net.mezzdev.config.api.value;

import org.jspecify.annotations.NullMarked;
