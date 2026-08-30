package com.clarkson.sot.events; // Or a more suitable package like com.clarkson.sot.items

import com.clarkson.sot.dungeon.VaultColor;
import com.clarkson.sot.entities.CoinStack;
import com.clarkson.sot.entities.FloorItem;
import com.clarkson.sot.entities.FloorLoot;
import com.clarkson.sot.entities.Key;
import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.main.SoT;
import com.clarkson.sot.scoring.ScoreManager; // Needed for awarding score/coins

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack; // If spawning generic loot needs ItemStacks
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of FloorItem instances within dungeon instances.
 * Handles spawning, tracking, pickup detection, and cleanup.
 */
public class FloorItemManager implements Listener {

    private final SoT plugin;
    private final GameManager gameManager;
    private final ScoreManager scoreManager;

    // Tracks all active floor items by their unique ID for quick lookup
    private final Map<UUID, FloorItem> activeFloorItems;
    // Tracks which items belong to which team instance for efficient cleanup and lookup
    private final Map<UUID, Set<UUID>> itemsByTeamInstance; // TeamID -> Set<FloorItem UUID>

    // Configuration for pickup radius (squared for efficiency)
    private static final double PICKUP_RADIUS_SQUARED = 1.5 * 1.5; // Example: 1.5 blocks
    // FloorItem.getLocation() is the block corner, but every implementation spawns its ItemDisplay
    // at the block centre (see CoinStack/FloorLoot/Key.spawnRepresentation). Measure to the visual
    // so the pickup sphere is centred on what the player can actually see; keep these in step.
    private static final double DISPLAY_OFFSET_X = 0.5;
    private static final double DISPLAY_OFFSET_Y = 0.1;
    private static final double DISPLAY_OFFSET_Z = 0.5;

    public FloorItemManager(@NotNull SoT plugin, @NotNull GameManager gameManager, @NotNull ScoreManager scoreManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.scoreManager = scoreManager;
        this.activeFloorItems = new ConcurrentHashMap<>();
        this.itemsByTeamInstance = new ConcurrentHashMap<>();
        // Registered as a listener by SoT.onEnable, which is the single registration point for the
        // GameManager-owned manager instances. Registering here too would double-fire every event.
        plugin.getLogger().info("FloorItemManager initialized.");
    }

    // --- Spawning Methods (Called by DungeonManager during instantiation) ---

    /**
     * Spawns a CoinStack at the specified location.
     * @param location Absolute world location.
     * @param baseValue Base coin value.
     * @param teamId Owning team.
     * @param segmentInstanceId Owning segment instance.
     * @param depth Dungeon depth.
     */
    public void spawnCoinStack(@NotNull Location location, int baseValue, @NotNull UUID teamId, @NotNull UUID segmentInstanceId, int depth) {
        // TODO: Add spawn rate logic here? Or assume coins always spawn if location provided?
        // Example: if (random.nextDouble() < COIN_SPAWN_CHANCE) { ... }

        CoinStack coinStack = new CoinStack(plugin, location, baseValue, teamId, segmentInstanceId, depth);
        trackItem(coinStack);
        plugin.getLogger().finer("Spawned CoinStack " + coinStack.getUniqueId() + " for team " + teamId);
    }

    /**
     * Spawns a coloured vault key as a tracked floor item.
     * <p>Unlike the dropped Item entity this replaced, the key stays on the block it was placed on,
     * never despawns, and can only be collected by the team that owns this dungeon instance.
     *
     * @param location Absolute world location.
     * @param color The vault colour this key opens.
     * @param teamId Owning team.
     * @param segmentInstanceId Owning segment instance.
     * @param depth Dungeon depth.
     * @return the tracked Key.
     */
    @NotNull
    public Key spawnKey(@NotNull Location location, @NotNull VaultColor color, @NotNull UUID teamId,
                        @NotNull UUID segmentInstanceId, int depth) {
        Key key = new Key(plugin, location, color, teamId, segmentInstanceId, depth);
        trackItem(key);
        plugin.getLogger().finer("Spawned " + color + " Key " + key.getUniqueId() + " for team " + teamId);
        return key;
    }

    /**
     * Spawns a rusty (segment door) key as a tracked floor item.
     * @return the tracked Key.
     */
    @NotNull
    public Key spawnRustyKey(@NotNull Location location, @NotNull UUID teamId,
                             @NotNull UUID segmentInstanceId, int depth) {
        Key key = Key.createRustyKey(plugin, location, teamId, segmentInstanceId, depth);
        trackItem(key);
        plugin.getLogger().finer("Spawned rusty Key " + key.getUniqueId() + " for team " + teamId);
        return key;
    }

    private static final double ITEM_SPAWN_CHANCE = 0.30; // 30% chance per location
    private static final Material[] LOOT_TABLE = {
        Material.TORCH, Material.TORCH, Material.TORCH,           // Common
        Material.ARROW, Material.ARROW,                            // Common
        Material.BREAD, Material.BREAD,                            // Common
        Material.IRON_SWORD,                                       // Uncommon
        Material.LEATHER_CHESTPLATE,                               // Uncommon
        Material.SHIELD,                                           // Rare
    };

    /**
     * Spawns a generic loot item (e.g., torch, armor, sword) at the location.
     * Incorporates spawn rates and loot tables.
     *
     * @param location Absolute world location.
     * @param teamId Owning team.
     * @param segmentInstanceId Owning segment instance.
     * @param depth Dungeon depth.
     * @param rng The caller's seeded RNG. Supplied rather than held as a field because one
     *            FloorItemManager serves every team: a shared instance RNG would interleave their
     *            draws and no seed could reproduce the result.
     */
    public void spawnGenericItem(@NotNull Location location, @NotNull UUID teamId,
                                 @NotNull UUID segmentInstanceId, int depth, @NotNull Random rng) {
        // 30% chance to spawn anything at this location
        if (rng.nextDouble() > ITEM_SPAWN_CHANCE) return;

        // Pick a random item from the loot table
        Material lootMaterial = LOOT_TABLE[rng.nextInt(LOOT_TABLE.length)];
        int amount = (lootMaterial == Material.TORCH || lootMaterial == Material.ARROW) ? 2 + rng.nextInt(3) : 1;
        ItemStack itemToSpawn = new ItemStack(lootMaterial, amount);

        FloorLoot floorLoot = new FloorLoot(plugin, location, itemToSpawn, teamId, segmentInstanceId, depth);
        trackItem(floorLoot);
        plugin.getLogger().finer("Spawned FloorLoot " + floorLoot.getUniqueId() + " (" + lootMaterial + ") for team " + teamId);
    }

     /**
      * Spawns a Sand Pile item at the location (if sand is handled as a FloorItem).
      * Incorporates spawn rates.
      * @param location Absolute world location.
      * @param amount Amount of sand in the pile.
      * @param teamId Owning team.
      * @param segmentInstanceId Owning segment instance.
      * @param depth Dungeon depth.
      */
     /**
      * Sand spawning is handled by DungeonManager as actual SAND blocks placed in the world.
      * Players break them with a shovel to collect sand (handled by SandManager).
      * This method is kept for API compatibility but does nothing.
      */
     public void spawnSandPile(@NotNull Location location, int amount, @NotNull UUID teamId, @NotNull UUID segmentInstanceId, int depth) {
         // Sand is placed as real SAND blocks by DungeonManager.populateFloorItems()
         // Collection is handled by SandManager.onBlockBreak()
     }


    // --- Tracking Methods ---

    private void trackItem(FloorItem item) {
        activeFloorItems.put(item.getUniqueId(), item);
        itemsByTeamInstance.computeIfAbsent(item.getTeamId(), k -> ConcurrentHashMap.newKeySet()).add(item.getUniqueId());
    }

    private void untrackItem(FloorItem item) {
        activeFloorItems.remove(item.getUniqueId());
        Set<UUID> teamItems = itemsByTeamInstance.get(item.getTeamId());
        if (teamItems != null) {
            teamItems.remove(item.getUniqueId());
            // Optional: remove team entry if set becomes empty?
            // if (teamItems.isEmpty()) {
            //     itemsByTeamInstance.remove(item.getTeamId());
            // }
        }
    }

    /**
     * @return A snapshot of the floor items currently tracked for a team. Empty if the team has none.
     */
    @NotNull
    public List<FloorItem> getTrackedItems(@NotNull UUID teamId) {
        Set<UUID> ids = itemsByTeamInstance.get(teamId);
        if (ids == null) return List.of();
        return ids.stream().map(activeFloorItems::get).filter(Objects::nonNull).toList();
    }

    // --- Pickup Detection (Proximity) ---

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Check if player actually moved between blocks for efficiency
        if (!event.hasChangedBlock()) {
            return;
        }
        // Check game state
        if (gameManager.getCurrentState() != GameState.RUNNING) {
            return;
        }

        Player player = event.getPlayer();
        UUID teamId = gameManager.getTeamManager().getPlayerTeamId(player);
        if (teamId == null) {
            return; // Player not on a team or team info unavailable
        }

        Location playerLoc = event.getTo(); // Use the destination location
        if (playerLoc == null || playerLoc.getWorld() == null) return; // Should not happen

        // Get items relevant to this player's team for potential optimization
        Set<UUID> teamItemUUIDs = itemsByTeamInstance.get(teamId);
        if (teamItemUUIDs == null || teamItemUUIDs.isEmpty()) {
            return; // No items tracked for this team
        }

        // Check proximity against items for this team
        // Convert to ArrayList for safe iteration if modification occurs (though handled by isPickedUp flag)
        for (UUID itemId : new ArrayList<>(teamItemUUIDs)) {
            FloorItem item = activeFloorItems.get(itemId);
            if (item != null && !item.isPickedUp()) {
                // Check world first for efficiency
                // Measure to the item's visual, not to the block corner it is anchored at.
                Location itemLoc = item.getLocation()
                        .add(DISPLAY_OFFSET_X, DISPLAY_OFFSET_Y, DISPLAY_OFFSET_Z);
                 if (itemLoc.getWorld().equals(playerLoc.getWorld())) {
                     // Use distance squared for performance
                     if (playerLoc.distanceSquared(itemLoc) <= PICKUP_RADIUS_SQUARED) {
                         handleItemPickup(player, item);
                         // Optional: break here if player can only pick up one item per move event?
                     }
                 }
            }
        }
    }

    // --- Pickup Handling ---

    /**
     * Handles the logic when a player is determined to have picked up a FloorItem.
     * @param player The player picking up the item.
     * @param item The FloorItem being picked up.
     */
    private void handleItemPickup(@NotNull Player player, @NotNull FloorItem item) {
        // Double check state in case of concurrent events (though PlayerMoveEvent is sync)
        if (item.isPickedUp()) {
            return;
        }

        // 1. Update item state and remove visual
        item.handlePickup(player);

        // 2. Notify ScoreManager (ScoreManager handles the logic of what the item is worth)
        scoreManager.collectFloorItem(player, item); // TODO: Implement this method in ScoreManager

        // 3. Untrack the item
        untrackItem(item);

        // 4. Play sound effect for the player
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.5f);

        // 5. Optional: Send message? (ScoreManager might do this)
        // player.sendActionBar(Component.text("Picked up item!", NamedTextColor.GREEN));
    }


    // --- Cleanup ---

    /**
     * Removes all visual representations and tracking for floor items associated with a team.
     * Called by GameManager or DungeonManager when an instance ends.
     * @param teamId The UUID of the team instance to clear.
     */
    public void clearTeamState(UUID teamId) {
        Set<UUID> teamItemUUIDs = itemsByTeamInstance.remove(teamId); // Remove the entry for the team
        if (teamItemUUIDs != null) {
            plugin.getLogger().info("Clearing " + teamItemUUIDs.size() + " floor items for team: " + teamId);
            for (UUID itemId : teamItemUUIDs) {
                FloorItem item = activeFloorItems.remove(itemId); // Remove from global map
                if (item != null) {
                    item.removeRepresentation(); // Remove visual entity
                }
            }
        } else {
             plugin.getLogger().info("No floor items to clear for team: " + teamId);
        }
    }

    public void clearAllTeamStates() {
        plugin.getLogger().info("Clearing all floor items for " + itemsByTeamInstance.size() + " teams.");
        for (UUID teamId : new ArrayList<>(itemsByTeamInstance.keySet())) {
            clearTeamState(teamId);
        }
    }

}
