/**
 * Import settings and persistent sort orders from files that a mod used before adopting MezzConfig.
 * <p>
 * Declare the destination values, then register the old file locations and a parser on the schema builder:
 * <pre>{@code
 * IConfigRegistration registration = Configs.forMod("example");
 * IConfigSchemaBuilder schema = registration.createClientSchemaBuilder("example.ini", "config.example");
 * IConfigValue<Boolean> enabled = schema.addCategory("general")
 *     .addBoolean("enabled", true)
 *     .build();
 *
 * schema.setLegacyMigration(List.of(oldConfigPath), (path, migration) -> {
 *     List<String> oldLines = Files.readAllLines(path);
 *     migration.set(enabled, oldLines.contains("enabled=true"));
 * });
 * schema.build();
 * }</pre>
 * The parser only needs to understand the old format. MezzConfig backs up the source, validates all migrated values, and
 * writes its current format without exposing that format to the mod.
 * <p>
 * To load a MezzConfig file from an older location, use
 * {@link IConfigSchemaBuilder#setLegacySources(List)}. To rename, move, or convert a value while importing that legacy
 * source, use the legacy methods on {@link IConfigValueBuilder}.
 */
@NullMarked
package net.mezzdev.config.api.migration;

import net.mezzdev.config.api.schema.builder.IConfigSchemaBuilder;
import net.mezzdev.config.api.value.builder.IConfigValueBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.List;
