# Black Raven And Witch Maiden Design

**Status:** Approved after the July 15, 2026 grilling session.

## Goal

在 SparkWitch 中加入两个可独立交付的杀手阵营职业：

1. `sparkwitch:black_raven`，显示名“黑羽鸦”，身份色 `#51445F`。
2. `sparkwitch:witch_maiden`，显示名“巫女”，身份色 `#B04A8B`。

每个阶段在玩法完成后同步加入 SparkAssist Guidebook，并放在现有杀手阵营职业附近。第一阶段先交付黑羽鸦，第二阶段再交付巫女。

## Global Boundaries

- 不修改 TrainMurderMystery/Wathe 或 NoellesRoles。
- 不需要修改 SparkFactionAPI；两个职业只使用现有 `FactionRoleDefinition` 注册契约。
- 不修改 SparkStrength 或 SparkTraits。SparkTraits 仍然只通过既有公开门面参与兼容。
- SparkAssist 只增加 Guidebook JSON、两份语言资源和测试；不改 catalog、parser、packet、runtime、metadata 或版本。
- 不向共享 `WitchPlayerComponent`、`WitchPlayerNbtCodec` 或 `WitchPlayerSyncCodec` 增加字段，也不改变其 tick、NBT 或 packet 顺序。
- `SparkWitchEvents` 继续只是注册与生命周期聚合器；实际规则由新职业模块持有。
- 不更改既有角色、商店、保护、毒药、移动、奖励、回放或高亮顺序，除非本设计明确列出交互。
- 保持 Minecraft `1.21.1`、Java `21`、SparkWitch `0.1.5.8`、SparkFactionAPI floor `0.1.5.8` 和 SparkAssist `0.1.3.4`；本工作不自动升版本。

## Accepted Architecture

采用角色自有状态与薄适配层：

- 黑羽鸦使用独立的受害者组件 `sparkwitch:black_raven_mark` 保存延迟刺杀，不进入共享 Witch component。
- 巫女的“聚焦步伐”使用角色自有隐藏状态效果承载持续时间和跑/走阶段，不增加新的技能按键、冷却包或共享字段。
- 毒苹果只有在 Wathe 没有公开事件的位置使用 SparkWitch mixin。mixin 只负责捕获版本敏感调用，纯状态转移仍放在 `roles/killer/witchmaiden/`。
- 托法娜仙液使用 Wathe 的公开 `KillPlayer.AFTER` 和 `GameFunctions.killPlayer(...)`。
- Guidebook 始终跟随对应玩法阶段落地，避免先展示尚未存在的职业。

拒绝的方案：

- 扩展共享 `WitchPlayerComponent`：会碰触受保护的 tick、NBT 和 packet 契约。
- 修改 Wathe/NoellesRoles 增加新事件：边界不允许，而且现有公开 hook 加薄 mixin 已足够。
- 用 Wathe 全局 `Scheduler` 或永久方块坐标表保存延迟/餐盘状态：无法可靠处理回合、断线、区块卸载和旧状态复活。

## Common Role Contract

两个职业都通过以下形状注册：

```java
FactionRoleDefinition.builder(roleId, FactionIds.KILLER)
        .color(roleColor)
        .moodType(Role.MoodType.FAKE)
        .maxSprintTime(-1)
        .canSeeTime(true)
        .nativeWatheFaction(Faction.KILLER)
        .build()
```

- 不添加最低人数条件。
- Wathe 的特殊杀手候选每局只消费一次，因此每个职业每局至多一名。
- 两个职业可以在同一局同时出现。
- 胜利、基础金币、杀手能力、友军误伤和普通奖励全部沿用杀手阵营。
- 不增加自定义选角、胜利或经济 mixin。

## Phase 1: Black Raven

### Identity And Loadout

- Role id: `sparkwitch:black_raven`。
- Display name: `zh_cn` 为“黑羽鸦”，`en_us` 为 `Black Raven`。
- Quote: “我已预见你的死亡”。
- 开局获得一把 `sparkwitch:feather_blade`（羽刃）。
- 羽刃不注册到任何死亡掉落流程；黑羽鸦死亡不会把它掉到局内。
- 只有当前职业确实为黑羽鸦的存活玩家可以使用羽刃。其他持有者右键不产生标记，也不进入冷却。

### Restricted Shop

黑羽鸦清空普通杀手商店后只保留以下条目，并原样保留 Wathe 已构造的停电条目：

| Entry | Price | Stock |
| --- | ---: | ---: |
| `crowbar` | 75 | 1 |
| `poison_vial` | 75 | unlimited |
| `scorpion` | 75 | unlimited |
| `body_bag` | 75 | unlimited |
| captured `blackout` | Wathe dynamic price | Wathe contract |

停电条目必须从当前 `BuildShopEntries.ShopContext` 捕获并重新加入，不能重建其动态价格、五分钟共享冷却或购买回调。

### Feather Blade Targeting

- 短按右键时，由服务端使用玩家眼睛位置和服务端朝向射线检测。
- 最大距离为 `3.0` 格，必须直接对准另一名仍在游戏且存活的玩家，并通过方块视线检测。
- 可以标记杀手队友；不增加阵营过滤。
- 无效目标、自身、超距、遮挡、死亡目标、非黑羽鸦使用者，以及目标已经有羽刃标记时均拒绝，不消耗物品且不进入冷却。
- 成功后不消耗羽刃，设置 `1200` tick（60 秒）物品冷却，并写入 `400` tick（20 秒）延迟标记。
- 已标记目标不能被刷新、覆盖或叠加。
- 使用与命中本身不播放声音，也不调用 Wathe 的刀刺声音 payload。

### Delayed Mark State

受害者自有的 `BlackRavenMarkPlayerComponent` 保存：

- 标记者 UUID；
- 到期 world tick；
- 当前 Wathe match UUID。

组件使用 `RespawnCopyStrategy.NEVER_COPY`，由自己的 server tick 检查状态：

1. 当前比赛 UUID 不同、目标已经死亡或回合已经结束时，清除标记。
2. 到期时先清除并同步标记，保证任何保护拦截都不会造成第二次尝试。
3. 若黑羽鸦仍在线，使用其 `ServerPlayerEntity` 作为 killer；若已断线，传入 `null`。
4. 调用：

```java
GameFunctions.killPlayer(
        victim,
        true,
        ravenOrNull,
        GameConstants.DeathReasons.KNIFE
);
```

该调用保持 `force=false`，因此保镖、忍者格挡、疯魔护甲、圣徒和其他既有 `KillPlayer.BEFORE` 保护仍可拦截。拦截后标记已清除，不重试，原本的 60 秒羽刃冷却也不返还。

黑羽鸦死亡但仍连接时，标记继续并保留正常直接击杀归属。黑羽鸦断线时标记仍继续，到期死亡没有击杀归属、个人奖励或对应回放 actor。目标断线时组件保留；仅在同一比赛内重连才会继续并在已过期时立即结算，换局后自动清除。

尸体死因使用 Wathe 原生 `KNIFE`，因此尸检显示原生刀刺原因。正常的尸体生成、保护、死亡广播、全局角色反应、回放与奖励流程保持不变；“无声”只指羽刃使用声和刀刺命中声。

### Private Highlight

- 被标记者对标记自己的黑羽鸦始终显示 `#51445F` 穿墙轮廓。
- 不要求开启本能透视。
- 高亮优先级使用 `GetInstinctHighlight.HighlightResult.PRIORITY_HIGH + 1`，明确覆盖 NoellesRoles Phantom 使用的默认 `skip()` 高优先级。
- 组件的 NBT 保存标记者、到期 tick 和 match UUID，但自定义 sync packet 只向当前黑羽鸦观察者发送“此目标是否由你标记”的 boolean；不向客户端发送标记者 UUID、到期时间或比赛 UUID。
- `shouldSyncWith(...)` 对黑羽鸦观察者保持可达，从而在清除时仍能发送 `false`；`writeSyncPacket(...)` 再按接收者 UUID 计算 boolean。

### Feather Blade Art

羽刃需要新的实际物品贴图和 generated-item model。目标是 32x32 透明像素画：

- 主轮廓是一根细长黑羽；
- 羽轴延伸为冷银色刃；
- 只在羽缘使用少量 `#51445F` 暗紫高光；
- 不画成普通匕首，也不复用 Wathe 刀或忍者苦无贴图。

## Phase 2: Witch Maiden

### Identity

- Role id: `sparkwitch:witch_maiden`。
- Display name: `zh_cn` 为“巫女”，`en_us` 为 `Witch Maiden`。
- Role color: `#B04A8B`。
- Quote: “曾经我也相信爱与魔法…”。
- Skill id: `sparkwitch:focused_footsteps`，显示名“聚焦步伐”。

`#B04A8B` 与 NoellesRoles 巫毒师的 `#8072FD` 明确区分。两者机制也不同：巫毒师是好人身份，预先绑定任意目标，并在自己死亡后延迟 5 秒杀死该目标；巫女是杀手身份，只在携带托法娜仙液且被另一名玩家直接击杀时，立即反杀实际 killer。

### Restricted Shop

巫女清空普通杀手商店后使用以下条目，并原样保留 Wathe 已构造的停电条目：

| Entry | Price | Stock |
| --- | ---: | ---: |
| `knife` | 100 | 1 |
| `lockpick` | 50 | 1 |
| `poison_vial` | 75 | unlimited |
| `scorpion` | 75 | unlimited |
| `poison_apple` | 75 | unlimited |
| `tofana_elixir` | 200 | 1 |
| captured `blackout` | Wathe dynamic price | Wathe contract |

### Focused Footsteps

- 使用现有身份技能键与 `UseWitchSkillC2SPacket`；不新增按键或目标 packet。
- 开局立即可用，initial cooldown 为 `0`。
- 服务端按施法者眼睛位置与服务端朝向选择 `3.0` 格内、视线无遮挡、非自身、仍存活的玩家。阵营不是限制条件。
- 无效目标不进入冷却。
- 成功后施加隐藏的 `sparkwitch:focused_footsteps` 状态效果 `600` ticks（30 秒），并进入 `1800` ticks（90 秒）冷却。
- 如果目标已有该效果，再次成功施放会先移除旧实例再施加完整的 30 秒实例，重置跑/走阶段，并正常消耗本次 90 秒冷却。
- 施法者死亡不解除效果；目标死亡时清除效果。

客户端在 `KeyboardInput.tick` 尾部只强制正向输入，并清除后退、横移和潜行降速；相机转向、跳跃、攻击、物品使用和交互仍可用。目标只能通过转动视角改变前进方向，不能停下、后退、横移或蹲伏减速。

服务端每 tick 执行：

- 跑步阶段：只要体力未耗尽就强制 sprint。
- 目标已疲惫或本次效果内首次耗尽体力时，切换为步行阶段并保持到效果结束；之后即使体力恢复也不重新疾跑。
- 目标在施放时已经疲惫，则直接进入步行阶段。
- 无限体力目标在整个效果期间保持疾跑。
- 每 tick 额外按 `GameConstants.MOOD_DRAIN` 扣除心情，600 ticks 共 `0.125`，等于一个任务 30 秒的消耗速度。Wathe 的 `PlayerMoodComponent.setMood(...)` 保证 FAKE/NONE 心情目标保持不变，但移动效果仍生效。

Hunter 捕兽夹硬定身与 Pig God 冻结拥有最终优先级：定身期间效果计时和心情消耗继续，目标不能移动；硬定身结束后按剩余时间恢复强制前进。

### Poison Apple Plate State

`sparkwitch:poison_apple` 是可以右键餐盘布置的一次性商店物品。任意持有者都可以布置，并以实际布置者 UUID 作为后续毒源。

- 食物盘或饮料盘为空时也可以布置。
- 已经有普通 Wathe 毒药的餐盘仍可布置毒苹果；两层状态独立共存。
- 已经有毒苹果状态时拒绝再次布置，不消耗、不覆盖。
- 布置成功消耗一个毒苹果。
- 插入食物/饮料、失败拿取、重复物品限制、下普通毒和其他非拿取交互都不计数。
- 第一次成功拿到食物或饮料只把安全计数从 0 变成 1，不附加毒苹果毒源。
- 第二次成功拿取时，把实际取得的 `ItemStack` 标记为毒苹果毒物，然后立即解除餐盘的毒苹果状态。
- 如果第一次拿走了最后一份物品，陷阱保留；补餐后下一次成功拿取仍是第二次并中毒。
- 普通毒药与毒苹果同时在首次拿取前存在时，第一次物品照常获得普通毒源，同时计为毒苹果的安全第一次；第二次物品获得毒苹果毒源。

权威状态属于 `BeveragePlateBlockEntity` mixin，并包含布置者 UUID、成功拿取数和 Wathe match UUID。敏感字段写入服务器持久 NBT，但从 `toInitialChunkDataNbt(...)` 中移除；客户端只收到 `armed` boolean。

一个 role-owned weak tracker 只持有当前已加载且已布置的 plate entity，用于回合结束时清除并同步可见状态；它不是权威状态，也不保存永久方块位置。已卸载餐盘依靠 match UUID 在下次读取或交互时拒绝旧局状态。这样回合结束后不会在下一局重新出现旧陷阱或旧粒子。

### Poisoned Food And Drinks

第二次拿取的物品同时携带：

- Wathe 原生 `WatheDataComponentTypes.POISONER`，让普通食物继续走 `PoisonUtils.applyFoodPoison(...)`、原生延迟、归属、回放和奖励；
- 一个 namespaced SparkWitch marker，只用于识别毒苹果产生的饮品。

Wathe 目前只在 `PlayerEntity.eatFood` 处理原生毒源，NoellesRoles 的 `FineDrinkItem` 和 `BaseSpiritItem` 又覆盖了 `CocktailItem.finishUsing`。因此 SparkWitch 在 `ItemStack.finishUsing` HEAD 增加严格门控：仅当 item 是 `CocktailItem` 且存在 SparkWitch marker 时调用一次 `PoisonUtils.applyFoodPoison(...)`，随后清除 marker 和该 stack 的原生 poisoner，避免重复处理。普通 Wathe 毒饮品和没有 marker 的其他物品不被改变。

### Platter Adapter And Antidote

SparkWitch 用一个 MixinExtras `@WrapMethod` 包住 Wathe `FoodPlatterBlock.onUse`：

- 毒苹果与仅毒苹果状态下的解毒由 wrapper 处理。
- 其他交互完整调用 `original.call(...)`，因此 NoellesRoles mixin priority `1100` 的服务员双份拿取、基酒/上等佳酿特殊拿取和普通 Wathe 逻辑都先正常运行。
- wrapper 比较调用前后的主手 `empty -> nonempty`，而不是依赖 `ActionResult`，因为服务员成功拿取仍返回 `PASS`。
- 每次检测到成功拿取后才推进纯 `PoisonApplePlateState`。

NoellesRoles Toxicologist antidote 清除两层毒：

- 若普通毒与毒苹果同时存在，先让 NoellesRoles 原逻辑处理声音、冷却和回放，再清除毒苹果状态，不重复反馈。
- 若只有毒苹果状态，SparkWitch 镜像 NoellesRoles 当前公开行为：清除状态、播放 burp 声、设置同一 antidote cooldown，并记录同一 `cure_plate` item-use 事件。

### Poison Apple Particles And Visibility

毒苹果状态不写 Wathe `BeveragePlateBlockEntity.poisoner`，因此不会借用 Wathe 的红色骷髅毒粒子。SparkWitch client mixin 在 plate `clientTick` 中生成稀疏的纯红色 `DustParticleEffect`。

可见性完全复用 Wathe 当前毒药可见判定：

- 原生杀手阵营玩家，包括底层职业仍为杀手的“善良杀手”；
- 共犯、大魔女、杀意魔女；
- Toxicologist；
- 已死亡观察者；
- 普通存活好人不可见。

如果普通毒和毒苹果同时存在，可见者会同时看到 Wathe 原生骷髅粒子和 SparkWitch 红色粒子；两层效果不会互相覆盖。

### Tofana Elixir

`sparkwitch:tofana_elixir`（托法娜仙液）是最大堆叠 1 的被动物品，没有主动右键效果。

在 `KillPlayer.AFTER` 中，仅当下列条件全部满足时触发：

- 已确认死亡的 victim 是巫女；
- killer 非空、与 victim 不同，且在回调时仍存活并参与当前游戏；
- 巫女死亡时，自己的主背包或快捷栏中仍携带至少一瓶托法娜仙液。

副手、护甲槽、个人合成格、光标、已打开容器、地面掉落物和其他容器都不算“携带”。环境死亡、自杀、管理员/脚本无 killer 死亡，以及 killer 已不存活时均不触发也不消耗。

触发顺序固定：

1. 从巫女主背包/快捷栏消耗一瓶。
2. 以已经死亡的巫女作为直接 killer，调用：

```java
GameFunctions.killPlayer(
        killer,
        true,
        deadWitchMaiden,
        WitchMaidenRules.TOFANA_DEATH_REASON_ID
);
```

3. 保持 `force=false`。若保护、疯魔护甲或其他 `KillPlayer.BEFORE` 规则挡下反杀，瓶子仍已消耗，不重试。

消耗在嵌套 kill 前发生，并要求回调 killer 仍存活，所以不会形成托法娜递归。该死亡明确算作巫女的直接击杀：保留 Wathe 的直接 actor、回放、巫女击杀金币、存活杀手队友金币和所有标准下游击杀反应，不额外压制奖励。

尸体死因翻译必须逐字为：

> 我们的怒火永无止境，自由永远不会被束缚

### Witch Maiden Art

增加 `poison_apple` 与 `tofana_elixir` 的实际透明像素贴图和 generated-item model：

- 毒苹果使用鲜明红色苹果、暗紫伤痕和小面积冷色叶片，不能像普通金苹果。
- 托法娜仙液使用细长透明玻璃瓶、浅色标签和 `#B04A8B` 液体/封蜡点缀，不能复用普通 poison vial 或巫毒师视觉。

## SparkAssist Guidebook

SparkAssist 保持 resource/test-only。每个 JSON 使用：

- `tab: "ROLE"`
- `sourceModId: "sparkwitch"`
- `summaryKey: "guidebook.sparkassist.content.role.overview"`
- `ownerRoleIds: []`
- `requiredModIds: ["sparkwitch"]`
- 与 SparkWitch 注册完全相同的 id 和 `#RRGGBB` color。

第一阶段新增：

```text
assets/sparkassist/guidebook/roles/sparkwitch/black_raven.json
id: sparkwitch:black_raven
color: #51445F
order: 430
```

第二阶段新增：

```text
assets/sparkassist/guidebook/roles/sparkwitch/witch_maiden.json
id: sparkwitch:witch_maiden
color: #B04A8B
order: 440
```

实时工作树中的 `sparkwitch:kidnapper` 已占用 `420`，因此早期的 `420/430` 候选已调整为 `430/440`。预期相对顺序为：

```text
sparkwitch:ninja (410)
sparkwitch:kidnapper (420)
sparkwitch:black_raven (430)
sparkwitch:witch_maiden (440)
sparkwitch:grand_witch (500)
```

执行时必须重新扫描实时资源，不能覆盖并行工作；测试以相邻关系为主，并同时断言当前选定值未冲突。

Guidebook 的 `zh_cn.json` 和 `en_us.json` 都加入中文名字“黑羽鸦”与“巫女”，符合当前 Guidebook 强制中文与本地化测试契约。页面内容使用已经确认的 quote、数值、商店、保护、断线、可见性和物品规则，不写尚未实现的占位功能。

## Delivery Phases

### Phase 1 Acceptance: Black Raven

- 完成角色注册、开局羽刃、限制商店、延迟标记、私有高亮、贴图/model、语言与测试。
- 完成 `black_raven.json` 和 SparkAssist focused resource test。
- SparkWitch 与 SparkAssist 各自通过 Java 21 focused tests、architecture check、full sequential build 和 `git diff --check`。
- 客户端验收 3 格边界、遮挡、友军目标、重复标记、20 秒、60 秒冷却、死亡/断线、保护拦截、无本能高亮、Phantom 隐身和无刀刺声。

### Phase 2 Acceptance: Witch Maiden

- 完成角色注册、限制商店、聚焦步伐、毒苹果、托法娜仙液、三件资源中的后两件、语言与测试。
- 完成 `witch_maiden.json` 和 SparkAssist focused resource test。
- SparkWitch 与 SparkAssist 再次通过 Java 21 focused tests、architecture check、full sequential build 和 `git diff --check`。
- 客户端验收移动输入、耐力转阶段、FAKE/NONE 心情、Hunter/Pig 优先级、空盘/补餐/服务员/Noelles 饮品、双层毒、解毒、红粒子可见性、托法娜保护/消耗/归属和巫毒师区分。

## Concurrent Worktree Rules

SparkWitch 与 SparkAssist 当前都有其他未提交职业工作，中央 registry、items、components、events、skills、mixin configs、语言文件与 Guidebook aggregate tests 都是共享热点。执行每个任务前必须重新读取目标文件和其 diff，只合并本任务所需行；不得 reset、checkout、覆盖、重排、顺手重构、暂存或提交其他人的改动。

## Out Of Scope

- 不加入配置化价格、时间、距离、颜色或粒子。
- 不新增自定义 HUD、screen、keybinding、胜利逻辑、离线 UUID 击杀 API 或跨模组毒药 API。
- 不修复 Wathe/NoellesRoles 与本设计无关的饮品、餐盘、奖励或 mixin 行为。
- 不更改巫毒师、善良杀手、共犯、大魔女、杀意魔女、Toxicologist、Hunter、Pig God、Phantom、Ninja 或 Saint 的自身规则。
- 不 commit、push、发布或升版本，除非所有者另行明确要求。
