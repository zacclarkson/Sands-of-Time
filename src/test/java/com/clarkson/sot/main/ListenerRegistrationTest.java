package com.clarkson.sot.main;

import com.clarkson.sot.dungeon.DoorManager;
import com.clarkson.sot.dungeon.MobManager;
import com.clarkson.sot.dungeon.VaultManager;
import com.clarkson.sot.events.FloorItemManager;
import com.clarkson.sot.scoring.ScoreManager;

import org.bukkit.Server;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Pins the single listener-registration point (the duplicated-warning bug).
 *
 * <p>{@link VaultManager}, {@link DoorManager} and {@link FloorItemManager} each called
 * {@code registerEvents(this, plugin)} in their own constructor, while {@link SoT#onEnable()}
 * registered the very same instances again. Bukkit does not de-duplicate — it just appends a
 * second {@code RegisteredListener} — so every handler body ran twice and a single right-click
 * on a vault printed each message twice. #81 fixed {@code VaultManager} and
 * {@code FloorItemManager}; {@code DoorManager} was the one left over.
 *
 * <p>All three are covered here because the invariant is the pattern, not the one bug:
 * {@code SoT.onEnable()} is the only place that may register these, so their constructors must
 * not touch the {@link PluginManager} at all.
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

    /**
     * {@link MobManager} builds its PDC {@code NamespacedKey}s in its constructor, and
     * {@code JavaPlugin.getName()} is final — so a Mockito plugin mock hands NamespacedKey a null
     * namespace and throws. This one case therefore needs a real MockBukkit plugin, and asserts the
     * invariant directly against the handler lists instead of a mocked PluginManager.
     */
    @Test
    void mobManagerDoesNotRegisterItself() {
        MockBukkit.mock();
        try {
            Plugin mockPlugin = MockBukkit.createMockPlugin();

            new MobManager(mockPlugin, mock(GameManager.class));

            assertTrue(HandlerList.getRegisteredListeners(mockPlugin).isEmpty(),
                    "SoT.onEnable() is the only place that may register this listener");
        } finally {
            MockBukkit.unmock();
        }
    }

    private void assertNothingWasRegistered() {
        verify(pluginManager, never()).registerEvents(any(), any());
    }
}
