package net.mezzdev.config.file;

import net.mezzdev.config.ConfigValueChange;
import net.mezzdev.config.ConfigValueUpdateType;

import java.util.List;

public interface IConfigSchema extends net.mezzdev.config.IConfigSchema {
	void register(FileWatcher fileWatcher, IConfigFileRegistrar configFileRegistrar);

	void loadIfNeeded();

	@Override
	ConfigValueUpdateType getUpdateType(List<ConfigValueChange<?>> changes);

	ConfigValueUpdateType applyChanges(List<ConfigValueChange<?>> changes);

	@Override
	List<ConfigDisplayCategory> getDisplayCategories();

	void markDirty();

	void clearListeners();
}
