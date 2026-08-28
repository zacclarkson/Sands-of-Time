'use strict';

/**
 * End-to-end smoke test: connect a headless Mineflayer bot to a real Paper server that is running
 * the SoT plugin, confirm the bot can join and act in the world, and exit non-zero on any failure
 * or timeout so this can be wired into CI later.
 *
 * This is the SCAFFOLD. It proves the server + plugin + bot pipeline works. To assert on real game
 * mechanics (score after picking up a coin, timer after sacrificing sand, etc.) the plugin needs a
 * way to expose internal state to the bot — see integration-test/README.md ("Assertion strategy").
 * Once a debug command exists, add scenario steps that run it via bot.chat('/sot debug ...') and
 * parse the response in the 'message' handler below.
 */

const mineflayer = require('mineflayer');

const HOST = process.env.MC_HOST || 'localhost';
const PORT = parseInt(process.env.MC_PORT || '25565', 10);
const USERNAME = process.env.MC_USERNAME || 'Tester';
const VERSION = process.env.MC_VERSION || '1.21.1';
const OVERALL_TIMEOUT_MS = 90_000;

function fail(msg, err) {
  console.error(`[smoke-test] FAIL: ${msg}${err ? ` — ${err.message || err}` : ''}`);
  process.exit(1);
}

function ok(msg) {
  console.log(`[smoke-test] OK: ${msg}`);
}

const bot = mineflayer.createBot({
  host: HOST,
  port: PORT,
  username: USERNAME,
  version: VERSION,
  auth: 'offline',
});

// Hard ceiling so a hung connection fails the job instead of hanging forever.
const guard = setTimeout(() => fail('overall timeout reached before scenario completed'), OVERALL_TIMEOUT_MS);
guard.unref?.();

bot.on('error', (err) => fail('bot error', err));
bot.on('kicked', (reason) => fail(`kicked: ${reason}`));

bot.once('spawn', async () => {
  ok(`spawned into the world at ${JSON.stringify(bot.entity.position)}`);
  try {
    // Basic liveness: the bot is in a loaded world and can observe game state.
    await bot.waitForTicks(20);
    ok(`game time is ${bot.time.timeOfDay}, ${Object.keys(bot.players).length} player(s) online`);

    // Example interaction: jump, to confirm the bot can act and the server responds.
    bot.setControlState('jump', true);
    await bot.waitForTicks(5);
    bot.setControlState('jump', false);
    ok('performed a movement action');

    // --- Add real SoT scenarios here once a debug command exists, e.g.:
    //   bot.chat('/sot debug score ' + USERNAME);
    //   const line = await waitForChat(/unbanked=(\d+)/);
    //   assert(...);

    clearTimeout(guard);
    ok('smoke test passed');
    bot.quit();
    process.exit(0);
  } catch (err) {
    fail('scenario step threw', err);
  }
});
