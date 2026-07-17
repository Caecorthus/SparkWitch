# Wraith SparkWitch Ownership Migration Design

**Approved approach:** complete ownership transfer with a narrow SparkTraits
compatibility bridge.

## Goal

Move Wraith, its complete runtime, and all five promotion identities from
SparkTraits to SparkWitch. All canonical role and component identifiers become
`sparkwitch:*`. Curser becomes a Witch-faction identity without gaining a
Grand-Witch skill panel or Grand-Witch-specific abilities.

The live, later Wraith implementation in the dirty SparkTraits workspace is the
behavioral source of truth, with one explicit correction: the approved Wraith
design allows normal text chat, so the later source-only chat lock is not
migrated.

## Canonical Identities

SparkWitch owns these non-opening identities:

| Identity | Canonical id | Registration | Base faction | Color |
| --- | --- | --- | --- | --- |
| Wraith | `sparkwitch:wraith` | Wathe special role | transitional | `0x79C7D4` |
| Wind Spirit | `sparkwitch:wind_spirit` | non-rollable role | civilian | `0x36E51B` |
| Guardian Angel | `sparkwitch:guardian_angel` | non-rollable role | civilian | `0x36E51B` |
| Vendetta | `sparkwitch:vendetta` | non-rollable role | civilian | `0x36E51B` |
| Saboteur | `sparkwitch:saboteur` | non-rollable role | killer | `0xC13838` |
| Curser | `sparkwitch:curser` | non-rollable role | Witch | `0xC13838` |

The five promotion roles keep `Role.MoodType.NONE`, their current base-role
sprint/time settings, and `appearanceCondition -> false`. They remain valid
Assassin guesses but never enter opening role selection. Wraith remains a
special role and does not enter the Assassin guess list.

The promotion pools remain unchanged and selection stays uniform with repeated
identities allowed:

- GOOD: Wind Spirit, Guardian Angel, Vendetta.
- KILLER: Saboteur, Curser.

## Repository Ownership

### SparkWitch

SparkWitch becomes the only owner of:

- role definitions and registration;
- Wraith player and round components;
- death capture, conversion, quota, tasks, promotion, reconnect, and cleanup;
- role transition and full Wathe opening replay;
- restricted effects, interaction rules, player isolation, and train phasing;
- client state, visibility, body/player projection, name and outline rules;
- grayscale rendering and server-confirmation gates;
- outgoing voice suppression and living/dead voice-group transitions;
- Wraith and promotion-role localization;
- legacy `sparktraits:*` role/component ingestion.

The Wraith runtime lives under a role-owned package such as
`roles/special/wraith/`. Component classes remain data-only. Event aggregation
stays in `SparkWitchEvents`, and mixins remain thin adapters into role-owned
services.

SparkWitch adds a small public, null-safe facade:

```java
SparkWitchApi.isWraithActive(PlayerEntity player)
SparkWitchApi.isWraithRestricted(PlayerEntity player)
```

These queries expose synchronized state only and do not expose mutable
components.

### SparkTraits

SparkTraits removes Wraith role registration, runtime services, Wraith mixins,
client rendering, voice handling, components, localization, and promotion-role
definitions. It retains only:

- retired legacy trait id filtering for `sparktraits:wraith`;
- a public, narrow trait snapshot/restore/terminal-clear contract on
  `SparkTraitsApi`;
- compatibility delegation from `SparkTraitsApi.isWraithActive` to the public
  SparkWitch facade, failing closed when SparkWitch is absent.

The trait compatibility contract uses an opaque structured `NbtCompound`
snapshot. SparkWitch calls only the public facade by reflection and never names
`sparktraits.impl` or `sparktraits.component`.

The restore operation is owned by SparkTraits because only SparkTraits may
restore exact ordered active/revealed traits and append `sparktraits:cautious`
outside the normal three-slot cap. An absent or incompatible SparkTraits build
returns an empty snapshot and leaves Wraith conversion functional without
traits.

The same facade exposes one Wraith-specific terminal clear operation. It maps
the caller's terminal/game-end flag to SparkTraits' existing removal reason so
falling below the train and invalid reconnect cleanup do not bypass trait
removal events. It does not expose a general downstream trait mutator.

### SparkAssist

SparkAssist moves the Wraith Guidebook entry to
`roles/sparkwitch/wraith.json`, changes its id, `sourceModId`, and required mod
to SparkWitch, and updates the text-chat wording. The entry remains beside the
special-role section rather than being presented as a Witch-faction role.

The five promotion identities receive role entries in their actual factions:

- Wind Spirit, Guardian Angel, and Vendetta under civilian;
- Saboteur under killer;
- Curser under Witch.

These pages describe identity/faction only; they must not invent abilities for
the placeholder roles.

### Unchanged Providers And Consumers

SparkFactionAPI, Wathe, NoellesRoles, and SparkStrength need no production
changes. Existing contracts already provide custom factions, special roles,
dead-player participation, full role announcement replay, generic Assassin
identity lookup, and generic tablet membership.

## Gameplay State Machine

### Round Quota

The starting roster fixes the round quota:

- fewer than 10 players: `0`;
- otherwise: `1 + floor((startingPlayers - 10) / 5)`.

SparkWitch initializes that canonical quota at Wathe
`GameEvents.ON_FINISH_INITIALIZE` from `gameComponent.getAllPlayers().size()`.
SparkTraits no longer initializes or mutates the Wraith quota.

Each eligible confirmed death rolls exactly `random < 0.75`. Original civilian
and killer players qualify. Neutral roles, `wathe:escaped`, and
`wathe:fell_out_of_train` do not. Last Stand resolves before this decision; a
cancelled death neither rolls nor consumes quota. A successful UUID remains
consumed for the rest of the round.

### Confirmed-Death Conversion

Before Wathe mutates the player, SparkWitch captures:

- original role id and native faction;
- the current effective faction from SparkFactionAPI, mapped to GOOD/KILLER;
- task progress and generation cadence;
- the opaque optional SparkTraits trait snapshot.
- the death game time used by Wathe's corpse record.

SparkWitch records whether Last Stand had already triggered for that player,
then defers the confirmed-death conversion to `END_SERVER_TICK`. At that point
all `KillPlayer.AFTER` listeners have completed. A newly triggered Last Stand
cancels the pending conversion, while an earlier Last Stand from the same round
does not block a later real death. This also guarantees that SparkTraits' normal
death cleanup runs before SparkWitch restores the opaque trait snapshot,
independent of Fabric mod initialization or listener registration order.

After confirmed death and a successful chance/quota decision, SparkWitch:

1. keeps ordinary drops and exactly one corpse with the original role;
2. asks SparkTraits to restore the exact trait snapshot plus Cautious;
3. activates `sparkwitch:wraith_player` state;
4. replaces the live role with `sparkwitch:wraith`;
5. syncs the role and calls `RoleAnnouncementApi.announceCurrentRole` without
   firing `RoleAssigned` or granting opening loadouts;
6. switches Spectator to Adventure once, restores the living voice group and
   tasks, and clears only inventory remaining after death drops.

### Restricted Wraith

Restricted Wraith keeps Wathe's dead-player membership while participating
through `DeadPlayerParticipation`. It has:

- invulnerability, invisibility, Slowness II, Blindness I, no collision, and
  train-door phasing;
- bilateral player-affect isolation through SparkFactionAPI policy;
- task-only mood ticking with at least three distinct tasks;
- the existing restricted right-click allowlist;
- blocked outgoing voice but normal incoming voice and normal text chat;
- full owner desaturation (`1.0`);
- Steve projection/privacy, hidden-body visibility, and existing spectator
  visibility rules;
- normal inventory opening.

An administrator's later Spectator change remains authoritative. The runtime
must not repeatedly force Adventure.

### Promotion

The third post-activation task completion queues promotion for the end of the
same server tick. Promotion:

- selects uniformly from the saved GOOD/KILLER pool;
- preserves all traits and active Wraith state;
- changes only the live role and replays the full opening presentation without
  `RoleAssigned`;
- removes Slowness, Blindness, and restricted interaction rules;
- keeps invisibility, invulnerability, no collision, player isolation, task
  participation, and outgoing voice suppression;
- changes owner desaturation to `0.5`.

The saved GOOD/KILLER effective-faction override applies only while the Wraith
is restricted. After promotion the promoted role's registered faction wins.
Therefore Curser resolves as `sparkwitch:witch`, not `wathe:killer`.

Promoted Wraith remains in Wathe's dead-player set. Curser shares Witch cohort
visibility, blackout immunity, and Witch-area-spell immunity, and wins with the
Witch faction, but it is not counted as a living Witch for majority checks and
does not resurrect.

Curser does not receive Grand Witch or Accomplice economy, mana, shop, loadout,
or active-skill behavior. Poison visibility also remains restricted to its
existing explicit role allowlist.

Because the Witch faction advertises generic killer-style reward capabilities,
SparkWitch's role economy policy returns an explicit denial for Curser passive
and direct-kill rewards. This role-level denial takes precedence over the
faction fallback without changing Grand Witch or Accomplice rewards.

## Witch Presentation Boundary

Curser is a Witch-faction member for faction rules and cohort presentation.
It is not a Grand Witch, Apprentice Witch, or Murderous Witch.

`WitchSkillPresentationRules` and `WitchSkillInventoryScreenMixin` must keep
their exact three-role allowlist. Neither Curser nor any other promotion role
may render a name or description in `gui.sparkwitch.skills`.

## Persistent Id Migration

Canonical runtime writes use only `sparkwitch:*`. Exact legacy ids are accepted
only at read boundaries:

| Legacy id | Canonical id |
| --- | --- |
| `sparktraits:wraith` | `sparkwitch:wraith` |
| `sparktraits:wind_spirit` | `sparkwitch:wind_spirit` |
| `sparktraits:guardian_angel` | `sparkwitch:guardian_angel` |
| `sparktraits:vendetta` | `sparkwitch:vendetta` |
| `sparktraits:saboteur` | `sparkwitch:saboteur` |
| `sparktraits:curser` | `sparkwitch:curser` |

A narrowly targeted SparkWitch NBT adapter copies old GameWorld role lists to
canonical keys only when the canonical key is absent, and canonicalizes exact
matches in disabled-role and role-history data. Unrelated namespaces and role
ids are untouched.

SparkWitch temporarily registers read-only legacy CCA readers for
`sparktraits:wraith_player` and `sparktraits:wraith_round`. On load they import
the known Wraith fields into the canonical components, then canonical
components become the only writers. Import is lazy after both component tags
have been read: canonical data wins when both are present, old-only data imports
once, and legacy writers emit no tag. SparkTraits no longer registers those
legacy component ids, preventing duplicate ownership.

The coordinated migration releases are SparkWitch `0.1.6.1`, SparkTraits
`0.1.9.10`, and SparkAssist `0.1.3.7`. SparkWitch rejects an installed
SparkTraits version below `0.1.9.10`; SparkTraits remains optional, but an old
Wraith-owning build may not coexist with SparkWitch's legacy component readers.
This makes duplicate component registration a loader-visible error instead of
an order-dependent runtime crash.

The canonical player sync order remains:

1. active;
2. restricted;
3. owner-only completed task count;
4. owner-only alignment ordinal;
5. owner-only promotion-pending flag.

The canonical round component stores only starting player count and cumulative
consumed UUIDs.

## Lifecycle And Cleanup

Reconnect restores valid Wraith task state, queued promotion, effects, voice,
and role identity without reopening quota. Round finish clears canonical and
legacy-import state, death snapshots, task snapshots, pending promotions, and
quota.

Falling below the train terminates Wraith participation, clears Wraith runtime
state and traits, and enters ordinary Spectator voice state. A successfully
promoted role remains the recorded live role.

## Verification

SparkWitch receives the Wraith pure and contract tests adapted to its packages
and canonical ids. Coverage includes:

- quota boundaries and exact `0.75` cutoff;
- eligible deaths, Last Stand ordering, and cumulative quota;
- immutable death/task/trait snapshots;
- one-time Adventure transition and administrator Spectator authority;
- CCA packet/NBT order, reconnect, promotion queue, and round cleanup;
- role registration, promotion pools, uniform selection, and no
  `RoleAssigned` dispatch;
- Curser's Witch faction, post-promotion effective faction, and non-living
  majority behavior;
- Curser's exclusion from the Witch skill panel and role-specific mana/shop
  paths;
- interaction, effect, player-affect, voice, client rendering, outline,
  inventory, and normal text-chat contracts;
- exact legacy role/component migration and canonical-only writes.

SparkTraits tests prove retired-id filtering, exact snapshot restoration with
Cautious, absence of Wraith runtime/registration, fail-closed compatibility,
and preservation of unrelated trait behavior.

SparkAssist tests prove Guidebook ownership, placement, canonical ids, exact
role names, and wording. No placeholder promotion ability is documented.

Each changed repository runs its Java 21 focused tests, architecture checks,
`git diff --check`, and a full sequential `clean build`. Builds do not overlap
across repositories. Packaged jars are inspected to prove that Wraith runtime
classes exist only in SparkWitch and no canonical role is registered twice.

## Integration Safety

Implementation occurs in isolated Wraith worktrees. Before transferring each
file or hunk into a shared checkout, re-read the live target and preserve all
unrelated dirty work. Central registries, components, events, mixin configs,
metadata, languages, Guidebook catalogs, dependency jars, and version fields
are merged surgically rather than replaced wholesale.

## Out Of Scope

- No abilities, shops, items, or configurable values are added to the five
  placeholder promotion identities.
- Curser does not join the top-left Witch skill panel.
- Curser does not become alive merely because it joins the Witch faction.
- No SparkFactionAPI, Wathe, NoellesRoles, or SparkStrength production change
  is made unless verification exposes a missing existing contract.
- No unrelated role, trait, faction, packet, NBT field, resource, gameplay
  value, or event order is changed.
