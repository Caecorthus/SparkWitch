# SparkWitch Context

This is a live routing map, not blanket permission to refactor. Structural
changes require explicit owner approval.

## Product Boundary

SparkWitch adds Grand Witch, Accomplice, Apprentice Witch, Murderous Witch, Pig
God, Prophet, Saint, Perfumer, Tarot Reader, Ninja, Kidnapper, Black Raven, and Bell Ringer gameplay to Wathe.
It also adds the Control Expert, a non-lethal police variant that shares the Vigilante slots,
and the Seeker, a police variant with a remote car and wall cameras that shares the same slots.
SparkFactionAPI owns shared faction contracts;
SparkTraits and NoellesRoles integrations stay behind compatibility Adapters.
SparkStrength and SparkAssist do not own SparkWitch gameplay.
Seeker availability requires the SparkStrength tablet item (`sparkstrength:tablet`), resolved by
registry id only; SparkStrength owns no Seeker gameplay.

Current build baseline:

- Minecraft `1.21.1`
- Java `21`
- SparkWitch `0.1.6.0` (Emma branch)
- SparkFactionAPI floor `0.1.5.10`

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
  faction-count snapshots, purchaser-only reading results, and economy. Client
  presentation (faction-count HUD, reading slip, reading log, selector ledger)
  lives in `client/tarot/`, `client/hud/Tarot*`, and `client/screen/`.
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
- `roles/civilian/controlexpert/`: Control Expert round-start loadout, task-money economy,
  restricted shop, Disruptor, Taser, thrown Shock Device, owner-only status, stun application
  with owned-effect tracking, and lifecycle cleanup; its mixins live in `mixin/controlexpert/`
  and `client/mixin/controlexpert/`, client presentation (status HUD, stun input lock, Taser
  crosshair) in `client/controlexpert/`.
- `roles/civilian/seeker/`: Seeker (`sparkwitch:seeker`) role rules, loadout, economy, shop,
  owner-private `SeekerStatusComponent`, targeting predicates (`SeekerTargeting`), and lifecycle
  cleanup. Subpackages: `device/` (car and camera entities, placement, battery, cooldowns, shared
  car physics, sounds), `remote/` (server-authoritative remote-view sessions and the owner-simulated
  car move validator), `hit/` (every device-break source, the single `SeekerDamageRules.mayBreak`
  gate, nearest-wins raycasts, the breaker mark), `net/` (payloads), `console/` (tablet console
  service), and `taotie/` (NoellesRoles Taotie swallow seam). Its mixins live in `mixin/seeker/`
  and `client/mixin/seeker/`; client presentation (console screen, remote view and car driver,
  device renderers) in `client/seeker/`. Cross-mod seams go through `compat/SparkTraitsSeekerBridge`,
  `compat/SeekerControlExpertBridge`, `compat/NoellesTaotieSeekerBridge`,
  `compat/SparkStrengthTabletCompat`, and `compat/SparkStrengthM67Compat`.
- `PoliceSlotAssignmentService` (`roles/civilian/judge/`) with `mixin/PoliceSlotAssignmentMixin`
  and `mixin/PoliceRoleHistoryMixin`: police-slot ownership. Judge, Emma, the Control Expert, and
  the Seeker share the Vigilante slots uniformly through `VARIANT_IDS`; no variant owns a separate
  slot mixin.
- `client/factor/`: low-priority fallback outlines after ordinary instincts and hiding.
- `client/emma/`: shared-key dispatch and role-owned target HUD; no witch inventory panel.
- `roles/witch/grandwitch/recruitment/`: cumulative world quota and inventory/gold conversion;
  `compat/recruitment/` owns pinned-provider shop-output and role-exit adapters.
- `mana/`: mana economy and natural-regeneration runtime.
- `component/`: CCA ids, stored fields, sync/NBT codecs, and narrow state
  operations used by the owning runtime Modules.
- `compat/`: optional or version-sensitive cross-mod Adapters.
- `impl/SparkWitchEvents`: watch-only registration/lifecycle aggregator.
- `util/hitscan/`: server-side lag compensation for hitscan weapons. `PlayerHitboxHistory` keeps a
  one-second, server-thread-only ring buffer of player hitboxes (never saved, synced, or sent);
  `HitscanLagRules` owns the ping-based rewind window and swept volumes. Used by the Hunter
  double-barrel shotgun, the Murderous Witch Death Ray, the Control Expert Taser, and the Black Raven
  Feather Blade (whose sight and feet-distance reach are taken at the rewound hit); client crosshair
  hints keep current boxes.

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
`sparkwitch:fire_death_ray` carries the caster's yaw and pitch at key press (the attack key
is handled before that tick's rotation packet); a payload without them still decodes and
falls back to the server rotation. The server still decides every Death Ray and shotgun hit.
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

Control Expert state never enters that shared schema either.
`sparkwitch:control_expert_status` (`NEVER_COPY`, owner-only sync) holds only the Disruptor
and stun countdowns; the stun counter is never persisted, and the owner's client never unlocks
before the server's zero sync. Stun effects are attributed to the Control Expert, and only
effects the stun itself introduced are removed on a terminal (not Last Stand-intercepted) death,
reset, finalize, or disconnect. The Control Expert is non-lethal: no item calls `killPlayer`,
the Taser never joins `wathe:guns` or `GunShootPayload`, and the Shock Device is a role-owned
entity, never Wathe's grenade. Wathe's
gun packet sees the Control Expert as its native Vigilante only through
`mixin/controlexpert/ControlExpertPoliceGunMixin` (OR-wrapped `isRole`, no `@Redirect`). The
stun's input lock is client-side; the server denies item use, interactions, and the listed C2S
payloads (`ControlExpertStunGuards`, `ControlExpertStunPayloadGuardMixin`). The Disruptor gates
keyed instinct only through `client/mixin/controlexpert/ControlExpertInstinctGateMixin`
(`@WrapMethod` on `WatheClient`). The Control Expert never renders in the
`gui.sparkwitch.skills` panel.

Seeker state never enters that shared schema either. `sparkwitch:seeker_status` (`NEVER_COPY`,
owner-only sync) holds the Seeker's car, cameras, session, battery, cooldown-reason, and mark state
bound to the current Wathe match id; remote-session bookkeeping stays server-only and is never
synced or saved, and other players never see the battery or the mark. The remote view is a
client-only camera switch (`MinecraftClient#setCameraEntity` on the owner's client); the server
never calls `ServerPlayerEntity#setCameraEntity`, so server camera writers (Taotie, Last Stand,
Depression) keep working and the body stays in place. While the owner views the car or camera,
their own body is outlined through walls on the owner's client only
(`client/seeker/SeekerBodyClientHooks`, `SeekerRules.OWN_BODY_COLOR`), and the outline ends with
the view. The possession filter is a private
`PostEffectProcessor`, never `GameRenderer.postProcessor`. A placed camera's look (`LOOK_YAW`,
`LOOK_PITCH`) and `VIEWING` flag are public DataTracker state that every tracking client renders
(the head follows the view, the LED glows while viewed, and an idle head holds its last look); only
the owner's client sends `seeker_camera_look` while viewing, and the server accepts it only for the
owner's own CAMERA session and clamps it into that camera's cone (`SeekerCameraLookRules`; the
latest look wins). Sessions are server-authoritative: the
client never predicts entry, and every exit except the owner's own Shift is detected on the server.
The owner's client only simulates the car it drives, and every move is validated against the shared
`SeekerCarPhysics` (speed budget, replay, a server-side fall model that never trusts the client's
velocity, radius and play-area clamps). The session lock (`LOCK_SCOPE = SESSION`) applies only while
the Seeker drives the car or views a camera: `mixin/seeker/SeekerSprintLockMixin` clears sprint on
both sides, `SeekerInteractionGuards` fail the Fabric player callbacks in the `seeker_session_lock`
phase, `mixin/seeker/SeekerSessionPayloadGuardMixin` drops the blocked C2S payloads on the server
thread, and inventory clicks and drops are denied. The body stays locked, but the driven car itself
may right-click whitelisted doors, trapdoors, fence gates, buttons, and levers through
`seeker_car_use`: the owner's client takes the use-key presses via
`SeekerRemoteKeyDrain#sparkwitch$takePresses` while the body's use stays locked, and
`remote/SeekerCarUseService` bypasses the body's use callbacks by design, re-validates the session,
reach, the car's forward cone, and line of sight from the car's eye, and acts with an empty hand,
so the body's held item is never used from the car. Device entities never save to disk and cannot be
summoned; every device is swept at game start and at finalize. The Seeker may own any number of
cameras (150 each, no cap); each keeps a per-match label, the synced session focus names the viewed
camera, an untargeted camera open views the last-viewed usable camera (else the lowest label), and
while viewing, a fresh strafe-key press requests the previous or next camera by label as an atomic
switch (a held key cycles once and must be released after every session start or switch).
Role change, final death, and reset
end the session and clean up devices and state; disconnect only ends the session. A deployed car
starts at 100% battery and drains 1% every 10 ticks while driven and 1% every 60 ticks otherwise;
the drain runs on the server tick and the client only displays the synced value. At 0% the car shuts
down (`BATTERY_DEPLETED`) with the broken-car cooldown and no mark. Car cooldowns are exact + max
writes on the `seeker_car` item: 60 s at round start, 180 s after a break, a recall (physical or
remote), or depletion, and 60 s after a Taotie return. Any other round participant may break a
device: the break gate `SeekerDamageRules.mayBreak` uses the role-agnostic
`SeekerTargeting.isRoundParticipant` (never the Seeker-only `isActiveParticipant`), with the owner
as SparkFactionAPI proxy target and Vendetta isolation. Break sources are bare hands and every
left-click melee (`AttackEntityCallback`, server reach and line-of-sight re-check), the Wathe
revolver and derringer, the SparkWitch double-barrel shotgun, the NoellesRoles Demon Hunter pistol,
the Control Expert Taser and Shock Device, the knife stab (every `KnifeItem.getKnifeTarget` caller),
the NoellesRoles throwing axe, the thrown Ninja shuriken, the Black Raven feather blade, the
Murderous Witch Death Ray, the Wathe grenade (including the SparkTraits Bomb Maniac grenade), and
the SparkStrength M67. A client-picked gun hit (Wathe revolver and derringer, Demon Hunter pistol) is
accepted when the shooter's look ray meets the device box grown by its client targeting margin with
a clear line to a point of the device, else only through the 25° / 15-point-sample latency fallback
(`SeekerDamageRules.gunAimedAndVisible`); nothing breaks through walls. Rays and projectiles are
nearest-wins (a nearer device takes the hit, the player behind is not hit); blasts (Wathe grenade,
SparkStrength M67) break every device in a sphere with line of sight and still kill players as
before. Sources with no hit or damage geometry never
break a device: the firecracker (sound only), the Bomber timed bomb (kills only its holder), and the
poison gas cloud (status effect). A breaker other than the owner is marked for the owner only (10 s,
newest replaces oldest) when the owner holds the tablet; recalls, depletion, and Taotie swallows
never mark. The Taotie path goes only through `compat/NoellesTaotieSeekerBridge` (pinned
NoellesRoles `b58fa5f`) and `roles/civilian/seeker/taotie/`: the client predicts the Taotie's
swallow key on a car in its crosshair (`client/mixin/seeker/SeekerTaotieAbilityKeyMixin`) and sends
`seeker_car_swallow`, which the server re-validates; a swallow consumes the Taotie's swallow
cooldown, removes the car without a mark or the 180 s cooldown, and tells the owner without naming
the Taotie; the car returns (60 s cooldown) when that Taotie finally dies or loses the role. The
Seeker never renders in the `gui.sparkwitch.skills` panel.

Grand Witch rework state uses separate `sparkwitch:grand_witch_runtime`,
`sparkwitch:witch_factor_world`, and `sparkwitch:grand_witch_recruitment_round`
components; the existing shared packet and NBT layouts remain unchanged. Sword
kill readiness (30s) is independent of the item dash cooldown (5s). Recruitment
uses a cumulative world quota, never a living-teammate count. Sword piercing
applies to role/item shields, not trait protections such as Last Stand or Last
Escape; protection costs and retaliation keep their normal side effects.

Active Wraiths do not absorb name-tag raycasts they are hidden from.
`client/render/WraithNameTagPassThrough` owns the presentation rule: a player
whose synced Wraith state is active is skipped when
`WraithViewerRules.shouldHideFromOrdinaryViewer` hides it, except for the
promoted Curser viewed by the witch faction. Spectators, killers viewing the
promoted Saboteur, and the bound killer viewing its Vendetta keep selecting it.
`client/mixin/WraithNameTagRaycastMixin` narrows only the predicate of the first
(player) `ProjectileUtil.getCollision` in Wathe's `RoleNameRenderer.renderHud`;
`WitchCohortRoleNameMixin` and `BlackRavenRoleNameRenderer` apply the same filter
to their own raycasts so their labels follow the tagged player.

SparkTraits is optional and fail-closed. Reflection may target only
`dev.caecorthus.sparktraits.api.SparkTraitsApi`, never `sparktraits.impl` or
`sparktraits.component`. Black Raven may query only the public
`isInstinctHidden(viewer, target)` facade; an absent or older SparkTraits build
adds no suppression and must not break the client. Bell Ringer may query only
`isRoleSkillBlocked`, `registerTerminalDeathReason`, and
`setExactItemCooldownRemaining` beyond the shared weapon-action gate; an absent
or older build means no block, no terminal registration, and a vanilla cooldown.
The Ceremonial Sword and a credited Fire Poker fall may query only
`isNonFinalKillPending`, `getNonFinalKillCooldownTicks`, and
`runWithNonFinalKillWeapon` through `SparkTraitsKillerBridge` for non-final
(Depression fake death) kills; an absent or older build means no non-final
kill, the original cooldown, and the kill running directly exactly once.
Control Expert may query only `getMarksmanRangeMultiplier` and `hasActiveTrait` (Impostor) through
`compat/SparkTraitsControlExpertBridge`, and `isLastStandDeathIntercepted` through the existing
`WitchFactorTraitsBridge`, beyond the existing `SparkTraitsKillerBridge` seams
(`isRoleSkillBlocked`, `blocksWeaponAction`, `isLastEscapeActive`,
`setExactItemCooldownRemaining`). An absent SparkTraits means a 1.0 Taser range multiplier,
normal task money, a vanilla round-start cooldown, and death cleanup on every death; a present
build whose facade lacks or fails a method falls back per method to a 1.0 multiplier, no task
money (Judge semantics), a vanilla cooldown, or an intercepted death: the `KillPlayer.AFTER`
cleanup is skipped, the stun counters still drop once the victim stops participating, and
owned effects are removed at reset, finalize, or disconnect.
The Seeker may query only `isLastStandPending` and `hasActiveTrait` (Impostor) through
`compat/SparkTraitsSeekerBridge`, `isInstinctHidden` through its own client
`SeekerInstinctVisibilityBridge`, `discountShopEntryForCharisma` through
`SparkTraitsCharismaBridge`, and `isLastStandDeathIntercepted` through the existing
`WitchFactorTraitsBridge`, beyond the existing `SparkTraitsKillerBridge` seams
(`isRoleSkillBlocked`, `blocksWeaponAction` with its `getForcedMeleeCooldownTicks` read,
`isKillerInteractionBlocked`, `setExactItemCooldownRemaining`). An absent SparkTraits means not
pending, not Impostor (devices and task money allowed), nothing instinct-hidden, full shop prices,
no skill or weapon block, a vanilla car cooldown, and cleanup on every death. A present build whose
facade lacks or fails a method falls back per method to not pending, Impostor-or-unknown (devices
and task money denied, `DENY_IMPOSTOR_DEVICES`), not hidden, full prices, no block, a vanilla
cooldown, or an intercepted death: the `KillPlayer.AFTER` cleanup is skipped and the component
tick's final-death fallback cleans up once the Seeker is dead and not Last Stand pending.

Active Wraiths do not absorb another player's aimed action in the client
selectors listed below. `client/render/WraithAimPassThrough` owns the rule: a player whose synced
Wraith state is active is skipped as an aim or crosshair target, except by the
local bound killer of that active Vendetta. Invisibility and
`noellesroles:no_collision` are never Wraith signals. The Wathe revolver,
derringer, and knife selectors and the NoellesRoles Demon Hunter selector open a
thread-local shooter scope through `@WrapMethod` (so SparkTraits Marksman HEAD
replacements stay covered); only that shooter's
`ProjectileUtil.getCollision(Entity, Predicate, double)` is filtered. The vanilla
crosshair is filtered for the local non-spectator player, and Wathe's bed-hit
fallback drops a sleeping Wraith on the client. Server authority is unchanged:
SparkFactionAPI's affect policy still cancels any Wraith target that slips
through.

Active Wraiths pass closed Wathe doors, vanilla doors, trapdoors, and fence
gates for movement only. `mixin/WraithDoorPassingMixin` empties their collision
shape for the Wraith's own entity shape context, except inside
`util/RaycastShapeScope`. `mixin/RaycastShapeScopeMixin` is the one
`@WrapMethod` on `RaycastContext#getBlockShape` (both sides, entity context kept),
so a Wraith's COLLIDER rays (`canSee`, `ProjectileUtil.getCollision`, explosion
exposure) still stop at doors: the Vendetta knife and the Guardian Angel shield
need real sight. Every SparkWitch door-passing exemption must honour this scope
instead of adding its own ray wrapper.

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
