package com.clarkson.sot;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class SoT extends JavaPlugin {

    @Override
    public void onEnable() {
        DungeonManager dungeonManager = new DungeonManager();
        Door door = new Door(
                this,
                new Location(Bukkit.getWorld("world"), 3, 138, 214),
                new Location(Bukkit.getWorld("world"), 1, 141, 214),
                Direction.NORTH); // Initialize your door here with appropriate arguments

        CommandManager commandManager = new CommandManager(dungeonManager, door, this);

        this.getCommand("generatedungeon").setExecutor(commandManager);
        this.getCommand("cloneDoor").setExecutor(commandManager);
        ItemCollector testThing = new ItemCollector(new Location(Bukkit.getWorld("world"), -19, 141,231), new ItemStack(Material.MAGMA_CREAM , 1));
        this.getServer().getPluginManager().registerEvents(testThing, this);
    }

    @Override
    public void onDisable() {
        // Loop through all loaded worlds
        for (World world : Bukkit.getWorlds()) {

            // Loop through all entities in the world
            for (Entity entity : world.getEntities()) {

                // Check if the entity is an armor stand and has the custom name "StaticItem"
                if (entity instanceof ArmorStand && "StaticItem".equals(entity.getCustomName())) {
                    entity.remove();  // Remove the armor stand
                }
            }
        }
    }

}

