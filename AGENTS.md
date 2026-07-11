# Advanced Spark

## Repositories
- https://github.com/Caecorthus/SparkTraits (By default, check this repository first; if it hasn't been cloned locally, use GitHub/gh to retrieve the context)
- https://github.com/XruiDD/TrainMurderMystery (Spark-ver wathe)
- https://github.com/XruiDD/NoellesRoles (Spark-ver NoellesRoles, a role expansion add-on mod for wathe)
- https://github.com/Caecorthus/SparkFactionAPI (Faction API)
- https://github.com/Caecorthus/SparkWitch (Spark-ver Factions & Roles addons)
- https://github.com/Caecorthus/SparkStrength (Spark-ver roles buff)
- https://github.com/Caecorthus/SparkAssist (Client-side assist mod)

## Skills
- Use this skill in your and your subagents' workflow
	- [$using-superpowers](/Users/kricy/.codex/skills/using-superpowers/SKILL.md)

## Coding

### Architecture
- Read `CONTEXT.md` first for the live module map, stable contracts, and verification entry points.
- Read `ARCHITECTURE.md` before changing code.
- Read `ARCHITECTURE_LOGS.md` before moving, deleting, renaming, splitting, or merging existing modules.
- `CONTEXT.md` is the current routing map and does not grant permission to refactor.
- `ARCHITECTURE.md` is the constitution: what to do, what not to do, and what requires approval.
- `ARCHITECTURE_LOGS.md` is the history: approved boards, completed migrations, and watch-only notes.

### Check
- Inspect SparkWitch plus the provider/consumer repositories for the exact
  contract being changed. Do not mechanically read every Spark repository when
  no cross-mod seam is involved.
- SparkFactionAPI is the shared faction contract. Optional SparkTraits access
  may target only its public facade and must fail closed. SparkStrength and
  SparkAssist are checked only when code or metadata shows a relevant seam.
- Prove that unrelated roles, factions, traits, packets, NBT fields, resources,
  gameplay values, and event order remain outside the changed path.
- Use Java 21 and the risk-based test/build rules in `ARCHITECTURE.md`. Do not
  add production-only test hooks.

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
