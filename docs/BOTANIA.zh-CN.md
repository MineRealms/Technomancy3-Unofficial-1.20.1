# Botania 模块：上游精确规格与对齐清单

核对日期：2026-09-28。来源：原版 `37bf9a5` 的 `common/tiles/botania/**`、`lib/compat/Botania.java`、`lib/handlers/Rate.java`。目标：本工程的 Botania 模块必须与上游**严格对应**（贴图、模型、物品、代码逻辑、数值、功能）。当前实现是近似值，本文件记录差异，逐条改为上游值。

## 数值来源

| 常量 | 上游 | 位置 |
|---|---|---|
| `manaFabCost` | `1000000` | `Rate.manaFabCost` |
| `exchangerCost` | `1000` | `Rate.exchangerCost` |

## 1. Flower/Hippie Dynamo（`TileFlowerDynamo`）

上游：
- 继承 `TileDynamoBase`，**实现 `IManaReceiver`**，自身 `mana` 缓冲，`maxMana = 100000`。
- `extractFuel(int ener)`：`ratio = ener/80F`，`val = ceil(20*ratio)`；`val > mana` 返回 0，否则 `mana -= val` 并返回 **160**。
- `updateEntity` 后若 `mana <= maxMana - 100` 则 `drainMana`。
- `drainMana`：遍历 `x,z ∈ [-4,4]`、**同一 Y**，对每个 `IManaPool`，若 `pool.getCurrentMana() >= 100` 且自身未满，`pool.recieveMana(-100)`、`mana += 100`（**不 break，可对多个池各抽 100**）。
- `canRecieveManaFromBursts() = true`。

本工程现状（需改）：缓冲 40,000；只对相邻一个 `ManaPool` 抽 `MANA_PER_TICK=1`；`Q_PER_MANA=80`。**偏差**：缓冲、抽取范围/数量、燃料换算、`IManaReceiver` 能力。

## 2. Mana Fabricator（`TileManaFabricator`）

上游：
- 继承 `TileMachineBase`，**实现 `IManaPool, IWrenchable`**，即自身就是魔力池。
- `maxMana = 100000`；`cost = Rate.manaFabCost = 1000000`；能量容量 `cost*2`。
- `updateEntity`：若 `energy >= cost && mana+100 <= maxMana` → `mana += 100`、`extractEnergy(cost)`。
- `canConnectEnergy(from) = (from.ordinal() == facing)`：**只有一个面进电**，扳手旋转该面。
- `canRecieveManaFromBursts() = false`；`isOutputtingPower() = false`。
- `facing` 存 NBT；`onWrenched` 找到第一个相邻能量方块后转向它。

本工程现状（需改）：缓冲 1,000,000；六面输入；100 Q/mana；主动推送给相邻接收者。**偏差**：不是池、数值、面、扳手。

## 3. Mana Exchanger（`TileManaExchanger`）

上游：
- 继承 `TileTechnomancyRedstone`（`RedstoneSet.LOW`），**实现 `IFluidHandler, IEnergyReceiver, IWrenchable`**。
- **魔力池必须在其正上方**（`y+1` 是 `TilePool`，否则 `active=false`）。
- `FluidTank(1000)`（魔力流体）；`EnergyStorage(Rate.exchangerCost*10 = 10000)`。
- `updateEntity`（`set.canRun` 门控）：池在正上方且 `storage >= Rate.exchangerCost(1000)` 时：
  - `mode==true`：罐里有流体且 `pool.getCurrentMana() <= pool.manaCap-1000` → `pool.recieveMana(1000)`、`tank.drain(1)`、`extractEnergy(1000)`。
  - `mode==false`：罐未满且 `pool.getCurrentMana() >= 1000` → `pool.recieveMana(-1000)`、`tank.fill(manaFluid, 1)`、`extractEnergy(1000)`。
- 流体接口：`fill` 仅在 `!mode` 且 `from != UP`；`drain` 仅在 `mode` 且 `from != UP`；`canFill = from!=UP && mode`；`canDrain = from!=UP && !mode`。
- 能量：`canConnectEnergy(from) = from != UP`。
- 扳手切换 `mode`。

本工程现状（需改）：FE↔相邻池，80 Q/mana，无流体罐、无 mode、无扳手、无“池在正上方”约束。**偏差**：整块功能（流体与模式）缺失。

## 4. BO Processor（`TileBOProcessor`）

上游：
- 继承 `TileProcessorBase`，**实现 `IManaReceiver`**；`maxMana = 1000000`；自身 `mana` 缓冲。
- `getFuel(items, multiplier, reprocess)`：`cost = multiplier*150 + 1500*reprocess`；`cost > mana` 返回 false，否则 `mana -= cost`（**按一次加工结算**，非按 tick）。
- `perform()`：若未满，遍历 `x,z ∈ [-4,4]`、同一 Y，对每个 `IManaPool` 吸 `min(pool.getCurrentMana(), min(maxMana-mana, 5000))`。
- `canRecieveManaFromBursts() = true`。

本工程现状（需改）：无自身缓冲，`payTick` 从相邻池按 `cost*MANA_PER_COST(100)` 扣；继承自本工程的 `ProcessorBlockEntity`（60 tick 按 tick 计费）。**偏差**：缓冲、抽取范围/速率、按次 vs 按 tick 的架构差异。

## 5. 材料、配方与 Lexicon

上游 `lib/compat/Botania.java`：
- 机器配方走 **Botania 魔力灌注 / 普通合成**（`manaCoilRec`、`manaGear`、`flowerDynamo`、`manaFabricator`、`processorBO`、`manaExchanger`）。
- 材料：`mana coil`、`manasteel gear`。
- **Lexicon（Botania 手册）页**：`TechnoLexicon` 为每个机器/材料加页（`initBotaniaLexicon`），引用 `tc.research_name.*` 与 `techno.lexicon_page.*`。
- 魔力流体 `ManaFluid` + 桶（`ItemManaBucket`），`50,000 Mana 灌注 → 1,000 mB 魔力桶`（`1.12` 增量，见源码基线）。

本工程现状：已加 `mana_coil`/`manasteel_gear` 物品；其余（灌注配方、Lexicon 页、魔力流体+桶）**未做**。

## 6. 贴图与模型

上游用代码模型（`ModelFlowerDynamo` 等）与 `blocks/flowerDynamo.png` 等贴图；本工程当前用整块 cube JSON。**需改为与上游等价的模型与贴图**：`block/flowerdynamo.png`、`block/manafabricator.png`、`block/manaexchanger*.png`、`item/manacoil.png`、`item/managear.png`（已在 `textures/` 迁移）。

## 对齐顺序

1. FlowerDynamo：`IManaReceiver` + 100,000 缓冲 + 9×9 每池 100 + `extractFuel` 换算。
2. ManaFabricator：`IManaPool` + 100,000 + 1,000,000 FE/100 mana + 单面 + 扳手。
3. BOProcessor：自身 1,000,000 缓冲 + 9×9 每 tick 吸 5000 + `multiplier*150 + 1500*reprocess`（需与处理器架构一起定夺）。
4. ManaExchanger：正上方池 + 魔力流体罐 + mode/扳手 + 1000 mana↔1 mB + 1000 FE/次。
5. 配方（灌注/合成）、Lexicon 页、魔力流体与桶。
6. 模型与贴图对齐。
