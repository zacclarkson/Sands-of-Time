package com.clarkson.sot.dungeon;

import org.bukkit.Location;

import com.clarkson.sot.entities.Door;
import com.clarkson.sot.main.SoT;
import com.clarkson.sot.utils.Direction;

public class DoorClone extends Door {
    private final Door originalDoor;

    public DoorClone(Door originalDoor, SoT plugin, Location minPoint, Location maxPoint, Location lockLocation, Direction axis) {
        super(plugin, minPoint, maxPoint, lockLocation, axis); // Pass lockLocation to the Door constructor
        this.originalDoor = originalDoor;
    }

    public Door getOriginalDoor() {
        return originalDoor;
    }
}