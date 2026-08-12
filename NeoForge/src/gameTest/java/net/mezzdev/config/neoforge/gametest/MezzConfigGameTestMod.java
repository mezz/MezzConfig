package net.mezzdev.config.neoforge.gametest;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.testframework.conf.FrameworkConfiguration;
import net.neoforged.testframework.impl.MutableTestFramework;
import net.neoforged.testframework.summary.GitHubActionsStepSummaryDumper;
import net.neoforged.testframework.summary.JUnitSummaryDumper;

import java.nio.file.Path;

@Mod(MezzConfigGameTestMod.MOD_ID)
public final class MezzConfigGameTestMod {
	public static final String MOD_ID = "mezz_config_gametests";
	private static final String JUNIT_OUTPUT_DIR_PROPERTY = "mezzConfig.gameTest.junitDir";

	public MezzConfigGameTestMod(IEventBus modEventBus, ModContainer modContainer) {
		MutableTestFramework framework = FrameworkConfiguration.builder(
				ResourceLocation.fromNamespaceAndPath(MOD_ID, "tests")
			)
			.dumpers(
				new JUnitSummaryDumper(Path.of(
					System.getProperty(JUNIT_OUTPUT_DIR_PROPERTY, "../../build/test-results/gameTest")
				)),
				new GitHubActionsStepSummaryDumper()
			)
			.build()
			.create();
		framework.init(modEventBus, modContainer);
	}
}
