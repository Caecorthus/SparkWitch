# SparkWitch Context

This is a live routing map, not blanket permission to refactor. Structural
changes require explicit owner approval.

## Product Boundary

SparkWitch adds Grand Witch, Accomplice, Apprentice Witch, Murderous Witch, Pig
God, Prophet, Saint, Perfumer, Tarot Reader, Ninja, Kidnapper, Black Raven, and Bell Ringer gameplay to Wathe.
SparkFactionAPI owns shared faction contracts;
SparkTraits and NoellesRoles integrations stay behind compatibility Adapters.
SparkStrength and SparkAssist do not own SparkWitch gameplay.

Current build baseline:

- Minecraft `1.21.1`
- Java `21`
- SparkWitch `0.1.6.0` (Emma branch)
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
- `roles/killer/bellringer/`: Echo skill (game-time cost, forced Echo tasks,
  deadline penalty), owner-private Echo/hint/toll state, bound bell and toll kill,
  restricted native shop, and lifecycle cleanup; its mixins live in
  `mixin/bellringer/` and `client/mixin/bellringer/`, client presentation in
  `client/bellringer/`.
- `client/ability/`: generic configurable skill-key-2 registration and role-id
  dispatch only; concrete roles own their handlers.
- `roles/neutral/murderouswitch/`: Murderous Witch feature, Death Ray, shop,
  and win rules.
- `roles/witch/`: rules shared by Grand Witch and Accomplice.
- `roles/witch/grandwitch/`: Grand-Witch-private permanent sword reward, spells, fear,
  and recruitment transactions. Its `factor/` ledger is shared: cumulative world-wide
  quota, delayed private network views, source-independent income, and persistent provenance.
- `roles/civilian/emma/`: unique cop claim, role-owned mana skill, delayed backlash,
  owner-private failed-recruitment evidence, speed latch, and one reward per gun cycle.
- `client/factor/`: low-priority fallback outlines after ordinary instincts and hiding.
- `client/emma/`: shared-key dispatch and role-owned target HUD; no witch inventory panel.
- `roles/witch/grandwitch/recruitment/`: cumulative world quota and inventory/gold conversion;
  `compat/recruitment/` owns pinned-provider shop-output and role-exit adapters.
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

Bell Ringer state never enters that shared schema either. `sparkwitch:bell_echo`
(`NEVER_COPY`, owner-only sync, match-id bound) holds the forced Echo task
marker and deadline, the red heard-hint window, and the ringer's server-computed
toll-target flag; the owner's client receives the Echo task type, the toll flag,
and remaining-tick counters, never absolute server ticks. A silent Echo drop
(role change, death, non-participation, lost real sanity) removes the marked
Wathe task with the marker, so it can never pay out as an ordinary task. Echo reaches
Wathe's mood loop through `mixin/bellringer/PlayerMoodComponentBellEchoMixin`
(`remap = false`, no `@Redirect`): it holds normal task generation, keeps the
Echo completion out of `TaskComplete.EVENT` (Wathe's +0.5 mood and completion
arrow remain), and scales `MOOD_DRAIN` while the marker is active. The task
line is restyled only through `client/mixin/bellringer/` on
`MoodRenderer$TaskRenderer`. Owner-approved exception: the bell toll
(`sparkwitch:bell_toll`) is a forced, terminal kill that pierces every role,
item, and trait protection (registered as SparkTraits-terminal); only
SparkFactionAPI's structural `canAffectPlayer` veto still applies. Guards that
ignore Wathe's `force` flag (currently only the Saint HEAD guard) opt out through
`BellRingerRules.piercesProtection`. As with sword piercing, protections that
run as `KillPlayer.BEFORE` listeners still pay their normal costs before the
forced kill proceeds. The Bell Ringer never renders in the
`gui.sparkwitch.skills` panel.

Grand Witch rework state uses separate `sparkwitch:grand_witch_runtime`,
`sparkwitch:witch_factor_world`, and `sparkwitch:grand_witch_recruitment_round`
components; the existing shared packet and NBT layouts remain unchanged. Sword
kill readiness (30s) is independent of the item dash cooldown (5s). Recruitment
uses a cumulative world quota, never a living-teammate count. Sword piercing
applies to role/item shields, not trait protections such as Last Stand or Last
Escape; protection costs and retaliation keep their normal side effects.

SparkTraits is optional and fail-closed. Reflection may target only
`dev.caecorthus.sparktraits.api.SparkTraitsApi`, never `sparktraits.impl` or
`sparktraits.component`. Black Raven may query only the public
`isInstinctHidden(viewer, target)` facade; an absent or older SparkTraits build
adds no suppression and must not break the client. Bell Ringer may query only
`isRoleSkillBlocked`, `registerTerminalDeathReason`, and
`setExactItemCooldownRemaining` beyond the shared weapon-action gate; an absent
or older build means no block, no terminal registration, and a vanilla cooldown.

## Tofana Elixir Vocabulary

- **Tofana protection**: A single-use protection granted by possessing Tofana Elixir. It cancels one otherwise valid, non-forced Wathe kill by another active player and consumes one elixir.
- **Tofana retaliation**: The follow-up kill attempt against the player whose Wathe kill was cancelled by Tofana protection. It is a normal, non-forced Wathe kill attributed to the protected holder.
- **Tofana chain**: A finite sequence in which Tofana retaliation is itself cancelled by another Tofana protection, consuming one elixir at each link.
- **Holder**: Any player carrying Tofana Elixir in their main inventory, hotbar, or offhand. Tofana protection is not restricted by role.

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
