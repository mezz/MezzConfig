/**
 * Import settings from config files that a mod used before adopting MezzConfig.
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
 * To rename, move, or convert a value already stored by MezzConfig, use the legacy methods on
 * {@link net.mezzdev.config.api.value.IConfigValueBuilder} instead.
 */
@NullMarked
package net.mezzdev.config.api.migration;

import org.jspecify.annotations.NullMarked;
