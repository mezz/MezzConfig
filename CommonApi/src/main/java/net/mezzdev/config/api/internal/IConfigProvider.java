package net.mezzdev.config.api.internal;

import net.mezzdev.config.api.Configs;
import net.mezzdev.config.api.IConfigRegistration;
import net.mezzdev.config.api.schema.IConfigSchema;

import java.util.Collection;

/**
 * Runtime provider behind {@link Configs}.
 *
 * @since 0.3.0
 */
public interface IConfigProvider {
	IConfigRegistration createRegistration(String modId);

	Collection<? extends IConfigSchema> getSchemas();
}
