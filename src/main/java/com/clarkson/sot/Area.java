package com.clarkson.sot;

import org.bukkit.Location;

public class Area {


    private final Location minPoint;
    private final Location maxPoint;

    private final double width;
    private final double depth;
    private final double height;


    public Area(Location minPoint, Location maxPoint) {

        width = Math.abs(maxPoint.getX() - minPoint.getX());
        height = Math.abs(maxPoint.getY() - minPoint.getY());
        depth = Math.abs(maxPoint.getZ() - minPoint.getZ());

        this.minPoint = getNwCorner(minPoint, maxPoint);
        this.maxPoint = new Location(this.minPoint.getWorld(), this.minPoint.getX() + width, this.minPoint.getY() + height, this.minPoint.getZ() + depth);
        System.out.println("Creating Area:");
        System.out.println(minPoint);
        System.out.println(maxPoint);
    }


    public Location getMinPoint() {
        return minPoint;
    }

    public Location getMaxPoint() {
        return maxPoint;
    }


    private Location getNwCorner(Location loc1, Location loc2) {
        double nwX = Math.min(loc1.getX(), loc2.getX());
        double nwZ = Math.min(loc1.getZ(), loc2.getZ());
        double nwY = Math.min(loc1.getY(), loc2.getY());

        // Using the world from one of the locations, assuming both are in the same world
        return new Location(loc1.getWorld(), nwX, nwY, nwZ);
    }


    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public double getDepth() {
        return depth;
    }

    // Getters, setters, and other methods...
}

