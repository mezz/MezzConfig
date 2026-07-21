package net.mezzdev.config.client;

import net.mezzdev.config.ConfigValueEditorType;
import net.mezzdev.config.ConfigValueEditorTypes;
import net.mezzdev.config.ConfigValueUpdateType;
import net.mezzdev.config.IConfigValue;
import net.mezzdev.config.IConfigValueSerializer;
import net.mezzdev.config.file.serializers.EnumSerializer;
import net.minecraft.network.chat.Component;

/**
 * Adapts separate horizontal and vertical alignment config values into one screen value.
 */
public final class AlignmentConfigValue implements IConfigValue<ConfigAlignment> {
	public static final ConfigValueEditorType<ConfigAlignment> EDITOR_TYPE = ConfigValueEditorTypes.alignment();

	private static final IConfigValueSerializer<ConfigAlignment> SERIALIZER = new EnumSerializer<>(ConfigAlignment.class);

	private final IConfigValue<HorizontalConfigAlignment> horizontalAlignment;
	private final IConfigValue<VerticalConfigAlignment> verticalAlignment;
	private final String localizationKey;
	private final Component localizedName;
	private final Component localizedDescription;

	public AlignmentConfigValue(
		String localizationPath,
		String categoryName,
		IConfigValue<HorizontalConfigAlignment> horizontalAlignment,
		IConfigValue<VerticalConfigAlignment> verticalAlignment
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
	public ConfigAlignment getValue() {
		return ConfigAlignment.from(horizontalAlignment.getValue(), verticalAlignment.getValue());
	}

	@Override
	public ConfigAlignment getDefaultValue() {
		return ConfigAlignment.from(horizontalAlignment.getDefaultValue(), verticalAlignment.getDefaultValue());
	}

	@Override
	public boolean set(ConfigAlignment value) {
		boolean horizontalChanged = horizontalAlignment.set(value.horizontalAlignment());
		boolean verticalChanged = verticalAlignment.set(value.verticalAlignment());
		return horizontalChanged || verticalChanged;
	}

	@Override
	public ConfigValueUpdateType getUpdateType() {
		return max(horizontalAlignment.getUpdateType(), verticalAlignment.getUpdateType());
	}

	@Override
	public ConfigValueEditorType<ConfigAlignment> getEditorType() {
		return EDITOR_TYPE;
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
	public IConfigValueSerializer<ConfigAlignment> getSerializer() {
		return SERIALIZER;
	}
}
