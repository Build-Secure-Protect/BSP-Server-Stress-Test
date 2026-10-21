package com.mrgregles.bsp_server_stress_test;

import com.mojang.logging.LogUtils;
import com.mrgregles.bsp_server_stress_test.analyse.Watch;
import com.mrgregles.bsp_server_stress_test.command.StressCommands;
import com.mrgregles.bsp_server_stress_test.measure.Meter;
import com.mrgregles.bsp_server_stress_test.run.RunEngine;
import com.mrgregles.bsp_server_stress_test.text.Text;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Entry point for BSP-Server Stress Test: a test tool that fakes load on one server and reports what it cost. It runs on
 * the server only and keeps every file it writes in {@code config/bsp_server_stress_test/} (owner decision D4), so
 * deleting the jar and that folder removes it completely.
 */
@Mod(BSPServerStressTest.MODID)
public class BSPServerStressTest {
    /** Mod id. Must match {@code mod_id} in gradle.properties and {@code modId} in mods.toml. */
    public static final String MODID = "bsp_server_stress_test";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BSPServerStressTest(FMLJavaModLoadingContext context) {
        StressFiles.ensureRoot();
        // A COMMON config, not a SERVER one: Forge keeps SERVER configs inside the world folder.
        context.registerConfig(ModConfig.Type.COMMON, StressConfig.SPEC, StressConfig.FILE_NAME);
        Text.load();
        Meter.register();
        RunEngine.register();
        Watch.register();
        MinecraftForge.EVENT_BUS.addListener(StressCommands::onRegister);
        LOGGER.info("BSP-Server Stress Test loaded; its files are in {}", StressFiles.root());
    }
}
