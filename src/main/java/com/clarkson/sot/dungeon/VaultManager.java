package com.clarkson.sot.dungeon; // Or your chosen package

import com.clarkson.sot.main.GameManager; // Assuming GameManager is in .main package
import com.clarkson.sot.main.GameState; // Import GameState enum
import com.clarkson.sot.main.SoT; // Assuming SoT is in .main package
// Corrected import path assumption
import com.clarkson.sot.dungeon.segment.PlacedSegment;
import com.clarkson.sot.dungeon.segment.Segment;

// Import Bukkit API classes
import org.bukkit.Bukkit; // For scheduler
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World; // Import World
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority; // For listener priority if needed
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import com.sk89q.worldedit.math.BlockVector3; // Needed for relative positions

// Import Adventure API classes
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects; // Import Objects for requireNonNullElse
import java.util.Optional; // Import Optional
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap; // Use ConcurrentHashMap for thread safety
import java.util.logging.Level;

/**
 * Manages Vaults, Keys, and their placement/interaction within a Sands of Time game instance.
 * Uses Adventure API for messages and item formatting. Includes edge case handling.
 */
public class VaultManager implements Listener {

    private final SoT plugin;
    private final GameManager gameManager;

    // Use ConcurrentHashMap for thread safety on vault state
    private final Map<VaultColor, Location> vaultLocations;
    private final Map<VaultColor, Boolean> vaultOpened;
    private final Map<VaultColor, Location> keySpawnLocations;

    private final NamespacedKey vaultKeyTagKey;
    private final NamespacedKey vaultColorTagKey;

    // Define constant offsets for the Blue Key relative to the Hub origin/timer center
    // TODO: Make these configurable?
    private static final BlockVector3 BLUE_KEY_OFFSET_FROM_HUB = BlockVector3.at(0, -2, 0); // Example: 2 blocks directly below


    public VaultManager(SoT plugin, GameManager gameManager) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.gameManager = Objects.requireNonNull(gameManager, "GameManager cannot be null");

        // Initialize maps using ConcurrentHashMap for better thread safety
        this.vaultLocations = new ConcurrentHashMap<>();
        this.vaultOpened = new ConcurrentHashMap<>();
        this.keySpawnLocations = new ConcurrentHashMap<>();

        // Initialize NamespacedKeys
        this.vaultKeyTagKey = new NamespacedKey(plugin, "sot_vault_key");
        this.vaultColorTagKey = new NamespacedKey(plugin, "sot_vault_color");

        // Register listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Clears previous vault/key data and places new ones based on the generated dungeon layout.
     * Returns true if placement was successful (or at least didn't hit critical errors), false otherwise.
     * @param placedSegments The list of segments placed by the DungeonManager.
     * @return boolean indicating if placement was generally successful.
     */
    public boolean placeVaultsAndKeys(List<PlacedSegment> placedSegments) {
        // Clear data from previous game
        vaultLocations.clear();
        vaultOpened.clear();
        keySpawnLocations.clear();
        for (VaultColor color : VaultColor.values()) {
            vaultOpened.put(color, false);
        }

        plugin.getLogger().info("Placing Vaults and Keys...");

        if (placedSegments == null || placedSegments.isEmpty()) {
            plugin.getLogger().severe("Cannot place vaults/keys: No segments provided.");
            return false; // Indicate failure
        }

        // --- Placement Logic ---
        // Find Hub first, as other placements depend on it
        PlacedSegment hubSegment = findHubSegment(placedSegments);
        if (hubSegment == null) {
             plugin.getLogger().severe("Critical Error: Could not find Hub segment. Aborting vault/key placement.");
             return false; // Indicate failure
        }
        Location hubOrigin = hubSegment.getWorldOrigin(); // Use the origin of the hub segment as reference
        World world = hubOrigin.getWorld();
        if (world == null) {
             plugin.getLogger().severe("Critical Error: Hub segment's world is null. Aborting vault/key placement.");
             return false;
        }


        boolean placementSuccessful = true; // Track overall success

        // --- Specific Placements (Examples - Require Metadata Implementation in Segment) ---

        // Blue Key: Special case - placed relative to Hub origin, not segment metadata offset
        Location blueKeyLoc = hubOrigin.clone().add(
            BLUE_KEY_OFFSET_FROM_HUB.x(),
            BLUE_KEY_OFFSET_FROM_HUB.y(),
            BLUE_KEY_OFFSET_FROM_HUB.z()
        );
        placementSuccessful &= placeKeyItem(VaultColor.BLUE, blueKeyLoc);


        // Green Vault connected to Hub
        PlacedSegment greenVaultSegment = findConnectedSegmentWithMeta(placedSegments, hubSegment, segment -> segment.getContainedVault(), VaultColor.GREEN);
        if (greenVaultSegment != null) {
            // Use the generic getVaultOffset() method (needs to be added to Segment)
            BlockVector3 greenVaultOffset = greenVaultSegment.getSegmentTemplate().getVaultOffset(); // Assumed method in Segment
            if (greenVaultOffset != null) {
                Location greenVaultMarkerLoc = greenVaultSegment.getAbsoluteLocation(greenVaultOffset);
                placementSuccessful &= placeVaultMarker(VaultColor.GREEN, greenVaultMarkerLoc);
            } else {
                 plugin.getLogger().warning("Could not place Green Vault: Offset metadata missing in segment " + greenVaultSegment.getName());
                 placementSuccessful = false;
            }
        } else {
             plugin.getLogger().warning("Could not find segment for Green Vault connected to Hub.");
        }

        // Red Key in Puzzle Room connected to Hub
        PlacedSegment redKeySegment = findConnectedSegmentWithMeta(placedSegments, hubSegment, Segment::isPuzzleRoom, true);
         if (redKeySegment != null) {
             // Use the generic getKeyOffset() method (needs to be added to Segment)
             BlockVector3 redKeyOffset = redKeySegment.getSegmentTemplate().getKeyOffset(); // Assumed method in Segment
             if (redKeyOffset != null) {
                 Location redKeyLoc = redKeySegment.getAbsoluteLocation(redKeyOffset);
                 placementSuccessful &= placeKeyItem(VaultColor.RED, redKeyLoc);
             } else {
                  plugin.getLogger().warning("Could not place Red Key: Offset metadata missing in puzzle segment " + redKeySegment.getName());
                  placementSuccessful = false;
             }
         } else {
              plugin.getLogger().warning("Could not find Puzzle Room segment for Red Key connected to Hub.");
         }

         // Gold Key in Lava Parkour segment
         PlacedSegment goldKeySegment = findSegmentWithMeta(placedSegments, Segment::isLavaParkour, true);
         if (goldKeySegment != null) {
              // Use the generic getKeyOffset() method (needs to be added to Segment)
              BlockVector3 goldKeyOffset = goldKeySegment.getSegmentTemplate().getKeyOffset(); // Assumed method in Segment
              if (goldKeyOffset != null) {
                  Location goldKeyLoc = goldKeySegment.getAbsoluteLocation(goldKeyOffset);
                  placementSuccessful &= placeKeyItem(VaultColor.GOLD, goldKeyLoc);
              } else {
                   plugin.getLogger().warning("Could not place Gold Key: Offset metadata missing in lava segment " + goldKeySegment.getName());
                   placementSuccessful = false;
              }
          } else {
               plugin.getLogger().warning("Could not find Lava Parkour segment for Gold Key.");
          }

        // --- Generic Placement Loop (For remaining vaults/keys defined in segment metadata) ---
        for (PlacedSegment ps : placedSegments) {
            Segment template = ps.getSegmentTemplate();

            // Place Vault Marker if defined (using generic offset)
            VaultColor vaultColor = template.getContainedVault();
            if (vaultColor != null && !vaultLocations.containsKey(vaultColor)) {
                BlockVector3 vaultOffset = template.getVaultOffset(); // Assumed method in Segment
                if (vaultOffset != null) {
                    Location vaultLoc = ps.getAbsoluteLocation(vaultOffset);
                    placementSuccessful &= placeVaultMarker(vaultColor, vaultLoc);
                } else {
                    plugin.getLogger().warning("Segment " + ps.getName() + " defines vault " + vaultColor + " but lacks offset metadata.");
                    placementSuccessful = false;
                }
            }

            // Place Key Item if defined (using generic offset)
            VaultColor keyColor = template.getContainedVaultKey();
             if (keyColor != null && !keySpawnLocations.containsKey(keyColor)) {
                 BlockVector3 keyOffset = template.getKeyOffset(); // Assumed method in Segment
                 if (keyOffset != null) {
                     Location keyLoc = ps.getAbsoluteLocation(keyOffset);
                     placementSuccessful &= placeKeyItem(keyColor, keyLoc);
                 } else {
                     plugin.getLogger().warning("Segment " + ps.getName() + " defines key " + keyColor + " but lacks offset metadata.");
                     placementSuccessful = false;
                 }
             }
        }


        plugin.getLogger().info("Vaults and Keys placement finished. Success: " + placementSuccessful);
        return placementSuccessful;
    }

    // --- Helper methods for placement (Conceptual - Require Segment Metadata Implementation) ---
    // TODO: Implement these helper methods robustly based on Segment metadata structure
    // Assumes Segment class now has isHub(), getVaultOffset(), getKeyOffset() methods
    private PlacedSegment findHubSegment(List<PlacedSegment> segments) {
         return segments.stream().filter(ps -> ps.getSegmentTemplate().isHub()).findFirst().orElse(null);
    }
    private PlacedSegment findConnectedSegmentWithMeta(List<PlacedSegment> segments, PlacedSegment targetSegment, java.util.function.Function<Segment, Object> metaGetter, Object expectedValue) {
        // Placeholder - Requires complex connection checking + metadata access
        return null;
    }
    private PlacedSegment findSegmentWithMeta(List<PlacedSegment> segments, java.util.function.Function<Segment, Object> metaGetter, Object expectedValue) {
         // Corrected lambda usage assuming boolean methods like isLavaParkour exist
         if(expectedValue instanceof Boolean) {
              java.util.function.Predicate<Segment> boolGetter = s -> expectedValue.equals(metaGetter.apply(s));
              return segments.stream()
                      .filter(ps -> boolGetter.test(ps.getSegmentTemplate()))
                      .findFirst().orElse(null);
         } else {
              return segments.stream()
                      .filter(ps -> expectedValue.equals(metaGetter.apply(ps.getSegmentTemplate())))
                      .findFirst().orElse(null);
         }
    }

    /** Places the vault marker block, returns true on success */
    private boolean placeVaultMarker(VaultColor color, Location location) {
        if (location == null || location.getWorld() == null || !location.isWorldLoaded()) {
             plugin.getLogger().warning("Invalid or unloaded location for " + color + " vault marker.");
             return false;
        }
        // Ensure we're modifying blocks on the main thread
        if (!Bukkit.isPrimaryThread()) {
             Bukkit.getScheduler().runTask(plugin, () -> placeVaultMarker(color, location));
             return true; // Assume success for async call, actual result unknown here
        }
        try {
            vaultLocations.put(color, location);
            Material vaultMaterial = getVaultMaterial(color);
            location.getBlock().setType(vaultMaterial, false); // false = don't apply physics if possible
            plugin.getLogger().info("Placed " + color + " vault marker at " + location.toVector());
            return true;
        } catch (Exception e) {
             plugin.getLogger().log(Level.SEVERE, "Failed to set vault marker block for " + color + " at " + location.toVector(), e);
             vaultLocations.remove(color); // Remove if placement failed
             return false;
        }
    }

    /** Places the key item, returns true on success */
    private boolean placeKeyItem(VaultColor color, Location location) {
         if (location == null || location.getWorld() == null || !location.isWorldLoaded()) {
             plugin.getLogger().warning("Invalid or unloaded location for " + color + " key item.");
             return false;
         }
         // Ensure we're modifying world on the main thread
         if (!Bukkit.isPrimaryThread()) {
              Bukkit.getScheduler().runTask(plugin, () -> placeKeyItem(color, location));
              return true; // Assume success for async call
         }
         try {
             keySpawnLocations.put(color, location);
             ItemStack keyStack = createKeyItem(color);
             // Center the drop location slightly for better appearance/pickup
             Location dropLocation = location.clone().add(0.5, 0.5, 0.5);
             location.getWorld().dropItemNaturally(dropLocation, keyStack);
             plugin.getLogger().info("Placed " + color + " key item near " + location.toVector());
             return true;
         } catch (Exception e) {
              plugin.getLogger().log(Level.SEVERE, "Failed to drop key item for " + color + " near " + location.toVector(), e);
              keySpawnLocations.remove(color); // Remove if placement failed
              return false;
         }
     }

    // --- Event Handling --- (Unchanged from previous version with Adventure API)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (gameManager.getCurrentState() != GameState.RUNNING) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (clickedBlock == null) return;

        VaultColor clickedVaultColor = getVaultColorAtLocation(clickedBlock.getLocation());
        if (clickedVaultColor != null) {
            event.setCancelled(true);
            if (vaultOpened.computeIfAbsent(clickedVaultColor, k -> false)) {
                player.sendMessage(Component.text("This vault has already been opened!", NamedTextColor.YELLOW));
                return;
            }
            if (itemInHand != null && isVaultKey(itemInHand)) {
                VaultColor keyColor = getKeyColor(itemInHand);
                if (keyColor == clickedVaultColor) {
                    synchronized (vaultOpened) {
                         if (vaultOpened.get(clickedVaultColor)) {
                              player.sendMessage(Component.text("This vault was just opened!", NamedTextColor.YELLOW));
                              return;
                         }
                        if (consumeKeyItem(player, keyColor)) {
                            vaultOpened.put(clickedVaultColor, true);
                            openVault(player, clickedVaultColor, clickedBlock.getLocation());
                        } else {
                            player.sendMessage(Component.text("Could not find the key in your inventory to consume!", NamedTextColor.RED));
                        }
                    }
                } else {
                    player.sendMessage(Component.text("This key doesn't fit this vault!", NamedTextColor.RED));
                }
            } else {
                player.sendMessage(Component.text("You need the ", NamedTextColor.RED)
                    .append(Component.text(clickedVaultColor.name(), getVaultColorTextColor(clickedVaultColor)))
                    .append(Component.text(" key to open this vault!", NamedTextColor.RED)));
            }
        }
    }

    /** Checks if the given location matches a registered vault location. */
    private VaultColor getVaultColorAtLocation(Location location) { /* ... unchanged ... */
         World world = location.getWorld(); int x = location.getBlockX(); int y = location.getBlockY(); int z = location.getBlockZ(); if (world == null) return null;
         for (Map.Entry<VaultColor, Location> entry : vaultLocations.entrySet()) { Location vaultLoc = entry.getValue(); if (vaultLoc.getWorld().equals(world) && vaultLoc.getBlockX() == x && vaultLoc.getBlockY() == y && vaultLoc.getBlockZ() == z) { return entry.getKey(); } } return null;
    }

    /** Handles the logic after a vault is successfully opened (called within synchronized block). */
    private void openVault(Player player, VaultColor vaultColor, Location vaultLocation) { /* ... unchanged ... */
        player.sendMessage(Component.text("You opened the ", NamedTextColor.GREEN).append(Component.text(vaultColor.name(), getVaultColorTextColor(vaultColor))).append(Component.text(" vault!", NamedTextColor.GREEN)));
        plugin.getLogger().info(vaultColor + " vault opened by " + player.getName());
        Bukkit.getScheduler().runTask(plugin, () -> { Block block = vaultLocation.getBlock(); if(block.getType() == getVaultMaterial(vaultColor)) { block.setType(Material.GLASS); } });
        // TODO: Implement reward logic
    }

    /** Consumes one key of the specified color from the player's inventory. */
    private boolean consumeKeyItem(Player player, VaultColor color) { /* ... unchanged ... */
        PlayerInventory inventory = player.getInventory(); ItemStack keyToFind = createKeyItem(color); keyToFind.setAmount(1); HashMap<Integer, ItemStack> result = inventory.removeItemAnySlot(keyToFind);
        if (result.isEmpty()) { player.updateInventory(); return true; } else { plugin.getLogger().warning("Failed to remove key item from " + player.getName() + " even after checks passed."); return false; }
    }


    // --- Key Item Utility Methods --- (Unchanged)
    public ItemStack createKeyItem(VaultColor color) { /* ... unchanged ... */
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK, 1); ItemMeta meta = key.getItemMeta(); if (meta != null) { Component displayName = Component.text(color.name() + " Vault Key", getVaultColorTextColor(color), TextDecoration.BOLD); meta.displayName(displayName); List<Component> loreComponents = new ArrayList<>(); loreComponents.add(Component.text("Used to unlock the " + color.name().toLowerCase() + " vault", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)); loreComponents.add(Component.text("Sands of Time Item", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)); meta.lore(loreComponents); PersistentDataContainer pdc = meta.getPersistentDataContainer(); pdc.set(vaultKeyTagKey, PersistentDataType.BYTE, (byte) 1); pdc.set(vaultColorTagKey, PersistentDataType.STRING, color.name()); key.setItemMeta(meta); } return key;
    }
    public boolean isVaultKey(ItemStack item) { /* ... unchanged ... */
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false; ItemMeta meta = item.getItemMeta(); if (meta == null) return false; return meta.getPersistentDataContainer().has(vaultKeyTagKey, PersistentDataType.BYTE);
    }
    public VaultColor getKeyColor(ItemStack item) { /* ... unchanged ... */
        if (item == null || !item.hasItemMeta()) return null; ItemMeta meta = item.getItemMeta(); if (meta == null) return null; PersistentDataContainer pdc = meta.getPersistentDataContainer(); if (!pdc.has(vaultKeyTagKey, PersistentDataType.BYTE)) return null; String colorString = pdc.get(vaultColorTagKey, PersistentDataType.STRING); if (colorString != null) { try { return VaultColor.valueOf(colorString); } catch (IllegalArgumentException e) { plugin.getLogger().warning("Invalid VaultColor string found in item PDC: " + colorString); return null; } } return null;
    }
    private TextColor getVaultColorTextColor(VaultColor color) { /* ... unchanged ... */
        switch (color) { case BLUE: return NamedTextColor.BLUE; case RED: return NamedTextColor.RED; case GREEN: return NamedTextColor.GREEN; case GOLD: return NamedTextColor.GOLD; default: return NamedTextColor.WHITE; }
    }
    private Material getVaultMaterial(VaultColor color) { /* ... unchanged ... */
         switch (color) { case BLUE: return Material.BLUE_CONCRETE; case RED: return Material.RED_CONCRETE; case GREEN: return Material.GREEN_CONCRETE; case GOLD: return Material.GOLD_BLOCK; default: return Material.STONE; }
     }

     // --- Added methods assumed to exist on Segment ---
     // These need to be added to your Segment class definition
     /*
     public interface Segment { // Example additions needed in Segment.java
         BlockVector3 getBlueKeyOffset(); // Or remove if Blue Key handled differently
         BlockVector3 getVaultOffset();
         BlockVector3 getKeyOffset();
         // ... other existing methods ...
     }
     */
}

// VaultColor enum definition needed elsewhere
// public enum VaultColor { BLUE, RED, GREEN, GOLD }
