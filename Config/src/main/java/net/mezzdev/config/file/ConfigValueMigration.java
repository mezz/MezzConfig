package net.mezzdev.config.file;

import net.mezzdev.config.value.IConfigValueSerializer;

import java.util.List;
import java.util.function.Consumer;

record ConfigValueMigration<T>(
	IConfigValueSerializer<T> serializer,
	Consumer<T> migration
) {
	ConfigValueMigration {
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
			.ifPresent(migration);
		return deserializeResult.getErrors();
	}
}
