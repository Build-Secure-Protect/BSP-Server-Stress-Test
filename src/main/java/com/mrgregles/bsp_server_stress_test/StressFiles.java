package com.mrgregles.bsp_server_stress_test;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The one folder this mod writes in: {@code config/bsp_server_stress_test/} beside the server's other configs (owner
 * decision D4). Settings, profiles, reports and the record of what a run placed all go here; nothing goes in the world
 * folder, a {@code serverconfig/} file or saved data. Every file the mod writes is reached through {@link #file}.
 */
public final class StressFiles {
    public static final String FOLDER = BSPServerStressTest.MODID;

    private StressFiles() {
    }

    public static Path root() {
        return FMLPaths.CONFIGDIR.get().resolve(FOLDER).toAbsolutePath().normalize();
    }

    /** Makes the folder if it is missing and returns it. */
    public static Path ensureRoot() {
        Path root = root();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not make " + root, e);
        }
        return root;
    }

    /** A file inside the folder by its relative name ({@code reports/run.txt}); a name that leads outside it is refused. */
    public static Path file(String relative) {
        Path root = root();
        Path path = root.resolve(relative).toAbsolutePath().normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IllegalArgumentException("Not a file inside " + FOLDER + ": " + relative);
        }
        return path;
    }

    /** The folder as an admin would type it from the server's own folder, for messages. */
    public static String shown() {
        Path game = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        Path root = root();
        return (root.startsWith(game) ? game.relativize(root) : root).toString().replace('\\', '/') + "/";
    }
}
