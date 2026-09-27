# Unleashed Lead: in-game testing checklist

The headless gametests (`./gradlew runGameTestServer`, and again with `-PvanillaBackport`) cover the server rules, the leash physics and the rope solver. Everything below needs a real client: how the rope looks, multiplayer, and the other mods.

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

## 6. Other mods
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
- [ ] **MKW Mobility:** a kart and its driver can't be leashed. A leashed mob that sits in a kart loses the lead, as in vanilla.
- [ ] **GrappleVS:** its lead overhaul is off with Unleashed Lead installed, so there is only one rope per lead and the physics isn't doubled. A player on a grapple (motion claimed) is never pulled by a lead.
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

## 7. Performance
- [ ] Put about 50 animals on leads: ten fences with five each, plus a few held. Look at them from nearby and from 60 blocks away.
  - Compare frame time (F3 graph) with rope rendering on and with `rope.enabled = false`.
  - Expected: a small cost up close. Past `simulationDistance` (48) ropes are fixed curves, and only the nearest `maxSimulated` (64) are simulated.
- [ ] Same scene with shaders (Oculus) if you use them. The ropes render, possibly without shader lighting.
- [ ] Server tick time (`/forge tps`) with 50 leashed animals is no worse than vanilla leashes by a meaningful margin.
