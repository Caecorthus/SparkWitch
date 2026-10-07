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
It also adds the Magician (`sparkwitch:magician`, 魔术师, merged from main), a killer whose recorded puppet replays
its actions disguised as a chosen player.
It also adds the Blind (`sparkwitch:blind`), a civilian whose screen stays black and who perceives
the world through sounds, helped by a White Cane, the Attune skill and a ComTac VIII headset.
It also adds the Abyss Listener (`sparkwitch:abyss_listener`, 聆渊者), a witch-faction special accomplice that only a
Bewitched promotion (through the special-accomplice pool) or an admin force assigns; it is never drawn naturally.
It also adds the Potion Gunner (`sparkwitch:potion_gunner`), a witch-faction special accomplice that only a Bewitched
promotion (or an admin force) assigns; it fires four kinds of potion shell from a bound anti-tank launcher.
It also adds the Riftwalker (`sparkwitch:riftwalker`, 隙行者), a witch-faction special accomplice that only a Bewitched
promotion or an admin force assigns; its Rift Gates let the witches hide and travel between gates.
It also adds the Bewitched (`sparkwitch:bewitched`, 魔化使), a witch-faction member dealt at round start in a Grand
Witch round (it replaces Grand Witch recruitment); after 2 completed tasks it is promoted to an accomplice rolled from
the special-accomplice pool.
SparkFactionAPI owns shared faction contracts;
SparkTraits and NoellesRoles integrations stay behind compatibility Adapters.
SparkStrength and SparkAssist do not own SparkWitch gameplay.
Seeker availability requires the SparkStrength tablet item (`sparkstrength:tablet`), resolved by
registry id only; SparkStrength owns no Seeker gameplay. SparkWitch never sells or grants the tablet: SparkStrength
issues it free at round start to every tablet-eligible player (SparkWitch's `PoliceRoles` members and the witch
faction included) and, through a mid-round reconciliation pass, to players who become eligible later.

Current build baseline:

- Minecraft `1.21.1`
- Java `21`
- SparkWitch `0.1.6.0` (Emma branch)
- SparkFactionAPI floor `0.1.5.12` (SparkFactionAPI `0.1.5.13`+ adds the two-row limited inventory; see
  `compat/SparkFactionSecondRowCompat`)

## Read Order

1. `CONTEXT.md`
2. The live owning Module and its direct callers

## Current Ownership

- `api/`: the only public downstream SparkWitch Interface.
  - `api/client/SecondarySkillKeyApi` (client source set, 2026-10-07) is the stable cross-mod seam onto Role Skill 2
    (key N): `static boolean register(Identifier roleId, Runnable onPressed)` (false, no throw, when the role already
    has a handler; null arguments throw NPE) and `static Text boundKeyText()`. It adapts the Runnable into
    `client/ability/SecondaryAbilityRegistry.tryRegister`, so a press fires on the client thread only while the local
    player's raw Wathe role id matches on a confirmed SparkWitch server; the caller's server validates. SparkStrength
    reaches it by reflection (FQCN and both signatures are frozen) for the NoellesRoles Vulture's Super Curse
    (秃鹫超级骂), whose C2S id `sparkstrength:vulture_super_curse` sits next to `sparkstrength:demon_hunter_sniff` on
    the Control Expert stun, Seeker session, Riftwalker session and Grand Witch Fear deny-lists (Fear blocks every
    SparkStrength role skill since the owner's 2026-10-07 decision, the Sniff included).
- `roles/civilian/apprentice/`: Apprentice instinct and ability runtime, plus the 2026-10-06 buff (owner decisions
  D1–D10, numbers in each ability class):
  - `ApprenticePlayerComponent` (`sparkwitch:apprentice_player`, `NEVER_COPY`, appended last in the CCA list) keeps the
    buff's state out of the frozen `sparkwitch:player` packet. Only the Clairvoyance exposure timer syncs to everyone;
    tasks toward graduation, the Purify cooldown, the two Swift Step recharge timers, the Mighty Force forfeit flag and
    the Healing fear ward sync to the owner only. Apprentice-only fields clear themselves once the role changes;
    `ApprenticeFeatureService` clears everything on role assignment, `ResetPlayer` and round finalize.
  - Graduation (D2): the second completed task as Apprentice (`TaskComplete`) raises her natural mana to cap 150 and
    1 per 2 s through the graduated overloads of `WitchManaRules` (the role-only signatures are unchanged), widens
    Murder Sense to 30 blocks, and unlocks Purify.
  - Purify (D3, `abilities/Purify/PurifyAbility`): the secondary key (`client/apprentice/ApprenticeClientModule`)
    sends the empty `sparkwitch:use_apprentice_purify` (`net/UseApprenticePurifyC2SPayload`, registered by
    `ApprenticeFeatureService`, in the Control Expert stun, Seeker session and Rift session deny-lists, not in Fear's).
    It is not in `WitchSkillRegistry`, so the random draw never deals it. 30 mana, 20 s own cooldown
    (`compat/cooldown/ApprenticePurifyCooldownStore`, appended last), aim within 6 blocks
    (`GrandWitchTargeting.findTarget(caster, requested, range)`), else herself if she carries a factor.
    `WitchFactorService.purify` removes the factor without refunding quota; success pays her 30 mana.
  - Spell-proof (D4/D8): `GrandWitchFearService.isPlayerFeared` = `isPlayerUnderFear` minus
    `ApprenticeFearExemption` (the Apprentice, or anyone with a Healing fear ward); the shop keeps
    `isPlayerUnderFear`, and Fear's sanity drain is unchanged. Client outline suppression drops Fear for exempt
    viewers and Obscure for the Apprentice; `WitchInstinctPolicy` answers her own skill outlines before Obscure.
  - Magic Resonance (D1, `ApprenticeResonance`): skill-use success in `WitchSkillUseService`, Grand Witch shop spells,
    Rift Gate placement, entry and exit report casts; a living witch-faction member or Murderous Witch within 24
    blocks of a living, non-spectator Apprentice gives her a private purple ripple and resonance sound there.
  - Mighty Force misfire (D5): pre-hit faction snapshots like the Swordfish stab; Wathe `KILL_SHOOTER` kills her with
    `wathe:shot_innocent` (no killer), `PREVENT_GUN_PICKUP` forfeits Mighty Force for the round.
  - Swift Step (D6) has two charges with independent 20 s recharges and registers no cooldown; the shared cooldown is
    set only when no charge is left. Clairvoyance (D9) exposes her to every living viewer for 10 s.
- `roles/civilian/piggod/`: Pig God chase, psycho, sound, economy, and rules.
- `roles/civilian/prophet/`: passive Death Sense (world-wide corpse pulse every 60 s; skips Scavenger-hidden bodies and,
  per owner decision, SparkTraits Depression fake-death bodies via `compat/SparkTraitsBodyDragBridge` and Magician
  decoys via `MagicianDecoyBodies`; a pulse that skipped a decoy says so on its action-bar line, 2026-10-07 D8), the
  owner-only `sparkwitch:prophet_player` component (permanent highlight set, necrology,
  Prophecy records), the Prophecy skill registration, and economy. The client outline
  lives in `client/hooks/ProphetCorpseHighlightClientHooks`.
  - The bound Necrology (`sparkwitch:prophet_necrology`): `ProphetNecrologyItem`, the binding rules
    `ProphetNecrologyRules`, and the lifecycle `ProphetNecrologyLoadoutService` (grant on assignment,
    one-copy restore from `ProphetRuntime.tick`, deletion on death, role loss, reset, round end, and stale
    match). Its mixins live in `mixin/prophet/` (death drop, item drop, slot click, decorated pot), parallel to
    the Black Raven ledger's; a Fabric `UseEntityCallback` refuses handing it to item frames, armor stands and
    allays, and `DecoratedPotBlockProphetNecrologyMixin` makes a decorated pot answer
    `SKIP_DEFAULT_BLOCK_INTERACTION` so the pot never takes it and the book still opens; hand hiding is the
    NoellesHiddenEquipment registration. The empty
    `net/OpenProphetNecrologyS2CPacket` opens the read-only two-tab book
    `client/prophet/ProphetNecrologyBookScreen`, which reads only `sparkwitch:prophet_player`.
  - Prophecy flow: the ability key's Prophet branch (`client/prophet/ProphetClientModule`, before the
    generic fallback) sends `net/RequestProphecyC2SPacket`; `ProphetProphecyService` checks role, life,
    Fear and the shared skill cooldown, opens a nonce/match-bound `ProphetProphecySessions` entry and
    sends `net/OpenProphecyS2CPacket` (dead names only); `client/prophet/ProphetProphecyScreen` answers
    with `net/ConfirmProphecyC2SPacket`, which the server re-validates (`ProphetProphecyRules`), records on
    the owner-only component, then charges 50 coins and cools down 30 s. The answer is one of the 11 fixed
    `ProphetDeathCauseGroup` groups; its table maps death-reason ids by string and sends unknown ids to OTHER. Every
    `sparkwitch:` death reason needs an owner-picked entry: the local `ProphetDeathCauseGroupTest` collects them from
    `SparkWitchDeathReasons`, the `death_reason.sparkwitch.*` lang keys and every `*DEATH_REASON*` constant, and fails
    on a missing one. The responsible player is the Judge attribution's actor, else the killer; a self-kill counts as
    no killer. Each ledger death carries a serial;
    a Prophecy record made against an earlier death of the same victim (revived, then killed again) is
    treated as fresh. Never in the Witch skill panel;
    both C2S ids are in the Control Expert stun and Seeker remote-view deny-lists.
- `roles/civilian/saint/`: Saint protection, Hellfire, player-local state,
  UUID-bound Karma, and the Saint shop (`SaintShopService`).
  - `flash/`: the Holy Flash (`sparkwitch:holy_flash`) item, thrown entity,
    owner-only component, victim targeting, inventory rules, and lifecycle;
    its no-drop mixin lives in `mixin/saint/`.
- `roles/civilian/perfumer/`: private scent marks, cologne healing, corpse mood,
  outlines, shop, and economy.
- `roles/civilian/tarotreader/`: divination shop, one-shot selection sessions,
  faction-count snapshots, purchaser-only reading results, and economy. Client
  presentation (faction-count HUD, reading slip, reading log, selector ledger)
  lives in `client/tarot/`, `client/hud/Tarot*`, and `client/screen/`.
- `roles/killer/ninja/`: parry, dark-kill bounty, shop, and death cleanup.
  - Grappling Hook (`sparkwitch:ninja_grappling_hook`, 钩爪, owner 2026-10-07): a 100-coin, stock-1 shop TOOL,
    role-agnostic like the Kunai and removed on death with the other Ninja items. `NinjaGrappleService` (server only)
    keeps one active hook per player, throws it (Wathe playing+alive and alive+survival; refused for Fabric fake
    players such as Magician replay stand-ins, and during SparkTraits Last Escape through `isKillerInteractionBlocked`,
    never the weapon gate) and discards it silently on death, ResetPlayer, role assignment and round finalize.
    `entity/NinjaGrapplingHookEntity` is a plain `Entity` (never a `ProjectileEntity`, so projectile hooks never see it;
    not saved, not summonable): the server flies it 24 blocks on a block-only COLLIDER ray (it passes through every
    entity), latches on the first block (vanilla barriers and Wathe barrier panels / light barriers are a miss),
    auto-retracts after 5 s or when the latched block stops holding the hook point (a door opens, a block goes), and
    breaks the chain when the owner is over 32 blocks away, stops holding it in either hand, is no longer playing, alive
    and survival, or is held (`NinjaGrappleService.isHeld`: the Rift session's private list, deliberately copied —
    Taotie swallow, Last Stand, Last Escape, Kidnapper control, Control Expert stun, Seeker session, foreign camera —
    plus a Hunter root; a held player can neither throw nor pull). A second right-click on a latched hook starts the
    pull: the server syncs the feet target (`NinjaRules.grappleFeetTarget`, floor/wall/ceiling) as tracked data and the
    owner's client (`client/ninja/NinjaGrappleClientPull`, START_CLIENT_TICK) sets its own velocity toward it, so walls
    and doors stop it through client physics and latency cannot overshoot; the server keeps fall distance at zero and
    ends the pull on arrival, stall or a 2 s timeout by zeroing the velocity. The item has no cooldown while a hook is
    out (holding right-click therefore throws and then reels in on its own); every cycle end writes the 10 s cooldown
    unless a longer forced cooldown is already running (`ForcedCooldowns.itemRemainingTicks`), and Ninja assignment
    locks it for 90 s. Vanilla chain sounds only, never a `.step` id. The state (FLYING, LATCHED, PULLING) is tracked
    data and the owner id rides the spawn packet for the client `client/renderer/NinjaGrapplingHookEntityRenderer`: a
    camera-facing head sprite (`textures/entity/ninja_grappling_hook.png`, hidden for its first ticks next to the
    camera) and a leash-style steel chain to the hand holding the hook (vanilla bobber hand anchor; geometry in
    `NinjaGrapplingChainGeometry`), taut unless FLYING, and nothing at all for an owner invisible to the viewer.
    `BlindClientGates.hidesEntity` hides a hook whose owner is hidden, like the fishing bobber.
- `roles/killer/kidnapper/`: corpse targeting, dragging, positioning, and cleanup. `KidnapperFalseBodyPolicy` refuses
  SparkTraits fake bodies, camera-bound bodies and Magician decoys; a deliberate drag on a decoy tells the Kidnapper.
- `roles/killer/blackraven/`: Feather Blade marks, owner-private Perception state,
  bound ledger, restricted shop, and lifecycle cleanup. The bound ledger and Raven Mask
  (`BlackRavenInventoryRules`) never drop, never leave their owner's inventory slots, and are
  refused by a `UseEntityCallback` veto for item frames, armor stands and allays; a decorated pot
  answers `SKIP_DEFAULT_BLOCK_INTERACTION` for them through
  `mixin/blackraven/DecoratedPotBlockBlackRavenItemMixin`, so it never takes them (not even through a
  Wathe ornament hung on it) and the ledger or mask still opens there.
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
  `client/bellringer/`. The bell cannot be handed to a world target: a `UseEntityCallback` veto in
  `BellRingerFeatureService` refuses item frames, armor stands and allays, and
  `mixin/bellringer/DecoratedPotBlockTollBellMixin` makes a decorated pot answer
  `SKIP_DEFAULT_BLOCK_INTERACTION`, so the per-tick restore never mints a second bell.
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
  constant names). The Clock and stamps cannot be handed to a world target: a `UseEntityCallback` veto in
  `TimeStealerFeatureService` refuses item frames, armor stands and allays, and
  `mixin/timestealer/DecoratedPotBlockTimeStealerItemMixin` makes a decorated pot answer
  `SKIP_DEFAULT_BLOCK_INTERACTION`, so the per-tick restore never mints a second Clock and the Clock's own
  use still runs at a pot.
- `roles/killer/magician/`: Magician (`sparkwitch:magician`, 魔术师), an ordinary killer merged from main (#99,
  collaborator Huankings, who may keep editing these files upstream: keep v2-side edits minimal). It records 30 s of
  its own actions, then a puppet disguised as a chosen player replays them; the 2026-10-07 audit fix applies owner
  decisions D1–D8 (`MAGICIAN-AUDIT.md` in the merge-all-2026-10-07 archive).
  - State: `MagicianAbility` (the shared ability key's four-stage machine; server debounce on server ticks; the
    Control Expert stun, the NoellesRoles silence, the SparkTraits role-skill block and Fear all refuse it) and
    `MagicianPlayerComponent` (`sparkwitch:magician`, synced to its owner only: stage, cooldown, chosen disguise and
    the round roster). `MagicianRoster` (D7) captures every round participant when the round starts or a player
    becomes the Magician; the disguise list and the target packet accept any roster member, alive or dead, so a pick
    never reveals a death. `client/magician/MagicianKeyRepeatGuard` drops key-repeat presses (one stage step per
    physical press).
  - Recording only records accepted actions: gun shots and knife stabs at Wathe's `recordItemUse` anchor
    (`mixin/MagicianGunShootPayloadReceiverMixin`, `mixin/MagicianKnifeStabPayloadReceiverMixin`), full-charge bat
    kills at Wathe's `killPlayer(..., BAT)` (`mixin/MagicianRecordBatKillMixin`); the other `MagicianRecord*Mixin`s
    capture movement-side interactions. A rejected, parried or out-of-range action never replays.
  - Replay: `MagicianPlaybackManager` alone owns live puppets (spawn, per-tick frames, the replay proxy, round-end
    `clearAll`, disconnect cleanup, discarding a finished puppet even when its owner is offline).
    `MagicianPlaybackActionExecutor` replays through a `FakePlayer` proxy that carries the owner's UUID; weapon
    targets are picked like Wathe's own (`ProjectileUtil.getCollision`, cut by blocks) and never the owner; a
    recorded left-click replays as a punch, a knife stab needs the knife in hand, a bat kill needs `BAT_HIT`.
    `MagicianPlaybackEntity` (`sparkwitch:magician_playback`, `disableSaving`, `disableSummon`) tracks only the
    copied player's UUID and name; its owner UUID is a server-only field, never synced.
  - Ending a puppet: `MagicianPuppetHits` has one entry per damage source on the Seeker-device pattern
    (`SeekerDeviceHits`): revolver/derringer at the `recordItemUse` anchor, the knife at HEAD priority 2300 after the
    SparkTraits guards, blasts as a sphere with line of sight; every entry validates weapon, reach, aim and line of
    sight, refuses the Magician's own puppet and spectators, and costs the attacker what a real hit costs without the
    innocent-shot punishment or mood loss (D3). All entries end in `MagicianPlaybackManager.endPuppet`, which leaves
    a decoy body stamped with the Civilian cover role through the Wraith death-role seam (D1), registers it in the
    server-only `MagicianDecoyBodies` (D2) and pays the Magician 50 coins when anyone other than the Magician ended
    it, blasts included (D4).
  - Other weapons (D5) end a puppet through their own `MagicianPuppetHits` entry, placed next to each weapon's Seeker
    hook (a nearer puppet wins over the player and over a breakable device): Hunter shotgun, NoellesRoles Demon
    Hunter pistol (only when the copied player would die to it; `mixin/MagicianDemonHunterRefundMixin` pays the
    Jester refund), Death Ray (pierces), Wathe bat (`MagicianPuppetAttackHandlers`, full charge), Swordfish,
    Ceremonial Sword strike and dash, ninja shuriken, NoellesRoles throwing axe, Potion Gunner shell and backblast,
    and the SparkStrength M67 blast. Non-lethal tools still pass through puppets.
  - Presentation (D6): `client/magician/MagicianPuppetStandIn` answers every skin, name-label and instinct-glow
    question for a puppet with a stand-in player (the copied player when loaded, else a detached client-only copy
    that is never spawned), so each viewer's existing rules apply unchanged (`MagicianPuppetAppearance`,
    `MagicianPuppetNameTags`, `client/mixin/MagicianPlaybackRoleNameMixin`, `MagicianPlaybackInstinctMixin`). The
    Blind never sees puppets. Client aim (`MagicianPuppetAim`) wraps Wathe's revolver, derringer, knife and crosshair
    target methods and returns a puppet only when it is strictly nearer than the original result.
  - Decoys (D2): the Prophet's Death Sense, the Kidnapper's drag and the NoellesRoles Vulture's eat
    (`mixin/vulture/NoellesVultureDecoyBodyMixin` through `compat/NoellesVultureDecoyGuard`) ignore decoy bodies and
    tell that player; Wraith conversion never takes a decoy for the real body. Other body readers (Perfumer, Coroner,
    SparkStrength) still treat a decoy as a body.
  - The ability cooldown is the `sparkwitch:magician` store in `compat/cooldown/MagicianCooldownStore` (appended
    last; nominal = the 15 s playback cooldown; raise-only). Client presentation lives in `client/magician/` and
    `client/mixin/Magician*`; HUD, button and replay texts are lang keys (`hud.sparkwitch.magician.*`,
    `ui.sparkwitch.magician.*`, `replay.global.sparkwitch.magician_*`).
- `client/ability/`: generic configurable skill-key-2 registration and role-id
  dispatch only; concrete roles own their handlers. `register` throws on a duplicate role; `tryRegister` (used only
  by `api/client/SecondarySkillKeyApi`) returns false and keeps the first handler.
- `roles/neutral/fiend/`: Fiend rules (`FiendRules`), side-safe predicates (`FiendParticipation`), the
  `sparkwitch:fiend_moment` world component and its pure state, dormant immunity and hit reactions, cooldown
  aura, bomb-pass ledger, swallow block, last-one-standing exclusion (`FiendWinExclusion`), the Fiend Moment
  shop, economy, win listener, lifecycle and owned effects, and the moment-only Dash (`FiendDashRules`,
  `FiendDashService`, `FiendNetworking`, `net/UseFiendDashC2SPayload`). Its mixins live in `mixin/fiend/` and
  `client/mixin/fiend/`; client presentation (countdown HUD, Dash HUD, outline decision) in `client/fiend/`.
- `roles/neutral/murderouswitch/`: Murderous Witch feature, Death Ray, shop,
  and win rules.
- `roles/neutral/insider/`: Insider (`sparkwitch:insider`) rules, Team Jiahao membership predicates,
  pairing with a drawn Corrupt Cop, task-money economy, shop, neutral master key doors, gun-punishment
  exemption, and Team Jiahao win seams. Its mixins live in `mixin/insider/`; client presentation
  (instinct outlines, the "嘉豪同伙" label, the Team Jiahao end title) lives in
  `client/insider/` and `client/mixin/insider/`, registered once by `InsiderClient.init()`.
- `roles/witch/`: rules shared by Grand Witch and Accomplice.
- `roles/witch/accomplice/variant/`: the special-accomplice pool (`AccompliceVariants`, the promotion roll
  `AccompliceVariantRoll`, the `sparkwitch:accomplice_variant_round` ledger, and the per-variant hooks: the
  post-promotion callback and the variant's own skills for the `gui.sparkwitch.skills` panel).
- `roles/witch/potiongunner/`: Potion Gunner (`sparkwitch:potion_gunner`) rules and constants (`PotionGunnerRules`,
  `PotionShellType`, `PotionBallistics`), pool registration, shop, loadout, bound-item rules and lifecycle.
  Subpackages: `launcher/` (the launcher item, inventory loading, the server-authoritative fire service, the backblast)
  and `shell/` (the shell items and entity, flat flight, blast targeting and falloff, rewards) with `shell/effect/`
  (GW-DK, GW-AC, GW-MR, TR, harmless burning). Its mixins live in `mixin/potiongunner/` and
  `client/mixin/potiongunner/`; client presentation (scope zoom and reticle, fire input, HUD, two-handed pose) in
  `client/potiongunner/`. Its packet is `net/FirePotionLauncherC2SPacket`.
- `roles/witch/grandwitch/`: Grand-Witch-private permanent sword reward, spells and fear;
  `GrandWitchFeatureService` also owns the round lifecycle of the special-accomplice ledger (seed at
  `ON_FINISH_INITIALIZE`, clear at `ON_FINISH_FINALIZE`). Its `factor/` ledger is shared: cumulative world-wide
  quota, delayed private network views, source-independent income, and persistent provenance.
  Its client presentation lives in `client/grandwitch/`. The held sword's cooldowns (owner pick A2 + B2, 2026-10-05)
  are drawn by `GrandWitchSwordHud`, with pure layout in `GrandWitchSwordHudRules`. `GrandWitchSwordCrosshairMixin`
  draws a kill glyph left of Wathe's crosshair, dash chevrons right of it, and the attack bar under it.
  `GrandWitchSwordCooldownMixin` draws a slot badge (kill seconds plus five dash pips) above whichever hotbar or
  off-hand slot holds the sword. While the sword is held, the badge replaces Wathe's own cooldown number. The
  bottom-right role lines no longer show the kill or dash timers.
- `roles/witch/abysslistener/`: Abyss Listener (`sparkwitch:abyss_listener`, 聆渊者) frozen tuning constants and pure
  predicates (`AbyssListenerRules`), the role definition (`AbyssListenerRole`), its special-accomplice pool entry and
  feature wiring (`AbyssListenerFeatureService`), the role-owned shop (`AbyssListenerShopService`), replay formatters
  (`AbyssListenerReplayFormatters`), and `AbyssSuppression`, the only place the skill, the gun, and the zone decide
  eligibility and write suppression state. Its optional SparkTraits reads live in
  `compat/SparkTraitsAbyssListenerBridge`.
  - `shriek/`: `WardensShriekService`, the Warden's Shriek handler on the shared Witch-skill path.
  - `gun/`: the Shriek Gun: `ShriekGunItem` (the `Item#use` entry), `ShriekGunService` (server fire mode, match
    beam, hit, presentation, record, and the off-match presentation shot), `ShriekGunRules` (pure fire mode, ally/enemy
    hit plan, sync-capped knockback vector, particle spacing, Vendetta rule), and `ShriekGunTargeting` (side-neutral
    beam geometry shared by the server hit and the client crosshair).
  - `loadout/`: the bound gun: `AbyssListenerInventoryRules` (pure binding matrix, holder entitlement, free-holder
    drop return, round-start gate), `AbyssListenerGunSweep` (participant-only sweep gate, pure reconcile decisions,
    staggered cadence, full-hotbar displacement slot), and `AbyssListenerLoadout` (grants, entitlement sweep,
    free-holder drop return, death/reset/finalize removal).
  - `zone/`: the Deep Dark Spore Flask (item, thrown entity, and its registration in `AbyssListenerEntities`); the Deep
    Dark Zone terrain: flood-fill shape and landing cell (`DeepDarkZoneShape`), eligibility and palette
    (`DeepDarkZoneEligibility`), timeline (`DeepDarkZoneSchedule`), in-memory registry (`DeepDarkZoneState`), section
    grouping (`DeepDarkZoneSections`), read-only world adapter (`DeepDarkZoneBlocks`), chunk-delta sender
    (`DeepDarkZoneSync`), sampled vanilla cues (`DeepDarkZoneCues`), and the runtime with its lifecycle
    (`DeepDarkZoneService`); standing effects, exposure, and drain operands (`DeepDarkZoneStandingRules`,
    `DeepDarkZoneStandingService`, `DeepDarkZoneStandingDrain`); and the owner-only `AbyssZoneExposureComponent`.
  - Its mixins live in `mixin/abysslistener/` (the five Shriek Gun guards and the zone mood drain) and
    `client/mixin/abysslistener/` (`ShriekGunCrosshairMixin`, `MoodRendererAbyssZoneExposureMixin`, and the read-only
    row accessor `MoodRendererTaskRowAbyssAccessor`).
  - Its client presentation lives in `client/abysslistener/`, registered once by `AbyssListenerClient.init()`: the
    crosshair hint `ShriekGunClientTargeting` and the pseudo task line `AbyssZoneExposureTaskLine` (with its
    `AbyssZoneExposureTaskLineRules`).
- `item/firepoker/FirePokerFallAttributionService` (historical name kept): the shared train-fall push ledger for every
  pushing weapon (Fire Poker and Shriek Gun), not only the Fire Poker.
- `roles/witch/riftwalker/`: Riftwalker (`sparkwitch:riftwalker`, 隙行者) role definition, rules, gate-user
  classification (`RiftGateUser`, `RiftGateUsers`), shared status probes (`RiftwalkerStatusProbes`), special-accomplice
  pool entry, feature wiring, and shop. Subpackages: `gate/` (Rift Gate item, entity, placement, lifecycle, the
  gate registry, and the operator-only Rift Gate Remover), `session/` (server-authoritative in-gate sessions, guards, and the occupant affect policy),
  `projectile/` (projectiles through gates), `sabbath/` (Witches' Sabbath), `tablet/` (the tablet gate console),
  `swapper/` (the NoellesRoles Swapper crush), and `net/` (payloads and `RiftwalkerNetworking`). Its mixins live in
  `mixin/riftwalker/` and `client/mixin/riftwalker/`; client presentation (gate renderer and outline, in-gate input
  lock, grey view and HUD, console screen) lives in `client/riftwalker/` (`gate/`, `session/`, `tablet/`, `swapper/`).
- `roles/witch/bewitched/`: Bewitched (`sparkwitch:bewitched`, 魔化使) role definition (`BewitchedRole`), constants
  and pure predicates (`BewitchedRules`: task threshold, money visibility, queue decision), the pure promotion role
  choice and lock rules (`BewitchedPromotionRules`), the owner-synced task counter (`BewitchedPlayerComponent`,
  `sparkwitch:bewitched`), the end-of-tick promotion queue (`BewitchedPromotionQueue`), the promotion transaction
  (`BewitchedPromotionService`), the empty shop and balance visibility (`BewitchedShopService`) and the wiring
  (`BewitchedFeatureService`). Its round-start dealing lives in `registry/WitchRoleAssignmentService` (formula
  `WitchRoleCounts.bewitched`); the forced lock lives in `WitchWorldComponent` (`component/ForcedAccompliceRoleLocks`)
  and `command/ForceAccompliceRoleCommand`. Client presentation (the `n/2` HUD line) lives in
  `client/bewitched/BewitchedClientPresentation`, registered once from `SparkWitchClient`.
- `roles/civilian/emma/`: unique cop claim, role-owned mana skill, delayed backlash,
  speed latch, and one reward per gun cycle.
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
  - **No shown room** (mouse mode). Shown slots are the hotbar, plus the second row (inventory
    27-35) with SparkFactionAPI 0.1.5.13+, whose `getEmptySlot` fills that row before hidden 9-26.
    A worn piece cannot be picked onto an empty cursor while every shown slot is taken (a vanilla
    close puts it in the hidden main inventory); the press sends nothing, and number-key swaps
    still exchange it. Touchscreen release pick-ups are not guarded. In either mode, while every
    shown slot is taken a close first clicks a cursor armor piece back into its own empty armor slot
    (one vanilla PICKUP); with a free shown slot vanilla's close offer already lands there.
  - **Layout.** A 50x50 2x2 block (head, chest / legs, feet) at `[X-54, X-4) x [Y-9, Y+41)` beside
    Wathe's 176x32 strip, or at `[X-54, X-4) x [Y-20, Y+30)` centred on the 176x54 two-row block
    `(X, Y-22)` with SparkFactionAPI 0.1.5.13+ (`LimitedInventoryArmorLayout.anchorY`), clear of the
    shop row, role head rows, logo and the right-hand info card at every scaled size the layout test
    covers (320 px wide and up). Its frame is cut at draw time from Wathe's own
    `limited_inventory.png`; no copied art.
  - **Neighbours.** The SparkWitch/SparkTraits info card and SparkAssist's guidebook route around
    the panel because it is a visible `ClickableWidget` child; being inactive, it never consumes a
    click.
- `PoliceSlotAssignmentService` (`roles/civilian/judge/`) with `mixin/PoliceSlotAssignmentMixin`
  and `mixin/PoliceRoleHistoryMixin`: police-slot ownership. Judge, Emma, the Control Expert, and
  the Seeker share the Vigilante slots uniformly through `VARIANT_IDS`; no variant owns a separate
  slot mixin.
- Police gun parity: Wathe's server gun receiver gives innocent non-Vigilante shooters a 15 s
  cooldown and -0.35 mood per hit. Each SparkWitch police role has its own additive OR-wrap of all
  four `isRole` calls that also answers "Vigilante" for that role, so it gets the 10 s revolver
  cooldown and no mood penalty. Innocent-shot punishment is unchanged. The wraps are
  `mixin/JudgePoliceGunMixin`, `mixin/EmmaPoliceGunMixin`,
  `mixin/controlexpert/ControlExpertPoliceGunMixin` and `mixin/seeker/SeekerPoliceGunMixin`. A new
  police role needs its own wrap; registering in `PoliceRoles` is not enough.
- `client/factor/`: low-priority fallback outlines after ordinary instincts and hiding.
- `client/emma/`: shared-key dispatch and role-owned target HUD; no witch inventory panel.
- `client/judge/`: primary-key selector and the role-owned bottom-right line (`JudgeHudRenderer`: "press key to
  judge, 100 coins" or the coin requirement below 100). `WitchSkillHudRenderer` dispatches it right after Emma,
  gated by `JudgeClientModule.ownsHud` (the selector gate minus Grand Witch Fear); no witch inventory panel.
- `mana/`: mana economy and natural-regeneration runtime.
- `component/`: CCA ids, stored fields, sync/NBT codecs, and narrow state
  operations used by the owning runtime Modules.
- `compat/`: optional or version-sensitive cross-mod Adapters.
- `compat/SparkFactionSecondRowCompat`: the two-row limited inventory gate. With SparkFactionAPI
  0.1.5.13+ (read once from the local mod version; SparkFactionAPI requires client and server to
  match exactly; unknown versions fail closed), Wathe's in-round inventory also shows main slots
  27-35 directly above the hotbar, and `getEmptySlot` fills 0-8, 27-35, then 9-26. With the bundled
  floor every caller keeps its old behavior (the Abyss Listener's vacated-slot reuse below is new on
  either version). Callers: bound-item displacement (Blind cane, Time Stealer Clock and Gift Watch, Abyss Listener
  gun: a full-hotbar re-grant moves the displaced item into the slot a removed stray vacated, but
  never into hidden storage while the shown row has room), Potion Gunner launcher placement and
  keeper move, shell returns and surfacing, the Seeker's swallowed-car return, the armor block, and
  the info card's reserved inventory block. Hotbar-only rules are unchanged: a bound item in 27-35
  is still a stray.
- `compat/cooldown/`: SparkWitch's registrations with the SparkFactionAPI forced-cooldown contract
  (`api.cooldown.ForcedCooldowns`): role-skill stores for SparkWitch and NoellesRoles counters, the SparkWitch item
  nominal-cooldown provider, and the Seeker car item exemption. `SparkWitchForcedCooldowns.register()` runs once from
  `SparkWitch.onInitialize`, right after `SparkWitchEvents.register()`. `SparkWitchItemCooldownReleases` releases the
  SparkWitch timers behind an item whose vanilla cooldown is removed (`mixin/ItemCooldownRemovalMixin`). For the
  SparkFactionAPI admin clear alone (`mixin/SparkFactionClearCooldownMixin`), it also exempts the item from Saint Karma.
- `impl/SparkWitchEvents`: watch-only registration/lifecycle aggregator.
- `util/hitscan/`: server-side lag compensation for hitscan weapons. `PlayerHitboxHistory` keeps a
  one-second, server-thread-only ring buffer of player hitboxes (never saved, synced, or sent);
  `HitscanLagRules` owns the ping-based rewind window and swept volumes. Used by the Hunter
  double-barrel shotgun, the Murderous Witch Death Ray, the Control Expert Taser, the Abyss Listener Shriek
  Gun, and the Black Raven Feather Blade (whose sight and feet-distance reach are taken at the rewound hit);
  client crosshair hints keep current boxes. The Potion Gunner shell's in-flight player check uses
  `projectileHitVolumes`, which rewinds only the ping-independent view delay: the gunner's client also simulates the
  shell, so the ping cancels.
- `util/OffMatchUse`: the owner rule (2026-10-04) for heavy weapons any holder may use (Anti-Tank Launcher and shells,
  Shriek Gun, SparkStrength M67). `mode` gives a living participant of an `ACTIVE` round a match shot, refuses a dead
  one, and gives anyone else a presentation-only shot; `isMatchParticipant` scopes the bound-item rules.

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
The Feather Blade aim (`BlackRavenTargeting.findAimedPlayer`) treats spectators as transparent:
a Rift Gate occupant (an alive spectator, Riftwalker D3) is never marked, never starts the blade
cooldown, and never shields the player behind its gate. An active Vendetta is an adventure-mode
Wraith and stays a target of its bound killer.

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
and no custom packet. It ignores factions (owner decision 2026-10-05, replacing Q10): any exact Time
Stealer may use it, Conscience included, and any living participant other than the user is a target,
fellow killers included, so they are no longer transparent; the Last Escape, Vendetta, structural
`canAffectPlayer` and already-stolen vetoes still apply. A Conscience Time Stealer's curse kill
of a civilian-side player still triggers SparkTraits' own Conscience punishment. The 45 s cooldown (also at round start) is an authoritative server tick
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
A Conscience Time Stealer also earns one stamp per completed task (owner decision 2026-10-05): the
role's own `TaskComplete` listener pays a living exact Time Stealer whose SparkFactionAPI effective
faction is known and not `wathe:killer` (`TimeStealerRules.earnsTaskStamp`), since Wathe fires
`TaskComplete` for a killer's fake tasks too; the grant keeps its usual holder and match gates.
The same Conscience Time Stealer also holds a second bound watch, the Gift Watch
(`sparkwitch:time_stealer_gift_watch`, owner decision 2026-10-05), the Clock in reverse. It is
granted lazily from the living holder's tick (`TimeGiftWatchLoadout`, because SparkTraits Conscience
settles after RoleAssigned) and kept in the hotbar next to the Clock without ever displacing it;
everyone else is swept like the Clock. It uses the Clock's ray and target vetoes (`canGift`), with its
own SparkFactionAPI action id `sparkwitch:time_gift` and "already gifted" in place of "already
stolen", and never the user; Seeker devices neither absorb nor block it. Its 45 s cooldown (also from the first grant of the round) is the
authoritative `GiftReadyAt` in `sparkwitch:time_stealer`, independent of `ClockReadyAt`, and written
only after a committed gift. The target-owned `sparkwitch:time_gift` (`NEVER_COPY`, never synced,
match-bound; giver, match, gift tick, stage) runs the curse's timeline in reverse: silent for 15 s,
Speed I-IV with a private chime at 15/20/25/30 s, and at 35 s Speed V for 5 s, full sanity
(`setMood(1)`) and a private actionbar line, after which the gift ends and its Speed runs out. No
effect is ever removed early, the Timekeeper never lifts a gift, a gift and a curse on the same
player run independently, and gifts keep running after the giver dies or leaves.
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
previous role (a stray holder is swept within 20 ticks anyway). A
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
payloads (`ControlExpertStunGuards`, `ControlExpertStunPayloadGuardMixin`). Add-on sessions those
guards cannot end read the stun through the public `SparkWitchApi.isControlExpertStunned(PlayerEntity)`
(2026-10-07, name and signature frozen): SparkStrength reflects it to refuse and end a Bomber drone pilot
session as STUNNED, since drone moves and exit stay off the deny-list. The SparkStrength Serial
Killer's psycho-pistol shot (`sparkstrength:serial_pistol_shoot`, 2026-10-07) is classified as a gun
shot: it sits on exactly the lists that hold `wathe:gunshoot` (stun, Seeker session, Rift occupant)
and, like that id, not on Grand Witch Fear's. The Disruptor gates
keyed instinct only through `client/mixin/controlexpert/ControlExpertInstinctGateMixin`
(`@WrapMethod` on `WatheClient`). The Control Expert never renders in the
`gui.sparkwitch.skills` panel.

Holy Flash state never enters that shared schema either. `sparkwitch:holy_flash` (`NEVER_COPY`,
owner-only sync, never saved) holds the flashed player's total and remaining ticks, the burst
position, and whether they faced it; it uses no vanilla Blindness, so Blindness cleanups never touch
it, and the client only draws the mask and plays the ringing. The Saint's only shop entry is the
re-buyable 75-coin Holy Flash (role-only gate, default buy handler, which needs a free hotbar slot
and never merges stacks). A deny-only `ShopPurchase.BEFORE` listener refuses a purchase when the buyer
already carries 3 or more (main, hotbar, offhand, crafting grid and cursor stack). Because the shop makes
`ShopUtils.canAccessShop` true for the Saint, `SaintEconomyService` answers `CanSeeMoney` DENY for a Saint that
is not playing and alive, keeping dead and STARTING-phase coin visibility (and SparkTraits money-trait
eligibility) unchanged. Throws, bursts and flashes exist only while Wathe's status is ACTIVE
(`HolyFlashRules.isActivePhase`), so the ringing never spills into STOPPING. Any participant may throw one. The role-owned `HolyFlashEntity` (never Wathe's
grenade, so no grenade hook fires) bursts on its first block or entity hit with sound and particles
but no damage. It flashes every participant whose hitbox is within 6 blocks of the burst (nearest point
of the body, `HolyFlashTargeting.bodyDistance`) and whose eyes have line of sight to it; facing away keeps
3/4 of the duration (owner buff 2026-10-07, was 4 blocks to the eyes and halved). The thrower is included and needs only participation; everyone else must also pass
SparkFactionAPI's `sparkwitch:holy_flash` veto, Last Escape, and Vendetta isolation. If the thrower
died or left mid-flight, the burst still blinds without an actor: no faction veto, Last Escape still
honoured, active Vendetta endpoints untouched. `mixin/saint/GameFunctionsHolyFlashDropMixin` (HEAD guard on
`shouldDropOnDeath`) keeps flashes out of death drops, and a confirmed (not Last Stand-intercepted)
death removes them. Reset and finalize clear every flash and discard flashes still in flight.
The server tick ends a flash as soon as its holder stops being a participant (death, spectating,
creative, active Wraith). A Rift Gate occupant is the exception: it is an alive spectator
(Riftwalker D3), so entering a gate never cleanses a flash, which keeps counting down inside.
No new flash reaches an occupant, with or without a thrower: the victim filter requires a
participant first, and the `sparkwitch:holy_flash` veto denies occupants as well. The
Holy Flash never renders in the `gui.sparkwitch.skills` panel and never triggers Saint Karma.
Client side lives in `client/saint/`: `HolyFlashOverlayRenderer` (via `client/mixin/saint/HolyFlashHudMixin`,
`InGameHud.render` TAIL, priority 1100) draws owner pick B — a bright spot at the projected burst, then
black held to 60% and eased out — above every HUD overlay, including the Seeker remote view;
`HolyFlashAudioClient` plays the `sparkwitch:skill.holy_flash_tinnitus` loop and drives
`client/mixin/saint/HolyFlashSoundSystemMixin`, which multiplies every other sound by a factor floored
at 0.1 (composes with SparkAssist); `voice/HolyFlashVoiceClientBridge` loads `HolyFlashVoiceReceiver`
on the physical client only to scale incoming Simple Voice Chat PCM by the same factor.
The Blind takes only the audio side (owner decision Q12): the server flashes a Blind like any other
participant (same radius, sight, facing and duration rules, no role check), and the mask alone is skipped
while `BlindView.isActive` holds on that client, so nothing is layered over the echo view or its
fail-closed black; the tinnitus, the muffle and the voice muffle are not gated on the Blind view.

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
The owner's client only simulates the car it drives (`SeekerRules.CAR_SPEED` 0.375 blocks/tick, owner
decision 2026-10-04), and every move is validated against the shared `SeekerCarPhysics` (speed budget,
replay, a server-side fall model that never trusts the client's velocity, play-area clamp). Neither the
car nor a camera has a distance limit from the body (owner decision 2026-10-04): the Wathe play area is
the only spatial bound of open, the per-tick exit (`OUT_OF_RANGE`, a stable name that now means outside
the play area) and the car clamp; deploy and camera-place reach limit placement only. Console
availability, quick connect and camera cycling therefore read the owner-synced car state and camera
list, never client entity presence (a far device is usually untracked until a session views it), and
the CCTV HUD shows the body distance without a range bar. The session lock (`LOCK_SCOPE = SESSION`) applies only while
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
Time Stealer Pocket Watch, the Murderous Witch Death Ray, the Wathe grenade (including the SparkTraits Bomb Maniac
grenade), the SparkStrength M67, the Abyss Listener Shriek Gun (`SHRIEK_GUN`, appended after the pre-existing
sources in `SeekerBreakSource` so earlier replay ids keep their values; a server ray through
`SeekerDeviceHits.onShriekGunFired`), and the Potion Gunner (`POTION_SHELL`: the shell's in-flight sweep, its impact
blast, and the launcher backblast lane). A client-picked gun hit (Wathe revolver and derringer, Demon Hunter pistol)
is accepted when the shooter's look ray meets the device box grown by its client targeting margin with
a clear line to a point of the device, else only through the 25° / 15-point-sample latency fallback
(`SeekerDamageRules.gunAimedAndVisible`); nothing breaks through walls. Rays and projectiles are
nearest-wins (a nearer device takes the hit, the player behind is not hit); blasts (Wathe grenade,
SparkStrength M67, Potion Gunner shell) break every device in a sphere with line of sight and still kill players as
before. An M67 breaks devices only when the round is ACTIVE and its thrower holds a match role
(`compat/SparkStrengthM67Compat`, the `util/OffMatchUse` rule); SparkStrength's presentation-only M67s
(non-participants, or thrown during STARTING or STOPPING) break nothing. Sources with no hit or damage geometry never
break a device: the firecracker (sound only), the Bomber timed bomb (kills only its holder), and the
poison gas cloud (status effect). A breaker other than the owner is marked for the owner only (10 s,
newest replaces oldest) when the owner holds the tablet; recalls, depletion, and Taotie swallows
never mark. The Taotie path goes only through `compat/NoellesTaotieSeekerBridge` (pinned
NoellesRoles `b58fa5f`) and `roles/civilian/seeker/taotie/`: the client predicts the Taotie's
swallow key on a car in its crosshair (`client/mixin/seeker/SeekerTaotieAbilityKeyMixin`) and sends
`seeker_car_swallow`, which the server re-validates; a swallow consumes the Taotie's swallow
cooldown, removes the car without a mark or the 180 s cooldown, and tells the owner without naming
the Taotie; the car returns (60 s cooldown) when that Taotie finally dies or loses the role, into its
original slot, else the first empty hotbar slot, else the shown second row (SparkFactionAPI
0.1.5.13+), else a main slot. The Seeker never renders in the `gui.sparkwitch.skills` panel.

Seeker remote streaming (unlimited range, owner decision 2026-10-04) is server-only and never moves
the body: `remote/SeekerRemoteStreaming#focusOf` names the device a live session shows (alive owner,
not a spectator, its own server camera, server bookkeeping matching the synced focus), and the three
`mixin/seeker/SeekerRemote*` mixins centre that owner's chunk view (`sendWatchPackets`), chunk batches
(`sendChunkBatches`) and entity tracking (`EntityTracker#updateTrackedStatus`: the focus is always
tracked, other entities are measured from it) on the device, chaining with SparkStrength's drone
mixins on the same calls. Player chunk tickets stay on the body's real section, so the body's chunks
stay loaded and ticking on the server. On `END_WORLD_TICK`, after every component tick, the service
refreshes an expiring `sparkwitch:seeker_remote` ticket (radius min(view, 8) + 2) around the focus
and calls `updatePosition` for the owner, then keeps re-evaluating for 40 ticks after the session
ends; a new or switched session streams at once from `SeekerRemoteSessionService#open`. While the
owner is the owner of record, every referenced device also holds an expiring
`sparkwitch:seeker_device` ticket (car radius 2, entity-ticking; camera radius 0, loaded only), so an
unsaved device never unloads under its owner; the game-start and finalize sweeps stop the refresh and
the tickets expire on their own (40-tick lifetime, never removed explicitly).

The owner's client follows that streaming without trusting entity or chunk presence.
`client/seeker/remote/SeekerRemoteViewClient` starts a session on the server's word and re-resolves the
focus by its synced id every tick (`SeekerRemoteViewRules#linkStep`): while the focus is missing (a
far open, an atomic switch to an untracked camera, a device re-tracked as a new instance) the session
stays locked, nothing is driven, looked at or cycled, and the CCTV overlay shows an opaque CONNECTING
panel (`hud.sparkwitch.seeker.view.connecting`) until the focus and its surrounding chunks are on the
client. A switch never ends the view; only `SeekerRules.ATTACH_TIMEOUT_TICKS` (100, shared with the
server's CAR attach deadline) without a focus gives up as "Signal lost" and sends close. A re-tracked
car of the same session keeps its move sequence (`SeekerCarClientDriver#retrack`), and the driven car
holds still until the chunks it stands on and could enter are loaded. The client view centres the
server's chunk view on the device, so the body's own chunks unload on the client:
`client/seeker/remote/SeekerBodyHold` with `client/mixin/seeker/SeekerBodyFreezeMixin`
(`ClientPlayerEntity#move` HEAD) freezes the body while its surroundings are missing from the
`ClientChunkManager` (`ClientWorld#isChunkLoaded` is always true in 1.21.1), during the session and
after it (capped at `SeekerRemoteViewRules.BODY_SETTLE_MAX_TICKS`, behind a RETURNING panel), so its
movement packets never report a fall. `client/mixin/seeker/SeekerRemoteTerrainGridMixin` centres the
vanilla terrain render grid on the viewed device; `client/mixin/SparkWitchClientMixinPlugin` skips it
when Sodium is loaded, because Sodium overwrites `WorldRenderer#setupTerrain` (an injection into an
overwritten method crashes even with `require = 0`) and already centres on the camera.

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
every player; its packet carries only a presence flag, the moment Fiend's UUID, remaining ticks and the remaining
Dash cooldown (the real value only to the moment Fiend, 0 to everyone else), never absolute server time or the match
id; clients count both down only for display, and it is never persisted. It also keeps a
server-only, never-synced spent ledger bound to the match id. A dormant Fiend (Fiend role, playing and alive, not
the moment Fiend, not spent) dies only to `wathe:fell_out_of_train`, `wathe:escaped` and `wathe:vanilla_death`:
`mixin/fiend/GameFunctionsFiendImmunityMixin` is a cancellable HEAD guard on Wathe's 5-arg `killPlayer`
(priority 1100, so SparkFactionAPI's affect veto runs first) that ignores `force`, so the owner-approved piercing
kills (bell toll, time curse, portal crush) do not reach it either. Only a kill that guard cancelled pays a hit reaction, once
per attack: `wathe:gun_shot` (every gun) gives the Fiend +50 gold and Speed III 5 s, and puts a 20 s item-cooldown
floor on every other participant within 8 blocks, queued at the hit and applied at END_SERVER_TICK through
SparkFactionAPI `ForcedCooldowns.raiseAll(player, CooldownKind.ITEM, …)` (exact, never shortened; role-skill counters
untouched; the registry's exemptions skip the Seeker car and NoellesRoles `timed_bomb`, whose cooldown is the Bomber
pass gate); a hand-held
stab, recognised only by `FiendStabScope` around Wathe's `KnifeStabPayload` receiver, +50 gold and 4 notes;
`wathe:bat_hit` and `sparkwitch:ceremonial_blade` +100 gold. A bomb the Fiend passed that kills its direct
recipient pays +50 only while the Fiend is still dormant (server-only `FiendBombLedger`). The Taotie cannot
swallow a dormant Fiend (SparkWitch guard on NoellesRoles `TaotiePlayerComponent.swallowPlayer`; SparkFactionAPI's
Taotie packet guard is live but runs only affect policies), and a dormant Fiend is never a Serial Killer target
(`SerialKillerPlayerComponentFiendTargetMixin` filters `getEligibleTargets` and `isTargetValid`; the Bodyguard
copies that target). A dormant Fiend counts as not alive in every last-one-standing count: `WitchWinConditions`
and Murderous Witch `checkWin` skip it directly, and NoellesRoles'
Jester-moment and Corrupt Cop loops (`lambda$registerEvents$14` alive-check ordinals 6 and 9),
`countAliveAndNotSwallowed` and Taotie `hasSwallowedEveryone` reach `FiendWinExclusion` through additive
`@WrapOperation`s pinned to b58fa5f. The Fiend Moment is a 200-gold, stock-1 shop entry whose all-or-nothing
`onBuy` starts it (crowbar and Speed II for 2400 ticks, no shield; owner tuning 2026-10-04); the crowbar carries the
`sparkwitch:fiend_moment_crowbar` custom-data marker and every marked stack is taken back when the moment ends
without a win, and a disconnect (`wathe:escaped`) ends it as "ended", not "slain". `FiendWinService` runs in
phase `sparkwitch:fiend_moment_win`, ordered before `Event.DEFAULT_PHASE` on `CheckWinCondition`: no moment →
abstain; the moment Fiend offline, dead, swallowed, re-roled or the match changed → end the moment (a swallow
also marks it spent) and abstain; complete → `neutralWin`; otherwise `block()`, so every other win, `TIME`
included, waits. The moment Fiend's crowbar cooldown is written as exactly 5 s after a door pry or vent-hatch use,
without a second redirect. During the moment only, the moment Fiend has Dash on the shared NoellesRoles ability key
(owner decision 2026-10-04): the empty C2S `sparkwitch:use_fiend_dash` (`net/UseFiendDashC2SPayload`, registered on
both sides by `FiendNetworking` from `FiendFeatureService`) reaches `FiendDashService`, which re-checks against server
state, in `FiendDashRules.verdict` order, the role-gated moment Fiend, an ACTIVE round, a running moment (active and
not complete), playing and alive, not swallowed, not stunned, not SparkTraits role-skill blocked
(`SparkTraitsKillerBridge.isRoleSkillBlocked`; absent or failing SparkTraits means no block) and ready, with Grand
Witch Fear last (refused with the Fear skill message); every refusal costs nothing. A use grants Speed IV for 10 s
(`FiendMomentEffects.grantDash`; vanilla upgrades the moment's Speed II instance in place, so it falls back to
Speed II, and the moment end removes the owned instance at either level) and moves the absolute Dash ready tick to
30 s after the use. The ready tick lives in `sparkwitch:fiend_moment`: ready at the moment start and cleared with the
moment, so the cooldown dies with it. The id sits on the Control Expert stun, Seeker session, Riftwalker session and
Grand Witch Fear deny-lists, and forced cooldowns reach Dash only through the `sparkwitch:fiend_dash` store. The
client outline is a cancellable HEAD on `WatheClient.getInstinctHighlight`
(`remap = false`, priority 500; lower-priority HEADs run first, so it precedes SparkTraits, Wraith and Black
Raven): while a moment is active the moment Fiend sees every other playing, living, non-spectator player and every
other viewer sees the moment Fiend, both in `FiendRules.COLOR`; other pairs fall through. The Grand Witch
Obscure/Fear `@WrapMethod` veto (`WatheClientFearInstinctMixin`) exempts those moment pairs, like the Final Moment
(owner decision, 2026-09-30); its swallow veto still applies. The countdown HUD is a
`HudRenderCallback` line for every player, never the action bar. The Dash HUD is a role-owned bottom-right line
shown only to the moment Fiend (`client/fiend/FiendDashHud`, drawn from `FiendClient`'s HUD callback), and
`SparkWitchClient`'s ability-key dispatch sends the request through `client/fiend/FiendDashClient`.
The Fiend is absent from `isRegisteredSparkWitchRole` and `WitchSkillRegistry` and never renders in the
`gui.sparkwitch.skills` panel.
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
clear, revolver 150 and crowbar 50, each stock 1, then restore. The tablet is not sold: the Insider is in SFA
`PoliceRoles`, so SparkStrength issues it a free tablet at round start on the police channel. That also makes it a
SparkStrength police elector for meetings and votes (`TabletChannelResolver.isPoliceElector`).
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
screen reads "嘉豪胜利！" over "整列列车的人都被嘉豪们豪完了", and Wathe's `didWin` and `GameRecordManager.endMatch`
read the same rows. Every other win keeps Wathe's rows. Team Jiahao has its own faction color,
`InsiderRules.TEAM_JIAHAO_COLOR` (tuhao gold `0xFFC125`), apart from the Insider's mint and the Corrupt Cop's navy:
`client/mixin/insider/RoundTextRendererJiahaoTitleMixin` (`@ModifyExpressionValue` on the neutral title's
`RoleAnnouncementTexts.getForRole(Identifier)` lookup in `RoundTextRenderer.renderHud`) swaps an Insider-led title's
role text for an unregistered twin built with the same id in that gold, so the lang keys stay
`announcement.win.insider` / `game.win.insider` and the Insider's own role announcement stays mint. SparkFactionAPI's
`@Redirect` on the `winText` read that follows still wins for a custom faction win.
Insider presentation is client-only. One `GetInstinctHighlight` listener answers `always` only while its condition
holds. At priority 65 (below SparkStrength's tablet suspect mark at 80, which keeps its color; SparkStrength no
longer outlines police-network members), a living Insider holding instinct sees every other living, visible player in
`0x00FFD0`; the Corrupt Cop is answered at 93 in `0x193264`, so the partner stays navy even under a tablet suspect
mark. At priority 93 (above SparkStrength's Corrupt Cop x-ray at 90, below the Seeker mark 95, `skip()` 100 and
suppression 102), a living Corrupt Cop sees the living, visible Insider in `0x00FFD0` while holding instinct or
during its Moment vision window. Targets hidden by SparkTraits' `isInstinctHidden` get no Insider answer.
Killers, SparkTraits Impostors included, get no Insider answer and no cohort line (owner 2026-10-05, replacing the
D3 Impostor disguise): Wathe's default paints the Insider green like any passenger, with no "杀手同伙".
`InsiderCohortRoleNameMixin` draws the `game.tip.sparkwitch.jiahao_cohort` label in the Team Jiahao gold between an
Insider and any Team Jiahao member, both ways, with the witch cohort trigger. The Insider shares killer-style
instinct light through `WitchInstinctClientHooks`. The Insider never renders in the `gui.sparkwitch.skills` panel.

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
`BlindEchoSilhouetteMixin`; its per-pass white vertex consumer drops every write once the pass ends,
because PatPat 1.3 keeps the provider and draws through it at the next frame's `AFTER_ENTITIES`), and `client/mixin/blind/BlindGameRendererMixin` runs the pass after
`GameRenderer#render`'s `Framebuffer#beginWrite(Z)` with `shift = AFTER`, so it draws over the
Seeker and Black Raven filters injected at the same call, and the HUD is drawn on top. The view
fails closed to black, never to the plain world: an Iris shader pack in use (reflective check; an
error counts as in use) paints black and shows `hud.sparkwitch.blind.view_unavailable` (turn shader
packs off); a load or render failure, or 20 consecutive missed depth captures, with no shader pack
paints black and shows `hud.sparkwitch.blind.view_failed` instead, never the shader-pack hint. A
failed pipeline is rebuilt after 2, 4, 8, 16, then every 30 s (`BlindEchoMode.retryDelaySeconds`;
reconnect and resource reload still reset it), and the first failure logs the stack with the GPU
renderer, vendor and OpenGL version. A swallowed, off-camera or fake-death spectator Blind sees black
with no line art. While the view is active,
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
environment and are never gated, except a hidden player's fishing bobber and Magician puppets, which stay
hidden like a never-perceived player. A body another mod draws in place of the
player model keeps its own shape (owner 2026-10-07). A perceived SparkTraits Pig, whose renderer cancels
`PlayerEntityRenderer#render` before `LivingEntityRenderer` runs, stays a pig outline in the line art and the
silhouette: the pig body and head, with the helmet cut by the armor veto. Feature-like extras drawn outside the feature
loop ask the public `SparkWitchApi.hidesFeaturesFromBlind`, installed by `BlindClientGateWiring`. It is true under
either Blind feature skip: the world frame (`BlindClientGates.suppressesFeatures`) or the silhouette pass. SparkStrength's
skateboard under a replaced body skips itself while it holds. Bumps are client-only (no
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
composes with SparkAssist's volume seams on the same two methods and never writes options. A Blind hit
by a Holy Flash gets only its audio side (owner decision Q12): `HolyFlashOverlayRenderer` draws no mask
while `BlindView.isActive`, the client-only tinnitus loop (PLAYERS, never ducked by Attune) and the
Holy Flash muffle still apply, `HolyFlashSoundSystemMixin` and `BlindAttuneSoundSystemMixin` wrap the
same two calls and their factors multiply (0.1 x 0.25 at the lowest, never 0), and server perception
never reads `sparkwitch:holy_flash`, so a flashed Blind's pulses are unchanged. While a
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
one before it when the last slot is selected) into the slot a misplaced cane vacated, else a main slot
or the offhand, preferring the shown second row (SparkFactionAPI 0.1.5.13+) over hidden storage, and
with no room at all nothing moves and the placement retries every tick. A same-match re-assignment keeps its
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
nothing. Forced cooldowns reach Attune only through the `sparkwitch:blind_attune` store. The cane,
like every carried non-exempt item, is floored by `raiseAll`; its item nominal only sizes nominal-based
extensions such as GW-AC (see the SparkFactionAPI `ForcedCooldowns` registrations). The White Cane and the ComTac VIII are bound: never an item entity (drop, death drop),
never outside the holder's own inventory slots (head slot included; QUICK_MOVE only for the ComTac
quick-equip from the hotbar into an empty head slot; never the offhand (Wathe's server already
refuses the swap-hands action in a round), a container, the crafting grid, an item frame, an armor
stand, an allay or a decorated pot, where `BlindKitDecoratedPotMixin` answers `SKIP_DEFAULT_BLOCK_INTERACTION`
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
task, no Impostor check. Any role change away from the Blind strips the kit at once
(`BlindLoadoutService.onRoleAssigned`).

Grand Witch rework state uses separate `sparkwitch:grand_witch_runtime` and
`sparkwitch:witch_factor_world` components; the existing shared packet and NBT layouts remain unchanged. Sword
kill readiness (30s) is independent of the item dash cooldown (5s). `sparkwitch:grand_witch_runtime` syncs and
saves only the sword kill cooldown (NBT `SwordKillCooldown`). Grand Witch recruitment is gone (owner, 2026-10-05; the
Bewitched below replaces it): the `sparkwitch:grand_witch_recruitment_round` world component, the runtime's
`RecruitmentCount` and `RoundParticipants` fields, Emma's `Revealed` list, the C2S payload
`sparkwitch:recruit_accomplice` and the replay cause `sparkwitch:grand_witch_recruitment` are no longer registered,
read or written. The Grand Witch has no secondary-key (key N) handler; her inventory card shows the Witch Factor and
the Ceremonial Sword rows only.
A placed Hunter trap is reclaimed only by its owner while still the real Hunter, so an ex-Hunter whose role changed
gets no trap back (the trap itself stays armed until it expires or the round ends).
Sword piercing
applies to role/item shields, not trait protections such as Last Stand or Last
Escape; protection costs and retaliation keep their normal side effects.

Special accomplices (`roles/witch/accomplice/variant/AccompliceVariants`) and the Bewitched inherit every basic
Accomplice rule through `WitchFactionRules.isAccompliceLike`, which is true for the plain Accomplice, a registered
variant, or the Bewitched (`WitchFactionRules.isBewitched`, exact). The Bewitched is never an `AccompliceVariants`
entry, so it is never rolled. Rules that use `isAccompliceLike`:
- witch-faction membership (`isWitchFactionMember`): win counts, blackout, Fear and Obscure immunity, the cohort
  label, and Curser visibility;
- killer-style instinct light, the dropped-item outline, and the hidden-Phantom skip;
- instinct colors: the Grand Witch and every accomplice see each accomplice (the Bewitched included) in that role's
  own color;
- passive, direct-kill and task money (+50 per task, see "Witch task money" below), accomplice starting money, and
  the Grand Witch's +25 team-kill share;
- Grand Witch mana for accomplice kills, and hidden poison vision;
- the witch factor: accomplices are never carriers and always see the network;
- Emma's fatal backlash;
- the Rift Gate instinct outline.

Rules keyed on role ids keep their fixed id sets, which list `sparkwitch:bewitched` explicitly, and also check
`WitchFactionRules.isAccompliceVariantId`. That method reads the live registry on every call and is false for the
Bewitched. These rules are `HunterRules.isInstinctTrapViewer` (`BEWITCHED_ROLE_ID`), `HunterTrapClientHooks`, and
`SeekerInstinctRules.isWitchInstinctRole` (`BEWITCHED_ID`).

`isAccomplice` stays exact, so the plain Accomplice shop (`AccompliceShopService`) never touches a variant or the
Bewitched. These never include variants merely for being variants, nor the Bewitched: `WitchManaRules.isManaRole`
(the Abyss Listener and the Riftwalker are added by exact role) and `SparkWitchRoleRegistry.isRegisteredSparkWitchRole`
(the Abyss Listener and the Riftwalker are added by exact role, each for its shared skill).

Witch task money (owner request 2026-10-04): `WitchEconomyService.onTaskComplete` pays
`WitchFactionRules.WITCH_TASK_MONEY_REWARD` (+50) per completed task to the Grand Witch and every accomplice (plain or
special) while the round is ACTIVE and the player is playing, alive, not a spectator, not creative and not
Wraith-restricted (`WitchFactionRules.earnsTaskMoney`). It follows the Insider model: no Impostor skip, because witch
roles roll only UNIVERSAL SparkTraits (SparkTraits `TraitRoleEligibility`), and SparkTraits task bonuses stack on top.
Wathe, SparkStrength, NoellesRoles and SparkFactionAPI pay witch roles nothing per task (SparkFactionAPI's
`RewardKind.TASK` is never asked). Mana per task and the Grand Witch's sword task counter are unchanged; the Curser
(NONE mood, no tasks), the Apprentice Witch and the Murderous Witch earn nothing here.

The `gui.sparkwitch.skills` panel (`WitchSkillPresentationRules.shouldShowInventorySkillPanel`) belongs to the Grand
Witch, Apprentice Witch and Murderous Witch, plus accomplices (owner decision D13: plain and special, via
`WitchFactionRules.isAccompliceLike`); each shows only its own skills. An accomplice's own skills are exactly
`AccompliceVariants.hooks(role).ownSkillIds()` (`AccompliceVariantHooks`, default empty). The plain Accomplice and the
Bewitched (accomplice-like, not a variant) get `NONE` hooks, so they never show the panel; a variant that owns no
skill does not either. Registration in `WitchSkillRegistry`, the `sparkwitch` namespace, or shared dispatch, storage,
packets and cooldowns grants no access, and every other role stays excluded. The gate runs every client frame, so
`AccompliceVariants.hooks` reads the same lock-free snapshot as `isVariant` and `variants`. Mana in the panel (header
tail, cost line, "not enough mana" pill) stays gated by `WitchManaRules.isManaRole`.

A variant must register during common mod initialization, because client rules read the same registry. Hard-coded
`sparkwitch:accomplice` lists in other repos still need each variant and `sparkwitch:bewitched`, for example
SparkTraits `isBlockingTeamWinNeutral`.

Variants never appear naturally; the Bewitched promotion rolls the special-accomplice pool.
`BewitchedPromotionService.promote` picks the role once, before any mutation (`BewitchedPromotionRules.choose`):
1. **Lock.** The player's `/sparkwitch:forceAccompliceRole` lock wins: the plain Accomplice always; a special
   accomplice not used this round even when it is disabled; a used one (or an id no longer registered) is ignored and
   the roll runs.
2. **Roll.** The pure `AccompliceVariantRoll.pick` makes a uniform choice among the registered variants
   (`AccompliceVariants.variants()`, in registration order) that are enabled (`game.isRoleEnabled`), not used this
   round, and not reserved by another lock. Its `java.util.Random` is seeded from `world.getRandom().nextLong()`. When
   no variant is left, the player becomes the plain Accomplice (Bewitched D3). A variant counts as used when the round
   ledger records it or when the role map holds it (`!game.getAllWithRole(role).isEmpty()`). A lock reserves its role
   only while its holder is a living player (in the role map, not dead) whose real role is the Bewitched
   (`BewitchedPromotionRules.reservedByAnother`); a holder who dies before promoting frees the seat (Bewitched D4).

The round ledger is `sparkwitch:accomplice_variant_round` (`AccompliceVariantRoundComponent`, state in
`AccompliceVariantRoundState`), a never-synced world component whose only NBT key is `UsedVariants`, a list of role id
strings. It is appended to `custom.cardinal-components` after every earlier entry; only later role components follow
it (the special accomplices' `sparkwitch:abyss_zone_exposure`, `sparkwitch:rift_session` and `sparkwitch:rift_gates`,
then `sparkwitch:bewitched`). Its round lifecycle is owned by `GrandWitchFeatureService`:
- **Round start.** At `ON_FINISH_INITIALIZE` it resets the ledger and seeds it with every variant already in the role
  map (`!game.getAllWithRole(role).isEmpty()`), so a forced round-start variant stays used after it dies and becomes a
  Curser.
- **Promotion.** A promotion into a variant marks it used right after `game.addRole` (`ledger.markUsed`).
- **Round end.** `clearRound` empties the ledger at `ON_FINISH_FINALIZE`.

After a promotion into a variant commits, the shared transaction calls `AccompliceVariants.hooks(role)
.afterPromotionCommitted(player)` once, after the `finally` block that restores the balance, rebuilds the shop and
syncs, and before the announcement and the chat lines. A `RuntimeException` from the hook is logged and never undoes
the promotion. The inventory is kept, so a `RoleAssigned` grant is still present: hooks must be idempotent (the Abyss
Listener's `AbyssListenerLoadout.grantAfterPromotion` reconciles to one gun; the Potion Gunner's
`PotionGunnerLoadoutService.ensureLauncher` is idempotent).

`AccompliceShopRules.entriesWithout(ids...)` returns the plain Accomplice entries minus the given `PlannedEntry.id()`
values, in their original order. Variants call it from their own `BuildShopEntries` listeners. An unknown id throws.
The plain `AccompliceShopService` stays exact.

The Bewitched (`sparkwitch:bewitched`, 魔化使, color `0x8A6E99`) replaces Grand Witch recruitment (owner, 2026-10-05;
Dn/Cn below are the owner decisions in `docs/plans/bewitched/decisions.md`). It only does tasks, and after 2 completed
tasks it is promoted to an accomplice role through the pool above.
- **Registration.** `BewitchedRole.DEFINITION`: witch faction, the Accomplice profile (`MoodType.FAKE`, unlimited
  sprint, `canSeeTime(true)`), `appearanceCondition(context -> false)`. It is registered right after the Riftwalker and
  before the Wind Spirit in `registerFactionApiRoles` (the Abyss Listener stays right after the Accomplice, and
  nothing may follow the Insider), and it follows the Riftwalker in the assassin-guess order. It is not a Wathe
  special role, so `/wathe:forceRole` can force it. It is not in `isRegisteredSparkWitchRole` (it owns no skill),
  never shows the witch skill panel, and stays out of the Black Raven disguise pool like the Accomplice (not
  civilian-base).
- **Dealing (D1).** `WitchRoleAssignmentService.assignAfterNeutralsBeforeCivilians`, right after the Grand Witch is
  dealt: when the round then has at least one Grand Witch (dealt, neutral-drawn or forced) and the Bewitched is
  enabled, it deals `WitchRoleCounts.bewitched(n)` minus the already forced Bewitched on the shuffled `NO_ROLE` seats,
  where `bewitched(n) = n < 18 ? 0 : (n - 18) / 6` (owner, 2026-10-06: an 18-23 round has the Grand Witch alone,
  24-29 deal 1, then +1 per further 6). There is no new mixin call: `MurderGameModeMixin` still runs
  the Hunter/Orthopedist pairing, the witches and the Insider, in that order, before `assignCivilians`.
- **Before promotion (D2).** A witch-faction member with teammate vision and accomplice money (starting money,
  passive, kill rewards, the Grand Witch's +25 share), but no shop. `BewitchedShopService` empties its shop on both
  sides from the exact synced role, in its own `BuildShopEntries` phase `sparkwitch:bewitched_shop_clear` ordered after
  the default phase (so no default-phase listener can add entries back, whatever the mod load order), and its
  `CanSeeMoney` listener returns `ALLOW` for a living Bewitched (an empty shop would hide the balance).
- **Task counter (C3).** `sparkwitch:bewitched` (`BewitchedPlayerComponent`, player, `NEVER_COPY`, synced to its owner
  only, NBT `PromotionTasks`) is the last `custom.cardinal-components` entry, after `sparkwitch:rift_gates`. The Wathe
  `TaskComplete` listener in `SparkWitchEvents` (registered right after the Grand Witch's) counts only in an ACTIVE
  round (never once Wathe is STOPPING), for a playing and alive player whose real role is the Bewitched. On the 2nd
  task it enqueues the UUID and never changes the role, because Wathe is iterating the task map. Round start, round
  end and `ResetPlayer` clear the counter and the queue.
- **Queue.** `BewitchedPromotionQueue.finishPromotions` runs at `END_SERVER_TICK`, in enqueue order, and re-validates
  each entry (`BewitchedRules.queueAction`): offline → keep; round not ACTIVE, not alive, real role no longer the
  Bewitched or fewer than 2 tasks → drop; inside a Rift Gate (`RiftSessionService.isInside`), swallowed by a
  NoellesRoles Taotie (`NoellesTaotieSeekerBridge.isSwallowed`), hijacked by a Kidnapper
  (`KidnapperControlComponent.isControlled`) or afflicted by a Hunter trap (rooted, fractured or carrying trap-poison
  credit in `HunterPlayerComponent`) → keep and retry next tick; else promote. The Kidnapper and Hunter `RoleAssigned`
  listeners reset that victim-side state on any role change, so promoting then would end the hijack, heal the injury
  and drop the Hunter's poison credit; each state ends on its own. A throwing
  promotion is logged once and dropped; if the role never changed, the counter is rewound to 1 (`rewindForRetry`), so
  the next task queues it again.
- **Transaction (C4).** Choose the role (above) → capture the balance → `game.addRole` inside
  `SparkReplayApi.withRoleChangeCause(sparkwitch:bewitched_promotion, (UUID) null, …)` → mark a variant used →
  clear the player's lock and counter → `RoleAssigned` (try/catch: logged, never undone) → `finally`: restore the
  balance, `shop.initializeShop(ShopUtils.getShopEntriesForPlayer(player))`, `game.sync()`, `shop.sync()` → the
  variant hook → `WraithRoleAnnouncementService.announceCurrentRole` (public; the existing
  `WraithRoleAnnouncementS2CPacket`) → chat lines: `message.sparkwitch.bewitched.promoted` (role name) to the
  player and `message.sparkwitch.bewitched.promoted_grand_witch` (player name, role name) to every living Grand
  Witch. The inventory is never cleared.
- **Death before promotion (C6).** The normal witch death flow (Wraith → Curser); the ledger is untouched, and the
  seat of a lock the dead player held returns to the pool.
- **Forced lock (D4, C5).** `/sparkwitch:forceAccompliceRole <role> <players>` (`command/ForceAccompliceRoleCommand`,
  permission `sparkwitch.command.forceaccomplicerole`, default level 2, run through `Wathe.executeSupporterCommand`; a
  bare path resolves to the `sparkwitch` namespace; suggestions are `accomplice` plus every registered special
  accomplice). There is no round guard: set outside a round, a lock applies to the next round; set during a round, to
  the current one (the round is read from the world where it runs, since map voting can move it off the overworld). A
  special accomplice is locked to one player at a time: more than one target → `special_single_target`; another
  holder → `already_locked`, except that during a round a holder that is not a living Bewitched
  (`BewitchedPromotionService.isLivingBewitched`, the same test as the reservation) has its stale lock dropped. The
  plain Accomplice may be locked to many players. Locks are stored per UUID on the overworld `WitchWorldComponent`
  (NBT `ForcedAccompliceRoles`, a list of `Player`/`Role` strings; never in the world sync packet), cleared by
  `clearRoundState` and, explicitly on the overworld store, by `BewitchedFeatureService` at `ON_FINISH_FINALIZE`, and
  cleared for the player when its promotion commits.
- **Client.** `client/bewitched/BewitchedClientPresentation` draws `hud.sparkwitch.bewitched.progress` (`n/2`)
  bottom-right for a living Bewitched on a confirmed SparkWitch server through `HudRenderCallback` (hidden under F1 or
  without Wathe's train HUD).
- **Lang.** `announcement.role/title/goal/goals/win.bewitched`, `game.win.bewitched`,
  `announcement.role/goal/win.sparkwitch.bewitched`, the HUD line, the two promotion lines, the command feedback
  (`command.sparkwitch.force_accomplice_role.*`) and
  `replay.sparkfactionapi.role_changed.cause.sparkwitch.bewitched_promotion`. The shared texts
  `announcement.goals.grand_witch`, `announcement.goals.riftwalker`, `tip.letter.riftwalker.tooltip1/2` and
  `tip.letter.potion_gunner.tooltip1` describe the Bewitched promotion; these and
  `skill.sparkwitch.emma_factor.network` no longer mention recruitment in either language.
- **Local tests** under `src/test/java/dev/caecorthus/sparkwitch/`: `roles/witch/bewitched/` (rules, promotion rules,
  registration, promotion source contracts, lang), `registry/BewitchedDealingContractTest`,
  `component/ForcedAccompliceRoleLocksTest`, `command/ForceAccompliceRoleCommandTest`, the Bewitched cases in
  `roles/witch/accomplice/variant/AccompliceLikeParityTest`, and the ledger lifecycle in
  `roles/witch/grandwitch/GrandWitchVariantLedgerLifecycleContractTest`.

Features that force a cooldown on another player (penalties, auras) go through SparkFactionAPI `ForcedCooldowns`
(floor 0.1.5.11), never through a counter directly; affect vetoes stay the caller's job (`canAffectPlayer`).
`compat/cooldown/SparkWitchForcedCooldowns` registers these role-skill stores, in this observable order (it is the
`slots` order):
1. `sparkwitch:witch_skill`: the shared `WitchPlayerComponent` cooldown, for any player with a skill (`hasSkill()`;
   Saint has none). Remaining time includes a pending deferred cooldown (`max(cooldown, window + deferred)`); the
   nominal is the active `WitchSkillDefinition.cooldownTicks`. Raises and extensions go through the additive
   `WitchPlayerComponent.raiseForcedCooldownFloors`, which floors the shared cooldown and, only while a deferred
   cooldown is already pending, that too, so a penalty during an active window survives the window end; it never
   shortens, never creates a deferred cooldown, and syncs once. Never forced while the Kidnapper's drag skill carries a
   body (the same counter gates the release press).
2. `sparkwitch:saint_hellfire`: `SaintPlayerState.raiseHellfireCooldown` plus `component.sync()`; never forced while
   Hellfire burns (its end writes the post cooldown). Nominal 1200.
3. `sparkwitch:orthopedist` and 4. `sparkwitch:saboteur`: their exact, syncing public setters. The Orthopedist gate is
   the widened `isRole` self gate, as in `OrthopedistSkillService.use`.
5. `noellesroles:ability` (`AbilityPlayerComponent`, always through `setCooldown`, the only syncing write), for the
   13 roles whose ability packet reads or writes it on the server: Voodoo, Morphling (gated only), Vulture, Swapper
   (write-only; its gate is client-side), Recaller, Phantom, Pathogen, Noisemaker, Reporter, Detective, Silencer,
   Party Animal and Spirit Walker (`noellesroles:spiritualist`). The nominal is the full post-use value the holder's
   current role writes: the Pathogen's dynamic `PathogenPlayerComponent.getBaseCooldownTicks`, else the literal
   mirrored in `NoellesAbilityNominals` (ticks: Voodoo 600, Vulture 100, Swapper 1200, Recaller 600 = the recall,
   placing the mark writes 200, Phantom 1800, Noisemaker 3600, Reporter 600, Detective 1800, Silencer 900, Party
   Animal 1200, Spirit Walker 1200); the Morphling, which only gates on the counter, has none. A contract test reads
   the pinned jar's bytecode, so a NoellesRoles bump that changes any write fails it.
   6. `noellesroles:taotie_swallow` (`setSwallowCooldown`; nominal = the private per-round
   `calculatedSwallowCooldown` every swallow writes, read through the cooldown-owned
   `NoellesTaotieForcedCooldownAccessor`; non-positive means unknown), 7. `noellesroles:assassin` (`setCooldown`;
   nominal `AssassinPlayerComponent.COOLDOWN_TICKS`). NoellesRoles gates use `isRole`, so these stores (and the
   nominal lookup) also match the acting role of a disguised Black Raven (`ForcedCooldownRoles`). All members are
   checked against the pinned NoellesRoles 1.7.6 jar.
8. `sparkwitch:blind_attune` (owner decision 2026-10-03, appended last so earlier slots keep their positions): the
   Blind's Attune, for a real, active Blind with a granted kit in an ACTIVE round (`BlindAttuneService`'s gate; the
   Blind is never a disguise target). Remaining time counts to the absolute ready tick, so it includes a running 10 s window, as the HUD
   shows; the nominal is the 45 s post-window cooldown (`BlindRules.ATTUNE_COOLDOWN_TICKS`, 900), as the witch-skill
   nominal leaves its window out. Every write only moves the ready tick later (`ForcedCooldownMath.raiseReadyTick`)
   through the owner-syncing `BlindComponent.setAttune`; it never touches the window and never shortens. Raise and
   extend are the SparkFactionAPI defaults.
9. `sparkwitch:fiend_dash` (`FiendDashCooldownStore`, coordinator default 2026-10-04 following the Blind,
   appended after it): the Fiend's Dash, for the role-gated moment Fiend in an ACTIVE round (`FiendDashService`'s
   role and round gates), so it exists only while a moment runs.
   Remaining time counts to the absolute Dash ready tick in `sparkwitch:fiend_moment`; the nominal is the 30 s
   post-use cooldown (`FiendRules.DASH_COOLDOWN_TICKS`, 600). Every write only moves the ready tick later
   (`ForcedCooldownMath.raiseReadyTick`) through the resyncing `FiendMomentWorldComponent.setDashReadyTick`; it never
   shortens, never touches a running Speed IV, and dies with the moment. Raise and extend are the SparkFactionAPI
   defaults.
`SparkWitchItemCooldownNominals` supplies the full post-use cooldown of every SparkWitch item that writes one (Taser,
Disruptor, Shock Device, shotgun empty reload, Time Pocket Watch, toll bell, Angler rod and edible fish, Holy Flash,
White Cane (its tap writes the 5 s window plus the 10 s cooldown), Ninja shuriken, knife and Grappling Hook (the 10 s
written when a hook cycle ends), Feather Blade, Knockout Drug, Ceremonial Sword dash, Fire Poker, Shriek Gun, and the
1 s anti-repeat writes of the potion launcher and the Rift Gate, which no current consumer reaches) and of NoellesRoles
items with a public constant (Antidote, Repair Tool, Poison Needle) plus the 200-tick neutral master key; round-start
cooldowns are not nominals, and Wathe items fall through to Wathe's own table. The local
`SparkWitchItemCooldownNominalsTest` scans every main source file for item cooldown writes (direct, through a local
`ItemCooldownManager`, or through a ticks-parameter helper) and requires each written constant, by its qualified name,
to be in the provider, unless it is listed as round-start, non-nominal, or a variable write with its reason.
The Seeker car (`sparkwitch:seeker_car`) is registered as an item exemption: `SeekerCooldowns` stays its sole
"max + exact" writer and offers no write path to other features, so the Fiend gun-hit aura skips it too (owner
decision, 2026-10-02). Not registered (out of scope): Wathe shop-entry cooldowns, the Black Raven disguise switch,
the Curser and Guardian Angel, and SparkStrength components.
Removing an item cooldown releases SparkWitch's own timer behind the item (2026-10-05). The removal is
`ItemCooldownManager.remove` on the server. For these items it comes from SparkFactionAPI
`/sparkfactionapi:clearCooldown` (main hand), Wathe's round reset, or SparkTraits dropping a parry lock once its
holder is dead or the round ended. `ItemCooldownRemovalMixin` runs at TAIL, so a `remove` that SparkTraits cancels at
HEAD (its forced melee floor) releases nothing, and expiry never calls `remove`. `SparkWitchItemCooldownReleases`
then acts by item id:
- White Cane: the `BlindComponent` cane ready tick moves to now; a running window is kept.
- Clock: `ClockReadyAt` moves to now. Both ready ticks stay positive, since a positive tick is the "kit granted" /
  "same round" flag.
- Ceremonial Sword: the 30 s kill cooldown is zeroed.
- Any of the five edible fish: every cooling fish is removed, because any cooling fish refuses all of them.
Other items gate only on the vanilla entry, so the command already clears them fully.
The admin command alone also beats Saint Karma (owner decision, 2026-10-05). `SparkFactionClearCooldownMixin` wraps the
command's `remove` call (the same method body from SparkFactionAPI 0.1.5.12 through 0.1.5.15). When that call really
cleared the item, `SaintKarmaState` exempts the item for that player until the Karma ends or is triggered again. The
exemption is transient and never saved. Role mechanics that remove a cooldown (NoellesRoles Catalyst, the Bomber pass)
do not come through the command, so Karma still re-covers those items. SparkTraits lifts its own forced melee floor
for the same command, in its own `SparkFactionClearCooldownMixin`. Not released: the Black Raven disguise switch, since
the mask never carries an item cooldown.
Saint Karma writes are exact (SparkTraits facade, vanilla fallback), so Fast Hands or Stimulation no longer leave a
write below the Karma that restarts the slot's bar every tick. The Time Stealer holder's tick rewrites the Clock's
display, exactly through SparkTraits only, whenever it shows more than one tick less than `ClockReadyAt` (for example
after Last Escape halving), so the slot never shows ready while the Clock is refused.

The Abyss Listener (`sparkwitch:abyss_listener`) is a special accomplice. Its own Warden's Shriek renders in the
`gui.sparkwitch.skills` panel (owner decision D13): its `AccompliceVariantHooks.ownSkillIds()` is exactly
`sparkwitch:wardens_shriek`, and no other skill ever shows there for it.
- **Registration.** It is registered right after the Accomplice in `registerFactionApiRoles` (nothing may follow the
  Insider, so like the Curser it sits mid-list), with the Accomplice's profile (`MoodType.FAKE`, unlimited sprint,
  `canSeeTime(true)`) and `appearanceCondition(context -> false)`, and it follows the Accomplice in the assassin-guess
  order. As a registered `AccompliceVariants` entry it reaches every basic Accomplice rule through
  `WitchFactionRules.isAccompliceLike`; `isAccomplice` stays exact. Its own shop (`AbyssListenerShopService`) is every
  plain Accomplice entry, then the Deep Dark Spore Flask (`sparkwitch:deep_dark_spore_flask`) at 75 coins with no
  stock limit and no purchase cooldown.
- **Skill and mana.** Warden's Shriek (`sparkwitch:wardens_shriek`) is a `WitchSkillDefinition` with an exact-role
  selector, registered right after the Bell Ringer's Echo. The role is in `isRegisteredSparkWitchRole` only for that
  shared skill path; the skills panel reaches it only through its variant hooks (D13). It uses the Grand Witch's mana
  economy through `WitchManaRules.usesGrandWitchManaEconomy` (Grand Witch, exact Abyss Listener, or exact Riftwalker):
  it is a mana role (`isManaRole`), starts at 0 on every assignment (a mid-round promotion included), regenerates 1
  mana every 20 ticks up to the natural cap of 300, and earns 50 mana for a generic kill and 100 for a witch-mana-role
  victim. The Abyss Listener itself never counts as a witch-mana-role victim, so every other role's rewards are
  unchanged. The Grand-Witch-only bonus for an accomplice's kill pays living Grand Witches only, and the Abyss
  Listener's kills feed it. Its mana
  shows in the generic top-right `WitchManaHudRenderer` row (gated by `hasManaSystem`, not by the skills panel) and in
  the bottom-right skill line's "not enough mana" state. While the inventory is open, the skills panel card shows the
  Shriek with its 75-mana cost line, the "not enough mana" pill below 75, the cooldown gauge (60 s, or the 90 s
  initial cooldown), and the NoellesRoles ability key; the card's header mana tail then replaces the top-right mana
  row and the card replaces the bottom-right skill line, as for every panel mana role. A Fire Poker in its hand spends
  its mana like any mana holder's.
- **Suppression.** `AbyssSuppression` (server only) is shared by the shriek, the gun, and the zone:
  - `isParticipantTarget`: playing and alive, not spectating, not creative, not an active Wraith, has a role, not
    SparkTraits Last Stand pending, and not in SparkTraits Last Escape;
  - `isAlly`: the SparkFactionAPI effective faction is `sparkwitch:witch`;
  - `canAffect(actor, target, actionId)`: `isParticipantTarget(target)`, plus `SparkFactionApi.canAffectPlayer` when
    an actor is known. It excludes neither allies nor the actor and has no Vendetta rule, so each caller applies its
    own (only the gun adds Vendetta exact-pair isolation);
  - `hasRealSanity`: mirrors SparkTraits' effective mood type. An active Wraith never has real sanity; otherwise
    Conscience makes it REAL, or else a REAL base role proven not Impostor does. An unknown trait answer never grants
    it;
  - `drainSanity`: Wathe's `setMood(getMood() - amount)`, with no check of its own: Wathe's `setMood` already ignores
    non-REAL targets. A breakdown at −1 is allowed and unattributed;
  - `forceCooldowns`: only SparkFactionAPI `ForcedCooldowns.raiseAll` (exact and monotonic, role skills plus carried
    items), never a cooldown store directly;
  - `addEffect`: not ambient, no particles, icon shown, with the Abyss Listener as `source` so the Wraith
    status-effect rule applies.
- **Warden's Shriek.** Skill assignment applies the initial cooldown `SHRIEK_INITIAL_COOLDOWN_TICKS` (90 s). The shared
  `WitchSkillUseService` gate checks neither the exact role nor the skill block and never spends
  `WitchSkillDefinition.manaCost`. `shriek/WardensShriekService` therefore re-checks the exact role, a running game with
  a living non-spectator caster, and SparkTraits `isRoleSkillBlocked` (any failure refuses with
  `message.sparkwitch.skill.unavailable`), then spends `SHRIEK_MANA_COST` (75) itself; too little mana refuses with
  `message.sparkwitch.skill.not_enough_mana` and costs nothing, like the Death Ray. A paid cast hits every player in the
  caster's world within `SHRIEK_RADIUS` (8 blocks, feet-to-feet 3D distance, inclusive), except the caster, that passes
  `canAffect(caster, target, sparkwitch:abyss_listener_shriek)` and is not an ally; no line of sight is needed. Each
  target gets Slowness II for 5 s, −0.40 sanity, and forced cooldowns of at least 5 s. The cast syncs world event
  `SCULK_SHRIEKS` (3007) at the caster's block, plays a low `ENTITY_WARDEN_ROAR`, records
  `GameRecordManager.recordSkillUse(caster, sparkwitch:wardens_shriek, null, {targets})`, and always returns
  `WitchSkillUseResult.success(SHRIEK_COOLDOWN_TICKS)` (60 s), so the shared cooldown starts even with nobody in range.
  The caster is never told how many were hit.
- **Shriek Gun.** `sparkwitch:shriek_gun` is a plain `Item#use` hitscan on the Taser template, hidden in hand through
  `NoellesHiddenEquipment` and never in `wathe:guns`. The client only swings and recoils. Any holder may fire and the
  role is never read (owner rule 2026-10-04, `util/OffMatchUse`). In every mode a spectator, a gun on cooldown, or a
  SparkTraits `blocksWeaponAction` holder is refused. A living, non-spectator, non-Wraith participant of an ACTIVE
  round fires the match shot below; a dead participant or active Wraith is refused. Everyone else (no match,
  STARTING/STOPPING, a lobby player during an ACTIVE match) fires a presentation shot: the `SONIC_BOOM` beam cut only
  at blocks, plus the fire sound; there is no target pick, Seeker seam, push, effect, fall record, or replay line.
  Every fired shot then writes the vanilla `ItemCooldownManager.set(gun, 600)`, hit or miss (Fast Hands applies).
  - The beam is one COLLIDER `world.raycast` from the eye over 12 blocks (closed doors stay solid through
    `RaycastShapeScope`). Candidates are filtered before geometry by
    `AbyssSuppression.canAffect(shooter, c, sparkwitch:abyss_listener_gun)` plus Vendetta exact-pair isolation (allies
    stay eligible), tested against `PlayerHitboxHistory.hitVolumes(…, 0.2)`, and only the first player on the beam is
    hit. A nearer Seeker device absorbs the beam exactly like the Taser, and a miss also breaks the nearest breakable
    device in reach.
  - A hit sets an absolute velocity, the beam's horizontal unit × 2.0 with vertical `max(vy, 0.45)` for a non-ally or
    × 3.0 with `max(vy, 0.2)` for an ally, every axis capped at the ±3.9 sync limit (`velocityModified = true`); every
    push, ally or enemy, is recorded for fall credit. A non-ally with real sanity also loses 0.60 sanity and gets Slowness II
    and Blindness for 2 s and `AbyssSuppression.forceCooldowns(40)`; a non-ally without real sanity is only pushed; an
    ally (`AbyssSuppression.isAlly`) gets Speed III for 2 s.
  - The gun never kills, never sends `GunShootPayload`, and never uses `wathe:gun_shot`. The server draws
    `ParticleTypes.SONIC_BOOM` every block up to the cut point (block, hit player, or the absorbing device, found by
    `SeekerDeviceHits.shriekGunAbsorber` with the break's own ray and `SeekerDamageRules.mayBreak` filter), plays
    `ENTITY_WARDEN_SONIC_BOOM` at the shooter, and records
    `GameRecordManager.recordItemUse(shooter, sparkwitch:shriek_gun, target, {hit, ally})` (`ally` only on a hit).
  - The client crosshair chains one more `@ModifyExpressionValue` on Wathe `CrosshairRenderer.CROSSHAIR`: a ready gun
    in the local Abyss Listener's main hand lights Wathe's target crosshair for any alive, survival, visible player on
    the beam (current boxes), the same for allies and enemies.
- **Fall credit.** `FirePokerFallAttributionService` is one train-fall push ledger for every pushing weapon: each record
  stores the pusher, the pushing `Item`, and the window end (`FirePokerRules.FALL_ATTRIBUTION_WINDOW_TICKS`, 200
  ticks), and the latest push wins regardless of weapon. `GameWorldComponentMixin` still redirects only Wathe's
  fall-death `killPlayer`: a `wathe:fell_out_of_train` death inside the window is credited to the online pusher and
  runs inside `SparkTraitsKillerBridge.runWithNonFinalKillWeapon(recordedWeapon, kill)`, with Judge responsibility read
  first as before. The Fire Poker records itself as before; the Shriek Gun records every push, ally or enemy, with
  itself, so anyone it pushes off the train is the Abyss Listener's kill (kill money, mana, replay, attributed death
  for Wraith conversion; a dormant Fiend can die this way). Owner decision D12: a teammate knocked off the train is
  credited too, with its coins and mana.
- **Bound gun.** The gun is never granted from `RoleAssigned`. It is granted by the variant hook
  `afterPromotionCommitted` (after the promotion's shop rebuild) and, for a forced round-start Abyss Listener that has
  no gun yet, in the post-default `ON_FINISH_INITIALIZE` phase `sparkwitch:abyss_listener_finish_initialize` (the
  round is still STARTING, so it reads the role map and dead set directly). Both write `GUN_INITIAL_COOLDOWN_TICKS`
  (1200) through the vanilla `ItemCooldownManager.set`, so SparkTraits Fast Hands applies.
  - Placement is Wathe's `ShopEntry.insertStackInFreeSlot`. With a full hotbar the rightmost non-selected hotbar item
    moves into the slot a removed stray gun vacated (Time Stealer rule; a gun parked in SparkFactionAPI 0.1.5.13+'s
    visible row 27-35 is a stray), else an empty main slot, else an empty offhand, preferring the shown second row
    over hidden storage; with no room at all nothing is placed and the next sweep retries.
  - The holder entitlement (`mayHold`) is playing, alive, and exactly the Abyss Listener. A staggered 20-tick sweep
    reconciles only match participants (`OffMatchUse.isMatchParticipant`: a role in a running ACTIVE/STOPPING match).
    It keeps exactly one gun for an entitled holder (the first hotbar copy, else a gun mid-move on the cursor, else a
    fresh hotbar grant that leaves the per-Item cooldown untouched), removes duplicates and strays (hidden slots,
    offhand, crafting grid, containers), and strips every other participant (role change, Wraith transition, death).
    Everyone else is a free holder whose copy the sweep never strips, grants, or deduplicates. `KillPlayer.AFTER`
    removes the gun unless `WitchFactorTraitsBridge.isDeathIntercepted`; `ResetPlayer` and `ON_FINISH_FINALIZE` remove
    it. There is no creative exemption.
  - The binding matrix (`AbyssListenerInventoryRules`) is copied from, never shared with, the Time Stealer rules. Its
    five guards in `mixin/abysslistener/` are HEAD, cancellable, and delegation-only:
    `PlayerEntityAbyssListenerGunMixin` (`dropItem(ItemStack,ZZ)`), `ServerPlayerEntityAbyssListenerGunDropMixin`
    (`dropSelectedItem(Z)Z`), `ScreenHandlerAbyssListenerGunMixin` (`internalOnSlotClick`),
    `GameFunctionsAbyssListenerGunDropMixin` (`shouldDropOnDeath` → false), and
    `DecoratedPotBlockAbyssListenerGunMixin` (`onUseWithItem` → `SKIP_DEFAULT_BLOCK_INTERACTION`, so a pot never
    takes the gun and the gun still fires). A `UseEntityCallback` veto in `AbyssListenerLoadout` refuses item frames,
    armor stands and allays, which would otherwise take the gun and let the sweep mint another. These guards hold for
    every holder. A drop path that removed the stack before `dropItem` (cursor drop on close, full-inventory offer)
    loses it; for a participant the next sweep restores the Abyss Listener's gun with its cooldown intact. A living free
    holder gets a closing screen's cursor gun straight back (`AbyssListenerLoadout.keepRefusedDrop`, placed like a sweep
    grant; with every slot full the displaced hotbar item drops instead), and no other refused drop is handed back, so
    `/give`'s pickup-animation copy never duplicates the gun.
- **Deep Dark Zone terrain.** The zone is a client-only overlay: the server world is never written.
  - A Deep Dark Spore Flask (item and thrown entity) is thrown only in an ACTIVE round by a living, non-creative,
    non-Wraith participant who passes `SparkTraitsKillerBridge.blocksWeaponAction`; it has no cooldown, and any holder
    may throw it. On the server it breaks on a block or on a living, survival, non-Wraith player other than its
    thrower; every non-player entity (e.g. Wathe `PlayerBodyEntity` corpses) is transparent. It opens a zone at the
    cell in front of the struck face (a player hit uses the hit-point cell). A landing that converts no block plays
    only the shatter cue and opens no zone (no heartbeat, no standing check).
  - A Riftwalker Rift Gate is the one non-player entity `canHit` accepts (Riftwalker D18,
    `RiftGateProjectileService.isProjectileTarget`): the gate's deflection moves the flask to another gate or reflects
    it, and nothing lands at the gate.
  - One snapshot at landing flood-fills 6-neighbour steps through cells whose entity-less collision shape is empty (open
    doors pass; closed doors and walls stop it) up to Euclidean `ZONE_RADIUS` from the landing cell, and every
    neighbouring block that passes `DeepDarkZoneEligibility` converts: inside the map `playArea`, outside the reset
    template source box, an opaque full cube with a full collision cube, no block entity, MODEL render, luminance 0, no
    redstone power, no properties or only `axis`, not a `FallingBlock`, vanilla slipperiness, velocity, and jump
    multipliers, namespace `minecraft` or `wathe` (never ban `wathe:`: the Harpy floors are Wathe blocks), not in the
    datapack tag `sparkwitch:sculk_conversion_immune` (seeded with `minecraft:magma_block`), and not already deep-dark
    palette (`DeepDarkZoneEligibility.isDeepDarkPalette`: sculk or any block whose id path contains `deepslate`, so
    every deepslate ore, infested and reinforced deepslate included; such blocks are never zone cells). Floors look like
    sculk; other faces hash their position (60 % sculk, the rest deepslate tiles, bricks, or cobbled).
  - Every block converts on the landing tick (owner D14, 2026-10-04: no spread phase; the same tick's
    `END_WORLD_TICK` advance shows the whole zone at once, with one charge burst sampled from centre to rim and one
    spread sound; the first heartbeat follows one interval later) and restores at
    `ZONE_HOLD_TICKS + round(ZONE_RESTORE_TICKS × (1 − d / R))`, so outer blocks restore first (distance clamped to the
    radius; `ZONE_RADIUS` is 10 since D14, was 8).
    `DeepDarkZoneService.isConverted(ServerWorld, BlockPos)` (convert ≤ now < restore in any live zone),
    `hasActiveZones(ServerWorld)`, and `ownersAt(ServerWorld, BlockPos)` read only the in-memory per-world registry.
    Overlapping zones keep one window per zone on a cell whose landing snapshot matches (a union, so the cell restores
    at its last owner's end); a cell whose snapshot differs is replaced, and its stale fake is resent as real.
  - Fake and real states reach only the chunk's trackers as vanilla `ChunkDeltaUpdateS2CPacket`s grouped per section.
    Every 20 ticks a heal sweep resends live fakes, and a cell whose real block no longer equals its landing snapshot is
    dropped (compare-and-swap, which stops its look and effect). Nothing is persisted (no NBT, `shouldSave() = false`),
    so a crash needs no cleanup.
  - The zone restores instantly (real states resent, registry dropped) at `ON_FINISH_INITIALIZE` (stale),
    `ON_WIN_DETERMINED`, `ON_FINISH_FINALIZE` (which also discards flasks in flight), `SERVER_STOPPING`, and on any
    tick whose round is no longer ACTIVE (covers `/stop`); every such restore also clears the exposure of that world's
    players in the same tick. `ServerWorldEvents.UNLOAD` only forgets the registry.
  - Known cosmetic limits: other players' footsteps use the real block's sound (server-played), and a right-click on a
    converted block shows the real block to that player for up to one heal interval.
- **Standing and exposure.** Standing is checked every `ZONE_CHECK_INTERVAL_TICKS` (5) ticks, only in worlds where
  `DeepDarkZoneService.hasActiveZones`. A player stands on the zone when `AbyssSuppression.isParticipantTarget`,
  `isOnGround()`, and `DeepDarkZoneService.isConverted(world, getSteppingPos())` (the block vanilla uses for the step
  sound). The acting owner is the first thrower from `ownersAt` that is online and still a living participant;
  otherwise there is no actor, so a dead or departed thrower neither stops the zone nor lends it a SparkFactionAPI veto
  or a Wraith-isolated effect source. An ally (`AbyssSuppression.isAlly`) gets Speed III; anyone else that passes
  `AbyssSuppression.canAffect(actor, player, sparkwitch:abyss_listener_zone)` gets Slowness III and
  `AbyssZoneExposureComponent.expose(ZONE_EXPOSURE_TICKS)`. Both effects last `ZONE_EFFECT_REFRESH_TICKS` (2 s) and are
  re-applied only when missing, weaker, or at most half remaining, so they never lapse while standing and end at most
  2 s after stepping off. The standing check never changes blocks, mood, cooldowns, or Wathe's task map.
  - `sparkwitch:abyss_zone_exposure` (player, `NEVER_COPY`, appended right after `sparkwitch:accomplice_variant_round`
    in `custom.cardinal-components`, never
    persisted) holds the remaining exposure ticks. It syncs to its owner only, and only when exposure starts (0 to
    positive) or ends (back to 0); the client prediction stops at one tick, so only the server's zero sync ends it on
    the client. Exposure is cleared at once (zero synced to the owner) on `KillPlayer.AFTER`, `ResetPlayer`,
    `ON_FINISH_FINALIZE`, every instant zone restore (including `ON_WIN_DETERMINED`), and a committed Rift Gate entry,
    so the ×15 drain and the pseudo task stop with the blocks or at the gate; otherwise it runs out within 10 ticks of
    leaving. On entry, `RiftSessionService.begin` calls the public, null-safe `DeepDarkZoneStandingService.clearExposure`
    right after the SPECTATOR switch, never on a refusal or rollback (owner 2026-10-04). `isParticipantTarget` rejects
    spectators, so an occupant is never re-exposed, slowed or sped inside. Zone effects already applied run out (≤ 2 s).
- **Exposed drain.** While exposed (owner-synced, so both sides agree),
  `mixin/abysslistener/PlayerMoodComponentAbyssZoneDrainMixin` turns Wathe's per-tick drain
  `if (!tasks.isEmpty()) setMood(mood - tasks.size() * MOOD_DRAIN)` into `(real tasks + 1) × MOOD_DRAIN × 15`
  (operands in `DeepDarkZoneStandingDrain`), also with zero real tasks. It has three `@ModifyExpressionValue` handlers
  on `serverTick` and `clientTick`, each `require = 2`, `allow = 2`, `remap = false`, never `@Redirect`: the ordinal-0
  `Map.isEmpty()` gate, the ordinal-0 `Map.size()` drain operand (`serverTick`'s second `size()` is the next-task timer
  and stays untouched), and the `MOOD_DRAIN` read. The ×15 multiplies with the Bell Ringer Echo ×1.5 on the same
  `MOOD_DRAIN` read (the Bell Ringer mixin is unchanged). No `setMood` call is added, so the SparkTraits and
  SparkStrength `setMood` argument adjusters (SparkStrength pins ordinal 0) still apply. Wathe's REAL gate and
  `setMood` pinning still exempt non-REAL players, and the Wraith HEAD cancel still skips the loop.
- **Pseudo task line.** The exposure task (`task.sparkwitch.abyss_zone_exposure`, 「快离开这里！！！」) is display-only:
  while the local player's synced exposure is set on a confirmed SparkWitch server,
  `client/mixin/abysslistener/MoodRendererAbyssZoneExposureMixin` draws it as the last row of Wathe's top-left task
  list, blinking between `PSEUDO_TASK_COLOR` and `PSEUDO_TASK_DIM_COLOR` every 10 ticks, and re-targets Wathe's public
  `moodOffset` / `moodTextWidth` so the mood bar sits under it (also with zero real tasks; its `renderers.isEmpty()` bar
  gate counts the line). It is never in Wathe's task map, so it cannot be completed, fires no `TaskComplete`, pays
  nothing, and never affects the Bell Ringer task hold. It is pinned to Wathe 1.5.6 `renderHud`: one `Map.isEmpty()`
  (the bar gate) and two `PUTSTATIC moodTextWidth` (one per branch, after the psycho return and every row draw, before
  the icon and bar). Its fade is static client state: `AbyssListenerClient` settles it every `HudRenderCallback`
  frame, dropping it when Wathe's layout skipped the line since the previous frame (death, round over, psycho, train
  HUD off) while the local player is not exposed, and resets it on every `ClientPlayConnectionEvents` disconnect and
  join, so a stale fade never flashes the line in a new round or on another server.
- **Replay.** `AbyssListenerReplayFormatters`, registered once from `AbyssListenerFeatureService.register()`, formats
  the Shriek Gun record as a hit, an ally launch, or a miss (`replay.item_use.sparkwitch.shriek_gun.hit` / `.ally` /
  `.miss`; the target is named only on a hit) and the flask record with the thrower only
  (`replay.item_use.sparkwitch.deep_dark_spore_flask`). The shriek uses Wathe's default skill line
  `replay.skill.sparkwitch.wardens_shriek`.
- **Item art.** The gun is not a Wathe gun, so its arm keeps the vanilla item pose, and `models/item/shriek_gun.json`
  copies the Taser's whole `display` block, which was solved for that pose. The block holds only while the sprite
  keeps the Taser's geometry: the muzzle points up-left with the barrel at θ = 135°, and grip rows 9–14 cover exactly
  the Taser's grip texels. A redraw with another barrel angle or grip must re-solve the third-person rotation
  (rz = 112 − θ, ry = ±90, mirrored left-hand entries) and move the grip texel to the hand at arm-local
  (−1, 8.5, −0.5). `models/item/deep_dark_spore_flask.json` is a plain `item/generated` icon, which the thrown flask
  also draws through `FlyingItemEntityRenderer`. Both 16×16 placeholder sprites use the role colour `0x0B5E78` and the
  sculk cyan `0x29DFEB`; their generator script `docs/plans/accomplice-sculk/art/L4-item-textures.py` is local and
  ignored.

Potion Gunner state never enters the shared `sparkwitch:player` schema, and the role has no component. It joins the
special-accomplice pool from `PotionGunnerFeatureService`, and its post-promotion hook grants the launcher
(`PotionGunnerLoadoutService.ensureLauncher`, idempotent). It has no witch skill and declares no panel skills (`ownSkillIds()` stays empty), so it renders nothing in the
`gui.sparkwitch.skills` panel; its kit is explained by the launcher and shell tooltips.
- **Loaded shell.** The single loaded shell is the stable CUSTOM_DATA key `LoadedShell` on the launcher
  (`PotionLauncherLoad`). Loading is inventory-only: `PotionLauncherItem.onClicked` takes one shell from the cursor
  on a right-click, refuses a second shell, and unloads onto an empty cursor. Wathe's inventory exposes only the
  hotbar, so both items must sit there. Any free holder or living participant may load, whatever the role; a dead
  participant may not (`PotionGunnerLoadoutService.mayLoad`, synced state on both sides).
- **Bound items.** The launcher and shells follow the Time Stealer bound-item rules (`PotionGunnerInventoryRules`
  plus the five `mixin/potiongunner/` HEAD injects, with no creative exemption). They are never dropped, never an
  item entity, never in a container or the offhand, never handed to a world target, and never a death drop: a
  `UseEntityCallback` veto in `PotionGunnerLifecycle` refuses item frames, armor stands and allays, and
  `DecoratedPotBlockPotionGunnerItemMixin` makes a decorated pot answer `SKIP_DEFAULT_BLOCK_INTERACTION`, so the
  sweep never mints a second launcher. In a running match only a living, playing, exact Potion Gunner holds them;
  every other participant is stripped on role change, terminal death (not a SparkTraits-intercepted one), reset,
  finalize, and by a staggered 20-tick sweep, which also re-grants a living gunner exactly one launcher. Both items
  stay visible in hand; the launcher is outside `wathe:guns`.
  - Free holders (owner rule 2026-10-04): the sweep binds only match participants
    (`OffMatchUse.isMatchParticipant`), so it never strips, grants, deduplicates or surfaces anyone else's copies,
    and a living free holder's refused drop goes back into the inventory like the gunner's. The role-change, reset
    and finalize strips still apply to everyone (Wathe's finalize clears every inventory anyway).
  - A holder's shell is never deleted. A removed duplicate launcher's shell loads the kept launcher or returns
    hotbar-first (the shown second row with SparkFactionAPI 0.1.5.13+, then a hidden slot, else the empty cursor, when
    the hotbar is full); a copy whose shell has nowhere to go stays put. A re-inserted drop keeps any remainder in its
    original stack. With a full hotbar a new launcher, or a kept one in hidden storage, the offhand or armor, goes to
    the shown second row before hidden slots; a launcher in that row still moves into a free hotbar slot.
  - The sweep also moves shells from hidden main slots into shown room (same-type stacks first, then empty slots) and
    never displaces another item. Hidden means 9-35, or 9-26 when SparkFactionAPI 0.1.5.13+ shows 27-35: shells the
    player parks in that row stay there, and surfaced shells fill that row before an empty hotbar slot.
- **Scope and fire.** Holding use scopes, client-side only: zoom ×0.25, mouse look ×0.25, a hidden hand, and the
  reticle with range ticks drawn by a priority-1100 `InGameHud#renderCrosshair` wrapper. Left-click sends
  `sparkwitch:fire_potion_launcher` (yaw and pitch at the press, tolerant codec), one shot per fresh press. Only an
  attack press edge drained in `MinecraftClient#handleInputEvents` while the launcher is in the main hand fires,
  scoped or not; `doAttack` and held-attack block breaking are only swallowed, and keyboard auto-repeat is ignored
  until the key is physically released. So a key held through a slot switch or past the end of a stun, Seeker or
  Kidnapper key lock never fires (`client/potiongunner/PotionFireInput`, `PotionFireLatch`). The client's aim is
  trusted for direction only (Death Ray precedent); there is no server aim cone.
  - Use never checks the role (owner rule 2026-10-04, `util/OffMatchUse`). The server re-checks, in order: the
    shot's mode (a dead participant of an `ACTIVE` round is refused), launcher in the main hand, not a spectator, not
    stunned, no Seeker session, no SparkTraits weapon block, the 20-tick launcher cooldown, and a loaded shell.
  - A living participant of a round that is exactly `ACTIVE` fires a match shot, whatever its role (the sweep strips
    a non-gunner's launcher). Anyone else (no match, `STARTING`, `STOPPING` once the winner is decided, or a lobby
    player during an `ACTIVE` match) fires a presentation shot: it consumes the shell, cools down and sounds like
    any shot, but its backblast and burst play only sound and particles, it breaks no Seeker device, and it records
    no replay line.
  - The payload is also on the stun and Seeker-session deny lists.
- **Flight.** The shell is a role-owned `ThrownItemEntity`, never Wathe's grenade, and it is never saved. While the
  path length from its synced launch point at the start of a tick is below 50 blocks, it moves straight at
  2.5 blocks/tick with no drag or gravity (`PotionShellFlight.isFlatTick`, snapped to the 2.5-block step).
  - After that, vanilla thrown physics apply.
  - The scope ticks use the same predicate (`PotionBallistics`).
  - The shell bursts on the first block, closed door, or player its blast could catch, on a Seeker device in its
    path, or after 100 ticks (99 moves). It passes through Wathe corpses (`PlayerBodyEntity`), spectators, creative and
    Wathe-dead players (Wraiths) on both sides, and SparkTraits Last Escape players on the server only.
  - Players are lag-compensated on the server, because the gunner sees them a view delay late. Right after the Seeker
    sweep, each tick's path is also tested against every player's view-delayed volumes
    (`PlayerHitboxHistory.projectileHitVolumes`, 0.3 margin like vanilla).
    - Those volumes stop at the first jump (teleport, gate exit) and at the first spectator, creative or Last Escape
      sample (Rift Gate occupants, swallowed or dead players).
    - Entry follows `PotionShellFlight.laggedEntrySquared`. A path that starts inside the margin still hits on entering
      the real box, and a start inside the real box counts once the shell has moved, never at the muzzle. Vanilla's
      ray misses both.
    - The gunner is never rewound. Only players that the shell's `canHit` and SparkFactionAPI's
      `sparkfactionapi:projectile` action accept count, as for vanilla.
    - The hit wins only when it is strictly nearer than vanilla's own collision
      (`PotionShellFlight.lagCompensatedHitFirst`), so blocks, doors and Rift Gates still shield a player.
    - It bursts on the player's current box (`PotionBlastGeometry.entityImpact`) when the blast's line of sight reaches
      it from the rewound entry point, else at that entry point.
  - A Riftwalker Rift Gate never bursts it (Riftwalker D18): the gate's deflection moves it to another gate or reflects
    it. On a flat tick the shell keeps the gate's exit velocity (`onDeflected`) and re-bases its launch point behind the
    new position (`PotionShellFlight.flatPathAfterTick`), so the path length carries over the jump: still 20 flat ticks
    in total, and the lifetime is unchanged. A Seeker device in the same tick's path still bursts it first.
  - Once the round leaves `ACTIVE`, a match shell still in flight is discarded without exploding, and its
    detonation also requires `ACTIVE`, so no kill, gold or bounty lands after the result. A presentation shell flies
    the same, ignores the round status and skips the Seeker sweep. Finalize discards any shell left.
- **Blast.** It uses the grenade presentation; a presentation shell stops there (no Seeker device, target, effect,
  Judge attribution or reward). The area is an N×N×N cube around the impact: feet `x`/`z` within N/2,
  body overlapping vertically, plus line of sight. It does not catch spectators, Wathe-dead players, or SparkTraits
  Last Escape players. Falloff comes in rings by horizontal Chebyshev distance (`PotionBlastRings`: 5 → 100/67/33%,
  7 → 100/75/50/25%, 3 → 100/50%).
  - GW-DK, GW-AC and GW-MR skip the witch faction (effective faction, unknown fails closed) and the gunner.
  - TR keeps everyone, the gunner and witch allies included.
  - While the gunner is online, every non-self target also needs the gunner's `canAffectPlayer`, Vendetta isolation
    and exact-pair approval; an offline gunner vetoes nobody, and the faction filter still applies.
  - Effects run inside `JudgeKillAttribution.runWith` for the gunner.
  - +15 gold per caught player who is neither an ally nor the gunner, paid only to an online, living, non-spectator
    gunner who is still exactly the Potion Gunner after the effect. A gunner inside a Rift Gate counts as a
    non-spectator here (an alive spectator, Riftwalker D3; owner 2026-10-04, `PotionBlastRewards.notSpectating`).
- **Effects.**
  - GW-DK: Blindness + Slowness II for up to 7 s.
  - GW-MR: up to 150 coins taken and destroyed, never below 0.
  - GW-AC: every `ForcedCooldowns.slots` entry with a known nominal is extended by `ceil(nominal × 20% × falloff)`.
  - TR: an ordinary, non-forced `killPlayer` with `sparkwitch:potion_shell` (the gunner's own death has no killer and
    comes last). A survivor gets Blindness, Slowness II and 3 s of harmless burning. Prophecy group: Explosion.
  - The harmless burn is a global `ServerLivingEntityEvents.ALLOW_DAMAGE` listener that vetoes fire damage only
    while a player holds an owned burn window (`PotionShellBurn`, server-only, never saved).
  - Kill bounties follow the normal faction rules; an ally kill still pays the SparkFactionAPI direct-kill reward
    (owner decision).
- **Backblast.** Every launched match shot also makes one ordinary, non-forced kill attempt with
  `sparkwitch:potion_backblast` on the nearest player in a 4-block lane straight behind the gunner; a presentation
  shot vents only the flame and sound. The lane follows the shot's yaw only: it runs
  horizontally from the eye whatever the pitch (half-width 0.5, clipped at the first block or door, line of sight),
  and only a player whose box centre lies behind the gunner counts. Any faction is hit, never the gunner, and never a
  vetoed or Last Escape player. The kill runs inside `JudgeKillAttribution.runWith` for the gunner. Prophecy group:
  Explosion, like the TR shell.
  - It is nearest-wins against Seeker devices with one measure: the player's distance is taken on the real box, and
    only a device strictly nearer absorbs it; a tie goes to the player (`SeekerDeviceHits.onPotionBackblast`).
  - It has no fallback effects and no reward.

The Riftwalker (`sparkwitch:riftwalker`, 隙行者) is a special accomplice whose Rift Gates (`sparkwitch:rift_gate`) let
witches hide in a gate and hop between gates (Dn/Cn: owner decisions, 2026-10-02). Numbers live in `RiftwalkerRules`;
`RiftGateUser`/`RiftGateUsers` classify gate users by RAW role (never the Black Raven acting role): Riftwalker, then
witch faction (effective `sparkwitch:witch`), Murderous Witch, Apprentice Witch. The server decides every outcome; the
client renders and sends requests. Its `gui.sparkwitch.skills` panel shows only `sparkwitch:witches_sabbath`
(`ownSkillIds()`).
- **Registration and shop.** Pool entry after `accomplice` in `SparkWitchRoleRegistry` (behind the Abyss Listener and
  the Potion Gunner, before the Wind Spirit; the assassin-guess order is the same), never drawn; exact-role
  gates `isRegisteredSparkWitchRole`, `WitchManaRules.isManaRole` and `usesGrandWitchManaEconomy` (the Grand Witch
  economy, starting at 0, D7). `RiftwalkerShopService` appends `sparkwitch_rift_gate` (50 mana, charged in `onBuy`;
  label in `WitchShopClientTexts`) to the plain Accomplice entries.
- **State and packets.** `sparkwitch:rift_session` (player, `NEVER_COPY`, owner-only sync, never persisted) and
  `sparkwitch:rift_gates` (world, server-only). C2S `rift_hop`, `rift_exit`, `rift_gate_close`,
  `rift_gate_console_request` and S2C `rift_gate_console` are registered by `net/RiftwalkerNetworking`; the Control
  Expert stun and Seeker session guards also drop `rift_hop` and `rift_gate_close`, never `rift_exit`.
- **Gates** (`gate/`). `RiftGatePlacementService.tryPlace` (living RAW Riftwalker, ACTIVE round, not
  Kidnapper-controlled, C16) floor-snaps at the feet facing the yaw; the 1×2 standing cell in front
  (`RiftGatePlacementRules.frontCell`) must be block-free, so no gate faces a wall as a dead exit (C15); ≥ 3.0 from
  other gates, clear of Seeker devices and `RiftGateNeighbourRules`. Of the Wathe play area only its fall line
  counts, base ≥ `playArea.minY` (`RiftGatePlacementRules.aboveFallLine`; owner 2026-10-05: server play areas
  refused every Harpy Express spot), and there is no height cap (owner 2026-10-06: the old y 148 cull cap refused
  every 星穹列车 spot). A refusal is free; a placement starts a 1 s item cooldown (N-2). Bought gates merge into the first hotbar gate stack (F-5).
  `RiftGateEntity` is an unsaved, indestructible, facing-rotated 1×2×0.25 slab; gates are unlimited (D4).
  `RiftGateRegistry` is the only writer: per-round numbers never reused (C9), number order = hop ring, a level-31 chunk
  ticket per gate, `repair` respawns a lost entity, `close` ends in `RiftSessionService.onGateRemoved`.
  `RiftGateLifecycle` sweeps silently at round edges and has no death or role listener, so gates outlive their placer.
- **Session entry** (`session/RiftSessionService.tryEnter`, right-click, D2; order in `RiftSessionRules.entryDenial`).
  Non-users are refused silently; held players are refused (Taotie, Last Stand, Last Escape, Kidnapper, Control Expert
  stun, Seeker session, foreign camera, and per C10 a Hunter-trap root or SparkStrength capture stun, read by
  `RiftwalkerStatusProbes`, where an absent or changed SparkStrength reads as not stunned and never throws); then reach
  3.0, cooldown, and the fee last (Apprentice and Murderous Witches pay 100 mana per entry, hops free, C1). Stay
  30/20/15/10 s (Riftwalker/faction/Murderous/Apprentice, D5b); re-entry cooldown 30 s Riftwalker, 45 s others, after
  any exit but round end (D14), a session server tick untouched by `ForcedCooldowns` and auras (C3).
- **Session body.** An occupant is an ALIVE SPECTATOR held at the gate anchor (D3). Entry syncs `inside` before
  `changeGameMode(SPECTATOR)`; every exit restores the mode before syncing `inside=false`. Only a living release
  restores the recorded ADVENTURE/SURVIVAL mode (`RiftSessionRules.mayRestoreMode`); DIED, INTERCEPTED, DISCONNECTED and
  ROUND_END only clear state. The tick snaps drift back, ends as BODY_MOVED only after a foreign teleport beyond √2, and
  force-exits at the CURRENT gate on stay expiry (C2) or gate close (C3). A "moved too quickly" reset issued during the
  occupant's own move packet is snapped back to the anchor, never BODY_MOVED (B-1, `RiftSessionNetworkHandlerMixin`).
  Exit cells must be reachable from the gate by a block-free sweep of the standing body (B-2,
  `RiftExitSearch.sweptBody`; the pre-entry position is exempt); of the play area they keep only Wathe's fall line,
  feet ≥ `playArea.minY` (`RiftExitSafety.insideBounds`, matching the placement rule above). Hops wrap (`RiftHopRing`, 10-tick throttle, D11).
- **Session guards.** `mixin/riftwalker/RiftSessionPayloadGuardMixin` drops `RiftSessionRules.BLOCKED_WHILE_INSIDE`;
  `RiftSessionGuards` fails use/attack callbacks and the shop; `RiftSessionNetworkHandlerMixin` and
  `RiftSessionPlayerMixin` close spectator teleport and possession; `voice/SparkWitchVoiceChatPlugin` mutes occupants.
  `session/RiftSessionAffectPolicy` (SFA `PlayerAffectPolicy`, fails closed) denies every action on an occupant except
  `RiftSessionRules.AFFECT_ALLOWED_ON_OCCUPANT`: `noellesroles:swapper` (the crush below), `wathe:poison` (D3), and the
  piercing terminal kills `sparkwitch:bell_toll` and `sparkwitch:time_stolen` (C13). `BellTollService.isTarget` admits
  occupants next to participants (C16); the kill drops the body at the gate and ends the session as DIED.
  Wathe's `isPlayerPlayingAndAlive` ignores the game mode, so a targeter that never asks SFA must skip spectators
  itself. Spectators are transparent (never a target, never a shield) to:
  - the Black Raven Feather Blade aim (`BlackRavenTargeting.findAimedPlayer`);
  - the shared aim `GrandWitchTargeting.findTarget`, used by Witch Factor and Emma;
  - its client hint mirror, `client/emma/EmmaClientTargeting`;
  - the Curser's 8-block confusion (`CurserFeatureService.use`; occupants alone read as "no target", no cooldown);
  - the Orthopedist aim and validator (`OrthopedistTargeting`) and its HUD hint (`client/hud/OrthopedistHudRenderer`);
  - the Guardian Angel aim and validator (`GuardianAngelTargeting`, `GuardianAngelRules.canTarget` takes a
    `targetSpectator` flag) and its HUD preview (`client/guardianangel/GuardianAngelTargetingPreview`).

  These use a spectator test, never SFA `canAffectPlayer`: the Curser and Guardian Angel casters are active Wraiths,
  which it denies outright. So an occupant is never marked, cursed, bone-set or newly shielded, and an
  occupied gate no longer fails an aim at the player behind it. The test covers every Wathe-alive spectator, so
  NoellesRoles Taotie-swallowed players and SparkTraits Last Stand / Depression holds are skipped the same way.
  `HolyFlashComponent` keeps a flash while `isInside`, so entering a gate never cleanses it
  (audit fixes, 2026-10-03). Deep Dark Zone exposure is the opposite: a committed entry clears it at once
  (`DeepDarkZoneStandingService.clearExposure`). A Potion Gunner inside a gate still earns its shell's hit reward.
  Both are owner picks from 2026-10-04.
- **Session client.** `client/riftwalker/session/RiftSessionClient` sends only `rift_hop`/`rift_exit` and never predicts
  entry or exit; `client/mixin/riftwalker/RiftSession*Mixin` pass only `RiftSessionInputRules.ALLOWED_KEYS` (sneak, A/D,
  player list, screenshot, fullscreen, voice chat, instinct), hide the hand and force a crosshair MISS; A/D, scroll or
  1/2 + use hop, a fresh Shift exits. `RiftGrayscaleFilter` (private `PostEffectProcessor`) re-composites outlines so
  instinct colours stay (D10); `RiftSessionHud` draws ←/→, `#gate · n/m` and the stay seconds (red from 5 s, C2).
  An occupant keeps its own living-role instinct outlines: `WitchFactionRules` / `MurderousWitchRules`
  `shouldUseCustomInstinctHighlight` key on Wathe-alive only (a null answer would reach SparkFactionAPI's
  role-revealing faction-colour fallback), Obscure/Fear still apply, and the Wraith reveal (`WraithViewerRules`) and
  the glimmering-Fisher outline exemption (`FisherGlimmerInstinctHooks`) belong to Wathe-dead spectators only. The
  factor fallback (`WitchFactorClientHooks`) still stays off for any spectator.
- **Melee.** A gate keeps `canHit()` only for the right-click entry and never shields a player behind it (client only;
  the server never re-raycasts melee). The client `RiftGateEntity.interact` returns PASS unless the local player could
  enter now (B-6, `RiftSessionService.claimsRightClick`), so a held item still fires.
  `client/mixin/riftwalker/RiftGateCrosshairMixin` ANDs the crosshair predicate in `findCrosshairTarget` so non-users
  (and foreign cameras) ignore gates, and `RiftGateAttackMixin` (WrapOperation on `doAttack`'s `attackEntity`) re-picks
  a gate user's left-click on a gate without gates and hits what is behind it, or sends nothing. Classification and
  rules: `client/riftwalker/gate/RiftGateCrosshairClient` / `RiftGateCrosshairRules`.
- **Projectiles** (`projectile/RiftGateProjectileService`, vanilla deflection seam). Any projectile, pearls included
  (D8, C5), moves to the front of a random other gate with rotated velocity, else reflects at full speed; at most 3
  passes. The NR throwing axe uses `mixin/riftwalker/RiftThrowingAxeMixin`, the SparkStrength M67 `RiftGateM67Sweep`
  (registry id only). Hitscan ignores gates; a custom player-only `canHit` must accept gates via `isProjectileTarget`.
  D18: the Abyss Listener flask does so (`DeepDarkSporeFlaskEntity.canHit`), and the Potion Gunner shell, which every
  non-player entity already stops, keeps the gate's exit velocity and its flat-range path length across a pass
  (`PotionShellEntity.onDeflected`).
  `mixin/riftwalker/RiftProjectileDeflectionMixin` keeps a gate deflection from flipping pickup to ALLOWED and from
  being remembered as `lastDeflectedEntity`; destinations must pass `RiftProjectileExitRules` (ticking exit/start, box
  clear of blocks, clear line), else another gate or reflect.
- **Witches' Sabbath** (`sabbath/WitchesSabbathService.use`; 150 mana, instant, 30 s cooldown after a
  successful cast, none at round start; owner 2026-10-05, replacing D6's "no cooldown"). A free,
  non-capture-stunned Riftwalker outside a gate pulls each living teammate whose effective faction is exactly
  `sparkwitch:witch` (C6) to a safe spot (`WitchesSabbathLandingPlan`, never on a live Hunter trap), skipping those
  inside a gate, swallowed, in Last Stand/Last Escape, Kidnapper-controlled, or SFA-vetoed
  (`sparkwitch:riftwalker_sabbath`). Control-Expert-stunned, Hunter-rooted and SparkStrength capture-stunned teammates
  are pulled and stay held at the landing spot (D19, superseding C10's Sabbath half and M-2): the stun and the root
  anchor no position, and `RiftwalkerStatusProbes.relockCaptureStun` calls SparkStrength's `stun(1)` (keeps the
  remaining ticks, re-locks at the current position) right after the teleport. A captured teammate whose lock cannot
  move (`isCaptureStunPinned`) is skipped. A pulled Grand Witch's ceremonial-sword dash is cancelled. No target or no
  space costs nothing, the mana is refunded when nobody moved, and a caster in a doorway gets its own refusal (N-5).
- **Presentation** (`client/riftwalker/gate/`). `RiftGateEntityRenderer` draws model B (`RiftGateModels`) full-bright
  for everyone, skipping the gate around an occupant's camera; `RiftGateClientEffects` adds sparse particles;
  `RiftGateInstinctHooks` (D9) outlines every gate through walls for living Grand Witch and accomplice-like viewers on
  instinct.
- **Tablet console** (D12, D16, C9). A living Riftwalker using `sparkstrength:tablet` (registry id only) from the hotbar
  gets `client/riftwalker/tablet/RiftGateConsoleOpener` (a Seeker-opener copy; 「魔女网络」 returns to SparkStrength).
  `tablet/RiftGateConsoleService` re-validates every request, lists fixed `#n`, distance, direction and occupant names
  (D16), and closes through `RiftGateRegistry.close(CONSOLE)` after two clicks (`RiftGateCloseConfirm`); no close-all.
- **Rift Gate Remover** (`sparkwitch:rift_gate_remover`, 传送门清除工具, owner 2026-10-05). An operator tool listed only
  in vanilla's Operator Utilities tab (`SparkWitchItemGroups.operatorOnlyItems`, kept out of the SparkWitch tab).
  `gate/RiftGateRemoverService` checks `SparkWitchPermissions.ITEM_RIFT_GATE_REMOVER` (op level 2 by default), picks
  the gate on the look ray within 24 blocks (stopped by block outlines), and closes it through
  `RiftGateRegistry.close(ADMIN)`: occupants are released at the gate with no re-entry cooldown, also when a blocked
  first release is retried by the tick (`RiftSessionComponent.pendingCloseReason`, server-only). Works in any round
  state, for any role. `RiftGateEntity.interact` passes for a hand holding it, and `RiftGateCrosshairClient` keeps gates
  targetable while it is ready in the clicking hand (`RiftGateRemoverItem.isReadyIn`: main hand, or off-hand behind an
  empty main hand), so the click reaches the tool, not a gate entry or a block behind the gate.
- **Swapper crush** (D13, C4, C7, C8). `mixin/riftwalker/RiftSwapperCrushMixin` (HEAD on NR's Swapper handler,
  `require = 1`, priority 1100 so SFA and SparkTraits guards decide first) calls `swapper/RiftSwapperCrushService`: if
  either target is inside a gate the swap is cancelled and the Swapper is killed on a safe cell in front of the gate
  (`RiftSwapperBodyCell`, one block out first, in sight of the gate by a COLLIDER ray, M-4); any other spectator
  target, dead or alive, cancels silently (M-3).
  `client/mixin/riftwalker/RiftSwapperWidgetMixin` (C7, pinned in `verifyClientMixinSelectors`) lets the Swapper pick an
  untracked, Wathe-alive spectator. Third owner-approved exception: `sparkwitch:portal_crushed` is a forced, terminal,
  killer-less environmental kill, registered as SparkTraits-terminal on `SERVER_STARTING` like `bell_toll` (the Swapper
  may still become a Wraith, D17). Its Prophecy group is Supernatural, and a correct guess reveals no killer. Its
  victim is always the Swapper, so the Saint and dormant-Fiend guards need no
  opt-out; a vetoed kill (older SparkTraits) moves the Swapper back with NR's 60 s cooldown.
- **Cross-mod seams.** SparkTraits only through existing bridges, failing closed: `isKillerInteractionBlocked`,
  `isRoleSkillBlocked`, `isLastEscapeActive`, `registerTerminalDeathReason` (`SparkTraitsKillerBridge`),
  `isLastStandPending` (`SparkTraitsSeekerBridge`), `isLastStandDeathIntercepted` (`WitchFactorTraitsBridge`).
  SparkStrength only by id (tablet, M67, `sparkstrength:engineer_stunned`). SparkTraits
  `feat/accomplice-riftwalker-support` adds the Riftwalker to its five hard-coded accomplice id sets.
- **Gaps and tests.** Open owner calls:
  - C11: the NR Pathogen can still infect an occupant within 3 blocks (non-lethal).
  - Found by the 2026-10-04 audit:
    - A Potion Gunner inside a gate breaks no Seeker devices with a landing or in-flight shell
      (`SeekerDamageRules.mayBreak` requires a non-spectator participant), while an offline gunner's landing shell
      breaks them.
    - A Guardian Angel shield cast before entry lasts up to 10 s inside the gate. It blocks the D3 `wathe:poison` death,
      and its block sound plays at the gate.
    - Witch Maiden Focused Footsteps (`FocusedFootstepsSkillService`) takes a client-sent target with no spectator or
      SFA check, so it reaches an occupant: hidden mood drain, forced sprint and forward input for up to 30 s.
    - Black Raven mark settlement (`BlackRavenMarkRuntime`) kills a marked occupant when the marker is offline (no
      killer, so the SFA guard is skipped) and drops the body at the gate. With the marker online, SFA refuses the
      kill and the mark is lost.
    - The Apprentice Healing aura (`HealingAbility.applyPulse`) still raises an innocent occupant's mood, and an
      Apprentice inside a gate keeps pulsing from the anchor.
  - Left unchanged on 2026-10-04, when the owner picked only the other audit items for fixing:
    - Grand Witch Fear pulses, Blindness and Heaviness (`GrandWitchSpellService`) still reach Apprentice and Murderous
      Witch occupants.
    - A pending Emma backlash, a forced kill with no killer, still kills an occupant.

  Local tests: `roles/witch/riftwalker/` and `client/riftwalker/` under `src/test/java/dev/caecorthus/sparkwitch/`. The
  audit guards above are pinned by `session/RiftOccupantTargeterGuardsContractTest` and
  `session/RiftOccupantWraithAimGuardsContractTest`, the in-gate Potion Gunner reward by
  `roles/witch/potiongunner/shell/PotionGunnerRiftRewardContractTest`, and the exposure clear by
  `roles/witch/abysslistener/zone/AbyssZoneExposureRiftEntryContractTest`.

Active Wraiths do not absorb name-tag raycasts they are hidden from.
`client/render/WraithNameTagPassThrough` owns the presentation rule: a player
whose synced Wraith state is active is skipped when
`WraithViewerRules.shouldHideFromOrdinaryViewer` hides it, except for the
promoted Curser viewed by the witch faction. Wathe-dead spectators (not a living
Rift Gate occupant), killers viewing the promoted Saboteur, and the bound killer
viewing its Vendetta keep selecting it.
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
The Riftwalker may query only `isKillerInteractionBlocked`, `isRoleSkillBlocked`, `isLastEscapeActive` and
`registerTerminalDeathReason` through `SparkTraitsKillerBridge`, `isLastStandPending` through
`SparkTraitsSeekerBridge`, and the Last Stand interception read through `WitchFactorTraitsBridge` (details in its
section); an absent or older build means no block and no terminal registration for `sparkwitch:portal_crushed`.
The Ceremonial Sword and a credited Fire Poker fall may query only
`isNonFinalKillPending`, `getNonFinalKillCooldownTicks`, and
`runWithNonFinalKillWeapon` through `SparkTraitsKillerBridge` for non-final
(Depression fake death) kills; an absent or older build means no non-final
kill, the original cooldown, and the kill running directly exactly once. A credited Shriek Gun push fall takes the
same fall path (`runWithNonFinalKillWeapon` with the gun as the weapon) and the same fallbacks.
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
The Insider may query only `isInstinctHidden` through its own client `client/insider/InsiderSparkTraitsBridge`;
an absent, older or failing build means nothing hidden. SparkTraits
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
Saint Karma may query only `setExactItemCooldownRemaining` (its per-tick raise of every carried item) through
`SparkTraitsKillerBridge`; an absent, older or failing SparkTraits means a vanilla Karma write.
The Abyss Listener may query only `isLastStandPending` and `hasActiveTrait` (Conscience and Impostor) through
`compat/SparkTraitsAbyssListenerBridge`; `isLastEscapeActive`, `isRoleSkillBlocked` (the shriek), and
`blocksWeaponAction` (the gun and the flask throw, with its `isKillerInteractionBlocked` and
`getForcedMeleeCooldownTicks` reads) through the existing `SparkTraitsKillerBridge`; `isLastStandDeathIntercepted`
(gun removal at death) through the existing `WitchFactorTraitsBridge`; and the push-fall queries above. Its Wraith
check uses SparkWitch's own `WraithStateService.isActive`, which SparkTraits `isWraithActive` delegates to, so it never
queries `isWraithActive`; Fast Hands reaches the gun through the vanilla cooldown write, with no SparkWitch call. An
absent SparkTraits means not pending, no Last Escape, no skill or weapon block, both traits inactive (real sanity
follows the base role), and gun removal on every death. A present build whose facade lacks or fails a method falls
back per method: not pending (the spectator gate still excludes a pending Last Stand player), no Last Escape and no
block (as `SparkTraitsKillerBridge` defines), traits unknown (which `hasRealSanity` never counts as real sanity), or an
intercepted death (the `KillPlayer.AFTER` removal is skipped, and the 20-tick sweep strips the dead holder).

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
  and keeps the live set. A `RoleAssigned(black_raven)` for a Raven that is still disguised (a
  forced role mid-round) reverts to the Raven set first, so the kit re-grant leaves exactly one
  blade, ledger, and mask.
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

Wraiths cannot open or close doors or windows (owner decision 2026-10-04). A
restricted Wraith's right-click fails on every block except food platters, drink
trays and beds. A promoted Wraith's right-click fails only on passage blocks,
through `runtime/WraithPassageGuard` inside the single Wraith `UseBlockCallback`
(both sides). Passage blocks are:
- the door family above;
- Wathe `PrivacyBlock` train windows;
- vent hatches;
- any button with a door-family block in its 3x3x3 cube.

A click on a Wathe ornament is judged by the block behind it, through any chain of
ornaments. Sneaking with an item in either hand stays allowed, except with the
Wathe crowbar: vanilla then skips the block's own use, and only the held item's
`useOnBlock` runs. The Wathe lockpick is the other exception, because it is the
only Saboteur and Curser shop item: on a Wathe door it may still sneak-jam, or
unlock a closed locked door. A closed train door counts as locked; a small door
counts as locked when it has a key name. Its plain open and close stay blocked
(`WraithParticipationRules.mayUsePassageBlock`).

A projectile thrown by an active Wraith (the Wind Spirit's wind charge) triggers
no blocks:
- `mixin/WraithExplosionTriggerMixin` makes `Explosion#canTriggerBlocks` false,
  so its explosion toggles no doors, trapdoors, gates, buttons, levers, bells or
  candles.
- `mixin/WraithProjectileBlockHitMixin` cancels the block's `onProjectileHit`
  (bells, target blocks, decorated pots).

Knockback is unchanged.

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
