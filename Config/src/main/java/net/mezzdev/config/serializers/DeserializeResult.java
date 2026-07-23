package net.mezzdev.config.serializers;

import net.mezzdev.config.value.IConfigValueSerializer;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Standard result implementation for config value deserialization.
 */
public final class DeserializeResult<T> implements IConfigValueSerializer.IDeserializeResult<T> {
	private final @Nullable T result;
	private final List<String> errors;

	/**
	 * Create a successful deserialization result.
	 */
	public DeserializeResult(T result) {
		this(result, List.of());
	}

	/**
	 * Create a failed deserialization result with one error message.
	 */
	public DeserializeResult(@Nullable T result, String error) {
		this(result, List.of(error));
	}

	/**
	 * Create a deserialization result with zero or more error messages.
	 */
	public DeserializeResult(@Nullable T result, List<String> errors) {
		this.result = result;
		this.errors = List.copyOf(errors);
	}

	@Override
	public Optional<T> getResult() {
		return Optional.ofNullable(result);
	}

	@Override
	@Unmodifiable
	public List<String> getErrors() {
		return errors;
	}
}
