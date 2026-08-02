package net.mezzdev.config.util;

import java.util.regex.Pattern;

public final class ConfigNameUtil {
	private static final Pattern CONFIG_NAME_PATTERN = Pattern.compile("\\w+");

	private ConfigNameUtil() {

	}

	public static String validateConfigName(String value, String name) {
		value = ErrorUtil.checkNotNull(value, name);
		if (!CONFIG_NAME_PATTERN.matcher(value).matches()) {
			throw new IllegalArgumentException(name + " must contain only letters, numbers, and underscores: " + value);
		}
		return value;
	}
}
