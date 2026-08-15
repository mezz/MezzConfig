package net.mezzdev.config.server;

import net.mezzdev.config.util.ErrorUtil;

public record ServerConfigValueData(
	String categoryName,
	String valueName,
	String serializedEffectiveValue,
	String serializedPendingValue
) {
	public ServerConfigValueData(String categoryName, String valueName, String serializedValue) {
		this(categoryName, valueName, serializedValue, serializedValue);
	}

	public ServerConfigValueData {
		categoryName = ErrorUtil.checkNotNull(categoryName, "categoryName");
		valueName = ErrorUtil.checkNotNull(valueName, "valueName");
		serializedEffectiveValue = ErrorUtil.checkNotNull(serializedEffectiveValue, "serializedEffectiveValue");
		serializedPendingValue = ErrorUtil.checkNotNull(serializedPendingValue, "serializedPendingValue");
	}
}
