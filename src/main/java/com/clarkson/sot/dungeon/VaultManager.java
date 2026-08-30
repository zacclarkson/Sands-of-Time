package com.clarkson.sot.dungeon; // Assuming package

import com.clarkson.sot.events.FloorItemManager;
import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.main.SoT; // Assuming main plugin class
import com.clarkson.sot.utils.ItemManager;

// Adventure API Imports
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

// Bukkit API Imports
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap; // For thread safety
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Manages Vaults, Keys, and their placement/interaction within specific dungeon instances.
 * Tracks vault open state per team.
 */
public class VaultManager implements Listener {

    private final SoT plugin;
    private final GameManager gameManager;

    // State: Tracks which vaults are open for each team instance
    // Key: Team UUID, Value: Set of VaultColors opened by that team
    private final Map<UUID, Set<VaultColor>> openVaultsByTeam;

    // Constructor remains the same...
    public VaultManager(SoT plugin, GameManager gameManager) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.gameManager = Objects.requireNonNull(gameManager, "GameManager cannot be null");
        this.openVaultsByTeam = new ConcurrentHashMap<>();
        // Registered as a listener by SoT.onEnable, which is the single registration point for the
        // GameManager-owned manager instances. Registering here too would double-fire every event.
        plugin.getLogger().info("VaultManager initialized.");
    }

    /**
     * Initializes/resets the vault state for a specific team instance.
     * Places vault marker blocks and key items based on the provided Dungeon data.
     * Should be called by DungeonManager when an instance is created.
     *
     * @param dungeonData The Dungeon object containing absolute locations for this instance.
     */
    public void initializeForInstance(@Nonnull Dungeon dungeonData) {
        UUID teamId = dungeonData.getTeamId();
        plugin.getLogger().info("Initializing vaults and keys for team instance: " + teamId);
        openVaultsByTeam.put(teamId, Collections.synchronizedSet(new HashSet<>()));
        for (Map.Entry<VaultColor, Location> entry : dungeonData.getVaultMarkerLocations().entrySet()) {
            VaultColor color = entry.getKey();
            Location loc = entry.getValue();
            if (loc != null && loc.isWorldLoaded()) {
                placeVaultMarkerBlock(color, loc);
            } else {
                plugin.getLogger().warning("Invalid location for " + color + " vault marker for team " + teamId);
            }
        }
        UUID instanceId = dungeonData.getInstanceId();
        for (Map.Entry<VaultColor, Location> entry : dungeonData.getKeySpawnLocations().entrySet()) {
             VaultColor color = entry.getKey();
             Location loc = entry.getValue();
             if (loc != null && loc.isWorldLoaded()) {
                 placeKeyItem(color, loc, teamId, instanceId);
             } else {
                 plugin.getLogger().warning("Invalid location for " + color + " key spawn for team " + teamId);
             }
        }
        plugin.getLogger().info("Finished initializing vaults and keys for team instance: " + teamId);
    }

    /**
     * Clears the vault state for a specific team.
     * Called when a game ends or an instance is cleaned up.
     * @param teamId The UUID of the team whose state should be cleared.
     */
     public void clearTeamState(UUID teamId) {
         openVaultsByTeam.remove(teamId);
         plugin.getLogger().info("Cleared vault state for team: " + teamId);
     }

    /**
     * Places the physical vault marker block in the world.
     * @param color The VaultColor.
     * @param location The absolute Location.
     */
    private void placeVaultMarkerBlock(VaultColor color, Location location) {
        Material vaultMaterial = getVaultMaterial(color);
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> placeVaultMarkerBlock(color, location));
            return;
        }
        try {
            location.getBlock().setType(vaultMaterial, false);
            plugin.getLogger().finer("Placed " + color + " vault marker block at " + location.toVector());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to set vault marker block for " + color + " at " + location.toVector(), e);
        }
    }

    /**
     * Spawns the key at the specified location as a tracked {@link com.clarkson.sot.entities.Key}
     * floor item, so it is picked up by the same proximity path as coins and floor loot.
     * <p>This used to be a {@code dropItemNaturally} call, which was wrong three ways: the drop's
     * random velocity slid the key off the block the builder marked, any team could walk over it,
     * and it despawned after the vanilla five minutes — often before the round even began, since
     * dungeons are generated at {@code /sot setup} but only become live at {@code /sot start}.
     *
     * @param color The VaultColor of the key.
     * @param location The absolute Location to spawn the key on.
     * @param teamId The team whose dungeon instance owns this key.
     * @param instanceId The dungeon instance id, used as the key's segment instance id.
     */
    private void placeKeyItem(VaultColor color, Location location, UUID teamId, UUID instanceId) {
         if (!Bukkit.isPrimaryThread()) {
             Bukkit.getScheduler().runTask(plugin, () -> placeKeyItem(color, location, teamId, instanceId));
             return;
         }
        FloorItemManager floorItemManager = gameManager.getFloorItemManager();
        if (floorItemManager == null) {
            plugin.getLogger().severe("Cannot spawn " + color + " key: FloorItemManager is null");
            return;
        }
        try {
            // Depth 0: depth only scales coin value (ScoreManager.calculateScaledCoinValue) and a key
            // is worth no coins. VaultManager also has no placed-segment list to measure depth from.
            floorItemManager.spawnKey(location, color, teamId, instanceId, 0);
        } catch (Exception e) {
             plugin.getLogger().log(Level.SEVERE, "Failed to spawn key floor item for " + color + " at " + location.toVector(), e);
        }
    }


    // --- Event Handling (onPlayerInteract) remains the same ---
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // ... (event handling logic as before) ...
        if (gameManager.getCurrentState() != GameState.RUNNING) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        Player player = event.getPlayer();
        UUID teamId = gameManager.getTeamManager().getPlayerTeamId(player);
        if (teamId == null) return;
        DungeonManager dungeonManager = gameManager.getTeamDungeonManager(teamId);
        if (dungeonManager == null) return;
        Dungeon dungeonData = dungeonManager.getDungeonData();
        if (dungeonData == null) return;

        VaultColor clickedVaultColor = null;
        Location clickedLocation = clickedBlock.getLocation();
        for (Map.Entry<VaultColor, Location> entry : dungeonData.getVaultMarkerLocations().entrySet()) {
            Location vaultLoc = entry.getValue();
             if (vaultLoc.getWorld().equals(clickedLocation.getWorld()) &&
                 vaultLoc.getBlockX() == clickedLocation.getBlockX() &&
                 vaultLoc.getBlockY() == clickedLocation.getBlockY() &&
                 vaultLoc.getBlockZ() == clickedLocation.getBlockZ())
             {
                 clickedVaultColor = entry.getKey();
                 break;
             }
        }

        if (clickedVaultColor == null) {
            return;
        }

        event.setCancelled(true);

        if (isVaultOpen(teamId, clickedVaultColor)) {
            player.sendMessage(Component.text("This vault has already been opened!", NamedTextColor.YELLOW));
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (!isVaultKey(itemInHand)) {
             player.sendMessage(Component.text("You need the ", NamedTextColor.RED)
                 .append(Component.text(clickedVaultColor.name(), getVaultColorTextColor(clickedVaultColor)))
                 .append(Component.text(" key to open this vault!", NamedTextColor.RED)));
             return;
        }

        VaultColor keyColor = getKeyColor(itemInHand);
        if (keyColor != clickedVaultColor) {
            player.sendMessage(Component.text("This key doesn't fit this vault!", NamedTextColor.RED));
            return;
        }

        plugin.getLogger().info("Player " + player.getName() + " attempting to open " + clickedVaultColor + " vault for team " + teamId);

        if (consumeKeyItem(player, keyColor)) {
            markVaultOpen(teamId, clickedVaultColor);
            revealVault(teamId, clickedVaultColor, clickedBlock.getLocation());
            openVaultEffects(player, clickedVaultColor);
        } else {
            player.sendMessage(Component.text("Error: Could not consume the key from your inventory!", NamedTextColor.RED));
            plugin.getLogger().warning("Failed to consume key " + keyColor + " from " + player.getName() + " even after checks passed.");
        }
    }

    /**
     * Sinks the vault door -- and the marker block with it -- to reveal what is behind the wall.
     *
     * <p>The reward is whatever the segment places <em>behind</em> the vault door, uncovered as the
     * wall drops. Coins used to be spawned in a scatter around the marker instead, which put them in
     * front of the wall the vault was supposed to be sealing, and left the marker itself standing in
     * mid-air as a glass block once the wall fell.
     *
     * <p>Deliberately a one-line hand-off to {@link DoorManager}: the vault marker click is this
     * manager's alone (bug #65), and the wall belongs to DoorManager, which built it and owns its
     * animation task.
     */
    private void revealVault(UUID teamId, VaultColor color, Location markerLocation) {
        DoorManager doorManager = gameManager.getDoorManager();
        if (doorManager != null && doorManager.openVaultDoors(teamId, color, markerLocation) > 0) {
            return;
        }
        // No wall to sink the marker with -- a segment can declare a vault marker and no vault door --
        // so clear it here, or it stands as a solid block over the reward it was guarding.
        plugin.getLogger().fine("No " + color + " vault door for team " + teamId + "; clearing the marker alone.");
        new BukkitRunnable() {
            @Override
            public void run() {
                Block block = markerLocation.getBlock();
                if (block.getType() == getVaultMaterial(color)) block.setType(Material.AIR, false);
            }
        }.runTask(plugin);
    }

    /**
     * Messages the opener and their team. The marker block is not touched here -- it sinks with the
     * vault door in {@link #revealVault}.
     *
     * @param player The player who opened the vault.
     * @param vaultColor The color of the vault.
     */
    private void openVaultEffects(Player player, VaultColor vaultColor) {
        player.sendMessage(Component.text("You opened the ", NamedTextColor.GREEN)
            .append(Component.text(vaultColor.name(), getVaultColorTextColor(vaultColor)))
            .append(Component.text(" vault!", NamedTextColor.GREEN)));

        Component broadcast = Component.text(player.getName(), getVaultColorTextColor(vaultColor))
            .append(Component.text(" has opened the ", NamedTextColor.GOLD))
            .append(Component.text(vaultColor.name(), getVaultColorTextColor(vaultColor)))
            .append(Component.text(" vault!", NamedTextColor.GOLD));
        UUID teamId = gameManager.getTeamManager().getPlayerTeamId(player);
        if (teamId != null) {
             gameManager.getTeamManager().getTeamMemberUUIDs(teamId).forEach(memberId -> {
                 Player member = Bukkit.getPlayer(memberId);
                 if (member != null && member.isOnline()) {
                     member.sendMessage(broadcast);
                 }
             });
        }

        plugin.getLogger().info(vaultColor + " vault opened by " + player.getName());
    }

    // --- State Management (isVaultOpen, markVaultOpen) remains the same ---
    public boolean isVaultOpen(UUID teamId, VaultColor color) {
        Set<VaultColor> openSet = openVaultsByTeam.get(teamId);
        return openSet != null && openSet.contains(color);
    }
    private void markVaultOpen(UUID teamId, VaultColor color) {
        openVaultsByTeam.computeIfAbsent(teamId, k -> Collections.synchronizedSet(new HashSet<>())).add(color);
    }


    // --- Key Item Utility Methods ---
    // Key items are built and identified by ItemManager alone, so the keys this manager spawns are
    // the same items VaultDoor and SegmentDoor accept.

    /** Creates a vault key ItemStack for the given colour. */
    public ItemStack createKeyItem(VaultColor color) {
        return ItemManager.createVaultKey(color);
    }

    /** Checks whether the item is any vault key. */
    public boolean isVaultKey(@Nullable ItemStack item) {
        return ItemManager.isVaultKey(item);
    }

    /** Gets the colour of a vault key, or null if the item is not a vault key. */
    @Nullable
    public VaultColor getKeyColor(@Nullable ItemStack item) {
        return ItemManager.getVaultKeyColor(item);
    }

    /**
     * Removes one vault key of the given colour from the player's inventory.
     * Matching is by PDC tag rather than by comparing against a freshly built key, so a key still
     * counts even if its display name or lore is changed later.
     */
    private boolean consumeKeyItem(Player player, VaultColor color) {
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack currentItem = inventory.getItem(i);
            if (currentItem != null && ItemManager.getVaultKeyColor(currentItem) == color) {
                 if (currentItem.getAmount() > 1) {
                     currentItem.setAmount(currentItem.getAmount() - 1);
                 } else {
                     inventory.setItem(i, null);
                 }
                 player.updateInventory();
                 return true;
            }
        }
        return false;
    }

    // --- Visual Helpers (getVaultMaterial, getVaultColorTextColor) remain the same ---
    private Material getVaultMaterial(VaultColor color) {
         switch (color) {
             case BLUE: return Material.BLUE_CONCRETE;
             case RED: return Material.RED_CONCRETE;
             case GREEN: return Material.LIME_CONCRETE;
             case GOLD: return Material.GOLD_BLOCK;
             default: return Material.STONE;
         }
     }
    private TextColor getVaultColorTextColor(VaultColor color) {
        switch (color) {
            case BLUE: return NamedTextColor.BLUE;
            case RED: return NamedTextColor.RED;
            case GREEN: return NamedTextColor.GREEN;
            case GOLD: return NamedTextColor.GOLD;
            default: return NamedTextColor.WHITE;
        }
    }


    /**
     * Clears the vault open state for ALL teams.
     * Should be called by GameManager when the game ends or resets.
     */
    public void clearAllTeamStates() {
        int count = openVaultsByTeam.size();
        openVaultsByTeam.clear(); // Clear the entire map
        plugin.getLogger().info("Cleared vault states for " + count + " teams.");
    }

     // TODO: Add method to get CustomModelData for keys if needed
     // private int getCustomModelDataForKey(VaultColor color) { ... }

}
