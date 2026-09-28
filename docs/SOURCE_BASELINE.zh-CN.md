# 源码基线、整合取舍与来源记录

核对日期：2026-09-27。本工程以原版 Technomancy 的完整玩法为行为基准，选择性吸收 Technomancy-2 的资源和修复意图，在 Java 17 / Forge 1.20.1 上重新实现平台层。**本文件记录已完成的源码研究，不代表旧游戏内容已移植。初始化时未复制原版或 fork 的游戏源码、美术素材；所有游戏功能状态见 [FEATURE_MATRIX.zh-CN.md](FEATURE_MATRIX.zh-CN.md)。**

## 固定基线

| 来源 | 固定提交 | 已核对关系与用途 |
|---|---|---|
| [原版 master][master] | `37bf9a56fe1f713258f298d7ef392b104ccae88f` | 262 次提交；MC 1.7.10；TC4 主体、研究、工具和修复后的行为基准 |
| [原版 origin/1.12][upstream112] | `e1b146ed6e651739af4b50af0e55ad917370f6ea` | master 之后 2 次提交；TC4 被关闭/删除，用于剥离通用 1.12 移植与 fork 独有变化 |
| [Technomancy-2 master][donor] | `223160a924e6f2c3c2170f2b9fa8b291a07cd31b` | 独立导入的 6 次提交；与原库没有共同 Git 祖先；资源与增量参考 |
| 原版 origin/CleanUp | `96b95f80c2938635a49791727903231771c5bcc9` | master 后 1 次提交；旧依赖/构建/集成保护可参考，不整体移入现代工程 |
| 原版 origin/Intial_Rework | `35f454fd6fa227acf5e414e5584340904a4c1a2f` | master 后 21 次提交，仍为 1.7.10；重构广且有回归，不能以日期更新作为优先依据 |
| 原版 origin/Affinities | `fc89f022b2540c9ae540064e19e119a9549bbfcb` | 已是 master 祖先，没有需要重复合并的独有内容 |

相邻本地参考库为 [Technomancy](../../Technomancy)、[Technomancy-2](../../Technomancy-2)。现代 TC4 的核对输入为相邻 Pollution 仓库中的 [20721 sources JAR](../../Pollution-Unofficial-1.20.1/local-repo/dev/tc4port/thaumcraft-forge/0.1.0-20721/thaumcraft-forge-0.1.0-20721-sources.jar)。只持有 sources JAR 不等于运行依赖已经装好；现代工程的实际依赖和运行配置以构建文件及迁移指导为准。API 核对使用 `dev.tc4port.thaumcraft.api.*`，不沿用旧 `thaumcraft.api.*` 包。

## Git 合并试验及其限度

试验是在独立 bare 仓库中通过 `merge-tree` 完成的；没有把冲突结果写进任何源库或本工程。证据可在相邻 [审计清单](../../research/technomancy-merge-audit-20260927/inventory.json) 与 [审计脚本](../../research/technomancy-merge-audit-20260927/audit.py) 复查。相邻审计目录是本地辅助材料，不是本工程构建依赖。

| 试验 | 实际结果 | 解释 |
|---|---|---|
| master + Technomancy-2，普通合并 | exit 128，`refusing to merge unrelated histories` | 内容接近不代表有共同 Git 祖先 |
| 同上，允许无关历史 | exit 1，156 个 add/add 冲突路径 | 153 个 Java + build.gradle + 1 个语言文件 + mcmod.info；解决冲突不会自动解决跨版本 API/行为语义 |
| master + 原版 origin/1.12 | exit 0，结果树与 origin/1.12 相同 | TC4 删除与关闭也会一并继承，不能称为恢复全部玩法 |
| CleanUp + 原版 origin/1.12 | exit 1，7 个冲突路径 | 旧工具链与依赖重构不是现代迁移捷径 |

因此不执行大范围“保留 ours/theirs”，也不把通过 Git 合并或补丁应用视为编译、客户端启动或玩法验收通过。

## 数量口径与真实增量

统计来自固定提交的 Git 对象；文本比较统一换行，资源另处理大小写，避免 Windows 文件系统影响结果。

| 统计范围 | 原版 master | Technomancy-2 |
|---|---:|---:|
| 全仓 Java | 349 | 218 |
| `src/main/java` 的 Java | 318 | 218 |
| 其中模组自有 `theflogat/technomancy` 包 | 301 | 218 |
| `src/api` 的 Java | 31 | 0 |
| `src/main/resources` 文件 | 143 | 233 |

两个 main/java 树共享 216 个路径：63 个在 LF 归一化后相同，153 个不同。原版独有 102 个，其中 17 个属于该目录内旧第三方 API；只计模组自有包时原版独有 85 个。fork 独有仅 `ItemColorHandler`、`FluidRenderUtils` 两个 Java 文件，没有独立新增的机器或仪式类。原版另有 40 个路径含 `/thaumcraft/` 的 Java，加 `lib/compat/Thaumcraft.java` 共 41 个 TC 核心文件，仍依赖目录外的共享机器、GUI、网络和渲染基础设施。

**应区别两个比较基准：master → fork 是整个 1.12 移植，origin/1.12 → fork 才是 fork 后续增量。** 后者的 218 个 Java 中 213 个相同、仅 5 个不同：

| 原版 origin/1.12 → fork 文件 | 有效变化 | 现代工程采用方式 |
|---|---|---|
| `Technomancy.java` | 配方注册从 postInit 提前到 init | 记录旧问题；现代版使用现代注册与数据配方，不照搬生命周期 |
| `TileManaExchanger.java` | 新增 FE capability 暴露 | 保留 FE 支持意图；原存储来自 CoFH，类型适配和返回值需重写，不声称旧实现已正确兼容 FE |
| `Botania.java` | 空桶 + 50,000 Mana → 1,000 mB 魔力桶配方 | 已采纳为 `technom:botania/mana_bucket`（`botania:mana_infusion`，空桶 + 50,000 Mana，带 `forge:mod_loaded` 门控）；平衡按 1.12 增量保留 |
| `BlockManaExchanger.java` | 删除已注释 getIcon | 无需迁移 |
| `CommonProxy.java` | 空白变化 | 无需迁移 |

提取的 [donor-java-changes.patch](../../research/technomancy-merge-audit-20260927/donor-java-changes.patch) 基线严格为 `e1b146ed6e651739af4b50af0e55ad917370f6ea`。独立 index 内已完成检查及应用，结果 Java 子树和 fork 同为 `b42648f72d0ca56ea993c26af9be9a39b945c098`。这个结果仅证明 5 文件增量的提取正确，不说明补丁可用于 1.7.10/1.20.1，也不含 TC4 恢复。

资源相对 master 的大小写归一化结果：

| 类别 | 数量与结论 |
|---|---|
| 原版独有归一化路径 | 0 |
| 仅路径大小写不同 | 71 对；其中 70 对字节相同，另一对为内容也变化的 `en_US.lang` → `en_us.lang` |
| fork 新增归一化路径 | 90：68 JSON（34 models、34 blockstates）、19 PNG、3 动画 mcmeta |
| 共同且原路径相同的内容修改 | 仅 `mcmod.info` |

中文语言文件不是相对 master 新增。不要将大小写重命名、恢复文件或编译输出当成新美术/新玩法。真正的 JSON、BM/BO 材质和魔力流体素材可作为移植候选，但 1.12 Forge blockstates、旧 lang 和引用名仍需转换为 1.20.1 格式；导入前保留逐文件来源。

## 已发现行为差异的取舍

本表比较 master 与 fork 的最终源码，不意味着每项都是 fork 独有提交。原版已有修复、共同旧缺陷和 1.12 回归分别列出，防止择新覆盖。

| 事项 | 已核对事实 | 迁移决定与验证 |
|---|---|---|
| 血液制造器满槽扣能 | master 使用 `amount + 200 <= capacity`；1.12 错成 `capacity + amount >= 200` | 以实际可接受产量决定消耗，满槽不扣能；检查模拟/执行/主动输出守恒 |
| 净化器自动化 | 原基础类允许槽 0 输入、槽 1 输出；1.12 BM/BO 子类以空 `getSlotsForFace` 覆盖，BO 插拔也返回 false | 重写 `IItemHandler` 保留正确输入输出语义；验所有面、漏斗/管道和满槽 |
| Existence 同步 | 原版有效客户端处理器在 1.12 被注释 | 重做登录、重生、换维度、变化同步；双客户端验结果 |
| 连接工具 | 原版初始化并注册 coilCoupler；1.12 只有字段、配方注释 | 与无线物品/精华完整恢复，别只迁机器 |
| 无序配方 | 1.12 把多个必需原料合进单个 `Ingredient.fromStacks` 候选集合 | 每个必需输入单独表示，全部配方采用唯一稳定 ID |
| 手册引用 | 1.12 返回未注册 recipe 对象，却另外注册不同对象；手册据 registry name 筛选 | 通过稳定 recipe ID 关联研究/手册，验证所有页面 |
| BM 成本与缓冲 | LP 公式 `multiplier*100+1000*reprocess` 改常数 16；血液制造器储能变 1,000,000 | 这是平衡变更，不能当兼容修复；统一 FE/EU 平衡表后单独决定 |
| 连接 NBT | `xcoordN/ycoordN/zcoordN` 被改成字面 `pos.getX()N` 等 | 新结构使用稳定 key 和版本，不承诺旧世界直读 |
| 玩家空值与随机下界 | 1.12 增加部分 null 保护和 `randVal <= 0` 保护，但未覆盖全部重载 | 保留防御目的，补齐测试边界；不要用临时空 NBT 掩盖数据归属问题 |
| Affinity 赋值 | 两版均把 `ordinal()` 写到参数而非 `this.id` | 明确修复；五属性独立键、颜色和查找均验证 |
| 融合核心节点状态 | 原版将节点状态放 Item 单例，放置后未清空 | 按 ItemStack 存储、服务端一次性事务；验证双物品隔离、失败恢复与防复制 |
| 精华储库返回量 | 原版 `takeEssentia` 的成功/失败数量语义需纠正 | 以现代“实际取出量”契约实现，模拟不可改变存量 |
| Rework 分支不是更可靠基线 | `RitualOfFireT2` 出现 z 坐标使用 yOffset 的回归 | 只按具体行为挑选改动，不整体合并分支 |

主要固定源码证据：

- 血液制造器：[master 条件][master-bloodfab] / [1.12 条件][donor-bloodfab]。
- 自动化：[原版基础槽规则][master-slots] / [BM 占位方法][donor-bm-slots] / [BO 占位方法][donor-bo-slots]。
- [1.12 同步处理器][donor-packet]、[1.12 无序配方][donor-recipes]、[1.12 recipe 对象返回][donor-module]。
- [原版连接工具初始化][master-items]、[玩家空值与随机保护][donor-player]、[原版融合核心][master-focus]。

下列原版成熟修复仍应保留，但不要重复宣称为 1.12 新贡献：`4016791` 将熔炼经验从 100 改为 1.0F；`3ad00e3` 加入默认关闭的 treasureSafeguard；`42d6175` 加入血液制造器主动排液。它们在 1.12 仍保留基本意图。`012b7d2` 和 `26a9e3d` 的精华线圈 Buffer/Arcane Bore 处理在原版 TC 实现中，应从修复后的 [TileEssentiaTransmitter][master-coil] 恢复，并适配现代接口。

精华炮、FireT3、精华重构器在原版也未有效注册；不把相关源码、配置字段或贴图计为默认功能已完成。存在类文件不是迁移范围的唯一依据。

## 许可证与归属边界

[原版 CurseForge 项目页](https://www.curseforge.com/minecraft/mc-mods/technomancy) 在本次核对时标明 **Apache License version 2.0**，页面的 Source 指向原版 GitHub 仓库。该网页是本次记录的原版许可来源；本地原版 checkout 未发现独立 LICENSE 文本。

Technomancy-2 固定提交的本地 checkout 也未发现独立 LICENSE 文本。内容相似、来自同一轮移植或公开可下载，均不在本记录中解释为可以给 fork 独有代码/美术重新授权。后续逐文件引入时保留作者、原有声明、来源提交和实际许可信息，分别处理原版内容、继承内容与 fork 独有贡献。

本工程新增代码/文档采用根目录许可证；官方 MDK 自带许可单独保留，其来源见 [MDK_PROVENANCE.json](MDK_PROVENANCE.json)。本工程根许可证不自动扩张到未导入的上游代码、第三方模组、素材或 TC4R/GTCEu 二进制。初始化不把旧库中的 CoFH、IC2、TC API 源码或依赖 JAR 打包进新模组。

## 当前可宣称的结果

已完成的是固定源码清点、注册范围核对、API 静态对照、Git 合并试验和 5 文件补丁提取验证。未通过这些研究证明旧工程可构建，也未证明任何现代机器、能量互联、研究或配方已工作。新工程构建状态应由实际构建日志单独报告；构建成功仍不等于游戏功能迁移完成。

[master]: https://github.com/Mordenkainen/Technomancy/tree/37bf9a56fe1f713258f298d7ef392b104ccae88f
[upstream112]: https://github.com/Mordenkainen/Technomancy/tree/e1b146ed6e651739af4b50af0e55ad917370f6ea
[donor]: https://github.com/alothmanmoe/Technomancy-2/tree/223160a924e6f2c3c2170f2b9fa8b291a07cd31b
[master-bloodfab]: https://github.com/Mordenkainen/Technomancy/blob/37bf9a56fe1f713258f298d7ef392b104ccae88f/src/main/java/theflogat/technomancy/common/tiles/bloodmagic/machines/TileBloodFabricator.java#L28
[donor-bloodfab]: https://github.com/alothmanmoe/Technomancy-2/blob/223160a924e6f2c3c2170f2b9fa8b291a07cd31b/src/main/java/theflogat/technomancy/common/tiles/bloodmagic/machines/TileBloodFabricator.java#L30
[master-slots]: https://github.com/Mordenkainen/Technomancy/blob/37bf9a56fe1f713258f298d7ef392b104ccae88f/src/main/java/theflogat/technomancy/common/tiles/base/TileProcessorBase.java#L238
[donor-bm-slots]: https://github.com/alothmanmoe/Technomancy-2/blob/223160a924e6f2c3c2170f2b9fa8b291a07cd31b/src/main/java/theflogat/technomancy/common/tiles/bloodmagic/machines/TileBMProcessor.java#L43
[donor-bo-slots]: https://github.com/alothmanmoe/Technomancy-2/blob/223160a924e6f2c3c2170f2b9fa8b291a07cd31b/src/main/java/theflogat/technomancy/common/tiles/botania/machines/TileBOProcessor.java#L100
[donor-packet]: https://github.com/alothmanmoe/Technomancy-2/blob/223160a924e6f2c3c2170f2b9fa8b291a07cd31b/src/main/java/theflogat/technomancy/network/PacketHandler.java#L34
[donor-recipes]: https://github.com/alothmanmoe/Technomancy-2/blob/223160a924e6f2c3c2170f2b9fa8b291a07cd31b/src/main/java/theflogat/technomancy/lib/handlers/CraftingHandler.java#L147
[donor-module]: https://github.com/alothmanmoe/Technomancy-2/blob/223160a924e6f2c3c2170f2b9fa8b291a07cd31b/src/main/java/theflogat/technomancy/lib/compat/ModuleBase.java#L88
[master-items]: https://github.com/Mordenkainen/Technomancy/blob/37bf9a56fe1f713258f298d7ef392b104ccae88f/src/main/java/theflogat/technomancy/common/items/base/TMItems.java#L33
[donor-player]: https://github.com/alothmanmoe/Technomancy-2/blob/223160a924e6f2c3c2170f2b9fa8b291a07cd31b/src/main/java/theflogat/technomancy/common/player/PlayerData.java#L82
[master-focus]: https://github.com/Mordenkainen/Technomancy/blob/37bf9a56fe1f713258f298d7ef392b104ccae88f/src/main/java/theflogat/technomancy/common/items/thaumcraft/ItemFusionFocus.java
[master-coil]: https://github.com/Mordenkainen/Technomancy/blob/37bf9a56fe1f713258f298d7ef392b104ccae88f/src/main/java/theflogat/technomancy/common/tiles/thaumcraft/machine/TileEssentiaTransmitter.java#L49
