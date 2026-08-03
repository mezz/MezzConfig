package net.mezzdev.config.api.schema;

import net.mezzdev.config.api.value.IConfigValueBuilder;
import net.mezzdev.config.api.value.IConfigListValueSerializer;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * Builds config values for one config category.
 * <p>
 * Create a builder for your category here: {@link IConfigSchemaBuilder#addCategory(String)}.
 * Value methods return a builder. Add any optional legacy migrations, then call {@link IConfigValueBuilder#build()}.
 * List value helpers create values whose serializers implement {@link IConfigListValueSerializer}.
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigCategoryBuilder {
	/**
	 * Create a builder for a config value with a custom serializer.
	 * Use this method with an {@link IConfigListValueSerializer} for custom list storage formats that expose their
	 * element serializer.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 * @param serializer serializer for reading, writing, and validation
	 *
	 * @since 0.1.0
	 */
	<T> IConfigValueBuilder<T> addValue(String name, T defaultValue, IConfigValueSerializer<T> serializer);

	/**
	 * Create a builder for a boolean config value.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 *
	 * @since 0.1.0
	 */
	IConfigValueBuilder<Boolean> addBoolean(String name, boolean defaultValue);

	/**
	 * Create a builder for a list config value containing booleans.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 *
	 * @since 0.1.0
	 */
	IConfigValueBuilder<List<Boolean>> addBooleanList(String name, List<Boolean> defaultValue);

	/**
	 * Create a builder for a string config value.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 *
	 * @since 0.1.0
	 */
	IConfigValueBuilder<String> addString(String name, String defaultValue);

	/**
	 * Create a builder for a list config value containing strings.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 *
	 * @since 0.1.0
	 */
	IConfigValueBuilder<List<String>> addStringList(String name, List<String> defaultValue);

	/**
	 * Create a builder for an integer config value.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 *
	 * @since 0.1.0
	 */
	IConfigValueBuilder<Integer> addInteger(String name, int defaultValue);

	/**
	 * Create a builder for a bounded integer config value.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 * @param minValue smallest valid value
	 * @param maxValue largest valid value
	 *
	 * @since 0.1.0
	 */
	IConfigValueBuilder<Integer> addInteger(String name, int defaultValue, int minValue, int maxValue);

	/**
	 * Create a builder for a list config value containing integers.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 *
	 * @since 0.1.0
	 */
	IConfigValueBuilder<List<Integer>> addIntegerList(String name, List<Integer> defaultValue);

	/**
	 * Create a builder for a list config value containing bounded integers.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 * @param minValue smallest valid value
	 * @param maxValue largest valid value
	 *
	 * @since 0.1.0
	 */
	IConfigValueBuilder<List<Integer>> addIntegerList(String name, List<Integer> defaultValue, int minValue, int maxValue);

	/**
	 * Create a builder for an ARGB color config value stored as an integer and serialized as {@code 0xAARRGGBB}.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 *
	 * @since 0.1.0
	 */
	IConfigValueBuilder<Integer> addColor(String name, int defaultValue);

	/**
	 * Create a builder for a list config value containing ARGB colors stored as integers and serialized as
	 * {@code 0xAARRGGBB}.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 *
	 * @since 0.1.0
	 */
	IConfigValueBuilder<List<Integer>> addColorList(String name, List<Integer> defaultValue);

	/**
	 * Create a builder for a long config value.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 *
	 * @since 0.1.0
	 */
	IConfigValueBuilder<Long> addLong(String name, long defaultValue);

	/**
	 * Create a builder for a bounded long config value.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 * @param minValue smallest valid value
	 * @param maxValue largest valid value
	 *
	 * @since 0.1.0
	 */
	IConfigValueBuilder<Long> addLong(String name, long defaultValue, long minValue, long maxValue);

	/**
	 * Create a builder for a list config value containing longs.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 *
	 * @since 0.1.0
	 */
	IConfigValueBuilder<List<Long>> addLongList(String name, List<Long> defaultValue);

	/**
	 * Create a builder for a list config value containing bounded longs.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 * @param minValue smallest valid value
	 * @param maxValue largest valid value
	 *
	 * @since 0.1.0
	 */
	IConfigValueBuilder<List<Long>> addLongList(String name, List<Long> defaultValue, long minValue, long maxValue);

	/**
	 * Create a builder for a finite double config value.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 *
	 * @since 0.1.0
	 */
	IConfigValueBuilder<Double> addDouble(String name, double defaultValue);

	/**
	 * Create a builder for a bounded finite double config value.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 * @param minValue smallest valid value
	 * @param maxValue largest valid value
	 *
	 * @since 0.1.0
	 */
	IConfigValueBuilder<Double> addDouble(String name, double defaultValue, double minValue, double maxValue);

	/**
	 * Create a builder for a list config value containing finite doubles.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 *
	 * @since 0.1.0
	 */
	IConfigValueBuilder<List<Double>> addDoubleList(String name, List<Double> defaultValue);

	/**
	 * Create a builder for a list config value containing bounded finite doubles.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 * @param minValue smallest valid value
	 * @param maxValue largest valid value
	 *
	 * @since 0.1.0
	 */
	IConfigValueBuilder<List<Double>> addDoubleList(String name, List<Double> defaultValue, double minValue, double maxValue);

	/**
	 * Create a builder for an enum config value.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 *
	 * @since 0.1.0
	 */
	<T extends Enum<T>> IConfigValueBuilder<T> addEnum(String name, T defaultValue);

	/**
	 * Create a builder for an enum config value with a restricted set of valid values.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 * @param validValues non-empty list of valid enum values
	 *
	 * @since 0.1.0
	 */
	<T extends Enum<T>> IConfigValueBuilder<T> addEnum(String name, T defaultValue, List<T> validValues);

	/**
	 * Create a builder for a list config value containing enum values.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 * @param enumClass enum class for serializing and validating list values
	 *
	 * @since 0.1.0
	 */
	<T extends Enum<T>> IConfigValueBuilder<List<T>> addEnumList(
		String name,
		List<T> defaultValue,
		Class<T> enumClass
	);

	/**
	 * Create a builder for a list config value containing enum values with a restricted set of valid values.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 * @param validValues non-empty list of valid enum values
	 *
	 * @since 0.1.0
	 */
	<T extends Enum<T>> IConfigValueBuilder<List<T>> addEnumList(
		String name,
		List<T> defaultValue,
		List<T> validValues
	);

	/**
	 * Create a builder for a list config value using a serializer for each list element.
	 * <p>
	 * The resulting config value's serializer implements {@link IConfigListValueSerializer}, so callers can inspect
	 * the element serializer.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 * @param elementSerializer serializer for each list element
	 *
	 * @since 0.1.0
	 */
	<T> IConfigValueBuilder<List<T>> addList(
		String name,
		List<T> defaultValue,
		IConfigValueSerializer<T> elementSerializer
	);
}
