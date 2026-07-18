# Prophet Death Omen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the good-aligned `sparkwitch:prophet` role and its `sparkwitch:death_omen` skill, then document the role in SparkAssist without disturbing concurrent role work.

**Architecture:** Reuse SparkWitch's existing role registration, skill key, assignment, HUD, shared cooldown, and player component. Fabric's server-side entity-load event records the UUID of each real Wathe corpse spawned while the 400-tick window is active; the owner-only synchronized UUID set drives a red client outline through Wathe's public highlight event. SparkAssist receives one authored ROLE entry, while Death Omen is excluded from automatic Witch Skill discovery.

**Tech Stack:** Java 21, Minecraft 1.21.1, Fabric API, Cardinal Components API, SparkFactionAPI, Wathe public events/entities, JUnit 5, Gson, Gradle.

## Global Constraints

- Treat both repositories as shared dirty worktrees. Re-read every touched file and its diff immediately before editing; never revert, overwrite, stage, renumber, or commit another agent's work.
- Do not change `build.gradle`, `gradle.properties`, `fabric.mod.json`, bundled jars, dependency floors, or release versions. At planning time SparkWitch is already moving through `0.1.5.8` work and SparkAssist remains `0.1.3.4`; those changes are outside Prophet scope.
- Preserve the relative order of every existing `WitchPlayerComponent.serverTick()` call and append all new sync/NBT fields after the live packet/schema tail.
- Gameplay constants are exact: initial cooldown `1200`, active window `400`, normal post-window cooldown `1800`, role color `0xD4AF37`, outline color `0xFF3030`, outline priority `90`.
- Only a real `PlayerBodyEntity` loaded after activation while its owner is marked dead qualifies. Bodies loaded before activation, chunk-loaded old bodies, fake bodies, and no-body deaths do not qualify.
- Wathe's body tracking range remains `128` blocks. Do not add a tracking packet, mixin, range extension, line-of-sight check, sound, item, screen, or second key binding.
- A dying Prophet's active window is cancelled and its deferred cooldown is cleared. Only normal 20-second completion starts the 90-second cooldown.
- Existing hidden-body rules and higher-priority highlight suppression remain authoritative.
- Do not modify SparkTraits. Prophet remains eligible for its ordinary civilian/universal traits by default.
- Do not add Death Omen to SparkAssist's SKILL tab. Its mechanics live only in Prophet's ROLE entry.
- Do not commit, stage, push, or bump a version unless the owner requests that separately.

---

### Task 0: Coordinate The Shared Integration Surface

**Files:**
- Inspect: `/Users/kricy/Documents/Codex-Projects/SparkWitch`
- Inspect: `/Users/kricy/Documents/Codex-Projects/SparkAssist`

**Interfaces:**
- Consumes: Current worktree ownership and the latest component/Guidebook tails.
- Produces: A conflict-free edit window and recorded live anchors for Tasks 1-5.

- [ ] **Step 1: Re-read repository routing and dirty state**

Run:

```bash
cd /Users/kricy/Documents/Codex-Projects/SparkWitch
sed -n '1,260p' CONTEXT.md
git status --short
git diff --name-only

cd /Users/kricy/Documents/Codex-Projects/SparkAssist
sed -n '1,260p' CONTEXT.md
git status --short
git diff --name-only
```

Expected: both worktrees may be dirty, but every path owned by another active agent is identified before Prophet edits begin.

- [ ] **Step 2: Record the live shared-schema tail and Guidebook order map**

Run:

```bash
cd /Users/kricy/Documents/Codex-Projects/SparkWitch
tail -n 45 src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerSyncCodec.java
tail -n 55 src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerNbtCodec.java
rg -n "Runtime.tick|tickCooldown|tickRegeneration|SaintAbilityService.tick" \
  src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerComponent.java

cd /Users/kricy/Documents/Codex-Projects/SparkAssist
rg -n '"order"' src/client/resources/assets/sparkassist/guidebook/roles -g '*.json'
```

Expected: note the final existing sync read/write field, final NBT field, and all occupied ROLE orders below `wathe:killer` at `300`. The planning snapshot has a collision at `250`; do not repair that unrelated collision in Prophet work.

- [ ] **Step 3: Coordinate before touching shared hotspots**

Do not begin an edit while another agent still owns any of these files:

```text
SparkWitchRoleRegistry.java
SparkWitchRoles.java
SparkWitchBuiltInSkills.java
WitchPlayerComponent.java
WitchPlayerNbtCodec.java
WitchPlayerSyncCodec.java
SparkWitchEvents.java
SparkWitchClient.java
assets/sparkwitch/lang/en_us.json
assets/sparkwitch/lang/zh_cn.json
SparkAssist Guidebook language and aggregate-count tests
```

Expected: the current owners have finished or explicitly handed off those files. Re-read each handed-off file instead of applying patches against this plan's line numbers.

---

### Task 1: Define And Register Prophet

**Files:**
- Create: `src/main/java/dev/caecorthus/sparkwitch/roles/civilian/prophet/ProphetRules.java`
- Create: `src/test/java/dev/caecorthus/sparkwitch/roles/civilian/prophet/ProphetRulesTest.java`
- Create: `src/test/java/dev/caecorthus/sparkwitch/roles/civilian/prophet/ProphetRegistrationSourceTest.java`
- Modify: `src/main/java/dev/caecorthus/sparkwitch/SparkWitchRoles.java`
- Modify: `src/main/java/dev/caecorthus/sparkwitch/registry/SparkWitchRoleRegistry.java`
- Modify: `src/main/resources/assets/sparkwitch/lang/zh_cn.json`
- Modify: `src/main/resources/assets/sparkwitch/lang/en_us.json`

**Interfaces:**
- Consumes: SparkFactionAPI's `FactionRoleDefinition` and Wathe's `Role` contract.
- Produces: `ProphetRules.ROLE_ID`, `DEATH_OMEN_ID`, exact tuning constants, `isProphet(Role)`, `shouldRecordLoadedBody(...)`, and `SparkWitchRoles.prophet()`.

- [ ] **Step 1: Write the failing pure contract test**

Create `ProphetRulesTest.java`:

```java
package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProphetRulesTest {
    @Test
    void exposesApprovedIdentityColorsAndTimings() {
        assertEquals(Identifier.of("sparkwitch", "prophet"), ProphetRules.ROLE_ID);
        assertEquals(Identifier.of("sparkwitch", "death_omen"), ProphetRules.DEATH_OMEN_ID);
        assertEquals(0xD4AF37, ProphetRules.ROLE_COLOR);
        assertEquals(0xFF3030, ProphetRules.CORPSE_HIGHLIGHT_COLOR);
        assertEquals(90, ProphetRules.CORPSE_HIGHLIGHT_PRIORITY);
        assertEquals(1200, ProphetRules.INITIAL_COOLDOWN_TICKS);
        assertEquals(400, ProphetRules.ACTIVE_TICKS);
        assertEquals(1800, ProphetRules.POST_COOLDOWN_TICKS);
    }

    @Test
    void identifiesOnlyTheSparkWitchProphet() {
        Role prophet = role(Identifier.of("sparkwitch", "prophet"));
        Role foreign = role(Identifier.of("example", "prophet"));

        assertTrue(ProphetRules.isProphet(prophet));
        assertFalse(ProphetRules.isProphet(foreign));
        assertFalse(ProphetRules.isProphet(null));
    }

    @Test
    void recordsOnlyCurrentTickBodiesWhoseOwnersAreActuallyDead() {
        assertTrue(ProphetRules.shouldRecordLoadedBody(900, 900, true));
        assertFalse(ProphetRules.shouldRecordLoadedBody(900, 899, true));
        assertFalse(ProphetRules.shouldRecordLoadedBody(900, 900, false));
    }

    private static Role role(Identifier id) {
        return new Role(id, 0, false, false, Role.MoodType.REAL, 200, false);
    }
}
```

- [ ] **Step 2: Write the failing registration and role-resource test**

Create `ProphetRegistrationSourceTest.java`:

```java
package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProphetRegistrationSourceTest {
    private static final Path REGISTRY = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/registry/SparkWitchRoleRegistry.java");
    private static final Path FACADE = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/SparkWitchRoles.java");
    private static final Path LANG = Path.of("src/main/resources/assets/sparkwitch/lang");

    @Test
    void registersProphetAsAnAlwaysEligibleCivilianRole() throws IOException {
        String registry = Files.readString(REGISTRY);
        assertTrue(registry.contains(
                "FactionRoleDefinition.builder(PROPHET_ID, FactionIds.CIVILIAN)"));
        assertTrue(registry.contains(".color(ProphetRules.ROLE_COLOR)"));
        assertTrue(registry.contains(".moodType(Role.MoodType.REAL)"));
        assertTrue(registry.contains(".maxSprintTime(GameConstants.getInTicks(0, 10))"));
        assertTrue(registry.contains(".canSeeTime(false)"));
        assertTrue(registry.contains(".nativeWatheFaction(Faction.CIVILIAN)"));
        assertTrue(registry.contains("|| role == prophet"));

        int registrationStart = registry.indexOf(
                "FactionRoleDefinition.builder(PROPHET_ID, FactionIds.CIVILIAN)");
        int registrationEnd = registry.indexOf(".build());", registrationStart);
        assertTrue(registrationStart >= 0 && registrationEnd > registrationStart);
        assertFalse(registry.substring(registrationStart, registrationEnd)
                .contains(".appearanceCondition("));

        int method = registry.indexOf("private static List<Role> assassinGuessRolesInOrder()");
        int listStart = registry.indexOf("return List.of(", method);
        int listEnd = registry.indexOf(");", listStart);
        String guessOrder = registry.substring(listStart, listEnd);
        assertTrue(guessOrder.contains("prophet"));
        assertTrue(guessOrder.indexOf("apprenticeWitch") < guessOrder.indexOf("prophet"));
        assertTrue(guessOrder.indexOf("prophet") < guessOrder.indexOf("murderousWitch"));
        assertTrue(Files.readString(FACADE).contains("public static Role prophet()"));
    }

    @Test
    void localizesTheApprovedRoleCopy() throws IOException {
        JsonObject chinese = parse("zh_cn");
        JsonObject english = parse("en_us");
        assertEquals("先知", chinese.get("announcement.role.prophet").getAsString());
        assertEquals("帮助好人阵营，预见死亡留下的痕迹。",
                chinese.get("announcement.goal.prophet").getAsString());
        assertEquals("Prophet", english.get("announcement.role.prophet").getAsString());
    }

    private static JsonObject parse(String locale) throws IOException {
        return JsonParser.parseString(Files.readString(LANG.resolve(locale + ".json")))
                .getAsJsonObject();
    }
}
```

- [ ] **Step 3: Run the focused tests and confirm the expected failure**

Run:

```bash
cd /Users/kricy/Documents/Codex-Projects/SparkWitch
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  test \
  --tests 'dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetRulesTest' \
  --tests 'dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetRegistrationSourceTest'
```

Expected: FAIL because `ProphetRules`, the role registration, and localization do not exist yet.

- [ ] **Step 4: Implement the pure Prophet contract**

Create `ProphetRules.java`:

```java
package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class ProphetRules {
    public static final Identifier ROLE_ID = SparkWitch.id("prophet");
    public static final Identifier DEATH_OMEN_ID = SparkWitch.id("death_omen");
    public static final int ROLE_COLOR = 0xD4AF37;
    public static final int CORPSE_HIGHLIGHT_COLOR = 0xFF3030;
    public static final int CORPSE_HIGHLIGHT_PRIORITY = 90;
    public static final int INITIAL_COOLDOWN_TICKS = 1200;
    public static final int ACTIVE_TICKS = 400;
    public static final int POST_COOLDOWN_TICKS = 1800;

    private ProphetRules() {
    }

    public static boolean isProphet(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    public static boolean shouldRecordLoadedBody(
            int currentGameTime,
            int bodyDeathGameTime,
            boolean ownerMarkedDead
    ) {
        return ownerMarkedDead && bodyDeathGameTime == currentGameTime;
    }
}
```

- [ ] **Step 5: Register Prophet without changing existing role ordering**

In `SparkWitchRoleRegistry.java`, add the import and append the Prophet members alongside the live civilian role block:

```java
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetRules;

public static final Identifier PROPHET_ID = ProphetRules.ROLE_ID;

private static Role prophet;

public static Role prophet() {
    ensureRegistered();
    return prophet;
}
```

Register it through SparkFactionAPI, without an appearance condition:

```java
prophet = SparkFactionApi.registerRole(FactionRoleDefinition.builder(PROPHET_ID, FactionIds.CIVILIAN)
        .color(ProphetRules.ROLE_COLOR)
        .moodType(Role.MoodType.REAL)
        .maxSprintTime(GameConstants.getInTicks(0, 10))
        .canSeeTime(false)
        .nativeWatheFaction(Faction.CIVILIAN)
        .build());
```

Add `prophet` once to `assassinGuessRolesInOrder()` beside the other good SparkWitch roles and append `|| role == prophet` to `isRegisteredSparkWitchRole(...)`. Do not move any existing list member.

In `SparkWitchRoles.java`, add:

```java
public static final Identifier PROPHET_ID = SparkWitchRoleRegistry.PROPHET_ID;

public static Role prophet() {
    return SparkWitchRoleRegistry.prophet();
}
```

- [ ] **Step 6: Add the exact role announcement copy**

Merge these entries into `zh_cn.json` beside the live civilian role entries:

```json
"announcement.role.prophet": "先知",
"announcement.title.prophet": "先知",
"announcement.goal.prophet": "帮助好人阵营，预见死亡留下的痕迹。",
"announcement.goals.prophet": "帮助好人阵营，预见死亡留下的痕迹。",
"announcement.win.prophet": "好人幸存了下来。",
"announcement.role.sparkwitch.prophet": "先知",
"announcement.goal.sparkwitch.prophet": "帮助好人阵营，预见死亡留下的痕迹。",
"announcement.win.sparkwitch.prophet": "好人幸存了下来。"
```

Merge the corresponding entries into `en_us.json`:

```json
"announcement.role.prophet": "Prophet",
"announcement.title.prophet": "Prophet",
"announcement.goal.prophet": "Help the passengers by foreseeing the traces left by death.",
"announcement.goals.prophet": "Help the passengers by foreseeing the traces left by death.",
"announcement.win.prophet": "The passengers survived.",
"announcement.role.sparkwitch.prophet": "Prophet",
"announcement.goal.sparkwitch.prophet": "Help the passengers by foreseeing the traces left by death.",
"announcement.win.sparkwitch.prophet": "The passengers survived."
```

- [ ] **Step 7: Run the focused tests to green**

Run the command from Step 3 again.

Expected: PASS for both Prophet tests, with no changes to role counts, SparkTraits, or existing role values.

---

### Task 2: Add Owner-Private Death Omen State

> **Live implementation note:** The concurrent Saint refactor established a role-owned state pattern, and direct `WitchPlayerComponent` behavior tests failed during CCA static initialization before reaching assertions. The final implementation therefore uses a plain-JUnit-testable `ProphetPlayerState`, with `WitchPlayerComponent` retaining only thin public delegates and shared deferred-cooldown ownership. The packet tail, NBT keys, public component API, and gameplay behavior below remain unchanged; `ProphetPlayerStateTest` and `ProphetComponentSchemaSourceTest` are the authoritative final tests.

**Files:**
- Create: `src/test/java/dev/caecorthus/sparkwitch/component/WitchPlayerProphetStateTest.java`
- Create: `src/test/java/dev/caecorthus/sparkwitch/roles/civilian/prophet/ProphetComponentSchemaSourceTest.java`
- Modify: `src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerComponent.java`
- Modify: `src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerNbtCodec.java`
- Modify: `src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerSyncCodec.java`

**Interfaces:**
- Consumes: `WitchPlayerComponent` shared cooldown/deferred-cooldown behavior.
- Produces: `beginDeathOmenWindow(int)`, `recordDeathOmenBody(UUID)`, `tickDeathOmenWindow()`, `cancelDeathOmenWindow()`, `isDeathOmenActive()`, and `isDeathOmenBody(UUID)`.

- [ ] **Step 1: Write the failing state and NBT round-trip test**

Create `WitchPlayerProphetStateTest.java`:

```java
package dev.caecorthus.sparkwitch.component;

import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitchPlayerProphetStateTest {
    private static final UUID BODY = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Test
    void tracksOnlyBodiesRecordedDuringAnActiveWindow() {
        WitchPlayerComponent component = new WitchPlayerComponent(null);
        assertFalse(component.recordDeathOmenBody(BODY));

        component.beginDeathOmenWindow(400);
        assertTrue(component.isDeathOmenActive());
        assertTrue(component.recordDeathOmenBody(BODY));
        assertFalse(component.recordDeathOmenBody(BODY));
        assertTrue(component.isDeathOmenBody(BODY));
        assertEquals(400, component.getActiveSkillWindowTicks());
    }

    @Test
    void normalCompletionStartsTheFullDeferredCooldown() {
        WitchPlayerComponent component = new WitchPlayerComponent(null);
        component.beginDeathOmenWindow(400);
        component.recordDeathOmenBody(BODY);
        component.deferCooldownUntilActiveWindowEnds(1800);

        for (int tick = 0; tick < 400; tick++) {
            component.tickDeathOmenWindow();
        }

        assertFalse(component.isDeathOmenActive());
        assertFalse(component.isDeathOmenBody(BODY));
        assertFalse(component.hasDeferredCooldown());
        assertEquals(1800, component.getCooldownTicks());
    }

    @Test
    void cancellationClearsTheWindowWithoutStartingCooldown() {
        WitchPlayerComponent component = new WitchPlayerComponent(null);
        component.beginDeathOmenWindow(400);
        component.recordDeathOmenBody(BODY);
        component.deferCooldownUntilActiveWindowEnds(1800);

        component.cancelDeathOmenWindow();

        assertFalse(component.isDeathOmenActive());
        assertFalse(component.isDeathOmenBody(BODY));
        assertFalse(component.hasDeferredCooldown());
        assertEquals(0, component.getCooldownTicks());
    }

    @Test
    void activeStateRoundTripsThroughNbt() {
        WitchPlayerComponent source = new WitchPlayerComponent(null);
        source.beginDeathOmenWindow(400);
        source.recordDeathOmenBody(BODY);
        source.deferCooldownUntilActiveWindowEnds(1800);
        NbtCompound tag = new NbtCompound();
        source.writeToNbt(tag, null);

        WitchPlayerComponent restored = new WitchPlayerComponent(null);
        restored.readFromNbt(tag, null);

        assertEquals(400, restored.getDeathOmenTicks());
        assertTrue(restored.isDeathOmenBody(BODY));
        assertTrue(restored.hasDeferredCooldown());

        restored.readFromNbt(new NbtCompound(), null);
        assertFalse(restored.isDeathOmenActive());
        assertFalse(restored.isDeathOmenBody(BODY));
        assertFalse(restored.hasDeferredCooldown());
    }
}
```

- [ ] **Step 2: Write the failing schema-tail test**

Create `ProphetComponentSchemaSourceTest.java`:

```java
package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProphetComponentSchemaSourceTest {
    private static final Path COMPONENT = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerComponent.java");
    private static final Path SYNC = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerSyncCodec.java");
    private static final Path NBT = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerNbtCodec.java");

    @Test
    void storesAndResetsTheProphetWindow() throws IOException {
        String component = Files.readString(COMPONENT);
        assertTrue(component.contains("int deathOmenTicks;"));
        assertTrue(component.contains("deathOmenBodyUuids.clear();"));
        int activeWindow = component.indexOf("public int getActiveSkillWindowTicks()");
        int nextMethod = component.indexOf("public boolean hasSkill()", activeWindow);
        assertTrue(activeWindow >= 0 && nextMethod > activeWindow);
        assertTrue(component.substring(activeWindow, nextMethod).contains("deathOmenTicks"));
    }

    @Test
    void appendsOwnerOnlyTicksAndBodyUuidsToSync() throws IOException {
        String sync = Files.readString(SYNC);
        int writeTicks = sync.indexOf("component.deathOmenTicks : 0");
        int writeCount = sync.indexOf("deathOmenBodies.size()", writeTicks);
        int readTicks = sync.indexOf("component.deathOmenTicks =", writeCount);
        int readCount = sync.indexOf("deathOmenBodyCount", readTicks);
        assertTrue(writeTicks >= 0 && writeCount > writeTicks);
        assertTrue(readTicks > writeCount && readCount > readTicks);
    }

    @Test
    void usesDedicatedNbtKeys() throws IOException {
        String nbt = Files.readString(NBT);
        assertTrue(nbt.contains("DeathOmenTicks"));
        assertTrue(nbt.contains("DeathOmenBodyUuids"));
    }
}
```

- [ ] **Step 3: Run the focused tests and confirm the expected failure**

Run:

```bash
cd /Users/kricy/Documents/Codex-Projects/SparkWitch
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  test \
  --tests 'dev.caecorthus.sparkwitch.component.WitchPlayerProphetStateTest' \
  --tests 'dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetComponentSchemaSourceTest'
```

Expected: FAIL because the Prophet component state and codec tail do not exist.

- [ ] **Step 4: Append the component state and narrow operations**

After the live final field in `WitchPlayerComponent`, append:

```java
int deathOmenTicks;
final Set<UUID> deathOmenBodyUuids = new LinkedHashSet<>();
```

Add `java.util.LinkedHashSet`, `java.util.Set`, and `java.util.UUID` imports. Add these methods:

```java
public int getDeathOmenTicks() {
    return deathOmenTicks;
}

public boolean isDeathOmenActive() {
    return deathOmenTicks > 0;
}

public boolean isDeathOmenBody(UUID bodyUuid) {
    return bodyUuid != null && deathOmenBodyUuids.contains(bodyUuid);
}

public void beginDeathOmenWindow(int durationTicks) {
    deathOmenTicks = Math.max(0, durationTicks);
    deathOmenBodyUuids.clear();
    sync();
}

public boolean recordDeathOmenBody(UUID bodyUuid) {
    if (deathOmenTicks <= 0 || bodyUuid == null || !deathOmenBodyUuids.add(bodyUuid)) {
        return false;
    }
    sync();
    return true;
}

public void tickDeathOmenWindow() {
    if (deathOmenTicks <= 0) {
        return;
    }
    deathOmenTicks--;
    if (deathOmenTicks == 0) {
        deathOmenBodyUuids.clear();
        startDeferredCooldownNow();
        sync();
    } else if (deathOmenTicks % 20 == 0) {
        sync();
    }
}

public void cancelDeathOmenWindow() {
    if (deathOmenTicks <= 0 && deathOmenBodyUuids.isEmpty()) {
        return;
    }
    deathOmenTicks = 0;
    deathOmenBodyUuids.clear();
    deferredCooldownTicks = 0;
    sync();
}
```

Extend `getActiveSkillWindowTicks()` without changing existing operands:

```java
return Math.max(Math.max(existingWindowTicks, ninjaParryTicks), deathOmenTicks);
```

If the live method has gained another tail since planning, preserve it and take the maximum of that live result and `deathOmenTicks` instead.

Add `deathOmenTicks == 0` and `deathOmenBodyUuids.isEmpty()` to the `clear()` fast-path guard, then set the ticks to zero and clear the set in the reset block.

- [ ] **Step 5: Append the owner-only sync shape after the live tail**

In `WitchPlayerSyncCodec.write(...)`, after the then-current last field, append:

```java
Set<UUID> deathOmenBodies = ownerVisible && component.deathOmenTicks > 0
        ? component.deathOmenBodyUuids
        : Set.of();
buf.writeVarInt(ownerVisible ? component.deathOmenTicks : 0);
buf.writeVarInt(deathOmenBodies.size());
deathOmenBodies.forEach(buf::writeUuid);
```

In `read(...)`, after the matching live read tail, append:

```java
component.deathOmenTicks = Math.max(0, buf.readVarInt());
component.deathOmenBodyUuids.clear();
int deathOmenBodyCount = Math.max(0, buf.readVarInt());
for (int index = 0; index < deathOmenBodyCount; index++) {
    UUID bodyUuid = buf.readUuid();
    if (component.deathOmenTicks > 0) {
        component.deathOmenBodyUuids.add(bodyUuid);
    }
}
```

Add `java.util.Set` and `java.util.UUID` imports. Spectators continue to receive the existing visible state but receive `0, 0` for this private list.

- [ ] **Step 6: Append NBT persistence without changing existing keys**

Add `NbtList`, `NbtString`, `Set`, and `UUID` imports to `WitchPlayerNbtCodec`. At the end of `write(...)`, append:

```java
if (component.deathOmenTicks > 0) {
    tag.putInt("DeathOmenTicks", component.deathOmenTicks);
    tag.put("DeathOmenBodyUuids", toUuidNbt(component.deathOmenBodyUuids));
}
```

At the end of `read(...)`, append:

```java
component.deathOmenTicks = tag.contains("DeathOmenTicks", NbtElement.NUMBER_TYPE)
        ? Math.max(0, tag.getInt("DeathOmenTicks"))
        : 0;
component.deathOmenBodyUuids.clear();
if (component.deathOmenTicks > 0) {
    readUuidNbt(
            tag.getList("DeathOmenBodyUuids", NbtElement.STRING_TYPE),
            component.deathOmenBodyUuids
    );
}
```

Add these codec-local helpers:

```java
private static NbtList toUuidNbt(Set<UUID> uuids) {
    NbtList list = new NbtList();
    uuids.stream().map(UUID::toString).map(NbtString::of).forEach(list::add);
    return list;
}

private static void readUuidNbt(NbtList list, Set<UUID> destination) {
    for (int index = 0; index < list.size(); index++) {
        try {
            destination.add(UUID.fromString(list.getString(index)));
        } catch (IllegalArgumentException ignored) {
            // Ignore malformed saved UUIDs so one stale field cannot block a world load.
            // 忽略损坏的存档 UUID，避免单个旧字段阻止世界加载。
        }
    }
}
```

- [ ] **Step 7: Run the focused component tests to green**

Run the command from Step 3 again.

Expected: PASS. The normal-completion assertion must be exactly `1800`, and cancellation must leave both current and deferred cooldown at zero.

---

### Task 3: Wire Skill Activation And Exact Server Corpse Collection

> **Live implementation note:** Task 2 replaced the planned CCA-bound `WitchPlayerProphetStateTest` with `ProphetPlayerStateTest`; final Task 3 verification uses the current Prophet package tests rather than the superseded class name in the original command sketch.

**Files:**
- Create: `src/main/java/dev/caecorthus/sparkwitch/roles/civilian/prophet/ProphetSkillService.java`
- Create: `src/main/java/dev/caecorthus/sparkwitch/roles/civilian/prophet/ProphetRuntime.java`
- Create: `src/test/java/dev/caecorthus/sparkwitch/roles/civilian/prophet/ProphetRuntimeIntegrationSourceTest.java`
- Modify: `src/main/java/dev/caecorthus/sparkwitch/skill/SparkWitchBuiltInSkills.java`
- Modify: `src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerComponent.java`
- Modify: `src/main/java/dev/caecorthus/sparkwitch/impl/SparkWitchEvents.java`
- Modify: `src/main/resources/assets/sparkwitch/lang/zh_cn.json`
- Modify: `src/main/resources/assets/sparkwitch/lang/en_us.json`

**Interfaces:**
- Consumes: Task 1 constants and Task 2 component operations.
- Produces: The role-selected Death Omen skill, exact `ENTITY_LOAD` collection, death/role cancellation, and post-cooldown lifecycle.

- [ ] **Step 1: Write the failing runtime integration test**

Create `ProphetRuntimeIntegrationSourceTest.java`:

```java
package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProphetRuntimeIntegrationSourceTest {
    private static final Path RUNTIME = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/roles/civilian/prophet/ProphetRuntime.java");
    private static final Path SKILLS = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/skill/SparkWitchBuiltInSkills.java");
    private static final Path COMPONENT = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerComponent.java");
    private static final Path EVENTS = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/impl/SparkWitchEvents.java");

    @Test
    void collectsRealBodiesAtTheServerSpawnBoundary() throws IOException {
        String runtime = Files.readString(RUNTIME);
        assertTrue(runtime.contains("ServerEntityEvents.ENTITY_LOAD.register"));
        assertTrue(runtime.contains("entity instanceof PlayerBodyEntity body"));
        assertTrue(runtime.contains("body.getDeathGameTime()"));
        assertTrue(runtime.contains("gameComponent.isPlayerDead(body.getPlayerUuid())"));
        assertTrue(runtime.contains("recordDeathOmenBody(body.getUuid())"));
        assertFalse(runtime.contains("KillPlayer.BEFORE.register"));
    }

    @Test
    void cancelsADeadOrReassignedProphetWithoutPostCooldown() throws IOException {
        String runtime = Files.readString(RUNTIME);
        assertTrue(runtime.contains("KillPlayer.AFTER.register"));
        assertTrue(runtime.contains("cancelDeathOmenWindow()"));
        assertTrue(Files.readString(EVENTS).contains("ProphetRuntime.assignForRole"));
    }

    @Test
    void registersTheRoleOwnedSkillAndTicksAfterSharedCooldown() throws IOException {
        String skills = Files.readString(SKILLS);
        assertTrue(skills.contains("ProphetRules.DEATH_OMEN_ID"));
        assertTrue(skills.contains("ProphetRules.INITIAL_COOLDOWN_TICKS"));
        assertTrue(skills.contains("ProphetRules.POST_COOLDOWN_TICKS"));
        assertTrue(skills.contains("ProphetSkillService::use"));

        String component = Files.readString(COMPONENT);
        int cooldown = component.indexOf("tickCooldown()");
        int prophet = component.indexOf("ProphetRuntime.tick(serverPlayer, this)", cooldown);
        int mana = component.indexOf("WitchManaService.tickRegeneration", prophet);
        assertTrue(cooldown >= 0 && prophet > cooldown && mana > prophet);
    }
}
```

- [ ] **Step 2: Run the runtime test and confirm the expected failure**

Run:

```bash
cd /Users/kricy/Documents/Codex-Projects/SparkWitch
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  test --tests 'dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetRuntimeIntegrationSourceTest'
```

Expected: FAIL because the service, runtime, skill registration, and tick call are absent.

- [ ] **Step 3: Implement server-authoritative activation**

Create `ProphetSkillService.java`:

```java
package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;

public final class ProphetSkillService {
    private ProphetSkillService() {
    }

    public static WitchSkillUseResult use(WitchSkillUseContext context) {
        if (!ProphetRules.isProphet(context.role())) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.unavailable");
        }
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(context.player());
        component.beginDeathOmenWindow(ProphetRules.ACTIVE_TICKS);
        return WitchSkillUseResult.successAfterActiveWindow(
                ProphetRules.POST_COOLDOWN_TICKS,
                "message.sparkwitch.skill.death_omen.activated"
        );
    }
}
```

- [ ] **Step 4: Implement exact spawn-time body registration and cancellation**

Create `ProphetRuntime.java`:

```java
package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class ProphetRuntime {
    private static boolean registered;

    private ProphetRuntime() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerEntityEvents.ENTITY_LOAD.register(ProphetRuntime::onEntityLoad);
        KillPlayer.AFTER.register(ProphetRuntime::afterKill);
    }

    public static void assignForRole(ServerPlayerEntity player, Role role) {
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        if (component.isDeathOmenActive() && !ProphetRules.isProphet(role)) {
            component.cancelDeathOmenWindow();
        }
    }

    public static void tick(ServerPlayerEntity player, WitchPlayerComponent component) {
        if (!component.isDeathOmenActive()) {
            return;
        }
        Role role = GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player);
        if (!ProphetRules.isProphet(role) || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            component.cancelDeathOmenWindow();
            return;
        }
        component.tickDeathOmenWindow();
    }

    private static void onEntityLoad(Entity entity, ServerWorld world) {
        if (!(entity instanceof PlayerBodyEntity body)) {
            return;
        }
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(world);
        if (!ProphetRules.shouldRecordLoadedBody(
                (int) world.getTime(),
                body.getDeathGameTime(),
                gameComponent.isPlayerDead(body.getPlayerUuid()))) {
            return;
        }
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            WitchPlayerComponent component = WitchPlayerComponent.KEY.get(viewer);
            if (component.isDeathOmenActive()
                    && ProphetRules.isProphet(gameComponent.getRole(viewer))
                    && GameFunctions.isPlayerPlayingAndAlive(viewer)) {
                component.recordDeathOmenBody(body.getUuid());
            }
        }
    }

    private static void afterKill(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(victim);
        if (component.isDeathOmenActive()) {
            component.cancelDeathOmenWindow();
        }
    }
}
```

The two event comments should be concise and bilingual because this ordering is a non-obvious external contract: Wathe marks dead before `spawnEntity`, and `KillPlayer.AFTER` occurs later.

- [ ] **Step 5: Register the skill and lifecycle hooks**

In `SparkWitchBuiltInSkills.register()`, add this definition beside the other civilian role-owned skills without moving existing registrations:

```java
WitchSkillRegistry.register(new WitchSkillDefinition(
        ProphetRules.DEATH_OMEN_ID,
        ProphetRules.ROLE_COLOR,
        1,
        ProphetRules.INITIAL_COOLDOWN_TICKS,
        ProphetRules.POST_COOLDOWN_TICKS,
        0,
        context -> ProphetRules.isProphet(context.role()),
        ProphetSkillService::use
));
```

In `SparkWitchEvents.register()`, call `ProphetRuntime.register()` once with the other feature registrations. In the existing `RoleAssigned.EVENT` server-player branch, add:

```java
ProphetRuntime.assignForRole(serverPlayer, role);
```

In `WitchPlayerComponent.serverTick()`, insert this after the live `tickCooldown()` call and before `WitchManaService.tickRegeneration(...)`:

```java
if (serverPlayer != null) {
    ProphetRuntime.tick(serverPlayer, this);
}
```

Merge it into the existing post-cooldown `if (serverPlayer != null)` block if that preserves the live code more cleanly. Do not move any existing call.

- [ ] **Step 6: Add skill localization**

Merge into `zh_cn.json`:

```json
"skill.sparkwitch.death_omen.name": "死亡预兆",
"skill.sparkwitch.death_omen.description": "开局冷却 60 秒。开启后持续 20 秒，并在 128 格内隔墙红色高亮期间新产生的尸体；完整结束后冷却 90 秒。",
"message.sparkwitch.skill.death_omen.activated": "死亡预兆已经降临。"
```

Merge into `en_us.json`:

```json
"skill.sparkwitch.death_omen.name": "Death Omen",
"skill.sparkwitch.death_omen.description": "After a 60-second initial cooldown, reveal newly created bodies through walls within 128 blocks for 20 seconds. A full omen starts a 90-second cooldown.",
"message.sparkwitch.skill.death_omen.activated": "Death Omen has begun."
```

- [ ] **Step 7: Run the runtime and prior Prophet tests to green**

Run:

```bash
./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  test \
  --tests 'dev.caecorthus.sparkwitch.roles.civilian.prophet.*' \
  --tests 'dev.caecorthus.sparkwitch.component.WitchPlayerProphetStateTest'
```

Expected: PASS. The source ordering test must show shared cooldown, then Prophet runtime, then mana; this prevents the new `1800` cooldown from being decremented on the same tick it starts.

---

### Task 4: Publish The Client Corpse Outline

**Files:**
- Create: `src/client/java/dev/caecorthus/sparkwitch/client/hooks/ProphetCorpseHighlightClientHooks.java`
- Create: `src/test/java/dev/caecorthus/sparkwitch/client/ProphetCorpseHighlightClientHooksSourceTest.java`
- Modify: `src/client/java/dev/caecorthus/sparkwitch/client/SparkWitchClient.java`

**Interfaces:**
- Consumes: Owner-synchronized component state and Wathe's `GetInstinctHighlight.EVENT`.
- Produces: A red, keyless, through-wall outline for exact qualifying body UUIDs already tracked by the client.

- [ ] **Step 1: Write the failing client-hook source test**

Create `ProphetCorpseHighlightClientHooksSourceTest.java`:

```java
package dev.caecorthus.sparkwitch.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProphetCorpseHighlightClientHooksSourceTest {
    private static final Path HOOK = Path.of(
            "src/client/java/dev/caecorthus/sparkwitch/client/hooks/ProphetCorpseHighlightClientHooks.java");
    private static final Path CLIENT = Path.of(
            "src/client/java/dev/caecorthus/sparkwitch/client/SparkWitchClient.java");

    @Test
    void usesThePublicAlwaysOnBodyHighlightEvent() throws IOException {
        String hook = Files.readString(HOOK);
        assertTrue(hook.contains("GetInstinctHighlight.EVENT.register"));
        assertTrue(hook.contains("target instanceof PlayerBodyEntity body"));
        assertTrue(hook.contains("isDeathOmenBody(body.getUuid())"));
        assertTrue(hook.contains("HighlightResult.always"));
        assertTrue(hook.contains("ProphetRules.CORPSE_HIGHLIGHT_PRIORITY"));
        assertTrue(hook.contains("HiddenBodiesWorldComponent"));
        assertFalse(hook.contains("squaredDistanceTo"));
        assertFalse(hook.contains("canSee("));
    }

    @Test
    void registersFromTheSparkWitchClientInitializer() throws IOException {
        assertTrue(Files.readString(CLIENT).contains(
                "ProphetCorpseHighlightClientHooks.register()"));
    }
}
```

- [ ] **Step 2: Run the focused client test and confirm the expected failure**

Run:

```bash
cd /Users/kricy/Documents/Codex-Projects/SparkWitch
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  test --tests 'dev.caecorthus.sparkwitch.client.ProphetCorpseHighlightClientHooksSourceTest'
```

Expected: FAIL because the client hook and initializer call do not exist.

- [ ] **Step 3: Implement the fail-closed public highlight adapter**

Create `ProphetCorpseHighlightClientHooks.java`:

```java
package dev.caecorthus.sparkwitch.client.hooks;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetRules;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.scavenger.HiddenBodiesWorldComponent;

public final class ProphetCorpseHighlightClientHooks {
    private ProphetCorpseHighlightClientHooks() {
    }

    public static void register() {
        GetInstinctHighlight.EVENT.register(ProphetCorpseHighlightClientHooks::highlightBody);
    }

    private static GetInstinctHighlight.HighlightResult highlightBody(Entity target) {
        if (!SparkWitchServerConnection.isConfirmedServer()
                || !(target instanceof PlayerBodyEntity body)) {
            return null;
        }
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null
                || !ProphetRules.isProphet(
                GameWorldComponent.KEY.get(viewer.getWorld()).getRole(viewer))
                || !GameFunctions.isPlayerPlayingAndAlive(viewer)
                || GameFunctions.isPlayerSpectatingOrCreative(viewer)
                || HiddenBodiesWorldComponent.KEY.get(viewer.getWorld()).isHidden(body.getPlayerUuid())) {
            return null;
        }
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(viewer);
        if (!component.isDeathOmenActive() || !component.isDeathOmenBody(body.getUuid())) {
            return null;
        }
        return GetInstinctHighlight.HighlightResult.always(
                ProphetRules.CORPSE_HIGHLIGHT_COLOR,
                ProphetRules.CORPSE_HIGHLIGHT_PRIORITY
        );
    }
}
```

Priority `90` is intentionally below Wathe's high-priority skip (`100`) and SparkWitch suppression (`102`), so Death Omen does not bypass existing hard suppression.

- [ ] **Step 4: Register the hook without adding a mixin**

Import `ProphetCorpseHighlightClientHooks` in `SparkWitchClient` and add this once in `onInitializeClient()` beside the other hook registrations:

```java
ProphetCorpseHighlightClientHooks.register();
```

Do not change either mixin JSON file and do not add a custom packet.

- [ ] **Step 5: Run client tests and compilation to green**

Run:

```bash
./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  test --tests 'dev.caecorthus.sparkwitch.client.ProphetCorpseHighlightClientHooksSourceTest' \
  compileClientJava
```

Expected: PASS. There is no explicit distance or visibility check; Wathe's `PlayerBodyEntity` tracking limit supplies the accepted 128-block boundary.

---

### Task 5: Add Prophet To SparkAssist Guidebook

**Files:**
- Create: `/Users/kricy/Documents/Codex-Projects/SparkAssist/src/client/resources/assets/sparkassist/guidebook/roles/sparkwitch/prophet.json`
- Create: `/Users/kricy/Documents/Codex-Projects/SparkAssist/src/test/java/dev/caecorthus/sparkassist/guidebook/GuidebookProphetResourcesTest.java`
- Create: `/Users/kricy/Documents/Codex-Projects/SparkAssist/src/test/java/dev/caecorthus/sparkassist/guidebook/GuidebookDiscoveryRulesTest.java`
- Modify: `/Users/kricy/Documents/Codex-Projects/SparkAssist/src/main/java/dev/caecorthus/sparkassist/guidebook/GuidebookDiscoveryRules.java`
- Modify: `/Users/kricy/Documents/Codex-Projects/SparkAssist/src/client/java/dev/caecorthus/sparkassist/client/guidebook/GuidebookRuntimeCatalog.java`
- Modify: `/Users/kricy/Documents/Codex-Projects/SparkAssist/src/client/resources/assets/sparkassist/lang/zh_cn.json`
- Modify: `/Users/kricy/Documents/Codex-Projects/SparkAssist/src/client/resources/assets/sparkassist/lang/en_us.json`
- Reconcile only after handoff: `/Users/kricy/Documents/Codex-Projects/SparkAssist/src/test/java/dev/caecorthus/sparkassist/guidebook/GuidebookAuthoredResourcesTest.java`
- Reconcile only after handoff: `/Users/kricy/Documents/Codex-Projects/SparkAssist/src/test/java/dev/caecorthus/sparkassist/guidebook/GuidebookLocalizationResourcesTest.java`

**Interfaces:**
- Consumes: Approved Chinese copy and the live Guidebook order map from Task 0.
- Produces: One authored Prophet ROLE entry and a tab-scoped exclusion for `sparkwitch:death_omen`.

- [ ] **Step 1: Select an unused order without changing other agents' entries**

Re-run the order scan from Task 0. Use `270` if it remains unused. If another completed role has taken `270`, use the first unused value from `280`, then `290`. If all three are occupied, stop and coordinate rather than renumbering Tarot Reader, Perfumer, or any other role.

Expected: exactly one unused Prophet order greater than Apprentice Witch's `240` and less than Killer's `300` is selected. The current candidate is `270`.

- [ ] **Step 2: Write the failing Guidebook resource test**

Create `GuidebookProphetResourcesTest.java`:

```java
package dev.caecorthus.sparkassist.guidebook;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.caecorthus.sparkassist.guidebook.content.GuidebookBlockType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuidebookProphetResourcesTest {
    private static final Path ROOT = Path.of(
            "src/client/resources/assets/sparkassist/guidebook");
    private static final Path PROPHET = ROOT.resolve("roles/sparkwitch/prophet.json");
    private static final Path APPRENTICE = ROOT.resolve("roles/sparkwitch/apprentice_witch.json");
    private static final Path KILLER = ROOT.resolve("roles/wathe/killer.json");
    private static final Path LANG = Path.of("src/client/resources/assets/sparkassist/lang");

    @Test
    void documentsProphetInsideTheCivilianRoleBlock() throws IOException {
        GuidebookEntry prophet = parse(PROPHET).find("sparkwitch:prophet").orElseThrow();
        GuidebookEntry apprentice = parse(APPRENTICE)
                .find("sparkwitch:apprentice_witch").orElseThrow();
        GuidebookEntry killer = parse(KILLER).find("wathe:killer").orElseThrow();

        assertEquals(GuidebookTab.ROLE, prophet.tab());
        assertEquals("sparkwitch", prophet.sourceModId());
        assertEquals("announcement.role.prophet", prophet.nameKey());
        assertEquals("guidebook.sparkassist.content.role.overview", prophet.summaryKey());
        assertEquals(List.of(), prophet.ownerRoleIds());
        assertEquals(List.of("sparkwitch"), prophet.requiredModIds());
        assertEquals(0xD4AF37, prophet.color());
        assertTrue(apprentice.order() < prophet.order());
        assertTrue(prophet.order() < killer.order());
        assertFalse(prophet.pages().stream()
                .flatMap(page -> page.blocks().stream())
                .anyMatch(block -> block.type() == GuidebookBlockType.QUOTE));
        assertEquals(List.of(
                "死亡预兆",
                "开局进入 60 秒冷却。按下技能键后，死亡预兆持续 20 秒。",
                "只会红色高亮生效期间新产生的尸体；技能开启前已有的尸体不会被标记。",
                "高亮不受墙体阻挡，但只能看见 128 格内的尸体。先知死亡时，效果立即终止。",
                "效果结束后进入 90 秒冷却。"
        ), prophet.pages().stream()
                .flatMap(page -> page.blocks().stream())
                .flatMap(block -> block.runs().stream())
                .map(run -> run.text())
                .toList());

        long entriesAtProphetOrder;
        try (var paths = Files.walk(ROOT.resolve("roles"))) {
            entriesAtProphetOrder = paths
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .flatMap(path -> parse(path).entries().stream())
                    .filter(entry -> entry.tab() == GuidebookTab.ROLE)
                    .filter(entry -> entry.order() == prophet.order())
                    .count();
        }
        assertEquals(1, entriesAtProphetOrder);
    }

    @Test
    void bothLocalesNameProphetInChinese() throws IOException {
        for (String locale : List.of("zh_cn", "en_us")) {
            JsonObject translations = JsonParser.parseString(
                    Files.readString(LANG.resolve(locale + ".json")))
                    .getAsJsonObject();
            assertEquals("先知", translations.get("announcement.role.prophet").getAsString());
        }
    }

    private static GuidebookCatalog parse(Path path) {
        try {
            return GuidebookCatalog.parse(Files.readString(path));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }
}
```

- [ ] **Step 3: Write the failing discovery exclusion test**

Create `GuidebookDiscoveryRulesTest.java`:

```java
package dev.caecorthus.sparkassist.guidebook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuidebookDiscoveryRulesTest {
    @Test
    void excludesRoleOwnedSkillsOnlyFromTheSkillTab() {
        assertFalse(GuidebookDiscoveryRules.includes(
                GuidebookTab.SKILL, "sparkwitch:death_omen"));
        assertFalse(GuidebookDiscoveryRules.includes(
                GuidebookTab.SKILL, "sparkwitch:pig_chase"));
        assertTrue(GuidebookDiscoveryRules.includes(
                GuidebookTab.SKILL, "sparkwitch:mighty_force"));
        assertTrue(GuidebookDiscoveryRules.includes(
                GuidebookTab.ROLE, "sparkwitch:death_omen"));
    }
}
```

- [ ] **Step 4: Run the focused SparkAssist tests and confirm the expected failure**

Run:

```bash
cd /Users/kricy/Documents/Codex-Projects/SparkAssist
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  test \
  --tests 'dev.caecorthus.sparkassist.guidebook.GuidebookProphetResourcesTest' \
  --tests 'dev.caecorthus.sparkassist.guidebook.GuidebookDiscoveryRulesTest'
```

Expected: FAIL because the authored resource, localization, and Death Omen exclusion do not exist.

- [ ] **Step 5: Add the authored Prophet ROLE entry**

Create `prophet.json`, using the order selected in Step 1 (`270` in the current snapshot):

```json
{
  "entries": [
    {
      "id": "sparkwitch:prophet",
      "tab": "ROLE",
      "sourceModId": "sparkwitch",
      "nameKey": "announcement.role.prophet",
      "summaryKey": "guidebook.sparkassist.content.role.overview",
      "pages": [
        {
          "blocks": [
            {
              "type": "section",
              "text": "死亡预兆",
              "tone": "danger"
            },
            {
              "type": "bullet",
              "text": "开局进入 60 秒冷却。按下技能键后，死亡预兆持续 20 秒。"
            },
            {
              "type": "bullet",
              "text": "只会红色高亮生效期间新产生的尸体；技能开启前已有的尸体不会被标记。"
            },
            {
              "type": "bullet",
              "text": "高亮不受墙体阻挡，但只能看见 128 格内的尸体。先知死亡时，效果立即终止。"
            },
            {
              "type": "bullet",
              "text": "效果结束后进入 90 秒冷却。"
            }
          ]
        }
      ],
      "ownerRoleIds": [],
      "requiredModIds": [
        "sparkwitch"
      ],
      "color": "#D4AF37",
      "order": 270
    }
  ]
}
```

If Step 1 selected `280` or `290`, change only the final numeric order and let the test read that value dynamically.

- [ ] **Step 6: Exclude both role-owned skills from SKILL discovery**

Replace the single Pig Chase constant in `GuidebookDiscoveryRules` with:

```java
private static final Set<String> EXCLUDED_SKILL_IDS = Set.of(
        "sparkwitch:death_omen",
        "sparkwitch:pig_chase"
);
```

Add `java.util.Set`, then keep the exclusion tab-scoped:

```java
public static boolean includes(GuidebookTab tab, String id) {
    return tab != GuidebookTab.SKILL || !EXCLUDED_SKILL_IDS.contains(id);
}
```

Generalize the two comments in `GuidebookRuntimeCatalog` to:

```java
// Role-owned active skills are documented with their roles, not in the witch-skill index.
// 职业专属主动技能随职业说明，不进入魔女技能目录。
```

- [ ] **Step 7: Add SparkAssist's Chinese fallback role name**

Merge this exact entry into both `zh_cn.json` and `en_us.json`, alphabetically beside the other role names:

```json
"announcement.role.prophet": "先知"
```

- [ ] **Step 8: Reconcile aggregate counts only after the shared files are handed off**

Run:

```bash
cd /Users/kricy/Documents/Codex-Projects/SparkAssist
find src/client/resources/assets/sparkassist/guidebook -name '*.json' | wc -l
rg -l '"tab"[[:space:]]*:[[:space:]]*"ROLE"' \
  src/client/resources/assets/sparkassist/guidebook -g '*.json' | wc -l
```

If the live aggregate tests have already been reconciled for Tarot Reader, Perfumer, Saint, Hunter, and Orthopedist, increase only the total authored-resource and ROLE counts by the single new Prophet resource. Preserve every other agent's content assertions. If those tests are still inconsistent before Prophet is counted, return them to their current owner instead of guessing at a combined total.

- [ ] **Step 9: Run the focused Guidebook tests to green**

Run the command from Step 4 again.

Expected: PASS. Prophet has a unique order between Apprentice Witch and Killer, and `death_omen` is absent only from SKILL discovery.

---

### Task 6: Verify Both Mods And Perform Live Acceptance

**Files:**
- Verify: all Prophet files from Tasks 1-5
- Inspect: built SparkWitch and SparkAssist jars

**Interfaces:**
- Consumes: Complete implementation and Guidebook resources.
- Produces: Sequential build evidence and live proof of the approved gameplay boundaries.

- [ ] **Step 1: Run the complete SparkWitch verification entry points**

Run:

```bash
cd /Users/kricy/Documents/Codex-Projects/SparkWitch
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  test verifyArchitecture compileJava compileClientJava
./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  clean build
git diff --check
```

Expected: both Gradle invocations end in `BUILD SUCCESSFUL`; `git diff --check` prints nothing. Do not overlap this build with SparkAssist or SparkFactionAPI builds.

- [ ] **Step 2: Run the complete SparkAssist verification entry points**

Run only after SparkWitch finishes:

```bash
cd /Users/kricy/Documents/Codex-Projects/SparkAssist
./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  test verifyArchitecture verifyDistributionMetadata compileJava compileClientJava
./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
  clean build
git diff --check
```

Expected: both Gradle invocations end in `BUILD SUCCESSFUL`; `git diff --check` prints nothing.

- [ ] **Step 3: Inspect packaged metadata and Prophet resources**

Run:

```bash
cd /Users/kricy/Documents/Codex-Projects/SparkWitch
WITCH_JAR=$(find build/libs -maxdepth 1 -name '*.jar' ! -name '*-sources.jar' | sort | tail -1)
unzip -p "$WITCH_JAR" fabric.mod.json | rg '"id"|"version"'
unzip -p "$WITCH_JAR" assets/sparkwitch/lang/zh_cn.json | rg 'prophet|death_omen'

cd /Users/kricy/Documents/Codex-Projects/SparkAssist
ASSIST_JAR=$(find build/libs -maxdepth 1 -name '*.jar' ! -name '*-sources.jar' | sort | tail -1)
unzip -p "$ASSIST_JAR" fabric.mod.json | rg '"id"|"version"'
jar tf "$ASSIST_JAR" | rg 'guidebook/roles/sparkwitch/prophet.json'
unzip -p "$ASSIST_JAR" assets/sparkassist/lang/zh_cn.json | rg 'announcement.role.prophet'
```

Expected: ids remain `sparkwitch` and `sparkassist`; their live pre-Prophet versions are unchanged; both jars contain the new localization, and SparkAssist contains `prophet.json`.

- [ ] **Step 4: Run focused source-boundary checks**

Run:

```bash
cd /Users/kricy/Documents/Codex-Projects/SparkWitch
rg -n "prophet|death_omen|DeathOmen" src/main src/client src/test
rg -n "death_omen" src/main/resources/sparkwitch.mixins.json \
  src/client/resources/sparkwitch.client.mixins.json || true

cd /Users/kricy/Documents/Codex-Projects/SparkAssist
rg -n "prophet|death_omen" src/main src/client src/test
```

Expected: the SparkWitch mixin search has no matches; no new packet, component id, SparkTraits exception, or version file was added. SparkAssist has one ROLE resource and one SKILL-discovery exclusion, not an authored SKILL page.

- [ ] **Step 5: Perform multiplayer gameplay acceptance**

Use at least two clients and force the role before starting the round:

```text
/wathe:forceRole <prophet-player> prophet
```

Verify in this order:

1. Initial HUD cooldown begins at 60 seconds and the skill cannot be used early.
2. Create a body before activation; activate Death Omen; the old body never gains a red outline.
3. Kill another player after activation; that exact body becomes `#FF3030` without holding the instinct key.
4. Put a wall between Prophet and the body at less than 128 blocks; the outline remains visible.
5. Move beyond the provider's 128-block tracking boundary; the body is no longer available to outline.
6. Spawn or expose a fake/hidden body; it remains excluded by the existing fake/hidden-body rules.
7. Let the full 20 seconds expire; all outlines disappear and the HUD starts a full 90-second cooldown.
8. In a fresh activation, kill Prophet before expiry; the window ends immediately, later bodies are not recorded, and the unit-tested cancellation path does not arm the 90-second cooldown.
9. Open SparkAssist's Guidebook: Prophet appears in the civilian ROLE block before Killer, the four approved bullets render, and Death Omen does not appear in the Witch Skill tab.

Expected: every approved boundary is observable, with no change to other roles' highlights, cooldowns, Guidebook entries, or trait eligibility.
