package com.clarkson.sot.utils;

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.main.SoT;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class GameListener implements Listener {

    private final GameManager gameManager; // Main interaction point

    public GameListener(GameManager gameManager, SoT plugin) {
        this.gameManager = gameManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        // Check if player is in the game (via PlayerStateManager)
        // Check if game state is RUNNING
        if (gameManager.getCurrentState() == GameState.RUNNING && gameManager.getPlayerStateManager().isPlayerInDungeon(player)) {
            event.getDrops().clear(); // Prevent normal item drops if desired
            event.setKeepInventory(true); // Maybe? Or handle inventory saving manually
            // Delay slightly if needed before handling death logic
            // Bukkit.getScheduler().runTaskLater(plugin, () -> gameManager.handlePlayerDeath(player), 1L);
             gameManager.handlePlayerDeath(player);
        }
    }

    // @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        // Check game state
        // Check if interacting with Timer, Sphinx, Vault, Key, Sacrifice Point...
        // Delegate to appropriate managers (SandManager, VaultManager, BankingManager)
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock != null) {
            // Example: Check if block is the central timer block
             // Location timerLoc = gameManager.getDungeonHubLocation().getBlock().getRelative(BlockFace.UP).getLocation(); // Example
            // if (clickedBlock.getLocation().equals(timerLoc)) {
            //    if (player.getInventory().getItemInMainHand().getType() == Material.SAND) {
            //       gameManager.getSandManager().useSandForTimer(player, player.getInventory().getItemInMainHand().getAmount());
            //       player.getInventory().setItemInMainHand(null); // Consume sand
            //    }
            //}

             // Example: Check if block is a sand sacrifice location (needs identification method)
             // if (isSandSacrificeLocation(clickedBlock.getLocation())) {
             //    int requiredSand = getSacrificeCost(clickedBlock.getLocation());
             //    if (gameManager.getSandManager().attemptSandSacrifice(player, requiredSand)) {
             //       triggerSacrificeReward(clickedBlock.getLocation());
             //    } else {
             //       player.sendMessage("Not enough team sand!");
             //    }
             //}
        }
    }

     // @EventHandler
     public void onPlayerPickupItem(EntityPickupItemEvent event) {
         if (event.getEntity() instanceof Player) {
             Player player = (Player) event.getEntity();
             ItemStack itemStack = event.getItem().getItemStack();
             // Check if item is SAND
             if (itemStack.getType() == Material.SAND) {
                 // Let FloorItemManager handle pickup visuals etc.
                 // Then notify SandManager
                 // gameManager.getSandManager().collectSandItem(player, itemStack.getAmount());
             }
             // Check if item is a specific KEY (VaultManager needs to identify key items)
              else if (gameManager.getVaultManager().isVaultKey(itemStack)) {
                  // VaultManager might handle key pickup/tracking
              }
              // Check if item is a COIN (needs identification beyond Material.GOLD_NUGGET/INGOT?)
              else if (isCoinItem(itemStack)) {
                  int baseValue = getCoinValue(itemStack);
                  // Let FloorItemManager handle pickup
                  // Notify ScoreManager
                  // gameManager.getScoreManager().playerCollectedCoin(player, baseValue, event.getItem().getLocation());
              }
         }
     }

    // Add other necessary event handlers:
    // - PlayerQuitEvent (handle players leaving mid-game)
    // - PlayerInteractEntityEvent (for Sphinx, maybe revival?)
    // - BlockPlaceEvent (e.g., placing carpet markers?)

    // Helper methods for checks (isCoinItem, isSandSacrificeLocation, etc.) would go here or in respective managers
     private boolean isCoinItem(ItemStack item) { /* TODO */ return false; }
     private int getCoinValue(ItemStack item) { /* TODO */ return 1; }

}
