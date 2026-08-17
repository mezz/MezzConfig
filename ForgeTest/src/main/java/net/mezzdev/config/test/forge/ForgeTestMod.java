package net.mezzdev.config.test.forge;

import net.mezzdev.config.api.Configs;
import net.mezzdev.config.api.IConfigRegistration;
import net.mezzdev.config.api.schema.ConfigScope;
import net.mezzdev.config.api.schema.IConfigCategoryBuilder;
import net.mezzdev.config.api.schema.IConfigSchemaBuilder;
import net.mezzdev.config.api.value.ConfigValueRestartRequirement;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod(ForgeTestMod.MOD_ID)
public final class ForgeTestMod {
	public static final String MOD_ID = "mezz_config_test_forge";

	public ForgeTestMod() {
		IConfigRegistration registration = Configs.forMod(MOD_ID);
		registerClientConfig(registration);
		registerServerConfig(registration);
	}

	private static void registerClientConfig(IConfigRegistration registration) {
		IConfigSchemaBuilder schema = registration.createClientSchemaBuilder("forge-test.ini", "mezz_config_test.forge");
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
		IConfigSchemaBuilder schema = registration.createServerSchemaBuilder("server-test.ini", "mezz_config_test.forge.server")
			.setScope(ConfigScope.WORLD);
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
