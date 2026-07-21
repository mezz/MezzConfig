package net.mezzdev.config;

import java.util.List;

@FunctionalInterface
public interface IConfigChangesHandler {
	ConfigValueUpdateType applyChanges(List<ConfigValueChange<?>> changes);
}
