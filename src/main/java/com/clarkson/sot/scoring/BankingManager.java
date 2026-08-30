package com.clarkson.sot.scoring;

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.utils.PlayerStatus;
import com.clarkson.sot.utils.SoTTeam;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Manages coin banking at the team's bank — the ender chest built in the hub at the segment
 * template's {@code BANK} marker. Right-clicking it deposits every unbanked coin the player is
 * carrying, minus a 20% tax.
 *
 * <p>Holds no per-team state: the bank cell is looked up through
 * {@link GameManager#isTeamBankAt(UUID, Location)} against the team's live dungeon, so there is
 * nothing to clear between rounds.
 */
public class BankingManager implements Listener {
    private final ScoreManager scoreManager;
    private final GameManager gameManager;
    private final Plugin plugin;

    private static final double BANKING_TAX = 0.20;

    public BankingManager(ScoreManager scoreManager, GameManager gameManager, Plugin plugin) {
        this.scoreManager = scoreManager;
        this.gameManager = gameManager;
        this.plugin = plugin;
        // Registered as a listener by SoT.onEnable, the single registration point for the
        // GameManager-owned manager instances.
    }

    /**
     * Banks ALL of a player's unbanked coins with the 20% tax.
     * Called when a player right-clicks their team's bank.
     */
    public void attemptBanking(Player player) {
        UUID playerUUID = player.getUniqueId();
        int unbankedCoins = scoreManager.getPlayerUnbankedScore(playerUUID);

        if (unbankedCoins <= 0) {
            player.sendMessage(Component.text("You have no coins to bank!", NamedTextColor.YELLOW));
            return;
        }

        // Resolve the team BEFORE clearing: a player with no team assignment must keep their coins
        // rather than have them cleared into nothing.
        UUID teamId = gameManager.getTeamManager().getPlayerTeamId(player);
        SoTTeam team = (teamId != null) ? gameManager.getActiveTeams().get(teamId) : null;
        if (team == null) {
            player.sendMessage(Component.text("You are not on a team, so you have nowhere to bank.",
                    NamedTextColor.RED));
            plugin.getLogger().warning(player.getName() + " tried to bank with no team assignment.");
            return;
        }

        int taxAmount = (int) Math.round(unbankedCoins * BANKING_TAX);
        int bankedAmount = unbankedCoins - taxAmount;

        scoreManager.clearPlayerUnbankedScore(playerUUID);
        team.addBankedScore(bankedAmount);

        // Feedback
        player.sendMessage(Component.text("Banked ", NamedTextColor.GREEN)
                .append(Component.text(bankedAmount + " coins", NamedTextColor.GOLD))
                .append(Component.text(" (tax: " + taxAmount + ")", NamedTextColor.GRAY)));
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.4f);
        plugin.getLogger().fine(player.getName() + " banked " + bankedAmount + " coins (tax: " + taxAmount
                + ", original: " + unbankedCoins + ")");
    }

    /**
     * Right-clicking the bank block banks everything the player is carrying.
     *
     * <p>The event is cancelled so the vanilla ender chest inventory never opens — the chest is a
     * button, not storage.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (gameManager.getCurrentState() != GameState.RUNNING) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // PlayerInteractEvent fires once per hand; without this the off-hand pass runs attemptBanking
        // a second time and reports "You have no coins to bank!" over the message that just landed.
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        Player player = event.getPlayer();
        UUID teamId = gameManager.getTeamManager().getPlayerTeamId(player);
        if (teamId == null) return;
        if (!gameManager.isTeamBankAt(teamId, clickedBlock.getLocation())) return;

        event.setCancelled(true);

        // Banking is a dungeon action: an escaped or dead player is done for the round. Mirrors the
        // way SandManager refuses to let an ESCAPED_SAFE player spend sand.
        if (gameManager.getPlayerStateManager().getStatus(player) != PlayerStatus.ALIVE_IN_DUNGEON) {
            return;
        }

        attemptBanking(player);
    }

    /**
     * Keeps the bank in the hub. An ender chest mined without silk touch drops 8 obsidian and takes
     * the team's only banking point out of the round with it.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockBreak(BlockBreakEvent event) {
        if (gameManager.getCurrentState() != GameState.RUNNING) return;

        UUID teamId = gameManager.getTeamManager().getPlayerTeamId(event.getPlayer());
        if (teamId == null) return;
        if (!gameManager.isTeamBankAt(teamId, event.getBlock().getLocation())) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text("You cannot break the bank!", NamedTextColor.RED));
    }
}
