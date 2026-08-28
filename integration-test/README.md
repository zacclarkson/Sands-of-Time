# Integration / E2E testing

Unit tests (`mvn test`, via MockBukkit) cover game logic and simulated events in-memory. This
harness is the next tier: it runs the **real plugin on a real Paper server** and drives a headless
bot that actually joins and interacts — catching wiring, registration, world, and WorldEdit issues
that a mocked server cannot.

Intended to run on the homelab (or any Docker host).

## Pieces

- **`docker-compose.yml`** — a Paper 1.21.1 server (`itzg/minecraft-server`) with `online-mode=false`,
  the freshly built plugin jar mounted into `plugins/`, and a Node service that runs the bot.
- **`bot/smoke-test.js`** — a [Mineflayer](https://github.com/PrismarineJS/mineflayer) bot that
  connects, confirms it can join and act, and exits non-zero on failure/timeout.

## Run it

From the repo root:

```bash
mvn package                    # build target/SoT-1.0-SNAPSHOT.jar
cd integration-test
docker compose up -d server    # boot the server with the plugin
docker compose logs -f server  # wait until you see the world load and SoT enable
docker compose run --rm bot    # run the bot scenario (exit 0 = pass)
docker compose down            # tear down
```

The `data/` directory (server world + config) is created on first run and gitignored.

## Assertion strategy (the one prerequisite)

The smoke test only proves the pipeline works — server boots, plugin enables, bot joins and acts.
To assert on **actual game mechanics**, the bot needs to observe internal plugin state, which the
Bukkit protocol does not expose. In order of preference:

1. **Add a debug command** (recommended first task), e.g. `/sot debug score <player>` /
   `/sot debug timer <team>`, that prints state to chat. The bot runs it with `bot.chat('/sot ...')`
   and parses the reply in the `message` handler. Requires auto-op (already set via `OPS: Tester`).
2. Read a **scoreboard objective** the plugin already updates (bot can read the sidebar).
3. **RCON** + the same debug command.

Once (1) exists, extend `smoke-test.js` with scenarios such as:

- spawn a coin near the bot → bot walks over it → assert unbanked score increased;
- give the bot sand → right-click the sacrifice point → assert the team timer gained 10s;
- kill the bot while `ALIVE_IN_DUNGEON` → assert it enters the death cage and drops its coins.

## Notes

- Keep the server `VERSION` in `docker-compose.yml` in sync with the plugin's `paper-api` version
  (currently **1.21.1**).
- Mineflayer must support the server version; bump `mineflayer` in `bot/package.json` if you raise
  the server version and the bot fails to connect.
- This tier is heavier and slower than unit tests — run it on demand (or as a separate, non-blocking
  CI job), not on every push.
