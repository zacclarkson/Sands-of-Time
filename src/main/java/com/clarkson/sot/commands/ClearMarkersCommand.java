package com.clarkson.sot.commands;

import com.clarkson.sot.events.BuilderSessionManager;
import com.clarkson.sot.events.SegmentBuilderKeys;
import com.clarkson.sot.main.SoT;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * /sotclearmarkers
 * <p>
 * Removes every segment-builder marker entity in the player's current world and clears the
 * player's undo history. This does not touch markers in other worlds.
 */
public class ClearMarkersCommand implements CommandExecutor {

    private final BuilderSessionManager sessionManager;
    private final NamespacedKey buildMarkerTag;

    public ClearMarkersCommand(@NotNull SoT plugin, @NotNull BuilderSessionManager sessionManager) {
        this.sessionManager = sessionManager;
        this.buildMarkerTag = new NamespacedKey(plugin, SegmentBuilderKeys.BUILD_MARKER_TAG);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (!sender.hasPermission("sot.admin.builder")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }

        Player player = (Player) sender;
        int removed = 0;
        for (Entity e : player.getWorld().getEntities()) {
            if (e instanceof Display
                    && e.getPersistentDataContainer().has(buildMarkerTag, PersistentDataType.BYTE)) {
                e.remove();
                removed++;
            }
        }

        sessionManager.getSession(player).clearUndo();

        if (removed == 0) {
            player.sendMessage(Component.text("No builder markers found in this world.", NamedTextColor.GRAY));
        } else {
            player.sendMessage(Component.text("Cleared " + removed + " builder marker"
                    + (removed == 1 ? "" : "s") + " in this world.", NamedTextColor.GREEN));
        }
        return true;
    }
}
