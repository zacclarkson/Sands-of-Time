package com.clarkson.sot.commands;

import com.clarkson.sot.dungeon.DungeonGenerator;
import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.main.SoT;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * /sotreloadsegments
 * <p>
 * Re-reads all segment templates from {@code plugins/SoT/} into the live {@link DungeonGenerator}, so
 * a segment saved with {@code /sotsavesegment} can be used without restarting the server. Only affects
 * the <em>next</em> dungeon generation; it is refused while a game is running (that dungeon is already
 * built from the previously loaded templates).
 */
public class ReloadSegmentsCommand implements CommandExecutor {

    private final SoT plugin;
    private final GameManager gameManager;

    public ReloadSegmentsCommand(@NotNull SoT plugin, @NotNull GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("sot.admin.savesegment")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }

        if (gameManager.getCurrentState() == GameState.RUNNING) {
            sender.sendMessage(Component.text(
                    "Cannot reload segments while a game is running — end it first (the running "
                    + "dungeon is already built).", NamedTextColor.RED));
            return true;
        }

        DungeonGenerator generator = gameManager.getDungeonGenerator();
        boolean ok = generator.loadSegmentTemplates(plugin.getDataFolder());
        int count = generator.getLoadedSegmentCount();

        if (ok) {
            sender.sendMessage(Component.text(
                    "Reloaded " + count + " segment template" + (count == 1 ? "" : "s") + ".",
                    NamedTextColor.GREEN));
        } else if (count == 0) {
            sender.sendMessage(Component.text(
                    "No segment templates found in plugins/SoT/.", NamedTextColor.RED));
        } else if (!generator.hasHubTemplate()) {
            sender.sendMessage(Component.text(
                    "Loaded " + count + " template(s) but none is a HUB — /sot start will abort until "
                    + "a HUB segment exists.", NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text(
                    "Segment reload reported a problem — check the console.", NamedTextColor.YELLOW));
        }
        return true;
    }
}
