package com.clarkson.sot.main;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Reads and writes the game's world locations in {@code config.yml}.
 *
 * <p>Locations are stored as six plain scalars ({@code world}, {@code x}, {@code y}, {@code z},
 * {@code yaw}, {@code pitch}) rather than via Bukkit's {@code ConfigurationSerializable} support
 * for {@link Location}. The serialized form of that is an opaque {@code ==: org.bukkit.Location}
 * map that nobody wants to hand-edit, and it yields a {@link Location} with a {@code null} world
 * when the named world is not loaded instead of reporting the problem.
 *
 * <p>The world lookup is injected rather than calling {@code Bukkit.getWorld} directly, so this
 * class is unit-testable against a plain {@code YamlConfiguration} with no running server.
 */
public final class SoTConfig {

    /** Config path of the lobby anchor: round-end teleport, visual-timer anchor, dungeon world. */
    public static final String LOBBY_PATH = "locations.lobby";
    /** Config path of the universal location players are sent to when their timer runs out. */
    public static final String TRAPPED_PATH = "locations.trapped";

    private SoTConfig() {
        // Static utility.
    }

    /**
     * Reads a location from {@code root} at {@code path}.
     *
     * <p>An absent section, or a section whose {@code world} is blank, means "not configured yet"
     * and returns {@code null} quietly — the caller decides how loudly to complain. A section that
     * is present but unusable (unknown world, missing or non-numeric coordinate) logs a warning
     * naming the exact problem.
     *
     * @param root        the configuration to read from (usually {@code plugin.getConfig()}).
     * @param path        the section path, e.g. {@link #LOBBY_PATH}.
     * @param worldLookup resolves a world name to a loaded world; {@code Bukkit::getWorld} in production.
     * @param log         where to report malformed entries.
     * @return the location, or {@code null} if it is unset or unusable.
     */
    @Nullable
    public static Location readLocation(@NotNull ConfigurationSection root, @NotNull String path,
                                        @NotNull Function<String, World> worldLookup, @NotNull Logger log) {
        Objects.requireNonNull(root, "root configuration cannot be null");
        Objects.requireNonNull(path, "path cannot be null");
        Objects.requireNonNull(worldLookup, "worldLookup cannot be null");
        Objects.requireNonNull(log, "log cannot be null");

        ConfigurationSection section = root.getConfigurationSection(path);
        if (section == null) {
            return null; // Not configured; not an error.
        }

        String worldName = section.getString("world");
        if (worldName == null || worldName.isBlank()) {
            return null; // Blank world is the shipped "unset" sentinel; not an error.
        }

        World world = worldLookup.apply(worldName);
        if (world == null) {
            log.warning("config.yml: '" + path + "' names world '" + worldName
                    + "', which is not loaded; ignoring it.");
            return null;
        }

        Double x = readCoordinate(section, "x", path, log);
        Double y = readCoordinate(section, "y", path, log);
        Double z = readCoordinate(section, "z", path, log);
        if (x == null || y == null || z == null) {
            return null;
        }

        // Yaw and pitch are optional; a location with no facing is still usable.
        float yaw = (float) section.getDouble("yaw", 0.0D);
        float pitch = (float) section.getDouble("pitch", 0.0D);

        return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * Writes a location into {@code root} at {@code path}, creating the section if needed.
     * The caller is responsible for persisting the configuration afterwards
     * (e.g. {@code plugin.saveConfig()}).
     *
     * @throws NullPointerException if the location has no world.
     */
    public static void writeLocation(@NotNull ConfigurationSection root, @NotNull String path,
                                     @NotNull Location location) {
        Objects.requireNonNull(root, "root configuration cannot be null");
        Objects.requireNonNull(path, "path cannot be null");
        Objects.requireNonNull(location, "location cannot be null");
        World world = Objects.requireNonNull(location.getWorld(), "location must have a world");

        ConfigurationSection section = root.getConfigurationSection(path);
        if (section == null) {
            section = root.createSection(path);
        }
        section.set("world", world.getName());
        section.set("x", location.getX());
        section.set("y", location.getY());
        section.set("z", location.getZ());
        section.set("yaw", (double) location.getYaw());
        section.set("pitch", (double) location.getPitch());
    }

    /** Formats a location for an operator-facing message or log line. */
    @NotNull
    public static String describe(@NotNull Location location) {
        World world = location.getWorld();
        return String.format("%s %.1f, %.1f, %.1f",
                world != null ? world.getName() : "<no world>",
                location.getX(), location.getY(), location.getZ());
    }

    @Nullable
    private static Double readCoordinate(@NotNull ConfigurationSection section, @NotNull String key,
                                         @NotNull String path, @NotNull Logger log) {
        Object raw = section.get(key);
        if (raw == null) {
            log.warning("config.yml: '" + path + "' is missing the '" + key + "' coordinate; ignoring it.");
            return null;
        }
        if (!(raw instanceof Number number)) {
            log.warning("config.yml: '" + path + "." + key + "' is not a number (got '" + raw + "'); ignoring it.");
            return null;
        }
        return number.doubleValue();
    }
}
