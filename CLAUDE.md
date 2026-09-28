# Claude Code 交接入口

这是独立的 Technomancy Forge 1.20.1 移植工程。**先读本文，再按“先读这些文件”顺序读**。不要把“能编译/测试通过”理解为玩法完成。

## 当前进度（核对日期 2026-09-28）

- **S0（工程/构建/文档）**：完成。
- **S1（TC4 精华闭环）**：完成（量子罐、源质发电机、能量凝聚器、S1-B 配方/研究）。
- **S2（核心机器）**：完成（节点机器、线圈、法杖/工具、储库/创造罐/净化器/分解台/吞噬器/融合器，以及两台补做的机器）。
- **S3（仪式联动）**：完成（本轮补齐了此前只有创造栏一条路径的部分）：
  - 核心方块（水晶×5、催化器×5、玄武岩、假空气光源）。
  - 仪式 **16/16**、仪式手册 + Screen。
  - Existence 全套（喷泉、燃烧器、塔三阶、使用器三变体、传输接口）。
  - 宝物村民 + 三件宝物；新增 `world.treasures` 配置，**默认关闭**（对应上游 `treasureSafeguard=false` 的净效果，此前本工程无条件开启）。
  - 玩家亲和/Existence 数据、被动效果、HUD + 网络同步。
  - Botania：四台机器按上游精确对齐、`mana_coil`/`manasteel_gear`、魔力流体 + 桶、灌注/合成配方 7 条、Lexicon 词条 5 条、模型/贴图，且缺席安全。
  - **本轮新增**：`technom:existence_gem`（上游 `ItemExistenceGem`，此前整个物品缺失，而它是所有 Existence 机器配方的核心材料）+ 21 条 S3 合成配方（水晶×5、催化器×5、手册、宝石、燃烧器×2、使用器×3、塔×3）+ **喷泉接入 Existence 网络**（此前不是 `IExistenceProducer`，塔抽不到它，网络是断的）+ `technom_s3_rituals` / `technom_s3_existence` 两个 GameTest 批次（此前 S3 零覆盖）。
- **S3 仍缺（仅剩人眼验收）**：仪式手册的 GUI 缩放；客户端几何/朝向、Lexicon 排版、魔力流体/桶、节点创建光效仍需人眼。
- **S4（深层 TC）**：完成。
  - 节点创建（`technom:node_fabricator` 成对 + 法杖右键 200 刻仪式，复用 `AuraNodeBlock.setPlacedBy` 路径，无 Mixin）。
  - **注魔稳定灯** `technom:flux_lamp`：ordo→不稳定度→淤泥，数值全按上游；写不稳定度是 TC4R 唯一没有公开口的动作，集中在 `compat/thaumcraft/ThaumcraftInternals` 用反射兜底（查不到字段就降级）。
  - **电动风箱** `technom:electric_bellows`：吹 1–2 格外的奥术炼金炉或 1 格外的原版熔炉；原版熔炉写不进燃料，改为一次充能买 80 tick 推进（等价于原版的 `burnTime=80` + 每 2 tick +1）。
  - **生态转换器** `technom:biome_morpher`：公开 `TaintSpreadLogic.setSpecialBiomeColumn`，无需 Mixin；右键切换魔法森林/阴森/污染之地。
  - **融合焦点恢复“吸收节点→右键空地再造节点”**（潜行右键吸收、空地右键立起，节点只存在于世界或焦点之一，不会复制或丢失）。
  - 三台机器都有配方、研究（`FLUXLAMP`/`ELECTRICBELLOWS`/`BIOMEMORPHER`）与模型，均**未实机验证**。
- **S5**：未做（专用服务器/客户端/多人/重载/守恒总验收）。

## 最近一次全量验证（2026-09-29）

- `build`：**JUnit 238 通过 / 0 失败**（28 个测试类）。
- `runGameTestServer` ×3（默认 / `-PwithGtceu=true` / `-PwithBotania=false`）：**各 94/94 通过**（批次 `technom_s4_deep_tc` 是本轮新增）。
- `python tools/validate_technom_data.py`：**OK: no errors**（16 warning / 1 skip；本轮修复前是 45 errors）。
- Rosetta 客户端探针（带 GTCEu）：**3/3 通过**。注意“桥就绪 ≠ 世界就绪”，世界加载完成前跑会全报放置失败，需重跑。
- 本轮修复的 10 个缺陷（含 21 个此前就已存在的“要工具却无 mineable 标签”方块）逐条记录在 [VALIDATION](docs/VALIDATION.zh-CN.md) 的 2026-09-29 一节。

## 先读这些文件（按顺序）

1. [README.md](README.md)：本机准备、构建/运行命令、**客户端实机探针操作**（Rosetta 桥、非阻塞开客户端、GT、quickPlay）。
2. [docs/ENGINEERING_GUIDE.zh-CN.md](docs/ENGINEERING_GUIDE.zh-CN.md)：架构、能源语义、TC4R API 边界、S0–S5 验收。
3. [docs/FEATURE_MATRIX.zh-CN.md](docs/FEATURE_MATRIX.zh-CN.md)：逐条功能的阶段与状态（S3 行已更新）。
4. [docs/BOTANIA.zh-CN.md](docs/BOTANIA.zh-CN.md)：Botania 四台机器的上游精确数值/角色/面/扳手规格与对齐清单。**已实施**；保留作复核依据，含已知偏差与未验证项。
5. [docs/COMPAT.zh-CN.md](docs/COMPAT.zh-CN.md)：原版 compat 清单、目标整合包（Society Sunlit Valley）环境、TC4R 20711/20721 API 差异、Thaumic Energistics 自动兼容与 Mixin 兜底。
6. [docs/SOURCE_BASELINE.zh-CN.md](docs/SOURCE_BASELINE.zh-CN.md)：取舍、固定提交、已确认旧问题。
7. [docs/VALIDATION.zh-CN.md](docs/VALIDATION.zh-CN.md)：实际执行的检查、未验证边界、GameTest 批次与结果。

## 下一步（按优先级）

1. **S5 总验收（现在是唯一剩下的阶段）**：专用服务器长期运行、多人联机、`/reload`、跨维度/死亡重生、持久化往返、守恒总账、研究/配方可达性全链、无 Botania 客户端（`-PwithBotania=false runClient` 未跑）、JEI/Jade 集成（目前没有任何代码）。
2. **人眼验收**：仪式手册缩放、S4 三台机器的实机行为（尤其稳定灯对真实祭坛、风箱对真实炼金炉）、客户端几何/朝向、魔力流体与桶、节点创建光效。
3. 全部完成后：`build` + `runGameTestServer`（有/无 GTCEu/无 Botania）+ Rosetta 客户端探针，并更新 FEATURE_MATRIX 与 VALIDATION。
4. 工程卫生：`main` 领先 `origin/main` 的提交需要推送；`s1x/assets`、`s2/coils`、`s2/machines`、`s2/nodes-wands` 四个分支已全部合并（0 ahead），但挂在另一个 worktree 上，不要删。

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
.\gradlew.bat runGameTestServer -PwithBotania=false --console=plain --no-daemon  # 缺席安全
python tools\validate_technom_data.py                      # OK: no errors
```

需要 JDK 17（`JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot`）。客户端实机探针见 README，**非阻塞**启动用 `Start-Process`。

## 来源与边界

相邻 `../Technomancy`、`../Technomancy-2` 是参考，不做机械合并。TC4R JAR/POM 由 `tools/bootstrap-local-deps.ps1` 导入，不提交 Git。旧代码/美术引入时保留来源与署名。每批提交写明实现、验证命令、结果与剩余限制。
