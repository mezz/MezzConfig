package net.mezzdev.config;

import java.util.List;

/**
 * Standard editor types used by config screens.
 *
 * @since 19.39.0
 */
public final class ConfigValueEditorTypes {
	private static final String CONFIG_ID = "mezz_config";

	public static final ConfigValueEditorType<Boolean> BOOLEAN = ConfigValueEditorType.create(CONFIG_ID, "boolean");
	public static final ConfigValueEditorType<Integer> INTEGER = ConfigValueEditorType.create(CONFIG_ID, "integer");

	private static final ConfigValueEditorType<Object> SELECTION = ConfigValueEditorType.create(CONFIG_ID, "selection");
	private static final ConfigValueEditorType<List<Object>> LIST = ConfigValueEditorType.create(CONFIG_ID, "list");
	private static final ConfigValueEditorType<Object> ALIGNMENT = ConfigValueEditorType.create(CONFIG_ID, "alignment");
	private static final ConfigValueEditorType<Object> KEY_MAPPING = ConfigValueEditorType.create(CONFIG_ID, "key_mapping");
	private static final ConfigValueEditorType<Object> UNSUPPORTED = ConfigValueEditorType.create(CONFIG_ID, "unsupported");

	private ConfigValueEditorTypes() {
	}

	/**
	 * An editor for values with a finite set of valid options.
	 *
	 * @since 19.39.0
	 */
	@SuppressWarnings("unchecked")
	public static <T> ConfigValueEditorType<T> selection() {
		return (ConfigValueEditorType<T>) SELECTION;
	}

	/**
	 * An editor for ordered list values.
	 *
	 * @since 19.39.0
	 */
	@SuppressWarnings("unchecked")
	public static <T> ConfigValueEditorType<List<T>> list() {
		return (ConfigValueEditorType<List<T>>) (Object) LIST;
	}

	/**
	 * An editor for horizontal and vertical alignment values.
	 *
	 * @since 19.39.0
	 */
	@SuppressWarnings("unchecked")
	public static <T> ConfigValueEditorType<T> alignment() {
		return (ConfigValueEditorType<T>) ALIGNMENT;
	}

	/**
	 * An editor for key mapping values.
	 *
	 * @since 19.39.0
	 */
	@SuppressWarnings("unchecked")
	public static <T> ConfigValueEditorType<T> keyMapping() {
		return (ConfigValueEditorType<T>) KEY_MAPPING;
	}

	/**
	 * No standard editor is available for this config value.
	 *
	 * @since 19.39.0
	 */
	@SuppressWarnings("unchecked")
	public static <T> ConfigValueEditorType<T> unsupported() {
		return (ConfigValueEditorType<T>) UNSUPPORTED;
	}
}
