package net.mezzdev.config.api.plugin;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Lets MezzConfig discover config plugins on Forge and NeoForge.
 * Annotated {@link IConfigPlugin} implementations must have a public constructor with no arguments.
 *
 * @since 0.1.0
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface ConfigPlugin {
}
