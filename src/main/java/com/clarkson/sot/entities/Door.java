package com.clarkson.sot.entities;
import com.clarkson.sot.dungeon.segment.Direction;
import com.clarkson.sot.main.SoT;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.UUID;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class Door {

    private final SoT plugin;
    private final Location lockLocation; // Location of the lock block
    private Area bounds;
    private final UUID doorId; // Unique identifier for this door
    private final Direction axis;
    private boolean isOpen;
    private Location floor;

    public Door(SoT plugin, Location minPoint, Location maxPoint, Location lockLocation, Direction axis) {
        this.plugin = plugin;
        this.bounds = new Area(minPoint, maxPoint);
        this.lockLocation = lockLocation;
        this.doorId = UUID.randomUUID(); // Generate a unique ID for this door
        this.axis = axis;
        this.isOpen = false;
        this.floor = bounds.getMinPoint().clone().subtract(0, 1, 0);
        if (axis == Direction.SOUTH)
            axis = Direction.NORTH;
        if (axis == Direction.EAST)
            axis = Direction.WEST;
        // Tag the lock block with the door's UUID
        tagLockBlock();
    }

    private void tagLockBlock() {
        Block lockBlock = lockLocation.getBlock();
        PersistentDataContainer dataContainer = lockBlock.getChunk().getPersistentDataContainer();
        dataContainer.set(new NamespacedKey(plugin, "door_id"), PersistentDataType.STRING, doorId.toString());
    }

    public UUID getDoorId() {
        return doorId;
    }

    public boolean isBlockPartOfDoor(Block block) {
        Location blockLocation = block.getLocation();
        return blockLocation.getX() >= bounds.getMinPoint().getX() &&
                blockLocation.getX() <= bounds.getMaxPoint().getX() &&
                blockLocation.getY() >= bounds.getMinPoint().getY() &&
                blockLocation.getY() <= bounds.getMaxPoint().getY() &&
                blockLocation.getZ() >= bounds.getMinPoint().getZ() &&
                blockLocation.getZ() <= bounds.getMaxPoint().getZ();
    }

    public boolean isLockBlock(Block block) {
        return block.getLocation().equals(lockLocation);
    }

    public void onPlayerInteract(Player player) {
        if (isOpen) return;

        // Check if the player is holding the correct key (e.g., a slimeball)
        if (player.getInventory().getItemInMainHand().getType() == Material.SLIME_BALL) {
            // Open the door
            lowerDoor(lockLocation.getBlock());
            isOpen = true;

            // Remove one slimeball from the player's hand
            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            if (itemInHand.getAmount() > 1) {
                itemInHand.setAmount(itemInHand.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        }
    }



    private void lowerDoor(Block keyBlock) {
        // Convert the Bukkit block to a WorldEdit location
        com.sk89q.worldedit.util.Location weLocMin = BukkitAdapter.adapt(bounds.getMinPoint());
        com.sk89q.worldedit.util.Location weLocMax = BukkitAdapter.adapt(bounds.getMaxPoint());
        BukkitWorld world = new BukkitWorld(keyBlock.getWorld());

        CuboidRegion region = new CuboidRegion(world,
                BlockVector3.at(weLocMin.getX(), weLocMin.getY(), weLocMin.getZ()),
                BlockVector3.at(weLocMax.getX(), weLocMax.getY(), weLocMax.getZ()));

        try (EditSession editSession = WorldEdit.getInstance().newEditSession(world)) {
            BlockVector3 offset = BlockVector3.at(0, -1, 0);
            if (BlockTypes.AIR != null) {
                editSession.moveRegion(region, offset, 1, false, false, null, BlockTypes.AIR.getDefaultState());
            } else {
                System.err.println("BlockTypes.AIR is null, cannot move region.");
            }
            region.shift(offset);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Use a delay to make the movement of the door smooth
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            // Since the door has moved down, adjust the bounds
            this.bounds = new Area(bounds.getMinPoint().subtract(0, 1, 0), bounds.getMaxPoint().subtract(0, 1, 0));

            // Check if the door's bottom has reached the floor (or any other desired condition)
            if (bounds.getMaxPoint().getY() > floor.blockY()) {  // Assuming floorY is the Y-coordinate of the floor
                lowerDoor(keyBlock.getRelative(0, -1, 0));
            }
        }, 4L); // 10 ticks or 0.5 seconds delay for example
    }

    public Direction getAxis() {
        return axis;
    }
}
