# Black Raven And Witch Maiden Design

**Status:** Approved after the July 15, 2026 grilling session; Black Raven Perception plus Witch Maiden targeting and Voodoo immunity revised after owner review.

## Goal

在 SparkWitch 中加入两个可独立交付的杀手阵营职业：

1. `sparkwitch:black_raven`，显示名“黑羽鸦”，身份色 `#51445F`。
2. `sparkwitch:witch_maiden`，显示名“巫女”，身份色 `#B04A8B`。

玩法仍按第一阶段黑羽鸦、第二阶段巫女独立交付。所有者在本轮额外批准先同步巫女的 SparkAssist Guidebook 文案；黑羽鸦页面仍随第一阶段玩法落地。

## Global Boundaries

- 不修改 TrainMurderMystery/Wathe 或 NoellesRoles。
- 不需要修改 SparkFactionAPI；两个职业只使用现有 `FactionRoleDefinition` 注册契约，第二技能键也由 SparkWitch 自己注册。
- 不修改 SparkStrength。SparkTraits 只增加嗜血专用物品 tag、对应判定、一个用于下游遵守“隐蔽行动”的 null-safe 公开查询与 focused tests；不改嗜血倍率、层数、分配、其他天赋或其他武器行为。
- SparkAssist 只增加 Guidebook JSON、两份语言资源、测试，以及把角色自有的 `sparkwitch:perception`、`sparkwitch:focused_footsteps` 排除出通用技能发现页的窄规则；不改 catalog、parser、packet、其他 runtime、metadata 或版本。
- 不向共享 `WitchPlayerComponent`、`WitchPlayerNbtCodec` 或 `WitchPlayerSyncCodec` 增加字段，也不改变其 tick、NBT 或 packet 顺序。
- `SparkWitchEvents` 继续只是注册与生命周期聚合器；实际规则由新职业模块持有。
- 不更改既有角色、商店、保护、毒药、移动、奖励、回放或高亮顺序，除非本设计明确列出交互。
- 保持 Minecraft `1.21.1`、Java `21`、SparkWitch `0.1.5.8`、SparkFactionAPI floor `0.1.5.8` 和 SparkAssist `0.1.3.4`；本工作不自动升版本。

## Accepted Architecture

采用角色自有状态与薄适配层：

- 黑羽鸦使用独立的受害者组件 `sparkwitch:black_raven_mark` 保存延迟刺杀，并使用独立的拥有者组件 `sparkwitch:black_raven_perception` 保存感知活动状态、逐目标进度和已解锁身份快照；两者都不进入共享 Witch component。
- SparkWitch 公共客户端层只注册默认 `N`、可重绑的“技能键 2”并按角色 id 分发；具体处理器由各角色模块注册。黑羽鸦只在自己的 client 模块中注册普通/感知本能模式切换。
- 现有 deferred-cooldown/HUD 需要读取角色自有活动窗口，因此既有 `WitchSkillRegistry` 增加可选的按 skill id 活动窗口 provider。它只查询窗口 tick，不保存角色字段、不写 NBT/packet，也不改变 `WitchPlayerComponent.serverTick()` 顺序；黑羽鸦注册 provider 读取自己的感知组件。
- 黑羽鸦的灰阶、史蒂夫皮肤、本能门控、身份色外框和近距离身份文字都由角色自有 client hooks 加薄 mixin 实现；不复用 NoellesRoles 灵界行者的 free-camera 状态，也不扩大 Wathe 的旁观者权限。
- SparkTraits 定义 `sparktraits:bloodthirsty_weapons`，基础 tag 包含 Wathe 匕首和 NoellesRoles 毒针；SparkWitch 只以 datapack tag 贡献羽刃，不让 SparkTraits 依赖 SparkWitch 类或反射门面。
- SparkTraits 在现有稳定门面 `dev.caecorthus.sparktraits.api.SparkTraitsApi` 增加 `isInstinctHidden(viewer, target)`，统一公开 SparkTraits 自己拥有的最终本能隐藏判定，包括“隐蔽行动”、背水一战 pending/隐藏和灵体投射状态，并保留终局时刻等既有 bypass；SparkWitch 继续只反射这一门面并 fail closed，不读取 trait component 或 `impl` 类。
- 巫女的“聚焦步伐”使用角色自有隐藏状态效果承载持续时间和跑/走阶段，并在 Wathe 现有 `LimitedInventoryScreen` 上增加角色自有头像控件。它复用 `UseWitchSkillC2SPacket` 已有的可选目标 UUID、现有技能冷却同步和现有 HUD；施法者看到的持续时间由同步冷却与角色自有 client-only 目标状态推导，不增加按键、packet、共享字段或自定义 screen。
- 巫女的巫毒免疫由 `roles/killer/witchmaiden/` 自有规则通过 Wathe `KillPlayer.BEFORE` 实现，只取消精确的 `noellesroles:voodoo` 最终死亡；不拦截 NoellesRoles 的绑定、倒计时、冷却或记录。
- 毒苹果只有在 Wathe 没有公开事件的位置使用 SparkWitch mixin。mixin 只负责捕获版本敏感调用，纯状态转移仍放在 `roles/killer/witchmaiden/`。
- 托法娜仙液使用 Wathe 的公开 `KillPlayer.AFTER` 和 `GameFunctions.killPlayer(...)`。
- Guidebook 默认跟随对应玩法阶段落地；本轮提前加入巫女页面是所有者明确批准的文案同步例外，不代表巫女玩法已经实现。

拒绝的方案：

- 扩展共享 `WitchPlayerComponent`：会碰触受保护的 tick、NBT 和 packet 契约。
- 修改 Wathe/NoellesRoles 增加新事件：边界不允许，而且现有公开 hook 加薄 mixin 已足够。
- 把技能键 2、感知身份或本能模式提升到 SparkFactionAPI：按键与角色私有情报不是阵营契约，目前也没有第二个跨模组消费者。
- 在 SparkTraits 硬编码 `sparkwitch:feather_blade`，或复用 `thrust`/左键击退的“刀类”判断：前者反转依赖方向，后者会错误赋予羽刃无关的左键能力。
- 让感知绕过 shared HUD/deferred cooldown，自己维护第二套冷却显示与计时：会复制现有技能契约并制造两个互相漂移的真相源；无状态窗口 provider 已能解决。
- 新增巫女专属技能 packet、专属 screen 或依赖 SparkStrength 的 UI 类：现有目标 packet 与 Wathe 背包界面已经足够，跨模组复用实现会制造不必要的运行时依赖。
- 在 NoellesRoles 选人阶段阻止巫女被绑定：NoellesRoles 没有公开绑定策略事件，而且所有者已确认免疫只取消最终连锁死亡。
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
- 开局获得一本 `sparkwitch:black_raven_ledger`（感知册）。
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

### Perception Ledger

- 感知册是黑羽鸦专属、自绑定的只读册子。外观与打开方式保持书本语义，但物品 stack 不保存任何身份、UUID 或感知点秘密。
- 其他玩家观察黑羽鸦手持时看到空手；册子不能丢弃、死亡掉落、放入容器或转移给其他玩家。存活黑羽鸦的册子意外消失时由角色生命周期补回，身份丢失或回合结束时直接清理且不生成掉落物。
- 只有当前仍存活的黑羽鸦能够打开。页面从 owner-only 感知组件动态生成，未达到 10 点的玩家完全不出现，也不显示部分进度。
- 右键时服务端只发送一个不含身份数据的 `OpenBlackRavenLedgerS2CPacket` 作为已验证的打开授权；客户端页面仍从 owner-only 组件生成，packet 不复制快照列表。
- 每条格式为 `玩家名 - 身份`；玩家名使用白色，只有“身份”使用解锁时快照中的身份色。分页由只读书本界面处理。
- 身份快照保存解锁当刻的玩家名、真实服务端 role id、可本地化身份文本与 role color。目标之后换身份不会自动更新这条既有情报。

### Perception Active Skill

- Skill id: `sparkwitch:perception`，显示名“感知”。使用 NoellesRoles 已有身份技能键（技能键 1）和 SparkWitch 现有 `UseWitchSkillC2SPacket`/HUD，不新增主动技能 packet。
- 开局 initial cooldown 为 `1200` ticks（60 秒）。就绪时右下角显示“按 [技能键 1] 进行感知”。
- 成功发动后进入固定 `300` ticks（15 秒）活动窗口；不能主动取消，再按技能键 1 无效。
- 活动窗口自然结束后才开始 `1800` ticks（90 秒）冷却。死亡、失去黑羽鸦身份、断线或回合结束会立即终止活动并清除延迟冷却状态，此时不额外维持无意义的角色冷却。黑羽鸦本人断线只取消本次活动、失明与延迟冷却；同一比赛的未完成点数和已解锁快照继续保留，重连后仍可使用。
- `sparkwitch:perception` 在既有 `WitchSkillRegistry` 同步注册 provider，使现有 `successAfterActiveWindow(...)`、readiness 与右下角 HUD 都读取独立感知组件的 `300` tick 窗口；不在共享组件增加感知字段。
- 活动期间服务端维持原版失明状态；牛奶、杜松子酒或其他清除状态手段不能提前绕过。玩家仍可正常移动、疾跑、攻击、使用物品、交互和打开感知册。
- 客户端使用 SparkWitch 自有 post-effect，复用 SparkTraits 抑郁 shader 的去饱和思路但固定为 `50%` 灰白度；保留第三方许可归属，不导入或调用 SparkTraits 内部 client 类，并在断线/窗口尺寸变化时正确关闭或重建 processor。
- 活动期间其他玩家使用原版 Steve 皮肤，姓名牌保留；终端贴图适配必须晚于 SparkTraits 等其他皮肤 hook，不能被抑郁疯魔皮肤重新覆盖。该规则不复制灵界行者的自由视角、隐藏姓名、方块轮廓或其他行为。
- 活动期间普通本能与感知本能的玩家外框和环境增亮全部关闭。羽刃的角色自有命中外框不属于本能，继续显示。

### Server-Authoritative Perception Progress

- `BlackRavenPerceptionPlayerComponent` 以目标 UUID 保存整数感知点和不足 1 秒的累计 tick，并保存当前 match UUID、活动剩余 tick 与已解锁身份快照。
- Wathe 直到 `ON_FINISH_INITIALIZE` 才创建本局 match UUID，因此角色分配阶段只清理旧状态/发放装备；组件在该事件之后绑定新 match UUID，不能读取 null 或上一局 id。
- 活动期间每个 server tick 检查与黑羽鸦三维球形距离 `<= 8.0` 格的目标；不要求方块视线。
- 只计算同一世界、当前仍参与本局且存活的其他玩家；排除自己、尸体、旁观者和创造模式玩家。
- 每个目标累计满 `20` 个有效 tick 时增加 `1` 点，最多 `10` 点。离开范围或技能结束只暂停，整数点与不足一秒的 tick 都不清零；以后再次满足条件时从原进度继续。
- 目标断线时按 UUID 保留进度；同一比赛重连后可以继续。黑羽鸦本人断线时也保留同一比赛的进度与快照，只取消正在进行的 15 秒活动。新比赛、黑羽鸦身份失效或组件所属玩家死亡时清理全部进度与快照。
- 达到 `10` 点时，服务端读取目标当刻的真实 role 并生成一次冻结快照。之后停止为该 UUID 累计；目标后续换身份、离开范围或技能结束都不改写快照。
- 未完成的感知点与逐 tick 进度永不发给客户端；owner-only sync 只发送活动显示所需状态和已解锁快照，不能向其他玩家或物品 NBT 泄露全局身份表。

### Instinct Modes And Skill Key 2

- SparkWitch 注册默认 `N`、可重绑的“技能键 2”，公共入口只按当前 role id 查找并调用角色注册的 client handler；公共层不硬编码黑羽鸦。
- 黑羽鸦开局默认为 `NORMAL`。按技能键 2 在 `NORMAL` 与 `SENSED_ONLY` 间切换；模式只影响客户端表现，不发服务端 packet。
- 黑羽鸦角色自有 HUD 在现有技能行上方增加稳定的第二行 `本能：普通 [N]` / `本能：感知 [N]`；共享 `WitchSkillHudRenderer` 不硬编码角色。感知活动期间该行置灰且技能键 2 不响应；活动结束恢复此前选择。
- 死亡、身份丢失、回合结束或断开服务器时重置为 `NORMAL`，不得把模式带入下一局或其他服务器。
- Wathe 的左 Alt 仍保持“按住开启本能、松开关闭”。`NORMAL` 完全沿用现有本能外框与环境增亮。
- `SENSED_ONLY` 同样保留 Wathe 的环境增亮，但玩家外框只允许已达到 10 点的 UUID，并使用解锁快照中的身份色。未解锁玩家必须返回无外框，不能回落到 Wathe、NoellesRoles、SparkTraits 或其他 SparkWitch 的普通高亮。
- 感知外框继续尊重目标当前的防透视/隐身规则，例如 SparkTraits“隐蔽行动”和角色隐身；保护结束后重新显示。已解锁册子情报本身不删除。
- SparkTraits 拥有的本能隐藏规则通过其稳定公开门面查询；SparkTraits 未安装、版本过旧或反射失败时只视为“没有该 SparkTraits 保护”，不能让兼容失败阻断本能或崩溃客户端。
- 感知模式是玩家外框的最终规则：已解锁目标显示身份色；未解锁目标不显示。羽刃标记不能覆盖这个结果。普通模式和感知活动期间则继续使用既有羽刃标记规则。
- 实现顺序上，感知模式必须先让羽刃事件放弃该目标，再由低优先级终端裁决读取既有隐藏结果；不能先让 `HIGH + 1` 羽刃颜色变为可见后再覆盖颜色，否则会绕过 NoellesRoles/SparkTraits 的 skip。

### Nearby Identity Text

- 选择 `SENSED_ONLY` 后无需按住左 Alt，直接沿用 Wathe/NoellesRoles 的普通存活玩家近距离姓名逻辑。
- 在既有 `2.0` 格准心目标判定命中已解锁玩家时，Wathe 继续显示白色玩家名，SparkWitch 在同一区域补充身份色的快照身份文本。
- 该适配不把黑羽鸦伪装成旁观者，不扩大 `canSeeSpectatorInformation()`，也不显示尸体身份、天赋、死亡原因或其他旁观者信息。
- 身份文字使用与感知外框相同的目标可见性规则，并要求 Wathe 经 NoellesRoles/SparkStrength 包装后的目标姓名非空；不能绕过“隐蔽行动”、角色隐身或已经隐藏姓名的目标。
- 感知活动期间关闭这条近距离身份提示；普通模式下也不显示。

### Bloodthirsty Weapon Compatibility

- SparkTraits 新增专用 item tag `sparktraits:bloodthirsty_weapons`，基础值为 `wathe:knife` 与 `noellesroles:poison_needle`；`BloodthirstyCooldownMixin` 只把现有 `KillerTraitService.bloodthirstyCooldown(...)` 应用于该 tag。
- SparkWitch 通过 `data/sparktraits/tags/item/bloodthirsty_weapons.json` 以 `replace: false` 贡献 `sparkwitch:feather_blade`，不增加 SparkTraits runtime 依赖或公开 API。
- 不修改嗜血当前分配资格、层数上限、每层倍率或击杀确认语义；目标分支使用 3% 还是 5% 都继续由现有 `KillerTraitService` 决定。
- 毒针或羽刃开始物品冷却时，只按当时已有嗜血层数计算。之后的延迟死亡成功时新增层数只影响下一次冷却，不追溯缩短已运行的 cooldown；保护拦截或假死不增加层数。
- `sparkwitch:perception` 的 60/90 秒是角色技能冷却，不属于 item tag，不受嗜血影响。羽刃也不得进入 `thrust` 或左键冷却击退的其他武器 gate。

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

`#B04A8B` 与 NoellesRoles 巫毒师的 `#8072FD` 明确区分。两者机制也不同：巫毒师是好人身份，预先绑定任意目标，并在自己死亡后延迟 5 秒尝试杀死该目标；巫女是杀手身份，免疫这次最终死亡，并只在携带托法娜仙液且被另一名玩家直接击杀时立即反杀实际 killer。

### Voodoo Curse Immunity

- NoellesRoles 巫毒师仍可把巫女设为绑定目标；选人、30 秒技能冷却、绑定技能记录和绑定关系均照常产生。
- 巫毒师死亡后仍记录连锁触发，并让巫女进入原生 5 秒倒计时；动作栏提示照常显示。
- `WitchMaidenFeatureService` 注册角色自有 `KillPlayer.BEFORE` 监听并委托给纯 `WitchMaidenRules`。倒计时结束调用 `GameFunctions.killPlayer(...)` 时，只在 victim 的精确角色 id 为 `sparkwitch:witch_maiden` 且 death reason 为 `noellesroles:voodoo` 时返回 cancel。
- 取消最终死亡后不清除或重写 NoellesRoles 已产生的绑定、冷却、提示与记录，也不补发任何成功或失败反馈。
- 被取消的巫毒死亡不进入 `KillPlayer.AFTER`，因此不会触发或消耗巫女携带的托法娜仙液。
- 其他角色、其他 death reason，以及巫女受到的所有非巫毒伤害和击杀规则均不改变。该保护不放入 `GrandWitchRules`，也不把巫女视为魔女阵营成员。

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

- 聚焦步伐是背包头像技能，不使用现有身份技能键。身份技能键路径必须对精确的巫女角色跳过发送；服务端收到没有目标 UUID 的巫女技能请求时同样拒绝且不进入冷却。
- 客户端只在已确认 SparkWitch 服务端、当前玩家精确为巫女且仍在本局存活时，向 Wathe 现有 `LimitedInventoryScreen` 加入一排角色自有玩家头像按钮；不创建新的 screen。
- 候选来自客户端在线玩家列表，并以同步的 `GameWorldComponent.hasAnyRole(uuid)`、`isPlayerDead(uuid)` 过滤为本局仍存活的其他玩家。自己、未参与本局、已死亡、已离线的玩家不显示。
- 每页最多显示 10 个头像，使用与 SparkStrength 加强教授相同的 36 像素间距、紫色染料“上一页”和黄绿色染料“下一页”交互。翻页按钮只在对应页面存在时显示；重开背包保留客户端页码并按当前候选数重新 clamp。按钮 tooltip 使用 SparkWitch 自有本地化键，不复用 SparkStrength 文本。
- 头像显示 16x16 玩家皮肤，悬停显示玩家名。点击头像立即发送现有 `UseWitchSkillC2SPacket(Optional.of(targetUuid))`；SparkWitch 不导入、反射或依赖任何 SparkStrength 类。
- 没有候选目标时不显示翻页按钮，并在头像行位置显示 SparkWitch 自有的“没有可用目标”本地化空状态。
- 开局立即可用，initial cooldown 为 `0`。
- 服务端继续经过现有职业、存活、恐惧和技能 readiness 校验，并重新解析目标 UUID。目标必须在线、与施法者位于同一世界、参与当前游戏、仍存活且不是施法者本人。
- 阵营、距离、方块视线与可见性都不是限制条件；杀手队友和隐身的 Phantom 也可被选择。该交互不修改这些角色自身的规则。
- 客户端候选过滤只负责体验，服务端校验才是权威。断线、死亡、伪造 UUID、自己或其他无效目标都拒绝，并保持 cooldown 为 `0`。
- 成功后施加隐藏的 `sparkwitch:focused_footsteps` 状态效果 `600` ticks（30 秒），并立即进入 `1800` ticks（90 秒）冷却。90 秒从成功施法时开始，包含 30 秒效果期；效果自然结束时还剩 60 秒冷却。
- 如果目标已有该效果，再次成功施放会先移除旧实例再施加完整的 30 秒实例，重置跑/走阶段，并正常消耗本次 90 秒冷却。
- 施法者死亡不解除效果；目标死亡时清除效果。

现有右下角技能 HUD 和背包左上角技能信息继续显示聚焦步伐的持续时间、冷却与说明，不新增 HUD。客户端点击头像时只保存 pending target UUID；只有观察到 owner-only 同步冷却从 `0` 进入大于 `1200` ticks 后才确认本次成功，并以 `max(0, cooldownTicks - 1200)` 推导剩余效果时间。目标死亡、离线、角色变化、回合结束或断线时清理该 client-only 状态；服务端状态效果始终是玩法权威。无效请求没有冷却跃迁，因此不会伪造持续时间提示。

技能就绪时不再显示按键提示，而显示“打开背包，点击头像释放”；冷却期间头像仍可见但置灰且不可点击，冷却归零后重新启用，服务端始终保留最终校验。

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

SparkAssist 除一条技能发现排除规则外保持 resource/test-only。每个 JSON 使用：

本轮先交付巫女页面与 `focused_footsteps` 发现排除；这是所有者批准的交付顺序调整，Phase 2 实装时仍须按最终代码复核页面数值与交互描述。

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

Guidebook 的 `zh_cn.json` 和 `en_us.json` 都加入中文名字“黑羽鸦”与“巫女”，符合当前 Guidebook 强制中文与本地化测试契约。黑羽鸦页面明确写出羽刃延迟刺杀、保护拦截不返还冷却、感知册、60/15/90 秒、8 格每秒累积、10 点身份快照、50% 灰白和两种本能模式；巫女页面明确写出背包头像全局选人、30 秒持续、90 秒冷却、巫毒可绑定但最终死亡无效，以及毒苹果和托法娜仙液规则。

`sparkwitch:perception` 与 `sparkwitch:focused_footsteps` 仍注册在 SparkWitch 技能表中以复用现有冷却同步，但它们都是角色自有能力，不单独出现在 Guidebook 的 `SKILL` tab。SparkAssist 只在 `GuidebookDiscoveryRules.EXCLUDED_SKILL_IDS` 中加入这两个 id，并像现有 `sparkwitch:death_omen`、`sparkwitch:pig_chase` 一样断言 `SKILL` 不包含、`ROLE` 仍允许；不新增对应 `guidebook/skills/*.json`，也不修改 catalog 或 `ownerRoleIds` 规则。除这条排除规则外，不修改 parser、packet、其他 runtime、metadata 或版本。

## Delivery Phases

### Phase 1 Acceptance: Black Raven

- 完成角色注册、开局羽刃与感知册、限制商店、延迟标记、私有高亮、感知主动技能、通用技能键 2、本能双模式、近距离身份文字、贴图/model、语言与测试。
- SparkTraits 完成嗜血专用物品 tag、`isInstinctHidden(...)` 稳定门面与 focused tests；SparkWitch 只向 tag 贡献羽刃并通过既有 facade-only 反射边界查询“隐蔽行动”。完成 `black_raven.json`、`perception` 技能发现排除和 SparkAssist focused resource/discovery tests。
- 更新 `CONTEXT.md` 的 Product Boundary 与 Current Ownership，加入 `roles/killer/blackraven/`、角色自有组件和通用技能键 2；不改变受保护的 `WitchPlayerComponent.serverTick()` 顺序、NBT tail 或 packet tail。
- SparkWitch、SparkTraits 与 SparkAssist 各自通过 Java 21 focused tests、architecture check、full sequential build 和 `git diff --check`。
- 自动测试覆盖 3 格羽刃目标、20 秒一次性结算、保护拦截不返还 cooldown、册子不掉落/转移、60/15/90 秒、每目标 20 tick 累积与暂停恢复、10 点冻结快照、owner-only sync、模式重置、感知模式终止普通高亮、羽刃优先级、既有防透视优先级、2 格身份文本 gate 和嗜血 tag 隔离。
- 客户端验收友军/遮挡/重复标记、死亡与断线、无刀刺声、册子分页与身份色、实际失明、50% 灰白、Steve 皮肤、保留姓名、正常操作、普通/感知本能环境增亮、活动期间本能全禁用、默认 `N` 模式切换、右下角双行 HUD、近距离姓名与身份，以及 Phantom/“隐蔽行动”保护。

### Phase 2 Acceptance: Witch Maiden

- 完成角色注册、限制商店、背包头像聚焦步伐、巫毒最终死亡免疫、毒苹果、托法娜仙液、三件资源中的后两件、语言与测试。
- 完成 `witch_maiden.json`、`focused_footsteps` 技能发现排除和 SparkAssist focused resource/discovery tests。
- 更新 `CONTEXT.md` 的 Product Boundary 与 Current Ownership，加入 `roles/killer/witchmaiden/`；不改变受保护的 `WitchPlayerComponent.serverTick()` 顺序、NBT tail 或 packet tail。
- SparkWitch 与 SparkAssist 再次通过 Java 21 focused tests、architecture check、full sequential build 和 `git diff --check`。
- 自动测试覆盖现有目标 packet 的 UUID round trip 与字段顺序、仅巫女技能键排除、其他 SparkWitch 技能与 Saint 的空目标按键路径不变、精确角色 UI gate、每页 10 人的页数/clamp/翻页布局、服务端目标重验、无效目标零冷却、效果刷新、持续时间推导与 client-only 状态清理、精确 `noellesroles:voodoo` 拦截、Grand Witch 既有巫毒保护不变、其他角色与死因不受影响，以及 SparkAssist 不重复发现技能页。
- 客户端验收 0/1/10/11/20 人分页和空状态、不同 GUI scale、重开背包页码 clamp、自己/死亡/离线/伪造目标、全图隔墙目标、杀手队友和隐身 Phantom、巫女技能键无效但其他身份技能键正常、就绪/持续/冷却提示、头像冷却置灰与归零恢复、30 秒移动输入、90 秒即时冷却、耐力转阶段、FAKE/NONE 心情和 Hunter/Pig 优先级。
- 联动验收巫毒正常绑定、冷却、记录与 5 秒提示，最终巫毒死亡取消且不触发/消耗托法娜仙液，其他死亡仍生效；同时覆盖空盘/补餐/服务员/Noelles 饮品、双层毒、解毒、红粒子可见性和托法娜保护/消耗/归属。

## Concurrent Worktree Rules

SparkWitch 与 SparkAssist 当前都有其他未提交职业工作，中央 registry、items、components、events、skills、mixin configs、语言文件与 Guidebook aggregate tests 都是共享热点。执行每个任务前必须重新读取目标文件和其 diff，只合并本任务所需行；不得 reset、checkout、覆盖、重排、顺手重构、暂存或提交其他人的改动。

## Out Of Scope

- 不加入配置化价格、时间、距离、颜色或粒子。
- 除 SparkWitch 通用“技能键 2”和既有技能 HUD 的一行模式状态外，不新增自定义 HUD、screen、其他 keybinding、专属技能 packet、胜利逻辑、离线 UUID 击杀 API 或跨模组毒药 API；感知册复用只读书本界面，巫女头像控件只附着在 Wathe 现有背包 screen，并复用现有 packet 的目标 UUID 字段。
- 不修复 Wathe/NoellesRoles 与本设计无关的饮品、餐盘、奖励或 mixin 行为。
- 不阻止巫毒绑定，不改它的选人 UI、冷却、提示或记录；只取消巫女的精确巫毒最终死亡。
- 不更改巫毒师、善良杀手、共犯、大魔女、杀意魔女、Toxicologist、Hunter、Pig God、Phantom、Ninja 或 Saint 的自身规则。聚焦步伐能够全局选择杀手队友和隐身 Phantom 只是巫女自身的目标规则。
- 不 commit、push、发布或升版本，除非所有者另行明确要求。
