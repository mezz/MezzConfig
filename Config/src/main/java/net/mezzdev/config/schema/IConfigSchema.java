package net.mezzdev.config.schema;

import net.mezzdev.config.api.value.ConfigValueChange;
import net.mezzdev.config.api.value.ConfigValueUpdateType;
import net.mezzdev.config.file.FileWatcher;
import net.mezzdev.config.file.IConfigFileRegistrar;

import java.util.List;

public interface IConfigSchema extends net.mezzdev.config.api.schema.IConfigEditableSchema {
	void register(FileWatcher fileWatcher, IConfigFileRegistrar configFileRegistrar);

	void loadIfNeeded();

	void markDirty();

	@Override
	ConfigValueUpdateType getUpdateType(List<ConfigValueChange<?>> changes);

	@Override
	ConfigValueUpdateType applyChanges(List<ConfigValueChange<?>> changes);

	@Override
	List<ConfigDisplayCategory> getDisplayCategories();
}
