package net.mezzdev.config.serializers;

import net.mezzdev.config.api.value.ConfigValueEditorType;
import net.mezzdev.config.api.value.ConfigValueEditorTypes;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.api.value.IConfigValueEditorSerializer;
import net.mezzdev.config.api.value.IConfigValueEditorSerializerVisitor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Serializer for boolean config values.
 */
public final class BooleanSerializer implements IConfigValueEditorSerializer<Boolean> {
	/**
	 * Shared boolean serializer instance.
	 */
	public static final BooleanSerializer INSTANCE = new BooleanSerializer();
	private static final ResourceLocation ENABLED_ICON = ResourceLocation.withDefaultNamespace("container/beacon/confirm");
	private static final ResourceLocation DISABLED_ICON = ResourceLocation.withDefaultNamespace("container/beacon/cancel");

	private BooleanSerializer() {}

	@Override
	public String serialize(Boolean value) {
		return value.toString();
	}

	@Override
	public DeserializeResult<Boolean> deserialize(String string) {
		string = string.trim();
		if ("true".equalsIgnoreCase(string)) {
			return new DeserializeResult<>(true);
		}
		if ("false".equalsIgnoreCase(string)) {
			return new DeserializeResult<>(false);
		}
		return new DeserializeResult<>(null, "string must be 'true' or 'false'");
	}

	@Override
	public String getValidValuesDescription() {
		return "[true, false]";
	}

	@Override
	public boolean isValid(Boolean value) {
		return true;
	}

	@Override
	public Optional<Collection<Boolean>> getAllValidValues() {
		return Optional.of(List.of(true, false));
	}

	@Override
	public ConfigValueEditorType<Boolean> getEditorType() {
		return ConfigValueEditorTypes.BOOLEAN;
	}

	@Override
	public <R> R visitEditor(IConfigValue<Boolean> configValue, IConfigValueEditorSerializerVisitor<R> visitor) {
		return visitor.visitBoolean(configValue, this);
	}

	@Override
	public Component getLocalizedValueName(String configValueLocalizationKey, Boolean value) {
		return Component.translatable(value ? "mezz_config.config.value.boolean.true" : "mezz_config.config.value.boolean.false");
	}

	@Override
	public Optional<Component> getLocalizedValueDescription(String configValueLocalizationKey, Boolean value) {
		return Optional.of(ConfigValueSerializerUtil.getTranslatedValue(configValueLocalizationKey, String.valueOf(value), ".description")
			.orElseGet(() -> getGenericValueDescription(value)));
	}

	@Override
	public Optional<ResourceLocation> getValueIcon(Boolean value) {
		return Optional.of(value ? ENABLED_ICON : DISABLED_ICON);
	}

	private static Component getGenericValueDescription(boolean value) {
		return Component.translatable(value ? "mezz_config.config.value.boolean.true.description" : "mezz_config.config.value.boolean.false.description");
	}
}
