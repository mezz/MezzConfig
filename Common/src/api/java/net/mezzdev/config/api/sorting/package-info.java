/**
 * Persist user-defined ordering for values that a mod discovers at runtime.
 * <p>
 * {@link ISortingConfig} is the main interface. Use it when the complete set of values is not known while declaring a
 * schema, such as plugins, recipes, or dynamically registered content. Use {@link ISortingConfigMigrator} to import an
 * older order format.
 */
@NullMarked
package net.mezzdev.config.api.sorting;

import net.mezzdev.config.api.migration.ISortingConfigMigrator;
import org.jspecify.annotations.NullMarked;
