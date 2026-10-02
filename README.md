# Unleashed Lead

Bedrock Edition's leads on Java Edition, for Forge 1.20.1. Also backports the lead changes from Java 1.21 to 1.21.6 ("Chase the Skies").

- **Rope:** every lead is drawn as a simulated rope instead of vanilla's leash line. It sags, sways, bounces, pays out slack, drapes over fences and wraps around posts.
- **Physics:** leashed mobs are pulled by an elastic lead. A mob hanging from a knot over a drop swings like a pendulum.
- **1.21.6 rules:** leashed boats and rafts, mob-to-mob leads, shears that cut leads, the 12-block snap distance, the string recipe and the new lead sounds.
- **Fence-to-fence ropes:** tie a lead from one fence to another. On a Valkyrien Skies ship the rope moors the ship: it holds it back, several ropes hold it better, and it snaps when pulled too hard.

- **Mod id:** `unleashedlead`
- **Package:** `net.camacraft.unleashedlead`
- **Author:** Camawama
- **Licence:** GPL-3.0 (see `LICENSE.txt`, the same licence as Camafy). To change it, edit `mod_license` in `gradle.properties`, replace `LICENSE.txt`, and update this line.
- **Requires:** Minecraft 1.20.1, Forge 47.4 or later, and [Camafy](../../CAMAFY) 0.1.7 or later (below 0.2).
- **Sides:** both. The server runs the leash physics and rules. The client draws the rope. Both sides need the mod.

## What it does

### The rope (client)
Each lead is a chain of points solved with position-based dynamics: Verlet integration, distance constraints and long-range tethers to both ends.

- **Ends:** one end is pinned to the holder's hand or the knot, the other to the leashed entity's collar.
- **Gravity:** the rope sags along local gravity, as hard as that gravity pulls: a sixth as fast on the Moon, and not at all in VS Genesis's space or an Ad Astra orbit, where a slack rope floats in a sideways bow. Under Gravity Unbound it follows the rotated gravity of both ends.
- **Collisions:** the rope drapes over blocks. It collides with block collision shapes, but uses the visible outline for fences and walls, whose collision box is 1.5 blocks tall. A rope bent around a post takes the extra length it needs instead of fighting the block.
- **Slack:** when the ends are closer than the lead's free length (5 blocks), the spare rope hangs as slack. It pays out and reels in smoothly as the distance changes.
- **Look:** the rope is twice vanilla 1.20.1's thickness (Bedrock's and Java 1.21.6's look), with a square cross-section. It keeps vanilla's two-tone brown stripes, shades each face, and takes block and sky light from each point along it.
- **Smooth at any frame rate:** the simulation runs at 20 ticks a second and is interpolated between ticks. Each frame, the rope is bent so its ends sit exactly on the hand and the collar.
- **Cost:** only ropes near the camera are simulated (by default the 64 nearest within 48 blocks). Ropes further away are drawn as a fixed hanging curve, and ropes beyond 96 blocks are not drawn.
- **Culling:** ropes are culled by their own bounds, so a rope stays visible while it crosses the screen with both ends off it.
- **Replaces other lead renderers:** vanilla's call to `MobRenderer.renderLeash` is skipped (the call, not the method, so mods that scale the leash, such as Pehkui, keep their pose stack balanced). With Vanilla Backport installed, its lead renderer is switched off too.

### The physics (server)
Distances are measured between the centres of the two entities' bounding boxes, as Bedrock and Java 1.21.6 do.

- **Within the free length (5 blocks):** the lead does nothing. Walking mobs follow their holder as in vanilla.
- **Past the free length:** a spring pulls the entity toward the holder, harder the further it is stretched (capped). A damper removes part of the entity's speed away from the holder, relative to the holder's own motion, so a moving holder drags it along. Speed across the rope is never touched. That is what lets a mob hanging from a knot swing like a pendulum, and a boat arc around its holder.
- **Past the snap distance (12 blocks):** the lead snaps (Bedrock and Java 1.21.6; vanilla 1.20.1 snaps at 10). The lead drops, and the snap sound plays at the holder.
- **No fall damage while held:** a taut lead carrying an entity resets its fall distance, so hanging or being lowered on a lead never builds up fall damage.
- **Dragged entities face the holder** (1.21.6). A boat turns its bow toward its holder.
- **Chains of mobs pull both ways** (`mobChains`). A lead between two mobs pulls whichever end is further from what moves the chain, so a chain can be dragged by any of its mobs.
  - What moves a chain: a mob held by a player, a fence knot or a boat; a mob the lead can't move at all (another mod moves it, such as Physgun Unbound's grab or an MKW kart; a sitting pet; a ride a player steers; a mob with no AI; a mob riding something).
  - Each lead between two mobs counts how many leads each end is from the nearest of those. The nearer end stays put for that lead, and the further end gets exactly the pull a mob gets from a player: the same spring, damper and cap, against the near end's own motion. It also walks after the near end, and the near end doesn't walk back toward it. Grab the bottom mob of a chain and drag it: its holder follows, and that one's holder follows it.
  - Two mobs equally far from anything that moves them (a free chain wandering, or a tug of war) pull each other, shared by mass (Camafy's weights): a chicken tied to a pig moves about fifty times as much as the pig.
  - Players, knots and boats holding a lead are never pulled by it. A chain held by a player at its top works exactly as before.
  - Which mobs a mob holds is read from each lead's tick (a holder doesn't know what it holds), so a chain is seen as it was over the last two ticks.
- **Boats a player steers are not pulled.** In Bedrock and Java 1.21.6 a lead has no effect on them, though it still snaps at the snap distance. The option `pullPlayerDrivenBoats` turns the pull on; the force is then applied on the driving player's client, which is where the boat is simulated.
- **World space and rotated gravity:** every force goes through Camafy's gravity bridge, so pulls are correct under Gravity Unbound. Every holder position goes through Camafy's Valkyrien Skies bridge, so a knot on a ship's fence works.
- **Resized mobs (Pehkui, bundled in Genesis):** a shrunk or enlarged mob leads like any other.
  - Pehkui scales how far a mob's own velocity moves it (a pig at 1/16 moves 1/16 of it). The pull makes up for that across the ground (Camafy's `EntityScale.motion`), so a tiny pig keeps up with its holder (the pull reaches 0.6 blocks a tick, faster than sprinting) instead of falling behind until the lead snaps. Up and down it does not: Pehkui scales a mob's fall too, and the lead lifts a resized mob against its own weight exactly as it lifts a normal one, so a tiny pig is dragged along the ground rather than flown like a kite. Where it weighs nothing (in space) there is no weight to lift, and the pull works in real blocks on every axis.
  - Distances are measured from an enlarged body, not its centre: the free length and the snap distance are the lead beyond where the body would reach at normal size. A pig grown to 4 held at its side has its whole lead to use, and at normal size or smaller nothing changes (still centre to centre).
  - The rope meets the mob at its resized collar and leaves a resized player's hand at its resized hand (vanilla's hand point is a fixed block below the top of the player's box, which for a player shrunk to 1/16 is under its feet).
  - **The lead is sized for the two things it joins.** Each end has a size: 1 at normal size, 1/16 when Pehkui has shrunk it to 1/16 (read from its box, so vanilla babies and fence knots count as normal).
    - **Thickness follows the smaller end.** The rope's width and the radius it collides with are the normal ones times the smaller end's size: a normal rope is as tall as a pig shrunk to 1/16. A normal player leading a tiny pig, or a tiny player leading a normal pig, holds a thin cord. Two players grown to 4 share a rope four times as thick.
    - **Lengths follow the larger end, never above normal.** The free length, the snap distance, the rope's slack and segment spacing, how fast slack pays out, how fast the rope falls (so it sags and swings like a normal rope, only smaller), how close a mob walks up to its holder, and the distance at which a horse or camel reacts to its lead are the normal ones times the larger end's size, capped at 1. A player and a pig both at 1/16 share a lead at 1/16: 5/16 of a block free, snapping past 12/16. If either end is normal-sized, or the lead is tied to a fence, the lengths stay normal: the lead has to reach around the bigger body and keep up with the faster mover (a tiny pig on a 5/16 lead couldn't reach the ground from a fence knot, nor keep up with a normal player). An enlarged end doesn't make the lead longer; it is measured from its body instead (above).
    - The pull's spring needs no scaling (the stretch is already small), and its cap (`maxPull`) stays in real blocks per tick, so a tiny pig held by a tiny rider on a horse can still keep up.
    - The fence knot is drawn as it always is: it is sized to the fence post it wraps.
    - A lead between two normal-sized ends is exactly as before: every length is multiplied by exactly 1.

### Fence-to-fence ropes and mooring ships
Vanilla only ties mobs to fences. Unleashed Lead also ties a lead between two fences.

- **Tying one:** use a lead on a fence. A knot appears and you carry the rope's free end (one lead is used). Use another fence, or its knot, within 12 blocks, and the rope is tied there. It is as long as the distance between the two knots when tied, plus `tethers.slack` (half a block).
- **Taking it back:** using the rope's first fence again takes the lead back off (it drops there). Using a knot with an empty hand takes its ropes into your hand, as it does leads: the end at that knot comes off and the other end stays tied. Sneak-use only ties.
- **More than one:** a fence can hold up to 32 ropes. With a lead in your hand, using a fence always starts a new rope, even if its knot already has ropes or leads (sneak or use an empty hand to take them instead).
- **A carried end** snaps like any lead past 12 blocks, and breaks with a firework boost.
- **Breaking:** shears on either knot cut its ropes. A knot that is broken, loses its fence, or is taken down drops its ropes. Each rope drops one lead. A knot with a rope on it is never cleared away as empty.
- **Between two fences in the world** the rope just hangs, with its slack.
- **On a Valkyrien Skies ship** (ship to world, or ship to ship) the rope holds the ship back:
  - It is slack up to its length. Past it, it pulls along the rope with a spring and a damper, at the knot: a ship tied at its bow swings around the knot.
  - The pull grows with the square root of the ship's mass, so a small boat is held firmly and a big ship stretches the rope further.
  - Between two ships both are pulled, equally and oppositely.
  - Several ropes add up; each carries its own pull and snaps on its own.
  - A rope snaps when its pull passes `tethers.breakingStrength` (1,000,000 N). A small boat's engines can't do that; a big ship ramming away can. The lead drops where the rope broke, with the "Lead snaps" sound.
  - The pull is computed on Valkyrien Skies' physics thread, from the ship's live position and speed, through Camafy's ship force hook.
- **Loading:** ropes are saved with the world. An end whose fence isn't loaded waits and the rope goes slack; nothing is ever loaded to find it. When the fence is back, its knot is found or made again, as a mob tied there would.
- **Minigames:** tying, taking and cutting ropes asks the minigame host's `MatchPolicies.mayAlter` for the fence.

### Bedrock and Java 1.21 to 1.21.6 parity
| Feature | Source |
|---|---|
| Boats, chest boats and bamboo rafts can be leashed, saved with the world and synced to clients | Java 1.21, Bedrock |
| Sneak-use a mob while holding leashed mobs to tie them to it. Nothing is tied to itself, nothing that would snap at once, no loops | Java 1.21.6 (25w16a) |
| Shears cut an entity's lead and every lead tied to it. They cost 1 durability and the leads drop. A lead another player holds is never cut by hand | Java 1.21.6, Bedrock 1.21.90 |
| A dispenser with shears cuts the leads of an entity in front of it | Java 1.21.6 |
| Using a lead on an entity tied to a knot or a mob takes it over, and the old lead drops. Entities another player holds can't be taken | Java 1.21.6 |
| Using a lead on a mob another mob holds takes the whole chain: the mob is yours and the leads up the chain turn to follow it; nothing drops (on a chain you hold by its other end, your lead just moves). A chain tied to a fence or held by another player is taken over as above | Unleashed Lead (`mobChains`) |
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
| `freeLength` | `5.0` | Blocks before the lead pulls (on a normal-sized lead: times the lead's size when both ends are small). |
| `snapDistance` | `12.0` | Blocks at which the lead snaps (likewise). |
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
| `mobChains` | `true` |
| `shearsSnip` | `true` |
| `dispenserShears` | `true` |
| `knotBreaksWithFence` | `true` |
| `knotRules` | `true` |
| `fireworkBreaksLeads` | `true` |
| `releashTakesOver` | `true` |
| `leadSounds` | `true` |
| `neverLeash` | `[]` |

`neverLeash` lists entities that can never be leashed. Entries can be ids, `#tags`, wildcards or `/regex/`.

**`tethers`** (fence-to-fence ropes)

| Key | Default | What it does |
|---|---|---|
| `enabled` | `true` | Leads can be tied from fence to fence, and those ropes pull on ships. False: no new ropes; existing ones pull on nothing, and snap once stretched `snapDistance - freeLength` (7) blocks past their length. |
| `stiffness` | `20000.0` | Pull per block of stretch, in newtons, on a 1-tonne ship. It grows with the square root of the ship's mass: a 4.5 t raft gets about 42,000 N per block, a 100 t ship 200,000, a 1,000 t ship 630,000. |
| `damping` | `0.5` | How much the rope resists being stretched faster, as a share of critical damping. 0 is a bouncy spring, 1 doesn't bounce. |
| `breakingStrength` | `1000000.0` | Pull, in newtons, at which a rope snaps. |
| `slack` | `0.5` | Blocks of rope added to the distance between the fences when a rope is tied. |

### `config/unleashedlead-client.toml`
**`rope`**

| Key | Default | What it does |
|---|---|---|
| `enabled` | `true` | Draw the simulated rope. False draws vanilla's leash line for mobs, and a thin fixed curve for boats. |
| `thickness` | `0.05` | Rope width in blocks, for a normal-sized lead (times the smaller end's size). |
| `segmentLength` | `0.3` | Length of one simulated segment, for a normal-sized lead (times the lead's length scale). |
| `maxPoints` | `40` | Most points in one rope. |
| `collisions` | `true` | Drape over blocks. |
| `gravity` | `0.05` | How hard the rope sags under ordinary gravity (scaled by the local gravity: none in space). |
| `damping` | `0.06` | How fast the rope's sway dies down. |
| `slack` | `0.35` | Share of the unused free length that hangs as slack. |
| `simulationDistance` | `48` | Ropes further than this are drawn as a fixed curve. |
| `renderDistance` | `96` | Ropes further than this are not drawn. |
| `maxSimulated` | `64` | Most ropes simulated at once. |
| `color` | `#7F664C` | Rope colour. |
| `stripeShade` | `0.7` | Brightness of every other stripe. |

## Compatibility
- **Camafy (required, 0.1.7+):** gravity (`compat.gravity.Gravity`), entity size and Pehkui's motion scale (`compat.scale.EntityScale`), Valkyrien Skies (`compat.vs.Ships` / `ShipRef`, never VS classes directly), physics ownership (`physics.PhysicsOwnership`), minigame rules (`minigame.MatchPolicies`), networking (`network.CamafyChannel`), config helpers (`ConfigUtil`, `IdMatcher`), colour parsing, mod detection (`ModPresence`), logging (`LogOnce`).
- **Gravity Unbound:** leash forces are applied in world axes and converted into each entity's movement frame. The rope sags along the average gravity of its two ends. Collar and hand points follow each entity's render frame.
- **Valkyrien Skies:** a knot on a ship's fence lives in shipyard coordinates. Every distance, search, pull and rope end is taken to world space first, so a mob tied to a ship's fence is dragged along with the ship. This works with or without Vanilla Backport. The rope does not collide with ship blocks.
  - Knots on ships are found through the level's own entity events. Vanilla's search around a fence can't find them, because Valkyrien Skies turns entity searches in the shipyard into searches around the ship in the world, and a second knot would be made on the same fence.
  - Fence-to-fence ropes on ships pull through Camafy's one ship force hook (`ShipForces.registerHook`), on the physics thread. Unleashed Lead never names a Valkyrien Skies class.
- **Full Stop:** a rope catching or jerking an entity must not read as a crash. While a lead is taut, Unleashed Lead places a short `G_FORCE` claim on the entity, and on a boat's passengers. It deliberately claims nothing else, so a mob swung into a wall still takes Full Stop's impact damage. It never sets `hasImpulse` and never marks discontinuities. Full Stop replaces fall damage, and it already exempts leashed mobs being lowered.
- **MKW Mobility / Grapple Unbound / any mod claiming `MOTION`:** such entities can't be leashed. If one is already leashed, the lead never pulls it and never walks it. With Vanilla Backport installed, whatever Vanilla Backport's tick does to their velocity is also undone.
- **Grapple Unbound (formerly GrappleVS):** it no longer has a lead overhaul (removed), so Unleashed Lead is the only lead physics and rope.
- **Game Start and other minigame hosts:** `MatchPolicies.canHarm` is asked before a player leashes, re-ties or cuts the leads of another player's pet, or of something another player leads.
- **Pehkui (Virtuoel; bundled in Genesis):** optional and never compiled against. Unleashed Lead reads Pehkui's motion scale through Camafy, and everything else from vanilla (Pehkui scales `getDimensions`, eye height and the box). See the physics section. Tested with Pehkui 3.8.2, the version Genesis 0.7.3 nests.
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
./gradlew runGameTestServer -PshipTests --no-daemon    # the same, plus real Valkyrien Skies ships on ropes
./gradlew runGameTestServer -PshipTests -PvsBackend=classic --no-daemon   # ... on VS's classic physics backend
./gradlew runGameTestServer -Ppehkui --no-daemon       # the same, plus leads on mobs Pehkui has resized
./gradlew runGameTestServer -PadAstra --no-daemon      # the same, plus a smoke test in Ad Astra's real orbit and Moon
```
- The gametest run uses its own `run-gametest/` directory. Each run starts from a fresh, flat, fixed-seed world.
- `-PvanillaBackport` puts Vanilla Backport, its Platform library and MixinExtras on the dev runtime from Modrinth's maven. The mod never compiles against them.
- `ChainLeashTests` (chains of mobs) run in their own batch, `chains`, so adding them didn't move the other suites' plots.
- `-PshipTests` puts Valkyrien Skies 2.4.11 and Kotlin for Forge on the dev runtime (Modrinth's maven, as Camafy does) and compiles `src/gametest`, whose tests build real ships with VS's own assembler. The release jar never contains them. The test world starts on VS's PhysX backend; `-PvsBackend=classic|physx|jolt` picks another.
- `-Ppehkui` puts Pehkui 3.8.2 on the dev runtime (Modrinth's maven) and compiles `src/pehkuiTest`, whose nine tests resize pigs and players through Pehkui's API: a 1/16 pig and a 4x pig led by a walking player, a 1/16 pig led by a 1/16 player, the pull per tick at 1, 1/16 and 4, snapping past 12, the lead's size for each pair of ends, a 1/16 pair's lead snapping past 12/16 and pulling past 5/16, collar and hand points, and Camafy's weights. The release jar never contains them. The run sets `mixin.env.ignoreRequired`, because two of Pehkui's container-reach mixins can't apply to ForgeGradle's recompiled Minecraft (they apply in a real game); the log still lists every mixin that failed. It combines with `-PvanillaBackport`.
- `-PadAstra` puts Ad Astra 1.15.21 and its libraries (Botarium, Resourceful Lib, Resourceful Config) on the dev runtime (Modrinth's maven) and compiles `src/adAstraTest`: pigs in Ad Astra's own `earth_orbit` and `moon`, where Camafy must read 0 and a sixth, the orbit pig must float and the Moon pig fall as Ad Astra drops it. The default suite covers zero gravity with the no-gravity flag (how VS Genesis does it) and the Forge gravity attribute (`ZeroGravityTests`).
- `TESTING_CHECKLIST.md` lists what needs checking in game.
