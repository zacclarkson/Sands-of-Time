package com.clarkson.sot.events;

import com.clarkson.sot.main.ResourcePackSettings;
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-logic tests for {@link ResourcePackListener}: the decision of whether a player needs the
 * pack (re)sent, and the stability of the pack id. The network and scheduler paths are exercised
 * on the dev server, not here.
 */
class ResourcePackListenerTest {

    private static final byte[] HASH = ResourcePackSettings.sha1Of("v1".getBytes(StandardCharsets.UTF_8));
    private static final String HASH_HEX = ResourcePackSettings.toHex(HASH);

    @Test
    void playerWithExactPackLoadedIsLeftAlone() {
        // A plugin reload for an unrelated jar change must not make everyone's textures reload.
        assertFalse(ResourcePackListener.shouldOffer(HASH, HASH_HEX, Status.SUCCESSFULLY_LOADED));
        assertFalse(ResourcePackListener.shouldOffer(HASH, HASH_HEX.toUpperCase(), Status.SUCCESSFULLY_LOADED),
                "Hash comparison is case-insensitive");
    }

    @Test
    void changedHashIsOffered() {
        byte[] newer = ResourcePackSettings.sha1Of("v2".getBytes(StandardCharsets.UTF_8));

        assertTrue(ResourcePackListener.shouldOffer(newer, HASH_HEX, Status.SUCCESSFULLY_LOADED),
                "A swapped zip has a new hash and must reach players already online");
    }

    @Test
    void playerWithNoPackIsOffered() {
        assertTrue(ResourcePackListener.shouldOffer(HASH, null, null));
        assertTrue(ResourcePackListener.shouldOffer(HASH, "", null));
    }

    @Test
    void samePackNotYetLoadedIsOfferedAgain() {
        assertTrue(ResourcePackListener.shouldOffer(HASH, HASH_HEX, Status.DECLINED));
        assertTrue(ResourcePackListener.shouldOffer(HASH, HASH_HEX, Status.FAILED_DOWNLOAD));
        assertTrue(ResourcePackListener.shouldOffer(HASH, HASH_HEX, Status.ACCEPTED));
    }

    @Test
    void hashlessSendAlwaysOffers() {
        // Without a hash there is nothing to compare, so the offer is the only option.
        assertTrue(ResourcePackListener.shouldOffer(null, HASH_HEX, Status.SUCCESSFULLY_LOADED));
    }

    @Test
    void packIdIsStableAcrossInstances() {
        UUID expected = UUID.nameUUIDFromBytes("com.clarkson.sot:resource-pack".getBytes(StandardCharsets.UTF_8));

        assertEquals(expected, ResourcePackListener.PACK_ID,
                "Derived from a fixed name so a re-send after reload replaces, not stacks, the pack");
    }
}
