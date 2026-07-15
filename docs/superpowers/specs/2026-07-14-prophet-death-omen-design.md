# Prophet Death Omen Design

**Status:** Approved in the July 14, 2026 grilling session.

## Goal

Add the good-aligned SparkWitch role `sparkwitch:prophet` (display name `先知`) with one role-owned active skill, `sparkwitch:death_omen` (`死亡预兆`), and document the role in SparkAssist's Guidebook.

## Approved Gameplay

- Prophet is a normal special civilian role: at most one per round, no extra minimum-player condition, real mood, 10-second sprint, no round timer, no killer powers.
- Prophet uses role color `#D4AF37` and can receive normal SparkTraits civilian/universal traits.
- Death Omen has a 60-second initial cooldown, a 20-second active window, and a 90-second cooldown that starts only after a full active window ends.
- Only bodies created from deaths during the current active window are highlighted. Bodies that existed before activation never qualify.
- A qualifying body is outlined `#FF3030` through walls while Wathe tracks that body on the client. Wathe's body entity has a fixed 128-block tracking range, so bodies outside 128 blocks are not visible to the skill. The outline disappears when the active window ends.
- If Prophet dies during the active window, the effect ends immediately and does not continue gathering deaths. The aborted window does not start the post-window cooldown.
- No other roles, factions, traits, highlights, packets, gameplay values, or event order should change.

## Architecture

Use the existing SparkWitch role-skill key, assignment, readiness, HUD, and deferred-cooldown framework. Do not add a second key binding or a second player component.

Create four focused classes:

1. `ProphetRules` owns stable ids, colors, timings, role recognition, and the corpse-outline priority.
2. `ProphetSkillService` owns activation.
3. `ProphetRuntime` owns server ticking, body-spawn collection, role/death cancellation, and normal completion of the deferred cooldown.
4. `ProphetCorpseHighlightClientHooks` adapts the synchronized owner state and Wathe's public `GetInstinctHighlight` event.

Keep Prophet state in a role-owned `ProphetPlayerState` held by the existing `WitchPlayerComponent`; do not add a second component id. `WitchPlayerNbtCodec` and `WitchPlayerSyncCodec` delegate to that state after the live schema tail and never reorder existing fields. The owner receives the remaining active ticks plus the UUIDs of the exact corpse entities created during that window. Spectators do not receive the Prophet-private corpse list.

Wathe marks the victim dead before spawning its `PlayerBodyEntity`. `ProphetRuntime` observes Fabric's server-side `ENTITY_LOAD` event and records that body's entity UUID only when its death tick is the current world tick, its owner is actually marked dead, and the viewer is a living Prophet with an active window. This rejects chunk-loaded old bodies and fake-body spawns. `KillPlayer.AFTER` separately cancels the window when the dying player is the Prophet. The client highlights only a tracked `PlayerBodyEntity` whose entity UUID is present in the synchronized set while the window still has ticks remaining.

This server-authoritative spawn ordering makes the activation boundary exact even when a death and activation happen in the same server tick: a body loaded before activation is never recorded, while a body loaded after activation is. Entity UUIDs also distinguish multiple corpses belonging to the same player. No Wathe corpse mutation, mixin, or custom tracking protocol is added. Existing hidden-body and higher-priority highlight suppression rules still win.

## Registration And Presentation

- Register `sparkwitch:prophet` through SparkFactionAPI under `FactionIds.CIVILIAN` with native Wathe faction `CIVILIAN`.
- Add Prophet to the SparkWitch facade, role membership check, and assassin-guess ordering near the other SparkWitch good roles.
- Register `sparkwitch:death_omen` in `WitchSkillRegistry` with initial cooldown `1200`, active duration `400`, post cooldown `1800`, zero mana cost, and a Prophet-only selector.
- Use the existing generic skill HUD for initial cooldown, active seconds, post cooldown, and ready state.
- Add English and Chinese SparkWitch localization while preserving the approved Chinese wording.

## SparkAssist Guidebook

Add one authored role entry for `sparkwitch:prophet`, guarded by `requiredModIds: ["sparkwitch"]`, with this approved content:

- `开局进入 60 秒冷却。按下技能键后，死亡预兆持续 20 秒。`
- `只会红色高亮生效期间新产生的尸体；技能开启前已有的尸体不会被标记。`
- `高亮不受墙体阻挡，但只能看见 128 格内的尸体。先知死亡时，效果立即终止。`
- `效果结束后进入 90 秒冷却。`

Death Omen is role-owned and must be excluded from SparkAssist's Witch Skill discovery list.

Guidebook `order` is presentation metadata, not a permanent gameplay constant. Concurrent work already uses or reserves the old proposal `250`. The current candidate is `270`, after Tarot Reader and the planned Perfumer slot and before `wathe:killer` at `300`. Immediately before integration, rescan the live resources and choose an unused ten-step value in the civilian block. Tests must preserve the stable relation `sparkwitch:apprentice_witch < sparkwitch:prophet < wathe:killer`; they must not make the current candidate a cross-project contract.

## Concurrent Worktree Rules

Both repositories contain active work from other agents, including shared registry, component, language, Guidebook, and test files. Implementation must wait for or coordinate with those owners, then re-read every shared file and its diff before editing. Never overwrite, revert, stage, or renumber their work. Recompute aggregate Guidebook counts only after the resource set is quiet.

## Verification

- Pure tests cover exact timings, body-UUID registration, old-body exclusion, and normal versus cancelled completion.
- Component/schema tests cover owner-only corpse-list sync, NBT round trips, the 20-second window, normal 90-second deferred cooldown, and death cancellation without cooldown.
- Client compilation plus focused source/integration tests cover the Wathe highlight adapter; upstream source inspection and live 127/129-block acceptance cover Wathe's provider-owned tracking limit.
- SparkAssist tests cover exact Chinese content, role color, relative placement, and exclusion from the Witch Skill tab.
- Run Java 21 targeted tests first, then sequential full SparkWitch and SparkAssist builds. Because player sync/NBT and cross-repo resources change, inspect both built jars.
- Final live acceptance uses a living Prophet, one pre-existing body, and one body created during Death Omen to prove old-body exclusion, red through-wall highlighting, exact expiry, and death cancellation.

## Out Of Scope

- No SparkTraits code changes.
- No new faction, minimum-player formula, configurable timing, custom range protocol, line-of-sight rule, sound, item, or custom screen.
- No version bump, commit, push, or release unless separately requested.
