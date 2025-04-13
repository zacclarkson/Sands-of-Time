package com.clarkson.sot.dungeon; // Assuming package

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.main.SoT; // Assuming main plugin class
import com.clarkson.sot.utils.SoTTeam; // May need for context
import com.clarkson.sot.utils.TeamManager; // To get player's team

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
import org.bukkit.World;
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
import org.bukkit.plugin.Plugin; // Use Plugin interface
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

    // Offset for the Blue Key relative to the Hub's origin/center point
    // This might be defined here or retrieved from Hub segment metadata via Dungeon object
    // For simplicity here, let's assume it's defined, but using metadata is more flexible.
    // private static final Vector BLUE_KEY_OFFSET_FROM_HUB = new Vector(0, -2, 0); // Example

    public VaultManager(SoT plugin, GameManager gameManager) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.gameManager = Objects.requireNonNull(gameManager, "GameManager cannot be null");

        // Use ConcurrentHashMap for thread safety if accessed asynchronously
        this.openVaultsByTeam = new ConcurrentHashMap<>();

        // Initialize NamespacedKeys using the plugin instance
        this.vaultKeyTagKey = new NamespacedKey(plugin, "sot_vault_key");
        this.vaultColorTagKey = new NamespacedKey(plugin, "sot_vault_color");

        // Register this class as an event listener
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
        World world = dungeonData.getWorld();
        plugin.getLogger().info("Initializing vaults and keys for team instance: " + teamId);

        // Reset open state for this team
        openVaultsByTeam.put(teamId, Collections.synchronizedSet(new HashSet<>()));

        // --- Place Vault Markers ---
        for (Map.Entry<VaultColor, Location> entry : dungeonData.getVaultMarkerLocations().entrySet()) {
            VaultColor color = entry.getKey();
            Location loc = entry.getValue();
            if (loc != null && loc.isWorldLoaded()) {
                placeVaultMarkerBlock(color, loc);
            } else {
                plugin.getLogger().warning("Invalid location for " + color + " vault marker for team " + teamId);
            }
        }

        // --- Place Key Items ---
        // Note: Blue Key placement logic is now implicitly handled if dungeonData provides
        // the correct absolute location calculated from the Hub's relative offset.
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
        // Ensure execution on the main thread for block changes
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> placeVaultMarkerBlock(color, location));
            return;
        }
        try {
            location.getBlock().setType(vaultMaterial, false); // false = don't apply physics if possible
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
        // Ensure execution on the main thread for entity spawning
         if (!Bukkit.isPrimaryThread()) {
             Bukkit.getScheduler().runTask(plugin, () -> placeKeyItem(color, location));
             return;
         }
        try {
            // Center the drop location slightly for better appearance/pickup
            Location dropLocation = location.clone().add(0.5, 0.5, 0.5);
            location.getWorld().dropItemNaturally(dropLocation, keyStack);
            plugin.getLogger().finer("Spawned " + color + " key item near " + location.toVector());
        } catch (Exception e) {
             plugin.getLogger().log(Level.SEVERE, "Failed to drop key item for " + color + " near " + location.toVector(), e);
        }
    }


    // --- Event Handling ---

    @EventHandler(priority = EventPriority.NORMAL) // Normal priority is usually fine
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Ensure the game is running
        if (gameManager.getCurrentState() != GameState.RUNNING) return;
        // We only care about right-clicking a block
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // We need a clicked block
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        Player player = event.getPlayer();
        UUID teamId = gameManager.getTeamManager().getPlayerTeamId(player); // Get player's team

        // Player must be on a team and the team must have an active dungeon instance
        if (teamId == null) return;
        DungeonManager dungeonManager = gameManager.getTeamDungeonManager(teamId);
        if (dungeonManager == null) return;
        Dungeon dungeonData = dungeonManager.getDungeonData();
        if (dungeonData == null) return; // Instance data not ready

        // Check if the clicked block location matches a vault marker FOR THIS TEAM'S INSTANCE
        VaultColor clickedVaultColor = null;
        Location clickedLocation = clickedBlock.getLocation();
        for (Map.Entry<VaultColor, Location> entry : dungeonData.getVaultMarkerLocations().entrySet()) {
            // Compare block coordinates for robustness
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

        // If it wasn't a vault marker for this team, do nothing further
        if (clickedVaultColor == null) {
            return;
        }

        // It's a vault marker for this team's instance, cancel the event
        event.setCancelled(true);

        // Check if this specific vault is already open for this team
        if (isVaultOpen(teamId, clickedVaultColor)) {
            player.sendMessage(Component.text("This vault has already been opened!", NamedTextColor.YELLOW));
            return;
        }

        // Check if the player is holding the correct key
        ItemStack itemInHand = player.getInventory().getItemInMainHand(); // Check main hand
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

        // --- Conditions met: Correct key, vault not open ---
        plugin.getLogger().info("Player " + player.getName() + " attempting to open " + clickedVaultColor + " vault for team " + teamId);

        // Consume the key
        if (consumeKeyItem(player, keyColor)) {
            // Mark as open for the team
            markVaultOpen(teamId, clickedVaultColor);

            // Perform opening actions (visual change, messages, rewards)
            openVaultEffects(player, clickedVaultColor, clickedBlock.getLocation());

            // TODO: Trigger reward logic (e.g., call ScoreManager)
            // gameManager.getScoreManager().vaultOpened(player, clickedVaultColor);

        } else {
            // This shouldn't happen if isVaultKey and getKeyColor worked, but handle defensively
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
        // Success Messages
        player.sendMessage(Component.text("You opened the ", NamedTextColor.GREEN)
            .append(Component.text(vaultColor.name(), getVaultColorTextColor(vaultColor)))
            .append(Component.text(" vault!", NamedTextColor.GREEN)));

        // Broadcast to team/server?
        Component broadcast = Component.text(player.getName(), getVaultColorTextColor(vaultColor)) // Player name in vault color
            .append(Component.text(" has opened the ", NamedTextColor.GOLD))
            .append(Component.text(vaultColor.name(), getVaultColorTextColor(vaultColor)))
            .append(Component.text(" vault!", NamedTextColor.GOLD));
        // Example: Broadcast to all players on the same team
        UUID teamId = gameManager.getTeamManager().getPlayerTeamId(player);
        if (teamId != null) {
             gameManager.getTeamManager().getTeamMemberUUIDs(teamId).forEach(memberId -> {
                 Player member = Bukkit.getPlayer(memberId);
                 if (member != null && member.isOnline()) {
                     member.sendMessage(broadcast);
                 }
             });
        }

        // Change block visually (ensure on main thread)
        new BukkitRunnable() {
            @Override
            public void run() {
                Block block = vaultLocation.getBlock();
                // Check if block is still the correct vault type before changing
                if(block.getType() == getVaultMaterial(vaultColor)) {
                    block.setType(Material.GLASS); // Change to glass
                    // TODO: Add particle/sound effects?
                }
            }
        }.runTask(plugin);

        plugin.getLogger().info(vaultColor + " vault at " + vaultLocation.toVector() + " opened by " + player.getName());
    }


    // --- State Management ---

    /** Checks if a specific vault is open for a given team. */
    public boolean isVaultOpen(UUID teamId, VaultColor color) {
        Set<VaultColor> openSet = openVaultsByTeam.get(teamId);
        return openSet != null && openSet.contains(color);
    }

    /** Marks a specific vault as open for a given team. */
    private void markVaultOpen(UUID teamId, VaultColor color) {
        // computeIfAbsent ensures the set exists before adding
        openVaultsByTeam.computeIfAbsent(teamId, k -> Collections.synchronizedSet(new HashSet<>())).add(color);
    }


    // --- Key Item Utility Methods ---

    /** Creates the ItemStack for a vault key of the specified color. */
    public ItemStack createKeyItem(VaultColor color) {
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK, 1); // Using Tripwire Hook as base
        ItemMeta meta = key.getItemMeta();
        if (meta != null) {
            // Set Name using Adventure API
            Component displayName = Component.text(color.name() + " Vault Key", getVaultColorTextColor(color), TextDecoration.BOLD)
                                            .decoration(TextDecoration.ITALIC, false); // Ensure not italic by default
            meta.displayName(displayName);

            // Set Lore using Adventure API
            List<Component> loreComponents = new ArrayList<>();
            loreComponents.add(Component.text("Used to unlock the " + color.name().toLowerCase() + " vault", NamedTextColor.GRAY)
                                        .decoration(TextDecoration.ITALIC, false));
            loreComponents.add(Component.empty()); // Blank line
            loreComponents.add(Component.text("Sands of Time Item", NamedTextColor.DARK_GRAY)
                                        .decoration(TextDecoration.ITALIC, false));
            meta.lore(loreComponents);

            // Add Persistent Data Tags for identification
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(vaultKeyTagKey, PersistentDataType.BYTE, (byte) 1); // Mark as a vault key
            pdc.set(vaultColorTagKey, PersistentDataType.STRING, color.name()); // Store the color

            // TODO: Add CustomModelData if you have custom key textures
            // meta.setCustomModelData(getCustomModelDataForKey(color));

            key.setItemMeta(meta);
        }
        return key;
    }

    /** Checks if an ItemStack is a vault key managed by this system. */
    public boolean isVaultKey(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false; // Should not happen if hasItemMeta is true, but check anyway
        // Check for the presence of our specific key tag
        return meta.getPersistentDataContainer().has(vaultKeyTagKey, PersistentDataType.BYTE);
    }

    /** Gets the VaultColor associated with a vault key ItemStack. */
    @Nullable
    public VaultColor getKeyColor(@Nullable ItemStack item) {
        if (!isVaultKey(item)) { // Use the check above first
            return null;
        }
        // isVaultKey already checked for null meta and presence of vaultKeyTagKey
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String colorString = pdc.get(vaultColorTagKey, PersistentDataType.STRING);
        if (colorString != null) {
            try {
                return VaultColor.valueOf(colorString); // Convert string back to enum
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid VaultColor string found in item PDC: " + colorString);
                return null;
            }
        }
        plugin.getLogger().warning("Vault key item missing color tag!");
        return null; // Color tag was missing
    }

    /** Consumes one key of the specified color from the player's inventory. */
    private boolean consumeKeyItem(Player player, VaultColor color) {
        PlayerInventory inventory = player.getInventory();
        ItemStack keyToConsume = createKeyItem(color); // Create a reference item to match against
        keyToConsume.setAmount(1); // We only want to remove one

        // Iterate through inventory to find a matching key
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack currentItem = inventory.getItem(i);
            // Check if item exists and is a matching key (ignoring amount)
            if (currentItem != null && currentItem.isSimilar(keyToConsume)) {
                 // Decrease amount or remove item
                 if (currentItem.getAmount() > 1) {
                     currentItem.setAmount(currentItem.getAmount() - 1);
                 } else {
                     inventory.setItem(i, null); // Remove item stack completely
                 }
                 player.updateInventory(); // Update client inventory view
                 return true; // Key consumed successfully
            }
        }
        return false; // Key not found or couldn't be removed
    }


    // --- Visual Helpers ---

    /** Gets the corresponding material for a vault color marker. */
    private Material getVaultMaterial(VaultColor color) {
         switch (color) {
             case BLUE: return Material.BLUE_CONCRETE;
             case RED: return Material.RED_CONCRETE;
             case GREEN: return Material.LIME_CONCRETE; // Changed to Lime for better visibility? Or keep GREEN_CONCRETE
             case GOLD: return Material.GOLD_BLOCK;
             default: return Material.STONE; // Fallback
         }
     }

    /** Gets the Adventure API TextColor for a vault color. */
    private TextColor getVaultColorTextColor(VaultColor color) {
        switch (color) {
            case BLUE: return NamedTextColor.BLUE;
            case RED: return NamedTextColor.RED;
            case GREEN: return NamedTextColor.GREEN;
            case GOLD: return NamedTextColor.GOLD;
            default: return NamedTextColor.WHITE;
        }
    }

     // TODO: Add method to get CustomModelData for keys if needed
     // private int getCustomModelDataForKey(VaultColor color) { ... }

}
