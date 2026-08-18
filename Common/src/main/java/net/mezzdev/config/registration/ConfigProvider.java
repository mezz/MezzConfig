package net.mezzdev.config.registration;

import net.mezzdev.config.api.IConfigRegistration;
import net.mezzdev.config.api.internal.IConfigProvider;
import net.mezzdev.config.api.schema.ConfigOwnership;
import net.mezzdev.config.api.schema.ConfigScope;
import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.api.schema.IConfigSchemaBuilder;
import net.mezzdev.config.api.sorting.ISortingConfig;
import net.mezzdev.config.file.ConfigManager;
import net.mezzdev.config.client.ClientWorldConfigPathUtil;
import net.mezzdev.config.schema.ClientWorldConfigSchemaPathResolver;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.schema.ConfigSchemaBuilder;
import net.mezzdev.config.schema.ConfigSchemaPathResolver;
import net.mezzdev.config.schema.LayeredConfigSchemaPathResolver;
import net.mezzdev.config.schema.StaticConfigSchemaPathResolver;
import net.mezzdev.config.server.ServerConfigKey;
import net.mezzdev.config.server.ServerConfigPathResolver;
import net.mezzdev.config.sorting.SortingConfig;
import net.mezzdev.config.util.ErrorUtil;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.ServiceLoader;

public final class ConfigProvider implements IConfigProvider {
	private static final ConfigPhysicalSideProvider PHYSICAL_SIDE_PROVIDER = loadPhysicalSideProvider();
	private static final boolean CLIENT_CONFIGS_AVAILABLE = PHYSICAL_SIDE_PROVIDER.isPhysicalClient();
	private static final ConfigManager CONFIG_MANAGER = createConfigManager();

	public static ConfigManager getConfigManager() {
		return CONFIG_MANAGER;
	}

	@Override
	public Collection<? extends IConfigSchema> getSchemas() {
		return CONFIG_MANAGER.getSchemas();
	}

	@Override
	public IConfigRegistration createRegistration(String modId) {
		return createRegistration(PHYSICAL_SIDE_PROVIDER.getConfigRoot(), modId);
	}

	@Override
	public IConfigRegistration createRegistration(Path configRootDir, String modId) {
		configRootDir = ErrorUtil.checkNotNull(configRootDir, "configRootDir").normalize();
		modId = validateModDirectory(modId);
		Path modDirectory = configRootDir.resolve(modId).normalize();
		return new Registration(modId, modDirectory, getConfigManager());
	}

	private static String validateModDirectory(String modId) {
		modId = ConfigSchema.validateModId(modId);
		Path relativeModDirectory = Path.of(modId).normalize();
		if (relativeModDirectory.isAbsolute() ||
			relativeModDirectory.startsWith("..") ||
			relativeModDirectory.getNameCount() != 1 ||
			relativeModDirectory.toString().isEmpty()
		) {
			throw new IllegalArgumentException("modId must identify a directory inside the config root: " + modId);
		}
		return modId;
	}

	private record Registration(String modId, Path modDirectory, ConfigManager configManager)
		implements
			IConfigRegistration {
		@Override
		public IConfigSchemaBuilder createClientSchemaBuilder(String configFileName, String localizationPath) {
			return createSchemaBuilder(configFileName, localizationPath, ConfigOwnership.CLIENT);
		}

		@Override
		public IConfigSchemaBuilder createServerSchemaBuilder(String configFileName, String localizationPath) {
			return createSchemaBuilder(configFileName, localizationPath, ConfigOwnership.SERVER);
		}

		private IConfigSchemaBuilder createSchemaBuilder(
			String configFileName,
			String localizationPath,
			ConfigOwnership ownership
		) {
			localizationPath = ErrorUtil.checkNotNull(localizationPath, "localizationPath");
			Path relativeConfigFile = getRelativeConfigFile(configFileName);
			String normalizedFileName = relativeConfigFile.toString().replace(File.separatorChar, '/');
			if (ownership == ConfigOwnership.CLIENT && !CLIENT_CONFIGS_AVAILABLE) {
				return new ConfigSchemaBuilder(
					modId,
					ignored -> Optional::empty,
					localizationPath,
					configManager,
					ownership,
					normalizedFileName,
					false
				);
			}
			return new ConfigSchemaBuilder(
				modId,
				scope -> createPathResolver(ownership, scope, relativeConfigFile, normalizedFileName),
				localizationPath,
				configManager,
				ownership,
				normalizedFileName
			);
		}

		private ConfigSchemaPathResolver createPathResolver(
			ConfigOwnership ownership,
			ConfigScope scope,
			Path relativeConfigFile,
			String normalizedFileName
		) {
			String ownershipDirectoryName;
			if (ownership == ConfigOwnership.CLIENT) {
				ownershipDirectoryName = "client";
			} else {
				ownershipDirectoryName = "server";
			}
			Path ownershipDirectory = modDirectory.resolve(ownershipDirectoryName);
			if (scope == ConfigScope.INSTALLATION) {
				return new StaticConfigSchemaPathResolver(ownershipDirectory.resolve(relativeConfigFile).normalize());
			}
			if (ownership == ConfigOwnership.CLIENT) {
				Path defaultConfigFile = ClientWorldConfigPathUtil.getDefaultWorldPath(ownershipDirectory)
					.resolve(relativeConfigFile)
					.normalize();
				ClientWorldConfigSchemaPathResolver activePathResolver = new ClientWorldConfigSchemaPathResolver(
					relativeConfigFile,
					() -> ClientWorldConfigPathUtil.getWorldPath(ownershipDirectory)
				);
				return new LayeredConfigSchemaPathResolver(defaultConfigFile, activePathResolver);
			}
			ServerConfigKey key = new ServerConfigKey(modId, normalizedFileName);
			Path defaultConfigFile = ownershipDirectory.resolve("world")
				.resolve("default")
				.resolve(relativeConfigFile)
				.normalize();
			return new ServerConfigPathResolver(key, relativeConfigFile, defaultConfigFile);
		}

		@Override
		public ISortingConfig<String> createSortingConfig(
			String configFileName,
			Comparator<String> defaultSortOrder,
			boolean allowsRemovingValues
		) {
			Path relativeConfigFile = getRelativeConfigFile(configFileName);
			if (!CLIENT_CONFIGS_AVAILABLE) {
				return SortingConfig.inMemory(defaultSortOrder, allowsRemovingValues);
			}
			Path configFile = modDirectory.resolve("client")
				.resolve(relativeConfigFile)
				.normalize();
			return new SortingConfig(configFile, defaultSortOrder, allowsRemovingValues);
		}

	}

	private static ConfigManager createConfigManager() {
		ConfigManager configManager = new ConfigManager("MezzConfig File Watcher");
		configManager.startWatching();
		return configManager;
	}

	private static ConfigPhysicalSideProvider loadPhysicalSideProvider() {
		Iterator<ConfigPhysicalSideProvider> providers = ServiceLoader.load(
				ConfigPhysicalSideProvider.class,
				ConfigProvider.class.getClassLoader()
			)
			.iterator();
		if (!providers.hasNext()) {
			throw new IllegalStateException("MezzConfig physical-side provider is not present.");
		}
		ConfigPhysicalSideProvider provider = providers.next();
		if (providers.hasNext()) {
			throw new IllegalStateException("More than one MezzConfig physical-side provider is present.");
		}
		return provider;
	}

	private static Path getRelativeConfigFile(String configFileName) {
		configFileName = ErrorUtil.checkNotNull(configFileName, "configFileName");
		if (configFileName.isBlank()) {
			throw new IllegalArgumentException("configFileName must not be blank.");
		}
		Path relativeConfigFile = Path.of(configFileName).normalize();
		if (relativeConfigFile.isAbsolute() ||
			relativeConfigFile.startsWith("..") ||
			relativeConfigFile.toString().isEmpty()
		) {
			throw new IllegalArgumentException(
				"configFileName must be a relative path inside the mod's config directory: " + configFileName
			);
		}
		return relativeConfigFile;
	}
}
