package net.mezzdev.config.file;

import net.mezzdev.config.ConfigValueUpdateType;
import net.mezzdev.config.IJeiConfigValueSerializer;
import net.mezzdev.config.file.serializers.BooleanSerializer;
import net.mezzdev.config.file.serializers.EnumSerializer;
import net.mezzdev.config.file.serializers.IntegerSerializer;

import java.util.ArrayList;
import java.util.List;

public class ConfigCategoryBuilder implements IConfigCategoryBuilder {
	private final String name;
	private final String localizationPath;
	private final List<ConfigValue<?>> values = new ArrayList<>();

	public ConfigCategoryBuilder(String localizationPath, String name) {
		this.name = name;
		this.localizationPath = localizationPath + "." + name;
	}

	public String getName() {
		return name;
	}

	public <T> ConfigValue<T> addValue(ConfigValue<T> value) {
		this.values.add(value);
		return value;
	}

	@Override
	public <T> ConfigValue<T> addValue(String name, T defaultValue, IJeiConfigValueSerializer<T> serializer, ConfigValueUpdateType updateType) {
		return addValue(new ConfigValue<>(localizationPath, name, defaultValue, serializer, updateType));
	}

	@Override
	public ConfigValue<Boolean> addBoolean(String name, boolean defaultValue, ConfigValueUpdateType updateType) {
		return addValue(name, defaultValue, BooleanSerializer.INSTANCE, updateType);
	}

	@Override
	public <T extends Enum<T>> ConfigValue<T> addEnum(String name, T defaultValue, ConfigValueUpdateType updateType) {
		EnumSerializer<T> serializer = new EnumSerializer<>(defaultValue.getDeclaringClass());
		return addValue(name, defaultValue, serializer, updateType);
	}

	@Override
	public ConfigValue<Integer> addInteger(String name, int defaultValue, int minValue, int maxValue, ConfigValueUpdateType updateType) {
		IntegerSerializer serializer = new IntegerSerializer(minValue, maxValue);
		return addValue(name, defaultValue, serializer, updateType);
	}

	@Override
	public <T> ConfigValue<List<T>> addList(String name, List<T> defaultValue, IJeiConfigValueSerializer<List<T>> listSerializer, ConfigValueUpdateType updateType) {
		return addValue(name, defaultValue, listSerializer, updateType);
	}

	public ConfigCategory build(ConfigSchema schema) {
		for (ConfigValue<?> value : values) {
			value.setSchema(schema);
		}
		return new ConfigCategory(localizationPath, name, values);
	}
}
