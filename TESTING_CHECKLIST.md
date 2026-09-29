# Unleashed Lead: in-game testing checklist

The headless gametests (`./gradlew runGameTestServer`, again with `-PvanillaBackport`, with `-PshipTests` for real Valkyrien Skies ships, and with `-Ppehkui` for mobs Pehkui has resized) cover the server rules, the leash physics, the rope solver and fence-to-fence ropes pulling ships. Everything below needs a real client: how the rope looks, multiplayer, and the other mods.

Tick each box as you go. Next to anything that looks wrong, write what you saw and your settings (graphics mode, shaders, render distance).

## 1. Rope look
- [ ] Leash a pig and walk around it at 1, 3, 5, 8 and 11 blocks.
  - Up close the rope hangs in a soft curve, down to the ground when very close.
  - Near 5 blocks it straightens.
  - Past 5 it is taut with a hint of sag.
  - No vanilla leash line is drawn anywhere.
- [ ] The rope attaches at the pig's neck and at your hand (third person, F5), and follows as you turn.
- [ ] The rope attaches at the neck of a horse, a llama, a camel, a wolf and a chicken, and turns with each animal's body.
- [ ] Walk back and forth quickly. The rope sways and bounces a little behind the movement, then settles. It never jitters at rest.
- [ ] Look at it from above, from the side and end-on. It looks like a solid, twice-as-thick rope, with the light and dark vanilla stripes and a lighter top face.
- [ ] Lighting:
  - In a dark cave with a torch, the rope is lit near the torch and dark away from it.
  - Outdoors at night it goes dark.
  - Half under an overhang, each part of the rope takes its own light.
- [ ] Frame rate: at 30 fps and at an uncapped frame rate, the rope stays glued to the hand and the collar while you walk and turn, with no lag or stepping.
- [ ] Culling:
  - Stand so the rope crosses the screen with both the pig and your hand off-screen (look sideways across it). The rope is still drawn.
  - Tie a pig to a knot, walk away until the pig is off-screen but the rope is not. The rope stays.
- [ ] Distance:
  - At about 50 blocks the rope stops swaying (a fixed curve) but is still drawn.
  - Past 96 blocks it disappears.
  - No popping in the 40 to 60 block range.
- [ ] Pause the game (singleplayer). The rope freezes and resumes.

## 2. Draping and wrapping
- [ ] Tie a pig to a fence and let it wander behind other fence posts. The rope drapes over the fence tops at the visible height, not half a block above.
- [ ] Walk in a circle around a stone pillar holding a leashed pig. The rope bends around the pillar corners instead of passing through, and doesn't shake.
- [ ] Stand on a ledge with the leashed mob below. The rope runs over the ledge edge.
- [ ] Grass, flowers and water don't stop the rope. Stone, slabs, stairs, fences and walls do.
- [ ] Client config `rope.collisions = false`: the rope passes through blocks.

## 3. Physics
- [ ] Walk away from a leashed cow.
  - It follows on foot within 5 blocks.
  - Past 5 it is pulled along, and it keeps up with sprinting without ever snapping.
- [ ] Fly away in creative. The lead snaps just past 12 blocks, with the "Lead snaps" subtitle at your position and a lead item dropped at the cow.
- [ ] **Swinging from a cliff knot:**
  - Put a fence on a cliff edge and tie a pig to it.
  - Push the pig off. It drops, catches on the lead with a small bounce, and swings back and forth under the knot, slowly settling.
  - It never takes damage and never snaps.
  - Break the fence. The pig falls from where it is, and fall damage counts only from that point.
- [ ] Lower a pig slowly down a drop on a lead by walking toward the edge. No damage on landing.
- [ ] Tie a sitting (tamed) wolf. It isn't pulled, but it still snaps past 12.
- [ ] A leashed horse whose player rider is steering keeps its normal control.

## 4. Boats, rafts and chest boats
- [ ] Use a lead on an oak boat, a bamboo raft and a chest boat.
  - Each is tied to you, with the rope at the bow, and one lead is used each time.
  - Use each again with an empty hand: it is untied and the lead drops. You do not board it.
- [ ] Walk along a river with an empty leashed boat. It follows in the water and turns its bow toward you. On land it drags.
- [ ] Tie a leashed boat to a fence by using the fence. A knot appears and the boat stays.
- [ ] Save and quit, reload. The boat is still tied to the knot, and to you if you held it.
- [ ] Break a leashed boat. The lead drops. Take a leashed boat through a portal: it lets go, and no lead is duplicated.
- [ ] A boat with a pig in it, leashed to you, is pulled along with the pig.
- [ ] **Multiplayer (dedicated server):**
  - Player B sits in a boat that player A leads.
  - B steers away. The boat is not held back (Bedrock and 1.21.6), and it snaps past 12 blocks on both screens at once.
  - With `pullPlayerDrivenBoats = true` in the server config, B feels the pull smoothly with no rubber-banding.
  - Everyone sees each rope in the same place.

## 5. Rules
- [ ] **Mob-to-mob:**
  - Hold two leashed sheep and sneak-use a cow. Both sheep are now tied to the cow ("Lead tied").
  - Sneak-use one of your own leashed sheep: the other sheep is tied to it.
  - A sheep 13 or more blocks from the target stays on you.
  - A baby animal can't be a holder.
- [ ] **Shears:**
  - Shear a leashed sheep. The lead is cut ("Shears snip" and "Lead untied"), the lead drops, the shears lose 1 durability, and the sheep keeps its wool.
  - Shear a cow that holds two sheep: all three leads are cut.
  - Shear a knot: all its leads are cut and the knot goes.
  - Shear a sheep another player is leading: nothing happens to the lead (vanilla shearing still works without Vanilla Backport).
- [ ] A dispenser with shears, powered, cuts the lead of the mob in front of it and loses durability.
- [ ] **Take-over:** use a lead on a pig tied to a knot. It comes to you and the knot's lead drops. Use a lead on a pig another player leads: nothing happens.
- [ ] **Knots:**
  - Using a knot while holding leashed mobs ties them to it.
  - Using it again with nothing held takes them all back and the knot disappears.
  - Sneak-using a knot with nothing held does nothing.
- [ ] Break the fence under a knot, or push it with a piston. The knot breaks immediately (knot break sound) and the leads drop. They don't wait 5 seconds.
- [ ] A knot whose leads were all taken or broken disappears within a few seconds. A knot still holding a mob in an unloaded chunk comes back when the mob loads.
- [ ] Fly with elytra holding a leashed parrot and boost with a rocket. The lead breaks ("Lead snaps").
- [ ] **Recipe:**
  - The recipe book shows the lead as string in A1, B1, A2, B2 and C3, making 2.
  - With `recipe.newLeadRecipe = false` and `/reload`, the slimeball recipe is back.
- [ ] **Subtitles** on: "Lead tied", "Lead untied", "Lead snaps", "Shears snip" all appear at the right moments.

## 6. Fence-to-fence ropes
- [ ] **Tying:** hold 3 leads and use a fence. A knot appears, one lead is used, and a rope runs from the knot to your hand. Walk to a second fence 8 blocks away and use it. The rope is now tied fence to fence and hangs with a little sag (about 1.4 blocks at the middle). No second lead is used.
- [ ] Walk around the rope: it is the same thick, striped rope as a lead, lit along its length, and stays drawn with both knots off-screen.
- [ ] Leave and come back (past 100 blocks, or save and quit, and reload). The rope is still there, and both knots are back.
- [ ] **Off hand:** carry a rope with an **empty main hand** and use a fence. It is tied once, and not taken straight back into your hand.
- [ ] **Taking back:** use a rope's knot with an empty hand. That end comes back to your hand, the other stays tied ("Lead untied"). Use the rope's first fence: the lead drops there and the rope is gone. Sneak-use a knot with an empty hand: nothing is taken.
- [ ] **More ropes:** with a lead in hand, use a fence that already has a rope. A second rope starts from it. Tie three ropes from one fence to three others.
- [ ] Carry a rope more than 12 blocks from its fence: it snaps ("Lead snaps") and the lead drops at the fence. Glide and boost with a firework while carrying one: it breaks.
- [ ] **Breaking:** shear a knot (both its ropes drop, shears lose 1 durability); punch a knot; break a fence; push a fence with a piston. Each drops one lead per rope and the rope disappears on both screens at once.
- [ ] A dispenser with shears facing a knot cuts its ropes.
- [ ] Rope rendering off (`rope.enabled = false`): fence-to-fence ropes are still drawn, as a thin fixed curve.
- [ ] **Multiplayer:** a second player sees ropes appear, move to a new fence, and disappear, and sees ropes that were tied before they joined or came near.
- [ ] **Valkyrien Skies, ship to world:**
  - Build a small boat (a few dozen blocks) with a fence at the bow, and tie a rope from it to a fence on the shore. The rope runs from the fence on the ship, not toward the shipyard, and follows the ship smoothly as it bobs.
  - Drive away with the ship's engines. The rope stops the boat a little past its length and holds it there without jitter. The boat swings around the bow knot.
  - Add two more ropes (stern and middle). The boat is held closer, with less stretch.
  - Build a big ship (several hundred blocks or more) and ram it away at speed. The rope snaps with "Lead snaps", and a lead drops where it broke. A slow drift is still held.
  - With several ropes, the most stretched one snaps first; the others keep holding.
- [ ] **Ship to ship:** tie two ships together and drive one away. The other is towed along; the two pull on each other.
- [ ] **Unloaded ends:** tie a ship to a shore fence, fly the ship far away until it unloads, and come back: the rope is there again. Unload the shore fence's chunk instead: the rope goes slack (the ship drifts) and holds again once the chunk is back. Nothing loads chunks by itself (check with `/forge tps` or a chunk viewer).
- [ ] Remove the ship's fence, or disassemble the ship: the rope drops its lead.
- [ ] Tweak `tethers.stiffness`, `damping`, `breakingStrength` and `slack` in the server config and check each changes what it says.
- [ ] **Vanilla Backport installed:** all of the above still works, and each rope is drawn once.
- [ ] **Game Start:** in a match whose rules forbid changing blocks at a fence, you can't tie, take or cut ropes there.

## 7. Other mods
- [ ] **Gravity Unbound:**
  - On a gravity plate or core with sideways gravity, leash a mob. The rope sags toward that side's "down", attaches at the collar of the rotated model, and the pull still drags the mob toward you.
  - A mob hanging from a knot in sideways gravity swings along that gravity.
- [ ] **Valkyrien Skies:**
  - Build a ship with a fence and tie a pig to the fence. The lead does not snap on tying.
  - The rope runs from the fence on the ship (not toward the far-away shipyard) to the pig.
  - Fly or sail the ship away: the pig is dragged along and swings under it.
  - Using the ship's fence while holding leashed mobs ties them to it.
  - Repeat with Vanilla Backport installed.
- [ ] **Full Stop:**
  - A pig catching on its lead after a fall gets no damage and no g-force effects.
  - A pig swung hard into a wall on a lead can still take impact damage.
  - A player riding a leashed boat that catches gets no vignette.
- [ ] **Pehkui (in Genesis):** use `/scale set pehkui:base <scale> @e[type=pig,limit=1,sort=nearest]` (and `@s` for yourself).
  - A pig at 0.0625 (1/16): leash it and walk, then sprint, 20 blocks away and around. It stays on the lead and is dragged along behind you, on the ground (a small hop at most, no flying at hand height). Stop: it is pulled to about 5 blocks and waits. The rope meets its tiny neck.
  - Same pig: fly away in creative. The lead still snaps just past 12 blocks, with the subtitle and a lead dropped.
  - A pig at 4: stand right next to its side and walk away. It follows without being thrown at you or past you; the lead only tightens once you are about 5 blocks from its body. It snaps about 12 blocks from its body (not its centre). The rope meets its big neck, high up.
  - A pig at 1/16 hanging from a knot over a drop: it hangs and swings like a normal pig and doesn't bounce up and down endlessly.
  - Scale yourself to 0.0625 and hold a lead: the rope leaves your tiny hand, not a point under your feet. Scale yourself to 2: it leaves your hand, not your shoulder.
  - **Both at 1/16** (`/scale set pehkui:base 0.0625 @s` and the pig): the lead looks like a normal lead seen from a normal player, only 16 times smaller: as thin next to you as a normal lead is next to a normal player, with a small sag (not a loop lying around you), and it swings and settles at the usual pace. Walk off: it goes taut about 5/16 of a block from the pig (about five tiny pig-widths) and drags the pig; it snaps about 12/16 of a block away (fly off in creative). The pig walks after you and stops about 2/16 of a block short.
  - Tiny you, normal pig: a thin cord from your hand to its collar, normal lengths (it snaps at 12 blocks, as usual). Normal you, tiny pig: the same thin cord.
  - A tiny pig tied to a fence: the knot looks as always, a thin cord runs from it to the pig, and the pig can wander 5 blocks (it is tied to a normal-sized fence).
  - Both at 4: the rope is four times as thick; the lengths are the normal ones, measured from the bodies.
  - A calf or a baby turtle on a lead looks exactly as before (vanilla babies count as normal size).
  - With Vanilla Backport as well: the tiny pair's lead behaves the same (taut past 5/16, snapping past 12/16).
  - A leashed boat or horse at 1/16 and at 4 is dragged along too.
  - With Full Stop (Camafy 0.1.6): `/fullstop weigh @e[type=pig,limit=1,sort=nearest]` on a 1/16 pig shows about 0.037 kg, on a 4x pig 9600 kg; on a calf 81 kg; on yourself at 1/16 about 0.018 kg (plus what you carry).
- [ ] **MKW Mobility:** a kart and its driver can't be leashed. A leashed mob that sits in a kart loses the lead, as in vanilla.
- [ ] **Grapple Unbound:** a player on a grapple (motion claimed) is never pulled by a lead.
- [ ] **Game Start:** in a match where PvP is off, you can't take, re-tie or cut the leads of another player's tamed wolf.
- [ ] **Vanilla Backport:**
  - No crash on start.
  - One rope per lead: no second, thinner leash line anywhere.
  - Boats and rafts can be leashed.
  - A happy ghast can be leashed. A boat tied to a happy ghast hangs on four taut ropes drawn by Unleashed Lead and follows the ghast.
  - The ghast snaps at 16.
  - The recipe book shows Unleashed Lead's shape.
  - Shearing a sheep another player leads does nothing.
  - `leash_drop_connections` in Vanilla Backport's config controls the firework rule.

## 8. Performance
- [ ] Put about 50 animals on leads: ten fences with five each, plus a few held. Look at them from nearby and from 60 blocks away.
  - Compare frame time (F3 graph) with rope rendering on and with `rope.enabled = false`.
  - Expected: a small cost up close. Past `simulationDistance` (48) ropes are fixed curves, and only the nearest `maxSimulated` (64) are simulated.
- [ ] Same scene with shaders (Oculus) if you use them. The ropes render, possibly without shader lighting.
- [ ] Server tick time (`/forge tps`) with 50 leashed animals is no worse than vanilla leashes by a meaningful margin.
- [ ] A ship moored with 10 ropes: no drop in server tick time or in Valkyrien Skies' physics rate (its debug screen), and the ship sits still without shaking.

## 9. Zero and low gravity (Camafy 0.1.7)
- [ ] VS Genesis space (`genesis:great_unknown`) or an Ad Astra orbit: a lead held with slack, and a fence-to-fence rope, float in a sideways bow instead of hanging down. Close up, a simulated rope keeps whatever shape you leave it in and only damps.
- [ ] Same place: a leashed mob drifting past 5 blocks is pulled back along the rope from any direction, including from below, and never "falls". Inside 5 blocks it drifts freely.
- [ ] A ship moored in space doesn't sink or pull: the rope stays slack until you push the ship past its length.
- [ ] Ad Astra Moon: ropes settle into the same curve as on Earth, just more slowly. A mob hanging from a knot swings slowly.
- [ ] A Gravity Unbound zero-strength plate: a mob in its field floats on its lead. A rope is pulled by the average gravity at its two ends, so a lead from a player outside the field to a mob inside still settles into its hanging curve, only half as fast; a rope between two mobs both inside floats.
