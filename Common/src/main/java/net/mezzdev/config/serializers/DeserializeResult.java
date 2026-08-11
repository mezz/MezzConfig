package net.mezzdev.config.serializers;

import net.mezzdev.config.api.value.DeserializeResultState;
import net.mezzdev.config.api.value.IDeserializeResult;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Standard result implementation for config value deserialization.
 */
public final class DeserializeResult<T> implements IDeserializeResult<T> {
	private final DeserializeResultState state;
	private final @Nullable T result;
	private final List<String> diagnostics;

	private DeserializeResult(IDeserializeResult<T> result) {
		this.state = result.getState();
		this.result = result.getResult().orElse(null);
		this.diagnostics = result.getDiagnostics();
	}

	public static <T> DeserializeResult<T> success(T result) {
		return new DeserializeResult<>(IDeserializeResult.success(result));
	}

	public static <T> DeserializeResult<T> partialSuccess(T result, List<String> diagnostics) {
		return new DeserializeResult<>(IDeserializeResult.partialSuccess(result, diagnostics));
	}

	public static <T> DeserializeResult<T> failure(String diagnostic) {
		return new DeserializeResult<>(IDeserializeResult.failure(diagnostic));
	}

	public static <T> DeserializeResult<T> failure(List<String> diagnostics) {
		return new DeserializeResult<>(IDeserializeResult.failure(diagnostics));
	}

	@Override
	public DeserializeResultState getState() {
		return state;
	}

	@Override
	public Optional<T> getResult() {
		return Optional.ofNullable(result);
	}

	@Override
	@Unmodifiable
	public List<String> getDiagnostics() {
		return diagnostics;
	}
}
