package net.mezzdev.config.serializers;

import net.mezzdev.config.api.value.IConfigValueSerializer;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Serializer for boolean config values.
 */
public final class BooleanSerializer implements IConfigValueSerializer<Boolean> {
	/**
	 * Shared boolean serializer instance.
	 */
	public static final BooleanSerializer INSTANCE = new BooleanSerializer();

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
	public boolean isValid(@Nullable Boolean value) {
		return value != null;
	}

	@Override
	public Optional<Collection<Boolean>> getAllValidValues() {
		return Optional.of(List.of(true, false));
	}

}
