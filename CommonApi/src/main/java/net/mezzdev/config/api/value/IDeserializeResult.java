package net.mezzdev.config.api.value;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Optional;

/**
 * The result of deserializing a config value.
 * If deserialization is successful, {@link #getResult()} returns a value.
 * Otherwise, {@link #getErrors()} returns one or more errors.
 * <p>
 * Create a result with {@link #success(Object)}, {@link #failure(String)},
 * {@link #failure(List)}, or {@link #of(Object, List)} and returned from
 * {@link IConfigValueSerializer#deserialize(String)}.
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IDeserializeResult<T> {
	/**
	 * Create a successful deserialization result.
	 *
	 * @since 0.1.0
	 */
	static <T> IDeserializeResult<T> success(T result) {
		return of(result, List.of());
	}

	/**
	 * Create a failed deserialization result with one error message.
	 *
	 * @since 0.1.0
	 */
	static <T> IDeserializeResult<T> failure(String error) {
		return of(null, List.of(error));
	}

	/**
	 * Create a failed deserialization result with zero or more error messages.
	 *
	 * @since 0.1.0
	 */
	static <T> IDeserializeResult<T> failure(List<String> errors) {
		return of(null, errors);
	}

	/**
	 * Create a deserialization result with zero or more error messages.
	 *
	 * @since 0.1.0
	 */
	static <T> IDeserializeResult<T> of(@Nullable T result, List<String> errors) {
		List<String> errorsCopy = List.copyOf(errors);
		return new IDeserializeResult<>() {
			@Override
			public Optional<T> getResult() {
				return Optional.ofNullable(result);
			}

			@Override
			@Unmodifiable
			public List<String> getErrors() {
				return errorsCopy;
			}
		};
	}

	/**
	 * The successful deserialization result, or {@link Optional#empty()} if deserialization failed.
	 *
	 * @since 0.1.0
	 */
	Optional<T> getResult();

	/**
	 * A list of errors from deserialization.
	 * On successful deserialization this is an empty list.
	 *
	 * @since 0.1.0
	 */
	@Unmodifiable
	List<String> getErrors();
}
