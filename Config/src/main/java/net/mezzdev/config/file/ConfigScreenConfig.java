package net.mezzdev.config.file;

import net.mezzdev.config.schema.IConfigEditableSchema;
import net.mezzdev.config.screen.IConfigScreenConfig;
import net.minecraft.network.chat.Component;

record ConfigScreenConfig(
	String modId,
	Component title,
	IConfigEditableSchema schema,
	Runnable restartHandler
) implements IConfigScreenConfig {
	ConfigScreenConfig {
		if (modId == null) {
			throw new NullPointerException("modId must not be null.");
		}
		if (title == null) {
			throw new NullPointerException("title must not be null.");
		}
		if (schema == null) {
			throw new NullPointerException("schema must not be null.");
		}
		if (restartHandler == null) {
			throw new NullPointerException("restartHandler must not be null.");
		}
	}

	@Override
	public String getModId() {
		return modId;
	}

	@Override
	public Component getTitle() {
		return title;
	}

	@Override
	public IConfigEditableSchema getSchema() {
		return schema;
	}

	@Override
	public void onRestartRequired() {
		restartHandler.run();
	}
}
