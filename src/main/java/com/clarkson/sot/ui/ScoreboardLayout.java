package com.clarkson.sot.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Builds the text of the live scoreboard sidebar every player sees during a round.
 *
 * <p>Pure presentation logic — no Bukkit, no game state of its own. It turns a list of
 * {@link TeamSnapshot}s plus the viewer's own unbanked coins into {@link Component}s;
 * {@link GameScoreboardManager} does the Bukkit plumbing and the once-a-second refresh.
 *
 * <p><b>No clock.</b> The sidebar deliberately never shows the sand timer. Reading time off the
 * lobby's sand column and calling it out is part of the game — putting a countdown on everyone's
 * screen would take that job away from the team (see {@code GAME_RULES.md}, "Visual Sand Timer").
 *
 * <p>The sidebar is capped at {@link #MAX_SIDEBAR_LINES}, which is all Minecraft renders. With a
 * viewer on a team that leaves {@link #MAX_STANDINGS_LINES} rows for the standings, more than
 * enough for the ten standard teams (see {@code GAME_RULES.md}, "Teams").
 */
public final class ScoreboardLayout {

    /** Lines Minecraft renders in the sidebar. Anything beyond this is dropped. */
    public static final int MAX_SIDEBAR_LINES = 15;

    /** Sidebar lines spent on the viewer's own team before the standings start. */
    private static final int VIEWER_HEADER_LINES = 3; // unbanked, banked, spacer

    /** Standings rows that fit below the viewer's own lines. */
    public static final int MAX_STANDINGS_LINES = MAX_SIDEBAR_LINES - VIEWER_HEADER_LINES - 1;

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
