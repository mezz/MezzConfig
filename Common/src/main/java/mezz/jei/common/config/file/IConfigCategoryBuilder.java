package mezz.jei.common.config.file;

import mezz.jei.api.runtime.config.ConfigValueUpdateType;
import mezz.jei.api.runtime.config.IJeiConfigValueSerializer;

import java.util.List;

public interface IConfigCategoryBuilder {
	<T> ConfigValue<T> addValue(String path, T defaultValue, IJeiConfigValueSerializer<T> serializer, ConfigValueUpdateType updateType);
	ConfigValue<Boolean> addBoolean(String path, boolean defaultValue, ConfigValueUpdateType updateType);
	ConfigValue<Integer> addInteger(String path, int defaultValue, int minValue, int maxValue, ConfigValueUpdateType updateType);
	<T extends Enum<T>> ConfigValue<T> addEnum(String path, T defaultValue, ConfigValueUpdateType updateType);
	<T> ConfigValue<List<T>> addList(String path, List<T> defaultValue, IJeiConfigValueSerializer<List<T>> listSerializer, ConfigValueUpdateType updateType);
}
