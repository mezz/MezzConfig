package net.mezzdev.config.file;

import net.mezzdev.config.value.IConfigValue;
import net.mezzdev.config.value.IConfigValueSerializer;

import java.util.List;
import java.util.function.Function;

record ConfigValueMigration<T, R>(
	IConfigValue<R> configValue,
	IConfigValueSerializer<T> serializer,
	Function<T, R> migration
) {
	ConfigValueMigration {
		if (configValue == null) {
			throw new NullPointerException("configValue must not be null.");
		}
		if (serializer == null) {
			throw new NullPointerException("serializer must not be null.");
		}
		if (migration == null) {
			throw new NullPointerException("migration must not be null.");
		}
	}

	public List<String> migrate(String value) {
		IConfigValueSerializer.IDeserializeResult<T> deserializeResult = serializer.deserialize(value);
		deserializeResult.getResult()
			.ifPresent(oldValue -> configValue.set(migration.apply(oldValue)));
		return deserializeResult.getErrors();
	}
}
