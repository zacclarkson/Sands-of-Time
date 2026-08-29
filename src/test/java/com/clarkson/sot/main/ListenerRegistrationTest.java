package com.clarkson.sot.main;

import com.clarkson.sot.dungeon.DoorManager;
import com.clarkson.sot.dungeon.VaultManager;
import com.clarkson.sot.events.FloorItemManager;
import com.clarkson.sot.scoring.ScoreManager;

import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.mockito.Mockito.*;

/**
 * Pins the single listener-registration point (the duplicated-warning bug).
 *
 * <p>{@link VaultManager}, {@link DoorManager} and {@link FloorItemManager} used to call
 * {@code registerEvents(this, plugin)} in their own constructors, while {@link SoT#onEnable()}
 * registered the very same instances again. Bukkit does not de-duplicate — it just appends a
 * second {@code RegisteredListener} — so every handler body ran twice and a single right-click
 * on a vault printed each message twice. {@code SoT.onEnable()} is the only place that may
 * register these, so their constructors must not touch the {@link PluginManager} at all.
 */
class ListenerRegistrationTest {

    private SoT plugin;
    private PluginManager pluginManager;

    @BeforeEach
    void setUp() {
        plugin = mock(SoT.class);
        pluginManager = mock(PluginManager.class);
        Server server = mock(Server.class);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ListenerRegistrationTest"));
    }

    @Test
    void vaultManagerDoesNotRegisterItself() {
        new VaultManager(plugin, mock(GameManager.class));
        assertNothingWasRegistered();
    }

    @Test
    void doorManagerDoesNotRegisterItself() {
        new DoorManager(plugin, mock(GameManager.class));
        assertNothingWasRegistered();
    }

    @Test
    void floorItemManagerDoesNotRegisterItself() {
        new FloorItemManager(plugin, mock(GameManager.class), mock(ScoreManager.class));
        assertNothingWasRegistered();
    }

    private void assertNothingWasRegistered() {
        verify(pluginManager, never()).registerEvents(any(), any());
    }
}
