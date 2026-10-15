# 0.1.3：原生编辑器物理栏

通过扩展发射器的原生 `buildConfigurator` 接口，将注解生成的 collision 分组移动到 ParticleConfig 的 physics 分组内。定位使用原生 `sourceField` 元数据，不依赖中文／英文显示标签或屏幕坐标，保留原控件、变更事件和序列化字段。

所有公开扩展字段继续由 Configurable／Persisted 驱动，并增加中英文名称、悬浮提示、分类、数值范围、正确默认值、枚举翻译及已有特效搜索。单元测试覆盖每个字段的声明、分类与两种语言的标签／提示／枚举条目，不改变碰撞算法、原版默认模式或正式环斩的仅方块规则。

游戏内结构、控件输入、撤销／重做与保存回读测试：`integration/verify_editor.mjs`。重启加载 0.1.3 后运行；编译和单元测试不能替代界面实测。

2026-10-15 游戏内实测通过：正式启动加载的 0.1.3 位于 TransformingClassLoader／photon_collision_helper 模块，非临时热加载。30 项控件全部位于 physics → collision 下；布尔、数字、枚举、文本、特效搜索、撤销／重做及序列化回读全部通过，报告见 `integration/editor-verification.json`。编辑器完整截图确认原版反弹扩散率下方显示「碰撞扩展」，其中检测方块开启、实体和液体关闭。此前用户打开的是优化前的原生备份，不含扩展；现已打开正式 `susanoo_ring.fxproj`，选中首个 11 检测发射器并展开物理／碰撞扩展，未改动正式资产。

仅扩展类型追加控件；普通原版发射器不被全局替换。Photon 本体保持不变。
