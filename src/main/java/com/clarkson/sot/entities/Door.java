package com.clarkson.sot.entities;

import com.clarkson.sot.dungeon.DoorClone;
import com.clarkson.sot.main.SoT;
import com.clarkson.sot.utils.Direction;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class Door implements Listener {

    private SoT plugin;
    private Area bounds;
    private Location floor;

    private Direction axis;
    private boolean isOpen;

    public Door(SoT plugin, Location minPoint, Location maxPoint, Direction axis) {
        this.plugin = plugin;
        this.bounds = new Area(minPoint, maxPoint);
        this.floor = bounds.getMinPoint().clone().subtract(0, 1, 0);
        if (axis == Direction.SOUTH)
            axis = Direction.NORTH;
        if (axis == Direction.EAST)
            axis = Direction.WEST;
        this.axis = axis;
        isOpen = false;
    }

    public DoorClone cloneByLocation(Location locationToCloneTo) {
        // Convert the Bukkit locations to WorldEdit locations
        System.out.println("cloning to: " + locationToCloneTo);
        com.sk89q.worldedit.util.Location weLocMin = BukkitAdapter.adapt(bounds.getMinPoint());
        com.sk89q.worldedit.util.Location weLocMax = BukkitAdapter.adapt(bounds.getMaxPoint());
        BukkitWorld world = new BukkitWorld(locationToCloneTo.getWorld());

        CuboidRegion region = new CuboidRegion(world,
                BlockVector3.at(weLocMin.getX(), weLocMin.getY(), weLocMin.getZ()),
                BlockVector3.at(weLocMax.getX(), weLocMax.getY(), weLocMax.getZ()));

        try (EditSession editSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(world, -1)) {
            BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
            clipboard.setOrigin(region.getMinimumPoint());

            ForwardExtentCopy copy = new ForwardExtentCopy(editSession, region, clipboard, region.getMinimumPoint());
            Operations.completeLegacy(copy);
            System.out.println("Copied!");

            // Convert the destination Bukkit location to WorldEdit location
            com.sk89q.worldedit.util.Location weTo = BukkitAdapter.adapt(locationToCloneTo);

            ClipboardHolder holder = new ClipboardHolder(clipboard);
            Operation operation = holder.createPaste(editSession)
                    .to(BlockVector3.at(weTo.getX(), weTo.getY(), weTo.getZ()))
                    .build();
            Operations.completeLegacy(operation);

            System.out.println("Pasted!");


            Location newMaxPoint = new Location(locationToCloneTo.getWorld(), locationToCloneTo.getX() + bounds.getWidth(), locationToCloneTo.getY() + bounds.getHeight(), locationToCloneTo.getZ() + bounds.getDepth());

            return new DoorClone(this, plugin, locationToCloneTo, newMaxPoint, axis);

        } catch (WorldEditException e) {
            e.printStackTrace();
        }
        return null;
    }


    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();

        if (clickedBlock == null) return;
        if (isOpen) return;

        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Check if the clicked block is part of the door
        if (!isBlockPartOfDoor(clickedBlock)) return;
        // Check if player right-clicks the "door" (White Glazed Terracotta) with the "key" (Slimeball)
        if (clickedBlock.getType() == Material.WHITE_GLAZED_TERRACOTTA &&
                itemInHand.getType() == Material.SLIME_BALL) {

            // Decrement slimeball quantity by one
            if (itemInHand.getAmount() > 1) {
                itemInHand.setAmount(itemInHand.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }

            // Call the method to lower the door
            lowerDoor(clickedBlock);
            isOpen = true;

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
            bounds = new Area(bounds.getMinPoint().subtract(0, 1, 0), bounds.getMaxPoint().subtract(0, 1, 0));

            // Check if the door's bottom has reached the floor (or any other desired condition)
            if (bounds.getMaxPoint().getY() > floor.blockY()) {  // Assuming floorY is the Y-coordinate of the floor
                lowerDoor(keyBlock.getRelative(0, -1, 0));
            }
        }, 4L); // 10 ticks or 0.5 seconds delay for example
    }
    private boolean isBlockPartOfDoor(Block block) {
        Location blockLocation = block.getLocation();
        return blockLocation.getX() >= bounds.getMinPoint().getX() &&
                blockLocation.getX() <= bounds.getMaxPoint().getX() &&
                blockLocation.getY() >= bounds.getMinPoint().getY() &&
                blockLocation.getY() <= bounds.getMaxPoint().getY() &&
                blockLocation.getZ() >= bounds.getMinPoint().getZ() &&
                blockLocation.getZ() <= bounds.getMaxPoint().getZ();
    }

    public Direction getAxis() {
        return axis;
    }
}
