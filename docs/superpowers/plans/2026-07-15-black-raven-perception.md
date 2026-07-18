# Black Raven Perception Implementation Plan

> **Execution status:** approved by the owner on 2026-07-15. Implement without committing, pushing, publishing, or bumping versions.

**Goal:** Deliver the complete `sparkwitch:black_raven` Phase 1 vertical slice, including Feather Blade, the owner-private Perception skill and ledger, two instinct modes, SparkTraits Bloodthirsty compatibility, and the SparkAssist Guidebook entry.

**Architecture:** SparkWitch owns one generic secondary-key dispatcher and a role-owned Black Raven module. Delayed marks and Perception use separate CCA components; no fields enter `WitchPlayerComponent`, `WitchPlayerNbtCodec`, or `WitchPlayerSyncCodec`. Wathe and NoellesRoles remain unchanged. SparkTraits owns the additive Bloodthirsty tag and one stable suppression query; SparkWitch consumes only the public facade and contributes Feather Blade through data.

**Toolchain:** Minecraft 1.21.1, Fabric, Java 21, Cardinal Components API, JUnit 5/Gradle.

## Global Constraints

- Preserve all existing dirty Prophet, Witch Maiden, SparkTraits balance, and SparkAssist Guidebook work. Re-read each shared file and its diff immediately before editing. Record pre-existing baseline failures and never report unrelated failures as caused or fixed by Black Raven.
- Do not modify Wathe, NoellesRoles, SparkFactionAPI, SparkStrength, versions, metadata dependency floors, or unrelated roles/traits.
- Keep `SparkWitchEvents` as registration/lifecycle aggregation only; rules and state transitions live under `roles/killer/blackraven/`.
- Keep Perception points, snapshots, and active state out of the shared Witch player packet/NBT schema. Owner-only sync must never send incomplete points or another player's role table.
- The generic skill-key-2 layer may register and dispatch only. It must not contain a Black Raven branch.
- Mixins are thin adapters: capture version-sensitive render/input/inventory seams and delegate to testable role-owned hooks/rules.
- Write and run a focused failing test before each testable production behavior, then make the smallest change that passes it.
- No commit, push, publication, or version bump.

## Task 1: Freeze Pure Black Raven Contracts

**Tests first**

- Add `src/test/java/dev/caecorthus/sparkwitch/roles/killer/blackraven/BlackRavenRulesTest.java`.
- Add `src/test/java/dev/caecorthus/sparkwitch/roles/killer/blackraven/BlackRavenPerceptionStateTest.java`.
- Cover exact constants, role-id checks, 3-block Feather Blade targeting gates, 20-second mark duration, 60-second item cooldown, 60/15/90-second skill timing, 8-block squared distance, one point per 20 qualifying ticks, pause/resume including fractional ticks, 10-point cap, frozen snapshots, match reset, and no partial-client projection.
- Run the two classes and confirm RED because the Black Raven contracts do not exist.

**Implementation**

- Add `roles/killer/blackraven/BlackRavenRules.java` for ids, colors, timings, distances, prices, and pure predicates.
- Add `roles/killer/blackraven/BlackRavenIdentitySnapshot.java` as the immutable UUID/name/role-id/color snapshot value.
- Add `roles/killer/blackraven/BlackRavenPerceptionState.java` for per-target point/tick accumulation and frozen reveal insertion.
- Keep collections bounded to current-round UUIDs and preserve insertion order for deterministic ledger pages.
- Run the focused tests and confirm GREEN.

## Task 2: Register Role, Items, Shop, And Separate Components

**Tests first**

- Add focused source/contract tests for role registration, item/model presence, component ids/respawn strategy, restricted shop entries, loadout cleanup, and the absence of Black Raven fields from shared Witch codecs.
- Add pure tests for one-shot mark expiry decisions, offline killer attribution, match mismatch cleanup, protection-cancel no-retry semantics, ledger ownership, and container/drop rejection rules.
- Run focused tests and confirm RED before production edits.

**Implementation**

- Add `BlackRavenMarkPlayerComponent` (`sparkwitch:black_raven_mark`) on victims with marker UUID, expiry world tick, match UUID, server ticking, NBT persistence, and recipient-specific boolean sync.
- Add `BlackRavenPerceptionPlayerComponent` (`sparkwitch:black_raven_perception`) on the Raven owner with private state, server ticking, owner-only completed-snapshot sync, and no incomplete progress fields in sync packets.
- Register both with `RespawnCopyStrategy.NEVER_COPY` in `SparkWitchComponents` without touching the shared component codecs.
- Add both ids to `fabric.mod.json`'s existing Cardinal Components declaration without changing dependencies or versions.
- Add `FeatherBladeItem`, `BlackRavenLedgerItem`, `BlackRavenTargeting`, `BlackRavenMarkRuntime`, `BlackRavenInventoryRules`, `BlackRavenShopService`, and `BlackRavenFeatureService` under `roles/killer/blackraven/`.
- Merge minimal additions into `SparkWitchItems`, `SparkWitch`, `SparkWitchRoleRegistry`, `SparkWitchRoles`, and `SparkWitchEvents`; retain all concurrent Prophet/Witch Maiden edits and ordering.
- Register only the ledger with `NoellesHiddenEquipment`; do not hide Feather Blade or broaden hidden-item rules to vanilla books.
- Add an empty owner-directed `OpenBlackRavenLedgerS2CPacket` and register it in `SparkWitchPackets`. The server validates exact living role/item use; the packet contains no snapshots or role data and only authorizes the client to open pages from its owner-only component.
- Prevent ledger Q/drop-slot/container movement through a thin item-specific server mixin or event adapter, while allowing rearrangement inside the player's own inventory. Remove rather than drop the ledger on death/role loss/round end and restore exactly one ledger only for a living Raven in the same match. Feather Blade keeps only its approved death-drop exclusion and exact-role use guard; do not hide, bind, transfer-block, or auto-restore it.
- Capture Wathe's existing blackout `ShopEntry`, clear the Raven shop, and add exactly crowbar, poison vial, scorpion, body bag, and the captured blackout entry at approved prices/stock.
- Add the ledger model using vanilla book visuals; preserve the already-approved Feather Blade texture/model.
- Run focused tests and `compileJava`.

## Task 3: Wire Perception Into The Existing Primary Skill System

**Tests first**

- Add tests for role-only skill selection, initial cooldown assignment, fixed 300-tick active window, deferred 1800-tick cooldown start, non-cancelability, forced Blindness maintenance, valid-target accumulation, owner death/role/match cleanup, and protection against Milk/Gin removal. Cover owner disconnect/rejoin (active cancels with no post cooldown while same-match progress/snapshots persist) and target disconnect/rejoin (UUID progress resumes).
- Add tests for a generic skill-id active-window provider: zero for unregistered skills, exact role-component ticks for Perception, duplicate-registration rejection, client/server parity, and no packet/NBT fields.
- Add source tests proving no new active-skill packet and no shared Witch schema field.
- Run focused tests and confirm RED.

**Implementation**

- Add `BlackRavenPerceptionService` for server-authoritative activation/ticking/snapshot resolution and `BlackRavenSkillService` as the narrow `WitchSkillUseContext` adapter.
- Extend the existing `WitchSkillRegistry` with an overload accepting an optional `ToIntFunction<PlayerEntity>` active-window provider and an `activeWindowTicks(skillId, player)` query. Make `WitchPlayerComponent.getActiveSkillWindowTicks()` consult it after preserving every existing window calculation. Do not change `WitchSkillDefinition` ABI, component fields, codecs, or tick order.
- Register `sparkwitch:perception` in `SparkWitchBuiltInSkills` with initial `1200`, active `300`, post `1800`, zero mana, exact Black Raven selector, and `successAfterActiveWindow(...)`.
- Register a Perception provider that reads `BlackRavenPerceptionPlayerComponent.activeTicks()`. Use the existing deferred-cooldown seam; when the separate Perception component naturally reaches zero, call `WitchPlayerComponent.startDeferredCooldownNow()` exactly once and immediately call `sync()` so the owner cannot flash Ready. Do not add fields or reorder its tick.
- Implement `ClientTickingComponent` for a smooth owner-local active countdown. Sync at activation, one-second correction points, completed snapshot changes, and end; unfinished target points/fractional ticks remain unsynced.
- During the active window maintain vanilla Blindness, scan only same-world alive active players in a 3D 8-block sphere, and sync only at the approved correction/state boundaries.
- Bind the Perception component to the new match UUID during Wathe `ON_FINISH_INITIALIZE`, after `GameRecordManager.startMatch()` has created it. `RoleAssigned` may clear/give the role-owned loadout, while the existing `WitchSkillAssignmentService` remains the sole owner of initial skill selection/cooldown; neither path may capture a null or previous match id.
- Generate ledger pages client-side from owner-private synced snapshots; the server stack remains free of role data.
- Run focused tests and `compileJava`.

## Task 4: Add Generic Skill Key 2 And Black Raven Client Presentation

**Tests first**

- Add pure tests for the role-id keyed secondary handler registry, `NORMAL`/`SENSED_ONLY` cycle, default/reset behavior, active-window input lock, terminal outline precedence, Feather Blade precedence, protection suppression, and 2-block identity-text gate.
- Add source tests for key registration (`N`), disconnect reset, mixin delegation, exact shader factor `0.50`, Steve skin scope, existing HUD row integration, and absence of FactionAPI/Wathe/Noelles edits.
- Run focused tests and confirm RED.

**Implementation**

- Add a client-only secondary ability registry/controller. `SparkWitchClient` registers the configurable `N` key and dispatches by current role id; Black Raven registers its own handler during client initialization.
- Add Black Raven client mode state and clear it on login/disconnect, death, role loss, and round end. Ignore key 2 during the active skill and restore the prior mode afterward.
- Add a role-owned `BlackRavenHudRenderer` and narrow HUD mixin one stable row above the existing skill line. Do not hardcode Black Raven into `WitchSkillHudRenderer` or alter other role layouts.
- Add a SparkWitch-owned post effect based on the Depression desaturation math with `DesaturateFactor = 0.50`; preserve GPL attribution in `THIRD_PARTY_NOTICES.md`, close/recreate the processor across disconnect/resize, and do not call SparkTraits internals.
- Add both a broad `getSkinTextures` Steve adapter and a terminal lower-priority renderer-texture override for other players while the local Raven's Perception window is active. This prevents later SparkTraits skin hooks from replacing Steve; keep nameplates and all movement/combat/input behavior.
- Use two priority-separated Wathe adapters: a high-priority HEAD gate for `isInstinctEnabled*` during active Perception (turning light off), and a low-priority terminal `getInstinctHighlight` return resolver after Noelles/SparkTraits/Witch hooks.
- In `SENSED_ONLY`, make the Feather mark hook return null before upstream arbitration. The terminal resolver may return a frozen sensed color only while Alt is held, the target is known/alive, public visibility allows it, and upstream did not already resolve to hidden. Unrevealed targets return no outline. `NORMAL` falls through; active Perception allows only the independent Feather mark and no other instinct result.
- Add a fail-closed reflection bridge that calls only `SparkTraitsApi.isInstinctHidden(viewer, target)` when SparkTraits is present.
- Keep Feather Blade visible during the active skill and normal mode. In sensed mode, revealed role color wins and unrevealed targets remain hidden even when marked.
- Add a narrow `RoleNameRenderer` adapter using the exact ordinary 2-block `ProjectileUtil` living-player target path and an explicit upstream-visible-name signal. A TAIL adapter is allowed only if it shadows/checks Wathe's post-wrapper stored name, stored alpha, and the same darkness early-return condition. In sensed mode (without requiring Alt), append only the frozen role text when the target is known/alive/visible and the wrapped name is nonblank; never widen spectator/body/trait permissions or reveal through Noelles/SparkStrength blank-name hooks.
- Add the client-only read-only ledger screen adapter and dynamic styled pages.
- Update client mixin JSON and language keys with minimal merges.
- Run focused tests, `compileClientJava`, and a client resource validation/build.

## Task 5: Extend SparkTraits Through Owned Contracts

**Tests first**

- In SparkTraits, add focused tests proving the Bloodthirsty weapon gate accepts exactly `wathe:knife`, `noellesroles:poison_needle`, and downstream tag entries; rejects unrelated knife-like/Thrust weapons; and still delegates duration math to the existing service.
- Add public-facade tests for null safety, common-source/dedicated-server safety, Going Dark with Final Moment bypass, Last Stand pending/hidden, and spirit-projection suppression parity.
- Run the focused tests and confirm RED.

**Implementation**

- Add `data/sparktraits/tags/item/bloodthirsty_weapons.json` with Wathe knife and Noelles poison needle.
- Add a Bloodthirsty-specific tag key/helper and change only `BloodthirstyCooldownMixin` from the knife identity check to tag membership.
- Add `SparkTraitsApi.isInstinctHidden(PlayerEntity viewer, PlayerEntity target)` delegating to the existing synced SparkTraits-owned suppression rules: Going Dark with Final Moment bypass plus Last Stand pending/hidden and spirit-projection hiding. Keep it null-safe and do not expose components or pending maps.
- In SparkWitch, add `data/sparktraits/tags/item/bloodthirsty_weapons.json` with `replace: false` and Feather Blade only.
- Do not change Bloodthirsty percentage, cap, kill counting, Thrust, left-click knockback, Poison Needle implementation, or delayed-cooldown retroactivity.
- Run focused tests and Java 21 `test verifyArchitecture compileJava compileClientJava` in SparkTraits.

## Task 6: Guidebook, Context, And Localization Alignment

**Tests first**

- In SparkAssist, add `GuidebookBlackRavenResourcesTest` covering id `sparkwitch:black_raven`, color `#51445F`, order `430`, role-tab ownership, required mod, authored paragraphs, 60/15/90 timing, 8/10 accumulation, and adjacency after Kidnapper/before Witch Maiden or Grand Witch.
- Extend discovery tests so `sparkwitch:perception` is excluded from `SKILL` but allowed in `ROLE`, preserving all concurrent Prophet/Tarot/Witch Maiden expectations.
- Run focused tests and confirm RED.

**Implementation**

- Add `assets/sparkassist/guidebook/roles/sparkwitch/black_raven.json` and merge only the required localization keys into both SparkAssist language files.
- Add `sparkwitch:perception` to the existing excluded-skill set without overwriting concurrent `death_omen`, `pig_chase`, or `focused_footsteps` changes.
- Merge Black Raven item/skill/HUD/message localization into SparkWitch `zh_cn.json` and `en_us.json`.
- Update SparkWitch `CONTEXT.md` with the Black Raven module, two separate component ids, generic key-2 ownership, public SparkTraits facade seam, and verification entry points. Do not rewrite concurrent Prophet/Witch Maiden context.
- Run focused SparkAssist tests and resource validation.

## Task 7: Architecture Review And Full Verification

- Re-read this plan and the approved design spec line by line; produce a requirement checklist and inspect each implementation path.
- Confirm `git diff --name-only` contains no Wathe, NoellesRoles, SparkFactionAPI, SparkStrength, version, or metadata changes.
- Confirm no Black Raven field was added to `WitchPlayerComponent`, `WitchPlayerNbtCodec`, or `WitchPlayerSyncCodec`; `SparkWitchEvents` contains registration only; all mixins delegate.
- Confirm component sync recipients and packet fields do not expose incomplete points, target role tables, marker UUIDs, expiry ticks, or match UUIDs.
- Run Java 21 sequential verification in SparkWitch:

```sh
./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 test verifyArchitecture compileJava compileClientJava
./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 clean build
git diff --check
```

- Run the equivalent Java 21 focused/full sequential builds and `git diff --check` in SparkTraits and SparkAssist, one repository at a time. Because all three live checkouts contain unrelated dirty work and are behind/different branches, compare against recorded baselines; if an unrelated baseline failure remains, report it precisely rather than claiming full green or rewriting concurrent files.
- Perform final source review for unrelated-role reachability and report any manual in-game checks that cannot be executed in the workspace.
