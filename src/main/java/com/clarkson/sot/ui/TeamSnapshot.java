package com.clarkson.sot.ui;

import net.kyori.adventure.text.format.TextColor;

import java.util.Objects;
import java.util.UUID;

/**
 * An immutable read of one team's live state, as the live scoreboard needs it.
 *
 * <p>Deliberately free of Bukkit and of {@code SoTTeam}: the display layer reads a flat snapshot
 * taken once per refresh, so {@link ScoreboardLayout} stays pure and testable and every viewer's
 * sidebar in a single refresh is built from the same numbers.
 *
 * @param teamId           The team definition's ID, used to spot the viewer's own team.
 * @param name             The team's display name.
 * @param color            The team's colour.
 * @param bankedScore      Coins the team has banked (the only score that counts at the end).
 * @param remainingSeconds Seconds left on the team's sand timer; never negative.
 */
public record TeamSnapshot(UUID teamId, String name, TextColor color, int bankedScore, int remainingSeconds) {

    public TeamSnapshot {
        Objects.requireNonNull(teamId, "Team ID cannot be null");
        Objects.requireNonNull(name, "Team name cannot be null");
        Objects.requireNonNull(color, "Team colour cannot be null");
        remainingSeconds = Math.max(0, remainingSeconds);
    }
}
