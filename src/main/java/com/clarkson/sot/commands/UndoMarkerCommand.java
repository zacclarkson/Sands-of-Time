package com.clarkson.sot.commands;

import com.clarkson.sot.events.BuilderSessionManager;
import com.clarkson.sot.events.PlayerBuilderSession;
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

import java.util.ArrayList;
import java.util.List;

/**
 * /sotundo
 * <p>
 * Removes the most recently placed segment-builder marker group for the calling player.
 * Groups already gone (e.g. removed by left-click) are skipped so undo always reverts a
 * visible marker if any remain in history.
 */
public class UndoMarkerCommand implements CommandExecutor {

    private final BuilderSessionManager sessionManager;
    private final NamespacedKey buildMarkerTag;
    private final NamespacedKey boundGroupKey;

    public UndoMarkerCommand(@NotNull SoT plugin, @NotNull BuilderSessionManager sessionManager) {
        this.sessionManager = sessionManager;
        this.buildMarkerTag = new NamespacedKey(plugin, SegmentBuilderKeys.BUILD_MARKER_TAG);
        this.boundGroupKey  = new NamespacedKey(plugin, SegmentBuilderKeys.BOUND_GROUP);
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
        PlayerBuilderSession session = sessionManager.getSession(player);

        // Pop groups until we find one that still has live entities to remove.
        String groupId;
        while ((groupId = session.popUndo()) != null) {
            int removed = removeGroup(player, groupId);
            if (removed > 0) {
                player.sendActionBar(Component.text(
                        "Undid last marker (" + removed + " entit"
                        + (removed == 1 ? "y" : "ies") + ").", NamedTextColor.YELLOW));
                return true;
            }
            // else: group already gone, keep popping.
        }

        player.sendMessage(Component.text("Nothing to undo.", NamedTextColor.GRAY));
        return true;
    }

    /** Removes all build-marker entities in the player's world sharing the given group ID. */
    private int removeGroup(Player player, String groupId) {
        List<Entity> toRemove = new ArrayList<>();
        for (Entity e : player.getWorld().getEntities()) {
            if (e instanceof Display
                    && e.getPersistentDataContainer().has(buildMarkerTag, PersistentDataType.BYTE)
                    && groupId.equals(e.getPersistentDataContainer()
                            .get(boundGroupKey, PersistentDataType.STRING))) {
                toRemove.add(e);
            }
        }
        toRemove.forEach(Entity::remove);
        return toRemove.size();
    }
}
