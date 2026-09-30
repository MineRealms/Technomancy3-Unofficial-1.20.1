# 功能范围与迁移验收矩阵

核对日期：2026-09-30。目标：Minecraft 1.20.1 / Forge 47.4.23 / Java 17，核心对接 TC4R `0.1.0-20721`，以 Forge Energy 和 GTCEu EU 替换 CoFH RF。**状态列区分三种口径：标“已实现”的多数只经过 JUnit/GameTest 与数据校验而未实机点击；标“待迁移”的尚未实现。构建成功或测试通过都不等于实机玩法验收，逐项证据见 [VALIDATION.zh-CN.md](VALIDATION.zh-CN.md)。**

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
| S3 仪式联动 | 仪式、Existence、Botania 可选内容（Blood Magic 不做） |
| S4 深层 TC | 稳定灯、电动风箱、群系修改等需要额外 TC4R 适配的行为 |
| S5 验收 | 专用服务器、客户端、联机、重载、守恒与研究/配方可达性 |

## TC4 方块和机器

本节全部来自原版 [Thaumcraft.RegisterBlocks][tc-blocks]。20721 的 API 名称来自用户提供的 sources JAR 实查；有同名 API 不表示它提供旧内部类全部能力。API 统一前缀为 `dev.tc4port.thaumcraft.api`。

| 功能与旧注册字段 | 来源实现 | 依赖、20721 适配与主要风险 | 阶段 | 必须验证 | 状态 |
|---|---|---|---|---|---|
| 精华发电机 `essentiaDynamo` | `TileEssentiaDynamo` | 已实现为 `technom:essentia_dynamo`：`essentia.EssentiaTransport` + `aspect.AspectContainerView`；**刻意不实现 `EssentiaSource`**（它是消费者，不该被注魔祭坛抽走燃料）；燃料值表改为数据驱动 `data/technom/technomancy/essentia_fuel/`；RF 输出改 FE/EU 共用账本 | S1 | 管道与罐取料；模拟不扣料；满电停机；两个端口同 tick 不重复输出；重载燃料进度 | **已实现**。真实 `thaumcraft:warded_jar` 取料、向真实 FE 消费者交付、红石三态双向门控、满缓冲不扣料不烧料、源质与能量守恒、六面旋转、掉落可得、存盘往返，以及经 2 节 `thaumcraft:essentia_tube` 的真实管道链路，均由 `technom_dynamo` 批次 11 个 GameTest 在有/无 GTCEu 下实测通过（另有 116 项 JUnit 覆盖燃料表 codec、逐分支取值与满缓冲边界）。**客户端渲染未验证**（无 BER，静态模型，喷口角度按 22.5° 近似原版的 30°）；节点发电机仍属 S2。刻意偏离清单与实测数值见[验证记录](VALIDATION.zh-CN.md#源质发电机验证2026-09-28) |
| 节点发电机 `nodeDynamo` | `TileNodeDynamo` | `node.AuraNodeView` / `NodeVis`；核对消耗节点 vis 的语义，不能把节点总容量当可用燃料 | S2 | 节点损耗/恢复、类型和亮度边界；节点卸载；FE/EU 输出守恒 | 已实现（未入游戏验证）：`technom:node_dynamo`，9×9×9 已加载区块内节点/节点罐经 `NodeApi.replaceLoadedState` CAS 每 21 tick 取 1 Vis（不取末点）；1 Vis = 30 燃料点 × 80 Q × `essentiaFuelScale`（默认 600 Q）；复用 `DynamoFuelBank`/`MachineEnergy`/`RedstoneControl`；抽取移入红石门内。JUnit `NodeVisDrainTest`；GameTest `NodeDynamoGameTests` 已写未跑。**渲染器已移植（未人眼确认）**：本轮补上此前缺失的 4 个盒子，整机 22 盒（`NodeDynamoRenderer`/`NodeDynamoItemRenderer`/`NodeDynamoItem`） |
| 量子精华罐 `essentiaContainer` | `TileEssentiaContainer` / 对应 Block | `EssentiaTransport`、容器视图、标签和显示；旧版内部罐继承改自有 BE | S1 | 容量、吸力、方向、标签筛选、邻罐抽取、破坏掉落和存储恢复 | **已实现**：`technom:quantum_jar`，640 点单 aspect，吸力 `(贴标签 ? 64 : 48) + 存量/50`、只允许上面、贴标签内容优先且拒绝与内容矛盾的标签、摘标签返还空白标签、比较器输出、液面与标签渲染器。实机（Rosetta 桥，`probes/essentia/`）5/5：真实 TC4R 双管道拓扑守恒 64/64、吸力逐档实测、掉落物 NBT 往返。**刻意偏离**：已贴标签基数由 56 提到 64（TC4R 标签罐为 64，原值会比参考实现还弱）；新增比较器输出。**未验证**：客户端渲染、跨重启持久化、右键交互未实机点击 |
| 量子玻璃/装饰块 `cosmeticOpaque` | `BlockCosmeticOpaque` | 无独立机器 BE；与量子罐奥术配方一起迁移；不能遗漏无 TE 材料路径 | S1 | 配方可得、碰撞和透光、模型及掉落一致 | 奥术配方已完成（数据层）：`technom:arcane/quantized_glass`，4 玻璃 → 4 块，vis `ordo 5 / ignis 5`，已由服务端加载；方块本体见 S1-A。未在游戏内合成过 |
| 精华储库 `reservoir` | `TileEssentiaReservoir` | `EssentiaTransport`；旧 `takeEssentia` 返回值必须按“实际取出量”复核 | S2 | 单/多次模拟、取放返回量、吸力、无负数和复制 | **已实现（未实机）**：`technom:essentia_reservoir`，256 点单要素、六面双向、吸力 128（满时 0）、最小吸力 0、每 tick 每面拉 1 点且先模拟后取；`takeEssentia` 返回实际取出量（旧版颠倒）；异要素不再被取后销毁；非 `EssentiaSource`；掉落保留内容。与旧版一样无配方/研究（仅创造栏）。显示名为 **Quantum Reservoir**（TC4R 自带同名 `thaumcraft:essentia_reservoir`，为多要素 256/吸力 24 的不同方块，避免撞名）。GameTest `technom_s2_storage` 已写未运行 |
| 创造精华罐 `creativeJar` | `TileCreativeJar` | 创造工具；与生存存储和燃料规则分离 | S2 | 创造可选要素、管道输出；无生存配方或错误掉落导致可获得 | **已实现（未实机）**：`technom:creative_jar`，`EssentiaTransport`+`EssentiaSource`，顶面无限输出、all-or-nothing 与 `[0,amount]` 边界由 JUnit 覆盖；无配方、生存不可破坏、无 loot table；设定要素仅创造玩家。刻意偏离：最小吸力 0。GameTest 已写未运行；渲染未验证 |
| 精华线圈 `teslaCoil` | `TileEssentiaTransmitter` | `EssentiaSource` 不等于任意管道；必须一起迁入连接工具、连接记录与适配器；保留原版 Buffer/Arcane Bore 修复 | S2 | 连接/解除、标签/方向、距离、区块卸载、跨端口去重、方块替换后失效 | **已实现（未实机）**：`technom:essentia_coil`。既向 `facing` 邻居推送（每链接 1 点/tick，先双端模拟再取货），又作为该面上只出不进的 `EssentiaTransport` 供工作台/钻机/灯/管道自行抽取，取代旧版硬编码特殊方块类名与 Buffer 变通。**刻意不实现 `EssentiaSource`**（远程抽取不得穿过线圈放大距离）；`essentiaAmount` 改为链接真实可给量（旧版恒 1）；印字罐标签过滤，取下/破坏归还同一张标签；`minimumSuction` 保持 0。连接模型见下一行。研究 `technom:TESLACOIL`（新文件 `thaumcraft/research/coils.json`，复用既有分类）、奥术配方、要素与掉落表已写入，数据校验通过。**未验证**：客户端模型/上色、真实 TC4R 自抽取机器实机取货；5 项 GameTest（批次 `technom_coils`）已编写未运行 |
| 能量凝聚器 `condenserBlock` → `technom:energy_condenser` | `TileCondenser` | 精华存储/转换与 FE/EU；逐项核对源要素和副产物规则 | S1 | 可作为耗能闭环候选；满槽不耗电、停机恢复、显示同步、产物守恒 | 方块与 BlockEntity 已迁移：FE/EU 六面输入且无输出、匀速进度条转换、64 点 potentia 缓存、六面输出开关（blockstate multipart，无 BER）、三态红石、向真实源质罐推送并附守恒断言；A-6/A-9/A-10/A-11/A-12/A-13 均有测试证据，详见[验证记录](VALIDATION.zh-CN.md)。**尚缺配方与研究（只能创造获得）、客户端渲染与存档重载未验证** |
| 神秘净化器 `processorTC` | `TileTCProcessor` | 精华 API + 共享纯矿加工链；原版消耗 Ignis，不应因替换 RF 而取消此成本 | S2 | 要素消耗、每模块重复加工上限、输出数量/NBT、输入输出自动化 | **已实现（未实机）**：`technom:processor_tc`，64 点 ignis 缓存、吸力 128/满时 0、六面只进不出、每工作 tick 花 `max(1, 阶段+2×次数)` 点（生矿 2/tick、二次 5/tick）、60 tick 一次加工；**满输出槽零消耗**与**每面 `IItemHandler`（输入只进/输出只出/`null` 面内部）**两项旧缺陷均已修复并有测试；含 GUI（`technom:processor` MenuType）、奥术配方与 `technom:PROCESSOR` 研究。GameTest `technom_s2_processing` 已写未运行；GUI 未实机渲染 |
| 邪术吞噬器 `eldritchConsumer` | `TileEldritchConsumer` | 要素查询、破坏方块和耗能；旧 TC/Minecraft 内部逻辑不能直接复制 | S2→S4 | 每 tick 工作预算、不可破坏方块、方块实体库存、掉落/要素不得双重收益、卸载恢复 | **已实现（未实机）**：`technom:eldritch_consumer`。每吞掉一个生物、掉落物或方块花一份能量（`eldritchConsumerCostQ`，默认 20000 Q；缓冲 1,000,000 Q，FE/EU 六面输入），顺序为生物→掉落物→方块；六种工作范围潜行右键切换。不可破坏方块（硬度 < 0）与带方块实体的方块一律跳过；破坏方块只取掉落物的要素、不生成掉落物，杜绝双重收益。要素存入四要素池，从六面抽取。注魔配方、`technom:ELDRITCHCONSUMER` 研究、模型与掉落表已写，数据校验通过。GameTest `technom_s2_consumer` 通过；实机未点击。**渲染器已移植（未人眼确认）**：30 盒（9 个机体 + 转子 + 4 组各 5 段手臂），并且面板动画缺口已修——上游把 `panelRotation`/`cooldown` 作为公开字段同步到客户端并在此读取，本工程把这一对留在方块实体上（服务端持有 `cooldown`，客户端在自己的 ticker 里缓动 `panelRotation`），转子只在工作时旋转。 |
| 高级分解台 `advDeconTable` | `TileAdvDeconTable` | `aspect.AspectQueryApi` / `AspectPoolApi`、玩家研究状态；旧 owner 名称改稳定身份 | S2 | 基础要素拆分、研究点奖励上限、离线/改名玩家、自动化、奖励仅结算一次 | **已实现（未实机）**：`technom:adv_decon_table`。单槽、80 tick 破坏（潜行装力量宝石减半到 40）；先用 `rand(80) < 原始要素总量` 再用 1/8，再在物品所含原始要素中均匀取一种，经 `AspectPoolApi` 记到放置者名下。放置者存 UUID（改名不丢身份），离线时奖励挂起保留，到 `Short.MAX_VALUE` 上限则丢弃而不是像原版那样卡死机器。自动化走真实的单槽 `IItemHandler`。注魔配方、`technom:ADVDECONTABLE` 研究、模型与掉落表已写，数据校验通过。JUnit `DeconstructionTableTest` 与 GameTest `technom_s2_decon` 通过；实机未点击。**渲染器已移植（未人眼确认）**：9 盒（`AdvDeconTableRenderer`/`AdvDeconTableItemRenderer`/`AdvDeconTableItem`）。 |
| 精华融合器 `essentiaFusor` | `TileEssentiaFusor` | `EssentiaTransport`、`AspectApi`，多输入面与输出要素合成；共享耗能层 | S2 | 输入面配置、合成比例、输出堵塞、红石、面配置重载及资源守恒 | **已实现（未实机）**：`technom:essentia_fusor`，四水平面标记（装满药瓶=输入、空药瓶=输出，上表面四象限右键）、每面 64、两进一出、组合规则直接用 TC4R `AspectApi.combination`；红石默认 HIGH（同原版）；输出面负吸力 −48、输入 +48（满则 0）。**刻意偏离**：每次融合能量成本由固定 1000 FE 改为 `max(1000, 输出要素最大燃料值×80×essentiaFuelScale)`，否则"廉价原始要素→昂贵复合要素→发电机"是可再生净能量收益（11 项 JUnit 覆盖守恒与全倍率不获利）；另修 `takeEssentia` 无视方向、满面仍报吸力、不可输出面仍报存量三处旧缺陷。含 BER（四象限要素图标）、注魔配方与 `technom:ESSENTIAFUSOR` 研究。GameTest `technom_s2_fusor` 已写未运行；渲染未实机 |
| 节点制造器 `nodeGenerator` | `TileNodeGenerator` | `NodeApi` 只改已有节点；创建复用 `AuraNodeBlock.setPlacedBy` 的原语（放 `TCBlocks.AURA_NODE` + `setNodeState`），不需要 Mixin | S2→S4 | 成型、燃料/精华/电力共同提交、节点状态保存、失败回滚与完整拆除 | 部分实现（未入游戏验证）：`technom:node_fabricator` 成对（相距 6、互相面对，节点在 facing×3 且 +1 高）对已有节点/节点罐工作：1000 Q + 1 精华回充 1 点魔力；装效能宝石后 10000 Q + 10 精华提高该要素 base 并补 1 点（也是加入新要素的途径）。全部经 `NodeApi.replaceLoadedState` 同 tick CAS 提交，只有在节点确实变更后才扣精华与电力。缓冲 50,000,000 Q、256 单精华、吸力 48/最小 32、红石默认 LOW 且九格任一带信号即停。**节点创建已实现**：两台之间没有节点时，北/西侧只吸 auram、南/东侧只吸 vitium（吸力/接收都按专属要素门控），法杖右键（`WandInteractionTarget` 走 `Block#use`）在两缓冲合计 > 64 且能量足够时启动 200 刻仪式；完成时按 `(auram+taint)/2` 造出节点，类型/修正按上游 `generateNode` 级联（如 400 总量 → HUNGRY/PALE），Vis = 半总量，消耗 `round(((a+t)/2)^2 * 762.939453125)` Q，只有真正建成才扣精华与电力。创建原语 `NodeCreation` 直接复用 `AuraNodeBlock.setPlacedBy` 的路径，无 Mixin。外壳不再破坏玩家方块：位置被占即结构不成型。JUnit `NodeFabricatorWorkTest`、`NodeCreationRulesTest`；GameTest `technom_node_fabricator` 8 项（含建节点与专属吸力）。**客户端特效已补**：节点真正建成时才触发——从方块四角各升起一条带抖动的闪电（`RenderType.lightning()` 画成朝向摄像机的四边形，寿命 30 刻后淡出），同时按要素颜色发一圈尘埃粒子与附魔粒子并播放雷声；抖动种子取自 `BlockPos`，所以同一位置在所有客户端画出的闪电形状一致。闪电是几何体、只能由客户端画，因此走 `TechnomFxPacket`（`TechnomNetwork` 的 id 1）发给 64 格内的玩家；粒子用 `ServerLevel.sendParticles` 直接发送，不需要数据包。**融合焦点**放回节点走同一条路径。服务端安全：客户端类只从 `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)` 与客户端事件订阅里被引用|
| 节点多方块占位 `fakeAirNG` | `BlockFakeAirNG` / `TileFakeAirNG` | 主机多面能力代理；所有 FE/EU 端口必须共享主机存储和吞吐预算 | S2 | 断主机/断端口、跨区块加载顺序、拆卸不残留、能力失效、不复制库存/能量 | 已实现（未入游戏验证）：`technom:node_fabricator_shell`（无 BlockItem、无掉落表、不可破坏、不可见、无碰撞）。八个外壳把 `ForgeCapabilities.ENERGY` 和 `EssentiaTransport` 直接转交主机——交出的是主机 `MachineEnergy` 的同一个能力实例，故余额与每 tick 预算天然共用；主机消失/不再认领即自毁，主机区块未加载时端口报空而不自建存储；创造模式破坏外壳会连带拆除主机，避免"有洞但静默不工作"。证据结论：`TileFakeAirCore` 是共享基类（`TileFakeAirNG extends TileFakeAirCore`，而 `BlockFakeAirLight.createNewTileEntity` 直接 new 它，且在 `TMBlocks.java:126` 全局注册一次），因此它既不专属本行也不专属 S3 `fakeAirLight`；本组只在自己包内实现"主机消失即自毁"语义，S3 合并时可提取共用。GameTest `NodeFabricatorGameTests` 已写未跑 |
| 注魔稳定灯 `fluxLamp` | `TileFluxLamp` | 20721 的配方/注魔物品 API 不等同于“修改运行中祭坛不稳定度”的公开接口；需补 API 或版本限定适配 | S4 | 同平面祭坛识别、不稳定度变化、原有副作用、多个灯叠加与重载 | **已实现**：`technom:flux_lamp`。存 32 点 ordo（吸力 128，只进不出）、1,000 mB 淤泥罐对外输出；每 10 tick 找一次祭坛（原版的 21×10×21 搜索盒，带缓存），祭坛空闲时最多抽 5 次、每次 5 ordo 换 1 点不稳定度 + 200 mB 淤泥。读 `instability()/crafting()` 是公开 API，**写**不稳定度只能经 `compat/thaumcraft/ThaumcraftInternals`（反射 TC4R 自有字段，字段不参与重混淆，查不到就降级为只存 ordo）。**顺序**：先确认不稳定度真的降下来了再扣 ordo（上游是先扣后写，因为它的字段不会失败；本工程若反射不到，绝不能白扣料）。**主动抽取**：照上游每 10 tick 从正上方邻居抽 1 点 ordo（条件同上游：canOutputTo(DOWN)、持有 ordo、吸力 <128、最低吸力 ≤128）。**刻意偏离 A-7**：上游 `getEssentiaType/Amount` 会在有料时如实上报，导致 TC4R 缓冲管在它这一面白耗一次转向；本工程与处理器/凝聚器一致地返回 null/0（只进不出的机器不上报可取存量）。`isConnectable` 仅上面、`renderExtendedTube` 为真，同上游。注魔配方（machina/vitium/ordo/lux 各 45、不稳定度 10）与 `technom:FLUXLAMP` 研究已写。**未实机验证**：真实祭坛上的不稳定度下降 |
| 电动风箱 `electricBellows` | `TileElectricBellows` | `alchemy.ArcaneBellowsApi` 当前是吹向/相邻计数查询；不等同于注册自定义风箱、加热或加速回调 | S4 | 炼金设备实际识别、方向、倍率上限、仅工作时耗电、不同 TC 设备兼容 | **已实现**：`technom:electric_bellows`，20,000 Q 缓冲、每次鼓风 3,000 Q。按 facing 吹：1 或 2 格外的奥术炼金炉（`burnTime<=2` 且 `vis<50` 时置 80 并开加速，同样走 `ThaumcraftInternals`），或 1 格外的原版熔炉——原版熔炉写不进燃料，改为一次充能买 80 tick 的推进（每 2 tick +1 cooking，共 40 步，与原版“burnTime=80 + 每 2 tick +1”等价），用 TC4R 公开的 `FurnaceAccessor` 无需反射。奥术配方（aer/ordo/ignis 各 30，E 位用铁块替代已排除的 TE 机框）与 `technom:ELECTRICBELLOWS` 研究已写。**不移植**：上游还有一条“2 格外是奥术炉”的伸距规则，TC4R 没有对应机器，故只保留 1 格；原版熔炉分支额外要求输入**确实有熔炼配方**（上游同样先查 `FurnaceRecipes`），避免对烧不出来的东西白付 3000 Q。**未实机验证**。**渲染器已移植（未人眼确认）**：5 盒；`electricbellows.png` 确认为 **128×64**（上游导出残留的 `setTextureSize(64,32)` 无效，只有 128×64 能让五个部件都落在图内）；风箱袋动画按上游 `updateEntity` 的无状态振荡器展开成循环表，锯齿波 0.375→1.025、周期约 36 tick。 |
| 生态转换器 `biomeMorpher` | `TileBiomeMorpher` | `taint.TaintBiomeApi` 可污染已加载列；魔法森林/阴森群系等完整目标仍需现代群系写入适配 | S4 | 四分之一群系网格、垂直范围、客户端刷新、存档、边界与卸载；限制批量工作量 | **已实现**：`technom:biome_morpher`，800,000 Q 缓冲、每 20,000 Q 改两列（原版 `alterBiome` 两次），随机偏移沿用 `rand(100)-rand(100)` 三角分布，落在已目标群系上重试一次；目标由右键在魔法森林/阴森/污染之地间循环（blockstate 属性而非 metadata）。群系写入用公开的 `TaintSpreadLogic.setSpecialBiomeColumn` + `TCBiomes.*`，**无需 Mixin**；新增未加载区块检查，避免像原版那样把充能花在无效列上。奥术配方（terra/ordo/aqua 各 30）与 `technom:BIOMEMORPHER` 研究已写。**未实机验证**：客户端群系刷新。**渲染器已移植（未人眼确认）**：14 盒（上游声明 22 个 `addBox`，其中 8 个 `CenterTop*`/`CenterBottom*` 在构造器与 `render()` 里被注释掉、从未构建）。 |

## TC4 材料、法杖、工具和研究

注册证据为 [Thaumcraft.RegisterItems][tc-items]，研究证据为 [TechnoResearch][tc-research]。5 个普通 Item ID 是材料、笔、杖芯、融合核心、Technoturge 权杖；不把杖芯 metadata 或两种 WandRod 重复计作 Item ID。

| 功能 | 来源及原版范围 | 依赖、20721 适配与风险 | 阶段 | 必须验证 | 状态 |
|---|---|---|---|---|---|
| TC 材料组 | `ItemTHMaterial`：neutronizedMetal、enchantedCoil、neutronizedGear、penCore；metadata 4 为旧连接工具过渡项 | 坩埚/奥术/注魔/普通配方和 tags；metadata 4 缺独立图标并会在更新时替换为 coilCoupler，不宜保留为独立生存材料 | S1 | 所有材料配方可得；无 TE/RF 物料硬引用；加工链和研究引用一致 | 配方已完成（数据层）：`crucible/neutronized_metal`、`enchanted_coil`、`neutronized_gear` 三条已由服务端实际加载；要素数据已写入。penCore 配方与要素已由 S2 书写笔补齐。未在游戏内实际合成过 |
| 长效书写笔 | `ItemPen` | `item.ScribeTools`；耐久与研究台识别 | S2 | 研究台接受、使用耗损、合成余物、服务端结算 | 已实现（未入游戏验证）：`technom:pen`，实现 TC4R `api/item/ScribeTools`，3000 次墨水（耐久即墨水，可铁砧修复）；右键两张相邻 `thaumcraft:table` 成型研究桌并把笔放入书写槽——此处使用受版本约束的内部桥 `ResearchTableBlock.form` + `CompoundBlueprintCatalog.isTrigger`（公开 API 只有笔记事务）。同时补上 `technom:pen_core` 配方（`forge:nuggets/iron` + `forge:dyes/black`）与 `technom:PEN` 研究。GameTest `PenGameTests` 已写未跑 |
| Energized 杖芯与充能行为 | `ItemWandCores` / `ElectricWandUpdate`，旧 `electric` WandRod | `wand.WandPartApi`、`wand.behavior.WandBehaviorApi`；物品电力按 FE 能力接入，充电与 vis 转换必须守恒 | S2 | 装配、上限、充电、六原始 vis、物品切换/丢弃/重载、不重复扣款 | 已实现（未入游戏验证）：`technom:energized_wand_core` + 杖杆定义走数据（`data/thaumcraft/data_maps/item/wand_rods.json` 合并层 + `#thaumcraft:wand_rods` 标签），材料 id `technom:electric`、容量 25、craft_cost 10；充能由 `WandChargeEvents` 每 20 tick 扫描背包，经 `WandApi.insert` 以 centivis 结算，100 Q = 1 厘魔力（旧版 10000 RF = 1 魔力），缓冲存于 ItemStack 而非 Item 单例；TE 电容/线圈原料改为 enchanted_coil×2 + neutronized_metal + 红石块 + 铜块。JUnit `WandChargeTest`；GameTest `WandChargeGameTests` 已写未跑 |
| Technoturge 杖芯与权杖 | `ItemTechnoturgeScepter` / `ScepterRecipe`，旧 `technoturge` WandRod | `CustomWandItem` / `CustomWandSpec` / `WandPartApi`；动态帽材质与配方改现代数据 | S2 | 铁/金/神秘金属帽组合、容量/折扣、充能、配方成本和研究门槛 | 已实现（未入游戏验证）：`technom:technoturge_core`（数据杖杆 `technom:technoturge`，容量 100、craft_cost 11，权杖 ×1.5 = 每原始 150 魔力）。不再移植 `ItemTechnoturgeScepter`/`ScepterRecipe`：TC4R 内置权杖合成（3 个同帽 + 神秘护符 + 杆芯，费用 cap×rod×1.5，需 `SCEPTRE` 研究）与旧配方布局和成本完全一致；FE 充能走物品 `ForgeCapabilities.ENERGY`（只进不出，模拟不扣款）。扳手语义缩减为 `TechnomWrench`（仅本模组机器）。GameTest `WandChargeGameTests` 已写未跑 |
| 融合核心/焦点 | `ItemFusionFocus` | `focus.action.FocusActionApi` + `NodeApi`；旧 Item 单例存节点状态且放置后未清空，必须改逐 ItemStack 数据与服务端事务 | S2→S4 | 两玩家/两物品隔离，移动节点仅一次，失败不丢节点、成功不复制，卸载和重进保存 | 部分实现（未入游戏验证）：`technom:fusion_focus` 为真正的 TC4R 焦点（`FocusIdentityItem` + `data/technom/thaumcraft/focus_catalog/fusion_focus.json`，文件名必须等于焦点 id），动作经 `FocusActionApi` 注册在 `USE_BLOCK`：第一次右键节点选中（免费），第二次右键另一个节点把源节点的 base 与当前魔力经 `NodeApi.replaceLoadedStates` 双节点原子事务并入目标，费用为六原始各 3000 厘魔力（旧版同值），源节点掏空后才移除其方块，装不下的部分留在源节点。选中状态存于法杖的 focus properties（`FocusApi.updateInstalledProperties`），彻底取代旧版 Item 单例共享状态且不会重复复制节点。**凭空创建的手势已恢复**：潜行右键一个节点把它整颗收进焦点（节点方块移除，状态以 `AuraNodeState.CODEC` 存在该焦点栈的 `fusion_carry` 属性里），再右键空地重新立起（收着节点时不会吸收第二个，节点永远只在世界或焦点之一）。创建走 `NodeCreation.create` 的完整状态重载（base/current 分开），复用 `AuraNodeBlock.setPlacedBy` 的路径，无 Mixin。费用与合并相同。**未实机验证**JUnit `NodeFusionTest`；GameTest `FusionFocusGameTests` 已写未跑 |
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
| 水晶、催化器、假空气光源、玄武岩 | 五属性 metadata 与辅助方块，4 个 Block ID | BlockState/独立物品、照明与模型；辅助空气的放置清理不能吞方块 | S3 | 各属性形状、仪式匹配、破坏清理、照明与同步 | **已实现**：水晶与催化器各五种、玄武岩 `technom:basalt` 已注册，模型/blockstate/语言键齐备；催化器持有 `CatalystBlockEntity`，潜行右键或红石上升沿触发，运行中的核心不可破坏；`fakeAirLight`（亮度 15、主机消失即自毁）与其 BE 已落地。**已补**：五种水晶（荧石粉+染料，产 3）与五种催化器（金块+染料+专属材料）共 10 条合成配方，此前只能从创造栏获得。**上游颜色错位**：1.7 的水晶配方按 metadata 顺序取染料（地球/nature=黑、火=白、水=红、光=绿、暗=蓝），与催化器按语义取色（暗=黑、光=白、火=红、自然=绿、水=蓝）**不是同一套映射**。本工程按“与上游严格对应”照抄前者，所以“火焰水晶”用白色染料、“流水水晶”用红色染料——这是上游原样，不是笔误；改动记录见 VALIDATION。**渲染器已移植（未人眼确认）**：水晶的 3 阶段代码渲染器（`CrystalRenderer`/`CrystalItemRenderer`/`CrystalItem`），新增纯标记 `CrystalBlockEntity`，`CrystalBlock` 改为 `BaseEntityBlock` 且 `getRenderShape=INVISIBLE`。**用户决策 A（渲染方式）**：水晶走 BlockEntity + 代码渲染器——`blockcrystal.png` 是 64×64 的 RGB 贴图、没有 alpha，单靠 `render_type: translucent` 做不出 0.6 透明，`BlockColors` 也不行（原版方块着色会丢弃颜色的 alpha），只能走代码渲染的 `RenderType.entityTranslucent`。**用户决策 B（修正上游截断缺陷）**：上游 `ModelCrystal` 把 0–255 的通道值传给期望 0.0–1.0 的 `glColor4f`，每个通道被钳到 1.0，于是自然/火/水碰巧正确，而光 `0x111111` 与暗 `0xDDDDDD` 都被钳成纯白、上游根本区分不出光与暗；本工程按上游作者本意取色（自然绿/火红/水蓝/光近黑/暗浅灰），**不复刻上游的渲染结果**。注意此条与上面的“染料映射错位”是两回事，后者保持原样不动。**第六轮**：10 个水晶模型（5 方块 + 5 物品）的 `particle` 从 `technom:entity/blockcrystal` 改为上游 `BlockCrystal.registerBlockIcons` 实际注册的 `technom:block/catalyst_0..4`——`entity/` 是渲染器直接绑定的模型表、不进方块图集，粒子此前是 missingno，由 Rosetta 探针 `00_` 抓到；`.noOcclusion()` 本就正确。水晶的**碰撞箱仍按整格**，上游按堆叠阶段给 0.25..0.75 / 0.375..0.625，未移植 |
| Existence Gem | `ItemExistenceGem`（`TMItems.java:30/37/43`） | 1.7 用 item damage（100=空、0=满）承载充能；现代改栈内 NBT | S3 | 空/满边界、击杀充能、上限、只作材料不作工具 | **已实现**：`technom:existence_gem`，充能 0..100 存在 ItemStack tag（不再是“耐久条”的反向语义），击杀生物时按 `ExistenceConversion.getGem`（村民 25、动物 2、怪物 1）给背包里第一颗未满的宝石，满则发光。金粒×4 + 绿宝石的合成配方已补。**此前整个物品缺失**，而它是全部 Existence 机器配方的核心材料 |
| 16 个仪式 | 下表有效列表 | 原版硬编码 0/256 高度和同步大范围改世界；改实际维度边界、分 tick 任务、区块加载边界 | S3 | 正反向阵列、负 Y、重载恢复、移动 BE 数据、失败回滚、掉落守恒和 tick 预算 | **已实现 16/16**（`RitualRegistry.java:39-54`）：净化/流水/塌陷/黑洞各 T1–T3、火 T1/T2、**Extraction**、**Existence 喷泉**。GameTest 批次 `technom_s3_rituals` 断言数量与五种核心类型全覆盖。**客户端特效已补**：仪式触发时在催化器处生成该元素颜色的粒子环（半径 2.5）与竖直光柱（高 3.0）并播放信标音效；颜色由 `RitualFx.colour(Ritual.Type)` 给出（`Affinity.DARK` 的 0x101010 作为尘埃粒子近乎全黑，所以这里单独取了更亮的一套），`RitualFxTest` 固定五色且断言互不相同。特效只在 `activate()` 的一次性成功分支触发，**不在** `IRitualEffectHandler.applyEffect` 的每 tick 路径上，所以不会每 tick 刷一遍粒子与声音 |
| 仪式手册 | `ItemRitualTome`、GUI 与纹理 | Screen、现代文本/翻译和配方引用 | S3 | 所有已注册仪式可查、翻页、语言回退、无失效配方引用 | **已实现**：`ritual_tome` 物品 + `RitualTomeScreen`。屏幕是 8 个标签 + 每标签多个条目的 256×256 双页系统：标签用 `tomebuttonsritual.png` 的 16×16 图标、点击展开条目、条目用上下页按钮（`nextprevious.png`）和滚轮/左右箭头翻页；渲染 TEXT/IMAGE/RECIPE 三类半页，RECIPE 通过 `RecipeManager` 反查当前 Level 的合成配方并按 `ShapedRecipe`/`ShapelessRecipe` 还原 3×3 网格与产物，所以 1.20 配方改动不会让书失效；GUI 在 `computeScale()` 里按窗口剩余空间自动缩放（永远不放大到超过 1.0），因此高 GUI 倍率/小窗口不会让书溢出。仍是纯客户端 Screen，没有 MenuType。书/墨/荧石粉的合成配方与原"暗/火/水/光"颜色映射仍与上游对齐。**第八轮：正文全部改成 lang key**（`technom.tome.{tab,entry,page}.*`，53 条，中英各一份），因为换行器原先只在空格处断行——英文可用，中文没有空格，整页会被排成一行跑出书页；`RitualTomePage.wrap` 改为"优先在最后一个空格断，没有空格就逐字断"，并把"某个字符本身宽过整行"这个原先会死循环的情形也纳入。文案的缺失由 `LangGuardTest` 从**源码里**恢复 key 再逐语言核对（书页、JEI 用途页都在内）。**未验证**：换行后的实际排版仍未人眼确认 |
| 物品线圈与连接工具 | `itemTransmitter`、`ItemCoilCoupler`、`ICouplable` | `IItemHandler`、稳定连接数据；1.12 工具实例/配方缺失不能沿用 | S2 | 仓库输入输出、模拟、满库存、标签、断连/卸载、重复点击和无物品复制 | **已实现（未实机）**：`technom:item_coil` + `technom:coil_coupler`（普通配方，已注册；旧“未注册”只在 1.12 分支成立，master `TMItems.java:36/42` 是正常的）。搬运走 `IItemHandler` 的侧面视图，先模拟源与目标、再按目标接受量取出，目标少收则依次退回原槽/源任意槽/世界，不复制不销毁。连接模型 `common/coils/`：仅同维度、每轴 ≤32、上限 16、按坐标去重、拒绝自连、未加载区块保留而失效端点剔除、版本化 NBT 且读盘重新校验；轮换代替每 tick 洗牌。谐频器在 `onItemUseFirst` 生效（旧版被箱子界面抢先），再次点击已连接目标即解除，全部结果有本地化提示。红石沿用 `RedstoneMode`/`RedstoneControl`（默认无信号运行），力量宝石装入后改为“目标有空间时输出弱红石”。JUnit 22 项（链接规则 12 + 物品守恒 10，含撒谎目标与掉落兜底）通过。**未验证**：客户端模型/上色；7 项 GameTest（批次 `technom_coils`）已编写未运行 |
| Potency Gem / 增幅 | `ItemBoost`，机器升级接口 | 保持"纯吞吐 ×4、效率不变"语义：升级同时把发电速率和每次源质消耗都乘 4。不做多级升级 | S1→S3 | 安装/卸下只结算一次、掉落保存、耗能与产能同时调整 | **源质发电机部分已实现**：`technom:potency_gem` 右键安装、潜行空手右键非输出面取回、破坏时掉落，效率不变已由 JUnit（任意燃料值、任意 `essentiaFuelScale`）与 GameTest（实测 4 点源质 = 64000 Q，与未升级的 16000 Q/点一致）双层验证。其余机器待各自迁移；未做安装/卸下的客户端反馈验证。配方已完成（数据层）：`technom:potency_gem` 的坩埚配方已由服务端加载，要素数据已写入 |
| Existence 喷泉 | `fountainExistence` / `TileExistenceFountain` | 仪式生成与玩家/实体资源，保留独立 Existence 概念 | S3 | 仪式生成、资源产消、主人离线、多人归属、实体事件一致 | **已实现**：每 tick 产 500、上限 1,000,000。**已接网**：实现 `IExistenceProducer`（此前不是，塔只认 producer，喷泉是孤岛，`technom_s3_existence` 现在证明塔能从喷泉取到 power）|
| Existence 燃烧器 | 一个 `existenceBurner` ID，普通/动态 2 变体 | 实体消耗、资源生产；不得简单改成所有行为都消耗 FE | S3 | 目标筛选、产量、红石、重载、动态版本差异 | **已实现**：普通（上限 100）/动态（上限 150、每杀扣 10,000 Q），±3 盒内村民 15、怪物 1、其他 2。两条配方已补（普通=宝石+铁砧；动态=普通+红石+活塞，采用上游非 TE 分支）。**渲染器已移植（未人眼确认）**：4 盒（普通与动态两个变体共用同一渲染器）。**第六轮**：补上漏掉的 `.noOcclusion()`（上游 `BlockExistenceBurner.isOpaqueCube()` 为 false）——此前该方块被当成实心，邻居朝向它的面被剔除，而它自己的模型是空壳，看上去就是邻居被挖了一个洞；由用户实机报告，GameTest `technom_code_drawn` 守卫。**碰撞箱仍是整格**，上游是 x/z `0.25..0.75`，未移植 |
| Existence 塔 | 一个 `existencePylon` ID，3 种变体 | 原版能力/范围与传输规则迁入新 BE | S3 | 范围、升级、消费者连接、跨区块/维度限制与守恒 | **已实现**：BASIC/ADVANCED/COMPLEX = 5/25/125（同时是自身容量），±7×1×7 扫描，先抽 producer→供 consumer→塔间均衡。三条配方（含两条升级 shapeless）已补 |
| Existence 使用器 | 一个 `existenceUser` ID：作物加速、收割、封印 3 变体 | 作物 tags/事件、`IItemHandler`、玩家实体状态 | S3 | 成长和收获成本、满库存、掉落、封印持续时间、维度/重生清理 | **已实现**：催熟/收割/封印三变体，消耗 30/30/500,000。三条配方已补 |
| 玩家属性/HUD/效果 | `PlayerData`、五 affinity、Existence level/power、drown/slowFall | 两版 Affinity 构造器都有赋值错误；1.12 同步接收被注释；改稳定身份、服务端数据和客户端显示 | S3 | 五属性彼此独立、登录/死亡/换维度同步、两客户端一致、配置关闭 HUD | **已实现**：亲和与 Existence 进度、HUD overlay（`ExistenceHud`）、被动效果、登录/变化同步包（`ExistenceSyncPacket`）全部落地并接线。原版两处缺陷已修（构造器 `id` 赋错、离线玩家空指针）。JUnit `AffinityTest` 固定编号与映射 |
| 宝物村民与宝物 | `ItemTreasure` 的 fireGem/powerPlate/goldenWing | 默认 `treasures && treasureSafeguard`，后者 false；保留默认关闭及配置说明 | S3 | 默认不激活；开启后事件副作用、掉落次数、封印交互、多人同步 | **已实现**：三件宝物 + 1/50 村民携带者 + Extraction 仪式取宝。**已按上游默认关闭**：新增 `world.treasures` 配置（默认 false，对应上游 `treasureSafeguard=false` 的净效果）——此前本工程无条件开启，与上游不一致。**受击/摧毁副作用已补**（`TreasureItem.onUserHit` / `onTreasureDestroyed`）：火宝石吞火焰伤害并点燃攻击者（15 秒）、力量板给"只作用于当前这一击"的抗性 V（1 tick、amplifier 4）、金翼把攻击者上抛（垂直 2.1）；未封印的携带者死亡时摧毁宝物——火宝石半径 30 爆炸 + 半径 25 点燃 120 秒、力量板半径 5 减速 + 把半径 5 内所有可破坏方块（含空气）变黑曜石、金翼半径 25 上抛（垂直 8）——**不再掉落**；被封印的携带者死亡才掉落宝物（端口把 `seal` 读作"宝物被保住"，上游则从不掉落任何物品）。`TreasureEvents` 由 `TreasureVillagers` 更名（它现在也处理玩家携带者的反击），`technom_s3_treasure` 批次 6 条 GameTest 固定这些行为 |
| 纯矿多阶段加工 | 每材料 1 Item ID、6 metadata 阶段；TC/BO/BM 各有加工记录 | 旧 OreDictionary 动态注册改预定义材料/tags/数据配方；接 GT 材料并明确 2～7 锭默认倍率 | S2 | 各模块至多两轮等原版规则、顺序组合、输出 NBT、矿/粉兼容、每阶段熔炼经验、无重复增殖 | **已实现（未实机）**：铁/金/铜 × 6 阶段 = 18 个 `technom:pure_<材料>_<阶段>`，每模块两轮上限与阶段上限都显式检查，加工记录只存"每模块次数"（阶段由物品身份承载），熔炼 2..7 锭、经验 1.0；输入用标签 `technom:processable/<材料>` 收生矿与矿石方块。**刻意偏离**：材料集合固定为三种原版金属（不再按 OreDictionary 动态注册），染色值写死。只装 TC 模块时阶段上限为 1，2..5 的物品与配方已备好待 S3 |

| 已注册仪式 | 数量 | 原版类 |
|---|---:|---|
| Extraction | 1 | `RitualExtraction` |
| Fountain of Existence | 1 | `RitualFountainExistence` |
| Cave In | 3 | `RitualCaveInT1/T2/T3` |
| Black Hole | 3 | `RitualBlackHoleT1/T2/T3` |
| Water | 3 | `RitualWaterT1/T2/T3` |
| Purification | 3 | `RitualPurificationT1/T2/T3` |
| Fire | 2 | `RitualOfFireT1/T2` |

## Botania（S3 选装）与 Blood Magic（不做，仅存范围记录）

用户排除的是 TE/RF **与 Blood Magic**（**Blood Magic 相关一律不做**，见 `CLAUDE.md` 的“已确定的方向”第 58 行）；Botania 保留为 S3 选装，下面把它保留为可选迁移范围，不强制变成核心依赖。原版有效范围来自 [Botania 注册模块][botania]、[Blood Magic 注册模块][bloodmagic]；目标版本依赖和 API 要在实际实现阶段锁定。**Blood Magic 四行仅作为上游范围的事实记录保留，状态一律标“不做（超出范围）”，不进入迁移计划。**

| 功能 | 来源 | 依赖/迁移风险 | 阶段 | 必须验证 | 状态 |
|---|---|---|---|---|---|
| 花卉/Hippie 发电机 | `TileFlowerDynamo` | Botania Mana 接收与 FE/EU 输出；不能移植旧 RF 接口 | S3 | Mana 减少与电力增加守恒；满电、花/池连接、拔除模组时核心能启动 | 已实现：`IManaReceiver`、100,000 Mana 缓冲、9×9 每池每 tick 100、`extractFuel` 换算（80/320 Q）、EU 输出 LV 2 安培（与另两台发电机同一推导，且补上了此前缺失的 `emitsEu()` 端口闸门）。GameTest 待补，模型/贴图已换（`block/flowerdynamo`） |
| 魔力制造器 | `TileManaFabricator` | FE/EU→Mana，Botania 池识别/传输；独立存量与方向 | S3 | 满池不扣电、吞吐、颜色/连接、存储重载、无双接口套利 | 已实现：自身即 `ManaPool`、100,000、1,000,000 FE/100 Mana、单面进电、扳手转面（`use` 接线）。模型/贴图已换 |
| 植物净化器 | `TileBOProcessor` | Botania 魔力 + 共享加工链；1.12 空侧面槽与 false 插拔为回归 | S3 | 各方向自动化、魔力成本、记录与阶段、GUI 同步 | 已实现：1,000,000 Mana 缓冲、9×9 每 tick 5,000、按加工刻计费；模型改回 `processorbo*` 贴图并按 `lit` 分两模型。GameTest `technom_s2_processing` 覆盖 |
| 魔力交换器、魔力流体与桶 | `TileManaExchanger`、`ManaFluid`、桶；1.12 新增 50,000 Mana 灌注配方 | 现代 FluidType/流动流体/能力、物品桶；1.12 FE capability 仍返回 CoFH 存储，类型和行为不能直接认定正确 | S3 | 双向转换余数、桶灌装/倒出、模拟、槽满/池满、FE/EU 同 tick 合计、配方平衡 | 已实现（1.20.1 精确版）：池在正上方、1,000 mB 罐、mode/扳手、1,000 Mana↔1 mB、1,000 Q/次、侧面限流；`FluidType`+Source/Flowing+`LiquidBlock`+`BucketItem`+客户端贴图。GameTest `technom_botania` 3 项 |
| Botania 材料和手册 | Mana coil、Manasteel gear、Lexicon 页面 | 数据配方、现代手册扩展；1.12 返回未注册 recipe 对象导致页面断链 | S3 | 全部材料/页面/配方可达、未安装时不加载 Botania 类 | 已实现：灌注/合成配方 7 条（`forge:mod_loaded` 门控）、Patchouli 词条 5 条放在 `assets/botania/patchouli_books/lexicon/`（Patchouli 的 `use_resource_pack` 按书 id 分组，必须放书的命名空间），客户端探针确认已进书（12 分类/250 词条）。**缺席安全已做**：全部 Botania 方块/物品/BlockEntity 由 `compat/botania/BotaniaContent` 在 `BotaniaPresence` 为真时注册，`-PwithBotania=false` 下 85/85 GameTest 通过 |
| 鲜血发电机 | `TileBloodDynamo` | Blood Magic 生命精华流体→FE/EU，方向和填充能力重建 | S3 | 仅正确流体、各面规则、燃料/电力守恒、空槽和卸载 | **不做（超出范围）** |
| 鲜血制造器 | `TileBloodFabricator` | FE/EU→生命精华；保留主动输出行为，修复 1.12 满槽扣能条件 | S3 | 满槽零消耗、模拟、相邻 capability 槽接收、每 tick 上限、重载 | **不做（超出范围）** |
| 鲜血净化器 | `TileBMProcessor` | Blood Magic LP/soul network + 共享加工链；旧名字 owner 改现代身份；常数 16 成本不能当兼容修复 | S3 | 网络主人、离线/无网络、LP 不足、成本与加工记录、自动化 | **不做（超出范围）** |
| Blood Magic 材料与祭坛配方 | 祭献锭、blood coil；祭坛/普通配方 | 现代 altar 数据与依赖隔离，使用无 TE 材料路径 | S3 | 祭坛等级、LP/流体成本、配方获取、未安装时核心能启动 | **不做（超出范围）** |

## 现代化联动（Jade / JEI / KubeJS）

原版 1.7 只有 Waila HUD 联动（`lib/compat/waila/WailaProvider` 及各 `*HUDHandler`，并用 `registerNBTProvider`/`getNBTData` 把服务端数据送到客户端）；JEI 与 KubeJS 是后来才有的接口。原则：**只补别人推导不出来的信息**——配方、燃料表、矿石标签、配置都已经由数据包或 ForgeConfigSpec 表达，一律不写代码重复一遍。实施细节与坑见 [COMPAT 第 7 节](COMPAT.zh-CN.md)。

| 联动 | 依赖与适配 | 阶段 | 必须验证 | 状态 |
|---|---|---|---|---|
| Jade（Waila 后继） | `compat/jade/`：`@WailaPlugin` + 一个 common 侧 provider（`IServerDataProvider.appendServerData` 服务端采集、`IBlockComponentProvider.appendTooltip` 客户端只读回传 NBT）。`compileOnly` 常驻，`runtimeOnly` 由 `-PwithJade` 控制 | S5 | 专用服务器不加载任何客户端类；`-PwithJade=false` 能启动；数值与 GUI 一致 | **已实现**：15 个方块实体（能量、源质存量/容量、加工进度、节点灵气与储能、仪式要素/阶级/倒计时、存在之力）；`EnergyHolder` 让 8 台机器的 FE 缓冲走同一分支。Rosetta 探针 `probes/client/40_compat_plugins.java` 核对 uid、登记数量与 13 个 lang key。**未验证**：实机把光标对准机器看排版；**刻意不含** Botania 三台机器（登记它们会让 common 侧插件类引用 Botania 方块实体类，在"有 Jade 无 Botania"的包里 `NoClassDefFoundError`） |
| JEI | `compat/jei/`：`@JeiPlugin`，一个自定义类别 `technom:essentia_fuel`（手绘无槽位页面）+ 一批走 JEI 内建 Information 类别的用途页；沿用原有 `compileOnly` + `runtimeOnly`，不加开关 | S5 | 类别只在客户端加载；表为空时不注册任何页面；`/reload` 改表后页面同步；Botania 缺席时不注册那 4 条用途页 | **已实现**：逐要素一页 + 一条兜底页，显示燃料值、每单位 Q、各地形条件与随机加成，源质发电机为催化剂。**第八轮新增用途页**：`JeiUsagePages` 列 38 条（44 个物品，水晶/仪式核心/存在之塔各自共用一条），`technom.jei.info.<path>`，`addIngredientInfo` 一条一次调用；表放在**不含 JEI 类型**的类里，好让 JUnit 能读它（`compat/jei` 不在测试运行时类路径上）。探针 `40_` 逐条核对"JEI 能按物品找到这一页"且描述不是原始 key。**刻意不做**：普通配方类别（数据包配方 JEI 免费展示，重复注册只是多一处要同步的地方）；`fake_air_light`/`node_fabricator_shell`（无物品）、`basalt` 与纯合成材料（除了配方没有可说的）。**未验证**：实机打开 JEI 看排版 |
| KubeJS | `compat/kubejs/`：`kubejs.plugins.txt`（jar 根目录，KubeJS 的发现机制，**不是注解**）+ 只覆写 `registerBindings` 的插件；**仅 `compileOnly`**，永不进任何运行时 | S5 | 未安装时插件类不被加载；发现文件名与类名一致 | **已实现**：绑定 `Technom` 全局，暴露燃料表查询（`maxFuelValue`/`maxEnergyPerUnit`/`fuelValueAt`）、`qPerEu`、`essentiaFuelScale`、`listedAspects`；未知要素抛异常而非回落兜底值。**刻意不做**：配方 schema（本模组无自定义配方类型）、事件组（无可监听事件）。**未验证**：开发运行时没有 KubeJS，只有探针核对发现文件与类资源 |

三者的共同边界：Jade 插件是 **common 代码**（两侧都加载，不能引用 `net.minecraft.client.*`），JEI 插件是**客户端代码**（JEI 本身仅客户端），KubeJS 插件**永不进运行时**。

## 能量接口（FE/EU）覆盖

FE 与 EU 只是**同一个** `EnergyLedger` 上的两个协议视图，不是两份存量：两者同 tick 共享 `maxReceivePerTick` / `maxExtractPerTick`，EU 另外计安培。`EnergyPorts.consumer(faces)` 同时置 `feIn` 与 `euIn`，`generator(faces)` 同时置 `feOut` 与 `euOut`，但**端口位本身不够**——`EuPort.inputEnabled()/outputEnabled()` 还要求 `limits.acceptsEu()/emitsEu()`。两者必须一致，否则该面会交出一个永远拒收的 `IEnergyContainer`，还会挡掉 GTCEu 自带的 FE 包装（花卉发电机在第九轮之前正是如此）。

**消费者（9 台，输入）**：魔力交换器 HV、魔力制造器 ZPM、节点制造器 IV、存在燃烧器（动态）IV、源质融合器 ZPM、生物群系改写器 IV、电动风箱 EV、诡异吞噬器 ZPM、能量凝聚器 EV。电压一律由 `compat/gtceu/EuRating` 从**各机自己的「最贵一 tick 耗电」**推导，不再共用常数；规则与推导表见 [VALIDATION 第九轮](VALIDATION.zh-CN.md)。

**发电机（3 台，输出）**：源质发电机、节点发电机、花卉发电机，一律 LV 2 安培，由 `320 / (32 × qPerEu)` 推导。取整会超出每 tick 预算，所以 2.5 截断为 2 是刻意的：`EnergyLedger.reservePackets` 同样按 `floor(maxExtract / qPerPacket)` 封顶，第三个安培一个 Q 也多送不出去。

**覆盖范围**：全部 12 台持有 `MachineEnergy` 的机器都已接 EU，没有只做 FE 的耗能机器。

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
