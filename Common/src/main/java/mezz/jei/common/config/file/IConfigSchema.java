package mezz.jei.common.config.file;

import mezz.jei.api.runtime.config.ConfigValueUpdateType;
import mezz.jei.api.runtime.config.IJeiConfigFile;
import mezz.jei.common.config.ConfigManager;

import java.util.List;

public interface IConfigSchema extends IJeiConfigFile {
	void register(FileWatcher fileWatcher, ConfigManager configManager);

	void loadIfNeeded();

	ConfigValueUpdateType getUpdateType(List<ConfigValueChange<?>> changes);

	ConfigValueUpdateType applyChanges(List<ConfigValueChange<?>> changes);

	void markDirty();

	void clearListeners();
}
