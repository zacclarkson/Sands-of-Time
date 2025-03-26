package com.clarkson.sot.entities;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class CoinStack extends FloorItem {

    private final int points;

    public CoinStack(Location location, int points, Plugin plugin) {
        super(location, null, plugin); // No item is given to the player
        this.points = points;
    }

    @Override
    public void handlePickup(Player player) {
        // Award points to the player
        player.sendMessage("You earned " + points + " points!");
        // TODO: Add logic to update the player's score
        markAsPickedUp();
    }
}