package net.mezzdev.config.test.neoforge;

import net.mezzdev.config.api.plugin.ConfigPlugin;
import net.mezzdev.config.api.plugin.IConfigPlugin;
import net.mezzdev.config.api.plugin.IConfigRegistration;
import net.mezzdev.config.api.schema.IConfigCategoryBuilder;
import net.mezzdev.config.api.schema.IConfigSchemaBuilder;

import java.util.List;

@ConfigPlugin
public final class NeoForgeTestConfigPlugin implements IConfigPlugin {
	private static final String MOD_ID = "mezz_config_test_neoforge";

	@Override
	public String getModId() {
		return MOD_ID;
	}

	@Override
	public void registerConfigFiles(IConfigRegistration registration) {
		IConfigSchemaBuilder schema = registration.createSchemaBuilder("neoforge-test.ini", "mezz_config_test.neoforge");
		IConfigCategoryBuilder general = schema.addCategory("general");
		general.addBoolean("enabled", true)
			.build();
		general.addInteger("maxEntries", 16, 0, 64)
			.build();
		general.addEnum("mode", TestMode.STANDARD)
			.build();
		general.addEnumList("modes", List.of(TestMode.STANDARD), TestMode.class)
			.build();
		schema.build();
	}

	private enum TestMode {
		STANDARD,
		ADVANCED
	}
}
