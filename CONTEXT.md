# SparkWitch Context

This is a live routing map, not blanket permission to refactor. Structural
changes require explicit owner approval.

## Product Boundary

SparkWitch adds Grand Witch, Accomplice, Apprentice Witch, Murderous Witch, Pig
God, Prophet, Saint, Perfumer, Tarot Reader, Ninja, Kidnapper, Black Raven, Bell Ringer,
Time Stealer, and Angler (`sparkwitch:fisher`) gameplay to Wathe.
It also adds the Control Expert, a non-lethal police variant that shares the Vigilante slots,
and the Seeker, a police variant with a remote car and wall cameras that shares the same slots.
It also adds the Fiend (`sparkwitch:fiend`), a neutral drawn only in rounds with 18+ players that only a
train fall can kill and that may buy a timed Fiend Moment.
It also adds the Insider, a neutral paired with a drawn NoellesRoles Corrupt Cop in rounds with 4+ killers;
the two form Team Jiahao and win together.
It also adds the Blind (`sparkwitch:blind`), a civilian whose screen stays black and who perceives
the world through sounds, helped by a White Cane, the Attune skill and a ComTac VIII headset.
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
- `roles/civilian/prophet/`: passive Death Sense (world-wide corpse pulse every 60 s; skips Scavenger-hidden bodies and,
  per owner decision, SparkTraits Depression fake-death bodies via `compat/SparkTraitsBodyDragBridge`), the
  owner-only `sparkwitch:prophet_player` component (permanent highlight set, necrology,
  Prophecy records), the Prophecy skill registration, and economy. The client outline
  lives in `client/hooks/ProphetCorpseHighlightClientHooks`.
  - The bound Necrology (`sparkwitch:prophet_necrology`): `ProphetNecrologyItem`, the binding rules
    `ProphetNecrologyRules`, and the lifecycle `ProphetNecrologyLoadoutService` (grant on assignment,
    one-copy restore from `ProphetRuntime.tick`, deletion on death, role loss, reset, round end, and stale
    match). Its mixins live in `mixin/prophet/` (death drop, item drop, slot click), parallel to the Black
    Raven ledger's; Fabric use callbacks refuse handing it to item frames, armor stands, allays and decorated
    pots; hand hiding is the NoellesHiddenEquipment registration. The empty
    `net/OpenProphetNecrologyS2CPacket` opens the read-only two-tab book
    `client/prophet/ProphetNecrologyBookScreen`, which reads only `sparkwitch:prophet_player`.
  - Prophecy flow: the ability key's Prophet branch (`client/prophet/ProphetClientModule`, before the
    generic fallback) sends `net/RequestProphecyC2SPacket`; `ProphetProphecyService` checks role, life,
    Fear and the shared skill cooldown, opens a nonce/match-bound `ProphetProphecySessions` entry and
    sends `net/OpenProphecyS2CPacket` (dead names only); `client/prophet/ProphetProphecyScreen` answers
    with `net/ConfirmProphecyC2SPacket`, which the server re-validates (`ProphetProphecyRules`), records on
    the owner-only component, then charges 50 coins and cools down 30 s. Each ledger death carries a serial;
    a Prophecy record made against an earlier death of the same victim (revived, then killed again) is
    treated as fresh. Never in the Witch skill panel;
    both C2S ids are in the Control Expert stun and Seeker remote-view deny-lists.
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
  bound ledger, restricted shop, and lifecycle cleanup. The bound ledger and Raven Mask
  (`BlackRavenInventoryRules`) never drop, never leave their owner's inventory slots, and are
  refused by `UseEntityCallback`/`UseBlockCallback` vetoes for item frames, armor stands, allays,
  and decorated pots.
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
  - Its client presentation lives in `client/blackraven/`: `BlackRavenLedgerBookScreen` (the
    code-drawn ledger panel: Tab A is a ruled perceived-identity grid with player faces, Tab B a
    sectioned, scrolling disguise list with a footer revert button), `BlackRavenLedgerLayout`
    (its pure geometry and hit-testing), `BlackRavenLedgerPaint` (its role-local "raven noir"
    palette and fill primitives), `BlackRavenDisguiseClientState` (synced view,
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
- `roles/killer/timestealer/`: Time Stealer (`sparkwitch:time_stealer`) Clock use gates and server
  ray targeting, the victim-side Time Theft curse and its piercing settle, physical Time Stamps
  (binding, grants, balance, purchases), the restricted native shop, the Timekeeper counter policy
  (`TimekeeperCounter`, AFTER-only), replay formatters, and lifecycle cleanup; its mixins live in
  `mixin/timestealer/` and `client/mixin/timestealer/`, client presentation (stamp HUD, stamp price
  label, Clock crosshair) in `client/timestealer/`. Cross-mod seams go through
  `compat/NoellesTimekeeperPurchase` (the NoellesRoles reduce-time item identity),
  `compat/NoellesSilenceBridge` (NoellesRoles silence), and the `noellesroles:time_keeper` id
  (`TIMEKEEPER`) in `compat/NoellesRoleIds`. Naming: the item is 时间怀表 / Time Pocket Watch
  (`sparkwitch:time_stealer_pocket_watch`, own texture and own pocket-watch curse sounds, kept apart
  from the Bell Ringer's bell); "Clock" stays only as the Java code name (`ClockReadyAt`, class and
  constant names).
- `client/ability/`: generic configurable skill-key-2 registration and role-id
  dispatch only; concrete roles own their handlers.
- `roles/neutral/fiend/`: Fiend rules (`FiendRules`), side-safe predicates (`FiendParticipation`), the
  `sparkwitch:fiend_moment` world component and its pure state, dormant immunity and hit reactions, cooldown
  aura, bomb-pass ledger, swallow block, last-one-standing exclusion (`FiendWinExclusion`), the Fiend Moment
  shop, economy, win listener, lifecycle and owned effects. Its mixins live in `mixin/fiend/` and
  `client/mixin/fiend/`; client presentation (countdown HUD, outline decision) in `client/fiend/`.
- `roles/neutral/murderouswitch/`: Murderous Witch feature, Death Ray, shop,
  and win rules.
- `roles/neutral/insider/`: Insider (`sparkwitch:insider`) rules, Team Jiahao membership predicates,
  pairing with a drawn Corrupt Cop, task-money economy, shop, neutral master key doors, gun-punishment
  exemption, and Team Jiahao win seams. Its mixins live in `mixin/insider/`; client presentation
  (instinct outlines, the Impostor-viewer recolor, the killer cohort line, the "嘉豪同伙" label) lives in
  `client/insider/` and `client/mixin/insider/`, registered once by `InsiderClient.init()`.
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
- `roles/civilian/fisher/`: Angler (`sparkwitch:fisher`, 钓鱼佬) rules and catch table, economy,
  bait shop, round-start rod, server-authoritative drink-tray fishing, transferable fish effects,
  Key Fish doors, tracked pufferfish, replay formatting, and lifecycle cleanup. Subpackages:
  `item/` (rod, bait, edible fish, Key Fish), `swordfish/` (the single-use Swordfish, its payload,
  server release pairing, consumption, and friendly-fire death), and `spirit/` (the Glimmerfish
  window, its `sparkwitch:fisher_spirit` component, door passing, collision exemption, owned
  invisibility, and safe door exit). Its mixins live in `mixin/fisher/` and
  `client/mixin/fisher/`; client presentation (Glimmerfish outlines and HUD, hidden hands,
  Swordfish crosshair and stab dispatch) in `client/fisher/`.
- `roles/civilian/blind/`: the Blind (`sparkwitch:blind`, 盲人): `BlindRules` (ids and numbers), the
  owner-only `sparkwitch:blind` component (`BlindComponent` over the pure `BlindTimers`),
  `BlindParticipants` (shared real-role, ComTac and perception-range predicates), and
  `BlindFeatureService` (the single server wiring entry). Subpackages: `item/` (White Cane, ComTac
  VIII), `net/` (the `sparkwitch:blind_pulse` and `sparkwitch:use_blind_attune` payloads and the
  single `BlindPulseSender`), `perception/` (server sound and voice perception:
  `BlindPerceptionWiring` (events, the per-tick pass and cleanup), `BlindSoundPerception` (the mixin
  and voice entry points), the pure `BlindSoundRules` (filters, one-shots, radii) and
  `BlindSoundAttribution`, `BlindPulseFanout` (one source to every Blind in range),
  `BlindPerceptionTargets` (the listening Blinds), `BlindEmitterThrottle`, and `BlindVoiceInbox`
  (the voice-thread hand-off)), and `kit/` (cane, Attune, ComTac, economy, shop and lifecycle:
  `BlindKitWiring` (the kit's single server wiring: its own `sparkwitch:blind_finish_initialize`
  phase, `RoleAssigned`, `KillPlayer.AFTER`, `ResetPlayer`, `ON_FINISH_FINALIZE`, disconnect,
  `END_SERVER_TICK`, server stop, the item-frame and armor-stand use guards, and the blackout
  Blindness exemption),
  `BlindKitRules` (pure timing, gates, slots and money rules), `BlindLoadoutService` (grant,
  per-tick upkeep, strip), `BlindCaneService` (server-validated `Item#use` tap, public
  `sparkwitch:blind.cane_tap` sound, CANE pulse and in-window re-scan), `BlindAttuneService`
  (`sparkwitch:use_blind_attune`), `BlindShopService`, `BlindEconomyService`, `BlindInventoryRules`
  and `BlindItemDrops` (bound items), and `BlindSounds`). Its server mixins live in `mixin/blind/`
  (`ServerWorldBlindPerceptionMixin` and the bound-item `BlindKitDeathDropMixin`,
  `BlindKitDecoratedPotMixin`, `BlindKitPlayerDropMixin` and `BlindKitScreenHandlerMixin`); the
  optional Simple Voice Chat listener is `voice/BlindVoicePerceptionListener`, registered by `voice/SparkWitchVoiceChatPlugin`.
  Client presentation lives in `client/blind/`: `BlindClient` (module and pulse receiver),
  `BlindView` (the one activity predicate), `BlindPerceptionClientState` (pure pulse and
  perceived-player memory), `BlindClientStatus` (HUD remaining-tick view),
  `BlindComTacHeadVisibility` (the in-round ComTac head hide), `render/` (the private echo post
  processor `BlindEchoView`, depth capture and fail-closed hint in `BlindRenderWiring`,
  `BlindSilhouettePass`, the Iris check `BlindShaderPackCheck`, the fog-culling guard
  `BlindTerrainFog`), `gate/` (`BlindClientGates` and the
  pure `BlindGateRules` behind the `BlindGate*` vetoes, client-only bumps, the plain
  `BlindCrosshair`, and the Attune ducking in `BlindAttuneDucking`) and `kit/` (the owner-only HUD
  and the Attune key handler). Its client mixins live in `client/mixin/blind/`: the eleven
  `BlindGate*` vetoes, `BlindGameRendererMixin` (the end-of-frame pass), `BlindEchoSilhouetteMixin`,
  `BlindTerrainFogMixin`, `BlindAttuneSoundSystemMixin`, and `BlindComTacHeadRenderMixin`.
- `client/armor/`: the four vanilla armor slots (`PlayerScreenHandler` 5..8) on Wathe's
  `LimitedInventoryScreen` for every player (D10), client presentation only.
  `LimitedInventoryArmorLayout` (pure geometry, the shift-click plan and the full-hotbar rule),
  `LimitedInventoryArmorSlots` (gate, frame, empty icons, hit test, shift-click and pick-up adapters)
  and `LimitedInventoryArmorPanel` (the block as a visible, inactive `ClickableWidget`). Its one
  mixin, `client/mixin/armor/LimitedInventoryArmorSlotsMixin`, has seven seams on Wathe's
  `LimitedHandledScreen`, each pinned in `watheClientMixinContracts`: `init` TAIL adds the panel,
  `render` TAIL hands an unset `focusedSlot` to a hovered armor slot, `getSlotAt` RETURN answers
  armor slots where no hotbar slot hit, `isClickOutsideBounds` RETURN keeps a Touchscreen tap on the
  block inside, a `@WrapOperation` on the `onMouseClick` call in `mouseClicked` turns an eligible
  shift + left press into one vanilla `SWAP`, and `close` HEAD plus the `tick` INVOKE before
  `closeHandledScreen` (Wathe's forced close during a fade or after death) run the close-time
  return below before the close packet. Rules:
  - **Gate.** The panel is added at `init` only for `LimitedInventoryScreen` on a
    `PlayerScreenHandler` and a confirmed SparkWitch server; every other seam acts only while that
    panel exists. Elsewhere Wathe's hotbar-only screen is untouched.
  - **Server.** Unchanged. Every move is a vanilla click (PICKUP, QUICK_CRAFT drag, PICKUP_ALL,
    number-key or shift `SWAP`) that the server checks with `ArmorSlot.canInsert` /
    `canTakeItems`. No packet, NBT or server mixin.
  - **Shift-click** (mouse mode, one `SWAP` either way). Hotbar item → its preferred armor slot when
    empty (as vanilla quick-move would); worn piece → first empty hotbar slot (Wathe hides the main
    inventory vanilla would fill). Anything else stays Wathe's PICKUP.
  - **Full hotbar** (mouse mode). A worn piece cannot be picked onto an empty cursor while every
    hotbar slot is taken (a vanilla close puts it in the hidden main inventory); the press sends
    nothing, and number-key swaps still exchange it. Touchscreen release pick-ups are not guarded.
    In either mode, while every hotbar slot is taken a close first clicks a cursor armor piece back
    into its own empty armor slot (one vanilla PICKUP); with a free hotbar slot vanilla's close
    offer already lands in the hotbar.
  - **Layout.** A 50x50 2x2 block (head, chest / legs, feet) at `[X-54, X-4) x [Y-9, Y+41)` beside
    Wathe's 176x32 strip, clear of the shop row, role head rows, logo and the right-hand info card at
    every scaled size the layout test covers (320 px wide and up). Its frame is cut at draw time from
    Wathe's own `limited_inventory.png`; no copied art.
  - **Neighbours.** The SparkWitch/SparkTraits info card and SparkAssist's guidebook route around
    the panel because it is a visible `ClickableWidget` child; being inactive, it never consumes a
    click.
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
7. mana regeneration
8. Saint ability

The Prophet no longer ticks here; `sparkwitch:prophet_player` ticks itself.

Do not reorder these calls. The existing component ids remain `sparkwitch:player`
and `sparkwitch:world`; packet field order and NBT keys must remain stable.
`sparkwitch:fire_death_ray` carries the caster's yaw and pitch at key press (the attack key
is handled before that tick's rotation packet); a payload without them still decodes and
falls back to the server rotation. The server still decides every Death Ray and shotgun hit.
Perfumer state uses the separate owner-only `sparkwitch:perfumer_player`
component so its target lists are never added to the shared player packet.
Prophet state lives in the separate owner-only `sparkwitch:prophet_player`
component (`NEVER_COPY`, match-id bound), never in the shared player packet. It
holds the Death Sense countdown, an only-growing set of highlighted body entity
UUIDs, the necrology, and Prophecy records. The old `DeathOmenTicks` and
`DeathOmenBodyUuids` NBT keys are no longer read or written.
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

Time Stealer state never enters that shared schema either. The victim-owned `sparkwitch:time_theft`
(stealer UUID, match id, theft tick, last applied stage) and the stealer-owned
`sparkwitch:time_stealer` (authoritative `ClockReadyAt` tick, match id, and the `UndeliveredStamps`
retry counter) are both `NEVER_COPY`, never synced, and bound to the current Wathe match id (bound
in a SparkWitch `ON_FINISH_INITIALIZE` phase ordered after Wathe's default phase, and re-bound by a
holder's own tick if that was missed); a stale match clears a curse. The Clock is a plain `Item#use`
with a server-side ray (7.0 blocks, unexpanded-hitbox line of sight, ineligible players transparent)
and no custom packet; the 45 s cooldown (also at round start) is an authoritative server tick
written only after a committed theft (or a Seeker device absorb, below), so a refusal costs nothing.
The curse advances only from the victim's own component tick on absolute world ticks: silent for 15
s, then Slowness I-IV (no particles) and a chime sent to the victim alone at 15/20/25/30 s, and the
lethal fifth chime at 35 s. Second owner-approved exception: the Clock curse kill
(`sparkwitch:time_stolen`) is a forced, terminal kill (registered as SparkTraits-terminal when the
server starts) that settles exactly once, after the curse and its Slowness are cleared, and pierces
every role, item, and trait protection. Only a Timekeeper purchase lifts a curse before it settles;
curses keep running after the Time Stealer dies, changes role, or disconnects.
`TimeStealerRules.settleActor` picks the outcome: ATTRIBUTED when the stealer is online, still
exactly the Time Stealer, and SparkFactionAPI's structural `canAffectPlayer` allows the kill;
UNATTRIBUTED when the stealer is offline or no longer the Time Stealer, so the curse still kills
with no killer; VETOED when that structural veto refuses the attributed kill, so the curse is spent
and never turned into an unattributed kill. The kill always runs inside
`JudgeKillAttribution.runWith` for the Time Stealer's UUID, so a Judge sentence on that UUID still
blocks it. A vetoed or blocked curse is spent, never retried, and changes neither the round time nor
the stamps. The Saint HEAD guard ignores Wathe's `force` flag and opts out through
`TimeStealerRules.piercesProtection`. As with the bell toll, protections that run as
`KillPlayer.BEFORE` listeners still pay their normal costs before the forced kill proceeds. Wathe's
civilian-death time bonus (+30 s) is suppressed only for `sparkwitch:time_stolen` through
`mixin/timestealer/GameFunctionsTimeStolenClockMixin` (`@WrapOperation` on the single
`GameTimeComponent.addTime` call in the five-argument `killPlayer`, `require = 1`, `allow = 1`, no
`@Redirect`); every other death reason keeps it. Only a confirmed death in the same `ACTIVE` match
that SparkTraits did not intercept then sets the round time to exactly `max(0, t - 600)` (it may
drain to 0, and then the civilians win on time) and grants one stamp to a living exact Time Stealer.
Removing the curse's Slowness is chain surgery (`SlownessChain`, `TimeTheftSlowness`): the live
effect is read through `StatusEffectInstance.CODEC`, only the curse's own node is taken out, and
foreign Slowness (a Control Expert stun, Grand Witch Heaviness) keeps its level and duration. Known
limitations: vanilla already discards a same-or-weaker, same-or-shorter effect that the curse fully
covered; a stage whose application left an existing same-level foreign node untouched (it already
lasted as long or longer) owns nothing and removes nothing, but a same-level foreign Slowness
applied after the chime that is indistinguishable from the curse's node (same flags, remainder
within one tick) is removed with it, and after a reload the curse conservatively owns no node (the
ownership flag is not persisted); and a changed chain is written back as a new effect instance, so a
role that tracks its own effect by identity (the Control Expert's owned-effect record) may not
recognise a restored foreign instance, which is harmless because Wathe clears every effect on reset.
The only rescue is the NoellesRoles Timekeeper counter: a committed `ShopPurchase.AFTER` purchase
whose display item is `noellesroles:timekeeper_reduce_time`, in an `ACTIVE` round, by a living
player whose current Wathe role is exactly `noellesroles:time_keeper` and whose SparkFactionAPI
effective faction is known and not `wathe:killer`, lifts every curse in the world, grace period
included, with no immunity window and no change to cooldowns, stamps, or time beyond the purchase
itself; a victim who was offline during the purge is lifted on their first tick back in the same
match. An Impostor Timekeeper (killer faction) and a SparkStrength Coroner disguised as a Timekeeper
(still the Coroner role) never count. The policy lives in
`TimeStealerRules.countsAsTimekeeperRescue` and `TimekeeperCounter`; SparkWitch never mixes into
`TimekeeperShopHandler$1` or its `addTime` call, registers no `ShopPurchase.BEFORE` listener for the
counter, and never measures the time change, and the AFTER listener catches its own exceptions so it
never throws back into `PlayerShopComponent.tryBuy`. Time Stamps (`sparkwitch:time_stamp`, stack of
64) are the role's physical currency; no component holds a stamp count. The balance is the stamps in
the holder's own `PlayerInventory` plus the open handler's cursor: the server computes it for
purchases, and the owner's client computes the same value from its synced inventory for the price
label and HUD. The Clock and stamps are bound items: they share `TimeStealerInventoryRules` and the
three item-generic mixins in `mixin/timestealer/`, never become item entities, never leave the
holder's own inventory slots, never death-drop, and are hidden from other living players' held-item
view through `NoellesHiddenEquipment`; beyond the Bell Ringer's rules they also refuse shift-click
moves and offhand swaps, and a living holder keeps exactly one Clock in the hotbar. A stamp stack
caught by the `dropItem` guard is emptied in place (move semantics) and re-delivered only to a
living holder, so no drop path duplicates stamps. Only a living, playing, exact Time Stealer may
hold stamps (spectator mode does not count against it, so a Time Stealer swallowed by the
NoellesRoles Taotie keeps the Clock and stamps and still receives grants); everyone else is stripped
on role change, terminal death, reset, finalize, and a staggered 20-tick sweep. Grants go
hotbar-first (an existing stack, then an empty hotbar slot), then into hidden main slots or the
offhand, and only then into the server-only `UndeliveredStamps` counter, which purchases cannot
spend; stamps are merged into one stack each tick but never moved into a freed hotbar slot. Stamp
purchases run inside the 0-gold entry's `onBuy` on the server thread: they reserve (remove) the
cost, run the native effect, and undo the reservation into the exact slots if the effect fails, so
paying can free the hotbar slot a grenade or Psycho bat needs. There are no posthumous stamps: a
Clock kill that lands after the stealer's death, role change, or disconnect grants nothing. A
same-match re-assignment of the Time Stealer role keeps stamps and the cooldown without knowing the
previous role (a stray holder is swept within 20 ticks anyway). Grand Witch recruitment refunds each
stamp, like the Clock, at the unknown-item price of 25 gold; the recruitment module is unchanged. A
nearer Seeker device absorbs the Clock and breaks only when an eligible target stands behind it: the
break is recorded as `SeekerBreakSource.CLOCK`, nobody is stolen from, and the Clock goes on
cooldown; unlike the Taser and the Feather Blade, a Clock miss stays free and never breaks a device.
The shop is a role-gated deny-list rewrite of the native killer shop (`TimeStealerShopRules`, built
on both sides from the synced role): `poison_vial` and `scorpion` are removed, the native `grenade`
is replaced in place by the 3-stamp `sparkwitch_time_stealer_grenade` with the normal Wathe grenade
display (so SparkStrength still appends its own coin-priced M67), `psycho_mode` is replaced in place
under the same id (3 stamps, native cooldown kept), and the 1-stamp
`sparkwitch_time_stealer_add_time` (+60 s) follows it; every other entry keeps its price, stock,
cooldown, and callback, and the list is never cleared. The role's `ShopPurchase.BEFORE` listener
checks the exact role first and only ever denies, never `allow`s. The Time Stealer never renders in
the `gui.sparkwitch.skills` panel.

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
`PostEffectProcessor`, never `GameRenderer.postProcessor`. The Blind echo view is another private
processor; the shared `client/mixin/PostEffectProcessorAccessor` only reads a private processor's
pass list (for per-pass matrix and array uniforms, never adding or removing passes) and is used only
by `client/blind/render/BlindEchoView`. A placed camera's look (`LOOK_YAW`,
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
the Angler Swordfish stab (`SWORDFISH`, its own payload and `SeekerDeviceHits.onSwordfishStab`),
the NoellesRoles throwing axe, the thrown Ninja shuriken, the Black Raven feather blade, the
Time Stealer Pocket Watch, the Murderous Witch Death Ray, the Wathe grenade (including the SparkTraits Bomb Maniac grenade), and
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

Angler state never enters that shared schema either. `sparkwitch:fisher_spirit` (`NEVER_COPY`,
never saved) holds only the Glimmerfish window; its sync packet is one VarInt: 0 inactive, 1 for
every observer while active, remaining ticks + 1 for the owner. Every client needs the flag
(two-sided collision exemption, hidden hands, instinct skip); the client countdown never clears it.
The window is 156 ticks, server-authoritative, restarted by another Glimmerfish, kept across role
changes, and ended on expiry, terminal death, reset, finalize, disconnect, or loss of participation.
`mixin/fisher/FisherDoorPassingMixin` empties the collision shape of Wathe `DoorPartBlock` and
vanilla `DoorBlock` doors (never trapdoors or gates) only for a glimmering player's entity context,
so client prediction and server validation agree; it skips the exemption inside the shared
`util/RaycastShapeScope`, so rays (sight, aiming, weapons) still see the door;
SparkFactionAPI's entity-collision exemption
reads the same flag on both sides (`noellesroles:no_collision` alone is one-sided). Glimmerfish
claims only the invisibility instance it created in an empty slot; any foreign merge gives up its
removal right. Expiry inside a door first tries to move the player to a validated safe spot (door
sides, lateral offsets, then the last safe position); if none validates it only logs and clears.
The rod, bait and edible fish are hidden in hand through `NoellesHiddenEquipment`; the Key Fish and
Swordfish stay visible. Fishing is a Fabric `UseBlockCallback` in its own phase after the existing
interaction guards, for a main-hand rod on `wathe:drink_tray` only: the client answers SUCCESS (the
ordinary tray swing, identical to taking a drink), the server answers CONSUME on every handled
path, and before rolling reserves an empty hotbar slot (or the single-Bait slot this cast empties);
a no-room refusal only sets the 20-tick rod cooldown, never rolls or spends bait. Catches go only
into the hotbar, tray contents and poison are never touched, and feedback and sounds are private. Fish use never calls vanilla food
completion, so Wathe's EAT and DRINK tasks never complete from a fish; fish use and the Glimmerfish
start share one living-participant check (`FisherParticipants`), and a Glimmerfish is consumed only
after its window really starts. The Key Fish opens a closed
locked cabin door or train door on Wathe's `DoorInteraction.EVENT`, never a jammed one, and is
consumed only after a real closed-to-open transition; locks and auto-close are unchanged. Pufferfish
are vanilla entities tagged and tracked by the Angler: while puffed they drain 0.1 sanity from any
living REAL-mood participant they touch (one shared two-second cooldown per player, SparkFactionAPI
veto for anyone but the Angler, and no one but the Angler while the Angler is offline), carry an
empty loot table, and are discarded after ten seconds or
at lifecycle sweeps. The Swordfish is an independent `Item` (never a `KnifeItem`, never the Wathe
knife payload, identity, tags, or cooldown): SPEAR charge, release after at least 11 held ticks
on the client (the server accepts 9 for packet timing), 3 blocks, its own `sparkwitch:swordfish_stab`
payload paired once with a five-tick server release bound to the exact main-hand stack. The server
validates the submitted target against its own block-clipped 3-block ray along the attacker's current
rotation (player boxes expanded by 0.25, device boxes by their targeting margin plus 0.25): a nearer
Seeker device intercepts, a different nearer player rejects the request. It also re-checks
participation, stun, Seeker session lock, Last Escape, and the SparkFactionAPI veto; an accepted hit
consumes the stack even when parried or protected; both effective factions are captured before the
non-forced `sparkwitch:swordfish_stab` kill, and only a confirmed terminal civilian-on-civilian
death (not Last Stand, not a fake death) also kills the attacker with `SHOT_INNOCENT` (an Impostor
attacker is exempt). The Angler never renders in the `gui.sparkwitch.skills` panel.
Fiend state never enters that shared schema either. `sparkwitch:fiend_moment` is a world component synced to
every player; its packet carries only a presence flag, the moment Fiend's UUID and remaining ticks (never absolute
server time or the match id), clients count down only for display, and it is never persisted. It also keeps a
server-only, never-synced spent ledger bound to the match id. A dormant Fiend (Fiend role, playing and alive, not
the moment Fiend, not spent) dies only to `wathe:fell_out_of_train`, `wathe:escaped` and `wathe:vanilla_death`:
`mixin/fiend/GameFunctionsFiendImmunityMixin` is a cancellable HEAD guard on Wathe's 5-arg `killPlayer`
(priority 1100, so SparkFactionAPI's affect veto runs first) that ignores `force`, so the owner-approved piercing
kills (bell toll, time curse) do not reach it either. Only a kill that guard cancelled pays a hit reaction, once
per attack: `wathe:gun_shot` (every gun) +50 gold, Speed III 5 s and a 20 s cooldown floor, applied at
END_SERVER_TICK through SparkTraits' exact write with a vanilla fallback, on every other participant within
8 blocks (never shortened; NoellesRoles `timed_bomb` is skipped, its cooldown is the Bomber pass gate); a hand-held
stab, recognised only by `FiendStabScope` around Wathe's `KnifeStabPayload` receiver, +50 gold and 4 notes;
`wathe:bat_hit` and `sparkwitch:ceremonial_blade` +100 gold. A bomb the Fiend passed that kills its direct
recipient pays +50 only while the Fiend is still dormant (server-only `FiendBombLedger`). The Taotie cannot
swallow a dormant Fiend (SparkWitch guard on NoellesRoles `TaotiePlayerComponent.swallowPlayer`; SparkFactionAPI's
NoellesRoles packet guards do not match the pinned 1.7.6 jar), and a dormant Fiend is never a Serial Killer target
(`SerialKillerPlayerComponentFiendTargetMixin` filters `getEligibleTargets` and `isTargetValid`; the Bodyguard
copies that target). A dormant Fiend counts as not alive in every last-one-standing count: `WitchWinConditions`
and Murderous Witch `checkWin` skip it directly, and NoellesRoles'
Jester-moment and Corrupt Cop loops (`lambda$registerEvents$14` alive-check ordinals 6 and 9),
`countAliveAndNotSwallowed` and Taotie `hasSwallowedEveryone` reach `FiendWinExclusion` through additive
`@WrapOperation`s pinned to b58fa5f. The Fiend Moment is a 200-gold, stock-1 shop entry whose all-or-nothing
`onBuy` starts it (crowbar, Speed IV and one whiskey-shield layer, all for 2400 ticks); the crowbar carries the
`sparkwitch:fiend_moment_crowbar` custom-data marker and every marked stack is taken back when the moment ends
without a win, and a disconnect (`wathe:escaped`) ends it as "ended", not "slain". `FiendWinService` runs in
phase `sparkwitch:fiend_moment_win`, ordered before `Event.DEFAULT_PHASE` on `CheckWinCondition`: no moment →
abstain; the moment Fiend offline, dead, swallowed, re-roled or the match changed → end the moment (a swallow
also marks it spent) and abstain; complete → `neutralWin`; otherwise `block()`, so every other win, `TIME`
included, waits. The moment Fiend's crowbar cooldown is written as exactly 5 s after a door pry or vent-hatch use,
without a second redirect. The client outline is a cancellable HEAD on `WatheClient.getInstinctHighlight`
(`remap = false`, priority 500; lower-priority HEADs run first, so it precedes SparkTraits, Wraith and Black
Raven): while a moment is active the moment Fiend sees every other playing, living, non-spectator player and every
other viewer sees the moment Fiend, both in `FiendRules.COLOR`; other pairs fall through. The Grand Witch
Obscure/Fear `@WrapMethod` veto (`WatheClientFearInstinctMixin`) exempts those moment pairs, like the Final Moment
(owner decision, 2026-09-30); its swallow veto still applies. The countdown HUD is a
`HudRenderCallback` line for every player, never the action bar. The Fiend is absent from
`isRegisteredSparkWitchRole` and `WitchSkillRegistry` and never renders in the `gui.sparkwitch.skills` panel.
The Insider (`sparkwitch:insider`) has no component; every rule reads synced roles. Wathe never draws it
(`appearanceCondition(context -> false)`). `MurderGameModeMixin` calls, in order, the Hunter→Orthopedist
pairing, the Witch assignment, then `InsiderAssignmentService`, all before Wathe's civilian pass: when a
Corrupt Cop (exact id `noellesroles:corrupt_cop`) was drawn, no Insider exists, the role is enabled, and at
least `InsiderRules.MIN_KILLERS` killer-team roles were assigned (`canUseKiller`, forced killers included,
later SparkTraits Conscience compensation not counted), one uniformly random unassigned player becomes the
Insider, taking a civilian seat rather than a neutral slot. A forced Insider is never demoted or re-paired.
At `ON_FINISH_INITIALIZE` each living Insider receives one NoellesRoles neutral master key (`offerOrDrop`, so a
full inventory drops it at its feet), never removed; SparkWitch's own `DoorInteraction` listener lets an Insider
holding it open train doors and key-locked doors (ALLOW, then a 200-tick key cooldown; DENY while cooling;
blasted, jammed and open doors PASS), so SparkStrength is not required. It never conflicts with NoellesRoles' or
SparkStrength's key listeners: for an Insider each of them answers PASS or the same ALLOW/DENY. The Corrupt Cop gets
the same doors only with SparkStrength: NoellesRoles alone opens only key-locked room doors for it, and its train
doors come from SparkStrength's any-neutral key rule. A `ShouldPunishGunShooter`
listener cancels the innocent-shot revolver punishment for an Insider, as NoellesRoles does for the Corrupt Cop.
SparkWitch owns all Insider money: 0 at `RoleAssigned`, +50 per task while ACTIVE and alive, never an Impostor
skip. Its `CanSeeMoney` answers ALLOW for a living Insider and DENY for a dead one (with no answer, Wathe's
`canAccessShop` fallback would keep the counter, because the shop is built from the role alone), with no
running-state gate, because SparkTraits rolls traits while STARTING and offers Task Master to this FAKE-mood role only
through that answer. The shop is rebuilt on both sides, gated on the exact role only: capture `sparktraits:*`,
clear, revolver 150, crowbar 50, and (only when the SparkStrength tablet item is registered) the tablet at 100
under SparkStrength's own entry id `sparkstrength_tablet`, each stock 1, then restore. The Insider is in SFA `PoliceRoles`, so its tablet joins the police channel. That also makes it a SparkStrength
police elector for meetings and votes (`TabletChannelResolver.isPoliceElector`) and makes
`TabletShopRules.canBuyTabletRole` true; SparkStrength's final shop append skips its own 150-price tablet only because
the Insider entry reuses the id `sparkstrength_tablet`, so the shared entry id is load-bearing.
Team Jiahao (every Corrupt Cop and every Insider, by current role) applies only in a round that has an Insider
(any role-map entry with the Insider role, online or not, dead or alive); without one, all three wraps below
return NoellesRoles' own value, so several forced Corrupt Cops behave exactly as in NoellesRoles and the title
stays "黑警胜利！". With an Insider it wins inside NoellesRoles' Corrupt Cop win branch, so NoellesRoles' win order stays Vulture > Survival Master > Pathogen > Jester > Taotie > Team Jiahao >
Shadow Jester. `mixin/insider/NoellesRolesJiahaoTeamWinMixin` wraps three calls in `lambda$registerEvents$14`
(pinned NoellesRoles `b58fa5f`, ordinals fixed by `JiahaoTeamWinContractTest`): `getAllWithRole` ordinal 6 so the
living-cop lookup sees the whole team and any living member blocks killer and passenger wins;
`isPlayerPlayingAndAlive` ordinal 9 so the living team counts once; and the single-argument `neutralWin`
ordinal 5 so every online member co-wins, dead or alive, with the first living Insider, else the first online Insider, as primary (a `WinResult` can only name online
players).
Decisions live in `JiahaoTeamWinRules`, world reads in `JiahaoTeamWinSeam`; the Corrupt Cop Moment count stays
NoellesRoles' own. Wathe titles a neutral win by the first winning round-end row in role-map order, so
`mixin/insider/GameRoundEndComponentJiahaoTitleMixin`, just before the explicit-winner sync, handles a Team
Jiahao win (a winning team row in a round with an Insider): it marks every other team row as a winner, offline
`LEFT` / `LEFT_DEAD` rows included, then moves an Insider row first (the one the win named, else any). The end
screen reads "嘉豪阵营胜利！", and Wathe's `didWin` and `GameRecordManager.endMatch` read the same rows. Every
other win keeps Wathe's rows.
Insider presentation is client-only. One `GetInstinctHighlight` listener answers `always` only while its condition
holds. At priority 65 (below SparkStrength's tablet member and suspect marks at 70/80, which keep their colors), a
living Insider holding instinct sees every other living, visible player in `0x00FFD0`; the Corrupt Cop is answered
at 93 in `0x193264`, so the partner stays navy even under a tablet mark. At priority 93 (above SparkStrength's
Corrupt Cop x-ray at 90, below the Seeker mark 95, `skip()` 100 and suppression 102), a living Corrupt Cop sees the
living, visible Insider in `0x00FFD0` while holding instinct or during its Moment vision window, and a killer-instinct
viewer (`isInstinctEnabledAndIsKiller() && !canSeeSpectatorInformation() && isKiller()`, exactly when Wathe's default
would paint the target red or green, a promoted Saboteur Wraith included) holding instinct sees the living Insider in
the Impostor blue `0x0013FF`, invisible or not, as SparkTraits paints an invisible real Impostor. Targets hidden by
SparkTraits' `isInstinctHidden` get no Insider answer.
`client/mixin/insider/WatheClientInsiderImpostorHighlightMixin` is a `@WrapMethod` on
`WatheClient.getInstinctHighlight` that encloses every other injection and rewrites only SparkTraits' exact civilian
green `0x4EDD35` to `0x0013FF`, for a living local Impostor looking at a living Insider, visible or not. SparkTraits
effective killers get Wathe's red cohort line on a living Insider through `ShouldShowCohort.show(105)`, and
`InsiderCohortRoleNameMixin` draws the mint `game.tip.sparkwitch.jiahao_cohort` label between an Insider and any
Team Jiahao member, both ways, with the witch cohort trigger. The Insider shares killer-style instinct light
through `WitchInstinctClientHooks`. The Insider never renders in the `gui.sparkwitch.skills` panel.

Blind state never enters that shared schema either. `sparkwitch:blind` (`NEVER_COPY`, never saved)
holds the cane and Attune windows as absolute server world ticks plus a server-only match id and the
server-only match of the last ComTac purchase; its owner-only sync packet is four VarInts of remaining ticks (cane cooldown, cane active, Attune
cooldown, Attune active), never absolute ticks, and the client rebuilds them against its own world
time. Every Blind gate reads Wathe's real role (`getRole`), never the Black Raven acting overlay;
the Blind is on the Black Raven `DENYLIST`, is not police, and is not in
`isRegisteredSparkWitchRole` or `WitchSkillRegistry`. `sparkwitch:blind_pulse` goes only to a
living Blind and carries a position, radius, duration, kind, an optional emitter entity id and an
optional list of entity ids, never a UUID, name or role. `sparkwitch:use_blind_attune` is empty and
sits on the Control Expert stun, Seeker session and Grand Witch Fear deny-lists. The black screen
never uses vanilla Blindness, and Wathe's blackout Blindness skips a living, real Blind (C7: a
`BlackoutEffect.BEFORE` cancel that leaves every other player to the other listeners).
The Blind never renders in the `gui.sparkwitch.skills` panel.

Blind perception is server-side and read-only. `mixin/blind/ServerWorldBlindPerceptionMixin`
observes `ServerWorld#playSound`, `#playSoundFromEntity` and `#createExplosion` through
non-cancellable HEAD injects that never change the call; private sounds (`playSoundToPlayer`, raw
sound packets, `syncWorldEvent`) are never perceived, so a sound a Blind must perceive has to go
through one of those three public calls. `BlindSoundRules` drops the AMBIENT, MUSIC and RECORDS
categories, every `*:ambient.*` id and `minecraft:intentionally_empty`; explosions skip these
filters. Objects light the environment only (an OBJECT pulse, never a player): Wathe's
`block.door.toggle` (always actorless, auto-close included), anything played from or exploded by a
non-player entity, and the Wathe light and no-power button toggles when no actor is named, which
are dropped instead while a Wathe blackout runs (its flicker is the same call).
`BlindSoundAttribution` credits any other sound to the named actor within 8 blocks, else to a player
source entity, else to the nearest playing, living player whose hitbox contains the sound or lies
within 0.3 blocks of it (an eye-level gunshot, a stab at the victim), else to an object (so
firecracker decoys and Seeker devices are objects). An active Wraith or a player swallowed by the
NoellesRoles Taotie is silent: its own sounds (named actor or source) are dropped, but it never wins
the hitbox search and never drops a sound merely near it; invisible players are perceived.
`BlindPulseFanout` sends one pulse (4-block reveal) to every living, playing, real, unswallowed
Blind in the same world within that Blind's range (10 blocks, x3 with a worn ComTac, x5 during Attune: 30/50/150): SOUND or VOICE with the emitter's
entity id for another player, SELF for the Blind's own sounds, OBJECT otherwise. Each Blind
throttles each sound emitter to one pulse per 10 ticks; the one-shot sounds (the Wathe revolver,
grenade and door, the SparkStrength M67, the NoellesRoles bomb, the generic explosion, every vanilla
door, trapdoor and fence-gate open or close, and every `createExplosion`) bypass that throttle.
Voice needs the optional Simple Voice Chat (compile-only, never a dependency):
`voice/BlindVoicePerceptionListener` handles `MicrophonePacketEvent` at `Integer.MIN_VALUE`, after
SparkWitch's Wraith and Kidnapper speaker mute at `Integer.MAX_VALUE`, skips cancelled and empty
packets, and on the voice thread only records the speaker's UUID and whisper flag into
`BlindVoiceInbox`; the `END_SERVER_TICK` drain sends at most one VOICE pulse per speaker every 10
ticks, at half range for a whisper. Only proximity voice counts (no group, or an OPEN group), only
the speaker's own microphone frames count (a relayed copy never pulses at the relay point), a Blind
who cannot hear voice (NoellesRoles silenced, or Wathe psycho mode, which SparkTraits' Depression
psycho uses and mutes for its listener) gets none, and without Simple Voice Chat there are no voice
pulses.

Blind presentation is client-only. `BlindView.isActive` holds on a confirmed SparkWitch server while
a round runs for a real Blind (`getRole`) who is playing and alive in Wathe's sense (not marked
dead) or swallowed; the camera is not part of it, so a SparkTraits Last Stand or Depression fake
death keeps the view and every gate on (black) and only a real Wathe death ends it. Every gate
re-reads it per call. `client/blind/render/BlindEchoView` is a private
`PostEffectProcessor` (`sparkwitch:shaders/post/blind_echo.json`, programs
`sparkwitch_blind_silmask`, `sparkwitch_blind_blur` and `sparkwitch_blind_echo`): the world depth is
captured at `WorldRenderEvents.BEFORE_DEBUG_RENDER` (after flushing the pending block-entity layer and
the Fast/Fancy dropped-item layer), where `BlindSilhouettePass` also re-renders the
perceived players into a private silhouette target (their features and labels stripped by
`BlindEchoSilhouetteMixin`), and `client/mixin/blind/BlindGameRendererMixin` runs the pass after
`GameRenderer#render`'s `Framebuffer#beginWrite(Z)` with `shift = AFTER`, so it draws over the
Seeker and Black Raven filters injected at the same call, and the HUD is drawn on top. The view
fails closed to black, never to the plain world: an Iris shader pack in use (reflective check; an
error counts as in use), a load failure (kept until reconnect or resource reload), or 20 consecutive
missed depth captures paint black and show `hud.sparkwitch.blind.view_unavailable`, and a swallowed,
off-camera or fake-death spectator Blind sees black with no line art. While the view is active,
`BlindTerrainFogMixin` lifts the terrain fog end to the render distance right before
`WorldRenderer#setupTerrain`, so Sodium's fog occlusion never culls sections, entities or block
entities out of the captured depth (Blindness, Darkness, Wathe train fog). The `BlindGate*` vetoes:
`WorldRenderer#renderEntity` (priority 2000) skips every other player who is unperceived or a
spectator, and a fishing bobber whose owner is so hidden (its line is drawn to the owner's hand);
`LivingEntityRenderer` (priority 2000) skips features and name labels on every other player and
forces a perceived one visible unless it is a spectator or an active Wraith, and for that forced
player calls `getRenderLayer` (opaque layer) and the model's `render` (full alpha) directly, so
inner wraps there (today SparkTraits' Chameleon fade) do not apply to it;
`ArmorFeatureRenderer#renderArmor` is HEAD-cancelled for armor drawn outside the feature loop;
`PlayerEntityRenderer#getArmPose` (priority 2000, `EMPTY` for other players) and
`BipedEntityModel#positionRightArm` / `#positionLeftArm` (priority 2000, vanilla `EMPTY` branch
only, removing Wathe's gun hold) keep weapon stances out of the line art;
`MinecraftClient#hasOutline` (priority 2000) answers false, so no instinct, glow or role outline
reaches the Blind; `GameRenderer#renderHand` and `#shouldRenderBlockOutline` (priority 2000) hide
the hand and the block outline; `DebugHud#shouldShowDebugHud` (priority 2000) answers false (F3
blocked); `InGameHud#renderCrosshair` (priority 2100, above Wathe's 1000 and the Seeker's 1100)
draws the same plain reticle on the vanilla path; and the `@WrapMethod`s on Wathe's
`RoleNameRenderer.renderHud` (priority 2100, so it encloses every other injection there, the Wraith
name-tag and cohort-label seams included) and `CrosshairRenderer.renderCrosshair` (priority 2100,
Wathe's plain 3x3 reticle only, so no target pip, name, corpse info or note text) are pinned in
`watheClientMixinContracts`. Non-player entities (corpses, dropped items, Seeker devices) are
environment and are never gated, except a hidden player's fishing bobber. Bumps are client-only (no
packet, no public sound): a hard wall bump, or a head bump with the space just above the head
blocked, adds a local SELF pulse and briefly perceives a bumped player with no block in the gap
between the two boxes; the 8-tick throttle is per contact (blocks, or one player), so a bump on a
different player is never dropped. The owner-only kit HUD hides during Wathe's round-start fade
(`GameWorldComponent.getFade() > 0`) and while swallowed, and lifts above the hotbar and stamina
row when its widest line would reach the hotbar column (`SeekerHudRules` precedent).
Attune ducking (`BlindAttuneSoundSystemMixin`,
`@ModifyExpressionValue` on the inner `getAdjustedVolume(F, SoundCategory)` call in
`SoundSystem#play` and `#getAdjustedVolume(SoundInstance)`) multiplies AMBIENT, MUSIC, RECORDS and
WEATHER sounds and every `wathe:ambient.*` id by 0.25 while the local Blind's Attune is active; it
composes with SparkAssist's volume seams on the same two methods and never writes options. While a
Wathe round runs on a confirmed SparkWitch server, `BlindComTacHeadRenderMixin` (HEAD cancel on the
typed `HeadFeatureRenderer.render`, which draws only the head-slot item) skips a worn ComTac VIII on
every head for every viewer (D5/C15); other head items and all armor render normally, the ComTac
renders outside rounds, and the HEAD slot still syncs, so `BlindParticipants.wearsComTac` is
unchanged.

The Blind kit is granted at round start in the `sparkwitch:blind_finish_initialize` phase of
`GameEvents.ON_FINISH_INITIALIZE`, ordered after `Event.DEFAULT_PHASE` (so SparkTraits' Conscience
compensation has settled final roles and Wathe has started the match record), and on a mid-round
`RoleAssigned` to a playing, alive Blind while ACTIVE (C11): one White Cane in the hotbar, a 30 s
cane and 90 s Attune initial cooldown, and the bound match id. The cane takes the selected hotbar
slot when empty, else the first empty one; with a full hotbar it displaces the last hotbar item (the
one before it when the last slot is selected) into a hidden main slot or the offhand, and with no
room at all nothing moves and the placement retries every tick. A same-match re-assignment keeps its
cooldowns; the per-tick upkeep (only while ACTIVE) re-grants a state bound to another match and
keeps exactly one cane in a living Blind's hotbar, except in Wathe psycho mode (SparkTraits'
Depression psycho keeps the inventory bat-only), where the cane waits until psycho mode ends.
The cane window lasts 5 s and Attune's 10 s;
their 10 s and 45 s cooldowns count from the end of the window (C2). The cane's hotbar cooldown is
raise-only and written through SparkTraits' exact facade (Fast Hands never shortens it) with a
vanilla fallback. The cane tap is public (`world.playSound` with the Blind as `except`, plus a
private copy for the Blind), and the CANE pulse (15-block environment radius) lists every other
playing, alive, non-spectator, non-Wraith, unswallowed player within 5 blocks, invisible ones
included; every 10 ticks inside the window, late entrants are sent as a zero-radius CANE pulse
lasting the rest of the window, which the client perceives for that duration only and does not store
as a pulse. A refused tap (swallowed, in psycho mode, stunned, cooling down, not an active Blind)
costs nothing, and a swallowed Blind gets no re-scan pulse. Attune (the
shared NoellesRoles ability key sends `sparkwitch:use_blind_attune` while the view is active) is
refused silently while a Taotie has swallowed the Blind (C7), while stunned, skill-blocked or
cooling down, and with the Grand Witch Fear skill-blocked message while Feared; a refusal costs
nothing. The White Cane and the ComTac VIII are bound: never an item entity (drop, death drop),
never outside the holder's own inventory slots (head slot included; QUICK_MOVE only for the ComTac
quick-equip from the hotbar into an empty head slot; never the offhand (Wathe's server already
refuses the swap-hands action in a round), a container, the crafting grid, an item frame, an armor
stand or a decorated pot, where `BlindKitDecoratedPotMixin` answers `SKIP_DEFAULT_BLOCK_INTERACTION`
on both sides so the pot never takes it and the cane tap or ComTac equip still runs), hidden in hand
from other players through `NoellesHiddenEquipment` (D9),
and stripped from inventory, head slot, cursor and open screens of anyone who is not a living,
playing, exact Blind (role change, terminal death, reset, finalize, disconnect and a staggered
20-tick sweep that spares a real Blind inside a SparkTraits-intercepted Last Stand death). The
ComTac equips silently (`minecraft:intentionally_empty` equip sound) and neither item's `use` ever
returns SUCCESS, so no arm swing is broadcast. The Blind's shop is role-gated (rebuilt for the exact
role, `sparktraits:*` entries preserved) and sells one ComTac VIII for 100 coins, stock 1, shown with
the `shop.sparkwitch.comtac_viii` name and its "one per round" description (Fiend shop precedent),
refused with `shop.error.sparkwitch.comtac_owned` when the buyer already owns one or already bought
one in this match (also after a strip or for a role gained mid-round); it lands on the head when
the head slot is empty, else in the first empty hotbar slot, and with no room the purchase fails
without charging. Money follows the Angler: visible to a living Blind, 0 on assignment, +50 per
task, no Impostor check. Grand Witch recruitment is unchanged: the cane and the ComTac (custom buy
handler, no physical shop output) are refunded at `UNKNOWN_ITEM_PRICE` (25 each) and nothing of the
kit survives on an Accomplice.

Grand Witch rework state uses separate `sparkwitch:grand_witch_runtime`,
`sparkwitch:witch_factor_world`, and `sparkwitch:grand_witch_recruitment_round`
components; the existing shared packet and NBT layouts remain unchanged. Sword
kill readiness (30s) is independent of the item dash cooldown (5s). Recruitment
uses a cumulative world quota, never a living-teammate count. It keeps only keys, both NoellesRoles master
keys and letters (the revolver is refunded like any other item), re-initializes the new role's Wathe shop
stock and cooldowns, and refuses, on the real role and before any destructive step, every SparkFactionAPI
`PoliceRoles` member (Emma included, the Insider exempt) and the NoellesRoles Corrupt Cop with a random
flavor line (`GrandWitchRecruitmentRules.refusal`); Emma still records the failed recruitment.
A placed Hunter trap is reclaimed only by its owner while still the real Hunter, so a recruited
ex-Hunter gets no trap back (the trap itself stays armed until it expires or the round ends).
Sword piercing
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
`WitchCohortRoleNameMixin`, `InsiderCohortRoleNameMixin` and `BlackRavenRoleNameRenderer` apply the same filter
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
The Time Stealer may query only `isRoleSkillBlocked`, `blocksWeaponAction` (with its
`isKillerInteractionBlocked` and `getForcedMeleeCooldownTicks` reads), `isLastEscapeActive`,
`registerTerminalDeathReason`, and `setExactItemCooldownRemaining` through
`SparkTraitsKillerBridge`, and `isLastStandDeathIntercepted` through the existing
`WitchFactorTraitsBridge`; an absent or older build means no skill or weapon block, no Last Escape
refusal, no terminal registration (the Clock kill is then only forced), and a vanilla Clock
cooldown, while a present build whose facade lacks or fails `isLastStandDeathIntercepted` counts
every death as intercepted: the `KillPlayer.AFTER` cleanup is skipped and no Clock death moves the
round time or grants a stamp; the curse tick clears a victim who stops playing, and the 20-tick
non-holder sweep removes a dead Time Stealer's Clock and stamps.
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
The Angler may query only `isInstinctHidden` through its own client
`FisherInstinctVisibilityBridge` and `isLastStandDeathIntercepted` through the existing
`WitchFactorTraitsBridge`, beyond the existing `SparkTraitsKillerBridge` seams
(`blocksWeaponAction`, `isLastEscapeActive`, `shouldCancelMeleeAttack`, `isNonFinalKillPending`)
and `SparkTraitsShopEntryPreserver`. An absent SparkTraits means nothing hidden, no weapon block, no
Last Escape, no parry, no pending fake death, and cleanup on every death; a present build whose
facade lacks or fails a method falls back per method as those bridges already define (an
intercepted death skips the Angler's `KillPlayer.AFTER` cleanup, which then runs at reset or finalize).
The Insider may query only `isInstinctHidden` and `hasActiveTrait` (Impostor, Conscience, local player only)
through its own client `client/insider/InsiderSparkTraitsBridge`; an absent, older or failing build means
nothing hidden and no trait: Impostor viewers then see the Insider in SparkTraits' green, and Conscience killers are
not filtered from the killer cohort line (`show(105)` beats SparkTraits' Conscience `hide()` at 100), so they see
"杀手同伙" on the Insider but never on a real Impostor. SparkTraits
`feat/insider-support` hard-codes `sparkwitch:insider` in `GoingDarkRules.PROTECTED_VIEWER_ROLE_IDS` (Going Dark
Veterans hidden from the Insider) and `isBlockingTeamWinNeutral` (a living Insider defers SparkTraits'
KILLERS/PASSENGERS verdicts like the Corrupt Cop); an older SparkTraits leaves the Veteran visible and may end
the round for killers or passengers while a lone Insider lives.
The Blind may query only `isRoleSkillBlocked` (Attune), `setExactItemCooldownRemaining` (the cane)
and `isLastStandDeathIntercepted` (the kit strip) through `SparkTraitsKillerBridge`, beyond
`SparkTraitsShopEntryPreserver` (its shop rebuild keeps the `sparktraits:*` entries). An absent,
older or failing SparkTraits falls back per method to no skill block, a vanilla cane cooldown, and a
death that is not intercepted (fail closed): the `KillPlayer.AFTER` strip runs and the 20-tick
sweep spares no dead Blind.

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

Client mixin changes also need `verifyClientMixinSelectors`, a standalone task that no other task
(`build`, `check`, `verifyArchitecture`) runs: for every entry in `watheClientMixinContracts`
(`build.gradle`) it checks the selector and `@At` target the remapped jar actually ships against the
pinned Wathe and NoellesRoles jars, and fails on a missing handler or provider method, selector
drift, or an injection weakened to `require = 0`.

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
