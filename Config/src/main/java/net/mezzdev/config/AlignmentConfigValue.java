package net.mezzdev.config;

import net.mezzdev.config.file.serializers.EnumSerializer;
import net.minecraft.network.chat.Component;

/**
 * Adapts separate horizontal and vertical alignment config values into one screen value.
 */
public final class AlignmentConfigValue implements IJeiConfigValue<ConfigAlignment> {
	public static final ConfigValueEditorType<ConfigAlignment> EDITOR_TYPE = ConfigValueEditorType.create("jei", "alignment");

	private static final IJeiConfigValueSerializer<ConfigAlignment> SERIALIZER = new EnumSerializer<>(ConfigAlignment.class);

	private final IJeiConfigValue<HorizontalConfigAlignment> horizontalAlignment;
	private final IJeiConfigValue<VerticalConfigAlignment> verticalAlignment;
	private final Component localizedName;
	private final Component localizedDescription;

	public AlignmentConfigValue(
		String categoryName,
		IJeiConfigValue<HorizontalConfigAlignment> horizontalAlignment,
		IJeiConfigValue<VerticalConfigAlignment> verticalAlignment
	) {
		this.horizontalAlignment = horizontalAlignment;
		this.verticalAlignment = verticalAlignment;

		String localizationKey = "jei.config.client." + categoryName + ".alignment";
		this.localizedName = Component.translatable(localizationKey);
		this.localizedDescription = Component.translatable(localizationKey + ".description");
	}

	@Override
	public String getName() {
		return "alignment";
	}

	@Override
	@SuppressWarnings("removal")
	public String getDescription() {
		return localizedDescription.getString();
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
		if (first == ConfigValueUpdateType.RESTART_JEI || second == ConfigValueUpdateType.RESTART_JEI) {
			return ConfigValueUpdateType.RESTART_JEI;
		}
		if (first == ConfigValueUpdateType.ON_APPLY || second == ConfigValueUpdateType.ON_APPLY) {
			return ConfigValueUpdateType.ON_APPLY;
		}
		return ConfigValueUpdateType.IMMEDIATE;
	}

	@Override
	public IJeiConfigValueSerializer<ConfigAlignment> getSerializer() {
		return SERIALIZER;
	}
}
