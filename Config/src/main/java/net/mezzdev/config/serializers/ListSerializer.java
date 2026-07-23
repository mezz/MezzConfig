package net.mezzdev.config.serializers;

import net.mezzdev.config.value.ConfigValueEditorType;
import net.mezzdev.config.value.ConfigValueEditorTypes;
import net.mezzdev.config.value.IConfigListValueSerializer;
import net.mezzdev.config.value.IConfigValueSerializer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Serializer for comma-separated list config values.
 */
public final class ListSerializer<T> implements IConfigListValueSerializer<T> {
	private final IConfigValueSerializer<T> valueSerializer;

	/**
	 * Create a list serializer using the given serializer for each list element.
	 */
	public ListSerializer(IConfigValueSerializer<T> valueSerializer) {
		this.valueSerializer = valueSerializer;
	}

	@Override
	public String serialize(List<T> values) {
		return values.stream()
			.map(valueSerializer::serialize)
			.collect(Collectors.joining(", "));
	}

	@Override
	public DeserializeResult<List<T>> deserialize(String string) {
		string = string.trim();
		if (string.startsWith("[")) {
			if (!string.endsWith("]")) {
				String errorMessage = """
					No closing brace found.
					List must have no braces, or be wrapped in [ and ].""";
				return new DeserializeResult<>(null, errorMessage);
			}
			string = string.substring(1, string.length() - 1);
		}
		String[] split = string.split(",");

		List<String> errors = new ArrayList<>();
		List<T> results = Arrays.stream(split)
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.map(valueSerializer::deserialize)
			.<T>mapMulti((r, c) -> {
				r.getResult().ifPresent(c);
				errors.addAll(r.getErrors());
			})
			.toList();

		return new DeserializeResult<>(results, errors);
	}

	@Override
	public String getValidValuesDescription() {
		return "A comma-separated list containing values of:\n%s".formatted(valueSerializer.getValidValuesDescription());
	}

	@Override
	public boolean isValid(List<T> value) {
		return value.stream()
			.allMatch(valueSerializer::isValid);
	}

	@Override
	public IConfigValueSerializer<T> getListValueSerializer() {
		return valueSerializer;
	}

	@Override
	public Optional<Collection<List<T>>> getAllValidValues() {
		return Optional.empty();
	}

	@Override
	public ConfigValueEditorType<List<T>> getEditorType() {
		return ConfigValueEditorTypes.getList();
	}

	@Override
	public Component getLocalizedValueName(String configValueLocalizationKey, List<T> values) {
		if (values.isEmpty()) {
			return Component.translatable("mezz_config.config.value.list.empty");
		}
		MutableComponent result = Component.empty();
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				result.append(Component.literal(", "));
			}
			result.append(valueSerializer.getLocalizedValueName(configValueLocalizationKey, values.get(i)));
		}
		return result;
	}
}
