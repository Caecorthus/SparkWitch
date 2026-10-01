# SparkWitch Context

This is a live routing map, not blanket permission to refactor. Structural
changes require explicit owner approval.

## Product Boundary

SparkWitch adds Grand Witch, Accomplice, Apprentice Witch, Murderous Witch, Pig
God, Prophet, Saint, Perfumer, Tarot Reader, Ninja, Kidnapper, Black Raven, and Bell Ringer gameplay to Wathe.
It also adds the Control Expert, a non-lethal police variant that shares the Vigilante slots,
and the Seeker, a police variant with a remote car and a wall camera that shares the same slots.
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
  faction-count snapshots, and economy.
- `roles/killer/ninja/`: parry, dark-kill bounty, shop, and death cleanup.
- `roles/killer/kidnapper/`: corpse targeting, dragging, positioning, and cleanup.
- `roles/killer/blackraven/`: Feather Blade marks, owner-private Perception state,
  bound ledger, restricted shop, and lifecycle cleanup.
  - `disguise/`: the Black Raven disguise. It owns the acting-role overlay
    (`BlackRavenActingRole`), the owner-only `BlackRavenDisguiseComponent` and its sync codec, the
    Tab B pool snapshot, the bound Raven Mask (`sparkwitch:black_raven_mask`), one-shot open
    sessions, the server-authoritative switch transaction with per-identity inventory and wallet
    stashes (`BlackRavenDisguiseService`, `BlackRavenInventorySwap`), the disguise shop
    whitelist, and disguise task income and killer-income routing (`BlackRavenDisguiseEconomy`).
  - `disguise/adapter/`: one adapter per shipped disguise role, holding its kit, shop spec, task
    reward policy, first-entry cooldowns, and enter/exit hooks. Batch 1 ships Conductor,
    Attendant, Awesome Binglus, Mermaid, Time Keeper, Waiter, Reporter, Tarot Reader, and
    Orthopedist. Every other allowed role is listed as unsupported.
  - Its mixins live in `mixin/blackraven/`: M1 `GameWorldComponentActingRoleMixin`, M2
    `ShopUtilsBlackRavenDisguiseMixin`, M3 `KillerShopBuilderBlackRavenDisguiseMixin`, M4
    `PlayerShopComponentBlackRavenDisguiseMixin`, the death-index, swap-guard, swap-remainder and
    wallet mixins, and the optional SparkStrength seams.
  - Its client presentation lives in `client/blackraven/`: `BlackRavenLedgerBookScreen` (Tab A
    Perception pages and Tab B disguise list), `BlackRavenDisguiseClientState` (synced view,
    acting-role change cleanup, mask tooltip, `CanSeeMoney` phase), `BlackRavenDisguiseClientRules`,
    `BlackRavenDisguiseInstinctClientHooks`, and the disguise HUD line in `BlackRavenHudRenderer`.
  - Its packets are `net/OpenBlackRavenDisguiseS2CPacket` and `net/SelectBlackRavenDisguiseC2SPacket`.
  - Its cross-mod seams go through `compat/SparkTraitsBlackRavenBridge` and
    `compat/SparkStrengthDisguiseCompat`.
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
owner-only sync) holds the Seeker's car, camera, session, battery, cooldown-reason, and mark state
bound to the current Wathe match id; remote-session bookkeeping stays server-only and is never
synced or saved, and other players never see the battery or the mark. The remote view is a
client-only camera switch (`MinecraftClient#setCameraEntity` on the owner's client); the server
never calls `ServerPlayerEntity#setCameraEntity`, so server camera writers (Taotie, Last Stand,
Depression) keep working and the body stays in place. The possession filter is a private
`PostEffectProcessor`, never `GameRenderer.postProcessor`. Sessions are server-authoritative: the
client never predicts entry, and every exit except the owner's own Shift is detected on the server.
The owner's client only simulates the car it drives, and every move is validated against the shared
`SeekerCarPhysics` (speed budget, replay, a server-side fall model that never trusts the client's
velocity, radius and play-area clamps). The session lock (`LOCK_SCOPE = SESSION`) applies only while
the Seeker drives the car or views the camera: `mixin/seeker/SeekerSprintLockMixin` clears sprint on
both sides, `SeekerInteractionGuards` fail the Fabric player callbacks in the `seeker_session_lock`
phase, `mixin/seeker/SeekerSessionPayloadGuardMixin` drops the blocked C2S payloads on the server
thread, and inventory clicks and drops are denied. Device entities never save to disk and cannot be
summoned; every device is swept at game start and at finalize. Role change, final death, and reset
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
the SparkStrength M67. Rays and projectiles are nearest-wins (a nearer device takes the hit, the
player behind is not hit); blasts (Wathe grenade, SparkStrength M67) break every device in a sphere
with line of sight and still kill players as before. Sources with no hit or damage geometry never
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
adds no suppression and must not break the client.

For the Black Raven disguise, the allowlist grows by two queries:
- `isRoleSkillBlocked`, through the existing `SparkTraitsKillerBridge`, gates mask use and every
  switch;
- `hasActiveTrait`, through `compat/SparkTraitsBlackRavenBridge`, reads Conscience and Impostor
  only, to decide disguise task money.

When SparkTraits is absent, nothing is blocked and both traits count as inactive. When a present
build's facade lacks or fails a method, the result is "not blocked" for the first query, and "no
disguise task money" (Judge semantics) for the Noelles-native and SparkStrength good-role
policies. SparkWitch-policy task money is paid either way.

Bell Ringer may query only
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

Black Raven disguise state never enters `sparkwitch:player`, `sparkwitch:black_raven_perception`,
or `sparkwitch:black_raven_mark`. `sparkwitch:black_raven_disguise` (`NEVER_COPY`, owner-only
sync, match-id bound) holds the acting role, round clock, unlock and switch times, visited
identities, the Tab B pool snapshot, the Raven wallet shown to the owner, and one stash per
identity. Each stash holds that identity's inventory, overflow, scalars, and wallet. Nothing in
it is ever synced to another player.
- **Role authority.** A disguise never mutates Wathe's role map and never fires `RoleAssigned`.
  The only acting seam is M1: a `@WrapMethod` on `GameWorldComponent#isRole(UUID, Role)` that
  adds exactly one case, a living disguised Raven checked against its exact acting role. It uses
  a server index on the server and only the local player on the client.
- **What stays raw.** `getRole`, `getAllWithRole`, faction, win checks, weapon legality,
  inspections of the Raven by others, and serialization all keep the real Black Raven role.
- **Self-gates only.** SparkWitch migrates only self, recipient, or local-viewer gates to `isRole`,
  and marks each one with the "Widened by the Black Raven acting overlay" comment. Target and
  inspection reads stay raw: Tarot identity resolution and counts, divination candidates,
  `countActiveFactions`, the Professor serum, and the Judge, Prophet, Wraith, and Emma
  inspections.
- **Shops.** The Wathe killer shop (M3) and the Black Raven shop never reach a disguised Raven.
  Perception is refused while disguised, the ledger stays in the Raven stash, and killer instinct
  and Feather marks keep working.
- **Per-identity inventories and wallets.**
  - Each switch is a server transaction that never drops an item. It captures the cursor and the
    crafting inputs before the screen closes, keeps keys, letters, and the mask pinned in place,
    ignores `ClickSlot` for 20 ticks after the switch, and routes `offerOrDrop` remainders into
    the stash overflow.
  - The live Wathe balance always belongs to the live identity. A disguise's first entry starts
    at 0.
  - Killer income earned while disguised goes to the stashed Black Raven wallet, not the
    disguise wallet. The routed sources are:
    - Wathe passive income (both the cap check and the credit) in
      `MurderGameModeBlackRavenWalletMixin`;
    - the kill reward, teammate share, and civilian-death pool in
      `GameFunctionsBlackRavenWalletMixin`;
    - the SparkWitch Hunter poisoner and placer rewards (`HunterFeatureService`);
    - the optional SparkStrength seams below.
  - Known limitation: these SparkTraits killer receipts still land in the live disguise wallet:
    - the killer-trait rewards (Showman, Plunderer, Cornered, and Conscience's kill reward and
      dividend);
    - the Depression fake-death payouts from `DepressionTraitService.rewardFakeDeathKill`: half of
      Wathe's kill reward to the killer, and half of the killer-teammate share, which also reaches a
      disguised Raven when a teammate triggers the fake death. The fake death cancels the kill in
      `KillPlayer.BEFORE`, so `GameFunctionsBlackRavenWalletMixin` never sees it.

    While disguised, none of these receipts adds to the SparkStrength killer-team purse either,
    because `KillerTeamEconomyBlackRavenWalletMixin` rejects income that was not routed to the Raven
    wallet. Routing them would need a SparkTraits public-facade seam (for example a kill-income
    recipient hook on `SparkTraitsApi`) used through `compat/SparkTraitsBlackRavenBridge` and failing
    closed. A mixin into `sparktraits.impl` is not allowed.
- **Death and exits.** At death every stash (inventory and wallet) vanishes. The live set goes
  through Wathe's drop rules, except the mask, which never drops. Role loss discards the stashes
  and keeps the live set. Grand Witch recruitment reverts to the Raven set and wallet before its
  snapshot but keeps the other stashes and visited set; it discards them only after the conversion
  commits, so a refused recruitment (balance overflow) leaves the Raven's disguises intact. A
  `RoleAssigned(black_raven)` for a Raven that is still disguised (a forced role mid-round) reverts to
  the Raven set first, so the kit re-grant leaves exactly one blade, ledger, and mask.
- **Round binding.** The Raven round binding (disguise `beginRound`, Perception `bindCurrentMatch`,
  and `restoreLedgerIfNeeded`) runs in the `sparkwitch:black_raven_finish_initialize` phase of
  `GameEvents.ON_FINISH_INITIALIZE`, ordered after `Event.DEFAULT_PHASE`, so Wathe's
  `GameRecordManager.startMatch` (the binding id) has run whatever the mod initializer order.
- **Skills panel.** The Raven and every disguise never render in the `gui.sparkwitch.skills`
  panel.

Black Raven disguise client seams. The client never predicts a switch; it reads only the
owner-only sync. `client/blackraven/` adds no client mixin and registers these listeners:
- **Open packet.** `BlackRavenClientModule` registers the `OpenBlackRavenDisguiseS2CPacket`
  receiver, which opens Tab B with the server's session id. Registering it is also what lets the
  server's `canSend` check pass for the mask. A client disconnect clears the local acting entry.
  The client never learns the server's open tick, so the book counts ticks since the packet arrived
  and stops offering Tab B rows 20 ticks before the 60 s session TTL, showing "use the mask again".
  A select that still reaches the server on a dead session is refused with the `session_expired`
  actionbar message, never silently.
- **Acting-role changes.** `BlackRavenDisguiseClientState` installs the acting-role change
  listener. On any enter, exit, or switch it:
  - closes `LimitedInventoryScreen` and `TarotDivinationSelectorScreen`;
  - resets the instinct mode to normal and clears the Tarot client state;
  - clears Orthopedist observer data when leaving the Orthopedist identity.
- **Money visibility.** The `CanSeeMoney` phase `sparkwitch:black_raven_disguise_money` is ordered
  before `Event.DEFAULT_PHASE`. It answers only for the confirmed, disguised local Raven on the
  client world.
- **Killer-teammate highlight.** `BlackRavenDisguiseInstinctClientHooks` restates Wathe 1.5.6's
  killer-teammate highlight at `PRIORITY_DEFAULT + 50`. That sits above the disguise role's
  `GetInstinctHighlight.EVENT` results (NoellesRoles and SparkTraits event listeners) and below explicit
  skips (`PRIORITY_HIGH`) and the Feather mark (`PRIORITY_HIGH + 1`). It does not outrank SparkTraits'
  HEAD-cancel mixin `client/mixin/WatheClientMixin#getInstinctHighlight`, whose Toxicologist, Serial
  Killer, Morphling, and trait branches return before the event runs. No batch-1 role reaches those
  branches.
  - Batch-2 gate: before `noellesroles:toxicologist` becomes selectable, put the disguised-Raven
    teammate color ahead of that HEAD path (a higher-priority SparkWitch HEAD inject on
    `WatheClient.getInstinctHighlight`, or a SparkTraits public-facade check) and add a test.

Black Raven disguise shop and wallet seams (SparkStrength is optional and fail-closed):
- **M2.** `mixin/blackraven/ShopUtilsBlackRavenDisguiseMixin` is the outermost wrap of
  `ShopUtils.getShopEntriesForPlayer`, so every cross-player reader of a disguised Raven's shop
  sees only the disguise whitelist. SparkStrength `DemonHunterSniffRules` is such a consumer: it
  reads the target's entries for `psycho_mode`, which no disguise spec or fallback may emit.
- **Killer-team purse.** The optional `@Pseudo` `mixin/blackraven/KillerTeamEconomyBlackRavenWalletMixin`
  (every injector `require = 0`) targets SparkStrength `KillerTeamEconomyService` by name.
  `BlackRavenDisguiseEconomy` reflects only its `recordIncome(PlayerEntity, long)`, to report
  routed kill rewards. Without the seam, disguise purchases can draw on the killer-team purse
  (degraded, but no money is lost).
- **Engineer share.** The optional `@Pseudo` `mixin/blackraven/EngineerPowerRestorationBlackRavenWalletMixin`
  (`remap = false`, `require = 0`) wraps the `addToBalance` call in SparkStrength
  `EngineerPowerRestorationService#distributeRestorationCost`. It routes a disguised Raven's
  restoration share to the Raven wallet. Without it, the share stays in the live disguise wallet.
- **Where the disguise names SparkStrength.** It names SparkStrength implementation classes in
  only these two mixins and in the reflected purse class in `BlackRavenDisguiseEconomy`.
  `compat/SparkStrengthDisguiseCompat` touches only the `sparkstrength:flashlight` registry id.
- **Rest of SparkWitch.** Elsewhere, SparkStrength implementation classes are named by
  `compat/SparkStrengthM67Compat`, `compat/SparkStrengthCoronerCompat`, and the client
  `client/emma/EmmaVisibleName`.

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

`verifyArchitecture` first runs `verifyBlackRavenActingRoleSeams`, which you can also run on its
own.
- **What it does.** It scans the pinned Wathe and NoellesRoles jars for every `isRole`,
  `getAllWithRole`, role-equality, and role-id-equality read of the roles a Black Raven can
  disguise as. Each site must match the classified list in
  `src/test/resources/blackraven/acting_role_seams.txt`, with the same kind, role, owner method,
  tag, and count.
- **When it fails.** It fails on an unclassified site, on count drift, and on any dynamic `isRole`
  whose role argument is not an immediately preceding `GETSTATIC` of a `Role` field.
- **Local-only input.** The list lives under the gitignored `src/test/` tree, so a checkout
  without the local test tree fails `verifyArchitecture`.

Provider bump checklist for the Black Raven disguise. The seam audit covers only the pinned Wathe
and NoellesRoles jars, so a SparkStrength or SparkTraits compatibility-floor bump needs these
manual checks:
- **Wathe or NoellesRoles pin bump:** re-run `verifyBlackRavenActingRoleSeams` and re-classify
  every reported site. Re-check the `addToBalance` counts pinned by
  `GameFunctionsBlackRavenWalletMixin` (`require = 3`, `allow = 3`) and
  `MurderGameModeBlackRavenWalletMixin`.
- **SparkStrength or SparkTraits floor bump:** re-grep that provider's `isRole` call sites,
  including calls whose role argument is not a constant, and every `getRole` equality on an
  allowed disguise role. Classify each one as a self gate (widened) or an inspection (raw).
- **SparkStrength floor bump:**
  - Re-check the `sparkstrength:flashlight` id used by `SparkStrengthDisguiseCompat`.
  - Re-check the `@Pseudo` targets `KillerTeamEconomyService` (`recordIncome`,
    `availableForPurchase`, `personalBalanceAfterPurchase`) and
    `EngineerPowerRestorationService#distributeRestorationCost` (its `addToBalance` call).
  - Both seams use `require = 0`, so a rename silently degrades to the live wallet.
  - Re-check `DemonHunterSniffRules`, which reads the shop through M2 and must never see
    `psycho_mode`.
