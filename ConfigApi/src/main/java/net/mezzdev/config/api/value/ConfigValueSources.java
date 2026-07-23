package net.mezzdev.config.api.value;

import net.minecraft.client.KeyMapping;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Standard config value sources used by config screens.
 *
 * @since 19.39.0
 */
public final class ConfigValueSources {
	private ConfigValueSources() {

	}

	/**
	 * Source for config values that have already been created.
	 *
	 * @since 19.39.0
	 */
	public static IValues values(Collection<? extends IConfigValue<?>> values) {
		return new Values(List.copyOf(values));
	}

	/**
	 * A source for config values that have already been created.
	 *
	 * @since 19.39.0
	 */
	public interface IValues extends IConfigValueSource {
		@Override
		default ConfigValueSourceType<IValues> getType() {
			return ConfigValueSourceTypes.VALUES;
		}

		/**
		 * Config values from this source.
		 *
		 * @since 19.39.0
		 */
		@Override
		@Unmodifiable
		Collection<? extends IConfigValue<?>> getConfigValues();
	}

	/**
	 * Source for Minecraft key mappings.
	 *
	 * @param keyMappings key mappings to display
	 *
	 * @since 19.39.0
	 */
	public static IKeyMappings keyMappings(Collection<? extends KeyMapping> keyMappings) {
		List<KeyMapping> keyMappingsCopy = List.copyOf(keyMappings);
		return keyMappings(() -> keyMappingsCopy);
	}

	/**
	 * Source for Minecraft key mappings that are resolved when a config screen opens.
	 *
	 * @param keyMappingsSupplier supplies key mappings to display
	 *
	 * @since 19.39.0
	 */
	public static IKeyMappings keyMappings(Supplier<? extends Collection<? extends KeyMapping>> keyMappingsSupplier) {
		return new KeyMappings(keyMappingsSupplier);
	}

	/**
	 * A source for Minecraft key mappings.
	 *
	 * @since 19.39.0
	 */
	public interface IKeyMappings extends IConfigValueSource {
		@Override
		default ConfigValueSourceType<IKeyMappings> getType() {
			return ConfigValueSourceTypes.KEY_MAPPINGS;
		}

		/**
		 * Key mappings from this source.
		 *
		 * @since 19.39.0
		 */
		@Unmodifiable
		Collection<? extends KeyMapping> getKeyMappings();
	}

	private record Values(
		List<IConfigValue<?>> configValues
	) implements IValues {
		private Values {
			configValues = List.copyOf(configValues);
		}

		@Override
		public List<IConfigValue<?>> getConfigValues() {
			return configValues;
		}
	}

	private record KeyMappings(
		Supplier<? extends Collection<? extends KeyMapping>> keyMappingsSupplier
	) implements IKeyMappings {
		private KeyMappings {
			if (keyMappingsSupplier == null) {
				throw new NullPointerException("keyMappingsSupplier must not be null.");
			}
		}

		@Override
		public Collection<? extends KeyMapping> getKeyMappings() {
			Collection<? extends KeyMapping> keyMappings = keyMappingsSupplier.get();
			if (keyMappings == null) {
				throw new NullPointerException("keyMappingsSupplier must not return null.");
			}
			return List.copyOf(keyMappings);
		}
	}
}
