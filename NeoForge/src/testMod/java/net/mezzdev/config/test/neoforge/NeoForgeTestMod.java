package net.mezzdev.config.test.neoforge;

import net.mezzdev.config.api.Configs;
import net.mezzdev.config.api.IConfigRegistration;
import net.mezzdev.config.api.schema.builder.IConfigCategoryBuilder;
import net.mezzdev.config.api.schema.builder.IConfigSchemaBuilder;
import net.mezzdev.config.api.value.editor.ConfigValueRestartRequirement;
import net.neoforged.fml.common.Mod;

import java.util.List;

@Mod(NeoForgeTestMod.MOD_ID)
public final class NeoForgeTestMod {
	public static final String MOD_ID = "mezz_config_test_neoforge";

	public NeoForgeTestMod() {
		IConfigRegistration registration = Configs.forMod(MOD_ID);
		registerClientConfig(registration);
		registerServerConfig(registration);
	}

	private static void registerClientConfig(IConfigRegistration registration) {
		IConfigSchemaBuilder schema = registration.createClientSchemaBuilder("neoforge-test.ini", "mezz_config_test.neoforge");
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

	private static void registerServerConfig(IConfigRegistration registration) {
		IConfigSchemaBuilder schema = registration.createServerSchemaBuilder("server-test.ini", "mezz_config_test.neoforge.server");
		IConfigCategoryBuilder general = schema.addCategory("general");
		general.addBoolean("enabled", true)
			.build();
		general.addInteger("maxEntries", 16, 0, 64)
			.setRestartRequirement(ConfigValueRestartRequirement.WORLD_RESTART)
			.build();
		general.addEnum("mode", TestMode.STANDARD)
			.setRestartRequirement(ConfigValueRestartRequirement.GAME_RESTART)
			.build();
		schema.build();
	}

	private enum TestMode {
		STANDARD,
		ADVANCED
	}
}
