package net.mezzdev.config.api.value.change;

import java.util.List;

/**
 * Reacts to related config value changes after the complete batch is available.
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
