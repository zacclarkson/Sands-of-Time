package com.clarkson.sot.ui;

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.timer.TeamTimer;
import com.clarkson.sot.utils.SoTTeam;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

import javax.annotation.Nullable;

/**
 * Drives the live scoreboard: a per-player sidebar with the team standings and a per-team boss bar
 * counting the sand down.
 *
 * <p>Owned by {@link GameManager}, which {@link #start()}s it when a round begins and
 * {@link #stop()}s it when the round ends. While running it refreshes every
 * {@link #REFRESH_INTERVAL_TICKS} ticks on the main thread, reading the live managers rather than
 * caching game state of its own; {@link ScoreboardLayout} turns that reading into text.
 *
 * <p><b>How a line is drawn.</b> Sidebar rows are score entries, and an entry's text is fixed once
 * it is registered — rewriting the entries every second would make the whole sidebar flicker.
 * So each of the {@link ScoreboardLayout#MAX_SIDEBAR_LINES} rows owns a permanent, invisible entry
 * ({@link #lineEntry}) on a scoreboard {@link Team}, and a refresh only rewrites that team's
 * prefix. Rows the current layout does not use have their score reset so they leave the sidebar.
 */
public class GameScoreboardManager {

    /** Ticks between refreshes. One second, matching the timer's own tick. */
    public static final long REFRESH_INTERVAL_TICKS = 20L;

    private static final String OBJECTIVE_NAME = "sot_sidebar";
    private static final String LINE_TEAM_PREFIX = "sot_line_";

    private final Plugin plugin;
    private final GameManager gameManager;

    /** Player -> the scoreboard built for them. Cleared when the display stops. */
    private final Map<UUID, Scoreboard> playerBoards = new HashMap<>();
    /** Team -> that team's boss bar. */
    private final Map<UUID, BossBar> teamBossBars = new HashMap<>();
    /** Player -> the boss bar currently shown to them, so it can be swapped or hidden again. */
    private final Map<UUID, BossBar> shownBossBars = new HashMap<>();

    private BukkitTask refreshTask;
    /** Keeps a server without a scoreboard manager from warning once per player per second. */
    private boolean warnedAboutMissingManager;

    public GameScoreboardManager(Plugin plugin, GameManager gameManager) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.gameManager = Objects.requireNonNull(gameManager, "GameManager cannot be null");
    }

    /** Shows the display and starts refreshing it. Does nothing if it is already running. */
    public void start() {
        if (refreshTask != null) {
            plugin.getLogger().fine("Live scoreboard already running.");
            return;
        }
        refresh();
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh,
                REFRESH_INTERVAL_TICKS, REFRESH_INTERVAL_TICKS);
        plugin.getLogger().info("Live scoreboard started.");
    }

    /**
     * Stops refreshing and takes the display away again: boss bars are hidden and every player is
     * handed the server's main scoreboard back. Safe to call when it is not running.
     */
    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }

        for (Map.Entry<UUID, BossBar> shown : shownBossBars.entrySet()) {
            Player player = Bukkit.getPlayer(shown.getKey());
            if (player != null && player.isOnline()) {
                player.hideBossBar(shown.getValue());
            }
        }
        shownBossBars.clear();
        teamBossBars.clear();

        Scoreboard mainScoreboard = mainScoreboard();
        for (UUID playerId : playerBoards.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (mainScoreboard != null && player != null && player.isOnline()) {
                player.setScoreboard(mainScoreboard);
            }
        }
        playerBoards.clear();
    }

    /** True while the refresh task is scheduled. */
    public boolean isRunning() {
        return refreshTask != null;
    }

    /**
     * Rebuilds every participant's sidebar and boss bar from the live game state. Called once a
     * second by the refresh task, and directly by {@link #start()} so the display is up before
     * the first tick of the interval elapses.
     */
    public void refresh() {
        Map<UUID, SoTTeam> activeTeams = gameManager.getActiveTeams();
        if (activeTeams.isEmpty()) {
            return;
        }

        Map<UUID, TeamSnapshot> snapshotsByTeam = new LinkedHashMap<>();
        for (SoTTeam team : activeTeams.values()) {
            snapshotsByTeam.put(team.getTeamId(), snapshot(team));
        }
        List<TeamSnapshot> ranked = ScoreboardLayout.rank(snapshotsByTeam.values());

        for (SoTTeam team : activeTeams.values()) {
            TeamSnapshot snapshot = snapshotsByTeam.get(team.getTeamId());
            BossBar bossBar = updateBossBar(snapshot);
            for (Player player : team.getOnlineMembers()) {
                showBossBar(player, bossBar);
                applySidebar(player, ranked, snapshot);
            }
        }

        forgetOfflinePlayers();
    }

    private TeamSnapshot snapshot(SoTTeam team) {
        return new TeamSnapshot(
                team.getTeamId(),
                team.getTeamName(),
                gameManager.getTeamManager().getTeamColor(team.getTeamId()),
                team.getBankedScore(),
                team.getRemainingSeconds());
    }

    // --- Boss bar ---

    private BossBar updateBossBar(TeamSnapshot snapshot) {
        float progress = ScoreboardLayout.bossBarProgress(
                snapshot.remainingSeconds(), TeamTimer.DEFAULT_MAX_TIMER_SECONDS);
        BossBar bossBar = teamBossBars.get(snapshot.teamId());
        if (bossBar == null) {
            bossBar = BossBar.bossBar(ScoreboardLayout.bossBarName(snapshot), progress,
                    ScoreboardLayout.bossBarColor(snapshot.remainingSeconds()), BossBar.Overlay.PROGRESS);
            teamBossBars.put(snapshot.teamId(), bossBar);
            return bossBar;
        }
        bossBar.name(ScoreboardLayout.bossBarName(snapshot));
        bossBar.progress(progress);
        bossBar.color(ScoreboardLayout.bossBarColor(snapshot.remainingSeconds()));
        return bossBar;
    }

    private void showBossBar(Player player, BossBar bossBar) {
        BossBar shown = shownBossBars.get(player.getUniqueId());
        if (bossBar.equals(shown)) {
            return;
        }
        if (shown != null) {
            player.hideBossBar(shown);
        }
        player.showBossBar(bossBar);
        shownBossBars.put(player.getUniqueId(), bossBar);
    }

    // --- Sidebar ---

    private void applySidebar(Player player, List<TeamSnapshot> ranked, TeamSnapshot viewerTeam) {
        Scoreboard board = playerBoards.get(player.getUniqueId());
        if (board == null) {
            board = createBoard();
            if (board == null) return;
            playerBoards.put(player.getUniqueId(), board);
        }
        // Re-applied every refresh rather than only on creation: a player who reconnects, or whom
        // another plugin moved, is back on the server's scoreboard until we hand them ours again.
        if (!board.equals(player.getScoreboard())) {
            player.setScoreboard(board);
        }

        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            // Another plugin unregistered it. Drop the board so the next refresh builds a fresh one.
            plugin.getLogger().warning("Sidebar objective missing for " + player.getName() + "; rebuilding it.");
            playerBoards.remove(player.getUniqueId());
            return;
        }

        int unbanked = gameManager.getScoreManager().getPlayerUnbankedScore(player.getUniqueId());
        List<Component> lines = ScoreboardLayout.sidebarLines(ranked, viewerTeam, unbanked);

        for (int line = 0; line < ScoreboardLayout.MAX_SIDEBAR_LINES; line++) {
            String entry = lineEntry(line);
            if (line >= lines.size()) {
                board.resetScores(entry);
                continue;
            }
            Team lineTeam = board.getTeam(LINE_TEAM_PREFIX + line);
            if (lineTeam != null) {
                lineTeam.prefix(lines.get(line));
            }
            // Scores sort descending, so the first line needs the highest score.
            objective.getScore(entry).setScore(lines.size() - line);
        }
    }

    @Nullable
    private Scoreboard createBoard() {
        ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
        if (scoreboardManager == null) {
            // Only reachable before the first world loads. Warn once: refresh runs every second.
            if (!warnedAboutMissingManager) {
                plugin.getLogger().log(Level.WARNING, "No scoreboard manager available; sidebar disabled.");
                warnedAboutMissingManager = true;
            }
            return null;
        }
        Scoreboard board = scoreboardManager.getNewScoreboard();
        Objective objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, ScoreboardLayout.title());
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (int line = 0; line < ScoreboardLayout.MAX_SIDEBAR_LINES; line++) {
            Team lineTeam = board.registerNewTeam(LINE_TEAM_PREFIX + line);
            lineTeam.addEntry(lineEntry(line));
        }
        return board;
    }

    @Nullable
    private Scoreboard mainScoreboard() {
        ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
        return scoreboardManager != null ? scoreboardManager.getMainScoreboard() : null;
    }

    /**
     * The invisible, permanent score entry that carries sidebar row {@code line}.
     *
     * <p>Entries must be unique within an objective and are drawn to the right of their team's
     * prefix, so each row gets a distinct pair of legacy formatting codes: they render as nothing
     * while keeping the rows distinct, which lets the visible text live entirely in the prefix.
     */
    static String lineEntry(int line) {
        if (line < 0 || line >= ScoreboardLayout.MAX_SIDEBAR_LINES) {
            throw new IllegalArgumentException("Sidebar line out of range: " + line);
        }
        return "\u00A7" + Integer.toHexString(line) + "\u00A7r";
    }

    /**
     * Drops cached entries for players who have logged out, so a long round does not accumulate
     * scoreboards for people who are no longer here.
     */
    private void forgetOfflinePlayers() {
        List<UUID> offline = new ArrayList<>();
        for (UUID playerId : playerBoards.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) offline.add(playerId);
        }
        for (UUID playerId : offline) {
            playerBoards.remove(playerId);
            shownBossBars.remove(playerId);
        }
    }
}
