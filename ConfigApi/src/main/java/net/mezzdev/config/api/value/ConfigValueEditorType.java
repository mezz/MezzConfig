package net.mezzdev.config.api.value;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Identifies the kind of editor a config screen should use for a config value.
 *
 * @since 19.39.0
 */
public final class ConfigValueEditorType<T> {
	/**
	 * Create a config value editor type.
	 *
	 * @param namespace the namespace for this editor type
	 * @param path the path for this editor type
	 *
	 * @since 19.39.0
	 */
	public static <T> ConfigValueEditorType<T> create(String namespace, String path) {
		ResourceLocation uid = ResourceLocation.fromNamespaceAndPath(namespace, path);
		return create(uid);
	}

	/**
	 * Create a config value editor type.
	 *
	 * @param uid the unique id for this editor type
	 *
	 * @since 19.39.0
	 */
	public static <T> ConfigValueEditorType<T> create(ResourceLocation uid) {
		return new ConfigValueEditorType<>(uid);
	}

	private final ResourceLocation uid;

	private ConfigValueEditorType(ResourceLocation uid) {
		this.uid = Objects.requireNonNull(uid, "uid");
	}

	/**
	 * The unique id of this editor type.
	 *
	 * @since 19.39.0
	 */
	public ResourceLocation getUid() {
		return uid;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || obj.getClass() != ConfigValueEditorType.class) {
			return false;
		}
		ConfigValueEditorType<?> other = (ConfigValueEditorType<?>) obj;
		return uid.equals(other.uid);
	}

	@Override
	public int hashCode() {
		return uid.hashCode();
	}

	@Override
	public String toString() {
		return "ConfigValueEditorType[" +
			"uid=" + uid + ']';
	}
}
