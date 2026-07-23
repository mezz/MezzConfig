package net.mezzdev.config.value;

import net.minecraft.resources.ResourceLocation;

/**
 * Identifies the kind of editor a config screen should use for a config value.
 *
 * @since 19.39.0
 */
public final class ConfigValueEditorType<T> {
	public static <T> ConfigValueEditorType<T> create(String namespace, String path) {
		ResourceLocation uid = ResourceLocation.fromNamespaceAndPath(namespace, path);
		return new ConfigValueEditorType<>(uid);
	}

	private final ResourceLocation uid;

	@SuppressWarnings("ConstantValue")
	public ConfigValueEditorType(ResourceLocation uid) {
		if (uid == null) {
			throw new NullPointerException("uid must not be null.");
		}
		this.uid = uid;
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
