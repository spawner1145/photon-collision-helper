# Photon Collision Helper 0.1.4

工程目录：`D:/code/photon mcp/photon-collision-helper`。特效与备份仍位于项目根目录下的 `output`，游戏 mods 中已安装的组件不受工程移动影响。

可独立安装／移除的 **NeoForge 1.21.1 客户端碰撞扩展**。不修改 Photon 源码、不替换 Photon JAR、不使用 Mixin 或核心补丁。仅驱动特效表现，**不造成伤害、不破坏方块、不替服务器判定命中**。

## 安装与自动发现

依赖当前游戏的 Photon 2.2.8、LDLib2 2.2.42、NeoForge 21.1.252、Java 21。其他版本未经兼容性验证。

构建产物：`build/libs/photon-collision-helper-neoforge-1.21.1-0.1.4.jar`。复制至游戏实例的 `mods` 后重启 Minecraft。升级时先将旧 helper JAR 移出 mods，不能同时保留两个版本。组件通过 `@LDLRegisterClient(registry="photon:fx_object")` 注册 `collision_emitter`，由 Photon 现有扫描器加载；普通粒子的编辑器入口由独立组件通过 NeoForge 客户端事件自动添加，不需要修改 Photon、MCP 或在每次启动时执行脚本。

也可运行 `install.ps1` 安装，并在 `installation.json` 保存 helper 与原始 Photon 的 SHA-256。`rollback.ps1` 会先备份当前环斩、恢复原生后端，再删除 helper；回退应先退出游戏，重启后生效。脚本默认使用当前游戏实例，可通过 `-GameDirectory` 指定其他兼容实例。

MCP 正常工作流：

1. `photon_list_object_types` 可发现 `collision_emitter`。
2. `photon_describe_object_type(type="collision_emitter")` 返回继承的粒子配置和完整 `data.collision` 类型结构。
3. 使用现有对象创建／编辑接口创建该类型，或将工程中的检测发射器转换为该类型。材质、形状、Transform 和 Timeline 动画仍用 Photon 原生接口。

**不会偷偷替换所有原生发射器**：旧 `particle_emitter` 默认行为不变，需要碰撞扩展的对象才使用新类型。环斩的三个检测发射器已接入；其气浪仍纯视觉，不做碰撞查询。其他特效未批量改写。

`integration/mcp-schema.json` 保存了实测 MCP 返回的结构；实际编写配置请优先重新描述类型，而不是猜字段。

## 编辑器入口

0.1.4 修复「只有专用扩展类型才能看到入口」的问题：选择任意普通 **粒子发射器**（内部类型 `particle_emitter`）→ **物理** → **碰撞扩展** → **启用扩展设置（保留原版默认）**。入口位于原版反弹扩散率下方，无需先换工程或使用 MCP。重启后由组件自动接入，再次打开／切换工程或重新选择粒子也会显示；无需保存工程来保留入口本身。

没有点击按钮时，普通发射器的数据、类型和碰撞完全不变。点击后仅把当前对象原地升级成 `collision_emitter`，保留 UUID、名称、物理、材质、Transform、父子层级、排序和 Timeline；支持原生撤销／重做。默认后端仍为 NATIVE，不主动开启实体、液体或物理开关。扩展参数修改后需像其他属性一样保存工程，才能在下次打开时保留。普通 `particle_emitter` 注册工厂不被替换，不改 Photon 类，也不会批量迁移旧资产。

已有的 **碰撞扩展粒子发射器** 可直接在 **物理 → 碰撞扩展** 编辑全部字段。原生的重力、摩擦、碰撞移除、反弹率等配置仍在同一个物理栏，新增功能不会覆盖它们。环斩已有的三个 `11 Native Blue Blade Block Sweep` 检测对象就是扩展发射器，选中即可编辑。

启用物理栏后，扩展内可选择原版／缓存后端；其余字段按「检测对象与探针」「方块过滤」「实体过滤与归属」「碰撞响应」「去重与冷却」「命中子特效」「性能缓存」分组。每个新增字段都有原生可编辑控件、中英文名称和提示，数字有合法范围及默认值；命中特效可搜索已加载的 FX ID，也可清空。新增字段不再只是 MCP 接口可访问。

控件使用 LDLib2 原生变更事件，沿 Inspector 的原生快照历史机制记录撤销／重做。界面位置移到物理栏不改变保存结构：扩展仍存 `data.collision`，原生物理仍存 `data.config.physics`，禁用物理也不会丢弃预先配置的扩展参数。`integration/verify_editor.mjs` 用原生 Inspector 控件检查分组、字段覆盖、实际值编辑、撤销／重做、序列化回读及特效搜索；需新版在游戏中正式加载后运行。

普通 `particle_emitter` 在保留原有面板和行为的同时增加物理栏入口；组件没有改写原生类或偷偷把旧发射器全替换。也可从编辑器对象菜单直接新建「碰撞扩展粒子发射器」。此类型的默认后端仍是原版，实体与流体默认关闭。环斩为了已有性能优化明确选择缓存后端，但仍仅检测方块。

## 碰撞能力

**默认保留 Photon 原版行为。** `collision.backend=NATIVE` 直接创建 Photon 原生 `TileParticle`，不经过扩展扫掠、过滤、去重或自定义事件代码。发射器构造函数不再强行开启 Physics，不改重力等原生默认配置。旧的 `particle_emitter` 也始终不被替换。

要使用扩展，先显式设置 `collision.backend=CACHED_SWEEP`，再开启原生 `config.physics._enable` 和 `hasCollision`。这也让 Photon 将世界查询保持在主线程，而非并行粒子任务。缓存扫掠并非 Photon 原版轴向移动求解器，不能把参数继承等同于算法完全一致；要求完全原版时使用 NATIVE。

| 字段 | 行为 |
| --- | --- |
| `backend` | 默认 `NATIVE`；只有明确选择 `CACHED_SWEEP` 才启用下列扩展 |
| `blocks` / `entities` / `fluids` | 缓存模式的独立开关；默认仅方块开启，实体和流体均关闭 |
| `includedBlockTypes` / `excludedBlockTypes` | 方块 ID 白／黑名单，也作用于流体所在方块 |
| `entityFilter` | `ALL`、`LIVING`、`PLAYERS`、`NON_LIVING`；默认生物 |
| `includedEntityTypes` / `excludedEntityTypes` | 精确实体类型 ID 白／黑名单 |
| `ignoreOwner` / `ownerEntityId` | 自动识别原生 EntityEffectExecutor 的实体；非实体执行器可显式指定 ID |
| `sourceEntityExclusionRadius` | 默认 0；正数时首次模拟在运行时根节点中心建立世界单位球形出生区，记住当时与球相交的实体 ID，本次释放不碰这些实体；不屏蔽方块、不随源点移动扩大名单 |
| `ignoreSpectators` / `pickableOnly` | 默认忽略旁观者；可选仅可选中实体 |
| `sweepEntityMotion` | 可选用实体前后 tick 位移作线性相对运动估计；不是服务器运动预测 |
| `rayProbe` | 用点扫掠代替粒子世界轴对齐盒；适合细射线 |
| `detectStationaryOverlaps` | 静止粒子的重叠检测；可配合 PIERCE 做范围传感器 |
| `deduplicateParticleHits` | 默认关闭；开启后每粒子每目标只通知一次，不跳过物理阻挡 |
| `deduplicateEmitterHits` / `deduplicationGroup` | 同运行时多个发射器共用目标事件去重；组名为空时仅当前发射器 |
| `contactCooldownTicks` | 0 表示整个本次运行只通知一次；正数表示经过指定模拟 tick 可再次通知 |
| `blockEffect` / `entityEffect` / `fluidEffect` | 按目标类型额外触发的 FX ID，可留空；与原生子发射事件叠加 |
| `alignEffectsToNormal` | 子特效 Y 轴沿表面法线；默认保持原有方向 |
| `cachedTileLimit` | 共享 tile 缓存数量上限；超出后重算，不丢检测 |

ID 列表用逗号分隔，例如 `minecraft:zombie,minecraft:skeleton`，不是实体 tag 表达式。

方块命令执行器没有施放者身份，不能靠 `ignoreOwner` 凭空推断是谁释放。环斩现为 `CACHED_SWEEP + blocks=true + entities=false + fluids=false + response=INHERIT`，只在碰到方块时生成切割爆发，继承原配置的碰撞移除设置；显式保留已有每方块事件去重优化。`sourceEntityExclusionRadius` 恢复为 0，不用特殊出生区策略代替关闭实体碰撞。确需实体碰撞时才手动开启 entities，再选择显式 ownerEntityId／原生实体执行器或可选出生区策略。

`response` 支持：

- `INHERIT`：缓存模式继承 Photon 移除、反弹概率、反弹率及散射参数，不强制使用 REMOVE；默认不额外限制反弹次数。完全原版求解使用 NATIVE。
- `REMOVE`：首次真实接触处移除，适合命中爆炸。
- `STOP`：停止在接触处，仍受之后施加的力／重力影响。
- `BOUNCE`：按法线反射剩余行程及存储速度，`restitution` 为 0–1；`maxBounces=0` 表示不限制总反弹次数。
- `SLIDE`：去掉朝表面内部的速度分量，保留切向运动。
- `PIERCE`：继续本步行程，依次通知经过的目标，同一步不会反复命中同一目标。

`maxHitsPerStep` 默认 32。达到上限时停在最后已验证的接触处，不把未经检测的剩余行程直接放行。默认上限是保护机制，不是“穿过 32 个障碍后忽略剩下的”。不建议设为非正数以取消上限。

原生 `Collision`、`FirstCollision`、碰撞移除对应的 `Death` 子发射事件可继续使用，保留概率及颜色／大小等继承设置。出生、自然寿命死亡和 Tick 事件仍由 Photon 原生处理。碰撞子效果保存真实接触位置快照后入队，避免父粒子后续移动带偏爆炸位置。

## 算法与边界

- 同一个 FXRuntime 的扩展发射器共享 8 格 tile；同模拟 tick 的方块 VoxelShape 只读取一次，下一 tick 重建，支持动态放置／移除障碍。
- 真实世界跳过空区段，不强制加载未加载区块。编辑器的稀疏虚拟场景直接索引已有方块，避免对大范围空场逐格查询。
- 小扫掠枚举相交 tile；长距离斜向扫掠采用粗网格 3D DDA，结合粒子半尺寸邻域和精确 tile 检测，不扫描巨大对角包围盒的全部体积。
- 窄相位是 Minkowski 扩张后的连续 slab / TOI 检测，保留台阶、半砖、栅栏等真实碰撞形状；不走原生高速时跳过检测的路径。
- 实体每 tick 构建一次空间哈希。大实体超过索引格数上限时进入溢出候选表，不被丢弃。真实世界预览的代理世界也会正确查询代理中的实体。
- 缓存与去重状态由运行时所有，不使用持有世界的全局静态缓存；重放、世界切换、重置会清理。超出缓存容量只影响速度，不降低碰撞覆盖。

这是平移的世界轴对齐盒／点扫掠，不是任意三角网格连续碰撞；不自动模拟粒子 Local 空间父节点瞬移形成的轨迹。实体线性扫掠也是近似，可关闭。客户端只知道已加载、已同步到客户端的方块和实体。同一个模拟 tick 内发生的世界修改在下一模拟 tick 反映。

`FastCollisionEmitter.collisionStatistics()` 提供累计命中数、粒子模拟耗时、共享缓存统计及最近 128 次接触的目标、位置、法线和 tick，MCP 的原生执行接口可直接读取。`CollisionFrame.nearest(...)` 是可复用的扫掠查询接口；零位移配合非零 AABB 可做重叠查询。

## 验证与回退

```powershell
& '../PhotonMCP-1.21/gradlew.bat' -p . --offline --console=plain test jar
node integration/verify.mjs
node integration/verify_ring_entities.mjs
node integration/verify_native_editor.mjs
```

游戏内集成测试仅使用临时虚拟世界，不修改真实地图或造成伤害。几何单元测试、缓存／方块形状／实体过滤／流体／高速 DDA／持久化测试、六种响应与环斩实体命中测试报告都保存在 `integration`。`--reload` 仅是开发时的临时类加载验证，不是用户安装步骤。

`integration/verify_native_editor.mjs` 使用独立临时 FXEditor 验证普通粒子的物理栏入口、不重复注入、按钮实际回调、30 项控件、默认行为、原生注册表不变、配置与层级完整、编辑及类型转换的撤销／重做、保存回读。`--candidate` 使用唯一副本 JAR 做候选实现验证，不替换已加载 JAR，也不等同于正式启动自动注册验证。

0.1.0 的正式启动验证已通过，`integration/startup-verification.json` 保存当时证据。0.1.2 恢复原版默认行为并关闭环斩实体碰撞，新增单元测试检查所有公开碰撞字段同时有 Configurable／Persisted 注解，原版配置与原生粒子类型的游戏内检查位于 `integration/verify_particles.groovy`。安装后仍需重启复测。本实例保留 `config/fml.toml` 的 `earlyWindowControl=false` 启动规避配置，未修改 Photon 本体。

`integration/audit_assets.py` 对比 `../output` 中的原生备份，确认网格、七个 Shader Graph、噪声贴图、主 Timeline、三片探针的尺寸／位置／发射率及两个碰撞子工程均未更改；只有三个探针新增了扩展类型及设置。

环斩的探针数量、范围、高度和视觉 Shader 均保留；性能结果在 `../output/须佐能乎_环斩冲击/editor/performance-helper-final.json`，对照 `performance-cpu-diagnosis.json`。这些是编辑器场景 CPU tick 测量，不代表所有地图／GPU／密集实体场景的 FPS 保证。大面积命中后的许多体积爆炸仍可能有 GPU 开销。

卸载前先恢复原生碰撞环斩备份：`../output/backups/须佐环斩_算法性能优化前_20261015_141539/须佐能乎_环斩冲击`，包括工程、游戏 loose assets 和 fxpack，然后移除 helper JAR 并重启。未恢复时打开扩展类型工程会缺少对象类型。Photon 原始 JAR 不需替换或回滚。
