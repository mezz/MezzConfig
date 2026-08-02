package net.mezzdev.config.serializers;

import net.mezzdev.config.api.value.IConfigValueSerializer;

import java.util.Collection;
import java.util.Optional;

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
		return new DeserializeResult<>(string);
	}

	@Override
	public boolean isValid(String value) {
		return value != null;
	}

	@Override
	public Optional<Collection<String>> getAllValidValues() {
		return Optional.empty();
	}

	@Override
	public String getValidValuesDescription() {
		return "Any string";
	}
}
