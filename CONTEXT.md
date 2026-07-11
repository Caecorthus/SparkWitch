# SparkWitch Context

Read this file before `ARCHITECTURE.md`. It is a live routing map, not blanket
permission to refactor. Historical decisions and board status remain in
`ARCHITECTURE_LOGS.md`.

## Product Boundary

SparkWitch adds Grand Witch, Accomplice, Apprentice Witch, Murderous Witch, and
Pig God gameplay to Wathe. SparkFactionAPI owns shared faction contracts;
SparkTraits and NoellesRoles integrations stay behind compatibility Adapters.
SparkStrength and SparkAssist do not own SparkWitch gameplay.

Current build baseline:

- Minecraft `1.21.1`
- Java `21`
- SparkWitch `0.1.5.7`
- SparkFactionAPI floor `0.1.5.6`

## Read Order

1. `CONTEXT.md`
2. `ARCHITECTURE.md`
3. `ARCHITECTURE_LOGS.md` for structural work
4. The live owning Module and its direct callers

## Current Ownership

- `api/`: the only public downstream SparkWitch Interface.
- `roles/civilian/apprentice/`: Apprentice instinct and ability runtime.
- `roles/civilian/piggod/`: Pig God chase, psycho, sound, economy, and rules.
- `roles/neutral/murderouswitch/`: Murderous Witch feature, Death Ray, shop,
  and win rules.
- `roles/witch/`: rules shared by Grand Witch and Accomplice.
- `roles/witch/grandwitch/`: Grand-Witch-private sword, spell, fear, Voodoo,
  active-skill, and world runtime.
- `mana/`: mana economy and natural-regeneration runtime.
- `component/`: CCA ids, stored fields, sync/NBT codecs, and narrow state
  operations used by the owning runtime Modules.
- `compat/`: optional or version-sensitive cross-mod Adapters.
- `impl/SparkWitchEvents`: watch-only registration/lifecycle aggregator.

## Runtime Invariants

`WitchPlayerComponent.serverTick()` preserves this order:

1. Grand Witch ceremonial sword
2. Apprentice ability windows
3. Pig God chase
4. Murderous Witch Death Ray
5. shared cooldown
6. mana regeneration

Do not reorder these calls. The component ids remain `sparkwitch:player` and
`sparkwitch:world`; packet field order and NBT keys are specified in
`ARCHITECTURE.md`.

SparkTraits is optional and fail-closed. Reflection may target only
`dev.caecorthus.sparktraits.api.SparkTraitsApi`, never `sparktraits.impl` or
`sparktraits.component`.

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
