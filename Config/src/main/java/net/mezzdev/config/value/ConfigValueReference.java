package net.mezzdev.config.value;

public record ConfigValueReference(
	String categoryName,
	String valueName
) {
	public ConfigValueReference {
		if (categoryName == null) {
			throw new NullPointerException("categoryName must not be null.");
		}
		if (valueName == null) {
			throw new NullPointerException("valueName must not be null.");
		}
	}
}
