package com.clarkson.sot.events;

import com.clarkson.sot.main.ResourcePackSettings;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Offers the Sands of Time resource pack to every player, with the zip's current SHA-1.
 *
 * <p>Why the plugin does this instead of {@code server.properties}: the hash is what makes a client
 * re-download a changed pack, and {@code server.properties} is only read at server start. Here the
 * hash is (re)computed each time the plugin enables — which the deploy pipeline triggers with a
 * {@code plugman reload SoT} after swapping the zip — so a texture change reaches players without a
 * server restart. Once the hash is known, players already online are offered the pack too, and a
 * player whose client already reports that exact pack loaded is left alone, so a plugin reload for
 * an unrelated jar change does not make everyone's screen reload textures.
 *
 * <p>The pack is sent under one fixed {@link #PACK_ID}, so a re-send replaces the previous SoT pack
 * on the client rather than stacking a second copy.
 */
public final class ResourcePackListener implements Listener {

    /** Stable id for the SoT pack on the client; derived, not random, so it survives reloads. */
    public static final UUID PACK_ID = UUID.nameUUIDFromBytes(
            "com.clarkson.sot:resource-pack".getBytes(StandardCharsets.UTF_8));

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);

    private final Plugin plugin;
    private final ResourcePackSettings settings;
    private final Logger log;
    private final Function<URI, byte[]> fetcher;

    /** The hash to send; {@code null} until resolved (or when resolving failed). */
    private volatile byte @Nullable [] hash;
    /** Set when the download-and-hash step failed, so joins fall back to a hashless send. */
    private volatile boolean hashUnavailable;

    public ResourcePackListener(@NotNull Plugin plugin, @NotNull ResourcePackSettings settings) {
        this(plugin, settings, ResourcePackListener::download);
    }

    /** Test seam: {@code fetcher} stands in for the HTTP download of the pack. */
    ResourcePackListener(@NotNull Plugin plugin, @NotNull ResourcePackSettings settings,
                         @NotNull Function<URI, byte[]> fetcher) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.settings = Objects.requireNonNull(settings, "settings cannot be null");
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher cannot be null");
        this.log = plugin.getLogger();
    }

    /**
     * Resolves the pack hash and offers the pack to everyone already online. Call once after
     * registering the listener. Does nothing when no pack is configured.
     */
    public void start() {
        if (!settings.isEnabled()) {
            log.info("No resource pack configured ('" + ResourcePackSettings.PATH
                    + ".url' in config.yml); clients keep vanilla textures.");
            return;
        }
        byte[] configured = settings.sha1Bytes();
        if (configured != null) {
            hash = configured;
            log.info("Resource pack " + settings.url() + " (sha1 " + ResourcePackSettings.toHex(configured)
                    + ", from config.yml)" + (settings.required() ? ", required." : "."));
            pushToOnlinePlayers();
            return;
        }

        URI uri = URI.create(Objects.requireNonNull(settings.url()));
        log.info("Hashing resource pack at " + uri + " ...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            byte[] computed = null;
            Exception failure = null;
            try {
                computed = ResourcePackSettings.sha1Of(fetcher.apply(uri));
            } catch (Exception e) {
                failure = e;
            }
            // Back onto the main thread; skip if the plugin was disabled while we were downloading
            // (scheduling on a disabled plugin throws).
            if (!plugin.isEnabled()) {
                return;
            }
            byte[] result = computed;
            Exception cause = failure;
            Bukkit.getScheduler().runTask(plugin, () -> onHashResolved(result, cause));
        });
    }

    /** The hash currently being sent, or {@code null} if none is known. */
    public byte @Nullable [] currentHash() {
        return hash;
    }

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        if (!settings.isEnabled()) {
            return;
        }
        if (hash == null && !hashUnavailable) {
            return; // Still hashing; pushToOnlinePlayers() will reach this player when it lands.
        }
        offer(event.getPlayer());
    }

    @EventHandler
    public void onResourcePackStatus(@NotNull PlayerResourcePackStatusEvent event) {
        if (!settings.isEnabled() || !PACK_ID.equals(event.getID())) {
            return;
        }
        switch (event.getStatus()) {
            case DECLINED -> log.warning(event.getPlayer().getName() + " declined the resource pack"
                    + (settings.required() ? " and will be disconnected." : "; they will see vanilla textures."));
            case FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL, DISCARDED -> log.warning(
                    event.getPlayer().getName() + "'s client reported " + event.getStatus()
                            + " for the resource pack at " + settings.url()
                            + " -- is that URL reachable from the player's machine?");
            default -> { /* ACCEPTED / DOWNLOADED / SUCCESSFULLY_LOADED are the happy path. */ }
        }
    }

    private void onHashResolved(byte @Nullable [] computed, @Nullable Exception failure) {
        if (computed != null) {
            hash = computed;
            hashUnavailable = false;
            log.info("Resource pack " + settings.url() + " (sha1 " + ResourcePackSettings.toHex(computed)
                    + ")" + (settings.required() ? ", required." : "."));
        } else {
            hash = null;
            hashUnavailable = true;
            log.log(Level.WARNING, "Could not download the resource pack from " + settings.url()
                    + " to hash it; it will be offered without a hash, so clients that already have a"
                    + " pack for that URL keep their cached copy. Set '" + ResourcePackSettings.PATH
                    + ".sha1' in config.yml if the pack host is not reachable from the server.", failure);
        }
        pushToOnlinePlayers();
    }

    private void pushToOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            offer(player);
        }
    }

    private void offer(@NotNull Player player) {
        byte[] current = hash;
        if (!shouldOffer(current, player.getResourcePackHash(), player.getResourcePackStatus())) {
            return;
        }
        player.setResourcePack(PACK_ID, Objects.requireNonNull(settings.url()), current,
                Component.text(settings.prompt()), settings.required());
    }

    /**
     * Whether a player needs the pack (re)sent. A client that already reports the exact same hash
     * successfully loaded is left alone; anything else — no pack yet, a different hash, a hashless
     * send, or a client still mid-download of something — gets the offer.
     */
    static boolean shouldOffer(byte @Nullable [] hashToSend, @Nullable String clientHashHex,
                               @Nullable PlayerResourcePackStatusEvent.Status clientStatus) {
        if (hashToSend == null || clientHashHex == null || clientHashHex.isEmpty()) {
            return true;
        }
        if (clientStatus != PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            return true;
        }
        return !ResourcePackSettings.toHex(hashToSend).equalsIgnoreCase(clientHashHex);
    }

    private static byte[] download(@NotNull URI uri) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(HTTP_TIMEOUT).GET().build();
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + response.statusCode() + " from " + uri);
            }
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException("Download of " + uri + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while downloading " + uri, e);
        }
    }
}
