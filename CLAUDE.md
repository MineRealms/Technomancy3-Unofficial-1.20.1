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
- **S3 仍缺（仅剩人眼验收）**：客户端几何/朝向、Lexicon 排版、魔力流体/桶仍需人眼。**节点创建与仪式的客户端特效已补齐**（见下）。
- **S4（深层 TC）**：完成。
  - 节点创建（`technom:node_fabricator` 成对 + 法杖右键 200 刻仪式，复用 `AuraNodeBlock.setPlacedBy` 路径，无 Mixin）。
  - **注魔稳定灯** `technom:flux_lamp`：ordo→不稳定度→淤泥，数值全按上游；写不稳定度是 TC4R 唯一没有公开口的动作，集中在 `compat/thaumcraft/ThaumcraftInternals` 用反射兜底（查不到字段就降级）。
  - **电动风箱** `technom:electric_bellows`：吹 1–2 格外的奥术炼金炉或 1 格外的原版熔炉；原版熔炉写不进燃料，改为一次充能买 80 tick 推进（等价于原版的 `burnTime=80` + 每 2 tick +1）。
  - **生态转换器** `technom:biome_morpher`：公开 `TaintSpreadLogic.setSpecialBiomeColumn`，无需 Mixin；右键切换魔法森林/阴森/污染之地。
  - **融合焦点恢复“吸收节点→右键空地再造节点”**（潜行右键吸收、空地右键立起，节点只存在于世界或焦点之一，不会复制或丢失）。
  - 三台机器都有配方、研究（`FLUXLAMP`/`ELECTRICBELLOWS`/`BIOMEMORPHER`）与模型，均**未实机验证**。
- **S5**：部分完成。GameTest 已验证服务端 `/reload` 数据包重载；**Jade/JEI/KubeJS 三处联动已实现**（见 [COMPAT 第 7 节](docs/COMPAT.zh-CN.md)）：Jade 覆盖 15 个方块实体、JEI 一个源质燃料值类别、KubeJS 只绑定 `Technom` 只读全局且默认不进运行时。专用服务器长期运行/多人/跨维度/死亡重生/守恒总验收仍未做。

## 最近一次全量验证（2026-09-29）

- `build`：**JUnit 238 通过 / 0 失败**（28 个测试类）。
- `runGameTestServer` ×3（默认 / `-PwithGtceu=true` / `-PwithBotania=false`）：**各 95/95 通过**（批次 `technom_s4_deep_tc` 是本轮新增；另加服务端 `/reload` 燃料表替换测试）。
- `python tools/validate_technom_data.py`：**OK: no errors**（16 warning / 1 skip；本轮修复前是 45 errors）。
- Rosetta 客户端探针（带 GTCEu + KubeJS）：**10/10 通过**（`probes/client/`）。其中 `10_`/`11_` 这一对是真正的渲染器检查：10 在**集成服务端**放置全部 44 个方块（旧版用 `mc.level.setBlock`，`ClientLevel` 根本不接受写入，所以此前从未通过），11 等 3 秒后确认客户端看到 44/44 方块、36 个方块实体、3 个已注册渲染器。两者通过 JVM 系统属性传递原点，因为测试世界是虚空、玩家会掉落，位置不能各自重算。`12_`/`14_` 从烘焙模型与顶点捕获取证（不依赖人眼），`40_`/`41_` 覆盖三处联动与 TC4R 的要素来源页。首轮 8/10 的两个失败都不是回归：`11_` 是世界同步竞态（`--attach` 重跑 44/44），`14_` 是探针自身的颜色常量写反。
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

1. **S5 总验收（现在是唯一剩下的阶段）**：专用服务器长期运行、多人联机、跨维度/死亡重生、持久化往返、守恒总账、研究/配方可达性全链、无 Botania 客户端（`-PwithBotania=false runClient` 未跑）；服务端 `/reload` 已由 GameTest 验证。Jade/JEI/KubeJS 联动已实现并有探针覆盖，但**仍未被人在游戏里看过排版**。
2. **人眼验收**：S4 三台机器的实机行为（尤其稳定灯对真实祭坛、风箱对真实炼金炉）、客户端几何/朝向、魔力流体与桶、Jade 提示行与 JEI 燃料页的排版。节点创建闪电与仪式光效已实现（数值/颜色有 JUnit 与 Rosetta 探针覆盖），但仍未被人眼确认过观感。
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
.\gradlew.bat runGameTestServer -PwithBotania=false --console=plain --no-daemon  # Botania 缺席安全
.\gradlew.bat runGameTestServer -PwithJade=false --console=plain --no-daemon     # Jade 缺席安全
.\gradlew.bat runGameTestServer -PwithKubejs=true --console=plain --no-daemon    # KubeJS 完整运行时
python tools\validate_technom_data.py                      # OK: no errors
```

**验证顺序有要求**：`validate_technom_data.py` 读的是**上一次** `runGameTestServer` 写出的 `run-gametest/technom-data-inventory.json`。必须紧跟在**带 Botania**的那次运行之后跑，否则 Botania 配方里的 `botania:*` 物品会被报成"不在物品注册表里"（11 条假错误）。带 Botania 的默认运行放在最后即可。

`-PwithKubejs=true` 会把 KubeJS 连同它声明为必需的 Rhino 与 Architectury 一起装进开发运行时（三者都是 mod，必须各自成 jar）。默认关闭：脚本引擎会改变启动与每次资源重载，普通测试不需要为它付出这个代价。它存在的意义是让 `probes/client/40_compat_plugins.java` 能真的验证插件被加载，而不只是验证发现文件写对了。

需要 JDK 17（`JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot`）。客户端实机探针见 README，**非阻塞**启动用 `Start-Process`。

## 来源与边界

相邻 `../Technomancy`、`../Technomancy-2` 是参考，不做机械合并。TC4R JAR/POM 由 `tools/bootstrap-local-deps.ps1` 导入，不提交 Git。旧代码/美术引入时保留来源与署名。每批提交写明实现、验证命令、结果与剩余限制。
