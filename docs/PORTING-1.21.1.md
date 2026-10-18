# 1.21.1 移植审计与验收记录

状态：进行中；本文件中的“待验证”不代表通过。新建 1.21.1 世界为验收范围；未声明可升级 1.20.1 Forge 世界。

## 源码基线

|项目|源码|完整提交|
|---|---|---|
|MAE2|https://github.com/MineQuantumServer/MAE2|48538ec812580bd5abdbcda4c6e0d3ad79e0ff16|
|GT|https://github.com/FortyTwoCn/GregTech-Modern/tree/1.21|56986013d11de16b66a51805f9df8e3f1961cd69|
|AE2|https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/1.21.1|db17504a86128fdf3dae31f5fb7a112a646e0b93|

初始工作分支 work，工作树干净，无已有迁移。未找到适用 AGENTS.md。已读 README、TODO.org、构建配置、注册、资源、Mixin、配置和 GameTests。

## 工具链与前置

MC 1.21.1；Java 21；NeoForge 21.1.248；ModDevGradle 2.0.141；Gradle 8.14。版本来自指定 GT gradle/libs.versions.toml 和 Wrapper；满足 AE2 的 NeoForge >=21.1.169。
AE2 19.2.18 官方 tag 与分支提交一致；发布 JAR SHA-256 df15a07f86ca1ca93aa66373f9d5d84dd3d4586631fceb38f4fbc85dad591e39。
GT 8.0.0 必须从上述 fork 提交构建；已发布 clientfix JAR 对应其他提交，不能替代。GT 包含 Registrate、Configuration 和有 JEI 19.57 补丁的 ModularUI；不再依赖 LDLib。ModularUI 需按 GT scripts/build_modularui.py 准备。JEI、EMI 是可选开发工具，不作为必装前置。

## 现有功能清单

|原功能 / 注册 ID|目标实现|验证方式|状态|
|---|---|---|---|
|pattern_p2p_tunnel|官方 ICraftingMachine 与 ME_STORAGE，单目标完整投递、输出端待发送队列、回收|整块/部件供应器、装配室/GT、阻挡、重载、部分接收|待迁移/验证|
|eu_p2p_tunnel|指定 GT BlockCapability IEnergyContainer，保留电压与安培|方向、过压、每 tick 限流、部分接收、税费|待迁移/验证|
|item_multi_p2p_tunnel|NeoForge 物品能力、多输入输出|模拟/实际、满载、断连、守恒|待迁移/验证|
|fluid_multi_p2p_tunnel|NeoForge 流体能力、多输入输出|模拟/实际、部分接收、守恒|待迁移/验证|
|fe_multi_p2p_tunnel|NeoForge FE、多输入输出和缓冲|溢出、模拟、税费、保存|待迁移/验证|
|eu_multi_p2p_tunnel|指定 GT EU、多输入输出和缓冲|溢出、方向、税费、保存、电压不被残余缓冲降级|待迁移/验证|
|redstone_multi_p2p_tunnel|多输入红石聚合|4 个原有故障生命周期测试|待迁移/验证|
|pattern_multi_p2p_tunnel|多输入样板、单输出完整投递|模式切换、频率、网络拆分合并、回收与保存|待迁移/验证|
|faulty_card|模式数据组件、官方 AE2 设置组件、AoE/全网/递增递减/Über|持久化与网络往返、范围上限、只访问已加载区块|待迁移/验证|
|4x/16x/64x/256x_crafting_accelerator|仅替换官方 CPU 的单块限制检查，保留实际累加值|每档、混合、原版协处理器、拆装、重载与调度|待迁移/验证|
|cloud_chamber|方块、玻璃模型、客户端粒子、配方与配置|客户端渲染、服务端隔离、配置、掉落|待迁移/验证|
|配套|mae2 mod ID 和所有注册 ID 不变；频率、调谐、颜色、指南、标签、语言和资源|注册清单/最终 JAR 检查、数据生成、真实运行|待迁移/验证|

TODO.org 中 Pattern Multitool、GregTech Patterns、Sticky Card、Beam Former、Interface Capacity Card 等没有已注册实现，不属于本次新增范围。Faulty Card 虽仍标 TODO，已有完整实现，纳入迁移。
原 Java GameTests 有 11 个注解：样板 5，红石 4，原版供应器 2；不能将编译或任务 NO-SOURCE 记成运行成功。旧结构还含物品 NBT 格式，需转换后实际验证。

## 官方 AE2 / Cosmolite 差异

|旧依赖|官方 19.2.18|处理要求|
|---|---|---|
|BlockingMode、getBlockingMode、containsPatternInput(inputs,mode)|不存在；官方仅提供 isBlocking 和 containsPatternInput(inputs)|保留官方阻挡原料语义；Cosmolite ALL 等扩展单独列差异，不能假装已支持|
|PatternProviderTargetCache 5 参数 ConfigManager 构造器|不存在；4 参数类/构造器/find 为包可见|使用公开 PatternProviderTarget.get，消除对私有缓存的 AT|
|ICraftingMachine.of(level,pos,side,be)|仅 3 参数和 BlockEntity+side|改用公开入口|
|readFromNBT/writeToNBT(CompoundTag)|增加 HolderLookup.Provider|所有部件、队列、服务保存传注册表|
|IMemoryCard.getData/setMemoryCardContents/getColorCode/NBT_COLOR_CODE|仅剩 notifyUser；设置改为 AEComponents|使用原生导出设置组件与 MEMORY_CARD_COLORS|
|PowerUnits|改名 PowerUnit|统一更新|
|能力 getCapability/LazyOptional|RegisterPartCapabilitiesEvent 与 BlockCapability|显式注册每个具体 part；缓存失效与生命周期验证|
|CPU getAcceleratorThreads 调用|依次为 >0、<=16、实际累加（3 次）|只改第二次检查；第三次保留 4/16/64/256|
|ModelBakery.loadModel Mixin|需核对新模型加载入口|优先 NeoForge 模型事件|

## 初步发现的原实现缺陷

- pushPattern 使用静态阻挡上下文，TAIL 不覆盖提前返回/异常/嵌套；需 try/finally 隔离。
- EU 普通隧道安培累加用 int；普通税费漏乘电压；Multi 电压*电流可溢出，分配后的满意判断混合安培与 EU 单位；maxVoltage 重置使残余能量降压。
- Multi FE buffer/extracted 累加无溢出限制。
- 流体 fill 余数用 amount % amountPerOutput；drain 每个输入重复请求原数量；drain(int) 未返回第一次抽取的物料。
- Über 体积未包含两端方块且用 int 乘法；平面/直线可绕过上限；扫描未排除未加载区块。
- 原样板 GameTest 能力不存在时 ifPresent 内断言不执行；红石失电断言却期待亮灯，存在误报。
- Multi 服务空隧道未回收、tick queue 清空后 peek 可空、随机频率生成无界。

## 验证结果

构建：未运行目标构建。GameTests：11 个源码候选，运行 0；未发现/执行。专用服务器：未验证。客户端：未验证。GT 自动合成：未验证。

## Java 源码清单

- `src/main/java/stone/mae2/MAE2.java`
- `src/main/java/stone/mae2/api/Tickable.java`
- `src/main/java/stone/mae2/api/client/trails/BackgroundTrail.java`
- `src/main/java/stone/mae2/api/client/trails/CloudChamberUtil.java`
- `src/main/java/stone/mae2/api/client/trails/SimpleTrail.java`
- `src/main/java/stone/mae2/api/client/trails/Trail.java`
- `src/main/java/stone/mae2/api/client/trails/TrailForming.java`
- `src/main/java/stone/mae2/api/features/MultiP2PTunnelAttunement.java`
- `src/main/java/stone/mae2/block/CloudChamberBlock.java`
- `src/main/java/stone/mae2/block/crafting/DynamicCraftingBlockItem.java`
- `src/main/java/stone/mae2/block/crafting/DynamicCraftingUnitType.java`
- `src/main/java/stone/mae2/bootstrap/ClientProxy.java`
- `src/main/java/stone/mae2/bootstrap/MAE2BlockEntities.java`
- `src/main/java/stone/mae2/bootstrap/MAE2Blocks.java`
- `src/main/java/stone/mae2/bootstrap/MAE2Config.java`
- `src/main/java/stone/mae2/bootstrap/MAE2Items.java`
- `src/main/java/stone/mae2/bootstrap/MAE2Tags.java`
- `src/main/java/stone/mae2/bootstrap/Proxy.java`
- `src/main/java/stone/mae2/bootstrap/ServerProxy.java`
- `src/main/java/stone/mae2/client/render/crafting/DynamicCraftingCubeModelProvider.java`
- `src/main/java/stone/mae2/client/render/model/FaultyCardBakedModel.java`
- `src/main/java/stone/mae2/client/render/model/FaultyCardModel.java`
- `src/main/java/stone/mae2/core/datagen/MAE2BlockTagsProvider.java`
- `src/main/java/stone/mae2/core/datagen/MAE2RecipeProvider.java`
- `src/main/java/stone/mae2/gametests/PatternP2P.java`
- `src/main/java/stone/mae2/gametests/RedstoneP2P.java`
- `src/main/java/stone/mae2/gametests/Vanilla.java`
- `src/main/java/stone/mae2/hooks/BuiltInModelHooks.java`
- `src/main/java/stone/mae2/integration/GregTechIntegration.java`
- `src/main/java/stone/mae2/integration/MultiP2PStateDataProvider.java`
- `src/main/java/stone/mae2/item/faulty/AoEPaste.java`
- `src/main/java/stone/mae2/item/faulty/FaultyCardMode.java`
- `src/main/java/stone/mae2/item/faulty/FaultyMemoryCardItem.java`
- `src/main/java/stone/mae2/item/faulty/GlobalPaste.java`
- `src/main/java/stone/mae2/item/faulty/Incrementing.java`
- `src/main/java/stone/mae2/item/faulty/UberMode.java`
- `src/main/java/stone/mae2/me/service/MultiP2PService.java`
- `src/main/java/stone/mae2/mixins/BuiltInModelHooks.java`
- `src/main/java/stone/mae2/mixins/CraftingCPUClusterMixin.java`
- `src/main/java/stone/mae2/mixins/MAE2MixinPlugin.java`
- `src/main/java/stone/mae2/mixins/ModelBakeryMixin.java`
- `src/main/java/stone/mae2/mixins/PatternProviderLogicCLMixin.java`
- `src/main/java/stone/mae2/mixins/PatternProviderLogicMixin.java`
- `src/main/java/stone/mae2/parts/p2p/EUP2PTunnelPart.java`
- `src/main/java/stone/mae2/parts/p2p/PatternP2PPartLogic.java`
- `src/main/java/stone/mae2/parts/p2p/PatternP2PTunnelLogic.java`
- `src/main/java/stone/mae2/parts/p2p/PatternP2PTunnelPart.java`
- `src/main/java/stone/mae2/parts/p2p/multi/CapabilityMultiP2PTunnel.java`
- `src/main/java/stone/mae2/parts/p2p/multi/EUMultiP2PTunnel.java`
- `src/main/java/stone/mae2/parts/p2p/multi/FEMultiP2PTunnel.java`
- `src/main/java/stone/mae2/parts/p2p/multi/FluidMultiP2PTunnel.java`
- `src/main/java/stone/mae2/parts/p2p/multi/ItemMultiP2PTunnel.java`
- `src/main/java/stone/mae2/parts/p2p/multi/MultiP2PTunnel.java`
- `src/main/java/stone/mae2/parts/p2p/multi/PatternMultiP2PTunnel.java`
- `src/main/java/stone/mae2/parts/p2p/multi/RedstoneMultiP2PTunnel.java`
- `src/main/java/stone/mae2/util/LoadedModsHelper.java`
- `src/main/java/stone/mae2/util/TransHelper.java`
