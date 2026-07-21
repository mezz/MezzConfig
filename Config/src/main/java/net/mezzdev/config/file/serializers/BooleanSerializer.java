package net.mezzdev.config.file.serializers;

import net.mezzdev.config.ConfigValueEditorType;
import net.mezzdev.config.ConfigValueEditorTypes;
import net.mezzdev.config.IJeiConfigValueSerializer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class BooleanSerializer implements IJeiConfigValueSerializer<Boolean> {
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
	public Component getLocalizedValueName(Component configValueName, Boolean value) {
		return Component.translatable(value ? "jei.config.value.boolean.true" : "jei.config.value.boolean.false");
	}

	@Override
	public Optional<Component> getLocalizedValueDescription(Component configValueName, Boolean value) {
		return Optional.of(ConfigValueSerializerUtil.getTranslatedValue(configValueName, String.valueOf(value), ".description")
			.orElseGet(() -> getGenericValueDescription(value)));
	}

	@Override
	public Optional<ResourceLocation> getValueIcon(Boolean value) {
		return Optional.of(value ? ENABLED_ICON : DISABLED_ICON);
	}

	private static Component getGenericValueDescription(boolean value) {
		return Component.translatable(value ? "jei.config.value.boolean.true.description" : "jei.config.value.boolean.false.description");
	}
}
