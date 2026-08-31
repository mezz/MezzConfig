/**
 * Persist user-defined ordering for values that a mod discovers at runtime.
 * <p>
 * Use a sorting config when the complete set of values is not known while declaring a schema, such as plugins, recipes,
 * or dynamically registered content. Register an
 * {@link net.mezzdev.config.api.sorting.ISortingConfigMigrator} when importing an order from an older file format.
 */
@NullMarked
package net.mezzdev.config.api.sorting;

import org.jspecify.annotations.NullMarked;
