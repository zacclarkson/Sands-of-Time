package com.clarkson.sot.commands;

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.main.SoTConfig;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the {@code /sot set} flow and the guards that keep a round from starting before the
 * universal locations are configured.
 *
 * <p>MockBukkit supplies a real {@link PlayerMock} (it has a location and a readable message queue);
 * {@link GameManager} and {@link Plugin} are Mockito mocks, with the plugin handing back a real
 * {@link YamlConfiguration} so persistence can be asserted. {@code onCommand} is invoked directly
 * rather than through a registered command, so no plugin.yml loading is involved.
 */
class GameCommandTest {

    private ServerMock server;
    private Plugin plugin;
    private FileConfiguration config;
    private GameManager gameManager;
    private GameCommand command;
    private Command bukkitCommand;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        config = new YamlConfiguration();
        plugin = mock(Plugin.class);
        when(plugin.getConfig()).thenReturn(config);
        doNothing().when(plugin).saveConfig();

        gameManager = mock(GameManager.class);
        when(gameManager.getCurrentState()).thenReturn(GameState.SETUP);
        when(gameManager.areLocationsConfigured()).thenReturn(true);
        when(gameManager.getUnconfiguredLocationNames()).thenReturn(List.of());

        command = new GameCommand(plugin, gameManager);
        bukkitCommand = mock(Command.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock opPlayer() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        return player;
    }

    private boolean run(org.bukkit.command.CommandSender sender, String... args) {
        return command.onCommand(sender, bukkitCommand, "sot", args);
    }

    @Test
    void setLobbyAppliesLiveAndPersists() {
        PlayerMock player = opPlayer();
        player.teleport(new Location(player.getWorld(), 12.5, 64.0, -8.25, 90.0f, 0.0f));

        assertTrue(run(player, "set", "lobby"));

        verify(gameManager).setLobbyLocation(any(Location.class));
        verify(plugin).saveConfig();
        assertEquals(player.getWorld().getName(), config.getString(SoTConfig.LOBBY_PATH + ".world"));
        assertEquals(12.5, config.getDouble(SoTConfig.LOBBY_PATH + ".x"), 1e-6);
    }

    @Test
    void setTrappedWritesTheTrappedPath() {
        PlayerMock player = opPlayer();

        assertTrue(run(player, "set", "trapped"));

        verify(gameManager).setTrappedLocation(any(Location.class));
        assertNotNull(config.getString(SoTConfig.TRAPPED_PATH + ".world"));
    }

    @Test
    void setIsRejectedFromTheConsole() {
        // The console has no location to capture.
        assertTrue(run(server.getConsoleSender(), "set", "lobby"));

        verify(gameManager, never()).setLobbyLocation(any());
        verify(plugin, never()).saveConfig();
    }

    @Test
    void setRejectsAnUnknownTarget() {
        assertTrue(run(opPlayer(), "set", "hub"));

        verify(gameManager, never()).setLobbyLocation(any());
        verify(gameManager, never()).setTrappedLocation(any());
        verify(plugin, never()).saveConfig();
    }

    @Test
    void setRequiresATarget() {
        assertTrue(run(opPlayer(), "set"));

        verify(plugin, never()).saveConfig();
    }

    @Test
    void movingTheLobbyIsRejectedDuringARound() {
        // startGame() derives the dungeon world and origin from the lobby.
        when(gameManager.getCurrentState()).thenReturn(GameState.RUNNING);

        assertTrue(run(opPlayer(), "set", "lobby"));

        verify(gameManager, never()).setLobbyLocation(any());
        verify(plugin, never()).saveConfig();
    }

    @Test
    void movingTheTrappedLocationIsAllowedDuringARound() {
        when(gameManager.getCurrentState()).thenReturn(GameState.RUNNING);

        assertTrue(run(opPlayer(), "set", "trapped"));

        verify(gameManager).setTrappedLocation(any(Location.class));
        verify(plugin).saveConfig();
    }

    @Test
    void startIsRefusedWhileLocationsAreUnconfigured() {
        when(gameManager.areLocationsConfigured()).thenReturn(false);
        when(gameManager.getUnconfiguredLocationNames()).thenReturn(List.of("lobby"));

        assertTrue(run(opPlayer(), "start"));

        verify(gameManager, never()).startGame();
    }

    @Test
    void setupIsRefusedWhileLocationsAreUnconfigured() {
        when(gameManager.areLocationsConfigured()).thenReturn(false);
        when(gameManager.getUnconfiguredLocationNames()).thenReturn(List.of("lobby", "trapped"));

        assertTrue(run(opPlayer(), "setup"));

        verify(gameManager, never()).setupGame(any(), any());
    }

    @Test
    void tabCompleteOffersSetTargets() {
        List<String> completions =
                command.onTabComplete(opPlayer(), bukkitCommand, "sot", new String[]{"set", "lo"});

        assertEquals(List.of("lobby"), completions);
    }

    @Test
    void tabCompleteOffersSetAsASubcommand() {
        List<String> completions =
                command.onTabComplete(opPlayer(), bukkitCommand, "sot", new String[]{"se"});

        // "setup" shares the prefix, so assert membership rather than the whole list.
        assertTrue(completions.contains("set"));
        assertTrue(completions.contains("setup"));
    }
}
