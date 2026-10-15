# 0.1.4：普通粒子的物理栏入口

## 原因

0.1.3 仅重写 FastCollisionEmitter.buildConfigurator，普通 ParticleEmitter 的原生 Inspector 不会调用它。之前切换到正式环斩的专用检测发射器，虽能证明扩展类型的控件可用，但没有解决用户在普通粒子上寻找物理栏入口的问题。重启和移动源码目录也不会改变这个限制。

## 修复

独立组件启动时注册 NeoForge ClientTickEvent.Post 监听器，通过 ModularUI 原生类型索引取得 FXEditor，向当前普通粒子的 Physics 分组添加唯一入口；Physics 按 sourceField 声明类与字段名定位，不按翻译文字或坐标查找。未点击时不改对象、资产和注册工厂；不使用 Mixin、核心补丁，不修改 Photon。

点击入口才将当前对象升级为 collision_emitter。转换使用原生序列化保留配置，原生 EditAction 记录可逆替换，保存 UUID、父子关系、列表顺序、兄弟顺序和 Timeline，保留当前播放状态并请求恢复时间轴位置。后端仍默认 NATIVE，实体与液体关闭，物理开关保持原值。控件继续使用 0.1.3 的完整物理栏嵌套和原生历史记录。

## 验证

15 项 JUnit 测试通过。`node integration/verify_native_editor.mjs --candidate` 在游戏进程中以独立临时编辑器通过入口、重复注入保护、原生注册表不变、点击回调、30 项控件、默认配置、变换／父子／排序／Timeline、编辑撤销重做、转换撤销重做与序列化回读。报告：`integration/native-editor-verification.json`；candidate 使用唯一副本 URLClassLoader，非正式启动验证。正式工程和用户当前编辑器内容未改动。

正式安装需要退出游戏后将 0.1.3 换为 0.1.4，再重启核实自动接入。候选测试不代表安装已完成。

2026-10-15 16:36 用户确认退出后已安装 0.1.4，mods 中仅保留此 helper 版本，原始 Photon SHA-256 保持 A128A358FB37F260F5D3BD84F0B0BBEEA2561487CEA365726B04F4DBB467E92E。旧版和安装记录保存于 `output/backups/碰撞组件_普通粒子物理入口前_20261015_163618`（相对工作区根目录）。正式启动后的自动接入验证仍待重启；详见 installation.json。
