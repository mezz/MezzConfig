package net.mezzdev.config.serializers;

import net.mezzdev.config.api.value.serializer.IConfigValueSerializer;
import org.jetbrains.annotations.Nullable;

/**
 * Serializer for string config values.
 */
public final class StringSerializer implements IConfigValueSerializer<String> {
	/**
	 * Shared string serializer instance.
	 */
	public static final StringSerializer INSTANCE = new StringSerializer();

	private StringSerializer() {}

	@Override
	public String serialize(String value) {
		return value;
	}

	@Override
	public DeserializeResult<String> deserialize(String string) {
		return DeserializeResult.success(string);
	}

	@Override
	public boolean isValid(@Nullable String value) {
		return value != null;
	}

	@Override
	public String getValidValuesDescription() {
		return "Any string";
	}
}
