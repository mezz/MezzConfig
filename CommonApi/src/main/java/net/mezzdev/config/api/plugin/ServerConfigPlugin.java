package net.mezzdev.config.api.plugin;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Lets MezzConfig discover server config plugins on Forge and NeoForge.
 * Annotated {@link IServerConfigPlugin} implementations must have a public constructor with no arguments and must be
 * safe to load on a dedicated server.
 *
 * @since 0.2.0
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface ServerConfigPlugin {
}
