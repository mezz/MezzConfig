package net.mezzdev.config.value;

import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import net.mezzdev.config.api.util.ErrorUtil;

import java.util.List;
import java.util.function.Function;

public record ConfigValueMigration<T, R>(
	IConfigValue<R> configValue,
	IConfigValueSerializer<T> serializer,
	Function<T, R> migration
) {
	public ConfigValueMigration {
		configValue = ErrorUtil.checkNotNull(configValue, "configValue");
		serializer = ErrorUtil.checkNotNull(serializer, "serializer");
		migration = ErrorUtil.checkNotNull(migration, "migration");
	}

	public List<String> migrate(String value) {
		IConfigValueSerializer.IDeserializeResult<T> deserializeResult = serializer.deserialize(value);
		deserializeResult.getResult()
			.ifPresent(oldValue -> configValue.set(migration.apply(oldValue)));
		return deserializeResult.getErrors();
	}
}
