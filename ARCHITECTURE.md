# SparkWitch Architecture Constitution

This document is mandatory for all future agents working in this repository.
It defines what code is allowed to do, what code must not do, and how structural
changes are approved. It is not a changelog and it is not blanket permission to
refactor old code.

Historical reviews, approved boards, and completed migrations belong in
`ARCHITECTURE_LOGS.md`.

## Mandatory Rules

1. Read this file before changing code.

2. Read `ARCHITECTURE_LOGS.md` before moving, deleting, renaming, splitting, or
   merging existing Modules.

   The logs record current board status and past migration reasons. A historical
   note does not override this constitution.

3. Protect unrelated characters, talents, factions, roles, traits, commands,
   packets, resources, and UI behavior.

   Every behavior change must prove the requested scope stays isolated. If a task
   touches one role or skill, do not broaden it to another role, faction, trait,
   or external mod unless the owner explicitly approves that wider scope.

4. Before structural changes, request owner approval with:

   - Board: the named architecture area being changed.
   - Reason: the friction that makes the old Module shape unsafe or costly to
     keep.
   - Old code scope: exact packages, files, and existing methods/helpers that
     will be moved, deleted, renamed, or rewritten.
   - New Module shape: the proposed package/module name and its intended
     Interface, including allowed responsibilities.
   - Forbidden scope: files, methods, policies, public Interface semantics,
     ordering, fallback behavior, resources, and downstream contracts that must
     not change.
   - Behavior invariants: null behavior, fallback behavior, ordering, priority,
     role/faction isolation, packet ids, resource ids, translation keys, and
     sync/NBT semantics that must be preserved.
   - Downstream impact: whether SparkFactionAPI, SparkTraits, SparkStrength,
     SparkAssist, Wathe, NoellesRoles, tests, releases, or user workflows must
     change.
   - Verification plan: exact local tests, build commands, static checks, jar
     checks, and downstream searches/checks.

   If any item is unknown, perform a read-only review before requesting approval.
   Do not fill gaps by guessing during implementation.

5. Comments must be English and Chinese when they explain:

   - Public Interface semantics.
   - Wathe, NoellesRoles, SparkTraits, or SparkFactionAPI Seams.
   - Mixin injection reasons.
   - Client/server confirmation rules.
   - Cross-mod compatibility rules.
   - Legacy retention or migration reasons.

   Do not add noise comments to self-explanatory code.

6. Tests should cross the same Interface as callers. Prefer testing domain
   Modules through their real Interface instead of reaching into private helper
   details. Package-private pure rules are allowed when they preserve Locality.

7. Do not make broad architecture changes while fixing an urgent gameplay bug.
   Stabilize the bug first, then propose a separate board if the bug proves the
   Module shape caused drift.

## Stable Surface

The following surfaces are stable unless a task explicitly approves a breaking
change and includes downstream impact plus version planning.

- `api/`: public Witch skill Interface for built-in and future skill providers.
- Role ids:
  - `sparkwitch:grand_witch`
  - `sparkwitch:accomplice`
  - `sparkwitch:apprentice_witch`
  - `sparkwitch:murderous_witch`
  - `sparkwitch:pig_god`
- Faction ids:
  - `sparkwitch:witch`
  - `sparkwitch:murderous_witch`
- Component ids:
  - `sparkwitch:player`
  - `sparkwitch:world`
- Packet ids and version channels:
  - `sparkwitch:use_witch_skill`
  - `sparkwitch:fire_death_ray`
  - `sparkwitch:server_confirm`
  - `sparkwitch:version_check`
- Public command literals and permission nodes:
  - `sparkwitch:forceAbility`
  - `sparkwitch:setMana`
  - `sparkwitch.command.force_ability`
  - `sparkwitch.command.set_mana`
- Translation key families under `skill.sparkwitch.*`,
  `message.sparkwitch.*`, `gui.sparkwitch.*`, `faction.sparkwitch.*`,
  `announcement.win.sparkwitch.*`, `game.win.sparkwitch.*`,
  `death_reason.sparkwitch.*`, and `replay.death.sparkwitch.*`.
- Resource ids under `assets/sparkwitch/` that are shipped to users.
- Jar version metadata and client entrypoint packaging guards.

## Target Architecture

Only `api/` is a public downstream Interface. All other packages are internal
Implementation or Adapter Modules unless this document says otherwise.

```text
src/main/java/dev/caecorthus/sparkwitch/
  SparkWitch.java

  api/
    WitchSkillDefinition.java
    WitchSkillRegistry.java
    WitchSkillSelectionContext.java
    WitchSkillUseContext.java
    WitchSkillUseResult.java

  registry/
    roles and factions
    Wathe role ordering Adapters

  skill/
    skill registration, selection, assignment, use, lock validation, and
    presentation rules

  mana/
    mana rules, costs, regeneration, commands, and HUD-facing calculations

  roles/
    civilian/
      apprentice/
        abilities/
          ApprenticeAbilityCatalog.java
          ApprenticeAbilityWindowRules.java
          MightyForce/
            Mighty Force activation and active-window combat service
          SwiftStep/
            Swift Step activation and effect rules
          MurderSense/
            Murder Sense activation, range, color, and dangerous-item rules
          Healing/
            Healing activation and pulse service
          Clairvoyance/
            Clairvoyance activation, timing, and outline colors
      piggod/
        Pig God rules, chase state, economy, instinct, and compatibility hooks
    killer/
      future killer-side role Modules
    neutral/
      murderouswitch/
        MurderousWitchDeathRay/
          Death Ray rules and server-side service
        MurderousWitchFeature/
          effective faction, economy, instinct, and win-condition bridges
        MurderousWitchRules/
          pure Murderous Witch rules and win action enum
        MurderousWitchShop/
          Murderous Witch shop rules and server-side shop service
    witch/
      WitchFactionRules.java
      WitchFactionFeatureService.java
      WitchFactionEconomyPolicy.java
      WitchInstinctPolicy.java
      WitchFactionProtectionPolicy.java
      grandwitch/
        Grand Witch shop, spells, fear, ceremonial sword state, and active skills
      accomplice/
        AccompliceShop/
          Accomplice shop rules and server-side shop service

  economy/
    Witch-owned economy decisions that are not role-private

  win/
    Witch faction win rules and Shadow Jester showdown compatibility

  compat/
    SparkFactionAPI, SparkTraits, NoellesRoles, and Wathe compatibility bridges

  component/
    Cardinal Components registration, storage, sync, and NBT only

  command/
    admin command Adapters only

  item/
    ceremonialsword/
      Ceremonial Sword item Adapter, combat rules, and dash service
    firepoker/
      Fire Poker item-owned combat, fall attribution, and strike rules

  mixin/
    thin server-side Adapters only

  net/
    packet definitions and server-side networking
    version/

  util/
    small shared rules that have no better domain home

src/client/java/dev/caecorthus/sparkwitch/client/
  SparkWitchClient.java
  hooks/
  hud/
  text/
  mixin/
  net/version/

src/main/resources/
  fabric.mod.json
  sparkwitch.mixins.json
  assets/sparkwitch/

src/client/resources/
  sparkwitch.client.mixins.json
```

This target shape is a direction, not permission to move everything at once.
Use approved boards and keep each move narrow.

## Package Rules

### `api/`

`api/` is the stable Witch skill Interface. It may define skill descriptions,
selection/use contexts, result values, and the registry Interface.

Do not expose internal component state, Wathe internals, SparkFactionAPI
Implementation classes, or role-specific service Modules from `api/`.

### Root Package

The root package owns bootstrap and stable id/catalog entry points.

Root files may register top-level systems but must not grow role-specific
gameplay, skill execution, shop behavior, economy decisions, or client behavior.

### `registry/`

Role and faction registration belongs here once migrated.

Preserve these role/faction facts:

- Grand Witch and Accomplice are members of the SparkFactionAPI Witch faction.
- Apprentice Witch is a native Wathe role and must not silently become a Witch
  faction member.
- Murderous Witch is a native Wathe neutral role with explicit SparkWitch bridges.
- Pig God is a native civilian-side role with SparkWitch-specific powers.
- SparkWitch roles stay visible to Assassin-style guess panels through deliberate
  Wathe role-ordering logic.

### `roles/`

`roles/` groups role-owned Modules by their native alignment or custom faction
home.

- `roles/civilian/apprentice/abilities/` owns Apprentice Witch ability
  registration constants, shared window rules, and per-ability Modules under
  `MightyForce/`, `SwiftStep/`, `MurderSense/`, `Healing/`, and
  `Clairvoyance/`.
- `roles/civilian/piggod/` owns Pig God rules, chase state, economy, instinct,
  and compatibility hooks.
- `roles/neutral/murderouswitch/` owns Murderous Witch behavior through
  `MurderousWitchDeathRay/`, `MurderousWitchFeature/`, `MurderousWitchRules/`,
  and `MurderousWitchShop/`.
- `roles/killer/` is reserved for future killer-side role Modules.

`roles/witch/` owns the Grand Witch and Accomplice faction Module.

The root `roles/witch/` package may own rules and policies that apply to both
Grand Witch and Accomplice:

- Faction membership rules.
- SparkFactionAPI economy and instinct policy Adapters.
- Wathe protection hooks such as blackout immunity and narrow Voodoo guards.
- The registration Facade that wires the faction Module in stable order.

Role-private behavior must live below the role package:

- `roles/witch/grandwitch/` owns Grand Witch shop, spells, fear, ceremonial
  sword state, and active skills.
- `roles/witch/accomplice/AccompliceShop/` owns Accomplice shop rules and
  server-side shop service.

Do not put Apprentice Witch, Murderous Witch, or Pig God behavior in
`roles/witch/`. They are separate Modules even when they interact with Witch
faction rules.

Do not let the registration Facade grow gameplay logic. If a rule, economy
decision, instinct decision, protection hook, shop, spell, or item behavior grows
there, move it behind the owning Module's Interface.

### `item/`

`item/` owns SparkWitch item families. When an item has multiple Modules, keep
the Minecraft item Adapter, item-bound combat behavior, cooldown rules, dash
rules, fall attribution, and item-owned constants under the same item family
package.

Current item families:

- `item/ceremonialsword/` owns the Ceremonial Sword item Adapter, custom attack
  handling, and dash service.
- `item/firepoker/` owns Fire Poker strike rules, mana-spend effects, cooldown,
  and train-fall attribution.

Do not put shop rules, faction rules, skill assignment, component sync/NBT, role
active-window effects, or client presentation in `item/`.

### `component/`

`component/` owns CCA registration, state storage, sync, and NBT.

It must not become the owner of role gameplay rules. If a component method starts
needing role-specific timing, targeting, damage, shop, economy, or display
knowledge, propose moving that behavior behind an owning domain Module while
preserving the component's sync/NBT Interface.

Component id and NBT key changes require explicit approval and migration notes.

### `mixin/`

Mixin Modules are thin Adapters at Wathe, NoellesRoles, Minecraft, Fabric, or
client rendering Seams.

A mixin may:

- Locate the injection point.
- Read the minimum required context.
- Delegate to a domain Implementation Module.
- Preserve injection-specific comments in English and Chinese.

A mixin must not own gameplay rules, policy ordering, text rules, economy rules,
targeting rules, win rules, component lifecycle semantics, or resource decisions.

### `net/`

Networking owns packet definitions, packet registration, and version handshake
Adapters.

Protocol constants, packet ids, read/write behavior, compatibility checks,
disconnect messages, and play-stage confirmation behavior must stay stable unless
the owner approves a versioned change.

Client-only confirmation state and client receivers should live in client-side
packages when that can be done without changing behavior.

### `command/`

Commands are Adapters. They may parse Brigadier arguments, enforce permissions,
and delegate to domain Modules.

Command Modules must not own skill selection rules, mana rules, role conflicts,
or gameplay behavior. Those rules belong in domain Modules that tests can call
without command fixtures.

Use one vocabulary when adding new commands: the domain term is `Skill`. Existing
player-facing `forceAbility` text is stable and must not be renamed without
explicit approval.

### `client/`

Client Modules own presentation, HUD, input, text helpers, sound hooks, and
client-side compatibility Adapters.

HUD, visual, input, sound, and client hook behavior must remain gated by
`SparkWitchServerConnection.isConfirmedServer()` or an approved equivalent. Do
not let SparkWitch client behavior activate on ordinary servers.

### Resources

`src/main/resources` owns metadata, server mixin config, language files, item
models, textures, sounds, and font assets.

`src/client/resources` owns client-only mixin config.

Do not reintroduce migrated NoellesRole enhancement resources, components,
dynamic-light entrypoints, or flashlight/capsule assets unless the owner
explicitly reopens that scope.

For Minecraft `1.21.1`, SparkWitch item assets must keep the classic
`assets/sparkwitch/models/item/*.json` and
`assets/sparkwitch/textures/item/*.png` shape unless the owner approves a
resource-format migration.

## External Seams

### SparkFactionAPI

SparkFactionAPI owns shared faction capability, policy, economy, gun, target,
vision, and round-end Seams. SparkWitch may register policies and consume the
public SparkFactionAPI Interface. SparkWitch must not patch SparkFactionAPI
internal behavior from this repository.

Any change requiring a new SparkFactionAPI public Interface, changed policy
ordering, changed fallback behavior, or changed version requirement needs
downstream impact and version planning.

### Wathe

Wathe owns native role storage, game lifecycle, native factions, item/shop
screens, and base win flow. SparkWitch may adapt Wathe Seams through events,
public registries, and thin mixins.

Do not move SparkWitch roles into Wathe native killer/passenger buckets as a
shortcut. Use explicit bridges.

### NoellesRoles

NoellesRoles is a compatibility dependency and a source of specific neutral,
showdown, swallowed, and role-enhancement Seams.

Do not re-own role enhancements that have migrated out of SparkWitch. Keep
compatibility hooks narrow and soft where compile-time dependency is not needed.

### SparkTraits

SparkTraits interactions must stay trait-specific. Last Stand and Task Master
compatibility must be proven through the actual trait Seams, not inferred from
role names alone.

### SparkStrength

SparkStrength owns migrated NoellesRoles-style role buffs. SparkWitch keeps
Witch-only faction mechanics. Do not move Witch-faction mechanics into
SparkStrength or pull migrated role-buff Modules back into SparkWitch without an
approved migration board.

## Verification Rules

Docs-only changes:

```sh
git diff --check
```

Source/resource changes:

```sh
./gradlew test
./gradlew build
git diff --check
```

When Gradle appears stuck around compile/test discovery, first suspect stale
Gradle daemon/build state and rerun with Java 21 plus conservative Gradle flags
before rewriting logic.

When SparkFactionAPI is included as a sibling build, validate SparkFactionAPI and
SparkWitch sequentially. Avoid overlapping clean/build commands across the two
repos.

## Stop Line

Do not split, move, rename, or delete Modules just because a finer shape is
possible.

Architecture work requires an owner-approved board and at least one concrete
trigger:

- A bug, crash, compatibility issue, or release issue proves the current Module
  shape caused drift.
- A new feature needs behavior at an existing Seam and would otherwise add logic
  to the wrong Module.
- Tests, build output, or this document show the implementation has diverged
  from the rules above.

Absent one of those triggers, keep the architecture stable and make local,
task-scoped changes only.
