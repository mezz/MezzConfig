/**
 * Inspect and atomically update config schemas.
 * <p>
 * {@link IConfigSchema} is the main runtime interface. Declare schemas through {@link IConfigSchemaBuilder schema
 * builders}, and use the child packages for category discovery and batch updates.
 */
@NullMarked
package net.mezzdev.config.api.schema;

import net.mezzdev.config.api.schema.builder.IConfigSchemaBuilder;
import org.jspecify.annotations.NullMarked;
