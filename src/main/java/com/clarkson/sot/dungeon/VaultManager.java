package com.clarkson.sot.dungeon; // Assuming package

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.main.SoT; // Assuming main plugin class

// Adventure API Imports
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

// Bukkit API Imports
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
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

    // PDC Keys for identifying key items
    private final NamespacedKey vaultKeyTagKey; // Tag identifying an item as a vault key
    private final NamespacedKey vaultColorTagKey; // Tag storing the VaultColor string on the key

    // Constructor remains the same...
    public VaultManager(SoT plugin, GameManager gameManager) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.gameManager = Objects.requireNonNull(gameManager, "GameManager cannot be null");
        this.openVaultsByTeam = new ConcurrentHashMap<>();
        this.vaultKeyTagKey = new NamespacedKey(plugin, "sot_vault_key");
        this.vaultColorTagKey = new NamespacedKey(plugin, "sot_vault_color");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("VaultManager initialized and registered.");
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
        for (Map.Entry<VaultColor, Location> entry : dungeonData.getKeySpawnLocations().entrySet()) {
             VaultColor color = entry.getKey();
             Location loc = entry.getValue();
             if (loc != null && loc.isWorldLoaded()) {
                 placeKeyItem(color, loc);
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
     * Creates and drops the key item at the specified location.
     * @param color The VaultColor of the key.
     * @param location The absolute Location to spawn the item.
     */
    private void placeKeyItem(VaultColor color, Location location) {
        ItemStack keyStack = createKeyItem(color);
         if (!Bukkit.isPrimaryThread()) {
             Bukkit.getScheduler().runTask(plugin, () -> placeKeyItem(color, location));
             return;
         }
        try {
            Location dropLocation = location.clone().add(0.5, 0.5, 0.5);
            location.getWorld().dropItemNaturally(dropLocation, keyStack);
            plugin.getLogger().finer("Spawned " + color + " key item near " + location.toVector());
        } catch (Exception e) {
             plugin.getLogger().log(Level.SEVERE, "Failed to drop key item for " + color + " near " + location.toVector(), e);
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
            openVaultEffects(player, clickedVaultColor, clickedBlock.getLocation());
            // TODO: Trigger reward logic
        } else {
            player.sendMessage(Component.text("Error: Could not consume the key from your inventory!", NamedTextColor.RED));
            plugin.getLogger().warning("Failed to consume key " + keyColor + " from " + player.getName() + " even after checks passed.");
        }
    }

    /**
     * Performs the visual effects and messaging for opening a vault.
     * @param player The player who opened the vault.
     * @param vaultColor The color of the vault.
     * @param vaultLocation The location of the vault marker block.
     */
    private void openVaultEffects(Player player, VaultColor vaultColor, Location vaultLocation) {
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

        new BukkitRunnable() {
            @Override
            public void run() {
                Block block = vaultLocation.getBlock();
                if(block.getType() == getVaultMaterial(vaultColor)) {
                    block.setType(Material.GLASS);
                    // TODO: Add particle/sound effects?
                }
            }
        }.runTask(plugin);

        plugin.getLogger().info(vaultColor + " vault at " + vaultLocation.toVector() + " opened by " + player.getName());
    }


    // --- State Management (isVaultOpen, markVaultOpen) remains the same ---
    public boolean isVaultOpen(UUID teamId, VaultColor color) {
        Set<VaultColor> openSet = openVaultsByTeam.get(teamId);
        return openSet != null && openSet.contains(color);
    }
    private void markVaultOpen(UUID teamId, VaultColor color) {
        openVaultsByTeam.computeIfAbsent(teamId, k -> Collections.synchronizedSet(new HashSet<>())).add(color);
    }


    // --- Key Item Utility Methods (createKeyItem, isVaultKey, getKeyColor, consumeKeyItem) remain the same ---
    public ItemStack createKeyItem(VaultColor color) {
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK, 1);
        ItemMeta meta = key.getItemMeta();
        if (meta != null) {
            Component displayName = Component.text(color.name() + " Vault Key", getVaultColorTextColor(color), TextDecoration.BOLD)
                                            .decoration(TextDecoration.ITALIC, false);
            meta.displayName(displayName);
            List<Component> loreComponents = new ArrayList<>();
            loreComponents.add(Component.text("Used to unlock the " + color.name().toLowerCase() + " vault", NamedTextColor.GRAY)
                                        .decoration(TextDecoration.ITALIC, false));
            loreComponents.add(Component.empty());
            loreComponents.add(Component.text("Sands of Time Item", NamedTextColor.DARK_GRAY)
                                        .decoration(TextDecoration.ITALIC, false));
            meta.lore(loreComponents);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(vaultKeyTagKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(vaultColorTagKey, PersistentDataType.STRING, color.name());
            key.setItemMeta(meta);
        }
        return key;
    }
    public boolean isVaultKey(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(vaultKeyTagKey, PersistentDataType.BYTE);
    }
    @Nullable
    public VaultColor getKeyColor(@Nullable ItemStack item) {
        if (!isVaultKey(item)) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String colorString = pdc.get(vaultColorTagKey, PersistentDataType.STRING);
        if (colorString != null) {
            try {
                return VaultColor.valueOf(colorString);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid VaultColor string found in item PDC: " + colorString);
                return null;
            }
        }
        plugin.getLogger().warning("Vault key item missing color tag!");
        return null;
    }
    private boolean consumeKeyItem(Player player, VaultColor color) {
        PlayerInventory inventory = player.getInventory();
        ItemStack keyToConsume = createKeyItem(color);
        keyToConsume.setAmount(1);
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack currentItem = inventory.getItem(i);
            if (currentItem != null && currentItem.isSimilar(keyToConsume)) {
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
