package com.clarkson.sot;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

public class CoinStack extends FloorItem {
    int amount;
    final ItemStack COINS_ITEM = new ItemStack(Material.NETHER_BRICK, 1);
    public CoinStack(Location location, int amount){
        super(location, new ItemStack(Material.NETHER_BRICK, 1));
        this.amount = amount;
        spawnItemRepresentation(COINS_ITEM);
    }


    private void spawnItemRepresentation(ItemStack item) {

        //location = this.location.clone().add(0.85, -0.8, 0.15);
        @NotNull Location location = this.getLocation().clone().add(0.5, -1.8, 0.5);

        this.armorStand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);

        armorStand.setGravity(false);
        armorStand.setVisible(false);
        armorStand.setSmall(false);
        armorStand.setBasePlate(false);
        armorStand.setCustomName("StaticItem");
        armorStand.setCustomNameVisible(false);
        armorStand.setInvulnerable(true);
        armorStand.setItem(EquipmentSlot.HEAD, COINS_ITEM);

        // Pose to make the item lay flat on the ground;

        Random rand = new Random();
        float yaw = rand.nextFloat() * 360.0F;  // Generate a random angle between 0 and 360 degrees
        armorStand.setRotation(yaw, 0F);

    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        if (isPickedup) return;

        Player player = event.getPlayer();
        Location playerLocation = player.getLocation();

        double distance = playerLocation.distance(this.armorStand.getLocation());
        if(distance <= 1.5) {
            // If the entity is our custom ArmorStand
            if (this.armorStand.getCustomName() != null && this.armorStand.getCustomName().equals("StaticItem")) {
                //TODO: Implement Scoreboard


                // Remove the armor stand
                this.armorStand.remove();
                isPickedup = true;
            }
        }
    }
}
