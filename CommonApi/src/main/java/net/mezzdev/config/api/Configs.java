package net.mezzdev.config.api;

import net.mezzdev.config.api.schema.IConfigSchema;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Entry point for registering configs with MezzConfig.
 * <p>
 * Get an {@link IConfigRegistration} with {@link #forMod(String)}, then use it to create config schemas or persistent sort
 * orders. Config screens and other integrations can discover built schemas with {@link #getSchemas()}.
 *
 * @since 0.3.0
 */
public final class Configs {
	private Configs() {}

	/**
	 * Get a registration for config schemas and sort orders owned by a mod.
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
	 * Get all config schemas that have been built and registered.
	 *
	 * @return an unmodifiable snapshot of registered schemas
	 *
	 * @since 0.3.0
	 */
	@Unmodifiable
	public static Collection<? extends IConfigSchema> getSchemas() {
		return ProviderHolder.PROVIDER.getSchemas();
	}

	/**
	 * @hidden
	 */
	@ApiStatus.Internal
	public interface IConfigProvider {
		IConfigRegistration createRegistration(String modId);

		Collection<? extends IConfigSchema> getSchemas();
	}

	/**
	 * @hidden
	 */
	@ApiStatus.Internal
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
