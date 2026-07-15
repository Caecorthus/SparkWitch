# Third-Party Notices

## NoellesRoles Saint bell

`src/main/resources/assets/sparkwitch/sounds/ambient/saint_bell.ogg` is redistributed from
[Caecorthus/NoellesRoles](https://github.com/Caecorthus/NoellesRoles) commit
`45934af010561e22251cbfcfbc022f87ad581a36`.

The upstream repository licenses this work under LGPL-3.0-or-later. A copy of that license is
included at `licenses/NoellesRoles-LGPL-3.0-or-later.txt`.

## NoellesRoles Hunter and Orthopedist

The Hunter and Orthopedist role implementations, item and effect artwork, and placed-trap model
were copied or adapted from
[Caecorthus/NoellesRoles](https://github.com/Caecorthus/NoellesRoles) commit
`45934af010561e22251cbfcfbc022f87ad581a36`.

The adapted implementation is primarily under:

- `src/main/java/dev/caecorthus/sparkwitch/roles/killer/hunter/`
- `src/main/java/dev/caecorthus/sparkwitch/roles/civilian/orthopedist/`
- `src/client/java/dev/caecorthus/sparkwitch/client/hunter/`
- `src/client/java/dev/caecorthus/sparkwitch/client/hooks/HunterTrapClientHooks.java`
- `src/client/java/dev/caecorthus/sparkwitch/client/renderer/HunterTrapEntityRenderer.java`

Copied or namespace-adapted assets include the Hunter trap, double-barrel shotgun, shell, Fracture,
and Bone Setting models, textures, and icons under `src/main/resources/assets/sparkwitch/`.

SparkWitch's 2026-07-14 modifications use `sparkwitch` identifiers, integrate Wathe's native role,
shop, death, replay, and component contracts, pair random Hunter assignment with one Orthopedist,
and apply the project-specific balance and visibility rules documented by SparkWitch.

The upstream repository licenses this work under LGPL-3.0-or-later. A copy of that license is
included at `licenses/NoellesRoles-LGPL-3.0-or-later.txt`.

## StarRailExpress Ninja

The Ninja role mechanics, shop/loadout design, weapon implementation, and item artwork were
adapted or copied from
[catmoon-train/StarRailExpress](https://github.com/catmoon-train/StarRailExpress) at commit
`220d03ede335fc7971fcffbc302bc68bb91b0209`, credited upstream to the Catmoon Train Team
and commit author `wifi-left`.

The following files contain adapted code:

- `src/main/java/dev/caecorthus/sparkwitch/roles/killer/ninja/`, primarily from
  `src/main/java/org/agmas/noellesroles/game/roles/killer/ninja/NinjaPlayerComponent.java`,
  `src/main/java/org/agmas/noellesroles/init/RoleInitialItems.java`, and
  `src/main/java/org/agmas/noellesroles/init/RoleShopHandler.java`
- `src/main/java/dev/caecorthus/sparkwitch/item/ninja/NinjaKnifeItem.java`, from
  `src/main/java/org/agmas/noellesroles/content/item/NinjaKnifeItem.java`
- `src/main/java/dev/caecorthus/sparkwitch/item/ninja/NinjaShurikenItem.java`, from
  `src/main/java/org/agmas/noellesroles/content/item/NinjaShurikenItem.java`
- `src/main/java/dev/caecorthus/sparkwitch/entity/NinjaShurikenEntity.java`, from
  `src/main/java/org/agmas/noellesroles/content/entity/ThrowingKnifeEntity.java`

The following files are copied artwork with namespace-only model changes:

- `src/main/resources/assets/sparkwitch/textures/item/ninja_knife.png`
- `src/main/resources/assets/sparkwitch/textures/item/ninja_shuriken.png`
- `src/main/resources/assets/sparkwitch/models/item/ninja_knife.json`
- `src/main/resources/assets/sparkwitch/models/item/ninja_shuriken.json`

SparkWitch's 2026-07-14 modifications port the code to Minecraft 1.21.1 Yarn/Fabric APIs,
replace the upstream custom throw packet with server-authoritative vanilla item release,
integrate Wathe's kill/replay event pipeline, use SparkWitch identifiers, and render the
projectile from the shuriken item model instead of the generic throwing-knife texture.

The upstream README and root license declare GPL-3.0-only, while its Fabric metadata says
LGPL-3.0. This distribution follows the stricter GPL-3.0-only declaration. The upstream-derived
portion remains covered by GPL-3.0-only; SparkWitch's AGPL-3.0-only work is combined with it under
GPLv3 section 13. A verbatim copy of the upstream license is included at
`licenses/StarRailExpress-GPL-3.0-only.txt`.
