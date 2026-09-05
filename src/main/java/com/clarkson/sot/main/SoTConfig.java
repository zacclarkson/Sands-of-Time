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
    /** Config path of the dungeon generation seed; blank means "roll a fresh one each round". */
    public static final String SEED_PATH = "dungeon.seed";

    /** The word an operator can write in place of a seed to mean "roll a fresh one each round". */
    public static final String RANDOM_SEED_KEYWORD = "random";

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

    /**
     * Reads the dungeon generation seed from {@code root} at {@code path}.
     *
     * <p>An absent entry, a blank one, or the literal {@code random} all mean "roll a fresh seed
     * each round" and return {@code null} quietly — a seedless server is the normal case, unlike an
     * unconfigured location. Anything present but unusable logs a warning naming the problem.
     *
     * @param root the configuration to read from (usually {@code plugin.getConfig()}).
     * @param path the entry path, e.g. {@link #SEED_PATH}.
     * @param log  where to report malformed entries.
     * @return the fixed seed, or {@code null} to generate a random one per round.
     */
    @Nullable
    public static Long readSeed(@NotNull ConfigurationSection root, @NotNull String path,
                                @NotNull Logger log) {
        Objects.requireNonNull(root, "root configuration cannot be null");
        Objects.requireNonNull(path, "path cannot be null");
        Objects.requireNonNull(log, "log cannot be null");

        Object raw = root.get(path);
        if (raw == null) {
            return null; // Not configured; not an error.
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty() || trimmed.equalsIgnoreCase(RANDOM_SEED_KEYWORD)) {
                return null; // The shipped "unset" sentinel; not an error.
            }
            return parseSeed(trimmed);
        }
        log.warning("config.yml: '" + path + "' is not a number or text (got '" + raw
                + "'); generating a random seed instead.");
        return null;
    }

    /**
     * Turns operator input into a seed, the way Minecraft's world-seed field does: a value that
     * parses as a {@code long} is used as-is, and anything else is hashed, so {@code /sot seed
     * mcc-finals} is a legitimate — and repeatable — way to name a dungeon.
     */
    public static long parseSeed(@NotNull String raw) {
        Objects.requireNonNull(raw, "raw seed cannot be null");
        String trimmed = raw.trim();
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            return trimmed.hashCode();
        }
    }

    /**
     * Writes the dungeon seed into {@code root} at {@code path}, or clears it back to "random each
     * round" when {@code seed} is {@code null}. The caller is responsible for persisting the
     * configuration afterwards (e.g. {@code plugin.saveConfig()}).
     */
    public static void writeSeed(@NotNull ConfigurationSection root, @NotNull String path,
                                 @Nullable Long seed) {
        Objects.requireNonNull(root, "root configuration cannot be null");
        Objects.requireNonNull(path, "path cannot be null");
        // The blank string rather than null: it keeps the key (and its explanatory comment's
        // subject) in config.yml instead of deleting the entry entirely.
        root.set(path, seed != null ? seed : "");
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
