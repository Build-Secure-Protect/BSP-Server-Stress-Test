package com.mrgregles.bsp_server_stress_test.setups;

import com.mrgregles.bsp_server_stress_test.compat.Create;

import java.util.ArrayList;
import java.util.List;

/** Every working setup this server can run: those of each mod family that is loaded (owner decision D35). */
public final class Setups {
    private Setups() {
    }

    public static List<SetupOption> available() {
        List<SetupOption> out = new ArrayList<>();
        if (Create.isLoaded()) {
            out.addAll(Create.setups());
        }
        if (com.mrgregles.bsp_server_stress_test.compat.Mekanism.isLoaded()) {
            out.addAll(com.mrgregles.bsp_server_stress_test.compat.Mekanism.setups());
        }
        if (com.mrgregles.bsp_server_stress_test.compat.RefinedStorage.isLoaded()) {
            out.addAll(com.mrgregles.bsp_server_stress_test.compat.RefinedStorage.setups());
        }
        if (com.mrgregles.bsp_server_stress_test.compat.Ae2.isLoaded()) {
            out.addAll(com.mrgregles.bsp_server_stress_test.compat.Ae2.setups());
        }
        if (com.mrgregles.bsp_server_stress_test.compat.Thermal.isLoaded()) {
            out.addAll(com.mrgregles.bsp_server_stress_test.compat.Thermal.setups());
        }
        if (com.mrgregles.bsp_server_stress_test.compat.EnderIo.isLoaded()) {
            out.addAll(com.mrgregles.bsp_server_stress_test.compat.EnderIo.setups());
        }
        if (com.mrgregles.bsp_server_stress_test.compat.IndustrialForegoing.isLoaded()) {
            out.addAll(com.mrgregles.bsp_server_stress_test.compat.IndustrialForegoing.setups());
        }
        if (com.mrgregles.bsp_server_stress_test.compat.Botania.isLoaded()) {
            out.addAll(com.mrgregles.bsp_server_stress_test.compat.Botania.setups());
        }
        return out;
    }
}
