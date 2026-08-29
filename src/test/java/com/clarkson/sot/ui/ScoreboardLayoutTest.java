package com.clarkson.sot.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ScoreboardLayout}, the text of the live scoreboard.
 *
 * <p>Pure presentation logic, so no mock server is involved: the class only turns
 * {@link TeamSnapshot}s into Adventure components. Assertions read the rendered text back with
 * {@link PlainTextComponentSerializer} rather than picking components apart, which keeps them
 * about what a player sees.
 */
class ScoreboardLayoutTest {

    private static final UUID RED = UUID.randomUUID();
    private static final UUID BLUE = UUID.randomUUID();
    private static final UUID GREEN = UUID.randomUUID();

    private static TeamSnapshot team(UUID id, String name, int banked) {
        return new TeamSnapshot(id, name, NamedTextColor.WHITE, banked);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static List<String> plain(List<Component> components) {
        List<String> lines = new ArrayList<>(components.size());
        for (Component component : components) {
            lines.add(plain(component));
        }
        return lines;
    }

    // --- Ranking ---

    @Test
    void ranksTeamsByBankedScoreDescending() {
        List<TeamSnapshot> ranked = ScoreboardLayout.rank(List.of(
                team(RED, "Red Rabbits", 120),
                team(BLUE, "Blue Bats", 340),
                team(GREEN, "Green Geckos", 200)));

        assertEquals(List.of("Blue Bats", "Green Geckos", "Red Rabbits"),
                ranked.stream().map(TeamSnapshot::name).toList());
    }

    @Test
    void breaksScoreTiesByNameSoTheOrderDoesNotJitter() {
        List<TeamSnapshot> ranked = ScoreboardLayout.rank(List.of(
                team(RED, "Red Rabbits", 50),
                team(BLUE, "Blue Bats", 50)));

        assertEquals(List.of("Blue Bats", "Red Rabbits"),
                ranked.stream().map(TeamSnapshot::name).toList());
    }

    // --- Sidebar ---

    @Test
    void sidebarLeadsWithTheViewersOwnCoins() {
        TeamSnapshot viewer = team(RED, "Red Rabbits", 340);
        List<String> lines = plain(ScoreboardLayout.sidebarLines(List.of(viewer), viewer, 120));

        assertEquals("Unbanked: 120", lines.get(0));
        assertEquals("Banked: 340", lines.get(1));
        assertEquals("", lines.get(2), "a blank spacer separates the viewer's lines from the standings");
        assertEquals("Standings", lines.get(3));
        assertEquals("1. Red Rabbits 340", lines.get(4));
    }

    @Test
    void sidebarNeverShowsTheSandTimer() {
        TeamSnapshot viewer = team(RED, "Red Rabbits", 340);
        List<String> lines = plain(ScoreboardLayout.sidebarLines(List.of(viewer), viewer, 120));

        // Teams read the clock off the lobby's sand column and call it out themselves; a countdown
        // on every screen would take that job away from them.
        for (String line : lines) {
            assertFalse(line.toLowerCase().contains("time"), "sidebar line leaks the timer: " + line);
            assertFalse(line.matches(".*\\d+:\\d\\d.*"), "sidebar line leaks a countdown: " + line);
        }
    }

    @Test
    void sidebarClampsNegativeUnbankedCoinsToZero() {
        TeamSnapshot viewer = team(RED, "Red Rabbits", 10);
        List<String> lines = plain(ScoreboardLayout.sidebarLines(List.of(viewer), viewer, -5));
        assertEquals("Unbanked: 0", lines.get(0));
    }

    @Test
    void sidebarNumbersTheStandingsInRankOrder() {
        TeamSnapshot viewer = team(RED, "Red Rabbits", 120);
        List<TeamSnapshot> ranked = ScoreboardLayout.rank(List.of(
                viewer, team(BLUE, "Blue Bats", 340), team(GREEN, "Green Geckos", 0)));

        List<String> lines = plain(ScoreboardLayout.sidebarLines(ranked, viewer, 0));

        assertEquals("1. Blue Bats 340", lines.get(4));
        assertEquals("2. Red Rabbits 120", lines.get(5));
        assertEquals("3. Green Geckos 0", lines.get(6));
    }

    @Test
    void sidebarBoldsTheViewersOwnTeamInTheStandings() {
        TeamSnapshot viewer = team(RED, "Red Rabbits", 120);
        List<TeamSnapshot> ranked = ScoreboardLayout.rank(List.of(viewer, team(BLUE, "Blue Bats", 340)));

        List<Component> lines = ScoreboardLayout.sidebarLines(ranked, viewer, 0);
        Component ownTeamName = lines.get(5).children().get(0);
        Component otherTeamName = lines.get(4).children().get(0);

        assertEquals(TextDecoration.State.TRUE, ownTeamName.decoration(TextDecoration.BOLD));
        assertEquals(TextDecoration.State.NOT_SET, otherTeamName.decoration(TextDecoration.BOLD));
    }

    @Test
    void sidebarNeverExceedsWhatMinecraftRenders() {
        List<TeamSnapshot> teams = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            teams.add(team(UUID.randomUUID(), "Team " + i, 100 - i));
        }
        List<TeamSnapshot> ranked = ScoreboardLayout.rank(teams);

        List<Component> lines = ScoreboardLayout.sidebarLines(ranked, ranked.get(0), 0);

        assertEquals(ScoreboardLayout.MAX_SIDEBAR_LINES, lines.size());
        // Three viewer lines plus the "Standings" heading leave room for more than the ten
        // standard teams.
        assertEquals(11, ScoreboardLayout.MAX_STANDINGS_LINES);
        assertEquals("11. Team 10 90", plain(lines.get(ScoreboardLayout.MAX_SIDEBAR_LINES - 1)));
    }

    @Test
    void sidebarForSomeoneWithoutATeamShowsTheStandingsAlone() {
        List<TeamSnapshot> ranked = ScoreboardLayout.rank(List.of(
                team(RED, "Red Rabbits", 120), team(BLUE, "Blue Bats", 340)));

        List<String> lines = plain(ScoreboardLayout.sidebarLines(ranked, null, 0));

        assertEquals("Standings", lines.get(0));
        assertEquals("1. Blue Bats 340", lines.get(1));
        assertEquals("2. Red Rabbits 120", lines.get(2));
        assertEquals(3, lines.size());
    }
}
