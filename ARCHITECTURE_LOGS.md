# SparkWitch Architecture Logs

This file records architecture reviews, approved boards, completed migrations,
and historical constraints. It supports `ARCHITECTURE.md`, but does not replace
the constitution.

Status terms:

- `Observed`: current repo shape or review finding.
- `Approved`: owner-approved work that may proceed within the listed scope.
- `Completed`: already implemented and should not be reopened without a new
  trigger.
- `Partially Completed`: approved scope landed, but a named remainder stays
  visible and requires a new trigger before further work.
- `Watch-Only`: known area to protect; do not split unless a concrete trigger
  appears.

## 2026-07-04 Initial Architecture Review

Status: Observed.

SparkWitch currently has a working but crowded internal structure:

- `api/` is the cleanest public Interface. It owns Witch skill definition,
  registry, selection context, use context, and use result.
- `impl/` has grown into a 45-file catch-all for Grand Witch, Accomplice,
  Apprentice Witch, Murderous Witch, Pig God, skill, mana, economy, shop, combat,
  win, and compatibility behavior.
- `component/WitchPlayerComponent.java` is over 1000 lines and owns CCA storage,
  sync, NBT, server tick, mana, cooldowns, Grand Witch windows, Apprentice
  windows, Pig God chase state, and Death Ray state.
- `SparkWitchRoles.java` mixes role catalog, SparkFactionAPI faction/capability
  registration, Wathe native role registration, and Assassin guess-order
  compatibility.
- Client code is mostly flat under `client/`, with HUD renderers, hooks, text
  helpers, bootstrap, and client networking in one package.
- Server and client mixins are mostly thin Adapters and should stay that way.

Current governance files before this review:

- Present: `AGENTS.md`.
- Missing: `ARCHITECTURE.md`.
- Missing: `ARCHITECTURE_LOGS.md`.
- Missing: `CONTEXT.md`.
- Missing: `docs/adr/`.

## 2026-07-04 First-Round Architecture Boards

Status: Completed.

Approval scope: after writing `ARCHITECTURE.md`, split the remaining four review
items with subagents, according to the constitution. Keep boards disjoint and do
not widen scope.

Completion scope:

- Board 1 moved root `impl/` gameplay Modules into domain packages while leaving
  `impl/SparkWitchEvents.java` as the watch-only event aggregator.
- Board 2 moved safe helper ownership out of `component/` while keeping
  `WitchPlayerComponent` as the CCA storage/sync/NBT Interface.
- Board 3 turned `SparkWitchRoles.java` into a compatibility Facade and moved
  role/faction registration plus Wathe role-list ordering into `registry/`.
- Board 4 layered client helper Modules into `client/hooks`, `client/hud`,
  `client/text`, and `client/net/version`.

Verification:

- `./gradlew --no-daemon --no-watch-fs --console=plain clean test` succeeded.
- `./gradlew --no-daemon --no-watch-fs --console=plain build` succeeded.
- `git diff --check` succeeded.

Deferred intentionally:

- Larger `WitchPlayerComponent` sync/NBT/tick extraction was not done in this
  round. Preserving packet field order, NBT keys, and timing semantics mattered
  more than forcing a deeper split.

## 2026-07-04 Second-Layer Witch Faction And Combat Split

Status: Completed.

Approval scope: refine the newly created `combat/` and Witch faction shape
without changing gameplay behavior, ids, policy ordering, event ordering, packet
ids, translation keys, or resource ids.

Completion scope:

- `combat/ceremonialsword/` now owns ceremonial sword combat and dash behavior.
- `combat/firepoker/` now owns Fire Poker combat, fall attribution, and strike
  rules.
- `combat/MightyForceCombatService.java` remains at combat root because it is not
  part of either item family.
- `witchfaction/` replaces the top-level Grand Witch package as the shared Grand
  Witch and Accomplice faction Module.
- `WitchFactionRules` owns shared faction rules.
- `WitchFactionFeatureService` remains the narrow registration and
  role-assignment Facade.
- `WitchFactionEconomyPolicy`, `WitchInstinctPolicy`, and
  `WitchFactionProtectionPolicy` own economy, instinct, and protection policy
  logic.
- `witchfaction/grandwitch/` owns Grand Witch-private shop, spells, fear,
  ceremonial sword state, and active skills.
- `witchfaction/accomplice/` owns Accomplice shop and role-private rules.

Verification:

- Focused combat and Witch faction tests succeeded during the split.
- `./gradlew --no-daemon --no-watch-fs --console=plain clean test` succeeded.
- `./gradlew --no-daemon --no-watch-fs --console=plain build` succeeded.
- `git diff --check` succeeded.

Guardrails:

- Keep Apprentice Witch, Murderous Witch, and Pig God outside `witchfaction/`
  unless a future owner-approved board explicitly changes faction membership.
- Keep role-private Grand Witch behavior under `witchfaction/grandwitch/`.
- Keep role-private Accomplice behavior under `witchfaction/accomplice/`.
- Keep item-family combat behavior grouped under the item package once more than
  one Module belongs to the same item.

### Board 1: `impl/` Domain Package Split

Allowed direction:

- Move root `impl/` Modules into domain packages such as `skill`, `mana`,
  `witchfaction`, `apprentice`, `murderouswitch`, `piggod`, `combat`, `shop`,
  `economy`, `win`, and `compat`.
- Preserve public behavior, registration ordering, event ordering, ids,
  translation keys, and tests.
- Prefer mechanical package moves before behavior changes.

Forbidden scope:

- Do not change role/faction semantics.
- Do not change event ordering or win precedence.
- Do not rename public ids, command literals, packet ids, resource ids, or
  translation keys.
- Do not add new gameplay behavior during the move.

### Board 2: `WitchPlayerComponent` Responsibility Split

Allowed direction:

- Keep `WitchPlayerComponent` as the CCA storage/sync/NBT Interface.
- Move role-specific tick behavior and state calculations behind owning domain
  Modules when safe.
- Preserve component id `sparkwitch:player`, sync packet field order, visibility
  rules, and NBT keys unless a migration is explicitly approved.

Forbidden scope:

- Do not change CCA component ids.
- Do not change save compatibility.
- Do not alter cooldown, active-window, mana, Pig God, Death Ray, or Apprentice
  timing semantics.

### Board 3: Role/Faction Registry Split

Allowed direction:

- Split role/faction declaration and compatibility ordering out of
  `SparkWitchRoles.java` into clearer registry Modules.
- Preserve the existing static role accessors as compatibility Facades unless a
  caller migration is explicitly approved.

Forbidden scope:

- Do not change role ids, faction ids, colors, appearance conditions, native
  Wathe faction placement, or SparkFactionAPI capability semantics.
- Do not move Apprentice Witch or Murderous Witch into the Witch faction.
- Do not remove Assassin guess-order compatibility.

### Board 4: Client Package Layering

Allowed direction:

- Move client HUD, hooks, text helpers, and client version networking into
  clearer client packages.
- Keep client mixins thin and keep mixin config class names accurate.

Forbidden scope:

- Do not let client behavior run without SparkWitch server confirmation.
- Do not change HUD layout, text, colors, sound triggers, input behavior, or
  packet behavior during package moves.

## Completed And Watch-Only Decisions

## 2026-07-05 Main-Root Architecture Rebase And Second-Layer Deepening

Status: Completed.

Approval scope: owner approved doing the reviewed architecture candidates while
following the architecture constitution and `improve-codebase-architecture`.

Completion scope:

- Added `ARCHITECTURE.md` and `ARCHITECTURE_LOGS.md` to the main root so the
  constitution is no longer stranded in a side worktree.
- Finished the domain package migration in the main root: old duplicate
  `impl/*` Implementation Modules and old `impl/*Test` files were removed,
  leaving only `impl/SparkWitchEvents.java` as the watch-only event registration
  aggregator.
- Preserved `SparkWitchRoles` as the compatibility Facade while routing role,
  faction, and Assassin guess-order work through `registry/`.
- Moved client hook, HUD, text, and client version networking Modules into
  layered client packages while preserving confirmed-server gates.
- Added component-local `WitchPlayerSyncCodec` and `WitchPlayerNbtCodec`
  Modules. `WitchPlayerComponent` remains the CCA storage/sync/NBT Interface;
  packet field order, visibility rules, component id, and NBT keys stay stable.
- Added skill-local `WitchSkillUseReadiness` and `WitchSkillCooldownPolicy`
  Modules. `WitchSkillUseService.use(...)` remains the caller-facing Interface;
  role checks, messages, mana spending, cooldown values, and active-window
  semantics stay unchanged.
- Tightened the pure `WitchWinConditions.shadowShowdownAction(...)` Interface by
  removing an unused native-killer count parameter without changing the win rule.
- Preserved the prior client-confirmation and dependency metadata hardening:
  connection state resets on login/play lifecycle edges, confirmation is version
  compatible, and jar verification checks dependency predicates.

Verification:

- Focused component/skill/win/connection tests succeeded.
- `./gradlew --no-daemon --no-watch-fs --console=plain test` succeeded.
- `./gradlew --no-daemon --no-watch-fs --console=plain build` succeeded.

Guardrails:

- Do not reintroduce old root `impl/*` duplicates; use the owning domain package.
- Keep `WitchPlayerComponent` as the CCA Adapter unless a future board explicitly
  approves sync or NBT migration.
- Keep Pig God chase runtime extraction as a future high-risk Board 2 follow-up;
  it touches teleport, psycho state, sound, speed effects, and cooldown deferral.

## 2026-07-05 Role Directory Grouping

Status: Completed.

Approval scope: owner requested a mechanical role-domain grouping without
changing role semantics or gameplay behavior.

Completion scope:

- Added `roles/` as the role-owned Module root.
- Moved Apprentice Witch and Pig God under `roles/civilian/`.
- Moved Murderous Witch under `roles/neutral/`.
- Renamed the Witch faction Module from `witchfaction/` to `roles/witch/`.
- Kept `roles/killer/` as an empty reserved bucket until a concrete killer-side
  role Module exists.

Guardrails:

- This is a package ownership move only. Do not change role ids, faction ids,
  native Wathe faction placement, SparkFactionAPI capability semantics, packet
  ids, translation keys, resources, event ordering, or win precedence.
- Apprentice Witch and Pig God remain native civilian-side roles.
- Murderous Witch remains a native neutral role with explicit SparkWitch bridges.
- Grand Witch and Accomplice remain the only current `roles/witch/` members.

## 2026-07-05 Murderous Witch Internal Grouping

Status: Completed.

Approval scope: owner requested a mechanical grouping of Murderous Witch's
compiled class families into matching source directories.

Completion scope:

- `MurderousWitchDeathRay/` owns `MurderousWitchDeathRayRules` and
  `MurderousWitchDeathRayService`.
- `MurderousWitchFeature/` owns `MurderousWitchFeatureService`; compiler
  generated `$1` switch-map classes now land in the same output package.
- `MurderousWitchRules/` owns `MurderousWitchRules`; compiler generated `$1` and
  `$WinAction` classes now land in the same output package.
- `MurderousWitchShop/` owns `MurderousWitchShopRules` and
  `MurderousWitchShopService`.

Guardrails:

- This is a package ownership move only. Do not change the Murderous Witch role
  id, neutral faction bridge, economy policy, instinct priority/color, win
  precedence, Death Ray packet id, shop entries, translation keys, or resources.

## 2026-07-05 Item-Owned Combat Regrouping

Status: Completed.

Approval scope: owner approved grouping item-bound combat behavior under the
owning item families instead of keeping a separate `combat/` package.

Completion scope:

- Moved Ceremonial Sword item, combat, and dash Modules under
  `item/ceremonialsword/`.
- Moved Fire Poker combat, fall attribution, and rules Modules under
  `item/firepoker/`.
- Moved `MightyForceCombatService` out of the retired `combat/` root and into
  `roles/civilian/apprentice/MightyForce/`, because it is an Apprentice active
  skill window rather than an item family.

Guardrails:

- This is a package ownership move only. Do not change item ids, item assets,
  attack behavior, cooldown values, death reasons, fall attribution windows,
  event ordering, role ids, translation keys, or resources.

## 2026-07-05 Apprentice Ability Split

Status: Completed.

Approval scope: owner requested splitting Apprentice Witch's crowded skill
classes into ability-name directories under `roles/civilian/apprentice/abilities/`.

Completion scope:

- Replaced the old root `ApprenticeWitchSkillRules` constants bucket with
  per-ability Modules: `MightyForceAbility`, `SwiftStepAbility`,
  `MurderSenseAbility`, `HealingAbility`, and `ClairvoyanceAbility`.
- Replaced the old root `ApprenticeWitchSkillService` entry-point bucket with
  `use(...)` methods owned by each ability Module.
- Kept shared Apprentice activation guard logic in `ApprenticeAbilitySupport`.
- Kept the five-ability list and initial cooldown in `ApprenticeAbilityCatalog`.
- Moved the active-window timing helper to `ApprenticeAbilityWindowRules`.
- Moved `MightyForceCombatService` under
  `roles/civilian/apprentice/abilities/MightyForce/`.

Guardrails:

- This is a package and ownership split only. Do not change Apprentice Witch
  role id, ability ids, mana costs, cooldowns, active-window durations, outline
  colors, Murder Sense range, dangerous-item whitelist, translation keys, HUD
  behavior, or deferred-cooldown semantics.

### SparkWitch Client Server-Confirmation Gate

Status: Completed.

Client HUD, visuals, input, sound, and SparkWitch hooks are gated by
`SparkWitchServerConnection.isConfirmedServer()` and reset on disconnect. Version
confirmation uses login-stage and play-stage confirmation to survive proxy
behavior.

Rule: do not remove these gates or move them behind ordinary client-mod presence.

### NoellesRole Enhancement Migration

Status: Completed.

SparkWitch no longer owns migrated NoellesRoles role-enhancement packets,
components, client hooks, flashlight resources, capsule resources, or
dynamic-light entrypoints. SparkWitch still owns Witch-only faction mechanics and
targeted compatibility hooks.

Rule: do not restore migrated role-enhancement Modules for convenience.

### Shadow Jester Showdown And Last Stand Compatibility

Status: Completed.

SparkWitch blocks NoellesRoles Shadow Jester showdown neutral wins while living
Witch-faction members or living Last Stand-triggered outlaws remain. The Last
Stand rule is narrow: only living players who triggered Last Stand this round
count for that final showdown blocker.

Rule: do not broaden this into all outlaws, all past Last Stand participants, or
generic Witch win logic.

### Task Master Money Visibility

Status: Completed.

SparkTraits Task Master reward flow observes Wathe `CanSeeMoney.EVENT`.
SparkWitch-enhanced money visibility must be server-side for relevant roles; a
client-only hook cannot satisfy the reward path.

Rule: trace Task Master issues through the server-side money visibility Seam
before changing role skill logic.

### `SparkWitchEvents`

Status: Watch-Only.

`SparkWitchEvents` is an event registration aggregator. It may remain as a
single Module while it only wires registration and lifecycle cleanup.

Rule: do not split it unless event ordering grows, lifecycle hooks multiply, or a
concrete bug proves the aggregation is hiding domain behavior.

## Cleanup Notes

Status: Observed.

- Resolved on 2026-07-05: `sparkfactionapi_version` is now `0.1.5.1`, and
  `verifyModJarVersion` checks packaged dependency predicates plus the included
  or bundled SparkFactionAPI version.
- `fabric.mod.json` still depends on `noellesroles`, while migrated NoellesRole
  enhancement resources and hooks are intentionally removed. Treat this as a
  compatibility dependency, not ownership of migrated enhancements.
- Empty or stale directories were observed under `src/main/java/.../entity`,
  `src/client/java/.../client/screen`, `src/lambdynlightsStub`, and
  `src/main/resources/assets/sparkwitch/dynamiclights/item`.
- `assets/sparkwitch/models/item/ceremonial_sword.json` declares
  `format_version` `1.21.6` while the project targets Minecraft `1.21.1`.
  Existing tests do not currently catch this mismatch.

## 2026-07-09 Runtime Locality And Public Compatibility Contract

Status: Completed, with component schema Watch-Only.

Approval scope: repair the reviewed architecture drift across compatibility,
component runtime ownership, role-private rules, governance, and focused tests
without changing ids, packet/NBT schemas, tick order, timings, sounds,
teleports, cooldowns, mana, role/faction semantics, or unrelated gameplay.

Completion scope:

- SparkTraits Last Stand and Final Moment reflection now targets only the public
  `dev.caecorthus.sparktraits.api.SparkTraitsApi` facade and remains optional,
  fail-closed `false`.
- `ApprenticeAbilityRuntime`, `PigGodChaseRuntime`,
  `MurderousWitchDeathRayService`, `GrandWitchActiveSkillService`, and
  `WitchManaService` own their runtime tick decisions. `WitchPlayerComponent`
  retains the identical CCA id, stored fields, codecs, sync visibility, NBT
  schema, shared cooldown, and fixed tick-call order.
- `GrandWitchWorldRuntime` owns Grand Witch world-spell countdown behavior while
  `WitchWorldComponent` retains world state, packet/NBT, and sync ownership.
- `ApprenticeInstinctRules` owns Apprentice Murder Sense/Clairvoyance decisions.
- `GrandWitchRules` owns Grand-Witch-private ceremonial-sword constants and
  progress, spell catalog, and Voodoo decision. Shared faction rules remain in
  `WitchFactionRules`.
- `CONTEXT.md` was added; `AGENTS.md` now requires context, constitution, and log
  read order.
- The constitution now treats size numbers as advisory review triggers and uses
  risk-based focused tests instead of a blanket test ban.
- `verifyArchitecture` requires governance documents and rejects Java source
  references to SparkTraits `impl` or `component` packages.

Current board classification:

- Public SparkTraits facade migration: Completed.
- Role/domain runtime extraction from player/world components: Completed.
- Role-private rule Locality: Completed.
- Player/world component schema and codecs: Watch-Only. Their current size is not
  permission for another split; reopen only for a concrete schema or runtime bug.
- The 2026-07-05 note deferring Pig Chase extraction is superseded by this
  owner-approved board; teleport, psycho, sound, speed, and cooldown ordering are
  now preserved in `PigGodChaseRuntime`.

Verification:

- Focused Apprentice window and Grand Witch rule tests followed a red/green
  cycle in an isolated checkout.
- Java 21 main/client compilation completed through the focused Gradle test run.
- `test verifyArchitecture compileJava compileClientJava` succeeded under the
  explicit Temurin Java 21 runtime; 5 focused tests passed with no failures.
- Preserved component reset side-effect order as Pig Chase sound stop, Grand
  Witch BGM/world sync, then owned Pig psycho cleanup.
