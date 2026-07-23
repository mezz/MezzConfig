package net.mezzdev.config.file;

import net.mezzdev.config.value.ConfigValueChange;
import net.mezzdev.config.value.ConfigValueUpdateType;

import java.util.List;

public interface IConfigSchema extends net.mezzdev.config.schema.IConfigEditableSchema {
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
