package com.clarkson.sot.ui;

import net.kyori.adventure.bossbar.BossBar;
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

    private static TeamSnapshot team(UUID id, String name, int banked, int seconds) {
        return new TeamSnapshot(id, name, NamedTextColor.WHITE, banked, seconds);
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

    // --- Time formatting ---

    @Test
    void formatsTimeAsMinutesAndPaddedSeconds() {
        assertEquals("0:00", ScoreboardLayout.formatTime(0));
        assertEquals("0:05", ScoreboardLayout.formatTime(5));
        assertEquals("1:05", ScoreboardLayout.formatTime(65));
        assertEquals("2:30", ScoreboardLayout.formatTime(150));
    }

    @Test
    void formatsNegativeTimeAsZero() {
        assertEquals("0:00", ScoreboardLayout.formatTime(-30));
    }

    // --- Ranking ---

    @Test
    void ranksTeamsByBankedScoreDescending() {
        List<TeamSnapshot> ranked = ScoreboardLayout.rank(List.of(
                team(RED, "Red Rabbits", 120, 100),
                team(BLUE, "Blue Bats", 340, 100),
                team(GREEN, "Green Geckos", 200, 100)));

        assertEquals(List.of("Blue Bats", "Green Geckos", "Red Rabbits"),
                ranked.stream().map(TeamSnapshot::name).toList());
    }

    @Test
    void breaksScoreTiesByNameSoTheOrderDoesNotJitter() {
        List<TeamSnapshot> ranked = ScoreboardLayout.rank(List.of(
                team(RED, "Red Rabbits", 50, 100),
                team(BLUE, "Blue Bats", 50, 10)));

        assertEquals(List.of("Blue Bats", "Red Rabbits"),
                ranked.stream().map(TeamSnapshot::name).toList());
    }

    // --- Sidebar ---

    @Test
    void sidebarLeadsWithTheViewersOwnTimeAndCoins() {
        TeamSnapshot viewer = team(RED, "Red Rabbits", 340, 95);
        List<String> lines = plain(ScoreboardLayout.sidebarLines(List.of(viewer), viewer, 120));

        assertEquals("Time: 1:35", lines.get(0));
        assertEquals("Unbanked: 120", lines.get(1));
        assertEquals("Banked: 340", lines.get(2));
        assertEquals("", lines.get(3), "a blank spacer separates the viewer's lines from the standings");
        assertEquals("Standings", lines.get(4));
        assertEquals("1. Red Rabbits 340", lines.get(5));
    }

    @Test
    void sidebarReadsOutOnceTheSandHasRunOut() {
        TeamSnapshot viewer = team(RED, "Red Rabbits", 340, 0);
        List<String> lines = plain(ScoreboardLayout.sidebarLines(List.of(viewer), viewer, 0));
        assertEquals("Time: OUT", lines.get(0));
    }

    @Test
    void sidebarClampsNegativeUnbankedCoinsToZero() {
        TeamSnapshot viewer = team(RED, "Red Rabbits", 10, 95);
        List<String> lines = plain(ScoreboardLayout.sidebarLines(List.of(viewer), viewer, -5));
        assertEquals("Unbanked: 0", lines.get(1));
    }

    @Test
    void sidebarNumbersTheStandingsInRankOrder() {
        TeamSnapshot viewer = team(RED, "Red Rabbits", 120, 95);
        List<TeamSnapshot> ranked = ScoreboardLayout.rank(List.of(
                viewer, team(BLUE, "Blue Bats", 340, 60), team(GREEN, "Green Geckos", 0, 20)));

        List<String> lines = plain(ScoreboardLayout.sidebarLines(ranked, viewer, 0));

        assertEquals("1. Blue Bats 340", lines.get(5));
        assertEquals("2. Red Rabbits 120", lines.get(6));
        assertEquals("3. Green Geckos 0", lines.get(7));
    }

    @Test
    void sidebarBoldsTheViewersOwnTeamInTheStandings() {
        TeamSnapshot viewer = team(RED, "Red Rabbits", 120, 95);
        List<TeamSnapshot> ranked = ScoreboardLayout.rank(List.of(viewer, team(BLUE, "Blue Bats", 340, 60)));

        List<Component> lines = ScoreboardLayout.sidebarLines(ranked, viewer, 0);
        Component ownTeamName = lines.get(6).children().get(0);
        Component otherTeamName = lines.get(5).children().get(0);

        assertEquals(TextDecoration.State.TRUE, ownTeamName.decoration(TextDecoration.BOLD));
        assertEquals(TextDecoration.State.NOT_SET, otherTeamName.decoration(TextDecoration.BOLD));
    }

    @Test
    void sidebarNeverExceedsWhatMinecraftRenders() {
        List<TeamSnapshot> teams = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            teams.add(team(UUID.randomUUID(), "Team " + i, 100 - i, 100));
        }
        List<TeamSnapshot> ranked = ScoreboardLayout.rank(teams);

        List<Component> lines = ScoreboardLayout.sidebarLines(ranked, ranked.get(0), 0);

        assertEquals(ScoreboardLayout.MAX_SIDEBAR_LINES, lines.size());
        // The four viewer lines plus the "Standings" heading leave room for the standard ten teams.
        assertEquals(10, ScoreboardLayout.MAX_STANDINGS_LINES);
        assertEquals("10. Team 9 91", plain(lines.get(ScoreboardLayout.MAX_SIDEBAR_LINES - 1)));
    }

    @Test
    void sidebarForSomeoneWithoutATeamShowsTheStandingsAlone() {
        List<TeamSnapshot> ranked = ScoreboardLayout.rank(List.of(
                team(RED, "Red Rabbits", 120, 95), team(BLUE, "Blue Bats", 340, 60)));

        List<String> lines = plain(ScoreboardLayout.sidebarLines(ranked, null, 0));

        assertEquals("Standings", lines.get(0));
        assertEquals("1. Blue Bats 340", lines.get(1));
        assertEquals("2. Red Rabbits 120", lines.get(2));
        assertEquals(3, lines.size());
    }

    // --- Boss bar ---

    @Test
    void bossBarNamesTheTeamAndItsRemainingTime() {
        assertEquals("Red Rabbits · 1:35", plain(ScoreboardLayout.bossBarName(team(RED, "Red Rabbits", 0, 95))));
        assertEquals("Red Rabbits · OUT", plain(ScoreboardLayout.bossBarName(team(RED, "Red Rabbits", 0, 0))));
    }

    @Test
    void bossBarProgressIsTheFractionOfTheTimerLeft() {
        assertEquals(1.0f, ScoreboardLayout.bossBarProgress(150, 150));
        assertEquals(0.5f, ScoreboardLayout.bossBarProgress(75, 150));
        assertEquals(0.0f, ScoreboardLayout.bossBarProgress(0, 150));
    }

    @Test
    void bossBarProgressStaysInRangeForOddInputs() {
        assertEquals(1.0f, ScoreboardLayout.bossBarProgress(500, 150), "an over-full timer clamps to full");
        assertEquals(0.0f, ScoreboardLayout.bossBarProgress(-10, 150), "a negative timer clamps to empty");
        assertEquals(0.0f, ScoreboardLayout.bossBarProgress(50, 0), "a zero maximum cannot divide");
    }

    @Test
    void bossBarColourFollowsUrgency() {
        assertEquals(BossBar.Color.GREEN, ScoreboardLayout.bossBarColor(150));
        assertEquals(BossBar.Color.YELLOW, ScoreboardLayout.bossBarColor(ScoreboardLayout.WARNING_SECONDS));
        assertEquals(BossBar.Color.RED, ScoreboardLayout.bossBarColor(ScoreboardLayout.URGENT_SECONDS));
        assertEquals(BossBar.Color.RED, ScoreboardLayout.bossBarColor(0));
    }
}
