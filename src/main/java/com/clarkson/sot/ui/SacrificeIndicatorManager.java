package com.clarkson.sot.ui;

import com.clarkson.sot.dungeon.DeathCage;

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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Draws the "someone is caged here" marker above a sacrifice chest: a floating block of sand with a
 * downward arrow and the amount still owed to free the teammate assigned to that cage.
 *
 * <p>The indicator <em>is</em> the activity signal. Sacrifice chests are permanent hub furniture, so
 * a chest with nothing above it is one with nobody to revive; sand only appears while its cage holds
 * a player awaiting revive, and disappears the moment they are freed or the round takes them.
 *
 * <p>Deliberately event-driven with no scheduled task, unlike the sand-timer column: the numbers here
 * only change when somebody dies, pays, or is revived, so there is nothing to poll for. Like
 * {@link GameScoreboardManager} it is owned by the GameManager and holds no game state of its own —
 * it is a view over the cages, so there is nothing for {@code SoT.onEnable()} to register.
 */
public class SacrificeIndicatorManager {

    /** Height above the chest for the floating sand block. */
    private static final double SAND_HEIGHT = 1.6;
    /** Height above the chest for the arrow + amount text. */
    private static final double TEXT_HEIGHT = 2.4;
    private static final float SAND_SCALE = 0.45f;
    private static final float TEXT_SCALE = 0.9f;

    private final Logger logger;

    /** Live indicators keyed by the cage they belong to. */
    private final Map<DeathCage, Indicator> indicators = new HashMap<>();

    public SacrificeIndicatorManager(@NotNull Logger logger) {
        this.logger = logger;
    }

    /** The pair of display entities making up one indicator. */
    private record Indicator(BlockDisplay sand, TextDisplay text) {
        void remove() {
            if (sand != null && sand.isValid()) sand.remove();
            if (text != null && text.isValid()) text.remove();
        }
    }

    /**
     * Shows (or refreshes) the indicator above a cage's sacrifice chest.
     *
     * <p>Hides it instead when nothing is owed, which is what makes a fully-paid or freshly-revived
     * cage go quiet without the caller having to special-case it.
     */
    public void update(@NotNull DeathCage cage) {
        int remaining = cage.getRemainingSand();
        if (remaining <= 0) {
            hide(cage);
            return;
        }

        Component label = Component.text("▼ ", NamedTextColor.YELLOW)
                .append(Component.text(remaining + " sand", NamedTextColor.WHITE));

        Indicator existing = indicators.get(cage);
        if (existing != null && existing.text() != null && existing.text().isValid()) {
            existing.text().text(label);
            return;
        }

        hide(cage); // drop a half-dead indicator before building a fresh one
        Location chest = cage.getSacrificePointLocation();
        World world = chest.getWorld();
        if (world == null) return;

        double cx = chest.getBlockX() + 0.5;
        double cy = chest.getBlockY();
        double cz = chest.getBlockZ() + 0.5;

        try {
            BlockDisplay sand = world.spawn(new Location(world, cx, cy + SAND_HEIGHT, cz), BlockDisplay.class, display -> {
                display.setBlock(Material.SAND.createBlockData());
                display.setGravity(false);
                display.setInvulnerable(true);
                display.setPersistent(false);
                float offset = -SAND_SCALE / 2.0f; // centre the shrunken cube on the chest
                display.setTransformation(new Transformation(
                        new Vector3f(offset, 0f, offset),
                        new AxisAngle4f(0f, 0f, 0f, 1f),
                        new Vector3f(SAND_SCALE, SAND_SCALE, SAND_SCALE),
                        new AxisAngle4f(0f, 0f, 0f, 1f)
                ));
            });

            TextDisplay text = world.spawn(new Location(world, cx, cy + TEXT_HEIGHT, cz), TextDisplay.class, display -> {
                display.text(label);
                display.setBillboard(Display.Billboard.CENTER);
                display.setSeeThrough(true);
                display.setDefaultBackground(false);
                display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                display.setGravity(false);
                display.setInvulnerable(true);
                display.setPersistent(false);
                display.setTransformation(new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new AxisAngle4f(0f, 0f, 0f, 1f),
                        new Vector3f(TEXT_SCALE, TEXT_SCALE, TEXT_SCALE),
                        new AxisAngle4f(0f, 0f, 0f, 1f)
                ));
            });

            indicators.put(cage, new Indicator(sand, text));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not spawn sacrifice indicator at " + chest, e);
        }
    }

    /** Removes the indicator above a cage, if it has one. Safe to call when it has none. */
    public void hide(@NotNull DeathCage cage) {
        Indicator indicator = indicators.remove(cage);
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
