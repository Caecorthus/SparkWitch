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
