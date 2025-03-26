package com.clarkson.sot.entities;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import java.util.Random;

public class CoinStack extends FloorItem {
    private int amount;
    private BlockDisplay blockDisplay;

    public CoinStack(Location location, int amount) {
        super(location, null);
        this.amount = amount;
        spawnBlockRepresentation();
    }

    private void spawnBlockRepresentation() {
        Location location = this.getLocation().clone().add(0.5, -1.8, 0.5);

        // Get BlockData for the stone block
        BlockData blockData = location.getWorld().getBlockData(Material.STONE);

        // Spawn BlockDisplay entity
        this.blockDisplay = (BlockDisplay) location.getWorld().spawn(location, BlockDisplay.class, display -> {
            display.setBlock(blockData); // Set the block type
            display.setGravity(false);   // Prevent falling
            display.displayName(Component.text("CoinStack")); // Set the display name
            display.setInvulnerable(true);

            // Set transformation to random rotation
            Random rand = new Random();
            float yaw = rand.nextFloat() * 360.0F;
            Transformation transformation = new Transformation(
                new Vector(0, 0, 0), // Position offset
                new EulerAngle(0, Math.toRadians(yaw), 0), // Rotation
                new Vector(1, 1, 1), // Scale (default)
                new EulerAngle(0, 0, 0) // Left rotation (unused)
            );
            display.setTransformation(transformation);
        });
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        if (isPickedup || blockDisplay == null) return;

        Player player = event.getPlayer();
        Location playerLocation = player.getLocation();

        double distance = playerLocation.distance(this.blockDisplay.getLocation());
        if (distance <= 1.5) {
            if (this.blockDisplay.displayName() != null &&
                Component.text("CoinStack").equals(this.blockDisplay.displayName())) {
                
                // TODO: Award points to the player
                // addPoints(player, amount);

                // Remove the BlockDisplay entity
                this.blockDisplay.remove();
                isPickedup = true;
            }
        }
    }
}
