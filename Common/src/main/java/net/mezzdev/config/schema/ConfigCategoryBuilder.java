package net.mezzdev.config.schema;

import net.mezzdev.config.api.schema.IConfigCategoryBuilder;
import net.mezzdev.config.api.value.ConfigListOrdering;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import net.mezzdev.config.api.value.PackedColor;
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
import net.mezzdev.config.util.ErrorUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConfigCategoryBuilder extends ConfigEditorCategoryBuilder implements IConfigCategoryBuilder {
	private final List<ConfigValueBuilder<?>> valueBuilders = new ArrayList<>();
	private final List<ConfigValue<?>> values = new ArrayList<>();
	private final Set<String> valueNames = new LinkedHashSet<>();
	private final Map<ConfigValueReference, List<ConfigValueMigration<?>>> movedValueMigrations = new LinkedHashMap<>();
	private boolean built;

	public ConfigCategoryBuilder(String localizationPath, String name) {
		super(localizationPath, name);
	}

	ConfigCategoryBuilder(@Nullable ConfigSchemaBuilder schemaBuilder, String localizationPath, String name) {
		super(schemaBuilder, localizationPath, name);
	}

	public <T> ConfigValue<T> addValue(
		ConfigValue<T> value,
		Set<ConfigValueReference> legacyValueReferences,
		Map<ConfigValueReference, ConfigValueMigration<T>> legacyValueMigrations
	) {
		checkNotBuilt();
		this.values.add(value);
		for (ConfigValueReference reference : legacyValueReferences) {
			addMovedValueMigration(reference, ConfigValueMigration.deserialize(value));
		}
		for (Map.Entry<ConfigValueReference, ConfigValueMigration<T>> entry : legacyValueMigrations.entrySet()) {
			addMovedValueMigration(entry.getKey(), entry.getValue());
		}
		return value;
	}

	private void addMovedValueMigration(ConfigValueReference reference, ConfigValueMigration<?> migration) {
		movedValueMigrations.computeIfAbsent(reference, key -> new ArrayList<>())
			.add(migration);
	}

	@Override
	public <T> ConfigValueBuilder<T> addValue(String name, T defaultValue, IConfigValueSerializer<T> serializer) {
		return addValueBuilder(new ConfigValueBuilder<>(this, getLocalizationKey(), name, defaultValue, serializer));
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
		return addList(name, defaultValue, serializer);
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
		return addList(name, defaultValue, serializer);
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
	public ConfigValueBuilder<PackedColor> addColor(String name, PackedColor defaultValue) {
		return addValue(name, defaultValue, ColorSerializer.INSTANCE);
	}

	@Override
	public ConfigValueBuilder<List<PackedColor>> addColorList(String name, List<PackedColor> defaultValue) {
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
		return addList(name, defaultValue, elementSerializer, ConfigListOrdering.ORDERED);
	}

	@Override
	public <T> ConfigValueBuilder<List<T>> addList(
		String name,
		List<T> defaultValue,
		IConfigValueSerializer<T> elementSerializer,
		ConfigListOrdering ordering
	) {
		return addValue(name, defaultValue, new ListSerializer<>(elementSerializer, ordering));
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
		if (valueBuilders.isEmpty()) {
			throw new IllegalStateException("Config category must have at least one config value: " + getName());
		}
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
		return new ConfigCategory(getLocalizationKey(), getName(), values, movedValueMigrations);
	}

	public void resolveEditorCategories(
		List<ConfigEditorCategoryBuilder> categoryBuilders,
		Map<ConfigEditorCategoryBuilder, ? extends ConfigEditorCategory> categories
	) {
		for (ConfigValue<?> value : values) {
			value.resolveEditorCategories(categoryBuilders, categories);
		}
	}

	private void checkNotBuilt() {
		if (built) {
			throw new IllegalStateException("Config category has already been built: " + getName());
		}
	}
}
