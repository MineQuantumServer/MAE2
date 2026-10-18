# MAE2 1.21.1 NeoForge 移植与验收记录

验收对象为新建 Minecraft 1.21.1 世界，以及该版本内的保存/读取。全部原有注册功能已移植；下表分别说明代码完成情况和已执行验证，未覆盖的压力场景单独列出。最终提交、JAR 校验值见交付目录 `release.json`、`SHA256SUMS`。

## 1. 基线与依赖来源

|项目|来源|完整 commit SHA|
|---|---|---|
|MAE2 原始基线|https://github.com/MineQuantumServer/MAE2|48538ec812580bd5abdbcda4c6e0d3ad79e0ff16|
|指定 GT fork，1.21 分支|https://github.com/FortyTwoCn/GregTech-Modern|56986013d11de16b66a51805f9df8e3f1961cd69|
|官方 AE2，1.21.1 分支|https://github.com/AppliedEnergistics/Applied-Energistics-2|db17504a86128fdf3dae31f5fb7a112a646e0b93|
|GT 所需 ModularUI|https://github.com/brachy84/ModularUI-Modern|8ecb104d0c38b1eb9baab5c837624034db7ca873|

开始时处于 `work` 分支、工作树干净，无已有迁移；未找到适用 AGENTS.md。已读取 README、TODO.org、构建配置、注册、配置、全部 Java 源码和资源。工作分支为 `dot/mae2-1.21.1-neoforge`。未向远程推送。

|组件|锁定版本|来源/安装方式|
|---|---|---|
|Minecraft|严格 1.21.1|Mojang；元数据范围 `[1.21.1]`|
|Java|21；实测 Temurin 21.0.12.1+1-LTS，Linux x86_64|Eclipse Adoptium JDK，含 javac|
|NeoForge|21.1.248|NeoForge 官方 Maven/installer|
|Gradle|8.14|仓库 Wrapper，分发包 SHA-256 已锁定|
|ModDevGradle|2.0.141|NeoForge Gradle 插件|
|GT|8.0.0|仅上述 FortyTwoCn 提交源码构建|
|AE2|19.2.18|官方 `neoforge/v19.2.18` tag 与上述分支提交相同；官方发布 JAR|
|GuideME|21.1.1|Maven Central，AE2 必需独立运行库|
|ModularUI|3.3.1-jei19.57-preview1|上述源码 + GT 自带 `patches/modularui/jei-19.57-preview.diff`；内嵌于 GT|
|Registrate|MC1.21-1.3.0+67|GT JarJar 内嵌|
|Configuration|3.1.1-neoforge|GT JarJar 内嵌|
|EvalEx|3.6.0|ModularUI JarJar 内嵌|

NeoForge、ModDevGradle、Gradle 选择依据是指定 GT 的实际版本目录和 Wrapper，同时满足 AE2 最低 NeoForge 要求。JEI、EMI、Jade 等便利模组没有加入验收运行组合。不要另外安装 GT 内嵌库的不同版本。

重要校验值：

```text
GT b72df7b2b9dc2796ea901030be6ed705c7669a491a13a6e639421ab21044b04b
AE2 df15a07f86ca1ca93aa66373f9d5d84dd3d4586631fceb38f4fbc85dad591e39
GuideME 62229015025b7c0a741590b626b6719631f6b8a945c483ece12e7035d4fd903d
ModularUI patch e9f73750d30f277faa9f8774b5e7a6092a9ae6a6428ac9df1efc4002af63a5e2
```

完整来源、内嵌库哈希及补丁结果哈希在 `dependencies.lock.json`。已发布的 GT clientfix 产物对应其他提交，未拿相同版本号代替指定 fork。准备脚本从明确提交检出、检查补丁和源码状态，在 `work/dependencies/m2` 隔离发布 ModularUI，再构建 GT；不依赖用户已有 Maven local。构建附加的 init script 仅控制可复现 ZIP、来源 manifest 和依赖强制选择，不修改 GT 源码。两次独立源码检出与隔离 Maven 构建产生相同 GT SHA-256。

GT 上游声明的 AE2 19.2.8 编译依赖在构建中明确强制为官方 19.2.18；dependencyInsight 记录了 `19.2.8 -> 19.2.18 (forced)`，下载的 Maven JAR 与官方发布 JAR 哈希一致。MAE2 编译和运行直接引用校验过的三个本地文件；排除 AE2/GT 的 Maven 传递替代。`dependencyEvidence` 输出两个 classpath 的每个实际文件及哈希，运行中的 GameTest 再按 ModList 实际文件验证 AE2、GT 的哈希和 GT Source-Commit。

## 2. 功能清单与验收

mod ID `mae2`、14 个物品 ID、5 个方块 ID 和 `dense_accelerator` 方块实体 ID 均保留。新增 `mae2:faulty_data` 数据组件；未通过删除注册或返回空实现规避兼容问题。

|原有功能 / 注册 ID（均为 mae2 命名空间）|目标实现|已执行验证|状态|
|---|---|---|---|
|普通样板 `pattern_p2p_tunnel`|公开 ICraftingMachine/ME_STORAGE；每份样板选一个输出，部分接收后的 sendList 留在该输出；原料投递与产物回收|整块和部件供应器 → 分子装配室；整块和部件 → 真实 GT 电炉；满载/部分接收；阻挡策略；队列保存和重建|实现完成、上述用例通过|
|普通 EU `eu_p2p_tunnel`|指定 GT IEnergyContainer；电压和返回安培分开、方向检查、实际接收量计费|真实 GT LV 电炉，反面拒绝、部分安培、每 tick 接收限制恢复；128V 对 32V 机器的真实过压爆炸|实现完成、通过|
|物品 Multi `item_multi_p2p_tunnel`|NeoForge IItemHandler、多输入/输出；余数按输出数分配|满载桶、模拟不修改、4/8 部分接收和剩余返回|实现完成、通过|
|流体 Multi `fluid_multi_p2p_tunnel`|NeoForge IFluidHandler；有类型/无类型抽取只扣实际量|两个真实 GT 木桶合计 2000mB，模拟取1500不扣，实际1500后剩500，无类型取回剩余500|实现完成、通过|
|FE Multi `fe_multi_p2p_tunnel`|共享缓冲、被动提取和主动分发、AE2 能耗；精确内部记账|模拟/实际、500→抽125→剩375；重复保存不扣；频率拆合和真实 AE2 网络拆合守恒；旧能力失效；未结算费用保存和区块脏标记|实现完成、通过|
|EU Multi `eu_multi_p2p_tunnel`|GT 能量包接口、共享缓冲、原有最高电压平衡；内部精确记账|真实 GT 加工设备接收、方向、过压；Long.MAX_VALUE 包、满缓冲拒绝；两个最大缓冲合并与重载；安装版重启保持1001EU/128V|实现完成、通过|
|红石 Multi `redstone_multi_p2p_tunnel`|保留多输入合并和网络状态通知|原有4项：失电、控制器冲突、断线、移除红石源均熄灯|实现完成、通过|
|样板 Multi `pattern_multi_p2p_tunnel`|多供应器输入，完整样板在单输出处理；配方刷新与输出回收|整块/部件供应器、分子装配室、GT加工闭环；输出部分接收后的队列重建；真实服务器保存/恢复/只投递一次|实现完成、通过|
|Faulty Memory Card `faulty_card`|MAE2 模式数据组件 + AE2 导出设置组件、原生颜色组件|组件磁盘/网络往返；FakePlayer 原生复制，AoE/全网/Über 粘贴真实部件；优先级递减/递增；客户端放置、复制、绑定与颜色|实现完成、通过|
|`4x/16x/64x/256x_crafting_accelerator`|只绕过官方 CPU 对单块 <=16 的限制检查，实际线程累加保留原值|单块4/16/64/256；混合加原版=341；拆原版=340，恢复341；保存重建256；真实256档CPU完成自动合成|实现完成、通过|
|`cloud_chamber`|原注册、玻璃模型、配方、客户端粒子和倍率配置|客户端玻璃、物品纹理、粒子目视；资源/配方入包；专用服务器不加载渲染类|实现完成、通过|
|配套|调谐标签、绑定、颜色、名称、配置、模型、贴图、语言、掉落、指南保留|数据生成；14物品模型、13配方、5掉落表、9测试结构、4指南；真实客户端打开 MAE2 指南3D场景|实现完成、通过|

TODO.org 的 Pattern Multitool、GregTech Patterns、Sticky Card、Beam Former、Interface Capacity Card 等仅为未来计划，无已注册实现，不作为新增功能。Faulty Card 虽仍被旧 TODO 标记，但实际已有实现，因此纳入移植。

## 3. Forge、官方 AE2 与 Cosmolite 的适配

|原依赖/机制|官方目标 API/行为|本次处理|
|---|---|---|
|Forge 事件、DeferredRegister、ForgeConfigSpec、LazyOptional|NeoForge 事件/注册/ModConfigSpec/BlockCapability|NeoForge 模组入口及 ModContainer；显式注册每种具体 AE2 part 的 capability；不借用旧泛型基类注册|
|PatternProviderTargetCache 五参数 ConfigManager 构造器|官方缓存类/四参数构造器/find 包可见|使用自己的窄包装，调用公开存储与目标策略；删除旧 Access Transformer|
|ICraftingMachine.of(level,pos,side,be)|公开三参数版本|统一改为公开入口，兼容供应器方块与部件|
|BlockingMode/getBlockingMode/containsPatternInput(inputs,mode)|官方只有原生 isBlocking/单参数 containsPatternInput|恢复 ALL/SMART/DEFAULT 自定义 Setting，存于供应器原生 ConfigManager；排除 GT programmed_circuit；命令选择额外策略|
|旧静态 pushPattern 阻挡状态及 TAIL 清理|提前返回/异常不保证 TAIL 执行|WrapMethod + try/finally + ThreadLocal 栈；嵌套返回后还原上层策略，最外层结束移除线程状态|
|旧 CPU 全部 getAcceleratorThreads 重定向|官方调用3次：>0、<=16、实际累加|明确方法描述符，只重定向 ordinal=1；仅 MAE2 动态单元在该次检查返回1；原版协处理器与实际累加均保留|
|ModelBakery.loadModel 与 AE2 内置模型注册|官方 AE2 钩子仅接受 ae2 命名空间|客户端 getModel 定向 Mixin，只对 MAE2 已登记模型返回自定义模型；客户端列表隔离，不注入专用服务器|
|IMemoryCard 自定义 NBT 读写/颜色接口|AEComponents、DataComponentMap、MemoryCardColors|不可变副本更新模式数据；持久 Codec 和网络 StreamCodec；频率、类型、优先级用 AE2 原生组件|
|旧 NBT 持久化方法|增加 HolderLookup.Provider；RegistryFriendlyByteBuf|全量更新部件/发送队列/网络服务保存和网络同步|
|PowerUnits / 旧 GT capability 包|PowerUnit / 指定 GT 的 GTCapability|更新类型与接口；GT 方法返回值按安培处理|

Cosmolite 的额外阻挡策略用法：先在官方供应器 GUI 开启阻挡，再对准该供应器（方块或部件）执行 `/mae2 blocking default`、`all` 或 `smart`。DEFAULT 检查原料，ALL 检查任何非电路物料，SMART 检查非配方原料；与原 fork 代码语义一致。官方 AE2 没有 fork 的额外按钮，因此选择入口改为命令；设置仍通过供应器自身保存、记忆卡导出/导入。命令检查玩家交互权限。

每个 Mixin 默认 require=1；Java21；客户端模型 Mixin 独立于公共 Mixin。运行时已实际应用，无 MAE2 注入失败。GT 的可选 JEI/EMI 目标缺失警告来自锁定的上游 GT，见限制说明。

能力使用 AE2 PartAdjacentApi 查询、NeoForge invalidateCapabilities 通知。频率/输入输出身份变化和节点移除清掉旧逻辑；已持有的 FE、EU、物品、流体能力通过有效性检查停止调用旧端点。相邻 API 有深度守卫和 try/finally，防止隧道环无限递归；相邻设备失效向网络端点转发能力失效通知。

## 4. 已修复的原实现缺陷与行为差异

- 普通 EU 旧计费漏乘电压，现按实际安培 × 电压 × 原 GT FE 比率计费。原有 nerf 税率公式保留；部分支付按费用/单价扣减欠费，修复旧单位混用。
- Multi EU 的电压×电流接受量先限界；不接受零/负电压、电流；接收报告限于发送值。残余缓冲未耗尽前保留最高电压，避免重载或空 tick 降压。两个合法缓冲合并可超过 long，内部用精确整数累加，capability 数值仍限于接口范围；常规保存仍用 long，超范围份额额外用 `energyExact` 十进制字段恢复。
- EU/FE 主动分发先预留发送量，再按实际接收退款；接收回调中的GT爆炸、端点删除/重绑不会再次扣减已迁移的缓冲。端点迭代使用快照，退款寻找仍存活的网络。若所有端点被爆炸摧毁，缓冲按设备被摧毁处理。
- FE 模拟操作不改变缓冲；保存快照不再消耗缓冲；每个端点保存精确、确定的份额，重绑和网络移动只迁移自己的份额。输出提取产生的待付费用以 `pendingTax` 保存。共享状态变化按 tick 合并标记所有相关端点区块待保存。
- 物品/流体分配余数修正；多输入抽流体使用剩余请求量，无类型抽取先模拟识别，不再丢弃第一次真实抽取。修复重复防递归守卫导致第一个有效输入被跳过的问题。
- 样板一次只选择一个输出，部分投递后剩余物料在该输出排队，不能把一份样板拆到别的加工设备。队列改变标记保存，read 前清空，避免重复加载叠加。官方存储 capability 转发恢复加工设备的产物回收。
- AoE 半径限制1..7；Über 体积包含两个角，乘法防溢出，平面和直线也受限制；只访问已加载区块，不为了粘贴强加载。原配置上限32768方块保留。递增/递减达到数值边界时饱和，防止翻转。
- 空频率网络与其 tick 任务回收，tick queue 为空时不再解引用 peek；频率分配最多扫描65535个可用非零频率，耗尽时明确失败。
- EU 注册项始终存在；`Parts.EU P2P.enabled=false` 禁止能源能力，避免不同配置删除注册 ID。验收使用默认启用配置。本次目标配置要求 GT 为必需前置，已在元数据声明。
- 官方 AE2 移除了旧加速器拆解开关，保留潜行使用物品拆解功能；物料仅在服务端修改。
- 1.21.1 资源目录改为 recipe/advancement/loot_table/structure/tags/item|block；配方结果 item→id；矿物工具标签用 c 命名空间。生成资源确实打包，排除 datagen .cache。
- 旧测试结构中的物品、样板、AEKey 和 DataVersion 改为目标格式。旧部件供应器装配室结构原本是1.20.1执行中快照，现重建为1.21.1初始状态；这只迁移测试夹具，不宣称整包旧存档兼容。

## 5. 实际执行结果与证据

|类别|结果|证据（交付包 evidence/）|
|---|---|---|
|干净构建|Wrapper `clean build dependencyEvidence` 成功；JUnit 5项，通过5、失败0、跳过0|mae2-final-clean-build.log、junit/|
|集成 GameTests|发现42、执行42、通过42、失败0、跳过0；均 required|gametest-final.log、gametests.json|
|原测试|原11项全部迁移执行：样板5、红石4、官方原版供应器2|同上，源码原测试类|
|ModularUI|13单元测试通过，GT所需补丁版本实际源码构建|dependencies-clean-rebuild.log、modularui-tests/|
|GT 来源/可复现|指定源码两次独立构建，GT hash完全相同；Mixin 配置验证成功|dependencies-clean-rebuild.log、gt-ae2-dependency.txt|
|依赖解析|编译/运行实际文件哈希一致；运行中的 AE2/GT 实物校验通过|dependency-evidence.json、dependency lock、GameTest|
|数据生成|Recipes、block tags 成功，26生成文件|datagen.log|
|最终 JAR|元数据、全部源码资源、3个Mixin、Java65字节码、14物品模型、13配方、5掉落表、9结构、4指南检查通过|jar-audit.json|
|专用服务器|标准 NeoForge installer 安装，mods仅4个JAR；加载世界、正常停止；再次启动读回缓冲/队列/频率|production-install.log、production-server-1.log、production-server-final.log|
|真实客户端|Xvfb + Mesa 软件OpenGL进入专用服务器；部件放置、记忆卡复制/绑定、频率颜色、4档已成型CPU、云室粒子、MAE2指南3D场景|client-4.log、packaged-client.log、screenshots/|
|GT 自动合成|普通/Multi × 整块/部件，共4种；提交任务→投料→GT电炉加工→自动输出回网络→任务结束，CPU不busy|GtCraftingRegression、GameTest|
|生命周期|实际区块离开后查询报告“position is not loaded”，回来恢复；正常服务器重启；GameTest真实网络分裂/合并、重绑、节点重建|server-1.log、production-server-1.log、GameTest|
|规模|1000次频率重绑，旧网络和tick任务已回收，剩2网络、2输入、1输出；约10ms/本机单次样本|GameTest日志；不是通用性能保证|

GameTests 原有测试已修复误报：能力为 null 必须失败，不能 ifPresent 跳过断言；红石失电应断言熄灭。`scripts/run_gametests.py` 同时核对源码测试数量和实际完成/通过日志，避免仅凭 Gradle 成功或 NO-SOURCE 当作测试通过。NeoForge 生产模式明确禁止 GameTests；集成测试在 Wrapper 的开发测试服务端执行，使用真正的目标前置 JAR。安装版专服启动与客户端验证另行执行，没有把生产模式的“No test functions”算通过。

真实保存恢复数据：FE=12345，EU=1001/128V，样板 sendList=7钻石，绑定频率23653。正常停服后在标准安装版再次读取值一致；恢复目标桶后得到恰好7钻石且sendList清空。最终再次启动检查已交付物料未重复。待发送队列还有单独 GameTest 验证8份原料、只接4份、重建后再接剩4份。

## 6. 限制与未验证项

- 不支持声明将1.20.1 Forge整包世界直接升级；没有旧存档全局 DataFixer。仅目标版本内往返验证通过。
- 客户端使用 Linux Xvfb/Mesa 实际运行和交互；未在 Windows/macOS、独显驱动或其他整合包上验收。容器无音频设备，声音输出未验证。
- 未做数小时/数天稳定性、上千**同时存活**输出、跨维度大型网络或复杂GT多方块流水线压力测试。1000次重绑是任务回收回归，不等同大规模并发吞吐基准。
- 默认税费含电压、nerf部分付款/欠费拒收/充值恢复已用有限AE电池实测；仍未穷举每个电压档位、费用因子及长时间缺电组合。不能把单个场景当作所有平衡配置实测。
- 模型、粒子、绑定、指南已目视检查；没有逐像素审查全部端点方向、所有语言和配置倍率。原项目只有 en_us/ja_jp，保留这两种语言，没有承诺新增完整中文翻译。
- 指定上游 GT 在没有可选 JEI/EMI 时有目标类不存在的 Mixin 警告，还有部分字幕翻译/标签警告；MUI test_item 缺模型也来自上游。它们未导致启动或功能测试失败，未擅改指定 GT 源码来消除日志。容器访问 Mojang 公钥/遥测偶发失败，离线本机测试使用127.0.0.1且不影响配方运行；不把日志称作零错误。

完整复现和补充手工验收步骤见 [BUILDING-1.21.1.md](BUILDING-1.21.1.md)。源码分支与全部修改可从交付 git bundle 或源码压缩包恢复。
