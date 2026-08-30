package com.clarkson.sot.dungeon;

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.player.SoTPlayerData;
import com.clarkson.sot.player.SoTPlayerManager;
import com.clarkson.sot.utils.TeamManager;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pins mob-kill attribution (#46).
 *
 * <p>{@code SoTPlayerData.monstersKilled} and its incrementer shipped long ago but nothing ever
 * called them — {@link SoTPlayerManager} was not constructed anywhere outside tests, so the stat
 * was unreachable. {@link MobManager} is what feeds it now, and the PDC tag is what keeps the count
 * honest: vanilla spawns its own mobs in any dark dungeon room, and those must not count.
 */
class MobKillCountTest {

    private ServerMock server;
    private World world;
    private Plugin plugin;
    private GameManager gameManager;
    private SoTPlayerManager playerManager;
    private MobManager mobManager;
    private PlayerMock player;

    private final UUID teamId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("mob-kill-world");
        plugin = MockBukkit.createMockPlugin();

        player = server.addPlayer();

        TeamManager teamManager = mock(TeamManager.class);
        when(teamManager.getPlayerTeamId(player)).thenReturn(teamId);

        playerManager = new SoTPlayerManager(plugin);
        playerManager.initializePlayer(player);

        gameManager = mock(GameManager.class);
        when(gameManager.getCurrentState()).thenReturn(GameState.RUNNING);
        when(gameManager.getTeamManager()).thenReturn(teamManager);
        when(gameManager.getPlayerManager()).thenReturn(playerManager);

        mobManager = new MobManager(plugin, gameManager);
        server.getPluginManager().registerEvents(mobManager, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** A zombie carrying the manager's own tag, as if it had been spawned by a marker. */
    private Zombie taggedMob() {
        Zombie zombie = world.spawn(new Location(world, 100, 64, 100), Zombie.class);
        zombie.getPersistentDataContainer()
                .set(new NamespacedKey(plugin, MobManager.MOB_TAG_KEY), PersistentDataType.BYTE, (byte) 1);
        return zombie;
    }

    /** An untagged zombie, standing in for one vanilla spawned in a dark room. */
    private Zombie untaggedMob() {
        return world.spawn(new Location(world, 100, 64, 100), Zombie.class);
    }

    private void killWith(Mob mob, PlayerMock killer) {
        mob.setKiller(killer);
        DamageSource source = DamageSource.builder(DamageType.PLAYER_ATTACK)
                .withCausingEntity(killer)
                .withDirectEntity(killer)
                .build();
        server.getPluginManager().callEvent(new EntityDeathEvent(mob, source, List.<ItemStack>of()));
    }

    private int monstersKilled() {
        SoTPlayerData data = playerManager.getPlayerData(player.getUniqueId());
        assertNotNull(data, "the player should have stat data for the round");
        return data.getMonstersKilled();
    }

    @Test
    void killingATaggedMobCreditsTheKiller() {
        killWith(taggedMob(), player);

        assertEquals(1, monstersKilled());
    }

    @Test
    void killsAccumulate() {
        killWith(taggedMob(), player);
        killWith(taggedMob(), player);
        killWith(taggedMob(), player);

        assertEquals(3, monstersKilled());
    }

    @Test
    void anUntaggedMobDoesNotCount() {
        killWith(untaggedMob(), player);

        assertEquals(0, monstersKilled(),
                "vanilla spawns in a dark room are ambient danger, not a designed encounter");
    }

    @Test
    void aTaggedMobThatDiesWithNoKillerCreditsNobody() {
        Zombie zombie = taggedMob();
        // Fall damage, suffocation, another mob — getKiller() is null and there is nobody to credit.
        DamageSource source = DamageSource.builder(DamageType.FALL).build();
        server.getPluginManager().callEvent(new EntityDeathEvent(zombie, source, List.<ItemStack>of()));

        assertEquals(0, monstersKilled());
    }

    @Test
    void aKillerWithNoStatDataIsIgnoredRatherThanThrowing() {
        PlayerMock stranger = server.addPlayer(); // never passed through initializePlayer
        Zombie zombie = taggedMob();

        assertDoesNotThrow(() -> killWith(zombie, stranger));
        assertEquals(0, monstersKilled(), "the tracked player should be unaffected");
    }

    @Test
    void aKilledMobIsNoLongerTrackedForCleanup() {
        // Spawn through the manager so it is genuinely tracked, then kill it.
        mobManager.armSpawner(new Location(world, 100, 64, 100), teamId, 0);
        player.simulatePlayerMove(new Location(world, 100, 64, 96));
        assertEquals(1, mobManager.getTrackedMobCount(teamId), "precondition: the spawner fired");

        Mob spawned = world.getEntitiesByClass(Mob.class).iterator().next();
        killWith(spawned, player);

        assertEquals(0, mobManager.getTrackedMobCount(teamId),
                "a dead mob must be untracked so the set does not grow across a round");
    }

    @Test
    void statsDoNotCarryIntoTheNextRound() {
        killWith(taggedMob(), player);
        assertEquals(1, monstersKilled(), "precondition");

        // What GameManager.tearDownRound() does at the end of a round.
        playerManager.clearAll();
        playerManager.initializePlayer(player);

        assertEquals(0, monstersKilled());
    }
}
