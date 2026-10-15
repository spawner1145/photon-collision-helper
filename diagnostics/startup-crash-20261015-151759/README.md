# 2026-10-15 启动崩溃诊断

崩溃发生于游戏初始化，尚未进入世界或播放特效，日志中也尚未执行 helper 的模组构造函数。

直接异常路径：

`BackgroundScanHandler.waitForScanToComplete` → `ImmediateWindowHandler.renderTick` → `DisplayWindow.periodicTick` → GLFW 尺寸回调 → Minecraft `resizeDisplay` → Photon `IrisCompat` 静态初始化 → LDLib2 `Platform.isModLoaded` → `ModList.get()` 为 null。

因此，这是模组列表构造完成前的早期窗口回调触发了 Photon 兼容层初始化。仅从本次日志不能判定新增 JAR 是否通过改变扫描耗时而诱发了该时序。

不修改 Photon 或 LDLib2 的处理：将本实例 `config/fml.toml` 的 `earlyWindowControl` 从 true 改为 false，禁用 NeoForge 的早期加载窗口。已检查本机 NeoForge/FML 4.0.44 字节码：false 会选用 `DummyProvider`，避免本次堆栈中的早期 `DisplayWindow.periodicTick` 路径。影响是失去早期加载窗口及其 GL 初始化路径，游戏内辉光、折射、特效资源没有修改；游戏内实际渲染仍须重启后验证。

原配置保存在 `fml.before.toml`，需要回退时恢复其中的 `earlyWindowControl = true`。helper JAR、Photon JAR 与特效文件保持原样。该设置是针对启动问题的规避措施，并不是对 Photon 中缺少初始化保护的代码修复。

状态：用户重启并进入世界后验证通过。启动日志确认早期窗口禁用，helper 模组构造函数正常执行；原生模块 `photon_collision_helper` 使用 `TransformingClassLoader` 自动注册 `collision_emitter`，未使用临时类加载器。MCP 类型发现与字段描述、方块／实体／液体查询、碰撞响应、环斩实体过滤及白刃碰撞带、辉光与原生 Viewport 折射测试均通过。详见 `../../integration/startup-verification.json` 及同目录测试报告。这是本次启动与回归验证结论，不代表已覆盖所有启动环境。
