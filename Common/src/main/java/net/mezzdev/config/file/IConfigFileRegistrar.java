package net.mezzdev.config.file;

import net.mezzdev.config.schema.ConfigSchema;

public interface IConfigFileRegistrar {
	void addConfigFile(ConfigSchema configFile);
}
