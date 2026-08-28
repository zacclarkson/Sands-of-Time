package com.clarkson.sot.ui;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Builds the text of the live scoreboard: the sidebar lines every player sees during a round and
 * the boss bar above their hotbar.
 *
 * <p>Pure presentation logic — no Bukkit, no game state of its own. It turns a list of
 * {@link TeamSnapshot}s plus the viewer's own unbanked coins into {@link Component}s;
 * {@link GameScoreboardManager} does the Bukkit plumbing and the once-a-second refresh.
 *
 * <p>The sidebar is capped at {@link #MAX_SIDEBAR_LINES}, which is all Minecraft renders. With a
 * viewer on a team that leaves {@link #MAX_STANDINGS_LINES} rows for the standings, one per
 * standard team (see {@code GAME_RULES.md}, "Teams").
 */
public final class ScoreboardLayout {

    /** Lines Minecraft renders in the sidebar. Anything beyond this is dropped. */
    public static final int MAX_SIDEBAR_LINES = 15;

    /** Sidebar lines spent on the viewer's own team before the standings start. */
    private static final int VIEWER_HEADER_LINES = 4; // time, unbanked, banked, spacer

    /** Standings rows that fit below the viewer's own lines. Matches the 10 standard teams. */
    public static final int MAX_STANDINGS_LINES = MAX_SIDEBAR_LINES - VIEWER_HEADER_LINES - 1;

    /** At or below this many seconds the timer reads red. */
    public static final int URGENT_SECONDS = 30;

    /** At or below this many seconds (but above {@link #URGENT_SECONDS}) the timer reads yellow. */
    public static final int WARNING_SECONDS = 60;

    private ScoreboardLayout() {}

    /** The sidebar heading. */
    public static Component title() {
        return Component.text("Sands of Time", NamedTextColor.GOLD, TextDecoration.BOLD);
    }

    /**
     * Orders teams the way the standings show them: most banked coins first, ties broken by name
     * so the order does not jitter between refreshes.
     */
    public static List<TeamSnapshot> rank(Collection<TeamSnapshot> teams) {
        List<TeamSnapshot> ranked = new ArrayList<>(teams);
        ranked.sort(Comparator.comparingInt(TeamSnapshot::bankedScore).reversed()
                .thenComparing(TeamSnapshot::name, String.CASE_INSENSITIVE_ORDER));
        return ranked;
    }

    /**
     * Builds one viewer's sidebar, top line first.
     *
     * @param rankedTeams         All teams in standings order, as returned by {@link #rank}.
     * @param viewerTeam          The viewer's own team, or null for someone not playing (an admin
     *                            or a spectator), who gets the standings alone.
     * @param viewerUnbankedCoins The viewer's own unbanked coins. Negatives read as zero.
     * @return At most {@link #MAX_SIDEBAR_LINES} lines.
     */
    public static List<Component> sidebarLines(Collection<TeamSnapshot> rankedTeams,
                                               TeamSnapshot viewerTeam,
                                               int viewerUnbankedCoins) {
        Objects.requireNonNull(rankedTeams, "Ranked teams cannot be null");
        List<Component> lines = new ArrayList<>(MAX_SIDEBAR_LINES);

        if (viewerTeam != null) {
            lines.add(labelled("Time", timeValue(viewerTeam.remainingSeconds())));
            lines.add(labelled("Unbanked", coins(Math.max(0, viewerUnbankedCoins), NamedTextColor.GOLD)));
            lines.add(labelled("Banked", coins(viewerTeam.bankedScore(), NamedTextColor.YELLOW)));
            lines.add(Component.empty());
        }
        lines.add(Component.text("Standings", NamedTextColor.WHITE, TextDecoration.BOLD));

        int room = MAX_SIDEBAR_LINES - lines.size();
        int rank = 1;
        for (TeamSnapshot team : rankedTeams) {
            if (room <= 0) break;
            lines.add(standingsLine(rank, team, viewerTeam));
            rank++;
            room--;
        }
        return List.copyOf(lines);
    }

    /** The boss bar text for a team: its name and how much sand it has left. */
    public static Component bossBarName(TeamSnapshot team) {
        Objects.requireNonNull(team, "Team snapshot cannot be null");
        return Component.text(team.name(), team.color(), TextDecoration.BOLD)
                .append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                .append(timeValue(team.remainingSeconds()));
    }

    /**
     * How full the boss bar is drawn: the fraction of the maximum timer still left, clamped to
     * 0..1 so an over-full or negative timer cannot throw.
     *
     * @param remainingSeconds Seconds left on the team's timer.
     * @param maxSeconds       The timer's maximum (see {@code TeamTimer.DEFAULT_MAX_TIMER_SECONDS}).
     */
    public static float bossBarProgress(int remainingSeconds, int maxSeconds) {
        if (maxSeconds <= 0) return 0f;
        float ratio = (float) remainingSeconds / maxSeconds;
        return Math.max(0f, Math.min(1f, ratio));
    }

    /** The boss bar colour, matching the urgency of the sidebar's time line. */
    public static BossBar.Color bossBarColor(int remainingSeconds) {
        if (remainingSeconds <= URGENT_SECONDS) return BossBar.Color.RED;
        if (remainingSeconds <= WARNING_SECONDS) return BossBar.Color.YELLOW;
        return BossBar.Color.GREEN;
    }

    /**
     * Formats a countdown as {@code M:SS}. Negative values read as {@code 0:00}.
     */
    public static String formatTime(int totalSeconds) {
        int safeSeconds = Math.max(0, totalSeconds);
        return String.format(Locale.ROOT, "%d:%02d", safeSeconds / 60, safeSeconds % 60);
    }

    /** The remaining time, or a red {@code OUT} once the sand has run out. */
    private static Component timeValue(int remainingSeconds) {
        if (remainingSeconds <= 0) {
            return Component.text("OUT", NamedTextColor.RED, TextDecoration.BOLD);
        }
        return Component.text(formatTime(remainingSeconds), timeColor(remainingSeconds));
    }

    private static NamedTextColor timeColor(int remainingSeconds) {
        if (remainingSeconds <= URGENT_SECONDS) return NamedTextColor.RED;
        if (remainingSeconds <= WARNING_SECONDS) return NamedTextColor.YELLOW;
        return NamedTextColor.GREEN;
    }

    private static Component standingsLine(int rank, TeamSnapshot team, TeamSnapshot viewerTeam) {
        boolean isViewersTeam = viewerTeam != null && viewerTeam.teamId().equals(team.teamId());
        Component name = isViewersTeam
                ? Component.text(team.name(), team.color(), TextDecoration.BOLD)
                : Component.text(team.name(), team.color());
        return Component.text(rank + ". ", NamedTextColor.GRAY)
                .append(name)
                .append(Component.text(" " + team.bankedScore(), NamedTextColor.YELLOW));
    }

    private static Component labelled(String label, Component value) {
        return Component.text(label + ": ", NamedTextColor.GRAY).append(value);
    }

    private static Component coins(int amount, NamedTextColor color) {
        return Component.text(String.valueOf(amount), color);
    }
}
