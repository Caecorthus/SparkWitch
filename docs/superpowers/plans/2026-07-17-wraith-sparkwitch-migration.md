# Wraith SparkWitch Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make SparkWitch the sole owner of Wraith, all Wraith gameplay and presentation, and the five promotion identities, while preserving optional SparkTraits traits and making Curser a Witch-faction identity.

**Architecture:** The current role-first Wraith implementation in the live SparkTraits checkout is the behavioral source, but its ownership moves into role-owned SparkWitch packages and every canonical role/component id becomes `sparkwitch:*`. SparkTraits retains only a public opaque-NBT trait bridge, retired-id filtering, and a fail-closed Wraith query adapter; SparkAssist owns the corresponding Guidebook migration. Legacy `sparktraits:*` roles and CCA data are accepted only at read boundaries.

**Tech Stack:** Java 21, Fabric Loader/API, Minecraft 1.21.1, Cardinal Components API, Wathe 1.5.7 Spark, NoellesRoles 1.7.7, SparkFactionAPI 0.1.5.9, Simple Voice Chat API, Gradle, JUnit 5.

## Global Constraints

- Canonical role ids are exactly `sparkwitch:wraith`, `sparkwitch:wind_spirit`, `sparkwitch:guardian_angel`, `sparkwitch:vendetta`, `sparkwitch:saboteur`, and `sparkwitch:curser`.
- Canonical component ids are exactly `sparkwitch:wraith_player` and `sparkwitch:wraith_round`; canonical writers never emit the old namespace.
- Exact old `sparktraits:*` ids are accepted only at read boundaries and unrelated ids remain byte-for-byte untouched.
- SparkWitch may reflect only `dev.caecorthus.sparktraits.api.SparkTraitsApi`; it must not import or name SparkTraits internal packages.
- SparkTraits remains optional. SparkWitch `0.1.6.1` rejects an installed SparkTraits version below `0.1.9.10`.
- Coordinated versions are SparkWitch `0.1.6.1`, SparkTraits `0.1.9.10`, and SparkAssist `0.1.3.7`.
- Curser registers in `SparkWitchFactions.WITCH`, keeps color `0xC13838`, remains non-rollable, and remains in the KILLER-origin promotion pool.
- Curser shares Witch cohort visibility, blackout immunity, area-spell immunity, and Witch victory, but stays Wathe-dead and never counts as a living Witch majority member.
- Curser receives no Grand Witch/Accomplice economy, mana, shop, loadout, active skills, or poison visibility.
- `gui.sparkwitch.skills` remains exclusive to Grand Witch, Apprentice Witch, and Murderous Witch; Curser and all other promotion identities are excluded.
- Text chat and inventory opening remain normal for Wraith. Only outgoing Simple Voice Chat audio is blocked.
- Do not migrate `WraithChatRestrictionMixin` or the obsolete `WraithInventoryKeyMixin`.
- Do not fire `RoleAssigned` during Wraith conversion or promotion; use Wathe role replacement, sync, and `RoleAnnouncementApi.announceCurrentRole` only.
- Preserve the exact Wraith quota, `random < 0.75` cutoff, eligible-death ordering, one-time Adventure transition, task cadence, effects, privacy, reconnect, promotion, and cleanup behavior from the approved design.
- No production changes belong in SparkFactionAPI, Wathe, NoellesRoles, or SparkStrength unless a focused verification proves their existing public contract insufficient.
- Work only in isolated branches and preserve all unrelated dirt in shared checkouts.
- Add concise English and Chinese comments only on non-obvious public contracts, optional-mod seams, mixin authority boundaries, and legacy migration behavior.
- Run builds sequentially with Java 21; never overlap clean builds across repositories.

---

### Task 1: SparkTraits Public Trait Bridge And Retirement

**Repository:** `/Users/kricy/Documents/Codex-Projects/.codex-worktrees/wraith/SparkTraitsMigration`

**Files:**
- Modify: `src/main/java/dev/caecorthus/sparktraits/api/SparkTraitsApi.java`
- Create: `src/main/java/dev/caecorthus/sparktraits/compat/SparkWitchWraithBridge.java`
- Modify: `src/main/java/dev/caecorthus/sparktraits/component/RetiredTraitIds.java`
- Modify: `src/main/java/dev/caecorthus/sparktraits/component/TraitPlayerComponent.java`
- Modify: `src/main/java/dev/caecorthus/sparktraits/component/SparkTraitsComponents.java`
- Modify: `src/main/java/dev/caecorthus/sparktraits/impl/assignment/TraitAssignmentService.java`
- Modify: `src/main/java/dev/caecorthus/sparktraits/impl/lifecycle/TraitGameHooks.java`
- Modify: `src/main/java/dev/caecorthus/sparktraits/impl/compatibility/sparkfactionapi/SparkFactionApiEffectiveFactionBridge.java`
- Modify: `src/main/java/dev/caecorthus/sparktraits/voice/SparkTraitsVoiceChatPlugin.java`
- Modify: `src/main/resources/fabric.mod.json`
- Modify: `src/main/resources/sparktraits.mixins.json`
- Modify: `src/client/resources/sparktraits.client.mixins.json`
- Modify: `src/main/resources/assets/sparktraits/lang/en_us.json`
- Modify: `src/main/resources/assets/sparktraits/lang/zh_cn.json`
- Modify: `gradle.properties`
- Delete: Wraith-owned role, component, runtime, mixin, client, and promotion-role files listed in the approved design source inventory
- Test: `src/test/java/dev/caecorthus/sparktraits/api/SparkTraitsWraithBridgeTest.java`
- Test: `src/test/java/dev/caecorthus/sparktraits/component/RetiredTraitIdsTest.java`
- Test: `src/test/java/dev/caecorthus/sparktraits/WraithOwnershipRemovalSourceTest.java`

**Interfaces:**
- Consumes: canonical Wraith state through reflective public calls to `dev.caecorthus.sparkwitch.api.SparkWitchApi.isWraithActive(PlayerEntity)`.
- Produces: `SparkTraitsApi.captureWraithTraitSnapshot(PlayerEntity): NbtCompound`.
- Produces: `SparkTraitsApi.restoreWraithTraitSnapshot(PlayerEntity, NbtCompound): void`.
- Produces: `SparkTraitsApi.clearWraithTraits(PlayerEntity, boolean gameEnd): void`.
- Preserves: `SparkTraitsApi.isWraithActive(PlayerEntity): boolean`, now delegating fail-closed to SparkWitch.

- [ ] **Step 1: Create the isolated branch from the current provider baseline**

Run from `/Users/kricy/Documents/Codex-Projects/SparkTraits`:

```bash
git fetch origin
git worktree add /Users/kricy/Documents/Codex-Projects/.codex-worktrees/wraith/SparkTraitsMigration \
  -b codex/wraith-sparktraits-bridge origin/codex/trait-balance-bomb-maniac
```

Expected: the new worktree is clean and contains the non-Wraith trait work already published on the provider branch.

- [ ] **Step 2: Write failing public-bridge and ownership tests**

The bridge test must assert the exact opaque schema and event semantics:

```java
assertEquals(List.of("sparktraits:first", "sparktraits:hidden"), readStrings(snapshot, "ActiveTraits"));
assertEquals(List.of("sparktraits:first"), readStrings(snapshot, "RevealedTraits"));
SparkTraitsApi.restoreWraithTraitSnapshot(player, snapshot);
assertEquals(List.of(FIRST_ID, HIDDEN_ID, CautiousTrait.ID), component.getActiveTraitIds());
assertEquals(Set.of(FIRST_ID, CautiousTrait.ID), component.getRevealedTraitIds());
SparkTraitsApi.clearWraithTraits(player, false);
assertEquals(List.of(TraitRemovalReason.DEATH), removals);
SparkTraitsApi.clearWraithTraits(player, true);
assertEquals(List.of(TraitRemovalReason.GAME_END), removals);
```

`WraithOwnershipRemovalSourceTest` must scan production sources/resources and prove that no Wraith runtime/component/role/mixin/localization remains except `RetiredTraitIds`, `SparkTraitsApi`, `SparkWitchWraithBridge`, and test fixtures; it must also prove the voice plugin still contains Depression behavior but no Wraith microphone guard.

- [ ] **Step 3: Run the tests to verify RED**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  test --tests '*SparkTraitsWraithBridgeTest' --tests '*WraithOwnershipRemovalSourceTest' --tests '*RetiredTraitIdsTest'
```

Expected: FAIL because the public bridge methods are absent and Wraith-owned source still exists.

- [ ] **Step 4: Implement the public opaque-NBT bridge**

Use exactly these public methods and keys:

```java
public static NbtCompound captureWraithTraitSnapshot(PlayerEntity player) {
    NbtCompound snapshot = new NbtCompound();
    if (player == null) return snapshot;
    TraitPlayerComponent.KEY.maybeGet(player).ifPresent(component -> {
        snapshot.put("ActiveTraits", identifiers(component.getActiveTraitIds()));
        snapshot.put("RevealedTraits", identifiers(component.getRevealedTraitIds()));
    });
    return snapshot;
}

public static void restoreWraithTraitSnapshot(PlayerEntity player, NbtCompound snapshot) {
    if (player == null || snapshot == null) return;
    TraitPlayerComponent.KEY.maybeGet(player).ifPresent(component -> {
        LinkedHashSet<Identifier> active = readIdentifiers(snapshot, "ActiveTraits");
        active.add(CautiousTrait.ID);
        LinkedHashSet<Identifier> revealed = readIdentifiers(snapshot, "RevealedTraits");
        revealed.add(CautiousTrait.ID);
        component.restoreActiveTraitsForRuntime(active, revealed, TraitAssignmentReason.INTERNAL);
    });
}

public static void clearWraithTraits(PlayerEntity player, boolean gameEnd) {
    if (player == null) return;
    TraitPlayerComponent.KEY.maybeGet(player).ifPresent(component -> component.clearActiveTraits(
            gameEnd ? TraitRemovalReason.GAME_END : TraitRemovalReason.DEATH));
}
```

Keep identifier parsing fail-closed and ignore malformed/retired ids. `sparktraits:cautious` is appended beyond the normal three-slot cap and is owner-visible, matching the live Wraith behavior.

Implement `SparkWitchWraithBridge.isWraithActive(PlayerEntity)` with cached reflection of only `dev.caecorthus.sparkwitch.api.SparkWitchApi`; return `false` for missing mod/class/method, null player, reflection errors, or non-boolean results.

- [ ] **Step 5: Retire old ownership without broad cleanup**

Add exactly `Identifier.of("sparktraits", "wraith")` to `RetiredTraitIds`. Remove the old Wraith CCA registration, quota initialization from `TraitAssignmentService`, Wraith effective-faction resolver, Wraith event wiring, Wraith voice filtering, six role registrations, Wraith mixin entries/classes, client render integration, and only the 18 Wraith/promotion localization keys. Preserve Depression voice logic and every unrelated trait, role, mixin, resource, and event order.

Set `mod_version=0.1.9.10`.

- [ ] **Step 6: Run focused and full SparkTraits verification**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  test --tests '*SparkTraitsWraithBridgeTest' --tests '*WraithOwnershipRemovalSourceTest' --tests '*RetiredTraitIdsTest'
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  test verifyArchitecture compileJava compileClientJava
git diff --check
```

Expected: all commands PASS; no production class outside the narrow bridge owns Wraith.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: retire Wraith ownership from SparkTraits"
```

---

### Task 2: SparkWitch Roles, Factions, Versions, And Presentation Boundary

**Repository:** `/Users/kricy/Documents/Codex-Projects/.codex-worktrees/wraith/SparkWitch`

**Files:**
- Modify: `gradle.properties`
- Replace tracked provider jars in: `libs/`
- Modify: `src/main/resources/fabric.mod.json`
- Modify: `src/main/java/dev/caecorthus/sparkwitch/registry/SparkWitchRoleRegistry.java`
- Modify: `src/main/java/dev/caecorthus/sparkwitch/SparkWitchRoles.java`
- Modify: `src/main/java/dev/caecorthus/sparkwitch/roles/witch/WitchFactionRules.java`
- Modify: `src/main/java/dev/caecorthus/sparkwitch/roles/witch/WitchFactionEconomyPolicy.java`
- Modify: `src/main/java/dev/caecorthus/sparkwitch/skill/WitchSkillPresentationRules.java`
- Create: `src/main/java/dev/caecorthus/sparkwitch/roles/special/wraith/WraithRole.java`
- Create: `src/main/java/dev/caecorthus/sparkwitch/roles/special/wraith/WraithState.java`
- Create: `src/main/java/dev/caecorthus/sparkwitch/roles/special/wraith/WraithPromotionRoles.java`
- Create: `src/main/java/dev/caecorthus/sparkwitch/roles/special/wraith/PlaceholderRoleFactory.java`
- Modify: `src/main/resources/assets/sparkwitch/lang/en_us.json`
- Modify: `src/main/resources/assets/sparkwitch/lang/zh_cn.json`
- Test: `src/test/java/dev/caecorthus/sparkwitch/roles/special/wraith/WraithRoleRegistrationTest.java`
- Test: `src/test/java/dev/caecorthus/sparkwitch/roles/special/wraith/WraithPromotionRolesTest.java`
- Test: `src/test/java/dev/caecorthus/sparkwitch/roles/witch/CurserWitchBoundaryTest.java`

**Interfaces:**
- Produces: six role constants/getters and `assassinGuessRoles()` in `SparkWitchRoleRegistry` and `SparkWitchRoles`.
- Produces: `WraithPromotionRoles.pick(WraithState.Alignment, RandomGenerator): Role` with GOOD pool `[windSpirit, guardianAngel, vendetta]` and KILLER pool `[saboteur, curser]`.
- Produces: `WitchSkillPresentationRules.shouldShowInventorySkillPanel(Role, Identifier): boolean` with the exact three roles and each role's own skill ids.

- [ ] **Step 1: Synchronize the isolated target with the live dependency baseline**

Apply the live dependency coordinates before feature work: Wathe `1.5.7-spark-1.21.1`, NoellesRoles `1.7.7-h1.5.7-spark`, SparkFactionAPI `0.1.5.9`. Replace only the three matching provider jars with the byte-identical jars from the live SparkWitch checkout, then set `mod_version=0.1.6.1`.

In `fabric.mod.json`, keep SparkTraits optional and add:

```json
"breaks": {
  "sparktraits": "<0.1.9.10"
}
```

- [ ] **Step 2: Write failing role and boundary tests**

Assert canonical ids, colors, non-rollable conditions, `MoodType.NONE`, correct native/effective factions, Assassin order membership for the five promotion roles only, and exact promotion pools. Assert Curser is a Witch member but remains rejected by `WitchSkillPresentationRules`, `WitchManaRules`, shop/loadout rules, poison visibility, direct-kill rewards, and passive rewards.

```java
assertEquals(Identifier.of("sparkwitch", "curser"), SparkWitchRoles.curser().identifier());
assertEquals(SparkWitchFactions.WITCH, SparkFactionApi.resolveBaseFaction(SparkWitchRoles.curser()));
assertTrue(WitchFactionRules.isWitchFactionMember(SparkWitchRoles.curser()));
assertFalse(WitchSkillPresentationRules.shouldShowInventorySkillPanel(SparkWitchRoles.curser(), SparkWitch.id("curser_fixture")));
assertFalse(SparkWitchRoles.assassinGuessRoles().contains(SparkWitchRoles.wraith()));
```

- [ ] **Step 3: Run the tests to verify RED**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  test --tests '*WraithRoleRegistrationTest' --tests '*WraithPromotionRolesTest' --tests '*CurserWitchBoundaryTest'
```

Expected: FAIL because the six SparkWitch roles do not exist.

- [ ] **Step 4: Register Wraith and promotion identities**

Use `WatheRoles.registerSpecialRole` for Wraith. Use `FactionRoleDefinition` for the five promotion identities. Curser must be registered exactly as:

```java
curser = SparkFactionApi.registerRole(FactionRoleDefinition.builder(CURSER_ID, SparkWitchFactions.WITCH)
        .color(0xC13838)
        .moodType(Role.MoodType.NONE)
        .maxSprintTime(-1)
        .canSeeTime(true)
        .appearanceCondition(context -> false)
        .build());
```

The three GOOD identities use `FactionIds.CIVILIAN`, native Wathe `Faction.CIVILIAN`, ten-second sprint, and no time visibility. Saboteur uses `FactionIds.KILLER`, native Wathe `Faction.KILLER`, unlimited sprint, and time visibility. Append all five to the existing Assassin tail in the source order shown in Global Constraints.

Add exact English/Chinese role and goal keys under the SparkWitch namespace, preserving the established names `冤魂`, `风精灵`, `守护天使`, `仇杀客`, `破坏者`, and `诅咒者`.

- [ ] **Step 5: Extend only generic Witch faction membership**

Make `WitchFactionRules.isWitchFactionMember` recognize Curser in addition to Grand Witch and Accomplice for cohort visibility, blackout immunity, area-spell immunity, and Witch victory. Audit every caller: introduce a Grand-Witch/Accomplice-only predicate for killer-style instinct light, hidden-phantom skipping, and dropped-item instinct presentation so Curser does not inherit those unrelated visuals.

Port the exact live-checkout implementation of `WitchSkillPresentationRules`: it must validate both the role and that role's own skill id, not merely membership in a three-role set. Its decision structure remains:

```java
if (role == SparkWitchRoles.grandWitch()) {
    return GrandWitchActiveSkillService.CEREMONIAL_SWORD_SKILL_ID.equals(skillId);
}
if (role == SparkWitchRoles.apprenticeWitch()) {
    return ApprenticeAbilityCatalog.ABILITY_IDS.contains(skillId);
}
return role == SparkWitchRoles.murderousWitch()
        && MurderousWitchDeathRayRules.isDeathRaySkill(skillId);
```

Tests must reject Curser with every registered skill and reject cross-role skill ids for all three allowed roles. This closes the pre-existing broad gate and is required before registering the migrated roles.

In `WitchFactionRules.economyDecision`, return `Boolean.FALSE` for Curser with `RewardKind.PASSIVE` or `RewardKind.DIRECT_KILL`, and `null` for Curser reward kinds unrelated to those two. Add policy-level tests proving this explicit denial wins over the Witch faction capability fallback while Grand Witch and Accomplice results stay unchanged.

- [ ] **Step 6: Verify and commit**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  test --tests '*WraithRoleRegistrationTest' --tests '*WraithPromotionRolesTest' --tests '*CurserWitchBoundaryTest'
git diff --check
git add -A
git commit -m "feat: register Wraith promotion identities"
```

Expected: focused tests PASS and the only broadened Witch behavior is generic faction membership.

---

### Task 3: SparkWitch Canonical Components And Legacy Persistence Migration

**Repository:** `/Users/kricy/Documents/Codex-Projects/.codex-worktrees/wraith/SparkWitch`

**Files:**
- Create: `src/main/java/dev/caecorthus/sparkwitch/component/WraithPlayerState.java`
- Create: `src/main/java/dev/caecorthus/sparkwitch/component/WraithPlayerComponent.java`
- Create: `src/main/java/dev/caecorthus/sparkwitch/component/WraithRoundQuota.java`
- Create: `src/main/java/dev/caecorthus/sparkwitch/component/WraithRoundComponent.java`
- Create: `src/main/java/dev/caecorthus/sparkwitch/component/LegacyWraithPlayerComponent.java`
- Create: `src/main/java/dev/caecorthus/sparkwitch/component/LegacyWraithRoundComponent.java`
- Modify: `src/main/java/dev/caecorthus/sparkwitch/component/SparkWitchComponents.java`
- Create: `src/main/java/dev/caecorthus/sparkwitch/compat/WraithLegacyRoleIds.java`
- Create: `src/main/java/dev/caecorthus/sparkwitch/mixin/WraithGameWorldNbtMixin.java`
- Create: `src/main/java/dev/caecorthus/sparkwitch/mixin/WraithRoleHistoryNbtMixin.java`
- Modify: `src/main/resources/sparkwitch.mixins.json`
- Modify: `src/main/resources/fabric.mod.json`
- Create: `src/main/java/dev/caecorthus/sparkwitch/api/SparkWitchApi.java`
- Test: `src/test/java/dev/caecorthus/sparkwitch/roles/special/wraith/WraithPlayerStateTest.java`
- Test: `src/test/java/dev/caecorthus/sparkwitch/roles/special/wraith/WraithRoundQuotaTest.java`
- Test: `src/test/java/dev/caecorthus/sparkwitch/roles/special/wraith/WraithComponentSchemaSourceTest.java`
- Test: `src/test/java/dev/caecorthus/sparkwitch/roles/special/wraith/WraithLegacyPersistenceTest.java`

**Interfaces:**
- Produces: `WraithPlayerComponent.KEY` at `sparkwitch:wraith_player` with sync order active, restricted, completed tasks, alignment ordinal, promotion pending.
- Produces: `WraithRoundComponent.KEY` at `sparkwitch:wraith_round` with starting player count and cumulative consumed UUIDs.
- Produces: `SparkWitchApi.isWraithActive(PlayerEntity): boolean` and `SparkWitchApi.isWraithRestricted(PlayerEntity): boolean`.
- Produces: `WraithLegacyRoleIds.canonicalize(Identifier): Identifier` and an exact six-entry mapping.

- [ ] **Step 1: Write failing pure state, schema, and migration tests**

Use these boundary assertions:

```java
assertEquals(0, WraithRoundQuota.capForStartingPlayers(9));
assertEquals(1, WraithRoundQuota.capForStartingPlayers(10));
assertEquals(1, WraithRoundQuota.capForStartingPlayers(14));
assertEquals(2, WraithRoundQuota.capForStartingPlayers(15));
assertEquals(Identifier.of("sparkwitch", "curser"), WraithLegacyRoleIds.canonicalize(Identifier.of("sparktraits", "curser")));
assertSame(unrelated, WraithLegacyRoleIds.canonicalize(unrelated));
```

Schema tests must inspect source and assert exact component ids, NBT keys, sync write/read order, owner-only privacy, canonical-only writers, and the presence of read-only legacy readers for `sparktraits:wraith_player` and `sparktraits:wraith_round`.

Migration tests must perform actual CCA container serialization/deserialization and prove:

- old GameWorld role UUID lists copy to canonical keys only when canonical is absent;
- exact old disabled-role strings become canonical strings;
- exact old RoleHistory `RoleId` strings become canonical strings;
- unrelated namespaces and ids remain unchanged;
- old-only player/round component data imports once into canonical state;
- canonical-only data round-trips without a legacy tag;
- when both tags exist, canonical state wins regardless of component read order;
- legacy player/round components never emit a serialized tag.

- [ ] **Step 2: Run the tests to verify RED**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  test --tests '*WraithPlayerStateTest' --tests '*WraithRoundQuotaTest' \
  --tests '*WraithComponentSchemaSourceTest' --tests '*WraithLegacyPersistenceTest'
```

Expected: FAIL because canonical components and legacy adapters are absent.

- [ ] **Step 3: Port the data-only canonical components**

Port the later role-first state from SparkTraits and change only ownership/packages/ids. Preserve NBT fields exactly:

```java
tag.putBoolean("WraithActive", true);
tag.putBoolean("WraithRestricted", state.isRestricted());
tag.putInt("WraithCompletedTasks", state.getCompletedTasks());
tag.putString("WraithAlignment", state.getAlignment().name());
tag.putBoolean("WraithPromotionPending", true);
```

Preserve sync packet order exactly and expose only active/restricted to non-owner recipients. `SparkWitchApi` is null-safe and reads synchronized component state only.

- [ ] **Step 4: Implement exact legacy read-boundary adapters**

Implement the six-entry immutable mapping and use targeted mixins at Wathe NBT read boundaries. The GameWorld adapter performs this operation for each mapping:

```java
if (!nbt.contains(canonical.toString()) && nbt.contains(legacy.toString())) {
    nbt.put(canonical.toString(), nbt.get(legacy.toString()).copy());
}
```

Canonicalize exact strings in `DisabledRoles` and `RoleHistory` before Wathe parses them. Do not remove old keys from the loaded tag and do not touch partial matches.

Register legacy components as read-only holders. Their `readFromNbt` stores the known old fields, and `writeToNbt` is an intentional no-op. Canonical components lazily import after deserialization: a private canonical-data-present flag wins when both tags exist, an import-completed flag makes old-only transfer one-shot, and getters/writers call the focused `ensureLegacyImported()` boundary. Add both legacy ids to Fabric's Cardinal Components declaration while canonical ids remain the only state writers.

- [ ] **Step 5: Run focused verification and commit**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  test --tests '*WraithPlayerStateTest' --tests '*WraithRoundQuotaTest' \
  --tests '*WraithComponentSchemaSourceTest' --tests '*WraithLegacyPersistenceTest'
git diff --check
git add -A
git commit -m "feat: own Wraith state in SparkWitch"
```

Expected: state, privacy, and exact legacy migration tests PASS.

---

### Task 4: SparkWitch Server Lifecycle, Trait Bridge, Promotion, And Voice

**Repository:** `/Users/kricy/Documents/Codex-Projects/.codex-worktrees/wraith/SparkWitch`

**Files:**
- Create: the server Wraith domain files under `src/main/java/dev/caecorthus/sparkwitch/roles/special/wraith/` corresponding to the approved 24-file source inventory
- Create: `src/main/java/dev/caecorthus/sparkwitch/compat/SparkTraitsWraithBridge.java`
- Create: `src/main/java/dev/caecorthus/sparkwitch/roles/special/wraith/WraithDeferredActivationService.java`
- Create: `src/main/java/dev/caecorthus/sparkwitch/voice/SparkWitchVoiceChatPlugin.java`
- Modify: `src/main/java/dev/caecorthus/sparkwitch/impl/SparkWitchEvents.java`
- Modify: `src/main/java/dev/caecorthus/sparkwitch/SparkWitch.java`
- Create: `src/main/java/dev/caecorthus/sparkwitch/mixin/WraithDamageMixin.java`
- Create: `src/main/java/dev/caecorthus/sparkwitch/mixin/WraithDoorPassingMixin.java`
- Create: `src/main/java/dev/caecorthus/sparkwitch/mixin/WraithOwnedEffectMixin.java`
- Create: `src/main/java/dev/caecorthus/sparkwitch/mixin/WraithStatusEffectMixin.java`
- Create: `src/main/java/dev/caecorthus/sparkwitch/mixin/PlayerMoodComponentAccessor.java`
- Modify: existing thin Wathe game hooks/mixins only where Wraith event ordering requires it
- Modify: `src/main/resources/sparkwitch.mixins.json`
- Modify: `src/main/resources/fabric.mod.json`
- Modify: `build.gradle`
- Test: port/adapt all server-side Wraith pure and contract tests to `src/test/java/dev/caecorthus/sparkwitch/roles/special/wraith/`
- Test: `src/test/java/dev/caecorthus/sparkwitch/voice/WraithVoicePluginContractTest.java`

**Interfaces:**
- Consumes: Task 1 public SparkTraits methods by reflection only.
- Consumes: Task 2 roles/pools and Task 3 canonical components/API.
- Produces: `WraithService`, `WraithDeathService`, `WraithTaskService`, `WraithPromotionService`, `WraithSessionService`, and the thin event/mixin wiring used by the client task.
- Produces: SparkFactionAPI effective-faction/player-affect registrations owned by SparkWitch.

- [ ] **Step 1: Port failing server tests with canonical packages and ids**

Port the current later Wraith tests from the live SparkTraits checkout, changing only package ownership, canonical ids, and the approved chat correction. Coverage must include both possible SparkTraits/SparkWitch listener registration orders, normal death cleanup before trait restoration, a newly triggered Last Stand cancelling activation without consuming quota, an earlier Last Stand not blocking a later real death, runtime quota initialization from the fixed Wathe roster, and exactly one corpse retaining the original role even when world time advances before deferred activation. Also include:

```java
assertFalse(WraithRules.passesChance(0.75D));
assertTrue(WraithRules.passesChance(Math.nextDown(0.75D)));
assertEquals(3, state.recordTaskCompletion());
assertTrue(state.isPromotionPending());
assertFalse(source.contains("RoleAssigned.EVENT.invoker"));
assertTrue(source.contains("RoleAnnouncementApi.announceCurrentRole"));
```

Add Curser regressions proving the saved GOOD/KILLER override applies only while restricted and that a promoted Curser resolves as `SparkWitchFactions.WITCH` while remaining excluded from living-Witch counts.

- [ ] **Step 2: Run the server tests to verify RED**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  test --tests 'dev.caecorthus.sparkwitch.roles.special.wraith.*' --tests '*WraithVoicePluginContractTest'
```

Expected: FAIL because the server lifecycle and voice owner are absent.

- [ ] **Step 3: Port the server domain into the role-owned package**

Port the current 24 focused services without collapsing responsibilities. Replace SparkTraits component/role imports with Tasks 2-3 owners. `KillPlayer.BEFORE` captures the death/task/trait snapshot and the pre-death Last Stand triggered flag. `KillPlayer.AFTER` only queues the confirmed death. `WraithDeferredActivationService` processes that queue at `END_SERVER_TICK`, after every death listener, and first cancels a newly triggered Last Stand. Preserve this activation ordering:

```java
WraithDeathSnapshot snapshot = pending.remove(victim.getUuid());
if (traitBridge.didLastStandTriggerSince(victim, snapshot.lastStandTriggeredBefore())) return false;
if (!quota.hasCapacity(world) || !rules.passesChance(victim.getRandom().nextDouble())) return false;
if (!quota.tryConsume(world, victim.getUuid())) return false;
WraithBodyService.ensureDeathBody(victim, deathReason, snapshot.deathGameTime());
traitBridge.restore(victim, snapshot.traitSnapshot());
wraithComponent.activate(snapshot.alignment());
roleTransition.replaceAndAnnounce(victim, SparkWitchRoles.wraith());
sessionService.activatePlayer(victim);
taskService.restoreForActivation(victim, snapshot.taskSnapshot());
victim.getInventory().clear();
```

`WraithDeathSnapshot` captures `(int) victim.getServerWorld().getTime()` before death. Corpse deduplication and fallback creation use that captured value rather than the deferred tick's current time, so an existing Wathe corpse is found after time advances. Last Stand must resolve first. Eligible original alignments are civilian/killer only; neutral, escaped, and fell-out roles are excluded. Quota consumption is permanent per successful UUID. Promotion queues on the third task and executes at `END_SERVER_TICK`.

Preserve restricted effects, no collision, phasing, task-only mood, bilateral affect isolation, interaction allowlist, reconnect behavior, admin Spectator authority, and terminal cleanup. Promotion removes only Slowness, Blindness, and restricted interaction; active Wraith state and other protections remain.

- [ ] **Step 4: Implement the optional SparkTraits reflection bridge**

Resolve only these methods on `dev.caecorthus.sparktraits.api.SparkTraitsApi`:

```java
captureWraithTraitSnapshot(PlayerEntity.class)
restoreWraithTraitSnapshot(PlayerEntity.class, NbtCompound.class)
clearWraithTraits(PlayerEntity.class, boolean.class)
hasLastStandTriggeredThisRound(ServerWorld.class, UUID.class)
```

On absence/incompatibility, capture returns a new empty `NbtCompound`, restore and clear are no-ops, and Last Stand is reported as not triggered. Cache resolution, log a single concise compatibility warning, and never name an internal SparkTraits package in production source/resources.

- [ ] **Step 5: Move only outgoing Wraith voice suppression**

Add Simple Voice Chat as `modCompileOnly`, declare the `voicechat` entrypoint, and register `MicrophonePacketEvent` at `Integer.MAX_VALUE`. Cancel outgoing packets only when the sender is active Wraith. Do not move or duplicate Depression speaker/listener handling.

- [ ] **Step 6: Wire thin events, mixins, and cleanup**

Register Wraith services through `SparkWitchEvents`; keep mixins as adapters. Initialize the round quota exactly once from `gameComponent.getAllPlayers().size()` in `GameEvents.ON_FINISH_INITIALIZE`. Ensure round start, pre-death capture, confirmed-death queueing, deferred activation, task completion, join/reconnect, END_SERVER_TICK promotion, fall-out, and round finish each have one owner. Register Wraith's effective-faction resolver so saved GOOD/KILLER applies only when `isRestricted()`, and player-affect denial while active.

- [ ] **Step 7: Run focused verification and commit**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  test --tests 'dev.caecorthus.sparkwitch.roles.special.wraith.*' --tests '*WraithVoicePluginContractTest'
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  test verifyArchitecture compileJava
git diff --check
git add -A
git commit -m "feat: migrate Wraith server lifecycle"
```

Expected: server lifecycle, trait bridge, Curser faction, and voice tests PASS.

---

### Task 5: SparkWitch Client Presentation With Normal Chat And Inventory

**Repository:** `/Users/kricy/Documents/Codex-Projects/.codex-worktrees/wraith/SparkWitch`

**Files:**
- Create: `src/client/java/dev/caecorthus/sparkwitch/client/render/WraithClientState.java`
- Create: `src/client/java/dev/caecorthus/sparkwitch/client/render/WraithVisionRules.java`
- Create: `src/client/java/dev/caecorthus/sparkwitch/client/render/WraithViewerRules.java`
- Create: `src/client/java/dev/caecorthus/sparkwitch/client/render/WraithSteveProjection.java`
- Create: the approved Wraith client mixins under `src/client/java/dev/caecorthus/sparkwitch/client/mixin/`, excluding `WraithChatRestrictionMixin`
- Modify: existing SparkWitch grayscale/mood/round-text/player-render hooks only where needed for Wraith
- Modify: `src/client/resources/sparkwitch.client.mixins.json`
- Test: port/adapt Wraith client pure/source tests to `src/test/java/dev/caecorthus/sparkwitch/client/wraith/`
- Test: `src/test/java/dev/caecorthus/sparkwitch/client/wraith/WraithNormalUiContractTest.java`

**Interfaces:**
- Consumes: `SparkWitchApi.isWraithActive` and `isWraithRestricted`, plus `SparkWitchServerConnection.isConfirmedServer`.
- Produces: privacy/projection/render helpers with no direct component mutation.

- [ ] **Step 1: Port failing client tests**

Tests must cover full restricted grayscale `1.0`, promoted grayscale `0.5`, Steve projection, body skin/model, name hiding, held-item/cape/elytra privacy, hidden-body visibility, spectator bypass, instinct/outline veto, and server-confirmation gating.

The UI contract must explicitly assert both forbidden mixins are absent:

```java
assertFalse(clientMixinConfig.contains("WraithChatRestrictionMixin"));
assertFalse(clientMixinConfig.contains("WraithInventoryKeyMixin"));
assertFalse(productionSources.contains("setChatAllowed(false)"));
```

- [ ] **Step 2: Run the client tests to verify RED**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  test --tests 'dev.caecorthus.sparkwitch.client.wraith.*'
```

Expected: FAIL because Wraith client presentation is absent.

- [ ] **Step 3: Port presentation rules and thin mixins**

Port the later source behavior, replacing the connection gate with SparkWitch's server confirmation and using canonical SparkWitch state. Preserve this desaturation rule:

```java
public static float desaturation(PlayerEntity player) {
    if (!SparkWitchApi.isWraithActive(player)) return 0.0F;
    return SparkWitchApi.isWraithRestricted(player) ? 1.0F : 0.5F;
}
```

Do not add chat or inventory interception. Where SparkTraits previously reused its Depression shader hook, integrate Wraith as another caller of SparkWitch's client screen-effect hook without moving Depression assets or logic.

- [ ] **Step 4: Run client and architecture verification**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  test --tests 'dev.caecorthus.sparkwitch.client.wraith.*'
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  test verifyArchitecture compileClientJava
git diff --check
```

Expected: client tests and compilation PASS; text chat and inventory remain untouched.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: migrate Wraith client presentation"
```

---

### Task 6: SparkAssist Guidebook Ownership And Promotion Pages

**Repository:** `/Users/kricy/Documents/Codex-Projects/.codex-worktrees/wraith/SparkAssist`

**Files:**
- Create: `src/client/resources/assets/sparkassist/guidebook/roles/sparkwitch/wraith.json`
- Create: five promotion entries under the existing civilian, killer, and Witch Guidebook faction paths
- Remove: `src/client/resources/assets/sparkassist/guidebook/roles/sparktraits/wraith.json` if present on the branch
- Modify: Guidebook catalog/localization resources only where the current schema requires explicit registration
- Modify: `gradle.properties`
- Create/modify: `src/test/java/dev/caecorthus/sparkassist/guidebook/GuidebookWraithResourcesTest.java`

**Interfaces:**
- Consumes: canonical role ids and exact localized names from SparkWitch.
- Produces: Guidebook entries requiring `sparkwitch`, with no runtime dependency or Java integration.

- [ ] **Step 1: Create the isolated branch and port failing resource tests**

```bash
cd /Users/kricy/Documents/Codex-Projects/SparkAssist
git fetch origin
git worktree add /Users/kricy/Documents/Codex-Projects/.codex-worktrees/wraith/SparkAssist \
  -b codex/wraith-sparkassist-migration origin/main
```

Port the existing untracked Wraith test from the live checkout, then assert the new owner/path/id/required mod, exact role names, faction placement, and normal-chat wording. Assert none of the five promotion entries claims an active ability, skill, item, shop, mana pool, or loadout.

- [ ] **Step 2: Run the resource test to verify RED**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  test --tests '*GuidebookWraithResourcesTest'
```

Expected: FAIL because canonical SparkWitch Guidebook resources are absent.

- [ ] **Step 3: Create exact Guidebook resources**

Move the Wraith page to `roles/sparkwitch/wraith.json`, set `id` to `sparkwitch:wraith`, `sourceModId` to `sparkwitch`, and `requiredModIds` to `sparkwitch`. Preserve Wraith mechanics, change only the conflicting text-chat sentence so it states that text chat and incoming voice remain normal while outgoing voice is blocked.

Add identity-only pages for:

```text
sparkwitch:wind_spirit     civilian     风精灵
sparkwitch:guardian_angel  civilian     守护天使
sparkwitch:vendetta        civilian     仇杀客
sparkwitch:saboteur        killer       破坏者
sparkwitch:curser           witch        诅咒者
```

Curser's page identifies the Witch faction but does not advertise Witch skills, mana, shop access, or resurrection. Set `mod_version=0.1.3.7`.

- [ ] **Step 4: Verify and commit**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  test --tests '*GuidebookWraithResourcesTest'
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  test compileJava compileClientJava
git diff --check
git add -A
git commit -m "docs: move Wraith guidebook to SparkWitch"
```

Expected: resource and compilation checks PASS.

---

### Task 7: Cross-Repository Ownership And Release Verification

**Repositories:** all three isolated Wraith worktrees.

**Files:**
- Modify only files required by failures discovered in this task.
- Test: existing architecture/source/resource suites in all three repositories.

**Interfaces:**
- Consumes: completed Tasks 1-6.
- Produces: three independently buildable release branches with one Wraith runtime owner and compatible public seams.

- [ ] **Step 1: Run the complete SparkTraits build**

```bash
cd /Users/kricy/Documents/Codex-Projects/.codex-worktrees/wraith/SparkTraitsMigration
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 clean build
git diff --check
```

Expected: PASS at `0.1.9.10`.

- [ ] **Step 2: Run the complete SparkWitch build after SparkTraits finishes**

```bash
cd /Users/kricy/Documents/Codex-Projects/.codex-worktrees/wraith/SparkWitch
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 clean build
git diff --check
```

Expected: PASS at `0.1.6.1`.

- [ ] **Step 3: Run the complete SparkAssist build after SparkWitch finishes**

```bash
cd /Users/kricy/Documents/Codex-Projects/.codex-worktrees/wraith/SparkAssist
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 clean build
git diff --check
```

Expected: PASS at `0.1.3.7`.

- [ ] **Step 4: Inspect packaged ownership and metadata**

```bash
jar tf /Users/kricy/Documents/Codex-Projects/.codex-worktrees/wraith/SparkWitch/build/libs/sparkwitch-0.1.6.1.jar | rg 'Wraith|wraith|Curser|curser'
jar tf /Users/kricy/Documents/Codex-Projects/.codex-worktrees/wraith/SparkTraitsMigration/build/libs/sparktraits-0.1.9.10.jar | rg 'Wraith|wraith|Curser|curser'
unzip -p /Users/kricy/Documents/Codex-Projects/.codex-worktrees/wraith/SparkWitch/build/libs/sparkwitch-0.1.6.1.jar fabric.mod.json
```

Expected: SparkWitch contains the runtime/roles/components/client classes; SparkTraits contains only the public/compatibility bridge references and retired-id support; metadata has the optional-version rejection and voice entrypoint.

- [ ] **Step 5: Prove canonical ownership and forbidden boundaries**

```bash
rg -n 'sparktraits:(wraith|wind_spirit|guardian_angel|vendetta|saboteur|curser)' \
  /Users/kricy/Documents/Codex-Projects/.codex-worktrees/wraith/SparkWitch/src \
  /Users/kricy/Documents/Codex-Projects/.codex-worktrees/wraith/SparkAssist/src
rg -n 'sparktraits\.(impl|component)|WraithChatRestrictionMixin|WraithInventoryKeyMixin' \
  /Users/kricy/Documents/Codex-Projects/.codex-worktrees/wraith/SparkWitch/src
```

Expected: old ids occur only in exact read-boundary migration fixtures/code; internal SparkTraits packages and both forbidden mixins have no SparkWitch production hits.

- [ ] **Step 6: Commit any verification-driven fixes separately**

For each repository with a necessary fix:

```bash
git add -A
git commit -m "fix: complete Wraith migration contracts"
```

Do not create empty commits and do not fold unrelated dirt into any branch.

---

### Task 8: Safe Integration Handoff

**Repositories:** the shared SparkWitch, SparkTraits, and SparkAssist checkouts plus the three isolated branches.

**Files:**
- No new gameplay files; this task transfers reviewed commits only.

**Interfaces:**
- Consumes: clean reviewed branch tips and recorded base commits.
- Produces: a precise merge/cherry-pick handoff that preserves unrelated shared-checkout work.

- [ ] **Step 1: Record branch tips and dirty shared state**

```bash
git -C /Users/kricy/Documents/Codex-Projects/.codex-worktrees/wraith/SparkWitch status --short --branch
git -C /Users/kricy/Documents/Codex-Projects/.codex-worktrees/wraith/SparkTraitsMigration status --short --branch
git -C /Users/kricy/Documents/Codex-Projects/.codex-worktrees/wraith/SparkAssist status --short --branch
git -C /Users/kricy/Documents/Codex-Projects/SparkWitch status --short --branch
git -C /Users/kricy/Documents/Codex-Projects/SparkTraits status --short --branch
git -C /Users/kricy/Documents/Codex-Projects/SparkAssist status --short --branch
```

Expected: isolated branches are clean; shared checkouts may remain dirty and must not be reset or overwritten.

- [ ] **Step 2: Generate final review packages**

Use each branch's actual merge base, not `HEAD~1`, and request a broad code review covering lifecycle order, persistence, optional-mod reflection, Curser faction boundaries, client privacy, Guidebook ownership, and release metadata.

- [ ] **Step 3: Integrate only after final review is clean**

If a shared checkout can fast-forward without touching unrelated dirt, merge the reviewed branch normally. Otherwise leave the reviewed branches and exact commit lists as the handoff; do not copy files over dirty central registries or use destructive Git commands.

- [ ] **Step 4: Report exact verification and branch state**

Report branch names, commit ids, versions, full-build results, jar ownership inspection, any intentionally deferred live multiplayer checks, and whether commits were merged/pushed or remain isolated. Do not claim publish/deployment unless it actually occurred.
