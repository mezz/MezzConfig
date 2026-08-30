/**
 * Define how a mod's settings are grouped, stored, and synchronized.
 * <p>
 * Create an {@link net.mezzdev.config.api.schema.IConfigSchemaBuilder} from
 * {@link net.mezzdev.config.api.Configs#forMod(String)}, add storage categories and values, then build it during mod
 * initialization. Keep the returned config value objects to read and update settings at runtime.
 */
@NullMarked
package net.mezzdev.config.api.schema;

import org.jspecify.annotations.NullMarked;
