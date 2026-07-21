package mezz.jei.common.config;

import net.mezzdev.config.ConfigValueUpdateType;
import net.mezzdev.config.IConfigCategory;
import net.mezzdev.config.IConfigFile;
import net.mezzdev.config.IConfigListValueSerializer;
import net.mezzdev.config.IConfigManager;
import net.mezzdev.config.IConfigValue;
import net.mezzdev.config.IConfigValueSerializer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Adapts the standalone config API to JEI's deprecated config API.
 */
@SuppressWarnings({"deprecation", "removal"})
public final class ConfigManagerAdapter {
	private ConfigManagerAdapter() {

	}

	public static mezz.jei.api.runtime.config.IJeiConfigManager create(IConfigManager configManager) {
		return new ConfigManager(configManager);
	}

	private static mezz.jei.api.runtime.config.ConfigValueUpdateType toJei(ConfigValueUpdateType updateType) {
		return switch (updateType) {
			case IMMEDIATE -> mezz.jei.api.runtime.config.ConfigValueUpdateType.IMMEDIATE;
			case ON_APPLY -> mezz.jei.api.runtime.config.ConfigValueUpdateType.ON_APPLY;
			case RESTART -> mezz.jei.api.runtime.config.ConfigValueUpdateType.RESTART_JEI;
		};
	}

	private static mezz.jei.api.runtime.config.IJeiConfigValue<?> createConfigValue(IConfigValue<?> configValue) {
		return createTypedConfigValue(configValue);
	}

	private static <T> mezz.jei.api.runtime.config.IJeiConfigValue<T> createTypedConfigValue(IConfigValue<T> configValue) {
		return new ConfigValue<>(configValue);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <T> mezz.jei.api.runtime.config.IJeiConfigValueSerializer<T> createSerializer(
		IConfigValueSerializer<T> serializer,
		String configValueLocalizationKey
	) {
		if (serializer instanceof IConfigListValueSerializer<?> listSerializer) {
			return new ConfigListValueSerializer((IConfigListValueSerializer) listSerializer, configValueLocalizationKey);
		}
		return new ConfigValueSerializer<>(serializer, configValueLocalizationKey);
	}

	private record ConfigManager(IConfigManager delegate) implements mezz.jei.api.runtime.config.IJeiConfigManager {
		@Override
		@Unmodifiable
		public Collection<mezz.jei.api.runtime.config.IJeiConfigFile> getConfigFiles() {
			return delegate.getConfigFiles()
				.stream()
				.map(configFile -> (mezz.jei.api.runtime.config.IJeiConfigFile) new ConfigFile(configFile))
				.toList();
		}
	}

	private record ConfigFile(IConfigFile delegate) implements mezz.jei.api.runtime.config.IJeiConfigFile {
		@Override
		public Path getPath() {
			return delegate.getPath();
		}

		@Override
		@Unmodifiable
		public List<? extends mezz.jei.api.runtime.config.IJeiConfigCategory> getCategories() {
			return delegate.getCategories()
				.stream()
				.map(ConfigCategory::new)
				.toList();
		}
	}

	private record ConfigCategory(IConfigCategory delegate) implements mezz.jei.api.runtime.config.IJeiConfigCategory {
		@Override
		public String getName() {
			return delegate.getName();
		}

		@Override
		public Component getLocalizedName() {
			return delegate.getLocalizedName();
		}

		@Override
		public Component getDescription() {
			return delegate.getLocalizedDescription();
		}

		@Override
		@Unmodifiable
		public Collection<? extends mezz.jei.api.runtime.config.IJeiConfigValue<?>> getConfigValues() {
			return delegate.getConfigValues()
				.stream()
				.map(ConfigManagerAdapter::createConfigValue)
				.toList();
		}
	}

	private record ConfigValue<T>(IConfigValue<T> delegate) implements mezz.jei.api.runtime.config.IJeiConfigValue<T> {
		@Override
		public String getName() {
			return delegate.getName();
		}

		@Override
		public String getDescription() {
			return delegate.getLocalizedDescription().getString();
		}

		@Override
		public Component getLocalizedName() {
			return delegate.getLocalizedName();
		}

		@Override
		public Component getLocalizedDescription() {
			return delegate.getLocalizedDescription();
		}

		@Override
		public T getValue() {
			return delegate.getValue();
		}

		@Override
		public T getDefaultValue() {
			return delegate.getDefaultValue();
		}

		@Override
		public boolean set(T value) {
			return delegate.set(value);
		}

		@Override
		public void addListener(Consumer<T> listener) {
			delegate.addListener(listener);
		}

		@Override
		public mezz.jei.api.runtime.config.ConfigValueUpdateType getUpdateType() {
			return toJei(delegate.getUpdateType());
		}

		@Override
		public mezz.jei.api.runtime.config.IJeiConfigValueSerializer<T> getSerializer() {
			return createSerializer(delegate.getSerializer(), delegate.getLocalizationKey());
		}
	}

	private record ConfigValueSerializer<T>(
		IConfigValueSerializer<T> delegate,
		String configValueLocalizationKey
	) implements mezz.jei.api.runtime.config.IJeiConfigValueSerializer<T> {
		@Override
		public String serialize(T value) {
			return delegate.serialize(value);
		}

		@Override
		public IDeserializeResult<T> deserialize(String string) {
			return new DeserializeResult<>(delegate.deserialize(string));
		}

		@Override
		public boolean isValid(T value) {
			return delegate.isValid(value);
		}

		@Override
		public Optional<Collection<T>> getAllValidValues() {
			return delegate.getAllValidValues();
		}

		@Override
		public Component getLocalizedValueName(Component configValueName, T value) {
			return delegate.getLocalizedValueName(configValueLocalizationKey, value);
		}

		@Override
		public Optional<Component> getLocalizedValueDescription(Component configValueName, T value) {
			return delegate.getLocalizedValueDescription(configValueLocalizationKey, value);
		}

		@Override
		public Optional<ResourceLocation> getValueIcon(T value) {
			return delegate.getValueIcon(value);
		}

		@Override
		public String getValidValuesDescription() {
			return delegate.getValidValuesDescription();
		}
	}

	private record ConfigListValueSerializer<T>(
		IConfigListValueSerializer<T> delegate,
		String configValueLocalizationKey
	) implements mezz.jei.api.runtime.config.IJeiConfigListValueSerializer<T> {
		@Override
		public mezz.jei.api.runtime.config.IJeiConfigValueSerializer<T> getListValueSerializer() {
			return createSerializer(delegate.getListValueSerializer(), configValueLocalizationKey);
		}

		@Override
		public String serialize(List<T> value) {
			return delegate.serialize(value);
		}

		@Override
		public IDeserializeResult<List<T>> deserialize(String string) {
			return new DeserializeResult<>(delegate.deserialize(string));
		}

		@Override
		public boolean isValid(List<T> value) {
			return delegate.isValid(value);
		}

		@Override
		public Optional<Collection<List<T>>> getAllValidValues() {
			return delegate.getAllValidValues();
		}

		@Override
		public Component getLocalizedValueName(Component configValueName, List<T> value) {
			return delegate.getLocalizedValueName(configValueLocalizationKey, value);
		}

		@Override
		public Optional<Component> getLocalizedValueDescription(Component configValueName, List<T> value) {
			return delegate.getLocalizedValueDescription(configValueLocalizationKey, value);
		}

		@Override
		public Optional<ResourceLocation> getValueIcon(List<T> value) {
			return delegate.getValueIcon(value);
		}

		@Override
		public String getValidValuesDescription() {
			return delegate.getValidValuesDescription();
		}
	}

	private record DeserializeResult<T>(
		IConfigValueSerializer.IDeserializeResult<T> delegate
	) implements mezz.jei.api.runtime.config.IJeiConfigValueSerializer.IDeserializeResult<T> {
		@Override
		public Optional<T> getResult() {
			return delegate.getResult();
		}

		@Override
		public List<String> getErrors() {
			return delegate.getErrors();
		}
	}
}
