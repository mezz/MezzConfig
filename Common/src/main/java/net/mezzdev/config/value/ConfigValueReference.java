package net.mezzdev.config.value;

import net.mezzdev.config.util.ErrorUtil;

public record ConfigValueReference(
	String categoryName,
	String valueName
) {
	public ConfigValueReference {
		categoryName = ErrorUtil.checkNotNull(categoryName, "categoryName");
		valueName = ErrorUtil.checkNotNull(valueName, "valueName");
	}
}
