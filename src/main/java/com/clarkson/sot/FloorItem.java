package com.clarkson.sot;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;

import java.util.Random;

public class FloorItem implements Listener {

    private Location location;


    private ItemStack item;
    ArmorStand armorStand; // Reference to the armor stand
    boolean isPickedup;

    public FloorItem(Location location, ItemStack item) {
        this.location = location;
        this.item = item;
        System.out.println("creating armor stand with: " + item);
        System.out.println("its Material is: " + item.getType());
        spawnItemRepresentation(item);
    }

    private void spawnItemRepresentation(ItemStack item) {

        //location = this.location.clone().add(0.85, -0.8, 0.15);
        location = this.location.clone().add(0.5, -0.8, 0.5);

        this.armorStand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);

        armorStand.setGravity(false);
        armorStand.setVisible(false);
        armorStand.setSmall(false);
        armorStand.setBasePlate(false);
        armorStand.setCustomName("StaticItem");
        armorStand.setCustomNameVisible(false);
        armorStand.setInvulnerable(true);
        armorStand.setItem(EquipmentSlot.HAND, item);

        // Pose to make the item lay flat on the ground
        armorStand.setArms(true);
        armorStand.setRightArmPose(new EulerAngle(0,0,0));

        Random rand = new Random();
        float yaw = rand.nextFloat() * 360.0F;  // Generate a random angle between 0 and 360 degrees
        armorStand.setRotation(yaw, 0F);

    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (isPickedup) return;

        Player player = event.getPlayer();
        Location playerLocation = player.getLocation();

        double distance = playerLocation.distance(this.armorStand.getLocation());
        if(distance <= 1.5) {
            // If the entity is our custom ArmorStand
            if (this.armorStand.getCustomName() != null && this.armorStand.getCustomName().equals("StaticItem")) {
                // Give item to player
                if (item != null) {
                    player.getInventory().addItem(item);
                    player.sendTitle("Picked up " + item.getI18NDisplayName() + " [" + item.getAmount() + "x]", "",  10, 20, 10);
                    player.playSound(playerLocation, org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);

                }

                // Remove the armor stand
                this.armorStand.remove();
                isPickedup = true;
            }
        }
    }
    public Location getLocation() {
        return location;
    }

    public ItemStack getItem() {
        return item;
    }

    public ArmorStand getArmorStand() {
        return armorStand;
    }

    public boolean isPickedup() {
        return isPickedup;
    }

}
