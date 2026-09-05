package com.clarkson.sot.ui;

import com.clarkson.sot.dungeon.DeathCage;
import com.clarkson.sot.dungeon.GateSacrificePoint;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Draws the price tag above a sacrifice chest: a floating block of sand with a downward arrow and
 * the amount of sand still owed.
 *
 * <p>Two kinds of chest use it. Above a <em>cage</em> chest in the hub the indicator <em>is</em> the
 * activity signal: cage chests are permanent hub furniture, so one with nothing above it has nobody
 * to revive, and sand only appears while its cage holds a player awaiting revive. Above a <em>gate</em>
 * sacrifice chest out in a branch it is the asking price, shown from the moment the dungeon is built
 * and taken down when the gates open -- by payment or by the segment's lever.
 *
 * <p>Deliberately event-driven with no scheduled task, unlike the sand-timer column: the numbers here
 * only change when somebody dies, pays, is revived, or opens a gate, so there is nothing to poll for.
 * Like {@link GameScoreboardManager} it is owned by the GameManager and holds no game state of its own
 * — it is a view over the cages and sacrifice points, keyed on the chest's block so a cage and a gate
 * point never fight over one chest — and there is nothing for {@code SoT.onEnable()} to register.
 */
public class SacrificeIndicatorManager {

    /** Height above the chest for the floating sand block. */
    private static final double SAND_HEIGHT = 1.6;
    /** Height above the chest for the arrow + amount text. */
    private static final double TEXT_HEIGHT = 2.4;
    private static final float SAND_SCALE = 0.45f;
    private static final float TEXT_SCALE = 0.9f;

    private final Logger logger;

    /** Live indicators keyed by the chest block they float above. */
    private final Map<ChestKey, Indicator> indicators = new HashMap<>();

    public SacrificeIndicatorManager(@NotNull Logger logger) {
        this.logger = logger;
    }

    /** The pair of display entities making up one indicator. */
    private record Indicator(BlockDisplay sand, TextDisplay text) {
        void remove() {
            // No isValid() gate: removing an already-dead entity is a no-op, whereas an entity the
            // server has not finished registering yet would be skipped and left floating.
            if (sand != null) sand.remove();
            if (text != null) text.remove();
        }
    }

    /** A chest's block identity: world plus block coordinates, independent of Location equality. */
    private record ChestKey(UUID worldId, int x, int y, int z) {
        static ChestKey of(@NotNull Location chest) {
            World world = chest.getWorld();
            return new ChestKey(world != null ? world.getUID() : null,
                    chest.getBlockX(), chest.getBlockY(), chest.getBlockZ());
        }
    }

    /**
     * Shows (or refreshes) the indicator above a cage's sacrifice chest.
     *
     * <p>Hides it instead when nothing is owed, which is what makes a fully-paid or freshly-revived
     * cage go quiet without the caller having to special-case it.
     */
    public void update(@NotNull DeathCage cage) {
        update(cage.getSacrificePointLocation(), cage.getRemainingSand());
    }

    /** Removes the indicator above a cage's chest, if it has one. Safe to call when it has none. */
    public void hide(@NotNull DeathCage cage) {
        hide(cage.getSacrificePointLocation());
    }

    /**
     * Shows (or refreshes) the price tag above a gate sacrifice chest, and takes it down once the
     * gates it fronts are open.
     */
    public void update(@NotNull GateSacrificePoint point) {
        update(point.getLocation(), point.getRemainingSand());
    }

    /** Removes the price tag above a gate sacrifice chest, if it has one. */
    public void hide(@NotNull GateSacrificePoint point) {
        hide(point.getLocation());
    }

    /**
     * Shows (or refreshes) the indicator above the chest at {@code chest}, reading {@code remaining}
     * sand still owed; hides it instead when nothing is owed.
     */
    public void update(@NotNull Location chest, int remaining) {
        ChestKey key = ChestKey.of(chest);
        if (remaining <= 0) {
            hide(chest);
            return;
        }

        Component label = Component.text("▼ ", NamedTextColor.YELLOW)
                .append(Component.text(remaining + " sand", NamedTextColor.WHITE));

        Indicator existing = indicators.get(key);
        if (existing != null && existing.text() != null && existing.text().isValid()) {
            existing.text().text(label);
            return;
        }

        hide(chest); // drop a half-dead indicator before building a fresh one
        World world = chest.getWorld();
        if (world == null) return;

        double cx = chest.getBlockX() + 0.5;
        double cy = chest.getBlockY();
        double cz = chest.getBlockZ() + 0.5;

        // Spawn first and track immediately, then dress the entities up. A setter that throws inside
        // a spawn consumer leaves the entity unregistered on some servers and registered on others, and
        // either way an exception between the two spawns would strand a sand block nothing can ever
        // remove -- so every cosmetic call below is best-effort and never decides whether the
        // indicator exists.
        BlockDisplay sand;
        TextDisplay text;
        try {
            sand = world.spawn(new Location(world, cx, cy + SAND_HEIGHT, cz), BlockDisplay.class);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not spawn sacrifice indicator at " + chest, e);
            return;
        }
        try {
            text = world.spawn(new Location(world, cx, cy + TEXT_HEIGHT, cz), TextDisplay.class);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not spawn sacrifice indicator label at " + chest, e);
            sand.remove();
            return;
        }
        indicators.put(key, new Indicator(sand, text));

        float offset = -SAND_SCALE / 2.0f; // centre the shrunken cube on the chest
        bestEffort("sand block", () -> sand.setBlock(Material.SAND.createBlockData()));
        bestEffort("sand gravity", () -> sand.setGravity(false));
        bestEffort("sand invulnerable", () -> sand.setInvulnerable(true));
        bestEffort("sand persistence", () -> sand.setPersistent(false));
        bestEffort("sand transform", () -> sand.setTransformation(new Transformation(
                new Vector3f(offset, 0f, offset),
                new AxisAngle4f(0f, 0f, 0f, 1f),
                new Vector3f(SAND_SCALE, SAND_SCALE, SAND_SCALE),
                new AxisAngle4f(0f, 0f, 0f, 1f))));

        bestEffort("label text", () -> text.text(label));
        bestEffort("label billboard", () -> text.setBillboard(Display.Billboard.CENTER));
        bestEffort("label see-through", () -> text.setSeeThrough(true));
        bestEffort("label background", () -> {
            text.setDefaultBackground(false);
            text.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        });
        bestEffort("label gravity", () -> text.setGravity(false));
        bestEffort("label invulnerable", () -> text.setInvulnerable(true));
        bestEffort("label persistence", () -> text.setPersistent(false));
        bestEffort("label transform", () -> text.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(0f, 0f, 0f, 1f),
                new Vector3f(TEXT_SCALE, TEXT_SCALE, TEXT_SCALE),
                new AxisAngle4f(0f, 0f, 0f, 1f))));
    }

    /** Runs one cosmetic setter; an implementation that lacks it degrades the look, not the indicator. */
    private void bestEffort(String what, Runnable setter) {
        try {
            setter.run();
        } catch (Exception e) {
            logger.log(Level.FINE, "Sacrifice indicator: could not apply " + what, e);
        }
    }

    /** Removes the indicator above the chest at {@code chest}, if it has one. Safe to call when it has none. */
    public void hide(@NotNull Location chest) {
        Indicator indicator = indicators.remove(ChestKey.of(chest));
        if (indicator != null) {
            try {
                indicator.remove();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Could not remove sacrifice indicator", e);
            }
        }
    }

    /**
     * Removes every indicator. Called at round teardown — the dungeon cleanup also wipes entities in
     * its bounds, but clearing here keeps this manager's own map from holding dead references.
     */
    public void clearAll() {
        for (Indicator indicator : indicators.values()) {
            try {
                indicator.remove();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Could not remove sacrifice indicator during teardown", e);
            }
        }
        indicators.clear();
    }
}
