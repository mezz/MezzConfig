package net.mezzdev.config.api.value;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The result of deserializing a config value.
 * <p>
 * A result is always in exactly one of these states:
 * <ul>
 *     <li>{@link DeserializeResultState#SUCCESS}: a non-null result and no diagnostics;</li>
 *     <li>{@link DeserializeResultState#PARTIAL_SUCCESS}: a non-null, usable result and one or more diagnostics;</li>
 *     <li>{@link DeserializeResultState#FAILURE}: no result and one or more diagnostics.</li>
 * </ul>
 * Create a result with {@link #success(Object)}, {@link #partialSuccess(Object, String)},
 * {@link #partialSuccess(Object, List)}, {@link #failure(String)}, or {@link #failure(List)} and return it from
 * {@link IConfigValueSerializer#deserialize(String)}.
 *
 * @param <T> effectively immutable deserialized value type
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
		return create(DeserializeResultState.SUCCESS, Objects.requireNonNull(result, "result"), List.of());
	}

	/**
	 * Create a partially successful deserialization result with one diagnostic message.
	 *
	 * @since 0.1.0
	 */
	static <T> IDeserializeResult<T> partialSuccess(T result, String diagnostic) {
		return partialSuccess(result, List.of(Objects.requireNonNull(diagnostic, "diagnostic")));
	}

	/**
	 * Create a partially successful deserialization result with one or more diagnostic messages.
	 *
	 * @since 0.1.0
	 */
	static <T> IDeserializeResult<T> partialSuccess(T result, List<String> diagnostics) {
		return create(
			DeserializeResultState.PARTIAL_SUCCESS,
			Objects.requireNonNull(result, "result"),
			diagnostics
		);
	}

	/**
	 * Create a failed deserialization result with one diagnostic message.
	 *
	 * @since 0.1.0
	 */
	static <T> IDeserializeResult<T> failure(String diagnostic) {
		return failure(List.of(Objects.requireNonNull(diagnostic, "diagnostic")));
	}

	/**
	 * Create a failed deserialization result with one or more diagnostic messages.
	 *
	 * @since 0.1.0
	 */
	static <T> IDeserializeResult<T> failure(List<String> diagnostics) {
		return create(DeserializeResultState.FAILURE, null, diagnostics);
	}

	private static <T> IDeserializeResult<T> create(
		DeserializeResultState state,
		@Nullable T result,
		List<String> diagnostics
	) {
		List<String> diagnosticsCopy = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
		if (state == DeserializeResultState.SUCCESS && !diagnosticsCopy.isEmpty()) {
			throw new IllegalArgumentException("Successful results must not have diagnostics.");
		}
		if (state != DeserializeResultState.SUCCESS && diagnosticsCopy.isEmpty()) {
			throw new IllegalArgumentException("Partial and failed results must have at least one diagnostic.");
		}
		if (diagnosticsCopy.stream().anyMatch(String::isBlank)) {
			throw new IllegalArgumentException("Diagnostics must not be blank.");
		}
		Optional<T> optionalResult = Optional.ofNullable(result);
		if (state == DeserializeResultState.FAILURE && optionalResult.isPresent()) {
			throw new IllegalArgumentException("Failed results must not have a result.");
		}
		if (state != DeserializeResultState.FAILURE && optionalResult.isEmpty()) {
			throw new IllegalArgumentException("Successful and partial results must have a result.");
		}
		return new IDeserializeResult<>() {
			@Override
			public DeserializeResultState getState() {
				return state;
			}

			@Override
			public Optional<T> getResult() {
				return optionalResult;
			}

			@Override
			@Unmodifiable
			public List<String> getDiagnostics() {
				return diagnosticsCopy;
			}
		};
	}

	/**
	 * Get the explicit state of this result.
	 *
	 * @since 0.1.0
	 */
	DeserializeResultState getState();

	/**
	 * The usable deserialization result, or {@link Optional#empty()} if deserialization failed.
	 *
	 * @since 0.1.0
	 */
	Optional<T> getResult();

	/**
	 * Diagnostics produced while deserializing.
	 * <p>
	 * This is empty for {@link DeserializeResultState#SUCCESS} and non-empty for
	 * {@link DeserializeResultState#PARTIAL_SUCCESS} and {@link DeserializeResultState#FAILURE}.
	 *
	 * @since 0.1.0
	 */
	@Unmodifiable
	List<String> getDiagnostics();
}
