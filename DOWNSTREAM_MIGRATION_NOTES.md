# Downstream Migration Notes

## Historical Test-Only API Cleanup

- Removed `WitchSkillRegistry.clearForTests()` from the public API because
  production-only reset helpers are not a supported downstream contract.
- Runtime skill registration behavior is unchanged. Downstream runtime mods
  should not need any migration unless they were calling this test-only helper.

Focused committed tests are allowed under the current risk-based architecture
rules. This historical removal remains correct because tests must exercise real
interfaces rather than add public mutation hooks to production code.

## 2026-07-09 SparkTraits Public Facade Migration

SparkWitch's optional Last Stand and final-moment bridges now reflect only
`dev.caecorthus.sparktraits.api.SparkTraitsApi`. No SparkWitch public API changed,
and no downstream migration is required.

Integrations must not depend on SparkTraits `impl` or `component` classes. A
missing or incompatible optional SparkTraits facade continues to fail closed.

## 2026-07-17 Wraith Ownership

The unreleased Wraith implementation moved completely from SparkTraits to
SparkWitch. All live identities and components use `sparkwitch:*`, including
`wraith`, `wraith_player`, `wraith_round`, `wind_spirit`, `guardian_angel`,
`vendetta`, `saboteur`, and `curser`; there are no `sparktraits:*` aliases or
legacy readers.

Optional downstream queries must use the null-safe
`dev.caecorthus.sparkwitch.api.SparkWitchApi` facade. SparkTraits remains an
optional provider of generic trait and Last Stand state only and no longer owns
Wraith lifecycle, rendering, faction, role, or component contracts.

## 2026-11-16 Third-Faction Draw

The witch faction (`sparkwitch:witch`) and Team Jiahao (`sparkwitch:jiahao`) are
now SparkFactionAPI `third_faction` factions, dealt only by SparkFactionAPI's
round-start third-faction draw from lineups SparkWitch registers with
`SparkFactionApi.registerThirdFactionLineup`. This needs the SparkFactionAPI
2.0.0 jar rebuilt with that draw; an older jar fails closed (no third faction
is dealt).

- Removed the internal dealing classes `registry/WitchRoleAssignmentService`,
  `registry/WitchRoleCounts`, `roles/neutral/insider/InsiderAssignmentService`,
  `roles/neutral/insider/InsiderPairingRules` and the constant
  `InsiderRules.MIN_KILLERS`. None was public API; no Spark mod used them.
- The Grand Witch's appearance condition is now always false (it was
  `minPlayers(18)`), so `shouldAppear` no longer reports her as drawable.
- Team Jiahao is built with `dealtInWatheNeutralPool(false)`: Wathe's neutral
  pass no longer deals NoellesRoles' Corrupt Cop.
- New admin command `/sparkwitch:thirdFactionMinRequirement <players>`
  (permission `sparkwitch.command.thirdfactionminrequirement`) writes
  SparkFactionAPI's third-faction player minimum.
