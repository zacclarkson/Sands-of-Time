package com.clarkson.sot.dungeon; // Or your chosen package

import com.clarkson.sot.main.GameManager; // Assuming GameManager is in .main package
import com.clarkson.sot.main.SoT; // Assuming SoT is in .main package
import com.clarkson.sot.dungeon.segment.PlacedSegment;
import com.clarkson.sot.dungeon.segment.Segment;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler; // Import EventHandler
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList; // Import ArrayList
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID; // Import UUID

/**
 * Manages Vaults, Keys, and their placement/interaction within a Sands of Time game instance.
 */
public class VaultManager implements Listener { // Implement Listener

    private final SoT plugin;
    private final GameManager gameManager; // To interact with other managers if needed (e.g., ScoreManager for rewards)

    // Store locations of the vault interaction blocks
    private final Map<VaultColor, Location> vaultLocations;
    // Track which vaults have been opened in this game instance
    private final Map<VaultColor, Boolean> vaultOpened;
    // Store locations where keys are initially placed (can be complex, might use FloorItemManager instead)
    private final Map<VaultColor, Location> keySpawnLocations;
    // Store the NamespacedKey used to identify keys and their color
    private final NamespacedKey vaultKeyTagKey;
    private final NamespacedKey vaultColorTagKey;

    public VaultManager(SoT plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;

        // Initialize maps
        this.vaultLocations = new HashMap<>();
        this.vaultOpened = new HashMap<>();
        this.keySpawnLocations = new HashMap<>();

        // Initialize NamespacedKeys (use your plugin's name)
        this.vaultKeyTagKey = new NamespacedKey(plugin, "sot_vault_key");
        this.vaultColorTagKey = new NamespacedKey(plugin, "sot_vault_color");

        // Register this class as an event listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Clears previous vault/key data and places new ones based on the generated dungeon layout.
     * NOTE: This implementation is conceptual and relies heavily on metadata within PlacedSegment/Segment
     * that needs to be defined and loaded (e.g., segment.getTemplate().getContainedVaultKeyColor()).
     *
     * @param placedSegments The list of segments placed by the DungeonManager.
     */
    public void placeVaultsAndKeys(List<PlacedSegment> placedSegments) {
        // Clear data from previous game (if any)
        vaultLocations.clear();
        vaultOpened.clear();
        keySpawnLocations.clear();
        for (VaultColor color : VaultColor.values()) {
            vaultOpened.put(color, false); // Initialize all vaults as closed
        }

        plugin.getLogger().info("Placing Vaults and Keys...");

        if (placedSegments == null || placedSegments.isEmpty()) {
            plugin.getLogger().warning("Cannot place vaults/keys: No segments provided.");
            return;
        }

        // --- Placement Logic (Conceptual) ---
        // This needs refinement based on how you store metadata in Segments

        PlacedSegment hubSegment = findHubSegment(placedSegments); // Find the central hub/start room
        if (hubSegment == null) {
             plugin.getLogger().warning("Could not find Hub segment for vault/key placement.");
             // Fallback or error handling needed
             hubSegment = placedSegments.get(0); // Assume first is hub as fallback
        }

        Location hubOrigin = hubSegment.getWorldOrigin();
        org.bukkit.World world = hubOrigin.getWorld();

        // Example: Place Blue Key under timer (assuming timer is at hub origin + offset)
        Location blueKeyLoc = hubOrigin.clone().add(0, -2, 0); // Example offset
        placeKeyItem(VaultColor.BLUE, blueKeyLoc);

        // Example: Find Green Vault segment connected to Hub
        PlacedSegment greenVaultSegment = findConnectedSegmentWithMeta(placedSegments, hubSegment, segment -> segment.getContainedVault(), VaultColor.GREEN);
        if (greenVaultSegment != null) {
            Location greenVaultMarkerLoc = greenVaultSegment.getAbsoluteLocation(null /* Relative Pos from Segment Meta */ ); // Needs relative pos data
            placeVaultMarker(VaultColor.GREEN, greenVaultMarkerLoc);
        } else {
             plugin.getLogger().warning("Could not place Green Vault marker.");
        }

        // Example: Find Red Key Puzzle Room connected to Hub
        PlacedSegment redKeySegment = findConnectedSegmentWithMeta(placedSegments, hubSegment, Segment::isPuzzleRoom, true); // Assuming boolean flag
         if (redKeySegment != null) {
             Location redKeyLoc = redKeySegment.getAbsoluteLocation(null /* Relative Pos from Segment Meta */ ); // Needs relative pos data
             placeKeyItem(VaultColor.RED, redKeyLoc);
         } else {
              plugin.getLogger().warning("Could not place Red Key.");
         }

         // Example: Find Gold Key Lava Parkour
         PlacedSegment goldKeySegment = findSegmentWithMeta(placedSegments, segment -> segment.isLavaParkour(), true); // Assuming boolean flag
         if (goldKeySegment != null) {
              Location goldKeyLoc = goldKeySegment.getAbsoluteLocation(null /* Relative Pos from Segment Meta */ ); // Needs relative pos data
              placeKeyItem(VaultColor.GOLD, goldKeyLoc);
          } else {
               plugin.getLogger().warning("Could not place Gold Key.");
          }


        // TODO: Implement logic for placing other vaults (Red, Gold, Blue?) based on segment metadata
        // Iterate all segments, check getContainedVault() / getContainedVaultKey() metadata, calculate absolute position, call placeVaultMarker/placeKeyItem.

        plugin.getLogger().info("Vaults and Keys placement attempt finished.");
    }

    // --- Helper methods for placement (Conceptual) ---

    private PlacedSegment findHubSegment(List<PlacedSegment> segments) {
        return segments.stream().filter(ps -> Boolean.TRUE.equals(ps.getSegmentTemplate().isHub())).findFirst().orElse(null);
    }

    private PlacedSegment findConnectedSegmentWithMeta(List<PlacedSegment> segments, PlacedSegment targetSegment, java.util.function.Function<Segment, Object> metaGetter, Object expectedValue) {
        // Find segments connected to targetSegment where metaGetter(segment.getTemplate()) == expectedValue
        // Requires complex checking of entry points and connections
        return null; // Placeholder
    }

     private PlacedSegment findSegmentWithMeta(List<PlacedSegment> segments, java.util.function.Function<Segment, Object> metaGetter, Object expectedValue) {
         return segments.stream()
                 .filter(ps -> expectedValue.equals(metaGetter.apply(ps.getSegmentTemplate())))
                 .findFirst().orElse(null);
     }

    private void placeVaultMarker(VaultColor color, Location location) {
        if (location == null || location.getWorld() == null) return;
        vaultLocations.put(color, location);
        // Set the block at the location to represent the vault (e.g., colored concrete)
        Material vaultMaterial = getVaultMaterial(color);
        location.getBlock().setType(vaultMaterial);
        plugin.getLogger().info("Placed " + color + " vault marker at " + location.toVector());
    }

    private void placeKeyItem(VaultColor color, Location location) {
         if (location == null || location.getWorld() == null) return;
         keySpawnLocations.put(color, location); // Store where it should be
         ItemStack keyStack = createKeyItem(color);
         // Spawn the item in the world (e.g., drop naturally or use FloorItemManager)
         location.getWorld().dropItemNaturally(location.add(0.5, 0.5, 0.5), keyStack); // Center it slightly
         plugin.getLogger().info("Placed " + color + " key item near " + location.toVector());
     }


    // --- Event Handling ---

    @EventHandler // Ensure this listener is registered in SoT.java onEnable
    public void onPlayerInteract(PlayerInteractEvent event) {
        // We only care about right-clicking blocks for vault opening
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();
        ItemStack itemInHand = event.getItem(); // Item used to click

        if (clickedBlock == null || itemInHand == null) {
            return;
        }

        // Check if the clicked block is a vault location
        VaultColor clickedVaultColor = null;
        Location clickedLocation = clickedBlock.getLocation();
        for (Map.Entry<VaultColor, Location> entry : vaultLocations.entrySet()) {
            // Compare block locations precisely
            if (entry.getValue().getWorld().equals(clickedLocation.getWorld()) &&
                entry.getValue().getBlockX() == clickedLocation.getBlockX() &&
                entry.getValue().getBlockY() == clickedLocation.getBlockY() &&
                entry.getValue().getBlockZ() == clickedLocation.getBlockZ())
            {
                clickedVaultColor = entry.getKey();
                break;
            }
        }

        // If a vault was clicked
        if (clickedVaultColor != null) {
            // Check if the vault is already open
            if (vaultOpened.getOrDefault(clickedVaultColor, false)) {
                player.sendMessage(ChatColor.YELLOW + "This vault has already been opened!");
                event.setCancelled(true); // Prevent normal block interaction
                return;
            }

            // Check if the player is holding the correct key
            if (isVaultKey(itemInHand)) {
                VaultColor keyColor = getKeyColor(itemInHand);
                if (keyColor == clickedVaultColor) {
                    // Correct key for this vault! Attempt to open.
                    if (consumeKeyItem(player, keyColor)) {
                        openVault(player, clickedVaultColor, clickedLocation);
                        event.setCancelled(true); // Prevent normal block interaction
                    } else {
                        // This shouldn't happen if they are holding the key, but check anyway
                        player.sendMessage(ChatColor.RED + "Error consuming key item!");
                    }
                } else {
                    // Wrong key color
                    player.sendMessage(ChatColor.RED + "This key doesn't fit this vault!");
                    event.setCancelled(true);
                }
            } else {
                // Not holding a key
                player.sendMessage(ChatColor.RED + "You need the " + clickedVaultColor + " key to open this vault!");
                event.setCancelled(true);
            }
        }
        // No need to handle clicking key items here if they are dropped items (pickup handles it)
        // If keys were blocks, you'd add logic here.
    }

    /**
     * Handles the logic after a vault is successfully opened.
     */
    private void openVault(Player player, VaultColor vaultColor, Location vaultLocation) {
        vaultOpened.put(vaultColor, true);
        player.sendMessage(ChatColor.GREEN + "You opened the " + vaultColor + " vault!");
        // Optional: Broadcast to team/server?

        // --- Trigger Vault Rewards ---
        // TODO: Implement reward logic. This could involve:
        // 1. Spawning items/coins near the vaultLocation.
        // 2. Directly giving coins/items via ScoreManager/GameManager.
        // 3. Triggering mob spawns or other events.
        plugin.getLogger().info(vaultColor + " vault opened by " + player.getName());
        // Example: Change vault block appearance
        vaultLocation.getBlock().setType(Material.GLASS); // Indicate opened

        // Example: Delegate reward spawning
        // gameManager.getRewardManager().spawnVaultLoot(vaultColor, vaultLocation);
    }

    /**
     * Consumes one key of the specified color from the player's inventory.
     * @return true if a key was found and consumed, false otherwise.
     */
    private boolean consumeKeyItem(Player player, VaultColor color) {
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (isVaultKey(item) && getKeyColor(item) == color) {
                // Found the key, consume one
                item.setAmount(item.getAmount() - 1);
                // If amount becomes 0, Bukkit handles removing it, or set slot to null
                // inventory.setItem(i, item.getAmount() > 0 ? item : null); // Redundant?
                player.updateInventory();
                return true;
            }
        }
        return false; // Key not found
    }


    // --- Key Item Utility Methods ---

    /**
     * Creates a unique ItemStack representing a vault key.
     * Uses PersistentDataContainer for reliable identification.
     *
     * @param color The VaultColor for the key.
     * @return The created ItemStack key.
     */
    public ItemStack createKeyItem(VaultColor color) {
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK, 1); // Example material
        ItemMeta meta = key.getItemMeta();

        if (meta != null) {
            // Set visual identifiers
            meta.setDisplayName(getVaultColorChatColor(color) + "" + ChatColor.BOLD + color.name() + " Vault Key");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Used to unlock the " + color.name().toLowerCase() + " vault");
            lore.add(ChatColor.DARK_GRAY + "Sands of Time Item");
            meta.setLore(lore);

            // Add persistent data for reliable identification
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(vaultKeyTagKey, PersistentDataType.BYTE, (byte) 1); // Mark as a vault key
            pdc.set(vaultColorTagKey, PersistentDataType.STRING, color.name()); // Store the color

            key.setItemMeta(meta);
        }
        return key;
    }

    /**
     * Checks if an ItemStack is a valid vault key created by this manager.
     *
     * @param item The ItemStack to check.
     * @return true if it's a valid vault key, false otherwise.
     */
    public boolean isVaultKey(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false; // Should not happen if hasItemMeta is true

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(vaultKeyTagKey, PersistentDataType.BYTE);
    }

    /**
     * Gets the VaultColor associated with a key ItemStack.
     * Assumes isVaultKey() has already been checked or returns null.
     *
     * @param item The key ItemStack.
     * @return The VaultColor, or null if it's not a valid key or color tag is missing.
     */
    public VaultColor getKeyColor(ItemStack item) {
        if (!isVaultKey(item)) { // Check again for safety
            return null;
        }
        ItemMeta meta = item.getItemMeta();
         if (meta == null) return null;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String colorString = pdc.get(vaultColorTagKey, PersistentDataType.STRING);

        if (colorString != null) {
            try {
                return VaultColor.valueOf(colorString);
            } catch (IllegalArgumentException e) {
                // Invalid color string stored in tag
                plugin.getLogger().warning("Invalid VaultColor string found in item PDC: " + colorString);
                return null;
            }
        }
        return null; // Color tag missing
    }

    // Helper to get ChatColor from VaultColor
    private ChatColor getVaultColorChatColor(VaultColor color) {
        switch (color) {
            case BLUE: return ChatColor.BLUE;
            case RED: return ChatColor.RED;
            case GREEN: return ChatColor.GREEN;
            case GOLD: return ChatColor.GOLD;
            default: return ChatColor.WHITE;
        }
    }

     // Helper to get Material from VaultColor
     private Material getVaultMaterial(VaultColor color) {
         switch (color) {
             case BLUE: return Material.BLUE_CONCRETE; // Example
             case RED: return Material.RED_CONCRETE;
             case GREEN: return Material.GREEN_CONCRETE;
             case GOLD: return Material.GOLD_BLOCK;
             default: return Material.STONE;
         }
     }
}