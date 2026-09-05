/**
 * Persist user-defined ordering for values that a mod discovers at runtime.
 * <p>
 * {@link net.mezzdev.config.api.sorting.ISortingConfig} is the main interface. Use it when the complete set of values is
 * not known while declaring a schema, such as plugins, recipes, or dynamically registered content. Use
 * {@link net.mezzdev.config.api.migration.ISortingConfigMigrator} to import an older order format.
 */
@NullMarked
package net.mezzdev.config.api.sorting;

import org.jspecify.annotations.NullMarked;
