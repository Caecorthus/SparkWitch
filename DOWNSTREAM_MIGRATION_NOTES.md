# Downstream Migration Notes

## Test-Only API Cleanup

- Removed `WitchSkillRegistry.clearForTests()` from the public API because
  committed Java test suites and test-only reset helpers are no longer allowed.
- Runtime skill registration behavior is unchanged. Downstream runtime mods
  should not need any migration unless they were calling this test-only helper.
