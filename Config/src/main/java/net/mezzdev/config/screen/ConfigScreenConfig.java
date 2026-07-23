package net.mezzdev.config.screen;

import net.mezzdev.config.api.schema.IConfigEditableSchema;
import net.mezzdev.config.api.screen.ConfigRestartResult;
import net.mezzdev.config.api.screen.IConfigRestartHandler;
import net.mezzdev.config.api.screen.IConfigScreenConfig;
import net.mezzdev.config.api.util.ErrorUtil;
import net.minecraft.network.chat.Component;

public record ConfigScreenConfig(
	String modId,
	Component title,
	IConfigEditableSchema schema,
	IConfigRestartHandler restartHandler
) implements IConfigScreenConfig {
	public ConfigScreenConfig {
		modId = ErrorUtil.checkNotNull(modId, "modId");
		title = ErrorUtil.checkNotNull(title, "title");
		schema = ErrorUtil.checkNotNull(schema, "schema");
		restartHandler = ErrorUtil.checkNotNull(restartHandler, "restartHandler");
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
	public ConfigRestartResult onRestartRequired() {
		return restartHandler.onRestartRequired();
	}
}
