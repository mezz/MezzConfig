package net.mezzdev.config.server;

import net.mezzdev.config.util.ErrorUtil;

public record ServerConfigValueData(String categoryName, String valueName, String serializedValue) {
	public ServerConfigValueData {
		categoryName = ErrorUtil.checkNotNull(categoryName, "categoryName");
		valueName = ErrorUtil.checkNotNull(valueName, "valueName");
		serializedValue = ErrorUtil.checkNotNull(serializedValue, "serializedValue");
	}
}
