# Claude Code 交接入口

这是独立的 Technomancy Forge 1.20.1 移植工程。**先读本文，再按“先读这些文件”顺序读**。不要把“能编译/测试通过”理解为玩法完成。

## 当前进度（核对日期 2026-09-28）

- **S0（工程/构建/文档）**：完成。
- **S1（TC4 精华闭环）**：完成（量子罐、源质发电机、能量凝聚器、S1-B 配方/研究）。
- **S2（核心机器）**：完成（节点机器、线圈、法杖/工具、储库/创造罐/净化器/分解台/吞噬器/融合器，以及两台补做的机器）。80/80 GameTest 有/无 GTCEu 通过，JUnit 229 通过。
- **S3（仪式联动）**：**除客户端实机与 Botania 缺席安全外完成**：
  - 核心方块（水晶×5、催化器×5、玄武岩、假空气光源）。
  - 仪式 **16/16**、仪式手册 + Screen。
  - Existence 全套（喷泉、燃烧器、塔三阶、使用器三变体、传输接口）。
  - 宝物村民 + 三件宝物。
  - 玩家亲和/Existence 数据、被动效果、HUD + 网络同步。
  - Botania：**四台机器全部按上游精确对齐**（FlowerDynamo/ManaFabricator/BOProcessor/ManaExchanger），`mana_coil`/`manasteel_gear`、**魔力流体 + 桶**、**Botania 灌注/合成配方 7 条**、**Lexicon 词条 5 条**、**机器模型/贴图**全部落地。83/83 GameTest 有/无 GTCEu 通过，JUnit 233 通过。
- **S3 仍缺（仅两项，且都不是功能）**：Botania 不在运行时时机器类会 `NoClassDefFoundError`（未做缺席安全门控）；客户端实机渲染（模型/图集/池覆盖层/Lexicon 显示）未重跑探针。
- **S4/S5**：未做（稳定灯、电动风箱、生态转换器、节点创建；以及专用服务器/客户端/多人/重载/守恒总验收）。

## 先读这些文件（按顺序）

1. [README.md](README.md)：本机准备、构建/运行命令、**客户端实机探针操作**（Rosetta 桥、非阻塞开客户端、GT、quickPlay）。
2. [docs/ENGINEERING_GUIDE.zh-CN.md](docs/ENGINEERING_GUIDE.zh-CN.md)：架构、能源语义、TC4R API 边界、S0–S5 验收。
3. [docs/FEATURE_MATRIX.zh-CN.md](docs/FEATURE_MATRIX.zh-CN.md)：逐条功能的阶段与状态（S3 行已更新）。
4. [docs/BOTANIA.zh-CN.md](docs/BOTANIA.zh-CN.md)：Botania 四台机器的上游精确数值/角色/面/扳手规格与对齐清单。**已实施**；保留作复核依据，含已知偏差与未验证项。
5. [docs/COMPAT.zh-CN.md](docs/COMPAT.zh-CN.md)：原版 compat 清单、目标整合包（Society Sunlit Valley）环境、TC4R 20711/20721 API 差异、Thaumic Energistics 自动兼容与 Mixin 兜底。
6. [docs/SOURCE_BASELINE.zh-CN.md](docs/SOURCE_BASELINE.zh-CN.md)：取舍、固定提交、已确认旧问题。
7. [docs/VALIDATION.zh-CN.md](docs/VALIDATION.zh-CN.md)：实际执行的检查、未验证边界、GameTest 批次与结果。

## 下一步（Botania 已收尾，按优先级）

1. **客户端实机总验收**：按 README 的非阻塞探针起客户端，重跑 `python tools\client_probe.py probes\client --attach`。重点看 Botania 四台机器的新模型、`mana_fluid`/`mana_bucket`、池覆盖层、以及 Lexicon 词条是否出现在 Botania 手册里（Patchouli 的 `use_resource_pack` 跨命名空间加载尚未在客户端确认）。
2. **Botania 缺席安全（可选，但被 ENGINEERING_GUIDE 要求）**：把 Botania 机器及其方块/物品/配方的注册、能力与 API 引用收进存在性门控，使缺少 Botania 时核心 TC4R+FE 路线可完整启动。
3. **S4/S5**：稳定灯、电动风箱、生态转换器、节点创建；专用服务器/多人/重载/守恒总验收。
4. 全部完成后：`build` + `runGameTestServer`（有/无 GTCEu）+ 客户端探针，并更新 FEATURE_MATRIX 与 VALIDATION。

## 已确定的方向（不可随意改）

- Minecraft 1.20.1、Forge 47.4.23、Java 17、ForgeGradle 6.0.54。`mod_id=technom`，包 `theflogat.technomancy`。
- TC4R `0.1.0-20721` 必需（运行时 `4.2.3.5-1.20.1-port.0.1.0-20721`）。**注意**目标整合包的 TC4R 是 20711，差异见 COMPAT；`AspectQueryApi` 是 20721 新增。
- 去掉 Thermal Expansion/CoFH RF；Forge Energy 为基础，GTCEu 7.5.3 为可选 EU。GT API 始终 compileOnly。
- **Blood Magic 相关一律不做**（用户明确排除）。Botania 保留为 S3 选装。
- 1.7 基准是 `../Technomancy` 的 `37bf9a5`；`../Technomancy-2` 的 `223160a` 仅作资源/经验参考；两者无共同祖先，不做机械合并。
- **所有功能要与上游严格对应**（贴图、模型、物品、代码逻辑、数值、功能），不要用近似值；发现偏差按上游改，并记录到 VALIDATION。

## 日常构建与测试

```powershell
.\tools\bootstrap-local-deps.ps1
.\gradlew.bat build --console=plain --no-daemon            # JUnit
.\gradlew.bat runGameTestServer --console=plain --no-daemon            # 无 GT
.\gradlew.bat runGameTestServer -PwithGtceu=true --console=plain --no-daemon
python tools\validate_technom_data.py                      # OK: no errors
```

需要 JDK 17（`JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot`）。客户端实机探针见 README，**非阻塞**启动用 `Start-Process`。

## 来源与边界

相邻 `../Technomancy`、`../Technomancy-2` 是参考，不做机械合并。TC4R JAR/POM 由 `tools/bootstrap-local-deps.ps1` 导入，不提交 Git。旧代码/美术引入时保留来源与署名。每批提交写明实现、验证命令、结果与剩余限制。
