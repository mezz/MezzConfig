package net.mezzdev.config.test.schema;

import net.mezzdev.config.api.value.IAppliedConfigValueChange;
import net.mezzdev.config.api.value.IDeserializeResult;
import net.mezzdev.config.api.value.IConfigListValueSerializer;
import net.mezzdev.config.schema.ConfigCategoryBuilder;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.serializers.BooleanSerializer;
import net.mezzdev.config.value.ConfigValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigSchemaTest {
	@Test
	public void addEnumListSupportsEmptyDefaultLists() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<List<TestMode>> modes = builder.addEnumList(
				"modes",
				List.of(),
				TestMode.class
			)
			.build();

		assertEquals(List.of(), modes.getDefaultValue());
		assertEquals(
			List.of(TestMode.STANDARD, TestMode.ADVANCED),
			modes.getSerializer()
				.deserialize("[STANDARD, ADVANCED]")
				.getResult()
				.orElseThrow()
		);
	}

	@Test
	public void addListWrapsElementSerializer() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<List<Boolean>> flags = builder.addList(
				"flags",
				List.of(true),
				BooleanSerializer.INSTANCE
			)
			.build();

		assertEquals(
			List.of(false, true),
			flags.getSerializer()
				.deserialize("false, true")
				.getResult()
				.orElseThrow()
		);
		assertTrue(flags.getSerializer() instanceof IConfigListValueSerializer<?>);
		IConfigListValueSerializer<?> listSerializer = (IConfigListValueSerializer<?>) flags.getSerializer();
		assertSame(BooleanSerializer.INSTANCE, listSerializer.getElementSerializer());
	}

	@Test
	public void addBuiltInValueHelpersCreateSerializers() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<String> name = builder.addString("name", "default")
			.build();
		ConfigValue<List<String>> names = builder.addStringList("names", List.of("default"))
			.build();
		ConfigValue<List<Boolean>> flags = builder.addBooleanList("flags", List.of(true))
			.build();
		ConfigValue<Integer> unboundedInteger = builder.addInteger("unboundedInteger", 1)
			.build();
		ConfigValue<List<Integer>> unboundedIntegers = builder.addIntegerList("unboundedIntegers", List.of(1))
			.build();
		ConfigValue<List<Integer>> boundedIntegers = builder.addIntegerList("boundedIntegers", List.of(1), 0, 10)
			.build();
		ConfigValue<Integer> color = builder.addColor("color", 0xFF112233)
			.build();
		ConfigValue<List<Integer>> colors = builder.addColorList("colors", List.of(0xFF112233))
			.build();
		ConfigValue<Long> boundedLong = builder.addLong("boundedLong", 1L, 0L, 10L)
			.build();
		ConfigValue<List<Long>> unboundedLongs = builder.addLongList("unboundedLongs", List.of(1L))
			.build();
		ConfigValue<List<Long>> boundedLongs = builder.addLongList("boundedLongs", List.of(1L), 0L, 10L)
			.build();
		ConfigValue<Double> boundedDouble = builder.addDouble("boundedDouble", 1.5, 0.0, 10.0)
			.build();
		ConfigValue<List<Double>> unboundedDoubles = builder.addDoubleList("unboundedDoubles", List.of(1.5))
			.build();
		ConfigValue<List<Double>> boundedDoubles = builder.addDoubleList("boundedDoubles", List.of(1.5), 0.0, 10.0)
			.build();
		ConfigValue<TestMode> restrictedEnum = builder.addEnum("restrictedEnum", TestMode.STANDARD, List.of(TestMode.STANDARD))
			.build();
		ConfigValue<List<TestMode>> restrictedEnums = builder.addEnumList("restrictedEnums", List.of(), List.of(TestMode.STANDARD))
			.build();

		assertEquals("configured", name.getSerializer().deserialize("configured").getResult().orElseThrow());
		assertEquals(List.of("one", "two"), names.getSerializer().deserialize("one, two").getResult().orElseThrow());
		assertEquals(List.of(false, true), flags.getSerializer().deserialize("false, true").getResult().orElseThrow());
		assertEquals(Integer.MIN_VALUE, unboundedInteger.getSerializer().getRange().orElseThrow().min());
		assertEquals(List.of(1, 2), unboundedIntegers.getSerializer().deserialize("1, 2").getResult().orElseThrow());
		assertEquals(List.of(1, 2), boundedIntegers.getSerializer().deserialize("1, 2").getResult().orElseThrow());
		assertEquals("0xFF112233", color.getSerializer().serialize(0xFF112233));
		assertEquals(0xFF445566, color.getSerializer().deserialize("0xFF445566").getResult().orElseThrow());
		assertEquals(List.of(0xFF112233, 0x80445566), colors.getSerializer().deserialize("0xFF112233, 0x80445566").getResult().orElseThrow());
		assertEquals(10L, boundedLong.getSerializer().getRange().orElseThrow().max());
		assertEquals(List.of(1L, 2L), unboundedLongs.getSerializer().deserialize("1, 2").getResult().orElseThrow());
		assertEquals(List.of(1L, 2L), boundedLongs.getSerializer().deserialize("1, 2").getResult().orElseThrow());
		assertEquals(10.0, boundedDouble.getSerializer().getRange().orElseThrow().max());
		assertEquals(List.of(1.5, 2.5), unboundedDoubles.getSerializer().deserialize("1.5, 2.5").getResult().orElseThrow());
		assertEquals(List.of(1.5, 2.5), boundedDoubles.getSerializer().deserialize("1.5, 2.5").getResult().orElseThrow());
		assertEquals(TestMode.STANDARD, restrictedEnum.getSerializer().deserialize("STANDARD").getResult().orElseThrow());
		assertEquals(List.of(TestMode.STANDARD), restrictedEnums.getSerializer().deserialize("STANDARD").getResult().orElseThrow());
		assertListElementSerializer(names, "configured", "configured");
		assertListElementSerializer(flags, "false", false);
		assertListElementSerializer(unboundedIntegers, "2", 2);
		assertListElementSerializer(boundedIntegers, "2", 2);
		assertListElementSerializer(colors, "0xFF445566", 0xFF445566);
		assertListElementSerializer(unboundedLongs, "2", 2L);
		assertListElementSerializer(boundedLongs, "2", 2L);
		assertListElementSerializer(unboundedDoubles, "2.5", 2.5);
		assertListElementSerializer(boundedDoubles, "2.5", 2.5);
		assertListElementSerializer(restrictedEnums, "STANDARD", TestMode.STANDARD);
		assertTrue(boundedIntegers.getSerializer().deserialize("11").getErrors().getFirst().contains("Invalid integer"));
		assertTrue(color.getSerializer().deserialize("112233").getErrors().getFirst().contains("Invalid color"));
		assertTrue(boundedLongs.getSerializer().deserialize("11").getErrors().getFirst().contains("Invalid long"));
		assertTrue(boundedDoubles.getSerializer().deserialize("11.0").getErrors().getFirst().contains("Invalid double"));
		assertTrue(restrictedEnum.getSerializer().deserialize("ADVANCED").getErrors().getFirst().contains("Invalid enum name"));
		assertTrue(restrictedEnums.getSerializer().deserialize("ADVANCED").getErrors().getFirst().contains("Invalid enum name"));
	}

	@Test
	public void addCategoryRejectsInvalidNames() {
		assertThrows(IllegalArgumentException.class, () -> new ConfigCategoryBuilder("mezz_config.config.test", "bad-name"));
	}

	@Test
	public void addCategoryRejectsDuplicateLegacyNames() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		builder.addLegacyName("legacy");

		assertThrows(IllegalArgumentException.class, () -> builder.addLegacyName("category"));
		assertThrows(IllegalArgumentException.class, () -> builder.addLegacyName("legacy"));
	}

	@Test
	public void addValueRejectsDuplicateNames() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		builder.addBoolean("enabled", false)
			.build();

		assertThrows(IllegalArgumentException.class, () -> builder.addBoolean("enabled", true));
	}

	@Test
	public void addValueRejectsDuplicateLegacyNames() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		var valueBuilder = builder.addBoolean("enabled", false)
			.addLegacyName("oldEnabled");

		assertThrows(IllegalArgumentException.class, () -> valueBuilder.addLegacyName("enabled"));
		assertThrows(IllegalArgumentException.class, () -> valueBuilder.addLegacyName("oldEnabled"));
	}

	@Test
	public void addValueRejectsDuplicateLegacyValueMigrations() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		var valueBuilder = builder.addBoolean("enabled", false)
			.addLegacyValueMigration(Boolean::parseBoolean);

		assertThrows(IllegalStateException.class, () -> valueBuilder.addLegacyValueMigration(Boolean::parseBoolean));
	}

	@Test
	public void applyUpdatesNotifiesListenersAfterAllValuesUpdate() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true)
			.build();
		ConfigValue<Integer> count = builder.addInteger("count", 1, 0, 10)
			.build();
		ConfigSchema schema = createSchema(builder);
		List<String> valueChanges = new ArrayList<>();
		List<String> valueBatches = new ArrayList<>();
		List<String> schemaBatches = new ArrayList<>();
		enabled.addListener((oldValue, newValue) -> valueChanges.add("%s -> %s, count = %s".formatted(oldValue, newValue, count.getValue())));
		enabled.addBatchListener(changes -> valueBatches.add("value batch: %s, count = %s".formatted(changes.size(), count.getValue())));
		schema.addListener(changes -> schemaBatches.add("schema batch: %s, enabled = %s, count = %s".formatted(
			changes.size(),
			enabled.getValue(),
			count.getValue()
		)));

		List<? extends IAppliedConfigValueChange<?>> changes = schema.applyUpdates(List.of(
			enabled.createUpdate(false),
			count.createUpdate(3)
		));

		assertEquals(2, changes.size());
		assertFalse(enabled.getValue());
		assertEquals(3, count.getValue());
		assertEquals(List.of("true -> false, count = 3"), valueChanges);
		assertEquals(List.of("value batch: 2, count = 3"), valueBatches);
		assertEquals(List.of("schema batch: 2, enabled = false, count = 3"), schemaBatches);
	}

	@Test
	public void applyUpdatesValidatesAllUpdatesBeforeChangingValues() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true)
			.build();
		ConfigValue<Integer> count = builder.addInteger("count", 1, 0, 10)
			.build();
		ConfigSchema schema = createSchema(builder);

		assertThrows(IllegalArgumentException.class, () -> schema.applyUpdates(List.of(
			enabled.createUpdate(false),
			count.createUpdate(11)
		)));

		assertTrue(enabled.getValue());
		assertEquals(1, count.getValue());
	}

	@Test
	public void applyUpdatesRejectsDuplicateValueUpdates() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true)
			.build();
		ConfigSchema schema = createSchema(builder);

		assertThrows(IllegalArgumentException.class, () -> schema.applyUpdates(List.of(
			enabled.createUpdate(false),
			enabled.createUpdate(true)
		)));

		assertTrue(enabled.getValue());
	}

	@Test
	public void setNotifiesSchemaBatchListeners() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true)
			.build();
		ConfigSchema schema = createSchema(builder);
		List<String> schemaBatches = new ArrayList<>();
		schema.addListener(changes -> {
			IAppliedConfigValueChange<?> change = changes.getFirst();
			schemaBatches.add("%s: %s -> %s".formatted(change.configValue().getName(), change.oldValue(), change.newValue()));
		});

		assertTrue(enabled.set(false));

		assertEquals(List.of("enabled: true -> false"), schemaBatches);
	}

	@Test
	public void loadIfNeededNotifiesSchemaBatchListenersAfterAllValuesUpdate(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[category]",
			"enabled = false",
			"count = 3"
		));
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true)
			.build();
		ConfigValue<Integer> count = builder.addInteger("count", 1, 0, 10)
			.build();
		ConfigSchema schema = createSchema(path, builder);
		List<String> schemaBatches = new ArrayList<>();
		schema.addListener(changes -> schemaBatches.add(formatBatch(changes, enabled.getValue(), count.getValue())));

		schema.loadIfNeeded();

		assertFalse(enabled.getValue());
		assertEquals(3, count.getValue());
		assertEquals(List.of("enabled: true -> false, count: 1 -> 3; enabled = false; count = 3"), schemaBatches);
	}

	@Test
	public void buildCategoryRequiresValuesToBeBuilt() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		builder.addBoolean("enabled", true);

		assertThrows(IllegalStateException.class, () -> createSchema(builder));
	}

	@Test
	public void addIntegerRejectsInvalidDefaultsAndRanges() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");

		assertThrows(IllegalArgumentException.class, () -> builder.addInteger("tooHigh", 11, 0, 10));
		assertThrows(IllegalArgumentException.class, () -> builder.addInteger("invalidRange", 1, 10, 0));
	}

	@Test
	public void addLongRejectsInvalidDefaultsAndRanges() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");

		assertThrows(IllegalArgumentException.class, () -> builder.addLong("tooHigh", 11L, 0L, 10L));
		assertThrows(IllegalArgumentException.class, () -> builder.addLong("invalidRange", 1L, 10L, 0L));
	}

	@Test
	public void addDoubleRejectsInvalidDefaultsAndRanges() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");

		assertThrows(IllegalArgumentException.class, () -> builder.addDouble("tooHigh", 11.0, 0.0, 10.0));
		assertThrows(IllegalArgumentException.class, () -> builder.addDouble("invalidRange", 1.0, 10.0, 0.0));
		assertThrows(IllegalArgumentException.class, () -> builder.addDouble("infiniteDefault", Double.POSITIVE_INFINITY));
	}

	@Test
	public void addRestrictedEnumRejectsInvalidDefaultsAndValidValueLists() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");

		assertThrows(IllegalArgumentException.class, () -> builder.addEnum("invalidDefault", TestMode.ADVANCED, List.of(TestMode.STANDARD)));
		assertThrows(IllegalArgumentException.class, () -> builder.addEnum("emptyValidValues", TestMode.STANDARD, List.of()));
		assertThrows(IllegalArgumentException.class, () -> builder.addEnumList("emptyListValidValues", List.<TestMode>of(), List.of()));
	}

	private enum TestMode {
		STANDARD,
		ADVANCED
	}

	private static ConfigSchema createSchema(ConfigCategoryBuilder builder) {
		return createSchema(Path.of("test.ini"), builder);
	}

	private static ConfigSchema createSchema(Path path, ConfigCategoryBuilder builder) {
		return new ConfigSchema(
			path,
			List.of(builder),
			(command, delay) -> CompletableFuture.completedFuture(null)
		);
	}

	@SuppressWarnings("unchecked")
	private static <T> void assertListElementSerializer(
		ConfigValue<List<T>> configValue,
		String serializedValue,
		T expectedValue
	) {
		assertTrue(configValue.getSerializer() instanceof IConfigListValueSerializer<?>);
		IConfigListValueSerializer<T> listSerializer = (IConfigListValueSerializer<T>) configValue.getSerializer();
		IDeserializeResult<T> result = listSerializer.getElementSerializer()
			.deserialize(serializedValue);

		assertEquals(List.of(), result.getErrors());
		assertEquals(expectedValue, result.getResult().orElseThrow());
	}

	private static String formatBatch(
		List<? extends IAppliedConfigValueChange<?>> changes,
		boolean enabled,
		int count
	) {
		String formattedChanges = String.join(", ", changes.stream()
			.map(change -> "%s: %s -> %s".formatted(change.configValue().getName(), change.oldValue(), change.newValue()))
			.toList());
		return "%s; enabled = %s; count = %s".formatted(
			formattedChanges,
			enabled,
			count
		);
	}
}
