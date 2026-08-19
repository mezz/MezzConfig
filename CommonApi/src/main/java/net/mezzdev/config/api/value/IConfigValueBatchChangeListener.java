package net.mezzdev.config.api.value;

import java.util.List;

/**
 * Listener for an immutable batch of applied config value changes.
 *
 * @since 0.3.0
 */
@FunctionalInterface
public interface IConfigValueBatchChangeListener {
	/**
	 * Called after the complete batch has been applied.
	 *
	 * @param changes non-empty immutable list of applied changes
	 *
	 * @since 0.3.0
	 */
	void onConfigValuesChanged(List<? extends IAppliedConfigValueChange<?>> changes);
}
