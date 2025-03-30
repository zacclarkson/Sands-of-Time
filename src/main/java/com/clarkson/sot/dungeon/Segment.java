package com.clarkson.sot.dungeon;

import com.clarkson.sot.entities.Area;
import com.clarkson.sot.utils.Direction;
import com.clarkson.sot.utils.EntryPoint;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class Segment {

    private String name;
    private SegmentType type;
    private List<EntryPoint> entryPoints;
    private final Area bounds;

    private List<Location> sandSpawnLocations = new ArrayList<>();
    private List<Location> itemSpawnLocations = new ArrayList<>();
    private List<Location> coinSpawnLocations = new ArrayList<>();
    private int totalCoins; // Total number of coins to spawn

    public Segment(String name, SegmentType type, List<EntryPoint> entryPoints, Location minPoint, Location maxPoint,
                   List<Location> sandSpawnLocations, List<Location> itemSpawnLocations, List<Location> coinSpawnLocations, int totalCoins) {
                           this.name = name;
                           this.type = type;
                           this.entryPoints = entryPoints;
                           this.bounds = new Area(minPoint, maxPoint);
                           this.sandSpawnLocations = sandSpawnLocations;
                           this.itemSpawnLocations = itemSpawnLocations;
                           this.coinSpawnLocations = coinSpawnLocations;
                           this.totalCoins = totalCoins;
    }

    // Getters for spawn locations
    public List<Location> getSandSpawnLocations() {
        return sandSpawnLocations;
    }

    public List<Location> getItemSpawnLocations() {
        return itemSpawnLocations;
    }

    public List<Location> getCoinSpawnLocations() {
        return coinSpawnLocations;
    }


    public void cloneToLocation(Location to) throws WorldEditException {
        // convert the bounds to WorldEdit's format
        com.sk89q.worldedit.world.World world = BukkitAdapter.adapt(bounds.getMinPoint().getWorld());
        BlockVector3 min = BukkitAdapter.asBlockVector(bounds.getMinPoint());
        BlockVector3 max = BukkitAdapter.asBlockVector(bounds.getMaxPoint());
        BlockVector3 destination = BukkitAdapter.asBlockVector(to);

        // Create a region and clipboard
        CuboidRegion region = new CuboidRegion(world, min, max);
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);

        try (EditSession editSession = WorldEdit.getInstance().newEditSession(world)) {
            // Copy
            ForwardExtentCopy forwardExtentCopy = new ForwardExtentCopy(editSession, region, clipboard, min);
            Operations.complete(forwardExtentCopy);

            // Paste
            ClipboardHolder clipboardHolder = new ClipboardHolder(clipboard);
            Operation paste = clipboardHolder.createPaste(editSession)
                    .to(destination)
                    .ignoreAirBlocks(false) // Change this if you want
                    .build();
            Operations.complete(paste);
        }
    }

    public void cloneByEntryPoint(EntryPoint entryPoint) {
        Location locationToCloneTo = findRelativeNwCorner(entryPoint);

        com.sk89q.worldedit.world.World world = BukkitAdapter.adapt(bounds.getMinPoint().getWorld());
        BlockVector3 min = BukkitAdapter.asBlockVector(bounds.getMinPoint());
        BlockVector3 max = BukkitAdapter.asBlockVector(bounds.getMaxPoint());
        BlockVector3 destination = BukkitAdapter.asBlockVector(locationToCloneTo);

        CuboidRegion region = new CuboidRegion(world, min, max);
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);

        try (EditSession editSession = WorldEdit.getInstance().newEditSession(world)) {
            // Copy
            ForwardExtentCopy forwardExtentCopy = new ForwardExtentCopy(editSession, region, clipboard, min);
            Operations.complete(forwardExtentCopy);

            // Paste
            ClipboardHolder clipboardHolder = new ClipboardHolder(clipboard);
            Operation paste = clipboardHolder.createPaste(editSession)
                    .to(destination)
                    .ignoreAirBlocks(false) // Change this if you want
                    .build();
            Operations.complete(paste);
            System.out.println("Cloned to location: " + locationToCloneTo);
        } catch (WorldEditException e) {
            throw new RuntimeException(e);
        }
    }


    private EntryPoint getAdjacentEntryPoint(EntryPoint oppositeEntryPoint) {
        // Create a copy of the original location
        Location originalLocation = oppositeEntryPoint.getLocation();
        Location adjacentLocation = new Location(originalLocation.getWorld(),
                originalLocation.getX(),
                originalLocation.getY(),
                originalLocation.getZ(),
                originalLocation.getYaw(),
                originalLocation.getPitch());

        if (oppositeEntryPoint.getDirection() == Direction.NORTH)
            adjacentLocation.setZ(adjacentLocation.getBlockZ() - 1);
        else if (oppositeEntryPoint.getDirection() == Direction.SOUTH)
            adjacentLocation.setZ(adjacentLocation.getBlockZ() + 1);
        else if (oppositeEntryPoint.getDirection() == Direction.EAST)
            adjacentLocation.setX(adjacentLocation.getBlockX() + 1);
        else if (oppositeEntryPoint.getDirection() == Direction.WEST)
            adjacentLocation.setX(adjacentLocation.getBlockX() - 1);

        return new EntryPoint(adjacentLocation, oppositeEntryPoint.getOppositeDirection());
    }


    public Location findRelativeNwCorner(EntryPoint oppositeEntryPoint) {
        // 1. Identify the segment's entry point adjacent to the provided opposite entry point.
        EntryPoint adjacentEntryPoint = getAdjacentEntryPoint(oppositeEntryPoint);
        // 2. Check if the segment has an entry point in the same direction as the adjacent entry point.
        EntryPoint originalSegmentEntryPoint = getEntryPoint(adjacentEntryPoint);

        // 3. Calculate the offset from the segment's NW corner to the located entry point.
        int offsetX = originalSegmentEntryPoint.getLocation().getBlockX() - bounds.getMinPoint().getBlockX();
        int offsetY = originalSegmentEntryPoint.getLocation().getBlockY() - bounds.getMinPoint().getBlockY();
        int offsetZ = originalSegmentEntryPoint.getLocation().getBlockZ() - bounds.getMinPoint().getBlockZ();

        return adjacentEntryPoint.getLocation().clone().subtract(offsetX, offsetY, offsetZ);
    }

    @NotNull
    private EntryPoint getEntryPoint(EntryPoint adjacentEntryPoint) {
        EntryPoint segmentEntryPoint = null;
        for (EntryPoint ep : this.getEntryPoints()) {
            if (ep.getDirection() == adjacentEntryPoint.getDirection()) {
                segmentEntryPoint = ep;
                break;
            }
        }

        if (segmentEntryPoint == null) {
            throw new IllegalStateException("No matching entry point found in segment for the given opposite entry point.");
        }
        return segmentEntryPoint;
    }


    public Location getStartLocation() {
        return bounds.getMinPoint();
    }
    public Location getEndLocation() {
        return bounds.getMaxPoint();
    }
    public String getName() {
        return name;
    }

    public SegmentType getType() {
        return type;
    }

    public List<EntryPoint> getEntryPoints() {
        return entryPoints;
    }


    public Area getBounds() {
        return bounds;
    }

    public boolean hasEntryPointInDirection(Direction dir) {
        for (EntryPoint ep : this.getEntryPoints()) {
            if (ep.getDirection() == dir) {
                return true;
            }
        }
        return false;
    }
    @Override
    public String toString() {
        String result = "";
        result += name + "\n";
        result += type + "\n";
        for (EntryPoint e : entryPoints){
            result += e + "\n";
        }
        result += bounds.getMinPoint() + "\n";
        result += bounds.getMaxPoint() + "\n";
        return result;
    }

    public int getTotalCoins() {
        return totalCoins;
    }
}


