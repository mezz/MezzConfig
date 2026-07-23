package net.mezzdev.config.api.value;

import net.mezzdev.config.api.util.ErrorUtil;
import net.minecraft.resources.ResourceLocation;

/**
 * Identifies a source of config values.
 *
 * @since 19.39.0
 */
public final class ConfigValueSourceType<T extends IConfigValueSource> {
	/**
	 * Create a config value source type.
	 *
	 * @param namespace the namespace for this value source type
	 * @param path the path for this value source type
	 *
	 * @since 19.39.0
	 */
	public static <T extends IConfigValueSource> ConfigValueSourceType<T> create(String namespace, String path) {
		ResourceLocation uid = ResourceLocation.fromNamespaceAndPath(namespace, path);
		return create(uid);
	}

	/**
	 * Create a config value source type.
	 *
	 * @param uid the unique id for this value source type
	 *
	 * @since 19.39.0
	 */
	public static <T extends IConfigValueSource> ConfigValueSourceType<T> create(ResourceLocation uid) {
		return new ConfigValueSourceType<>(uid);
	}

	private final ResourceLocation uid;

	private ConfigValueSourceType(ResourceLocation uid) {
		this.uid = ErrorUtil.checkNotNull(uid, "uid");
	}

	/**
	 * The unique id of this config value source type.
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
		if (obj == null || obj.getClass() != ConfigValueSourceType.class) {
			return false;
		}
		ConfigValueSourceType<?> other = (ConfigValueSourceType<?>) obj;
		return uid.equals(other.uid);
	}

	@Override
	public int hashCode() {
		return uid.hashCode();
	}

	@Override
	public String toString() {
		return "ConfigValueSourceType[" +
			"uid=" + uid + ']';
	}
}
