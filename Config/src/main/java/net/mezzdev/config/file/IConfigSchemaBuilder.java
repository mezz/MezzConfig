package net.mezzdev.config.file;

public interface IConfigSchemaBuilder {

	IConfigCategoryBuilder addCategory(String name);

	void addDisplayCategory(ConfigDisplayCategory category);

	IConfigSchema build();
}
