package net.mezzdev.config.file;

import net.mezzdev.config.api.value.serializer.IDeserializeResult;
import net.mezzdev.config.api.value.serializer.IConfigValueSerializer;
import net.mezzdev.config.schema.ConfigCategory;
import net.mezzdev.config.serializers.BooleanSerializer;
import net.mezzdev.config.value.ConfigValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigFileValueAdapterTest {
	private static final String LOCALIZATION_PATH = "mezz_config.config.test";

	@Test
	public void throwingDeserializerBecomesABoundedFailure() {
		IConfigValueSerializer<String> serializer = serializer(
			value -> value,
			value -> {
				throw new IllegalArgumentException("x".repeat(2_000));
			},
			value -> true
		);

		IDeserializeResult<String> result = ConfigFileValueAdapter.deserializeScalar(serializer, "external input");

		assertTrue(result.getResult().isEmpty());
		assertEquals(1, result.getDiagnostics().size());
		assertTrue(result.getDiagnostics().getFirst().startsWith("Config serializer failed to deserialize"));
		assertTrue(result.getDiagnostics().getFirst().length() <= 1_024);
	}

	@Test
	public void successfulInvalidDeserializerResultIsRejected() {
		IConfigValueSerializer<String> successfulSerializer = serializer(
			value -> value,
			value -> IDeserializeResult.success("invalid"),
			"valid"::equals
		);
		IConfigValueSerializer<String> partialSerializer = serializer(
			value -> value,
			value -> IDeserializeResult.partialSuccess("invalid", "recovered input"),
			"valid"::equals
		);

		IDeserializeResult<String> successfulResult = ConfigFileValueAdapter.deserializeScalar(successfulSerializer, "external input");
		IDeserializeResult<String> partialResult = ConfigFileValueAdapter.deserializeScalar(partialSerializer, "external input");

		assertTrue(successfulResult.getResult().isEmpty());
		assertTrue(successfulResult.getDiagnostics().getFirst().contains("reports as invalid"));
		assertTrue(partialResult.getResult().isEmpty());
		assertEquals(2, partialResult.getDiagnostics().size());
		assertEquals("recovered input", partialResult.getDiagnostics().getFirst());
		assertTrue(partialResult.getDiagnostics().getLast().contains("reports as invalid"));
	}

	@Test
	public void serializerContractIsCheckedBeforeAValueIsBuilt() {
		AtomicInteger serializations = new AtomicInteger();
		IConfigValueSerializer<String> nonDeterministic = serializer(
			value -> value + serializations.incrementAndGet(),
			IDeserializeResult::success,
			value -> true
		);
		IConfigValueSerializer<String> lossy = serializer(
			value -> value,
			value -> IDeserializeResult.success(value + " changed"),
			value -> true
		);
		IConfigValueSerializer<String> throwing = serializer(
			value -> {
				throw new IllegalStateException("cannot serialize");
			},
			IDeserializeResult::success,
			value -> true
		);

		IllegalArgumentException nonDeterministicFailure = assertThrows(
			IllegalArgumentException.class,
			() -> new ConfigValue<>(LOCALIZATION_PATH, "nonDeterministic", "default", nonDeterministic)
		);
		IllegalArgumentException lossyFailure = assertThrows(
			IllegalArgumentException.class,
			() -> new ConfigValue<>(LOCALIZATION_PATH, "lossy", "default", lossy)
		);
		IllegalArgumentException throwingFailure = assertThrows(
			IllegalArgumentException.class,
			() -> new ConfigValue<>(LOCALIZATION_PATH, "throwing", "default", throwing)
		);

		assertTrue(nonDeterministicFailure.getMessage().contains("cannot round-trip"));
		assertTrue(lossyFailure.getMessage().contains("cannot round-trip"));
		assertTrue(throwingFailure.getMessage().contains("cannot round-trip"));
	}

	@Test
	public void laterSerializationFailureRejectsUpdateBeforeMutation() {
		AtomicBoolean serializationFails = new AtomicBoolean();
		IConfigValueSerializer<String> serializer = serializer(
			value -> {
				if (serializationFails.get()) {
					throw new IllegalStateException("serializer became unavailable");
				}
				return value;
			},
			IDeserializeResult::success,
			value -> true
		);
		ConfigValue<String> value = new ConfigValue<>(LOCALIZATION_PATH, "value", "original", serializer);
		serializationFails.set(true);

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> value.set("updated"));

		assertTrue(exception.getMessage().contains("cannot round-trip"));
		assertEquals("original", value.get());
	}

	@Test
	public void fileLoadPreservesValidNeighborsWhenOneDeserializerThrows(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("test.ini");
		Files.writeString(path, "[general]\nbad = boom\ngood = false\n");
		IConfigValueSerializer<String> serializer = serializer(
			value -> value,
			value -> {
				if (value.equals("boom")) {
					throw new IllegalArgumentException("malformed external value");
				}
				return IDeserializeResult.success(value);
			},
			value -> true
		);
		ConfigValue<String> bad = new ConfigValue<>(LOCALIZATION_PATH, "bad", "default", serializer);
		ConfigValue<Boolean> good = new ConfigValue<>(LOCALIZATION_PATH, "good", true, BooleanSerializer.INSTANCE);
		ConfigCategory category = new ConfigCategory(LOCALIZATION_PATH, "general", List.of(bad, good));

		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));

		assertEquals("default", bad.get());
		assertFalse(good.get());
		assertTrue(Files.exists(ConfigFileUtil.getBackupPath(path, 1)));
	}

	private static <T> IConfigValueSerializer<T> serializer(
		SerializerFunction<T, String> serialize,
		SerializerFunction<String, IDeserializeResult<T>> deserialize,
		SerializerFunction<T, Boolean> isValid
	) {
		return new IConfigValueSerializer<>() {
			@Override
			public String serialize(T value) {
				return serialize.apply(value);
			}

			@Override
			public IDeserializeResult<T> deserialize(String string) {
				return deserialize.apply(string);
			}

			@Override
			public boolean isValid(T value) {
				return isValid.apply(value);
			}

			@Override
			public String getValidValuesDescription() {
				return "test values";
			}
		};
	}

	@FunctionalInterface
	private interface SerializerFunction<T, R> {
		R apply(T value);
	}
}
