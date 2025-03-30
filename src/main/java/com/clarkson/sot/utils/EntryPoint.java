package com.clarkson.sot.utils;

import org.bukkit.Location;

public class EntryPoint {
    private Location location;



    private Direction direction;
    public EntryPoint(Location location, Direction direction) {
        this.location = location;
        this.direction = direction;
    }
    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public Direction getOppositeDirection() {
        if (direction == Direction.NORTH)
            return Direction.SOUTH;
        else if (direction == Direction.SOUTH)
            return Direction.NORTH;
        else if (direction == Direction.EAST)
            return Direction.WEST;
        else if (direction == Direction.WEST)
            return Direction.EAST;
        return null;
    }

    @Override
    public String toString() {
        return "EntryPoint{" +
                "location=" + location +
                ", direction=" + direction +
                '}';
    }
}
