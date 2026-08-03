package net.mezzdev.config.schema;

import net.mezzdev.config.api.schema.IConfigCategoryBuilder;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import net.mezzdev.config.serializers.BooleanSerializer;
import net.mezzdev.config.serializers.ColorSerializer;
import net.mezzdev.config.serializers.DoubleSerializer;
import net.mezzdev.config.serializers.EnumSerializer;
import net.mezzdev.config.serializers.IntegerSerializer;
import net.mezzdev.config.serializers.ListSerializer;
import net.mezzdev.config.serializers.LongSerializer;
import net.mezzdev.config.serializers.StringSerializer;
import net.mezzdev.config.value.ConfigValue;
import net.mezzdev.config.value.ConfigValueBuilder;
import net.mezzdev.config.value.ConfigValueMigration;
import net.mezzdev.config.value.ConfigValueReference;
import net.mezzdev.config.util.ConfigNameUtil;
import net.mezzdev.config.util.ErrorUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class ConfigCategoryBuilder implements IConfigCategoryBuilder {
	private final String name;
	private final String localizationPath;
	private final List<ConfigValueBuilder<?>> valueBuilders = new ArrayList<>();
	private final List<ConfigValue<?>> values = new ArrayList<>();
	private final Set<String> valueNames = new LinkedHashSet<>();
	private final Map<ConfigValueReference, List<ConfigValueMigration<?>>> movedValueMigrations = new LinkedHashMap<>();
	private boolean built;

	public ConfigCategoryBuilder(String localizationPath, String name) {
		this.name = ConfigNameUtil.validateConfigName(name, "categoryName");
		localizationPath = ErrorUtil.checkNotNull(localizationPath, "localizationPath");
		this.localizationPath = localizationPath + "." + this.name;
	}

	public String getName() {
		return name;
	}

	public <T> ConfigValue<T> addValue(
		ConfigValue<T> value,
		Set<ConfigValueReference> legacyValueReferences,
		Map<ConfigValueReference, Function<String, T>> legacyValueMigrations,
		@Nullable Function<String, T> currentValueMigration
	) {
		checkNotBuilt();
		this.values.add(value);
		if (currentValueMigration != null) {
			ConfigValueReference reference = new ConfigValueReference(name, value.getName());
			addMovedValueMigration(reference, ConfigValueMigration.migrate(value, currentValueMigration));
		}
		for (ConfigValueReference reference : legacyValueReferences) {
			addMovedValueMigration(reference, ConfigValueMigration.deserialize(value));
		}
		for (Map.Entry<ConfigValueReference, Function<String, T>> entry : legacyValueMigrations.entrySet()) {
			addMovedValueMigration(entry.getKey(), ConfigValueMigration.migrate(value, entry.getValue()));
		}
		return value;
	}

	private void addMovedValueMigration(ConfigValueReference reference, ConfigValueMigration<?> migration) {
		movedValueMigrations.computeIfAbsent(reference, key -> new ArrayList<>())
			.add(migration);
	}

	@Override
	public <T> ConfigValueBuilder<T> addValue(String name, T defaultValue, IConfigValueSerializer<T> serializer) {
		return addValueBuilder(new ConfigValueBuilder<>(this, localizationPath, name, defaultValue, serializer));
	}

	@Override
	public ConfigValueBuilder<Boolean> addBoolean(String name, boolean defaultValue) {
		return addValue(name, defaultValue, BooleanSerializer.INSTANCE);
	}

	@Override
	public ConfigValueBuilder<List<Boolean>> addBooleanList(String name, List<Boolean> defaultValue) {
		return addList(name, defaultValue, BooleanSerializer.INSTANCE);
	}

	@Override
	public ConfigValueBuilder<String> addString(String name, String defaultValue) {
		return addValue(name, defaultValue, StringSerializer.INSTANCE);
	}

	@Override
	public ConfigValueBuilder<List<String>> addStringList(String name, List<String> defaultValue) {
		return addList(name, defaultValue, StringSerializer.INSTANCE);
	}

	@Override
	public <T extends Enum<T>> ConfigValueBuilder<T> addEnum(String name, T defaultValue) {
		ErrorUtil.checkNotNull(defaultValue, "defaultValue");
		EnumSerializer<T> serializer = new EnumSerializer<>(defaultValue.getDeclaringClass());
		return addValue(name, defaultValue, serializer);
	}

	@Override
	public <T extends Enum<T>> ConfigValueBuilder<T> addEnum(String name, T defaultValue, List<T> validValues) {
		ErrorUtil.checkNotNull(defaultValue, "defaultValue");
		EnumSerializer<T> serializer = new EnumSerializer<>(defaultValue.getDeclaringClass(), validValues);
		return addValue(name, defaultValue, serializer);
	}

	@Override
	public <T extends Enum<T>> ConfigValueBuilder<List<T>> addEnumList(
		String name,
		List<T> defaultValue,
		Class<T> enumClass
	) {
		EnumSerializer<T> serializer = new EnumSerializer<>(enumClass);
		return addValue(name, defaultValue, new ListSerializer<>(serializer));
	}

	@Override
	public <T extends Enum<T>> ConfigValueBuilder<List<T>> addEnumList(
		String name,
		List<T> defaultValue,
		List<T> validValues
	) {
		ErrorUtil.checkNotNull(validValues, "validValues");
		if (validValues.isEmpty()) {
			throw new IllegalArgumentException("validValues must not be empty.");
		}
		T firstValidValue = ErrorUtil.checkNotNull(validValues.getFirst(), "validValues[0]");
		EnumSerializer<T> serializer = new EnumSerializer<>(firstValidValue.getDeclaringClass(), validValues);
		return addValue(name, defaultValue, new ListSerializer<>(serializer));
	}

	@Override
	public ConfigValueBuilder<Integer> addInteger(String name, int defaultValue) {
		return addInteger(name, defaultValue, Integer.MIN_VALUE, Integer.MAX_VALUE);
	}

	@Override
	public ConfigValueBuilder<Integer> addInteger(String name, int defaultValue, int minValue, int maxValue) {
		IntegerSerializer serializer = new IntegerSerializer(minValue, maxValue);
		return addValue(name, defaultValue, serializer);
	}

	@Override
	public ConfigValueBuilder<List<Integer>> addIntegerList(String name, List<Integer> defaultValue) {
		return addIntegerList(name, defaultValue, Integer.MIN_VALUE, Integer.MAX_VALUE);
	}

	@Override
	public ConfigValueBuilder<List<Integer>> addIntegerList(String name, List<Integer> defaultValue, int minValue, int maxValue) {
		IntegerSerializer serializer = new IntegerSerializer(minValue, maxValue);
		return addList(name, defaultValue, serializer);
	}

	@Override
	public ConfigValueBuilder<Integer> addColor(String name, int defaultValue) {
		return addValue(name, defaultValue, ColorSerializer.INSTANCE);
	}

	@Override
	public ConfigValueBuilder<List<Integer>> addColorList(String name, List<Integer> defaultValue) {
		return addList(name, defaultValue, ColorSerializer.INSTANCE);
	}

	@Override
	public ConfigValueBuilder<Long> addLong(String name, long defaultValue) {
		return addLong(name, defaultValue, Long.MIN_VALUE, Long.MAX_VALUE);
	}

	@Override
	public ConfigValueBuilder<Long> addLong(String name, long defaultValue, long minValue, long maxValue) {
		LongSerializer serializer = new LongSerializer(minValue, maxValue);
		return addValue(name, defaultValue, serializer);
	}

	@Override
	public ConfigValueBuilder<List<Long>> addLongList(String name, List<Long> defaultValue) {
		return addLongList(name, defaultValue, Long.MIN_VALUE, Long.MAX_VALUE);
	}

	@Override
	public ConfigValueBuilder<List<Long>> addLongList(String name, List<Long> defaultValue, long minValue, long maxValue) {
		LongSerializer serializer = new LongSerializer(minValue, maxValue);
		return addList(name, defaultValue, serializer);
	}

	@Override
	public ConfigValueBuilder<Double> addDouble(String name, double defaultValue) {
		return addDouble(name, defaultValue, -Double.MAX_VALUE, Double.MAX_VALUE);
	}

	@Override
	public ConfigValueBuilder<Double> addDouble(String name, double defaultValue, double minValue, double maxValue) {
		DoubleSerializer serializer = new DoubleSerializer(minValue, maxValue);
		return addValue(name, defaultValue, serializer);
	}

	@Override
	public ConfigValueBuilder<List<Double>> addDoubleList(String name, List<Double> defaultValue) {
		return addDoubleList(name, defaultValue, -Double.MAX_VALUE, Double.MAX_VALUE);
	}

	@Override
	public ConfigValueBuilder<List<Double>> addDoubleList(String name, List<Double> defaultValue, double minValue, double maxValue) {
		DoubleSerializer serializer = new DoubleSerializer(minValue, maxValue);
		return addList(name, defaultValue, serializer);
	}

	@Override
	public <T> ConfigValueBuilder<List<T>> addList(String name, List<T> defaultValue, IConfigValueSerializer<T> elementSerializer) {
		return addValue(name, defaultValue, new ListSerializer<>(elementSerializer));
	}

	private <T> ConfigValueBuilder<T> addValueBuilder(ConfigValueBuilder<T> builder) {
		checkNotBuilt();
		if (!valueNames.add(builder.getName())) {
			throw new IllegalArgumentException("There is already a config value named: " + builder.getName());
		}
		this.valueBuilders.add(builder);
		return builder;
	}

	public ConfigCategory build(ConfigSchema schema) {
		checkNotBuilt();
		List<String> unbuiltValueNames = valueBuilders.stream()
			.filter(valueBuilder -> !valueBuilder.isBuilt())
			.map(ConfigValueBuilder::getName)
			.toList();
		if (!unbuiltValueNames.isEmpty()) {
			throw new IllegalStateException("Config values have not been built: " + String.join(", ", unbuiltValueNames));
		}
		this.built = true;
		for (ConfigValue<?> value : values) {
			value.setSchema(schema);
		}
		return new ConfigCategory(localizationPath, name, values, movedValueMigrations);
	}

	private void checkNotBuilt() {
		if (built) {
			throw new IllegalStateException("Config category has already been built: " + name);
		}
	}
}
