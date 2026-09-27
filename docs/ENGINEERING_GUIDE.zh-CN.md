# Technomancy 1.20.1 总工程指导

文档基准：2026-09-27。目标是把 Technomancy 原版的成熟玩法与 Technomancy-2 中经审查有价值的改进，迁入独立的 Minecraft 1.20.1 / Forge 工程。本文同时记录实施顺序、设计约束、API 边界和验收条件。

**状态声明：当前工程是初始化骨架。模组入口与构建、依赖配置不等于玩法移植；机器、物品、研究、配方、FE/EU 存储、协议适配和游戏测试均须按后续里程碑实现。本文中的建议类名、算法、测试项和功能表是施工规范，不是完成清单。** 实际构建验证结果以仓库 README、构建输出和后续验收记录为准，不从本文推断。

## 目录

- [1. 目标和范围](#1-目标和范围)
- [2. 来源取舍与可追溯性](#2-来源取舍与可追溯性)
- [3. 工程基线与日常命令](#3-工程基线与日常命令)
- [4. 推荐架构与依赖方向](#4-推荐架构与依赖方向)
- [5. 功能分组和恢复依赖](#5-功能分组和恢复依赖)
- [6. 注册、生命周期与能力](#6-注册生命周期与能力)
- [7. FE 与 GTCEu EU 能源设计](#7-fe-与-gtceu-eu-能源设计)
- [8. 魔法资源与加工平衡](#8-魔法资源与加工平衡)
- [9. TC4R API 对照和扩展边界](#9-tc4r-api-对照和扩展边界)
- [10. 研究、配方与数据生成](#10-研究配方与数据生成)
- [11. 存档、网络与客户端](#11-存档网络与客户端)
- [12. 世界操作、多方块与性能](#12-世界操作多方块与性能)
- [13. 已知旧问题和迁移处置](#13-已知旧问题和迁移处置)
- [14. 里程碑与验收门槛](#14-里程碑与验收门槛)
- [15. 测试矩阵和发布边界](#15-测试矩阵和发布边界)
- [16. 开始下一项功能的工作模板](#16-开始下一项功能的工作模板)
- [17. 证据索引](#17-证据索引)

## 1. 目标和范围

### 1.1 固定目标

| 项目 | 工程目标 |
|---|---|
| Minecraft | 1.20.1 |
| 加载器 | Forge 47.4.23，基于官方 MDK |
| Java | 17 |
| ForgeGradle | 6.0.54 |
| Gradle Wrapper | 8.8 |
| Mod ID / 资源命名空间 | `technom` |
| Java 根包 | `theflogat.technomancy` |
| 必需魔法依赖 | TC4R Maven 坐标 `dev.tc4port:thaumcraft-forge:0.1.0-20721` |
| 基础电力协议 | Forge Energy，使用 Forge 自带接口 |
| 可选工业集成 | GTCEu Modern 7.5.3，原生 EU 输入和输出 |
| 后续可选魔法模块 | Botania、Blood Magic，分别核对其 1.20.1 API 后实施 |

精确版本以 [gradle.properties](../gradle.properties)、[build.gradle](../build.gradle)、[Gradle Wrapper 配置](../gradle/wrapper/gradle-wrapper.properties) 和 [mods.toml](../src/main/resources/META-INF/mods.toml) 为构建权威。升级版本必须重新核对本指南中有版本约束的 API，尤其是 TC4R 内部桥接和 GTCEu capability。

TC4R 的 Maven 制品版本与游戏内模组版本不同：当前运行依赖精确锁定 `[4.2.3.5-1.20.1-port.0.1.0-20721]`。GTCEu 是可选运行模组，当前版本范围精确锁定 `[7.5.3]`。不要把 Maven 版本直接填写为 TC4R 的运行范围，也不要据此宣称其他 TC4R/GT 版本已经兼容。

### 1.2 玩法与协议的区别

- 移除 Thermal Expansion、CoFH Redstone Flux 的源码/API/运行依赖，重新设计原来使用其部件的配方。
- 需要 RF 的原版设备迁为 FE，并增加按电压和安培工作的 GTCEu EU 接口。
- 精华、节点 Vis、法杖 Vis、Mana、LP、Existence 是独立玩法资源。替换 RF 不等于取消这些资源，也不等于所有机器都改成纯电力机器。
- 原生 EU 兼容包括 Technomancy 发电机向 GT 电网送电、耗能设备从 GT 电网受电、方向和电压约束；仅让 GT 的 FE 包装器识别设备不足以证明这些要求全部满足。
- Botania/Blood Magic 缺席时，基础 TC4R + FE 路线应完整可玩；涉及缺席模组的研究、物品和配方不能形成不可达前置。

### 1.3 暂不承诺的范围

当前不承诺旧 1.7.10/1.12 存档可直接打开，不承诺其他 TC4R 构建号或 GTCEu 8.x 兼容，不把未注册的原版实验内容自动列为正式移植范围。上述能力只能在各自实现、验证后增加到发布说明。

## 2. 来源取舍与可追溯性

### 2.1 三类来源各负其责

| 来源 | 固定提交 | 使用方式 |
|---|---|---|
| 原版 `Technomancy` master | `37bf9a56fe1f713258f298d7ef392b104ccae88f` | 完整 TC4 玩法、数值、配方和交互的行为基准 |
| 原版 `origin/1.12` | `e1b146ed6e651739af4b50af0e55ad917370f6ea` | 对照平台迁移意图，不作为完整 TC4 实现 |
| `Technomancy-2` master | `223160a924e6f2c3c2170f2b9fa8b291a07cd31b` | 选择吸收资源、FE 支持意图、修复意图和少量配方变化 |
| 新工程 | 当前仓库 | Java 17、现代注册、数据、能力、网络、渲染和验证的实际实现 |

完整研究材料见相邻目录的 [合并审计报告](../../research/technomancy-merge-audit-20260927/REPORT.zh-CN.md) 和 [对象级清单](../../research/technomancy-merge-audit-20260927/inventory.json)。这些链接是本工作区的辅助材料；对外分享时应随附对应文档或使用下方固定提交链接。

原版与 Technomancy-2 没有共同 Git 祖先，强制合并会产生大量 add/add 冲突。原版 `origin/1.12` 与 Technomancy-2 的 218 个 main/java 文件中有 213 个相同，只有 5 个文件存在差异，其中 3 个包含有效代码变化。因此不要把两个工程理解成两套互补、可以直接拼接的完整版本。

具体采取以下方式：

1. 在本工程中先完成自洽的现代共享层。
2. 每次按一个功能组读取原版行为，写现代实现和对应验证。
3. 逐项比较 Technomancy-2 的变化，记录“采纳、重新实现、拒绝、暂缓”及原因。
4. 模型、贴图、语言文本逐个记录来源；更新格式和路径后再纳入。
5. 不把旧 `src/api` 第三方接口、旧二进制依赖、旧 ASM 加载器打包进新模组。

### 2.2 合并不等于恢复目录

原版有 40 个 `/thaumcraft/` 路径 Java 文件，加上 `lib/compat/Thaumcraft.java` 为 41 个核心文件；它们还依赖节点占位块、机器共享基类、连接工具、客户端和研究配方。Technomancy-2 删除/关闭了 TC 部分，而共享基类已改为 1.12 形态，直接复制会同时触发接口、生命周期、坐标和数据格式冲突。

原版 `TileDynamoBase.updateEntity()` 是 tick，`update()` 是刷新；1.12 把二者改为 `update()` 与 `update2()`。这类同名异义必须按实际行为重建，不能进行全局字符串替换。

### 2.3 许可证与署名

[原版 CurseForge 项目](https://www.curseforge.com/minecraft/mc-mods/technomancy) 标注 Apache License 2.0；已审计的固定原版 Git 树没有根 LICENSE 文件。新工程的新代码和文档使用根 [LICENSE](../LICENSE) 声明，官方 MDK 原有许可证和署名单独保留。MDK 的原 `LICENSE.txt` 不能被解释为 Technomancy 所有旧资源的许可声明。

迁入旧源码/资源时记录仓库、提交、原路径和版权头，保留适用署名与通知；存在单独许可的第三方资源按其声明处理。初始化阶段不因建立新许可证文件就声称已经迁入或重新授权旧资产。依赖 JAR 使用依赖解析和明确的分发清单管理，不把用户提供的 sources.jar 自动纳入发布包。

## 3. 工程基线与日常命令

### 3.1 环境准备

从工程根运行命令。确认 `java -version` 和 Wrapper 使用 Java 17；IDE 的 Gradle JVM 也应设为 17。若需要临时指定 JDK，只修改当前终端的 `JAVA_HOME` 或使用独立的 Gradle Java 配置，不改动用户全局环境。

TC4R 的 sources.jar 用于查阅接口，编译/运行需要对应 runtime artifact、POM 及实际运行依赖。初始本地 Maven 资源来自相邻 Pollution 工程的 `local-repo`；复制本工程到其他电脑时，须配置同一 Maven 坐标可解析的仓库。不能把“本机已有缓存”当作新环境可复现证明。

TC4R 20721 对 Forge/Curios/TerraBlender 有自己的依赖约束，完整运行集要以其 `META-INF/mods.toml` 和本工程依赖配置为准。Forge 47.4.23 满足本轮已核对的 TC4R Forge 下限；后续不能只更新 Technomancy 的 Forge 版本而跳过依赖校验。

### 3.2 命令与结果边界

```powershell
.\gradlew.bat --version
.\gradlew.bat build
.\gradlew.bat -PwithGtceu=true build
.\gradlew.bat runClient
.\gradlew.bat -PwithGtceu=true runClient
.\gradlew.bat runServer
```

默认运行依赖集为 TC4R + Curios + TerraBlender；正常编译始终提供 GTCEu 7.5.3、LDLib 和 Registrate 的 `compileOnly` 依赖，`-PwithGtceu=true` 只启用开发运行时的 GTCEu 及其内嵌依赖。这样两种运行组合使用同一份源码和同一发行 JAR 的功能集合。**编译或运行依赖存在不代表已经实现 EU 功能。** 不要把该开关改为排除 GT 适配源码后，无标识地产出同名、同版本而功能不同的两个 JAR。

数据生成、GameTest 和单元测试要在对应任务与测试实现纳入后使用：

```powershell
.\gradlew.bat runData
.\gradlew.bat test
.\gradlew.bat runGameTestServer
.\gradlew.bat -PwithGtceu=true runGameTestServer
```

空测试任务成功不代表测试覆盖；空模组可以启动不代表 TC/FE/EU 联调成功；`build` 成功也不代表专用服务器能安全加载客户端相关类。初次服务器启动还应在实际运行目录按正常流程确认 EULA。

### 3.3 构建产物和仓库纪律

- 发布候选取自本次 `build` 的重混淆产物，核对 `build/libs` 内文件名、版本、时间和 SHA-256；不能凭旧 JAR 存在判定成功。
- 提交源码、必要资源、文档、Wrapper 和版本配置；忽略 `.gradle`、`build`、开发世界、日志、IDE 临时状态和下载缓存。
- 不向本仓库提交整个相邻 Pollution 的本地仓库、旧模组二进制或未经整理的审计 bare 仓库。
- 一个提交尽量对应一个可验证的变更：基础设施、单个功能组、数据或验证。每个功能提交写清实现、仍缺内容及实际验证。
- 版本升级和游戏规则调整单独记录，避免把平衡变更藏在 API 迁移提交里。

## 4. 推荐架构与依赖方向

下面的目录是建议布局，尚未全部创建。按实际功能逐步增补，不为目录完整性制造空类。

```text
theflogat.technomancy
  Technomancy                 入口、生命周期接线
  config                      配置定义和验证
  registry                    方块、物品、BE、菜单、配方类型注册
  common
    block                     放置、朝向、交互、掉落
    blockentity               机器调度与持久化
    energy                    与第三方类型无关的单一能源存储
    storage                   物品/流体/精华存储及操作结果
    machine                   加工、发电、连接、红石和升级逻辑
    item                      物品、连接工具和法杖承载物
    recipe                    自定义配方与序列化
    research                  本模组研究标识和必要运行逻辑
    multiblock                控制器、占位部件和结构检查
    player                    Existence/仪式相关玩家状态
    world                     有界扫描和世界操作
  compat
    tc4                       必需 TC4R 公开 API 适配
    tc4.internal              少量有版本边界的内部适配
    gtceu                     可选 EU 能力和网络输出
    botania                   后续可选模块
    bloodmagic                后续可选模块
    jei                       配方展示
    jade                      机器状态展示
  network                     包定义、校验和处理
  client                      renderer、screen、颜色与粒子
  data                        tags、模型、配方、语言等生成器
  gametest                    服务端行为验证
```

依赖方向：机器逻辑依赖自有存储与明确的资源接口；FE/GT/TC 协议层把外部请求转换为存储操作；客户端只读取同步后的展示状态。公共能源、基础 BE、注册对象和入口字段不要出现 GTCEu/Botania/Blood Magic 类型，以保证缺席时类加载安全。

TC4R 是必需依赖，允许本模块明确使用其公开 API；仍应把领域转换收拢到少数适配类，避免每台机器都复制 Aspect ID、Vis 单位和错误处理代码。不要为了模拟旧继承树重新建立巨型 `TileTechnomancy`；可组合的存储、朝向、红石、升级和运行状态更容易验证。

## 5. 功能分组和恢复依赖

### 5.1 推荐分组

| 功能组 | 同时涉及的内容 | 难点和验收主线 |
|---|---|---|
| 材料与基础研究 | 原版材料、线圈、齿轮、配方、研究页 | 注册 ID、旧 metadata 拆分、去除 TE 材料、研究可达 |
| 精华罐/储库/创造罐 | 自有 BE、过滤、管道、远程抽取、物品数据、渲染 | 管道与远程来源不同；搬运/掉落/重放置守恒 |
| 精华/节点发电机 | 魔法输入、电力存储、FE/EU 输出、升级、朝向 | 消耗与产出结算一致，满储能不白耗，输出不增能 |
| 冷凝器/精华合成器 | 能源、精华、要素组合规则、筛选与配方 | 复合要素组合、输出容量、数据重载 |
| TC 矿石处理器 | `TileProcessorBase` 行为、加工产物、跨模组轮次、菜单 | Ignis 燃料，tags/GT 材料，避免重复加工和产量套利 |
| 无线精华连接 | 线圈、连接器、连接数据、容器适配、红石升级、粒子 | 有界查询、方向、失效端点、buffer/特殊设备适配 |
| 节点生成器 | 主机、占位块、端口转发、拆除、节点规则 | 单一主机存储，分块卸载，节点创建事务 |
| 法杖工具 | 电动杖杆、权杖、帽/杆组装、笔、融合核心 | per-stack 数据、Vis/FE 单位、工具协议和动作授权 |
| 高等分解台 | 物品要素查询、所有者、研究要素池 | 在线/离线策略，玩家 UUID，服务器结算 |
| 邪术消耗器 | 世界/物品消耗、精华产出、区域选择 | 保护事件、现代掉落、不可破坏方块、负高度、吞吐 |
| 稳定灯/电风箱 | 注魔矩阵/熔炉深度联动 | TC4R 扩展点不足，须 API 补充或受控 Mixin |
| 群系改造 | 三种目标群系、区域更新、污染恢复历史 | quart 群系、同步、有界成本与可恢复性 |
| 仪式/Existence | 玩家数据、仪式注册、世界扫描、同步 | 与核心 TC 迁移分期；跨维度、重生和重连验证 |
| Botania/Blood Magic | 各自能源转换、处理器和资料展示 | 缺席安全、资源守恒、原版平衡与现代 API 逐项核对 |

### 5.2 必须成组处理的隐藏依赖

**无线精华线圈**依赖共享 `ICouplable`、`ItemCoilCoupler`、`TileCoilTransmitter`，不是一个独立机器类。Technomancy-2 把精华类型识别注释，连接工具初始化和配方也未完整接通。现代连接记录至少包含维度键、BlockPos、端点用途；应限制数量和操作距离，并在连接时与使用时都校验。

**节点生成器**的 `BlockFakeAirNG/TileFakeAirNG` 在 TC 目录之外。占位部件曾转发 RF、精华、法杖交互、红石和升级；新实现必须让所有部件引用一个控制器，所有 FE/EU 视图共用其余额和吞吐预算。主机不存在、区块未加载、结构部分拆除时端口应失效，不能残留可用复制端口。

**TC 处理器**消耗 Ignis，而共享 `TileProcessorBase` 跟踪跨 TC/Botania/BM 的加工轮次。恢复它时必须同时处理 `Ore`、`ItemProcessedOre`、GUI、输出和已有加工标记，不可把共享 NBT 标签丢掉后重复获得倍率。

**研究树**依赖材料、升级件、连接器配方以及 TC 自带前置。拆功能时需要拆研究前置和页面，不能让玩家看到指向空配方或不可获得物品的研究。

**客户端**须覆盖方块模型、BE 动画、物品外观、GUI 与 HUD。原版 TC 外围缺失的 12 个方块渲染类、11 个 Tile 渲染类及 GUI/HUD 是工作量线索，不是现代版需要照搬的类数。

## 6. 注册、生命周期与能力

### 6.1 注册与初始化

使用 Forge 1.20.1 的注册事件/`DeferredRegister` 组织方块、物品、BlockEntityType、MenuType、配方类型与序列化器。构造注册对象时避免读取世界、访问玩家或过早取得其他模组尚未准备的对象。需要延迟执行的共同初始化放到合适生命周期；数据定义尽量进入数据包。

所有资源 ID 使用小写 `technom:*`。保留 mod ID 有助于名称连续性，但不自动解决旧 metadata 方块拆分和存档格式迁移。新增 ID 一经发布就应稳定，重命名必须写明确映射与迁移测试。

### 6.2 BlockEntity 与服务器 tick

- Block 提供正确的 BE ticker；服务端逻辑从 `ServerLevel`/逻辑服务端进入。
- 存储变化调用必要的 dirty 标记；展示变化按粒度同步，不把完整 NBT 每 tick 发给所有玩家。
- 能源/物品/流体能力建立一次并维护生命周期，移除时 invalidate；若采用可恢复 BE 生命周期，按该实现正确重建视图。
- 向邻接机器缓存 capability 时订阅失效或重新查询；不能继续使用已拆除的 `LazyOptional` 内容。
- `null` 面的内部查询策略与六个面的自动化策略分开定义；不能利用无面查询绕过对外输入输出限制。
- 每台机器清楚定义红石控制的是加工、主动输出还是所有交互；GUI 和自动化必须遵守一致规则。

### 6.3 物品和流体自动化

使用 `IItemHandler` / `IFluidHandler` 暴露符合机器语义的侧面视图。输入槽、输出槽、容器槽、工具槽分别定义过滤和插拔规则，不恢复 1.12 的空槽占位实现。

所有转移按实际接受量结算：先检查可用输入、输出空间和成本，再执行一次有界操作；如果输出端只接受部分，输入消耗也必须对应实际结果，或者整笔拒绝。禁止“先扣全额能量，后发现流体槽已满”的行为。

物品空值使用 `ItemStack.EMPTY` 语义；不能把旧 `null` 分支机械映射为任意非空栈。机器方块掉落时选一种权威数据保存路径，不能同时掉出全部物品和保留带相同内容的机器物品。

## 7. FE 与 GTCEu EU 能源设计

本节是必须实现和测试的设计，不代表初始化工程已提供这些类。

### 7.1 单一余额，多套视图

建议自有 `long` 存储作为唯一真实余额；FE 和 GT 只提供协议视图，不能分别拥有两份可独立充放的 `EnergyStorage`。例如以整数 FE 等价值作为内部计量单位 `Q`，固定比例 `r = 每 EU 对应的 Q`，机器容量与吞吐也使用同一基准。

FE `IEnergyStorage` 对外采用 `int`，读取和单次转移必须夹到 `Integer.MAX_VALUE`；内部容量不必因此降为 int。GT 读数采用 `long`，从 Q 转换为 EU 时向下取整，未够一 EU 的 Q 留在唯一余额内。转换必须使用整数运算并在乘法前防溢出。

示例仅说明计量方法，不设定正式倍率：若配置 `r = 4`，存储 `Q = 7` 则 FE 视图显示 7，GT 视图显示 1 EU，剩余 3 Q 仍在同一存储里。随后从 GT 提取 1 EU，余额只剩 3 Q。不能把余数复制进额外缓冲，又保留在原余额中。

### 7.2 GT EU 是电压和电流协议

`IEnergyContainer.acceptEnergyFromNetwork(side, voltage, amperage)` 的接收结果按接受的安培数理解，不是直接返回 EU 或 FE。一次接收的能量为：

```text
EU_accepted = voltage × acceptedAmperage
Q_accepted  = voltage × acceptedAmperage × r
```

适配层需明确 `inputsEnergy/outputsEnergy`、输入/输出电压、输入/输出安培上限及可用容量。不同 API 的 `changeEnergy`、存储读数与网络请求语义要分别实现，不能令内部记账方法默认绕过所有网络限制并当公共入口使用。

所有等级使用锁定 GTCEu 的电压表/辅助方法或经验证的显式配置，不把等级数字直接当电压。额定电压、最大安培、基础功率、容量和升级倍率应进入机器定义；同一个“升级”不能悄悄同时提高燃料效率、电压和吞吐而缺少平衡说明。

### 7.3 按完整数据包接收

对 `voltage <= 0`、`amperage <= 0`、错误方向、未形成结构、已失效主机直接拒绝。合法请求的接受包数必须同时受以下因素限制：

```text
可接受安培 = min(
  请求安培,
  本 tick 剩余安培额度,
  floor(剩余 Q 容量 / 每包 Q),
  floor(本 tick 剩余输入 Q 额度 / 每包 Q)
)
```

不能接受半个 EU 电压包却向网络报告整安培。若剩余空间不足一包则接受 0；这与 FE 可以接受任意整数能量是不同协议边界。

过压策略必须明确、可配置且有测试。初期建议明确拒绝高于额定值的请求并返回 0；若后续选择沿用 GT 破坏/爆炸机制，须使用该版本机制并单独验证世界副作用。不要宣称遵循 GT 过压规则却仅截断电压，也不要在受电接口失败后自动退回 FE，从而绕过过压判定。

### 7.4 发电机主动输出

需要原生 EU 输出的发电机必须主动探测允许输出面的 GT 接收端，以自身电压和可用整包安培调用其受电接口，按照**实际接受的安培**扣除能源。发电机不是只实现一个可查询的存储 getter 就算完成 GT 输出。

对同时暴露原生 GT 和 FE 的邻居采用确定的协议选择：本次传输优先使用原生 GT；不能对同一条连接用两种视图重复发送同一预算。GT 接收器返回 0 时，不能无条件 FE 回退绕开电压、输入面或电流限制。

外部调用可能出现回调或改变邻居状态。推荐在本 tick 内先预留本机预算/可发送余额，调用一次邻居，按合法返回值提交实际扣款，释放未用预留；防止重入再次花掉同一能量。外部返回量必须夹到请求范围，异常路径不得增加本机余额，也不能为了“回滚”再次向已接收的邻居补发。

### 7.5 每 tick 预算和模拟

输入 Q 预算、输出 Q 预算、GT 安培额度应由主存储/控制器统一维护，并以服务器 game time 切换 tick。FE 与 EU 同 tick 访问时共享对应预算，多面和多方块代理也共享预算。若机器需要总 I/O 限制，再加统一总预算；不能每个 capability 对象各自刷新计数。

FE `simulate=true` 只返回当前可接受/可提取量，不改变余额、tick 额度、dirty 标记、粒子或转换余数。TC 精华/Vis 的模拟同样如此。模拟成功不是对未来任意外部操作的永久承诺；在实际执行时重新校验，并让内部同笔资源支付在一个明确服务器事务内完成。

机器内部燃料转换不得通过外部 FE/EU 能力绕一圈支付。应由内部逻辑一次性核算燃料、输出余额和损耗，再更新唯一存储。

### 7.6 倍率、余数与防套利

- 明确读取/覆盖 GTCEu 自身 FE/EU 兼容设置的策略。若其输入输出倍率不一致，本模组不能再叠加一套方向不对称倍率而宣称守恒。
- 第一版优先采用统一、正整数且在世界运行期间稳定的比例。若必须支持有理数倍率，用固定精度整数和持久化余数，并写溢出与循环测试。
- 比例属于存档经济规则。配置热重载改变倍率会改变现存余额的 EU 价值；默认应拒绝运行中变更，或实施带数据版本的显式迁移。
- 燃料效率、发电效率和协议换算应分开。协议转换不应创造能量，魔法发电的成本属于配方/机器平衡。
- 测试 FE→本机→EU→GT 变换器→FE 的完整闭环及反向闭环，包含 1、`r-1`、`r`、`r+1` 和接近容量/整数上界的值。
- 输入输出切面、装卸升级、拆放机器、卸载区块、服务器重启后，余额与余数必须守恒。

### 7.7 可选依赖与发行 JAR

GT 类型只出现在隔离的集成类；公共入口经存在性检查调用独立引导类，避免类验证阶段提前解析缺席的 GT 类型。`mods.toml` 标为 optional 只是元数据，不能替代代码隔离。

默认构建应能编译基础代码；最终发布候选应包含经过验证的可选 GT 适配类，并在同一个 JAR 上分别验证“有 GT”和“无 GT”。一旦引入适配源码，应固定编译 API，明确开发运行开关；在尚未运行验证之前只能标为“集成实现待验收”。

## 8. 魔法资源与加工平衡

### 8.1 各资源保留自己的账本

精华按 `AspectId → amount` 储存，节点与法杖 Vis 使用 TC4R 对应类型和单位，Mana/LP/Existence 各用自身协议。不能为了复用能源容器把所有资源塞进同一个 long，导致无来源转换或过滤失效。

TC4R 法杖相关公开 API 存在 whole Vis 与 centivis 的单位区别；逐个读取参数契约。`WandRodSpec` 容量为 whole Vis，而 `CustomWandSpec.capacityCentivis` 和部分转移接口明确使用 centivis。将单位写进变量名与边界测试，避免 100 倍误差。

### 8.2 多资源机器的结算顺序

对于“电力 + 精华 → 产物”的操作：读取配方快照，验证输入、所有成本、输出空间及世界条件；模拟各输入/输出；在服务器线程按一个明确的提交步骤更新内部所有账本。任何外部资源必须先取得可兑现结果，再结算对应实际产出，不把多个不可回滚外部调用误当成数据库事务。

输出满、配方失效、结构未加载、目标节点已经变化时不消耗完整成本。需要持续消耗维持工作时，把“维护成本”明写为独立规则，而不是失败操作的偶然副作用。

### 8.3 矿石加工链

建立稳定的材料 ID、已处理模块集合/轮次和产量定义。使用 tags 或经审查的 GT 材料映射；GT 缺席时基础材料仍有可用来源与结果。避免把旧字符串 `==` 比较、metadata 倍率和不受限 NBT 整数直接带入。

每条加工记录至少回答：输入是什么、哪种魔法资源付费、是否允许再次经同模块处理、其他模块是否可接续、最大轮数/倍率、最终熔炼产量是什么。GT 高产矿链加入后必须做全链收益表，防止 Technomancy 的乘法倍率在多个模块间无限重复。

Botania 1.12 分支的“空桶 + 50,000 Mana → 1,000 mB 魔力桶”是可评估的配方增量；Blood Magic 加工成本从公式变常数 16 是平衡变化。两者都应单独决定并记录，不能标为无争议兼容修复。

## 9. TC4R API 对照和扩展边界

### 9.1 查阅基准

本轮 API 依据对应的 `thaumcraft-forge-0.1.0-20721-sources.jar`，相邻工作区路径为：

```text
../Pollution-Unofficial-1.20.1/local-repo/dev/tc4port/thaumcraft-forge/
  0.1.0-20721/thaumcraft-forge-0.1.0-20721-sources.jar
```

表内条目均位于该 ZIP 的 `dev/tc4port/thaumcraft/` 下。旧 `thaumcraft.api.*` 命名空间并不存在。公开 Java 方法不必然属于稳定扩展 API；位于 `block.entity`、`common`、`registry` 等内部包的 public 方法也应列入受版本约束的桥接。

### 9.2 已具备的公开接口

| 旧需求 | 20721 条目与关键入口 | 实施要点 |
|---|---|---|
| 要素 ID/数量/组合 | `api/aspect/AspectId.java`、`AspectAmounts.java`、`AspectApi.java` | 以注册 ID 识别，查询当前 catalog；不要依赖旧 Aspect 单例比较 |
| 物品/方块要素 | `api/aspect/AspectQueryApi.java:38/49/73` | 区分单件与整栈查询；用于分解台、消耗器与配方估值 |
| 精华管道 | `api/essentia/EssentiaTransport.java:30-70` | 方向、吸力、过滤、实际接受量和模拟都要实现 |
| 远程精华来源 | `api/essentia/EssentiaSource.java:6-18`、`EssentiaApi.java` | 来源抽取要求完整请求或 0；普通管道不会自动成为远程来源 |
| 显示要素 | `api/aspect/AspectContainerView.java`、`api/essentia/EssentiaJarView.java` | 展示接口与可变存储分离 |
| 已有节点状态修改 | `api/node/NodeApi.java:36/89` | 使用 fresh expected state 的比较替换；双节点用双节点原子操作 |
| CV/Vis 网络 | `api/node/VisNetworkApi.java:37/75`、`VisSource.java:30-38` | 服务端、loaded-only、模拟预留及共享存储身份 |
| 研究状态 | `api/research/ResearchApi.java:76/175` | 查询完成度；授权的服务端研究完成操作 |
| 研究要素池 | `api/aspect/AspectPoolApi.java:44/48` | 针对 ServerPlayer 操作，明确所有者和离线策略 |
| 杖杆/杖帽 | `api/wand/WandPartApi.java:23/27` | 注册现代定义，研究与外观一起接通 |
| 法杖 Vis 转移 | `api/wand/WandApi.java:93-105` | 玩家/机器上下文、VisAction、单位检查 |
| 电动杖杆行为 | `api/wand/behavior/WandBehaviorApi.java:18` | `WandServerBehavior` 按 API 的 20 tick 节奏执行 |
| 自定义权杖 | `api/wand/CustomWandItem.java:33`、`CustomWandSpec.java:19` | 公开基类承载施法能力；无需照搬旧 ASM |
| 自定义核心动作 | `api/focus/action/FocusActionApi.java:12`、`FocusApi.java` | profile、动作定义、授权和持久化属性同时设计 |
| 笔/墨水工具 | `api/item/ScribeTools.java:14/23` | `hasInk/consumeInk`，研究台识别此接口 |
| 腐化群系列 | `api/taint/TaintBiomeApi.java:27` | 保持污染恢复历史和同步，不能旁路成单纯改颜色 |

**节点边界：** `NodeApi` 能修改现有普通节点和节点罐；energized nodes 和只供观察的第三方 view 不属于其可变 owner。不得把普通节点更新算法无条件应用到所有实现 `AuraNodeView` 的对象。

### 9.3 需要专门扩展的内容

| 功能 | 旧实现 | 20721 缺口/适配选择 |
|---|---|---|
| 注魔稳定灯 | 直接修改矩阵 `instability` | `InfusionMatrixBlockEntity` 的状态字段 private，仅有 getter；静态稳定器 tags 不等价于主动消耗资源降低不稳定度。优先向 TC4R 加受控操作 API；否则限定版本的 Mixin |
| 电动风箱 | 改炼金炉燃烧/进度、反射 speedBoost，改地狱炉时间 | 炼金炉相关字段 private；`ArcaneBellowsBlock` 为 final 且识别写死具体类型，现有 API 只查询。需要风箱提供者/加热加速扩展或局部 Mixin |
| 新建节点 | `ThaumcraftWorldGenerator.createNodeAt` | `NodeApi` 没有公开创建入口；可封装内部节点放置与 `AuraNodeBlockEntity.setNodeState`，最好由 TC4R 提供公开、受约束操作 |
| 魔法森林/阴森林改造 | `Utils.setBiomeAt` | 公开腐化 API 不覆盖任意群系；独立实现现代 quart 修改和同步，协调污染恢复历史 |
| 研究台创建交互 | 笔直接把两张桌替换为研究台 | 插槽识别已有公开 `ScribeTools`，但两格结构创建还需核对现代桌交互并单独封装 |
| 特殊设备无线精华 | 硬编码 Bore、Lamp、Thaumatorium 和 Buffer 内部类 | 重新核对各设备公开接口和转发 view；不照搬具体类名分支 |

内部桥接应集中列出目标 TC4R 版本、访问的类/方法、前置条件和对应测试。任何依赖升级都先运行桥接测试；无法满足的版本在加载/诊断阶段给出明确原因，而不是吞掉异常后继续复制资源。新增 Mixin 前先证明公开 API 无法表达需求，记录为何必须修改该内部状态。

## 10. 研究、配方与数据生成

### 10.1 数据布局

```text
src/main/resources/
  assets/technom/
    lang/en_us.json
    lang/zh_cn.json
    blockstates/
    models/block/
    models/item/
    textures/
  data/technom/
    recipes/
    loot_tables/blocks/
    tags/items/
    tags/blocks/
    thaumcraft/research/
```

最后一项来自 TC4R 的资源重载目录 `thaumcraft/research`；其内部示例为 `data/thaumcraft/thaumcraft/research/default.json`，顶层有 `categories`、`entries`。新项目应使用自己的 namespace，具体研究 key 与 recipe display 格式按 20721 示例和加载器校验，不能套用 TC6 JSON。

### 10.2 配方类型与材料替换

普通配方采用现代原生配方/tag；神秘配方采用 TC4R 的实际 serializer。其 infusion 示例使用 `type: thaumcraft:infusion`、`research`、`central`、`components`、`result`、`instability`、`aspects`。奥术、坩埚及动态法杖组装分别核对相应类型，不能将全部原 `add*Recipe` 调用统一转换为 shaped crafting。

为原 TE 部件建立替代表：原部件、基础配方方案、可选 GT 配方方案、所在研究阶段。优先让基础路线自洽，再用条件配方提供 GT 材料路线；不能将 GT 专属电路变为无 GT 环境不可替代的前置。

处理旧 `Ingredient.fromStacks` 误用时区分“这个槽位接受 A 或 B”和“合成需要 A 与 B”；多个必需输入必须是多个 ingredient。手册和研究页按稳定 recipe ID 引用实际已注册配方，不维护一份仅供显示却没有注册的 Java 配方对象。

### 10.3 研究可达与可选模块

建立研究依赖表：研究 key、父项、触发物、所需物品/机器、展示配方和可选模组条件。对每个正式功能，从新玩家获得基础研究到成品做一次路径检查。

Botania/Blood Magic 内容要按实际加载条件生成/过滤；不能只隐藏 JEI 而保留阻断主线的研究前置。禁用配置也必须处理对应页面、配方、能力与已有存档对象的行为，不使用“注册一半 null 字段”模式。

数据重载后失效的进行中配方应暂停或清理待处理状态，按已提交/未提交资源边界处理，不能重算成本后重复产出。数据生成的输出纳入版本控制时，检查重复 ID、失效 tags、缺模型/贴图/语言以及配方引用闭环。

## 11. 存档、网络与客户端

### 11.1 稳定持久化

每类长期数据使用显式 schema/version：能源余额与倍率版本、精华 ID/数量、库存与流体、升级、朝向、红石、加工进度、所有者 UUID、连线端点和多方块主机位置。数据加载需验证非负范围、容量和已知 ID，对未知数据给出可恢复处理，避免直接整数溢出或任意世界写入。

内部推荐 `long`，显示同步时不要通过 16-bit 菜单 data slot 截断高位；需要拆分或自定义同步。所有能源和精华字段只持久化一次，协议视图不另存余额。

旧连线 NBT `xcoordN/ycoordN/zcoordN` 和 1.12 的 `pos.getX()N` 等不能直接成为新格式。若以后支持导入，单独实现旧格式读取与一次转换测试；没有转换器前明确不支持旧世界直接迁入。

### 11.2 玩家数据和物品数据

玩家持久化按 UUID，明确登录、死亡/克隆、重生、维度切换、退出和服务端重启的保存同步规则。高等分解台无法直接操作离线 `ServerPlayer` 时，可暂停积累或存入受限待发放队列；选择一种并记录，不能偷偷绑定当前附近玩家。

法杖核心捕获的节点、物品能源和选择模式使用 per-stack 数据，不能存在 `Item` 单例字段或静态集合。核心捕获/放置必须由服务器授权，检查目标、成本和数据完整性；成功放置后消耗或清空对应捕获记录，失败不得丢节点或复制节点。

### 11.3 网络边界

服务器拥有真实存储、配方和世界状态。客户端只发“操作意图”，例如切换模式、选择过滤要素、连接端点、请求按钮动作；不得接受客户端发送的最终能源、产量、研究完成度或任意 NBT 覆盖。

每个 C2S 包验证发送者、维度、距离、区块已加载、正确 BE/菜单、权限、ID 与数值范围，切换到正确线程后执行。重复包、过期菜单、目标已拆除、卸载和快速连续点击都不能复制物品。S2C 按查看者/区块追踪者发送必要数据，进入追踪时提供完整初始展示状态。

### 11.4 渲染和界面

静态外观优先 JSON 模型，动态轴/液面/节点特效使用 BE renderer。旧 `IIcon`、`ISimpleBlockRenderingHandler`、固定管线 GL 调用和 TESR 不直接复制；采用现代 PoseStack/缓冲/RenderType 并控制透明绘制和动画状态。

资源路径全部小写；Windows 能找到大小写不一致贴图不能证明打包后 Linux 服务端/客户端一致。旧 `.lang` 转 JSON 时保留变量格式、颜色和多行语义，检查研究文本 key 与 recipe ID。

客户端注册集中在客户端入口，公共静态初始化不得引用 `Minecraft`、屏幕或渲染类。专用服务器启动是必验项。GUI 显示 FE/EU 必须写清当前单位、额定电压和速率；显示 EU 换算不代表设备已开放该方向的 EU 输入。

## 12. 世界操作、多方块与性能

### 12.1 有界扫描

节点搜索、无线端点、仪式、邪术消耗器和群系改造均采用有界扫描与分帧处理。检查 `hasChunkAt` 等 loaded-only 条件，禁止为周期性搜索意外加载远端区块。缓存的目标必须校验维度、位置、对象是否替换以及资源版本。

高度取世界提供的上下界，不能硬编码 0..255。群系是 quart 单元；需要定义“按整列还是按高度范围修改”，按实际变化体积结算成本和发送同步，避免把旧 1×1 列成本直接对应整个现代 4×4 区域而意外降低成本。

### 12.2 方块消耗与掉落

邪术消耗器的世界破坏必须尊重不可破坏方块、保护/交互事件和现代 loot 上下文。直接调用旧 `breakBlock` 并手算掉落会绕开真实事件及方块实体处理。掉落物、容器内容、精华回收和电力支出应定义唯一归属，杜绝同一内容既掉出又变成精华。

### 12.3 多方块操作

节点机形成时只占据允许的位置；先验证完整结构和空间，再修改。拆任一部件时清理其对应结构，不误拆相邻控制器。主机与部件保存明确关系，重启/区块乱序加载后能够恢复或安全停机。

与节点 state 的交互使用 TC4R 比较替换并保持节点持久身份。两节点变更用其双节点原子入口；新节点放置需要额外验证世界位置、类型、modifier、base/current Vis 和身份，不把连续两次有副作用调用简单称为“原子”。

### 12.4 性能验收

记录测试场景、机器数量、端点数量、扫描范围、服务器 tick 开销和网络量。先用行为等价的缓存/节流优化，再测重载和失效场景；不可只缩短扫描范围而声称性能修复却不披露玩法变化。机器停止时避免持续全量扫描和重复网络更新。

## 13. 已知旧问题和迁移处置

以下结论来自固定原版/1.12 源码审计。列入此表只表示需要处理，不表示新工程已经修复。

| 旧问题 | 证据位置（相邻原仓库内） | 迁移要求 |
|---|---|---|
| 血液制造器满槽仍扣能回归 | `Technomancy-2/.../TileBloodFabricator.java:30` | 按实际可填充量结算，满槽测试 |
| BM/BO 自动化空实现 | `Technomancy-2/.../TileBMProcessor.java:43`、`TileBOProcessor.java:100` | 重写侧面 item handler，验证漏斗/管道 |
| Existence 客户端同步未接通 | `Technomancy-2/.../network/PacketHandler.java:34` | 登录、重生、维度与变化同步 |
| 连接工具只剩字段 | 1.12 注册和配方链 | 与所有无线设备一起恢复并验交互 |
| 多原料被写成一个候选 ingredient | `Technomancy-2/.../CraftingHandler.java:147` | 重建配方语义，缺任何必需原料不能合成 |
| 展示配方和注册对象脱节 | `Technomancy-2/.../ModuleBase.java:88` | 按 recipe ID 查真实配方 |
| `Affinity(int id)` 赋值方向错误 | 两版 `Affinity` 构造器 | 验证每个 ID 与序列化双向映射 |
| 融合核心共享节点状态 | 原版 `ItemFusionFocus.java:38-41/87-94` | per-stack、服务器事务、放置后清空 |
| 线圈坐标 NBT 键被代码替换污染 | `Technomancy-2/.../TileCoilTransmitter.java:30` | 新 schema；导入另做，不沿用偶然键名 |
| 旧 API 时间/坐标/升级方法语义变化 | `TileDynamoBase`、`IUpgradable` 等 | 行为移植，避免机械复制/批量重命名 |
| 旧字符串材料比较和不受限加工标记 | 原版 `TileProcessorBase.java:119-123` 等 | 稳定 ID、值比较、轮次与倍率边界 |
| 旧核心和工具多模组接口绑死 | 原版 `ItemTechnoturgeScepter.java:48-61` | 移除旧 API，分离现代工具集成 |

另有范围辨别：两版只注册 16 个仪式，FireT3 在原版也未注册；`ItemEssentiaCannon.java:13` 明确暂缓，`Thaumcraft.java:192/200` 没有实际注册。不要把这些标成“现成可恢复”的功能。`Intial_Rework` 分支的 `RitualOfFireT2` 存在 z 坐标误用 yOffset 的回归，也不能整体替换较早成熟实现。

原版已经修复并被 1.12 保留的行为，例如特定熔炼经验、treasureSafeguard 默认值和血液制造器主动排液，应按证据保留，不能重新包装成现代移植中新发现的修复。

## 14. 里程碑与验收门槛

每阶段通过真实可观察结果验收，不给虚假的迁移百分比或未经实施验证的固定工期。默认尚未验收，完成后在独立进度记录中添加提交、依赖、测试命令和结果。

### S0：基础工程

范围：Java/Forge/Gradle 固定、`technom` 入口、构建配置、依赖、Git、文档。

验收：干净构建成功；确认打包元数据无 ExampleMod 残留；TC 基础客户端和专用服务器可加载；GT 开发依赖能够解析。若仅完成编译，应注明启动尚未验证。此阶段没有机器或能源兼容功能。

### S1：基础可玩闭环

本阶段分成公共层和第一个实际闭环两个验收点，公共层通过不代表可玩闭环完成。

#### S1-A：机器与能源公共层

范围：注册、基础 BE、持久化、item/fluid handler、唯一 long 能源存储、FE 视图、可选原生 EU 视图和输出策略。

验收：模拟无副作用、方向正确、容量和吞吐上限、GT 整包/电压/安培、跨协议同 tick、余数、溢出、能力失效、保存重载全部有有效测试。没有 GT 的专用服务器使用同一发行候选 JAR 能启动。

#### S1-B：第一个可玩闭环

范围：基础材料和研究、精华罐、精华发电机、一台耗电设备及实际配方。

验收：普通玩家能从合法材料获得设备；TC 管道输入精华→发电→FE 设备工作；GT 电网输入/输出各至少一条实测；满罐/满电/停机/拆放不重复、不白扣资源。以这组结果确认系统方向，再扩内容。

### S2：核心扩展

#### S2-A：加工与精华设备

范围：冷凝器、合成器、高等分解台、矿石处理链、无线精华。

验收：所有者研究资源正确、配方和前置可达、跨处理器标记正确、无线过滤/吸力/端点失效与跨区块情况正确；加工产量和能源资源守恒表完成。

#### S2-B：节点和法杖

范围：节点发电/补充/创建、多方块、杖杆、权杖、笔、融合核心。

验收：节点身份和状态不丢失/复制；相邻并发机器修改冲突安全失败；多方块端口不增加吞吐；两玩家、多个核心、重启前后不共享捕获状态；法杖 Vis 单位和能源结算符合定义。

### S3：仪式和可选魔法联动

范围：仪式/Existence、Botania/Blood Magic 模块及它们的加工链、研究/手册和资源转换。按实际确认的子范围逐批纳入，原版未注册实验仪式不自动加入本期。

验收：玩家状态在登录、死亡、换维度和重启后正确；可选模组缺席/存在各自可玩；Mana/LP/Existence 和电力闭环不增益；多玩家仪式、负高度和区块边界场景通过。

### S4：深层 TC 联动

范围：稳定灯、电风箱、群系改造、邪术消耗器及需要内部桥接的特殊精华设备行为。

验收：TC 内部桥接版本边界明确；安全世界操作、污染恢复历史和性能场景通过；已有阶段的功能回归验证通过。

### S5：整体验收与发布候选

范围：功能审计、语言/手册/JEI/Jade、服务器兼容、世界持久化、干净打包和更新说明。

验收：按下一节矩阵留证；README 将已实现、已验证、受限/暂缓明确分开。S5 不是要求把原版未完成实验内容做完，而是要求本次声明的范围全部自洽。

## 15. 测试矩阵和发布边界

### 15.1 环境矩阵

| 组合 | 必验结果 |
|---|---|
| TC4R + 必需依赖 + Technomancy | 客户端、专用服务器、基础研究/配方/FE 工作 |
| 上述组合 + GTCEu 7.5.3 及依赖 | 原生 EU 输入/输出、GT/FE 双暴露、过压和安培限制 |
| 基础 + Botania（实现该模块后） | Mana 路线、桶/流体转换、处理链和手册 |
| 基础 + Blood Magic（实现该模块后） | LP/血液路线、满槽、所有者和处理链 |
| 全部已支持可选模组 | 循环转换、配方冲突、混合自动化、研究可达 |
| 无 JEI/Jade 和有 JEI/Jade | 展示集成不成为主线或类加载硬依赖 |
| 单机与至少两名玩家的服务器 | 菜单、节点、工具和研究同步无共享状态问题 |

初始缺少对应集成的组合标记为“未实现/未测试”，不能用无内容启动代替功能通过。

### 15.2 测试分层

**单元测试：** 纯计算的能源换算、边界/溢出、资源请求裁剪、配方成本、加工轮次和序列化版本转换。测试外部可观察规则，不为每个 getter 写镜像测试。

**GameTest：** 真实方块、管道、FE/EU 交互、菜单服务器操作、存储与资源变化、多方块形成拆除、节点事务、模拟和卸载边界。每个 bug 测试提供能复现错误的输入并验证最终资源总量。

**客户端/联机验证：** 渲染、语言、工具姿态、GUI、粒子、研究页、JEI/Jade、初次追踪/重连。编译和纯服务端 GameTest 不证明视觉正确。

**持久化验证：** 保存→退出→重新加载，拆方块→物品→重放，区块卸载→重新进入；核对能源 Q、余数、精华、物品/流体、节点 ID、玩家所有者和连接数。

### 15.3 必须覆盖的资源边界

| 类别 | 最少场景 |
|---|---|
| 能源 | 0、1、容量-1、满、负/超大请求、int/long 边界、整包不足 |
| 模拟 | 多次模拟不改变实际状态，随后执行只结算一次 |
| 方向 | 正常面、禁止面、null 内部查询、旋转后视图 |
| 并发访问 | 同 tick 六面、FE+EU、控制器+部件、外部回调重入 |
| 转换 | 双向闭环、碎片输入、余数保存、比例变更策略 |
| 多资源 | 精华够电不够、电够输出满、配方/目标中途失效 |
| 物品/流体 | 部分接受、满槽、非法类型、容器嵌套和掉落 |
| 节点 | stale expected state、不同身份、普通/罐/energized、失败不扣资源 |
| 无线 | 未加载、已拆除、同维度限制、重复连接、过量连接 |
| 网络 | 重复/过期/越距/错误类型 C2S，不接受任意客户端余额 |
| 可选依赖 | 无 GT 等目标模组时类加载安全、数据前置不悬空 |

### 15.4 验收记录模板

```text
功能 / 版本：
源码提交：
运行依赖（精确版本）：
测试命令或游戏操作步骤：
预期结果：
实际结果与证据位置：
已知限制：
是否达到本阶段门槛：
```

发布前重新核对依赖元数据、只包含计划中的资源与类、无测试世界/日志/凭证、许可证与来源记录齐备，使用本次构建产物计算哈希。文档中“支持 FE/EU”“可选 GT”“可迁移存档”等表述必须有相应证据；某功能仅有 API 映射或实现草稿时继续标记计划状态。

## 16. 开始下一项功能的工作模板

以“精华发电机”为例：

1. 阅读固定原版 `TileEssentiaDynamo`、`BlockEssentiaDynamo`、`TileDynamoBase`，记录吸力、燃料、缓存、tick、朝向、升级和掉落语义。
2. 读取 TC4R 20721 `EssentiaTransport/EssentiaSource` 的方法契约；确定该机器需要哪些接口。
3. 写明机器定义：精华容量、能源容量、每种燃料产出、输出功率、FE 面、EU 等级/安培、红石和升级。未知数值先从原版提出候选并标记待平衡。
4. 实现独立魔法输入与统一能源存储的服务器逻辑，再接注册/方块/物品/配方/研究。
5. 先验证资源守恒和边界，再加显示、音效和粒子；所有显示来自已提交状态。
6. 用同一个候选 JAR 在基础 FE 与 GT 网络环境分别验证，记录上述验收模板。
7. 更新范围清单和来源记录后提交；下一台机器复用已验证的共享层，只新增它真实需要的行为。

这套模板同样适用于其他组，关键是先定义可观察行为和资源边界，再迁实现。遇到共享层缺能力时先完善该层，避免每台机器产生一份不同的临时协议代码。

## 17. 证据索引

以下原版路径均基于提交 `37bf9a56fe1f713258f298d7ef392b104ccae88f`；本地相邻仓库可直接阅读，固定提交链接用于异地复核。

- [原版固定提交](https://github.com/Mordenkainen/Technomancy/tree/37bf9a56fe1f713258f298d7ef392b104ccae88f)：完整玩法来源。
- [本地原版 TC 注册和配方](../../Technomancy/src/main/java/theflogat/technomancy/lib/compat/Thaumcraft.java)：机器/物品注册、TE/Mekanism/基础配方分支、未注册实验内容。
- [本地研究树](../../Technomancy/src/main/java/theflogat/technomancy/lib/compat/thaumcraft/TechnoResearch.java)：前置、页面和配方引用。
- [本地节点生成器](../../Technomancy/src/main/java/theflogat/technomancy/common/tiles/thaumcraft/machine/TileNodeGenerator.java) 与 [节点占位块](../../Technomancy/src/main/java/theflogat/technomancy/common/tiles/air/TileFakeAirNG.java)：多方块和端口分工。
- [本地精华线圈](../../Technomancy/src/main/java/theflogat/technomancy/common/tiles/thaumcraft/machine/TileEssentiaTransmitter.java) 与 [共享连接接口](../../Technomancy/src/main/java/theflogat/technomancy/common/tiles/base/ICouplable.java)：无线和特殊容器边界。
- [TC 处理器](../../Technomancy/src/main/java/theflogat/technomancy/common/tiles/thaumcraft/machine/TileTCProcessor.java) 与 [共享加工链](../../Technomancy/src/main/java/theflogat/technomancy/common/tiles/base/TileProcessorBase.java)：Ignis 燃料和跨模块加工。
- [融合核心](../../Technomancy/src/main/java/theflogat/technomancy/common/items/thaumcraft/ItemFusionFocus.java)、[权杖](../../Technomancy/src/main/java/theflogat/technomancy/common/items/thaumcraft/ItemTechnoturgeScepter.java)、[笔](../../Technomancy/src/main/java/theflogat/technomancy/common/items/thaumcraft/ItemPen.java)：状态与工具交互。
- [稳定灯](../../Technomancy/src/main/java/theflogat/technomancy/common/tiles/thaumcraft/machine/TileFluxLamp.java)、[电风箱](../../Technomancy/src/main/java/theflogat/technomancy/common/tiles/thaumcraft/machine/TileElectricBellows.java)、[群系改造器](../../Technomancy/src/main/java/theflogat/technomancy/common/tiles/thaumcraft/machine/TileBiomeMorpher.java)：TC 内部访问来源。
- [Technomancy-2 构建](../../Technomancy-2/build.gradle)、[关闭的 TC 模块](../../Technomancy-2/src/main/java/theflogat/technomancy/lib/compat/Thaumcraft.java)、[变更后的连线存储](../../Technomancy-2/src/main/java/theflogat/technomancy/common/tiles/base/TileCoilTransmitter.java)：1.12 分支状态。
- [合并审计报告](../../research/technomancy-merge-audit-20260927/REPORT.zh-CN.md)：分支关系、文件统计、合并试验、回归和取舍。

TC4R 具体接口依据第 9 节的 20721 sources.jar。GTCEu 具体网络和 capability 行为依据锁定的 7.5.3 编译依赖及其实现；升级后应更新对应证据和测试，不把本指南当作跨版本 API 保证。
