package mezz.jei.common.config;

import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;
import net.mezzdev.config.api.value.ConfigValueEditorType;
import net.mezzdev.config.api.value.ConfigValueUpdateType;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import mezz.jei.common.config.serializers.DeserializeResult;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Adapts separate horizontal and vertical alignment config values into one screen value.
 *
 * @since 19.39.0
 */
final class AlignmentConfigValue implements IConfigValue<Alignment> {
	/**
	 * Editor type for combined alignment config values.
	 *
	 * @since 19.39.0
	 */
	public static final ConfigValueEditorType<Alignment> EDITOR_TYPE = JeiConfigValueEditorTypes.ALIGNMENT;

	private static final IConfigValueSerializer<Alignment> SERIALIZER = new AlignmentSerializer();

	private final IConfigValue<HorizontalAlignment> horizontalAlignment;
	private final IConfigValue<VerticalAlignment> verticalAlignment;
	private final String localizationKey;
	private final Component localizedName;
	private final Component localizedDescription;

	/**
	 * Create a combined alignment value backed by separate horizontal and vertical values.
	 *
	 * @param localizationPath the localization key prefix for the owning config schema
	 * @param categoryName the storage category containing the backing values
	 * @param horizontalAlignment backing horizontal alignment config value
	 * @param verticalAlignment backing vertical alignment config value
	 *
	 * @since 19.39.0
	 */
	AlignmentConfigValue(
		String localizationPath,
		String categoryName,
		IConfigValue<HorizontalAlignment> horizontalAlignment,
		IConfigValue<VerticalAlignment> verticalAlignment
	) {
		this.horizontalAlignment = horizontalAlignment;
		this.verticalAlignment = verticalAlignment;

		this.localizationKey = localizationPath + "." + categoryName + ".alignment";
		this.localizedName = Component.translatable(localizationKey);
		this.localizedDescription = Component.translatable(localizationKey + ".description");
	}

	@Override
	public String getName() {
		return "alignment";
	}

	@Override
	public String getLocalizationKey() {
		return localizationKey;
	}

	@Override
	public Component getLocalizedName() {
		return localizedName;
	}

	@Override
	public Component getLocalizedDescription() {
		return localizedDescription;
	}

	@Override
	public Alignment getValue() {
		return Alignment.from(horizontalAlignment.getValue(), verticalAlignment.getValue());
	}

	@Override
	public Alignment getDefaultValue() {
		return Alignment.from(horizontalAlignment.getDefaultValue(), verticalAlignment.getDefaultValue());
	}

	@Override
	public boolean set(Alignment value) {
		boolean horizontalChanged = horizontalAlignment.set(value.horizontalAlignment());
		boolean verticalChanged = verticalAlignment.set(value.verticalAlignment());
		return horizontalChanged || verticalChanged;
	}

	@Override
	public void addListener(Consumer<Alignment> listener) {
		horizontalAlignment.addListener(value -> listener.accept(getValue()));
		verticalAlignment.addListener(value -> listener.accept(getValue()));
	}

	@Override
	public ConfigValueUpdateType getUpdateType() {
		return max(horizontalAlignment.getUpdateType(), verticalAlignment.getUpdateType());
	}

	private static ConfigValueUpdateType max(ConfigValueUpdateType first, ConfigValueUpdateType second) {
		if (first == ConfigValueUpdateType.RESTART || second == ConfigValueUpdateType.RESTART) {
			return ConfigValueUpdateType.RESTART;
		}
		if (first == ConfigValueUpdateType.ON_APPLY || second == ConfigValueUpdateType.ON_APPLY) {
			return ConfigValueUpdateType.ON_APPLY;
		}
		return ConfigValueUpdateType.IMMEDIATE;
	}

	@Override
	public IConfigValueSerializer<Alignment> getSerializer() {
		return SERIALIZER;
	}

	private static final class AlignmentSerializer implements IConfigValueSerializer<Alignment> {
		private static final Collection<Alignment> VALID_VALUES = List.of(Alignment.values());

		@Override
		public String serialize(Alignment value) {
			return value.name();
		}

		@Override
		public IDeserializeResult<Alignment> deserialize(String string) {
			string = string.trim();
			if (string.startsWith("\"") && string.endsWith("\"")) {
				string = string.substring(1, string.length() - 1);
			}
			try {
				return new DeserializeResult<>(Alignment.valueOf(string));
			} catch (IllegalArgumentException e) {
				return new DeserializeResult<>(null, "Invalid alignment name: %s".formatted(e.getMessage()));
			}
		}

		@Override
		public boolean isValid(Alignment value) {
			return VALID_VALUES.contains(value);
		}

		@Override
		public Optional<Collection<Alignment>> getAllValidValues() {
			return Optional.of(VALID_VALUES);
		}

		@Override
		public ConfigValueEditorType<Alignment> getEditorType() {
			return EDITOR_TYPE;
		}

		@Override
		public Component getLocalizedValueName(String configValueLocalizationKey, Alignment value) {
			return getTranslatedValue(configValueLocalizationKey, value.name(), ".name")
				.orElseGet(() -> Component.translatable("jei.config.value.Alignment." + value.name() + ".name"));
		}

		@Override
		public Optional<Component> getLocalizedValueDescription(String configValueLocalizationKey, Alignment value) {
			return Optional.empty();
		}

		@Override
		public String getValidValuesDescription() {
			return VALID_VALUES.toString();
		}

		private static Optional<Component> getTranslatedValue(String configValueLocalizationKey, String valueName, String suffix) {
			String translationKey = configValueLocalizationKey + ".value." + valueName + suffix;
			if (Language.getInstance().has(translationKey)) {
				return Optional.of(Component.translatable(translationKey));
			}
			return Optional.empty();
		}
	}
}
