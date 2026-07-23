package net.mezzdev.config.file;

record ConfigValueReference(
	String categoryName,
	String valueName
) {
	ConfigValueReference {
		if (categoryName == null) {
			throw new NullPointerException("categoryName must not be null.");
		}
		if (valueName == null) {
			throw new NullPointerException("valueName must not be null.");
		}
	}
}
