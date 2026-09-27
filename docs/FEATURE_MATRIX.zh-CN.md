# 功能范围与迁移验收矩阵

核对日期：2026-09-27。目标：Minecraft 1.20.1 / Forge 47.4.23 / Java 17，核心对接 TC4R `0.1.0-20721`，以 Forge Energy 和 GTCEu EU 替换 CoFH RF。**当前工程只有初始化骨架，以下游戏内容全部待迁移，没有任何一行代表实现完成或游戏测试通过。**

玩法基准是原版 `37bf9a56fe1f713258f298d7ef392b104ccae88f` 中实际执行的注册路径；Technomancy-2 `223160a924e6f2c3c2170f2b9fa8b291a07cd31b` 提供资源与部分移植经验。完整来源、差异及采用原则见 [SOURCE_BASELINE.zh-CN.md](SOURCE_BASELINE.zh-CN.md)。旧注册名仅用于追踪来源，新版 ID 需另建映射，不能据此承诺读取 1.7.10/1.12 存档。

## 统计与阶段口径

“注册”表示源码存在可执行注册调用，仍受配置及可选模组控制，不等同于生存可获得、研究可解锁或行为正确。metadata 变体不重复算注册 ID；占位方块也不是独立玩家机器。

| 原版模块 | 已核对的旧注册范围 | 计数限制 |
|---|---|---|
| TC4 | 17 个 Block 注册 ID、16 个 TileEntity 注册类、5 个普通 Item 注册 ID、2 个 WandRod 定义 | 包含装饰块、创造罐、节点占位方块；材料和杖芯含 metadata；不含精华炮和重构器 |
| 核心 | 9 个 Block 注册 ID、11 个 TileEntity 注册类、最多 5 个普通 Item 注册 ID | treasures 默认关闭；纯矿另按材料动态注册；1.12 多注册的 TileSelector 不是新增玩法 |
| 仪式 | 16 个有效注册条目 | FireT3 在原版也是注释状态 |
| Botania | 4 台机器 + 1 个魔力流体方块、4 个 TileEntity 注册类；材料及桶物品 | 选装模块，不能与未安装时的实际注册数量混为一谈 |
| Blood Magic | 3 台机器、3 个 TileEntity 注册类、1 个材料 Item 注册 ID | 选装模块；材料含变体 |
| TC4 研究 | 16 个条件注册的研究 key | 配置组合会减少研究；不能只按源码条目数判断研究可达性 |

阶段与总迁移指导一致；它们表达依赖顺序，不表达工期或已完成比例。

| 阶段 | 完成目标 |
|---|---|
| S0 基础 | 官方 MDK、模组入口、构建和依赖配置、Git、文档；验证范围另行记录 |
| S1 闭环 | 先实现注册、数据、网络与 FE/EU 公共层，再以精华储存、精华发电、一台耗能设备及配方研究组成可玩闭环 |
| S2 核心扩展 | 加工链、无线传输、节点多方块、法杖和工具 |
| S3 仪式联动 | 仪式、Existence、Botania/Blood Magic 可选内容 |
| S4 深层 TC | 稳定灯、电动风箱、群系修改等需要额外 TC4R 适配的行为 |
| S5 验收 | 专用服务器、客户端、联机、重载、守恒与研究/配方可达性 |

## TC4 方块和机器

本节全部来自原版 [Thaumcraft.RegisterBlocks][tc-blocks]。20721 的 API 名称来自用户提供的 sources JAR 实查；有同名 API 不表示它提供旧内部类全部能力。API 统一前缀为 `dev.tc4port.thaumcraft.api`。

| 功能与旧注册字段 | 来源实现 | 依赖、20721 适配与主要风险 | 阶段 | 必须验证 | 状态 |
|---|---|---|---|---|---|
| 精华发电机 `essentiaDynamo` | `TileEssentiaDynamo` | 已实现为 `technom:essentia_dynamo`：`essentia.EssentiaTransport` + `aspect.AspectContainerView`；**刻意不实现 `EssentiaSource`**（它是消费者，不该被注魔祭坛抽走燃料）；燃料值表改为数据驱动 `data/technom/technomancy/essentia_fuel/`；RF 输出改 FE/EU 共用账本 | S1 | 管道与罐取料；模拟不扣料；满电停机；两个端口同 tick 不重复输出；重载燃料进度 | **已实现**。真实 `thaumcraft:warded_jar` 取料、向真实 FE 消费者交付、红石三态双向门控、满缓冲不扣料不烧料、源质与能量守恒、六面旋转、掉落可得、存盘往返，以及经 2 节 `thaumcraft:essentia_tube` 的真实管道链路，均由 `technom_dynamo` 批次 11 个 GameTest 在有/无 GTCEu 下实测通过（另有 116 项 JUnit 覆盖燃料表 codec、逐分支取值与满缓冲边界）。**客户端渲染未验证**（无 BER，静态模型，喷口角度按 22.5° 近似原版的 30°）；节点发电机仍属 S2。刻意偏离清单与实测数值见[验证记录](VALIDATION.zh-CN.md#源质发电机验证2026-09-28) |
| 节点发电机 `nodeDynamo` | `TileNodeDynamo` | `node.AuraNodeView` / `NodeVis`；核对消耗节点 vis 的语义，不能把节点总容量当可用燃料 | S2 | 节点损耗/恢复、类型和亮度边界；节点卸载；FE/EU 输出守恒 | 已实现（未入游戏验证）：`technom:node_dynamo`，9×9×9 已加载区块内节点/节点罐经 `NodeApi.replaceLoadedState` CAS 每 21 tick 取 1 Vis（不取末点）；1 Vis = 30 燃料点 × 80 Q × `essentiaFuelScale`（默认 600 Q）；复用 `DynamoFuelBank`/`MachineEnergy`/`RedstoneControl`；抽取移入红石门内。JUnit `NodeVisDrainTest`；GameTest `NodeDynamoGameTests` 已写未跑 |
| 量子精华罐 `essentiaContainer` | `TileEssentiaContainer` / 对应 Block | `EssentiaTransport`、容器视图、标签和显示；旧版内部罐继承改自有 BE | S1 | 容量、吸力、方向、标签筛选、邻罐抽取、破坏掉落和存储恢复 | **已实现**：`technom:quantum_jar`，640 点单 aspect，吸力 `(贴标签 ? 64 : 48) + 存量/50`、只允许上面、贴标签内容优先且拒绝与内容矛盾的标签、摘标签返还空白标签、比较器输出、液面与标签渲染器。实机（Rosetta 桥，`probes/essentia/`）5/5：真实 TC4R 双管道拓扑守恒 64/64、吸力逐档实测、掉落物 NBT 往返。**刻意偏离**：已贴标签基数由 56 提到 64（TC4R 标签罐为 64，原值会比参考实现还弱）；新增比较器输出。**未验证**：客户端渲染、跨重启持久化、右键交互未实机点击 |
| 量子玻璃/装饰块 `cosmeticOpaque` | `BlockCosmeticOpaque` | 无独立机器 BE；与量子罐奥术配方一起迁移；不能遗漏无 TE 材料路径 | S1 | 配方可得、碰撞和透光、模型及掉落一致 | 奥术配方已完成（数据层）：`technom:arcane/quantized_glass`，4 玻璃 → 4 块，vis `ordo 5 / ignis 5`，已由服务端加载；方块本体见 S1-A。未在游戏内合成过 |
| 精华储库 `reservoir` | `TileEssentiaReservoir` | `EssentiaTransport`；旧 `takeEssentia` 返回值必须按“实际取出量”复核 | S2 | 单/多次模拟、取放返回量、吸力、无负数和复制 | 待迁移 |
| 创造精华罐 `creativeJar` | `TileCreativeJar` | 创造工具；与生存存储和燃料规则分离 | S2 | 创造可选要素、管道输出；无生存配方或错误掉落导致可获得 | 待迁移 |
| 精华线圈 `teslaCoil` | `TileEssentiaTransmitter` | `EssentiaSource` 不等于任意管道；必须一起迁入连接工具、连接记录与适配器；保留原版 Buffer/Arcane Bore 修复 | S2 | 连接/解除、标签/方向、距离、区块卸载、跨端口去重、方块替换后失效 | 待迁移 |
| 能量凝聚器 `condenserBlock` → `technom:energy_condenser` | `TileCondenser` | 精华存储/转换与 FE/EU；逐项核对源要素和副产物规则 | S1 | 可作为耗能闭环候选；满槽不耗电、停机恢复、显示同步、产物守恒 | 方块与 BlockEntity 已迁移：FE/EU 六面输入且无输出、匀速进度条转换、64 点 potentia 缓存、六面输出开关（blockstate multipart，无 BER）、三态红石、向真实源质罐推送并附守恒断言；A-6/A-9/A-10/A-11/A-12/A-13 均有测试证据，详见[验证记录](VALIDATION.zh-CN.md)。**尚缺配方与研究（只能创造获得）、客户端渲染与存档重载未验证** |
| 神秘净化器 `processorTC` | `TileTCProcessor` | 精华 API + 共享纯矿加工链；原版消耗 Ignis，不应因替换 RF 而取消此成本 | S2 | 要素消耗、每模块重复加工上限、输出数量/NBT、输入输出自动化 | 待迁移 |
| 邪术吞噬器 `eldritchConsumer` | `TileEldritchConsumer` | 要素查询、破坏方块和耗能；旧 TC/Minecraft 内部逻辑不能直接复制 | S2→S4 | 每 tick 工作预算、不可破坏方块、方块实体库存、掉落/要素不得双重收益、卸载恢复 | 待迁移 |
| 高级分解台 `advDeconTable` | `TileAdvDeconTable` | `aspect.AspectQueryApi` / `AspectPoolApi`、玩家研究状态；旧 owner 名称改稳定身份 | S2 | 基础要素拆分、研究点奖励上限、离线/改名玩家、自动化、奖励仅结算一次 | 待迁移 |
| 精华融合器 `essentiaFusor` | `TileEssentiaFusor` | `EssentiaTransport`、`AspectApi`，多输入面与输出要素合成；共享耗能层 | S2 | 输入面配置、合成比例、输出堵塞、红石、面配置重载及资源守恒 | 待迁移 |
| 节点制造器 `nodeGenerator` | `TileNodeGenerator` | `NodeApi` 提供已有节点状态替换，不等同于通用节点创建工厂；需明确创建/初始化入口 | S2→S4 | 成型、燃料/精华/电力共同提交、节点状态保存、失败回滚与完整拆除 | 部分实现（未入游戏验证）：`technom:node_fabricator` 成对（相距 6、互相面对，节点在 facing×3 且 +1 高）对已有节点/节点罐工作：1000 Q + 1 精华回充 1 点魔力；装效能宝石后 10000 Q + 10 精华提高该要素 base 并补 1 点（也是加入新要素的途径）。全部经 `NodeApi.replaceLoadedState` 同 tick CAS 提交，只有在节点确实变更后才扣精华与电力。缓冲 50,000,000 Q、256 单精华、吸力 48/最小 32、红石默认 LOW 且九格任一带信号即停。**节点创建仍缺**：需要 `createNodeAt` 等价能力，TC4R 公开 API 只能改已有节点，故法杖仪式、aurum/vitium 专用吸取与 762.94 RF 公式整体推到 S4（研究页已明说）。外壳不再破坏玩家方块：位置被占即结构不成型。JUnit `NodeFabricatorWorkTest`；GameTest `NodeFabricatorGameTests` 已写未跑 |
| 节点多方块占位 `fakeAirNG` | `BlockFakeAirNG` / `TileFakeAirNG` | 主机多面能力代理；所有 FE/EU 端口必须共享主机存储和吞吐预算 | S2 | 断主机/断端口、跨区块加载顺序、拆卸不残留、能力失效、不复制库存/能量 | 已实现（未入游戏验证）：`technom:node_fabricator_shell`（无 BlockItem、无掉落表、不可破坏、不可见、无碰撞）。八个外壳把 `ForgeCapabilities.ENERGY` 和 `EssentiaTransport` 直接转交主机——交出的是主机 `MachineEnergy` 的同一个能力实例，故余额与每 tick 预算天然共用；主机消失/不再认领即自毁，主机区块未加载时端口报空而不自建存储；创造模式破坏外壳会连带拆除主机，避免"有洞但静默不工作"。证据结论：`TileFakeAirCore` 是共享基类（`TileFakeAirNG extends TileFakeAirCore`，而 `BlockFakeAirLight.createNewTileEntity` 直接 new 它，且在 `TMBlocks.java:126` 全局注册一次），因此它既不专属本行也不专属 S3 `fakeAirLight`；本组只在自己包内实现"主机消失即自毁"语义，S3 合并时可提取共用。GameTest `NodeFabricatorGameTests` 已写未跑 |
| 注魔稳定灯 `fluxLamp` | `TileFluxLamp` | 20721 的配方/注魔物品 API 不等同于“修改运行中祭坛不稳定度”的公开接口；需补 API 或版本限定适配 | S4 | 同平面祭坛识别、不稳定度变化、原有副作用、多个灯叠加与重载 | 待迁移 |
| 电动风箱 `electricBellows` | `TileElectricBellows` | `alchemy.ArcaneBellowsApi` 当前是吹向/相邻计数查询；不等同于注册自定义风箱、加热或加速回调 | S4 | 炼金设备实际识别、方向、倍率上限、仅工作时耗电、不同 TC 设备兼容 | 待迁移 |
| 生态转换器 `biomeMorpher` | `TileBiomeMorpher` | `taint.TaintBiomeApi` 可污染已加载列；魔法森林/阴森群系等完整目标仍需现代群系写入适配 | S4 | 四分之一群系网格、垂直范围、客户端刷新、存档、边界与卸载；限制批量工作量 | 待迁移 |

## TC4 材料、法杖、工具和研究

注册证据为 [Thaumcraft.RegisterItems][tc-items]，研究证据为 [TechnoResearch][tc-research]。5 个普通 Item ID 是材料、笔、杖芯、融合核心、Technoturge 权杖；不把杖芯 metadata 或两种 WandRod 重复计作 Item ID。

| 功能 | 来源及原版范围 | 依赖、20721 适配与风险 | 阶段 | 必须验证 | 状态 |
|---|---|---|---|---|---|
| TC 材料组 | `ItemTHMaterial`：neutronizedMetal、enchantedCoil、neutronizedGear、penCore；metadata 4 为旧连接工具过渡项 | 坩埚/奥术/注魔/普通配方和 tags；metadata 4 缺独立图标并会在更新时替换为 coilCoupler，不宜保留为独立生存材料 | S1 | 所有材料配方可得；无 TE/RF 物料硬引用；加工链和研究引用一致 | 配方已完成（数据层）：`crucible/neutronized_metal`、`enchanted_coil`、`neutronized_gear` 三条已由服务端实际加载；要素数据已写入。penCore 配方与要素已由 S2 书写笔补齐。未在游戏内实际合成过 |
| 长效书写笔 | `ItemPen` | `item.ScribeTools`；耐久与研究台识别 | S2 | 研究台接受、使用耗损、合成余物、服务端结算 | 已实现（未入游戏验证）：`technom:pen`，实现 TC4R `api/item/ScribeTools`，3000 次墨水（耐久即墨水，可铁砧修复）；右键两张相邻 `thaumcraft:table` 成型研究桌并把笔放入书写槽——此处使用受版本约束的内部桥 `ResearchTableBlock.form` + `CompoundBlueprintCatalog.isTrigger`（公开 API 只有笔记事务）。同时补上 `technom:pen_core` 配方（`forge:nuggets/iron` + `forge:dyes/black`）与 `technom:PEN` 研究。GameTest `PenGameTests` 已写未跑 |
| Energized 杖芯与充能行为 | `ItemWandCores` / `ElectricWandUpdate`，旧 `electric` WandRod | `wand.WandPartApi`、`wand.behavior.WandBehaviorApi`；物品电力按 FE 能力接入，充电与 vis 转换必须守恒 | S2 | 装配、上限、充电、六原始 vis、物品切换/丢弃/重载、不重复扣款 | 已实现（未入游戏验证）：`technom:energized_wand_core` + 杖杆定义走数据（`data/thaumcraft/data_maps/item/wand_rods.json` 合并层 + `#thaumcraft:wand_rods` 标签），材料 id `technom:electric`、容量 25、craft_cost 10；充能由 `WandChargeEvents` 每 20 tick 扫描背包，经 `WandApi.insert` 以 centivis 结算，100 Q = 1 厘魔力（旧版 10000 RF = 1 魔力），缓冲存于 ItemStack 而非 Item 单例；TE 电容/线圈原料改为 enchanted_coil×2 + neutronized_metal + 红石块 + 铜块。JUnit `WandChargeTest`；GameTest `WandChargeGameTests` 已写未跑 |
| Technoturge 杖芯与权杖 | `ItemTechnoturgeScepter` / `ScepterRecipe`，旧 `technoturge` WandRod | `CustomWandItem` / `CustomWandSpec` / `WandPartApi`；动态帽材质与配方改现代数据 | S2 | 铁/金/神秘金属帽组合、容量/折扣、充能、配方成本和研究门槛 | 已实现（未入游戏验证）：`technom:technoturge_core`（数据杖杆 `technom:technoturge`，容量 100、craft_cost 11，权杖 ×1.5 = 每原始 150 魔力）。不再移植 `ItemTechnoturgeScepter`/`ScepterRecipe`：TC4R 内置权杖合成（3 个同帽 + 神秘护符 + 杆芯，费用 cap×rod×1.5，需 `SCEPTRE` 研究）与旧配方布局和成本完全一致；FE 充能走物品 `ForgeCapabilities.ENERGY`（只进不出，模拟不扣款）。扳手语义缩减为 `TechnomWrench`（仅本模组机器）。GameTest `WandChargeGameTests` 已写未跑 |
| 融合核心/焦点 | `ItemFusionFocus` | `focus.action.FocusActionApi` + `NodeApi`；旧 Item 单例存节点状态且放置后未清空，必须改逐 ItemStack 数据与服务端事务 | S2→S4 | 两玩家/两物品隔离，移动节点仅一次，失败不丢节点、成功不复制，卸载和重进保存 | 待迁移 |
| 研究分类与配方展示 | `TECHNOMANCY` 分类及 16 keys，见下表 | `research.ResearchApi` / 数据定义、配方与展示 API；旧大写 key 需明确映射 | S1→S4 | 每一条前置可达、页面可开、配方 ID 存在、配置关闭不产生悬空依赖、联机同步 | 分类与 S1-B 四条研究已完成（数据层）：`data/technom/thaumcraft/research/technomancy.json`，服务端加载后 TC4R 报告 7 个分类。旧大写 key 映射为 `technom:<KEY>`（见下表注）。页面从未在客户端渲染过 |

| 原版研究 key | 对应功能 | 迁移顺序 |
|---|---|---|
| `TECHNOBASICS`、`QUANTUMJARS`、`DYNAMO` | 材料、储罐和两种发电机 | S1 基础先落地，节点发电内容随 S2 补齐 |
| `CONDENSER`、`PROCESSOR`、`TESLACOIL` | 冷凝、矿石加工、无线精华 | S1→S2 |
| `NODEGENERATOR`、`ELDRITCHCONSUMER`、`ADVDECONTABLE`、`ESSENTIAFUSOR` | 节点制造与高级机器 | S2→S4 |
| `PEN`、`ROD_electric`、`TECHNOTURGESCEPTER` | 书写笔、杖芯、权杖 | S2 |
| `BIOMEMORPHER`、`FLUXLAMP`、`ELECTRICBELLOWS` | 群系改造、稳定灯、电动风箱 | S4 |

**S1-B 研究 key 映射与状态（数据层）**：新注册 key 带命名空间——`technom:TECHNOBASICS`、`technom:QUANTUMJARS`、`technom:DYNAMO`、`technom:CONDENSER`，分类为 `technom:TECHNOMANCY`。这是 TC4R `ResearchKey` 自己的约定（裸 key 归属 `thaumcraft` 命名空间），因此 lang 键形如 `tc.research_name.technom:TECHNOBASICS`。前两条无条件加载并已由 GameTest 按坐标/要素/标志/前置/页面逐项断言；`technom:DYNAMO` 与 `technom:CONDENSER` 带 `forge:item_exists` 条件，等 `technom:essentia_dynamo` / `technom:energy_condenser` 注册后自动生效，无需再改数据。原版把 `CONDENSER` 挂在节点发电机开关上（附录 A-21 的死线缺陷）**未被复刻**：它的前置只有 `DYNAMO`。complexity 由原版的 0 改为 1，因为 TC4R 的取值域是 1..3 且会静默钳制。

**TE 原料剥离（S1-B 范围）已完成**：逐条核对 `RegisterRecipes()` 后确认，S1-B 八条配方里唯一的 TE 原料是凝聚器 TE 分支的 `frameMachineBasic`，而原版自带的兜底分支已经把它换成 `itemMaterial:2`（中子齿轮），因此直接采用兜底分支，没有自创替换；其余七条完全不含 TE/CoFH 原料。

## 核心仪式、Existence 与加工链

来源为 [TMBlocks][core-blocks]、[TMItems][core-items]、`common/rituals`、`common/tiles/technom/existence` 和共享 `TileProcessorBase`。这些内容大多在两版存在，不能算作 1.12 新增玩法。

| 功能 | 原版范围/来源 | 依赖与迁移风险 | 阶段 | 必须验证 | 状态 |
|---|---|---|---|---|---|
| 水晶、催化器、假空气光源、玄武岩 | 五属性 metadata 与辅助方块，4 个 Block ID | BlockState/独立物品、照明与模型；辅助空气的放置清理不能吞方块 | S3 | 各属性形状、仪式匹配、破坏清理、照明与同步 | 待迁移 |
| 16 个仪式 | 下表有效列表 | 原版硬编码 0/256 高度和同步大范围改世界；改实际维度边界、分 tick 任务、区块加载边界 | S3 | 正反向阵列、负 Y、重载恢复、移动 BE 数据、失败回滚、掉落守恒和 tick 预算 | 待迁移 |
| 仪式手册 | `ItemRitualTome`、GUI 与纹理 | Screen、现代文本/翻译和配方引用 | S3 | 所有已注册仪式可查、翻页/缩放、语言回退、无失效配方引用 | 待迁移 |
| 物品线圈与连接工具 | `itemTransmitter`、`ItemCoilCoupler`、`ICouplable` | `IItemHandler`、稳定连接数据；1.12 工具实例/配方缺失不能沿用 | S2 | 仓库输入输出、模拟、满库存、标签、断连/卸载、重复点击和无物品复制 | 待迁移 |
| Potency Gem / 增幅 | `ItemBoost`，机器升级接口 | 保持"纯吞吐 ×4、效率不变"语义：升级同时把发电速率和每次源质消耗都乘 4。不做多级升级 | S1→S3 | 安装/卸下只结算一次、掉落保存、耗能与产能同时调整 | **源质发电机部分已实现**：`technom:potency_gem` 右键安装、潜行空手右键非输出面取回、破坏时掉落，效率不变已由 JUnit（任意燃料值、任意 `essentiaFuelScale`）与 GameTest（实测 4 点源质 = 64000 Q，与未升级的 16000 Q/点一致）双层验证。其余机器待各自迁移；未做安装/卸下的客户端反馈验证。配方已完成（数据层）：`technom:potency_gem` 的坩埚配方已由服务端加载，要素数据已写入 |
| Existence 喷泉 | `fountainExistence` / `TileExistenceFountain` | 仪式生成与玩家/实体资源，保留独立 Existence 概念 | S3 | 仪式生成、资源产消、主人离线、多人归属、实体事件一致 | 待迁移 |
| Existence 燃烧器 | 一个 `existenceBurner` ID，普通/动态 2 变体 | 实体消耗、资源生产；不得简单改成所有行为都消耗 FE | S3 | 目标筛选、产量、红石、重载、动态版本差异 | 待迁移 |
| Existence 塔 | 一个 `existencePylon` ID，3 种变体 | 原版能力/范围与传输规则迁入新 BE | S3 | 范围、升级、消费者连接、跨区块/维度限制与守恒 | 待迁移 |
| Existence 使用器 | 一个 `existenceUser` ID：作物加速、收割、封印 3 变体 | 作物 tags/事件、`IItemHandler`、玩家实体状态 | S3 | 成长和收获成本、满库存、掉落、封印持续时间、维度/重生清理 | 待迁移 |
| 玩家属性/HUD/效果 | `PlayerData`、五 affinity、Existence level/power、drown/slowFall | 两版 Affinity 构造器都有赋值错误；1.12 同步接收被注释；改稳定身份、服务端数据和客户端显示 | S3 | 五属性彼此独立、登录/死亡/换维度同步、两客户端一致、配置关闭 HUD | 待迁移 |
| 宝物村民与宝物 | `ItemTreasure` 的 fireGem/powerPlate/goldenWing | 默认 `treasures && treasureSafeguard`，后者 false；保留默认关闭及配置说明 | S3 | 默认不激活；开启后事件副作用、掉落次数、封印交互、多人同步 | 待迁移 |
| 纯矿多阶段加工 | 每材料 1 Item ID、6 metadata 阶段；TC/BO/BM 各有加工记录 | 旧 OreDictionary 动态注册改预定义材料/tags/数据配方；接 GT 材料并明确 2～7 锭默认倍率 | S2 | 各模块至多两轮等原版规则、顺序组合、输出 NBT、矿/粉兼容、每阶段熔炼经验、无重复增殖 | 待迁移 |

| 已注册仪式 | 数量 | 原版类 |
|---|---:|---|
| Extraction | 1 | `RitualExtraction` |
| Fountain of Existence | 1 | `RitualFountainExistence` |
| Cave In | 3 | `RitualCaveInT1/T2/T3` |
| Black Hole | 3 | `RitualBlackHoleT1/T2/T3` |
| Water | 3 | `RitualWaterT1/T2/T3` |
| Purification | 3 | `RitualPurificationT1/T2/T3` |
| Fire | 2 | `RitualOfFireT1/T2` |

## Botania 与 Blood Magic 可选模块

用户排除的是 TE/RF，并没有排除 Botania/Blood Magic。下面保留为可选迁移范围，不把它们强制变成核心依赖。原版有效范围来自 [Botania 注册模块][botania]、[Blood Magic 注册模块][bloodmagic]；目标版本依赖和 API 要在实际实现阶段锁定。

| 功能 | 来源 | 依赖/迁移风险 | 阶段 | 必须验证 | 状态 |
|---|---|---|---|---|---|
| 花卉/Hippie 发电机 | `TileFlowerDynamo` | Botania Mana 接收与 FE/EU 输出；不能移植旧 RF 接口 | S3 | Mana 减少与电力增加守恒；满电、花/池连接、拔除模组时核心能启动 | 待迁移 |
| 魔力制造器 | `TileManaFabricator` | FE/EU→Mana，Botania 池识别/传输；独立存量与方向 | S3 | 满池不扣电、吞吐、颜色/连接、存储重载、无双接口套利 | 待迁移 |
| 植物净化器 | `TileBOProcessor` | Botania 魔力 + 共享加工链；1.12 空侧面槽与 false 插拔为回归 | S3 | 各方向自动化、魔力成本、记录与阶段、GUI 同步 | 待迁移 |
| 魔力交换器、魔力流体与桶 | `TileManaExchanger`、`ManaFluid`、桶；1.12 新增 50,000 Mana 灌注配方 | 现代 FluidType/流动流体/能力、物品桶；1.12 FE capability 仍返回 CoFH 存储，类型和行为不能直接认定正确 | S3 | 双向转换余数、桶灌装/倒出、模拟、槽满/池满、FE/EU 同 tick 合计、配方平衡 | 待迁移 |
| Botania 材料和手册 | Mana coil、Manasteel gear、Lexicon 页面 | 数据配方、现代手册扩展；1.12 返回未注册 recipe 对象导致页面断链 | S3 | 全部材料/页面/配方可达、未安装时不加载 Botania 类 | 待迁移 |
| 鲜血发电机 | `TileBloodDynamo` | Blood Magic 生命精华流体→FE/EU，方向和填充能力重建 | S3 | 仅正确流体、各面规则、燃料/电力守恒、空槽和卸载 | 待迁移 |
| 鲜血制造器 | `TileBloodFabricator` | FE/EU→生命精华；保留主动输出行为，修复 1.12 满槽扣能条件 | S3 | 满槽零消耗、模拟、相邻 capability 槽接收、每 tick 上限、重载 | 待迁移 |
| 鲜血净化器 | `TileBMProcessor` | Blood Magic LP/soul network + 共享加工链；旧名字 owner 改现代身份；常数 16 成本不能当兼容修复 | S3 | 网络主人、离线/无网络、LP 不足、成本与加工记录、自动化 | 待迁移 |
| Blood Magic 材料与祭坛配方 | 祭献锭、blood coil；祭坛/普通配方 | 现代 altar 数据与依赖隔离，使用无 TE 材料路径 | S3 | 祭坛等级、LP/流体成本、配方获取、未安装时核心能启动 | 待迁移 |

## 非目标、遗留未注册内容与公共验收

| 内容 | 基准事实与本轮决定 | 实现状态 |
|---|---|---|
| Thermal Expansion/CoFH RF | 不移植 TE 专属配方分支、CoFH 能量接口或打包旧 API；原版已经有不依赖 TE 的配方分支，改为现代原版/TC/GT 材料 | 排除；FE/EU 替代待迁移 |
| Mekanism 配方替代模块 | 旧模块主要查材料，未提供独立机器；非本轮核心要求，不作为 GTCEu 的替身 | 非必需，未实现 |
| Thaumic Energistics | 原版可选精华兼容，1.12 关闭；目标存在可用适配对象后再评估，不强拉入核心依赖 | 另行评估，未实现 |
| 精华炮 `ItemEssentiaCannon` | 原版实例和注册均注释；不能计为已完成原版功能，也不进入默认迁移验收 | 不计默认范围，未实现 |
| 火系 T3 仪式 | 原版 `RitualOfFireT3` 类存在，但注册注释；1.12 同样未注册 | 不计默认范围，未实现 |
| 精华重构器 `reconstructorBlock` | 原版实例/Block/TE 注册均注释，保留模型不等于有效机器 | 不计默认范围，未实现 |
| `ItemGlasses`、`cosmeticPane` 等孤立类/字段 | 未发现默认注册调用；不以文件存在扩张功能范围 | 不计默认范围，未实现 |
| 旧 1.7/1.12 存档原地升级 | 方块、物品、metadata、NBT、群系和玩家数据都跨代；本矩阵不承诺直接兼容 | 非当前验收承诺 |

每个功能标为“已完成”前，必须同时具备注册/资源、可获得路径、功能行为、持久化、多人同步和专用服务器证据；涉及电力的另验 FE/EU 共享存储、simulation、方向、电压/安培、过压、同 tick 总预算与转换余数。涉及 TC4 资源的另验精华/vis 独立成本，不能用 FE/EU 替换掉全部魔法资源。构建成功只证明编译与打包，不替代这些验收。

[tc-blocks]: https://github.com/Mordenkainen/Technomancy/blob/37bf9a56fe1f713258f298d7ef392b104ccae88f/src/main/java/theflogat/technomancy/lib/compat/Thaumcraft.java#L216
[tc-items]: https://github.com/Mordenkainen/Technomancy/blob/37bf9a56fe1f713258f298d7ef392b104ccae88f/src/main/java/theflogat/technomancy/lib/compat/Thaumcraft.java#L190
[tc-research]: https://github.com/Mordenkainen/Technomancy/blob/37bf9a56fe1f713258f298d7ef392b104ccae88f/src/main/java/theflogat/technomancy/lib/compat/thaumcraft/TechnoResearch.java#L43
[core-blocks]: https://github.com/Mordenkainen/Technomancy/blob/37bf9a56fe1f713258f298d7ef392b104ccae88f/src/main/java/theflogat/technomancy/common/blocks/base/TMBlocks.java#L98
[core-items]: https://github.com/Mordenkainen/Technomancy/blob/37bf9a56fe1f713258f298d7ef392b104ccae88f/src/main/java/theflogat/technomancy/common/items/base/TMItems.java#L33
[botania]: https://github.com/Mordenkainen/Technomancy/blob/37bf9a56fe1f713258f298d7ef392b104ccae88f/src/main/java/theflogat/technomancy/lib/compat/Botania.java
[bloodmagic]: https://github.com/Mordenkainen/Technomancy/blob/37bf9a56fe1f713258f298d7ef392b104ccae88f/src/main/java/theflogat/technomancy/lib/compat/BloodMagic.java
