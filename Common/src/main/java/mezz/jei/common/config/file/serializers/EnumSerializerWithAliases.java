package mezz.jei.common.config.file.serializers;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class EnumSerializerWithAliases<T extends Enum<T>> extends EnumSerializer<T> {
	private final Map<String, T> aliases;

	public EnumSerializerWithAliases(Class<T> enumClass, Map<String, T> aliases) {
		super(enumClass);
		this.aliases = aliases.entrySet()
			.stream()
			.collect(Collectors.toUnmodifiableMap(
				entry -> normalize(entry.getKey()),
				Map.Entry::getValue
			));
	}

	@Override
	public DeserializeResult<T> deserialize(String string) {
		T alias = aliases.get(normalize(string));
		if (alias != null) {
			return new DeserializeResult<>(alias);
		}
		return super.deserialize(string);
	}

	private static String normalize(String string) {
		string = string.trim();
		if (string.startsWith("\"") && string.endsWith("\"")) {
			string = string.substring(1, string.length() - 1);
		}
		return string.toLowerCase(Locale.ROOT);
	}
}
