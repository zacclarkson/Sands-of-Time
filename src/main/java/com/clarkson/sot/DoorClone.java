package com.clarkson.sot;

import org.bukkit.Location;

public class DoorClone extends Door {
    private Door originalDoor;

    public DoorClone(Door originalDoor ,SoT plugin, Location minPoint, Location maxPoint, Direction axis) {
        super(plugin, minPoint, maxPoint, axis );
        this.originalDoor = originalDoor;
    }

    public Door getOriginalDoor() {
        return originalDoor;
    }
}
