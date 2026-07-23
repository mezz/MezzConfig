package net.mezzdev.config.api.schema;

import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.api.value.IConfigValueSource;
import net.minecraft.client.KeyMapping;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Builds one user-facing display category for a config screen.
 *
 * @since 19.39.0
 */
public interface IConfigDisplayCategoryBuilder {
	/**
	 * Add one config value to this display category.
	 *
	 * @param value config value to display
	 *
	 * @since 19.39.0
	 */
	IConfigDisplayCategoryBuilder addValue(IConfigValue<?> value);

	/**
	 * Add config values to this display category.
	 *
	 * @param values config values to display
	 *
	 * @since 19.39.0
	 */
	IConfigDisplayCategoryBuilder addValues(Collection<? extends IConfigValue<?>> values);

	/**
	 * Add a custom value source to this display category.
	 *
	 * @param valueSource source of config values to display
	 *
	 * @since 19.39.0
	 */
	IConfigDisplayCategoryBuilder addValueSource(IConfigValueSource valueSource);

	/**
	 * Add one key mapping to this display category.
	 *
	 * @param keyMapping key mapping to display
	 *
	 * @since 19.39.0
	 */
	IConfigDisplayCategoryBuilder addKeyMapping(KeyMapping keyMapping);

	/**
	 * Add key mappings to this display category.
	 *
	 * @param keyMappings key mappings to display
	 *
	 * @since 19.39.0
	 */
	IConfigDisplayCategoryBuilder addKeyMappings(Collection<? extends KeyMapping> keyMappings);

	/**
	 * Add key mappings that are resolved when the config screen opens.
	 *
	 * @param keyMappingsSupplier supplies the key mappings to display
	 *
	 * @since 19.39.0
	 */
	IConfigDisplayCategoryBuilder addKeyMappings(Supplier<? extends Collection<? extends KeyMapping>> keyMappingsSupplier);
}
