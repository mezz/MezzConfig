package mezz.jei.common.config.file.serializers;

import mezz.jei.api.runtime.config.IJeiConfigValueSerializer;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class EnumSerializer<T extends Enum<T>> implements IJeiConfigValueSerializer<T> {
	private final Class<T> enumClass;
	private final Collection<T> validValues;

	public EnumSerializer(Class<T> enumClass) {
		this.enumClass = enumClass;
		this.validValues = List.of(enumClass.getEnumConstants());
	}

	@Override
	public String serialize(T value) {
		return value.name();
	}

	@Override
	public DeserializeResult<T> deserialize(String string) {
		string = string.trim();
		if (string.startsWith("\"") && string.endsWith("\"")) {
			string = string.substring(1, string.length() - 1);
		}
		try {
			T value = Enum.valueOf(enumClass, string);
			return new DeserializeResult<>(value);
		} catch (IllegalArgumentException e) {
			return new DeserializeResult<>(null, "Invalid enum name: %s".formatted(e.getMessage()));
		}
	}

	@Override
	public String getValidValuesDescription() {
		String names = validValues.stream()
			.map(Enum::name)
			.collect(Collectors.joining(", "));

		return "[%s]".formatted(names);
	}

	@Override
	public boolean isValid(T value) {
		return validValues.contains(value);
	}

	@Override
	public Optional<Collection<T>> getAllValidValues() {
		return Optional.of(validValues);
	}

	@Override
	public Component getLocalizedValueName(Component configValueName, T value) {
		return getTranslatedEnumValue(configValueName, value, ".name")
			.orElseGet(() -> Component.literal(ConfigValueSerializerUtil.getDisplayNameFallback(value.name())));
	}

	@Override
	public Optional<Component> getLocalizedValueDescription(Component configValueName, T value) {
		return getTranslatedEnumValue(configValueName, value, ".description");
	}

	private Optional<Component> getTranslatedEnumValue(Component configValueName, T value, String suffix) {
		String valueName = value.name();
		Optional<Component> configValueTranslation = ConfigValueSerializerUtil.getTranslatedValue(configValueName, valueName, suffix);
		if (configValueTranslation.isPresent()) {
			return configValueTranslation;
		}

		String enumKey = "jei.config.value." + enumClass.getSimpleName() + "." + valueName + suffix;
		if (Language.getInstance().has(enumKey)) {
			return Optional.of(Component.translatable(enumKey));
		}
		return Optional.empty();
	}
}
