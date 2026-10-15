# Photon Collision Helper 0.1.4

可独立安装／移除的 **NeoForge 1.21.1 客户端碰撞扩展**。

## 安装与自动发现

首次构建时将 `local.properties.example` 复制为 `local.properties`，填写 Minecraft 实例目录（包含 `mods` 的文件夹，路径用 `/`）。本机路径不再写入受 Git 跟踪的 `gradle.properties`；也可用 `-PgameDir=...` 或环境变量 `PHOTON_GAME_DIR` 指定，优先级依次为命令行／Gradle 属性、环境变量、本地配置，未配置时查找工程内的 `run`。

依赖 Photon 2.2.8、LDLib2 2.2.42、NeoForge 21.1.252、Java 21

构建产物：`build/libs/photon-collision-helper-neoforge-1.21.1-0.1.4.jar`。复制至游戏实例的 `mods` 后重启 Minecraft。升级时先将旧 helper JAR 移出 mods，不能同时保留两个版本。组件通过 `@LDLRegisterClient(registry="photon:fx_object")` 注册 `collision_emitter`，由 Photon 现有扫描器加载；普通粒子的编辑器入口由独立组件通过 NeoForge 客户端事件自动添加，不需要修改 Photon、MCP 或在每次启动时执行脚本。

也可运行 `install.ps1` 安装，并在 `installation.json` 保存 helper 与原始 Photon 的 SHA-256。`rollback.ps1` 原来只处理环斩；批量迁移后它会安全拒绝直接卸载，避免其余七套特效缺少类型。应先退出游戏，按批量备份的 `migration.json` 恢复所有依赖组件的工程及安装资源，再移除 helper，重启后生效。脚本默认使用当前游戏实例，可通过 `-GameDirectory` 指定其他兼容实例。

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