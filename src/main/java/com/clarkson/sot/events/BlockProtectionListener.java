package com.clarkson.sot.events;

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Keeps players from dismantling the dungeon while a round is live.
 *
 * <p>Two rules, deliberately scoped differently:
 * <ul>
 *   <li><b>The whitelist.</b> A participant may break only the materials in
 *       {@link BreakableBlocks#BREAKABLE}, and may not place blocks at all. Non-participants — staff
 *       building elsewhere in the world while a round runs — are untouched, since every handler here
 *       is world-agnostic.</li>
 *   <li><b>The timer column.</b> The sand column that visualises a team's clock is protected from
 *       <em>everyone</em>, participant or not. It is made of {@code SAND}, so the whitelist alone
 *       would wave it through.</li>
 * </ul>
 * Players in {@link GameMode#CREATIVE} or {@link GameMode#SPECTATOR} bypass both — that is how an
 * admin fixes something mid-round, and no participant is ever put in those modes.
 *
 * <p><b>Why the timer column needs its own rule.</b> Before this listener, mining a column block
 * really broke it, and {@code SandManager.onBlockBreak} paid out for it: +1 sand and
 * {@code team.addSeconds(10)}. {@code TeamTimer.addSeconds} then calls {@code syncVisual()} →
 * {@code VisualSandTimerDisplay.syncVisualState()} → {@code addSandToTop()}, which puts the mined
 * block straight back. A player at their own hub column could mine it forever, pinning the timer at
 * its maximum so the round never ended and banking unlimited sand for free revives.
 *
 * <p><b>Event priority is load-bearing.</b> These handlers run at {@link EventPriority#LOW}, ahead
 * of {@code SandManager.onBlockBreak} at {@code NORMAL, ignoreCancelled = true}. Cancelling here
 * therefore makes Bukkit skip {@code SandManager} entirely, so a denied break credits no sand and
 * adds no seconds without {@code SandManager} needing to know this listener exists. Moving these to
 * {@code HIGH} would let the payout happen first and reopen the exploit.
 *
 * @see BreakableBlocks
 */
public class BlockProtectionListener implements Listener {

    private static final Component TIMER_DENIED =
            Component.text("The sand timer can't be broken!", NamedTextColor.RED);
    private static final Component NOT_BREAKABLE =
            Component.text("You can only break sand and spawners.", NamedTextColor.RED);
    private static final Component NOT_PLACEABLE =
            Component.text("You can't place blocks during a round.", NamedTextColor.RED);

    private final GameManager gameManager;

    public BlockProtectionListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        // Outside a live round the world belongs to the builders; do nothing at all, so the
        // segment builder tools keep working between games.
        if (!GameManager.isRoundLive(gameManager.getCurrentState())) return;

        Player player = event.getPlayer();
        if (bypasses(player)) return;

        Block block = event.getBlock();

        // Checked before the participant gate on purpose: nobody gets to mine a team's timer,
        // including an operator who teleported into a dungeon.
        if (gameManager.isVisualTimerBlock(block.getLocation())) {
            deny(event, player, TIMER_DENIED);
            return;
        }

        if (!gameManager.isParticipant(player.getUniqueId())) return;

        if (BreakableBlocks.isBreakableDuringRound(block.getType())) return;

        // Money blocks (not implemented): if the coin-block feature reuses a material the dungeon
        // already places, it cannot be recognised by material alone. Ask its location registry
        // here -- e.g. gameManager.isMoneyBlockAt(block.getLocation()) -- before denying.

        deny(event, player, NOT_BREAKABLE);
    }

    /**
     * Blocks all placement by participants during a round. There is no mechanic in the game that
     * requires a player to place a block, and placing one is a way to break things: a block dropped
     * into the timer column's path makes {@code VisualSandTimerDisplay.addSandToTop} log
     * "Visual timer path obstructed" and give up on refilling the column.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!GameManager.isRoundLive(gameManager.getCurrentState())) return;

        Player player = event.getPlayer();
        if (bypasses(player)) return;
        if (!gameManager.isParticipant(player.getUniqueId())) return;

        deny(event, player, NOT_PLACEABLE);
    }

    /** Creative and Spectator are the admin escape hatch; participants are never in either. */
    private boolean bypasses(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
    }

    /**
     * Cancels the event and tells the player why on the action bar rather than in chat: the action
     * bar overwrites itself, so holding left-click on a protected block cannot flood their chat.
     */
    private void deny(Cancellable event, Player player, Component reason) {
        event.setCancelled(true);
        player.sendActionBar(reason);
    }
}
