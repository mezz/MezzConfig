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
 * A result always has exactly one of these outcomes:
 * <ul>
 *     <li>success: a non-null result and no diagnostics;</li>
 *     <li>partial success: a non-null, usable result and one or more diagnostics;</li>
 *     <li>failure: no result and one or more diagnostics.</li>
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
		return create(Objects.requireNonNull(result, "result"), List.of());
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
			Objects.requireNonNull(result, "result"),
			requireDiagnostics(diagnostics, "Partial")
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
		return create(null, requireDiagnostics(diagnostics, "Failed"));
	}

	private static List<String> requireDiagnostics(List<String> diagnostics, String outcome) {
		List<String> diagnosticsCopy = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
		if (diagnosticsCopy.isEmpty()) {
			throw new IllegalArgumentException(outcome + " results must have at least one diagnostic.");
		}
		if (diagnosticsCopy.stream().anyMatch(String::isBlank)) {
			throw new IllegalArgumentException("Diagnostics must not be blank.");
		}
		return diagnosticsCopy;
	}

	private static <T> IDeserializeResult<T> create(
		@Nullable T result,
		List<String> diagnostics
	) {
		Optional<T> optionalResult = Optional.ofNullable(result);
		return new IDeserializeResult<>() {
			@Override
			public Optional<T> getResult() {
				return optionalResult;
			}

			@Override
			@Unmodifiable
			public List<String> getDiagnostics() {
				return diagnostics;
			}
		};
	}

	/**
	 * The usable deserialization result, or {@link Optional#empty()} if deserialization failed.
	 *
	 * @since 0.1.0
	 */
	Optional<T> getResult();

	/**
	 * Diagnostics produced while deserializing.
	 * <p>
	 * This is empty for success and non-empty for partial success and failure.
	 *
	 * @since 0.1.0
	 */
	@Unmodifiable
	List<String> getDiagnostics();
}
