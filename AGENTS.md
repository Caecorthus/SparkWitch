# Advanced Spark

## Repositories
- https://github.com/Caecorthus/SparkTraits (By default, check this repository first; if it hasn't been cloned locally, use GitHub/gh to retrieve the context)
- https://github.com/XruiDD/TrainMurderMystery (Spark-ver wathe)
- https://github.com/XruiDD/NoellesRoles (Spark-ver NoellesRoles, a role expansion add-on mod for wathe)
- https://github.com/Caecorthus/SparkFactionAPI (Faction API)
- https://github.com/Caecorthus/SparkWitch (Spark-ver Factions & Roles addons)
- https://github.com/Caecorthus/SparkStrength (Spark-ver roles buff)
- https://github.com/Caecorthus/SparkAssist (Client-side assist mod)

## Coding

### Architecture
- Read `CONTEXT.md` first for the live module map, stable contracts, and verification entry points.
- `CONTEXT.md` is the current routing map and does not grant permission to refactor.
- Get explicit owner approval before moving, deleting, renaming, splitting, or merging existing modules.

### Check
- Inspect SparkWitch plus the provider/consumer repositories for the exact
  contract being changed. Do not mechanically read every Spark repository when
  no cross-mod seam is involved.
- SparkFactionAPI is the shared faction contract. Optional SparkTraits access
  may target only its public facade and must fail closed. SparkStrength and
  SparkAssist are checked only when code or metadata shows a relevant seam.
- Prove that unrelated roles, factions, traits, packets, NBT fields, resources,
  gameplay values, and event order remain outside the changed path.
- Use Java 21 and the verification entry points in `CONTEXT.md`. Do not
  add production-only test hooks.

### Witch Skill Panel
- The top-left `gui.sparkwitch.skills` panel in
  `WitchSkillInventoryScreenMixin` is exclusively for Grand Witch
  (`sparkwitch:grand_witch`), Apprentice Witch
  (`sparkwitch:apprentice_witch`), and Murderous Witch
  (`sparkwitch:murderous_witch`) and their own skills.
- `WitchSkillInventoryScreenMixin` 的背包左上角
  `gui.sparkwitch.skills` 技能介绍仅属于大魔女
  (`sparkwitch:grand_witch`)、预备魔女
  (`sparkwitch:apprentice_witch`)和杀意魔女
  (`sparkwitch:murderous_witch`)及其自有技能。
- Registration in `WitchSkillRegistry`, a `sparkwitch` namespace, or reuse of
  shared dispatch, storage, packets, and cooldowns does not grant access to
  this panel. Other roles must use role-owned presentation and must never
  render a skill name or description here.
- 不得根据 `WitchSkillRegistry` 注册关系、`sparkwitch` 命名空间，或共享的
  分发、存储、数据包和冷却机制推断该面板的展示资格。其他职业必须使用
  职业自有展示，不得在该面板显示技能名称或介绍。

### Subagents
- You're the coordinator/leader between the subagents.
- Please use subagents. You can code with them together at the same time.
- Create multiple subagents for multiple purposes. Create as many as you needed.
	- Do NOT overload one subagent. Create multiple subagents to divide the tasks.
- Use English as the main language between you and your subagents.

### Annotations
- Add concise English and Chinese comments for non-obvious public/stable
  contracts, external seams, mixins, client/server authority, and migration
  behavior. Do not narrate self-evident code.
