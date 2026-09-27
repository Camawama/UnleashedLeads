# Unleashed Lead

Bedrock Edition's leads on Java Edition, for Forge 1.20.1. Also backports the lead changes from Java 1.21 to 1.21.6 ("Chase the Skies").

- **Rope:** every lead is drawn as a simulated rope instead of vanilla's leash line. It sags, sways, bounces, pays out slack, drapes over fences and wraps around posts.
- **Physics:** leashed mobs are pulled by an elastic lead. A mob hanging from a knot over a drop swings like a pendulum.
- **1.21.6 rules:** leashed boats and rafts, mob-to-mob leads, shears that cut leads, the 12-block snap distance, the string recipe and the new lead sounds.

- **Mod id:** `unleashedlead`
- **Package:** `net.camacraft.unleashedlead`
- **Author:** Camawama
- **Licence:** GPL-3.0 (see `LICENSE.txt`, the same licence as Camafy). To change it, edit `mod_license` in `gradle.properties`, replace `LICENSE.txt`, and update this line.
- **Requires:** Minecraft 1.20.1, Forge 47.4 or later, and [Camafy](../../CAMAFY) 0.1.1 or later (below 0.2).
- **Sides:** both. The server runs the leash physics and rules. The client draws the rope. Both sides need the mod.

## What it does

### The rope (client)
Each lead is a chain of points solved with position-based dynamics: Verlet integration, distance constraints and long-range tethers to both ends.

- **Ends:** one end is pinned to the holder's hand or the knot, the other to the leashed entity's collar.
- **Gravity:** the rope sags along local gravity. Under Gravity Unbound it follows the rotated gravity of both ends.
- **Collisions:** the rope drapes over blocks. It collides with block collision shapes, but uses the visible outline for fences and walls, whose collision box is 1.5 blocks tall. A rope bent around a post takes the extra length it needs instead of fighting the block.
- **Slack:** when the ends are closer than the lead's free length (5 blocks), the spare rope hangs as slack. It pays out and reels in smoothly as the distance changes.
- **Look:** the rope is twice vanilla 1.20.1's thickness (Bedrock's and Java 1.21.6's look), with a square cross-section. It keeps vanilla's two-tone brown stripes, shades each face, and takes block and sky light from each point along it.
- **Smooth at any frame rate:** the simulation runs at 20 ticks a second and is interpolated between ticks. Each frame, the rope is bent so its ends sit exactly on the hand and the collar.
- **Cost:** only ropes near the camera are simulated (by default the 64 nearest within 48 blocks). Ropes further away are drawn as a fixed hanging curve, and ropes beyond 96 blocks are not drawn.
- **Culling:** ropes are culled by their own bounds, so a rope stays visible while it crosses the screen with both ends off it.
- **Replaces other lead renderers:** vanilla's `MobRenderer.renderLeash` is cancelled. With Vanilla Backport installed, its lead renderer is switched off too.

### The physics (server)
Distances are measured between the centres of the two entities' bounding boxes, as Bedrock and Java 1.21.6 do.

- **Within the free length (5 blocks):** the lead does nothing. Walking mobs follow their holder as in vanilla.
- **Past the free length:** a spring pulls the entity toward the holder, harder the further it is stretched (capped). A damper removes part of the entity's speed away from the holder, relative to the holder's own motion, so a moving holder drags it along. Speed across the rope is never touched. That is what lets a mob hanging from a knot swing like a pendulum, and a boat arc around its holder.
- **Past the snap distance (12 blocks):** the lead snaps (Bedrock and Java 1.21.6; vanilla 1.20.1 snaps at 10). The lead drops, and the snap sound plays at the holder.
- **No fall damage while held:** a taut lead carrying an entity resets its fall distance, so hanging or being lowered on a lead never builds up fall damage.
- **Dragged entities face the holder** (1.21.6). A boat turns its bow toward its holder.
- **Boats a player steers are not pulled.** In Bedrock and Java 1.21.6 a lead has no effect on them, though it still snaps at the snap distance. The option `pullPlayerDrivenBoats` turns the pull on; the force is then applied on the driving player's client, which is where the boat is simulated.
- **World space and rotated gravity:** every force goes through Camafy's gravity bridge, so pulls are correct under Gravity Unbound. Every holder position goes through Camafy's Valkyrien Skies bridge, so a knot on a ship's fence works.

### Bedrock and Java 1.21 to 1.21.6 parity
| Feature | Source |
|---|---|
| Boats, chest boats and bamboo rafts can be leashed, saved with the world and synced to clients | Java 1.21, Bedrock |
| Sneak-use a mob while holding leashed mobs to tie them to it. Nothing is tied to itself, nothing that would snap at once, no loops | Java 1.21.6 (25w16a) |
| Shears cut an entity's lead and every lead tied to it. They cost 1 durability and the leads drop. A lead another player holds is never cut by hand | Java 1.21.6, Bedrock 1.21.90 |
| A dispenser with shears cuts the leads of an entity in front of it | Java 1.21.6 |
| Using a lead on an entity tied to a knot or a mob takes it over, and the old lead drops. Entities another player holds can't be taken | Java 1.21.6 |
| Using a knot or its fence ties your held leads to it (boats too). With nothing to tie, you take the knot's leads. Sneak-use only ties. An empty knot disappears | Java 1.21.6 |
| A knot breaks the moment its fence is removed | Bedrock 1.21.100 |
| A firework boost while gliding breaks the leads you hold | Java 1.21.6 |
| Free length 5 and snap distance 12, measured centre to centre | Java 1.21.6, Bedrock 1.21.90 |
| Elastic lead physics; a hanging mob swings | Bedrock, Java 1.21.6 |
| Dragged entities turn toward the holder | Java 1.21.6 |
| Leads twice as thick | Java 1.21.6 (matches Bedrock) |
| Recipe: 5 string (A1, B1, A2, B2, C3) make 2 leads | Java 1.21.6 (25w18a) |
| Sounds "Lead tied", "Lead untied" and "Lead snaps" | Java 1.21.6 (25w21a) |

Wall knots are deliberately not added: Bedrock removed them.

The lead sounds use vanilla 1.20.1's own leash-knot audio, through `sounds.json`. No sound files are shipped.

## Configuration
### `config/unleashedlead-common.toml`
| Key | Default | What it does |
|---|---|---|
| `recipe.newLeadRecipe` | `true` | Use the 1.21.6 string recipe. False keeps the slimeball recipe. The recipe is a `forge:conditional`, so turning this off never leaves the game without a lead recipe. Takes effect on world load or `/reload`. |

### `serverconfig/unleashedlead-server.toml`
Forge sends this file to every client that joins.

**`physics`**

| Key | Default | What it does |
|---|---|---|
| `enabled` | `true` | Elastic physics. False keeps 1.20.1's velocity kick; the snap distance still applies. |
| `freeLength` | `5.0` | Blocks before the lead pulls. |
| `snapDistance` | `12.0` | Blocks at which the lead snaps. |
| `stiffness` | `0.05` | Spring pull per block of stretch (blocks/tick²). |
| `radialDamping` | `0.5` | Share of outward speed a taut lead removes each tick. 1 is unstretchable, lower is bouncier. |
| `maxPull` | `0.6` | Cap on the speed the lead adds in one tick. |
| `noFallDamageWhileHeld` | `true` | A taut lead resets fall distance. |
| `faceHolder` | `true` | Dragged entities turn toward the holder. |
| `pullPlayerDrivenBoats` | `false` | See above. |

**`features`**

| Key | Default |
|---|---|
| `leashBoats` | `true` |
| `mobToMob` | `true` |
| `shearsSnip` | `true` |
| `dispenserShears` | `true` |
| `knotBreaksWithFence` | `true` |
| `knotRules` | `true` |
| `fireworkBreaksLeads` | `true` |
| `releashTakesOver` | `true` |
| `leadSounds` | `true` |
| `neverLeash` | `[]` |

`neverLeash` lists entities that can never be leashed. Entries can be ids, `#tags`, wildcards or `/regex/`.

### `config/unleashedlead-client.toml`
**`rope`**

| Key | Default | What it does |
|---|---|---|
| `enabled` | `true` | Draw the simulated rope. False draws vanilla's leash line for mobs, and a thin fixed curve for boats. |
| `thickness` | `0.05` | Rope width in blocks. |
| `segmentLength` | `0.3` | Length of one simulated segment. |
| `maxPoints` | `40` | Most points in one rope. |
| `collisions` | `true` | Drape over blocks. |
| `gravity` | `0.05` | How hard the rope sags. |
| `damping` | `0.06` | How fast the rope's sway dies down. |
| `slack` | `0.35` | Share of the unused free length that hangs as slack. |
| `simulationDistance` | `48` | Ropes further than this are drawn as a fixed curve. |
| `renderDistance` | `96` | Ropes further than this are not drawn. |
| `maxSimulated` | `64` | Most ropes simulated at once. |
| `color` | `#7F664C` | Rope colour. |
| `stripeShade` | `0.7` | Brightness of every other stripe. |

## Compatibility
- **Camafy (required):** gravity (`compat.gravity.Gravity`), Valkyrien Skies (`compat.vs.Ships` / `ShipRef`, never VS classes directly), physics ownership (`physics.PhysicsOwnership`), minigame rules (`minigame.MatchPolicies`), networking (`network.CamafyChannel`), config helpers (`ConfigUtil`, `IdMatcher`), colour parsing, mod detection (`ModPresence`), logging (`LogOnce`).
- **Gravity Unbound:** leash forces are applied in world axes and converted into each entity's movement frame. The rope sags along the average gravity of its two ends. Collar and hand points follow each entity's render frame.
- **Valkyrien Skies:** a knot on a ship's fence lives in shipyard coordinates. Every distance, search, pull and rope end is taken to world space first, so a mob tied to a ship's fence is dragged along with the ship. This works with or without Vanilla Backport. The rope does not collide with ship blocks.
- **Full Stop:** a rope catching or jerking an entity must not read as a crash. While a lead is taut, Unleashed Lead places a short `G_FORCE` claim on the entity, and on a boat's passengers. It deliberately claims nothing else, so a mob swung into a wall still takes Full Stop's impact damage. It never sets `hasImpulse` and never marks discontinuities. Full Stop replaces fall damage, and it already exempts leashed mobs being lowered.
- **MKW Mobility / GrappleVS / any mod claiming `MOTION`:** such entities can't be leashed. If one is already leashed, the lead never pulls it and never walks it. With Vanilla Backport installed, whatever Vanilla Backport's tick does to their velocity is also undone.
- **GrappleVS:** its own Bedrock-style "lead overhaul" switches itself off when Unleashed Lead is installed. Unleashed Lead covers what that overhaul did: pendulum physics, a sagging rope attached at the neck and rotated by body yaw, wrapping around corners, and ropes to knots on ships.
- **Game Start and other minigame hosts:** `MatchPolicies.canHarm` is asked before a player leashes, re-ties or cuts the leads of another player's pet, or of something another player leads.
- **Vanilla Backport (ItsBlackGear):** optional; see the next section.

### Vanilla Backport
Both mods can be installed together. Unleashed Lead declares Vanilla Backport as an optional dependency that loads first, detects it at startup, and splits the work as follows.

| Feature | Owner when both are installed |
|---|---|
| Leash data on boats, chest boats and rafts (holder, save data, sync) | Vanilla Backport. Unleashed Lead's boat mixin is not applied; it reads and writes Vanilla Backport's model through a reflection bridge. |
| Happy ghast, quad leash (4 ropes), ghast 10 / 16 distances | Vanilla Backport |
| The leash tick of `PathfinderMob`s and boats | Vanilla Backport (it cancels `PathfinderMob.tickLeash`), with Unleashed Lead's methods plugged into its hooks |
| Single-lead pull (spring, damper, pendulum) | Unleashed Lead, through Vanilla Backport's `vb$checkElasticInteractions` hook |
| Quad-lead physics (ghast carrying a boat or horse) | Vanilla Backport's own, handed back to its interface default |
| Free length / snap distance | Unleashed Lead's config, through `vb$leashElasticDistance` / `vb$leashSnapDistance`. The happy ghast keeps its own values. |
| Distance, home restriction and walking to a holder on a Valkyrien Skies ship | Unleashed Lead (`vb$leashDistanceTo`, `vb$whenLeashedTo`, `vb$closeRangeLeashBehavior`). Without this, Vanilla Backport snaps every lead tied to a ship's fence at once. |
| Drawing every lead, single and quad | Unleashed Lead. Vanilla Backport's `LeashFeatureRenderer` is cancelled while rope rendering is on. |
| Lead on a boat, untie, take-over, mob-to-mob, shears by hand | Unleashed Lead. Its interaction handler runs first (HIGH priority) and cancels the event, so Vanilla Backport's never sees the use. |
| Firework breaks leads | Vanilla Backport (its `leash_drop_connections` option) |
| Dispenser shears, knot and fence rules, tying boats to fences, knot breaks with its fence, lead sounds | Unleashed Lead (Vanilla Backport has none of these) |
| Lead recipe | Unleashed Lead. It loads after Vanilla Backport, so its `data/minecraft/recipes/lead.json` wins and its toggle works. |

With Vanilla Backport installed, Unleashed Lead's `features.*` toggles cover only Unleashed Lead's own handlers. Turning off `mobToMob` or `shearsSnip` lets Vanilla Backport's own versions run.

## Building and testing
```
./gradlew build --no-daemon                            # compile, reobf jar in build/libs
./gradlew runGameTestServer --no-daemon                # headless gametests
./gradlew runGameTestServer -PvanillaBackport --no-daemon   # the same, with Vanilla Backport 1.1.7.10 loaded
```
- The gametest run uses its own `run-gametest/` directory. Each run starts from a fresh, flat, fixed-seed world.
- `-PvanillaBackport` puts Vanilla Backport, its Platform library and MixinExtras on the dev runtime from Modrinth's maven. The mod never compiles against them.
- `TESTING_CHECKLIST.md` lists what needs checking in game.
