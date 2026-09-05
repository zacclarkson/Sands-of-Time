package com.clarkson.sot.main;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * The {@code resource-pack} section of {@code config.yml}: where clients fetch the Sands of Time
 * texture pack from, and how it is offered to them.
 *
 * <p>The pack is pushed by the plugin (see {@code ResourcePackListener}) rather than through
 * {@code server.properties}, because the plugin can send the zip's <em>current</em> SHA-1 with it.
 * The hash is what makes a client re-download: without one, a client that already holds a pack
 * for the same URL keeps its cached copy forever (Paper warns about exactly this at startup). And
 * because the hash lives in the plugin, a hot-swapped zip goes live with a plugin reload — no
 * server restart, nobody kicked.
 *
 * <p>{@code sha1} is optional. When it is blank the plugin downloads the zip once at enable and
 * hashes it, so the usual deploy is "replace the zip, reload the plugin" with nothing else to keep
 * in sync. Set it explicitly only if the pack host is not reachable from the server itself.
 *
 * @param url      where clients download the pack from, or {@code null} when no pack is configured.
 * @param sha1Hex  the zip's SHA-1 as 40 hex characters, or {@code null} to hash the download at enable.
 * @param required whether players who decline the pack are disconnected.
 * @param prompt   the message shown in the client's download prompt.
 */
public record ResourcePackSettings(@Nullable String url, @Nullable String sha1Hex, boolean required,
                                   @NotNull String prompt) {

    /** Config path of the section this record is read from. */
    public static final String PATH = "resource-pack";

    /** Prompt used when the config leaves it blank. */
    public static final String DEFAULT_PROMPT =
            "Sands of Time uses a small texture pack for its coins and vault keys.";

    /** Settings meaning "no pack configured": nothing is ever sent to clients. */
    public static final ResourcePackSettings DISABLED =
            new ResourcePackSettings(null, null, false, DEFAULT_PROMPT);

    private static final int SHA1_HEX_LENGTH = 40;

    public ResourcePackSettings {
        Objects.requireNonNull(prompt, "prompt cannot be null");
        if (sha1Hex != null) {
            sha1Hex = sha1Hex.toLowerCase();
            parseSha1(sha1Hex); // validates; throws on garbage
        }
    }

    /** Whether a pack URL is configured at all. */
    public boolean isEnabled() {
        return url != null;
    }

    /** The configured hash as bytes, or {@code null} when it is to be computed from the download. */
    public byte @Nullable [] sha1Bytes() {
        return sha1Hex == null ? null : parseSha1(sha1Hex);
    }

    /**
     * Reads the section at {@code path} from {@code root}.
     *
     * <p>An absent section or a blank {@code url} means "no pack" and returns {@link #DISABLED}
     * quietly — a server without a pack is normal. Anything present but unusable (a URL that is not
     * http(s), a {@code sha1} that is not 40 hex characters) logs a warning naming the problem and
     * either disables the pack or falls back to hashing the download, whichever keeps the most of
     * what the operator asked for.
     */
    @NotNull
    public static ResourcePackSettings read(@NotNull ConfigurationSection root, @NotNull String path,
                                            @NotNull Logger log) {
        Objects.requireNonNull(root, "root configuration cannot be null");
        Objects.requireNonNull(path, "path cannot be null");
        Objects.requireNonNull(log, "log cannot be null");

        ConfigurationSection section = root.getConfigurationSection(path);
        if (section == null) {
            return DISABLED;
        }

        String url = blankToNull(section.getString("url"));
        if (url == null) {
            return DISABLED;
        }
        if (!isHttpUrl(url)) {
            log.warning("config.yml: '" + path + ".url' is not an http(s) URL (got '" + url
                    + "'); no resource pack will be sent.");
            return DISABLED;
        }

        String sha1 = blankToNull(section.getString("sha1"));
        if (sha1 != null) {
            try {
                parseSha1(sha1);
            } catch (IllegalArgumentException e) {
                log.warning("config.yml: '" + path + ".sha1' is not a 40-character hex SHA-1 (got '"
                        + sha1 + "'); the pack will be hashed from its download instead.");
                sha1 = null;
            }
        }

        boolean required = section.getBoolean("required", false);
        String prompt = blankToNull(section.getString("prompt"));
        return new ResourcePackSettings(url, sha1, required, prompt != null ? prompt : DEFAULT_PROMPT);
    }

    /**
     * Parses a SHA-1 written as 40 hex characters (either case) into its 20 bytes.
     *
     * @throws IllegalArgumentException if the text is not exactly 40 hex characters.
     */
    public static byte @NotNull [] parseSha1(@NotNull String hex) {
        Objects.requireNonNull(hex, "hex cannot be null");
        String trimmed = hex.trim();
        if (trimmed.length() != SHA1_HEX_LENGTH) {
            throw new IllegalArgumentException("SHA-1 must be " + SHA1_HEX_LENGTH
                    + " hex characters, got " + trimmed.length());
        }
        try {
            return HexFormat.of().parseHex(trimmed);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("SHA-1 contains non-hex characters: '" + trimmed + "'", e);
        }
    }

    /** Formats hash bytes as lower-case hex, the form Minecraft and {@code sha1sum} both print. */
    @NotNull
    public static String toHex(byte @NotNull [] bytes) {
        return HexFormat.of().formatHex(Objects.requireNonNull(bytes, "bytes cannot be null"));
    }

    /** The SHA-1 of {@code bytes} — what the client compares its cached pack against. */
    public static byte @NotNull [] sha1Of(byte @NotNull [] bytes) {
        Objects.requireNonNull(bytes, "bytes cannot be null");
        try {
            return MessageDigest.getInstance("SHA-1").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM has no SHA-1 implementation", e);
        }
    }

    private static boolean isHttpUrl(@NotNull String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            return uri.getHost() != null && scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Nullable
    private static String blankToNull(@Nullable String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
