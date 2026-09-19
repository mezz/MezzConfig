package net.mezzdev.config.neoforge.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@ForEachTest(groups = "server_config")
public final class MezzConfigGameTestRegistration {
	private MezzConfigGameTestRegistration() {}

	@GameTest
	@EmptyTemplate
	@TestHolder(description = "Starting a dedicated-server world activates its authoritative config file.")
	public static void dedicatedServerActivatesAuthoritativeConfig(GameTestHelper helper) {
		MezzConfigGameTests.dedicatedServerActivatesAuthoritativeConfig(helper);
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(description = "A dedicated server starts without publishing client-owned schemas.")
	public static void dedicatedServerStartsWithoutClientSchemas(GameTestHelper helper) {
		MezzConfigGameTests.dedicatedServerStartsWithoutClientSchemas(helper);
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(description = "A dedicated server keeps common client schema declarations inert.")
	public static void dedicatedServerKeepsClientSchemaDeclarationsInert(GameTestHelper helper) {
		MezzConfigGameTests.dedicatedServerKeepsClientSchemaDeclarationsInert(helper);
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(description = "A dedicated server keeps client sorting configs independent and in memory.")
	public static void dedicatedServerKeepsClientSortingConfigsInMemory(GameTestHelper helper) {
		MezzConfigGameTests.dedicatedServerKeepsClientSortingConfigsInMemory(helper);
	}

	@GameTest(timeoutTicks = 10000)
	@EmptyTemplate
	@TestHolder(description = "Editing an authoritative config file schedules its reload without server-tick polling.")
	public static void authoritativeFileChangeSchedulesReload(GameTestHelper helper) {
		MezzConfigGameTests.authoritativeFileChangeSchedulesReload(helper);
	}
}
