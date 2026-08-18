package net.mezzdev.config.api;

import net.mezzdev.config.api.internal.IConfigProvider;
import net.mezzdev.config.api.schema.IConfigSchema;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Entry point for registering config schemas and sort orders.
 *
 * @since 0.3.0
 */
public final class Configs {
	private Configs() {}

	/**
	 * Create config registration for a mod under the conventional {@code config} directory.
	 *
	 * @param modId mod id that owns the registered configs
	 * @return config registration bound to this mod
	 *
	 * @since 0.3.0
	 */
	public static IConfigRegistration forMod(String modId) {
		return ProviderHolder.PROVIDER.createRegistration(modId);
	}

	/**
	 * Get all registered config schemas.
	 *
	 * @return an unmodifiable snapshot of registered schemas
	 *
	 * @since 0.3.0
	 */
	@Unmodifiable
	public static Collection<? extends IConfigSchema> getSchemas() {
		return ProviderHolder.PROVIDER.getSchemas();
	}

	private static final class ProviderHolder {
		private static final IConfigProvider PROVIDER = loadProvider();

		private static IConfigProvider loadProvider() {
			Iterator<IConfigProvider> providers = ServiceLoader.load(
					IConfigProvider.class,
					Configs.class.getClassLoader()
				)
				.iterator();
			if (!providers.hasNext()) {
				throw new IllegalStateException("MezzConfig runtime is not present.");
			}
			IConfigProvider provider = providers.next();
			if (providers.hasNext()) {
				throw new IllegalStateException("More than one MezzConfig runtime is present.");
			}
			return provider;
		}
	}
}
