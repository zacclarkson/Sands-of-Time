package com.clarkson.sot.dungeon;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the per-instance safe-exit location held by {@link Dungeon}. {@link Location} is plain
 * arithmetic over a {@link World} reference, so a mocked world is enough — no server needed.
 */
class DungeonSafeExitTest {

    private World world;
    private Location origin;

    @BeforeEach
    void setUp() {
        world = mock(World.class);
        origin = new Location(world, 100, 64, 200);
    }

    private Dungeon dungeonWithSafeExit(@Nullable Location safeExit) {
        return new Dungeon(UUID.randomUUID(), world, origin, mock(DungeonBlueprint.class),
                origin.clone(), safeExit,
                new HashMap<>(), new HashMap<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>());
    }

    @Test
    void returnsTheSafeExitItWasBuiltWith() {
        Location safeExit = new Location(world, 104, 65, 209);
        assertEquals(safeExit, dungeonWithSafeExit(safeExit).getSafeExitLocation());
    }

    @Test
    void safeExitIsClonedSoCallersCannotMutateInstanceState() {
        Dungeon dungeon = dungeonWithSafeExit(new Location(world, 104, 65, 209));

        Location handedOut = dungeon.getSafeExitLocation();
        assertNotNull(handedOut);
        handedOut.add(50, 0, 50);

        assertEquals(new Location(world, 104, 65, 209), dungeon.getSafeExitLocation());
    }

    @Test
    void safeExitIsNullWhenNoSegmentDefinedOne() {
        Dungeon dungeon = dungeonWithSafeExit(null);
        assertNull(dungeon.getSafeExitLocation(), "callers fall back to the hub in this case");
        assertNotNull(dungeon.getHubLocation());
    }
}
