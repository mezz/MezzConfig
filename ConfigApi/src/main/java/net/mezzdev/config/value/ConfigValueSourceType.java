package net.mezzdev.config.value;

import net.minecraft.resources.ResourceLocation;

/**
 * Identifies a source of config values.
 *
 * @since 19.39.0
 */
public final class ConfigValueSourceType<T extends IConfigValueSource> {
	public static <T extends IConfigValueSource> ConfigValueSourceType<T> create(String namespace, String path) {
		ResourceLocation uid = ResourceLocation.fromNamespaceAndPath(namespace, path);
		return new ConfigValueSourceType<>(uid);
	}

	private final ResourceLocation uid;

	@SuppressWarnings("ConstantValue")
	public ConfigValueSourceType(ResourceLocation uid) {
		if (uid == null) {
			throw new NullPointerException("uid must not be null.");
		}
		this.uid = uid;
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
