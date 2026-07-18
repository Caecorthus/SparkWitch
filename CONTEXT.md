# SparkWitch Context

This is a live routing map, not blanket permission to refactor. Structural
changes require explicit owner approval.

## Product Boundary

SparkWitch adds Grand Witch, Accomplice, Apprentice Witch, Murderous Witch, Pig
God, Prophet, Saint, Perfumer, Tarot Reader, Ninja, Kidnapper, and Black Raven gameplay to Wathe.
SparkFactionAPI owns shared faction contracts;
SparkTraits and NoellesRoles integrations stay behind compatibility Adapters.
SparkStrength and SparkAssist do not own SparkWitch gameplay.

## Approved Structural Change

SparkWitch will become the sole owner of Wraith gameplay. The migration covers
the complete server lifecycle, role and promotion routing, persistent and
synced state, death and return-point policy, NoellesRoles digestion handling,
pickup restrictions, client anonymity and peer vision, resources, tests, and
public contracts. SparkTraits must retain no Wraith implementation after the
migration; any legacy compatibility there is migration-only and fail-closed.
All live Wraith role, component, packet, resource, and contract identifiers use
the `sparkwitch` namespace; SparkWitch must not register runtime-owned
`sparktraits:wraith*` or `sparktraits:curser` identifiers.
The SparkTraits Wraith implementation has not shipped, so this is a clean
pre-release ownership move: do not add legacy aliases, dual registration, or
old-state readers for its development-only ids.
SparkTraits remains optional. When present, SparkWitch may use only its public,
generic facade to let Last Stand keep death priority and to snapshot/restore
ordinary trait state plus `sparktraits:cautious`; SparkTraits must contain no
Wraith decision or lifecycle implementation. Wraith remains fully functional
without SparkTraits, minus those trait-owned interactions.
Wraith fall eligibility follows Wathe's existing lower-boundary kill heuristic:
a player-valued `lastAttacker` is treated as a pushed fall, otherwise the fall
is voluntary. Do not add a separate impulse-attribution runtime.
Every active Wraith, restricted or promoted, follows Wathe's living-player text
chat restriction and cannot send text chat. The client chat guard is owned by
SparkWitch, and Guidebook text must not claim that Wraith text chat is normal.
Every active Wraith, restricted or promoted, also remains unable to send voice
while retaining incoming voice. Promotion does not lift text or outgoing-voice
restrictions.
Every active Wraith, restricted or promoted, is blocked from collecting ground
item entities. The restriction does not block shop delivery, skill-granted
stacks, direct inventory insertion, or use of inventory contents.
Wraith visibility is viewer-local presentation, never a server-side skin or
profile mutation. An active Wraith viewer locally anonymizes every other player
and every corpse as wide Steve; another active Wraith is additionally revealed
through that projection with the Wraith outline. This does not change
`GameProfile`, skin properties, or broadcast a replacement skin. An ordinary
living in-round viewer cannot see Wraiths; dead viewers and players who did not
join the round see the real player appearance normally. This rule does not
branch on Spectator or Creative mode.

## Wraith Contracts

- The round start captures each player's own world, position, yaw, and pitch as
  the Wraith return point. It is usable only while the world exists and the
  player can safely stand there; it is not the map-wide spawn.
- Last Stand keeps priority. A death it intercepts never rolls Wraith.
- `wathe:escaped`, a voluntary `wathe:fell_out_of_train`, and terminal
  `noellesroles:digested` never roll. A player-valued `lastAttacker` makes
  Wathe's fall reason eligible as a pushed fall.
- When a usable return point exists, an otherwise eligible confirmed death may
  roll even if its death location is below `playArea.minY`, and successful
  activation returns there.
- Without a return point, ordinary death falls back to the actual corpse, then
  the death snapshot. A swallowed in-round `wathe:mental_breakdown` creates no
  body and falls back to the swallowing Taotie's live death-time location.
- A fallback below `playArea.minY` is rejected before rolling. An accepted
  fallback may resolve only the nearest locally safe standable point; it may
  not cross worlds or compartments.
- Successful activation preserves ordinary death drops, clears the remaining
  inventory, coins, and mana once, and never synthesizes a body for a swallowed
  death.

## Saboteur Contracts

- Saboteur is the non-rollable Killer identity awarded by Killer-aligned Wraith
  promotion.
- Its role-owned shop contains exactly one `wathe:lockpick` for 50 coins and
  Wathe's original blackout entry. The lockpick keeps Wathe's existing name;
  blackout pricing, activation, and shared cooldown remain owned by Wathe.
- Sabotage and Wathe blackout are independent light-outage sources. Sabotage
  remains usable during blackout, and an overlapping lamp may recover only
  after both sources have ended.
- Sabotage does not apply Wathe's global countdown, blindness, night vision, or
  shared blackout cooldown.
- Sabotage captures all Wathe-eligible train-light states within a 20-block sphere
  around the player at activation time. It does not affect torches, redstone
  lamps, held-item dynamic light, or unrelated mods' light sources.
- Each Sabotage light lease lasts exactly 20 seconds.
- Sabotage starts with a 60-second cooldown when Wraith promotion grants the
  Saboteur role. Each successful use then starts a 120-second cooldown.
- A use with no eligible lights in range still succeeds and consumes the full
  120-second cooldown; it does not expose nearby-light presence through a
  failure result.
- Saboteur's bottom-right ability HUD uses only `技能冷却 X 秒` while cooling
  down and `按【技能键】使用破坏` when ready, with the player's actual
  NoellesRoles ability binding. It never appears in the Witch inventory skill
  panel.
- Each task completed after promotion while the player is the active Saboteur
  grants 50 coins. The task that triggers Wraith promotion is not rewarded
  retroactively.

Current build baseline:

- Minecraft `1.21.1`
- Java `21`
- SparkWitch `0.1.5.8`
- SparkFactionAPI floor `0.1.5.8`

## Read Order

1. `CONTEXT.md`
2. The live owning Module and its direct callers

## Current Ownership

- `api/`: the only public downstream SparkWitch Interface.
- `roles/civilian/apprentice/`: Apprentice instinct and ability runtime.
- `roles/civilian/piggod/`: Pig God chase, psycho, sound, economy, and rules.
- `roles/civilian/prophet/`: Death Omen skill, role-owned state, spawn-boundary
  corpse collection, and client outline rules.
- `roles/civilian/saint/`: Saint protection, Hellfire, player-local state, and
  UUID-bound Karma.
- `roles/civilian/perfumer/`: private scent marks, cologne healing, corpse mood,
  outlines, shop, and economy.
- `roles/civilian/tarotreader/`: divination shop, one-shot selection sessions,
  faction-count snapshots, and economy.
- `roles/killer/ninja/`: parry, dark-kill bounty, shop, and death cleanup.
- `roles/killer/kidnapper/`: corpse targeting, dragging, positioning, and cleanup.
- `roles/killer/blackraven/`: Feather Blade marks, owner-private Perception state,
  bound ledger, restricted shop, and lifecycle cleanup.
- `client/ability/`: generic configurable skill-key-2 registration and role-id
  dispatch only; concrete roles own their handlers.
- `roles/neutral/murderouswitch/`: Murderous Witch feature, Death Ray, shop,
  and win rules.
- `roles/witch/`: rules shared by Grand Witch and Accomplice.
- `roles/witch/grandwitch/`: Grand-Witch-private sword, spell, fear, Voodoo,
  active-skill, and world runtime.
- `mana/`: mana economy and natural-regeneration runtime.
- `component/`: CCA ids, stored fields, sync/NBT codecs, and narrow state
  operations used by the owning runtime Modules.
- `compat/`: optional or version-sensitive cross-mod Adapters.
- `impl/SparkWitchEvents`: watch-only registration/lifecycle aggregator.

## Runtime Invariants

`WitchPlayerComponent.serverTick()` preserves this order:

1. Grand Witch ceremonial sword
2. Apprentice ability windows
3. Pig God chase
4. Murderous Witch Death Ray
5. Ninja parry window
6. shared cooldown
7. Prophet Death Omen window
8. mana regeneration
9. Saint ability

Do not reorder these calls. The existing component ids remain `sparkwitch:player`
and `sparkwitch:world`; packet field order and NBT keys must remain stable.
Perfumer state uses the separate owner-only `sparkwitch:perfumer_player`
component so its target lists are never added to the shared player packet.
Prophet state remains inside the existing `sparkwitch:player` component and
appends its owner-only ticks and body UUIDs after the live packet tail.
Black Raven state never enters that shared schema. Victim marks use
`sparkwitch:black_raven_mark`; owner-only progress and completed identity
snapshots use `sparkwitch:black_raven_perception`, both with `NEVER_COPY`.
Its role-owned active window is exposed to the shared cooldown/HUD path only
through `WitchSkillRegistry`'s stateless active-window provider.

SparkTraits is optional and fail-closed. Reflection may target only
`dev.caecorthus.sparktraits.api.SparkTraitsApi`, never `sparktraits.impl` or
`sparktraits.component`. Black Raven may query only the public
`isInstinctHidden(viewer, target)` facade; an absent or older SparkTraits build
adds no suppression and must not break the client.

## Verification

Use the Java 21 runtime explicitly:

```sh
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 test verifyArchitecture compileJava compileClientJava
git diff --check
```

Run a full sequential `build` for a release or after packet, NBT, resource,
metadata, mixin, or cross-module changes. Do not overlap SparkWitch and sibling
SparkFactionAPI clean/build tasks.
