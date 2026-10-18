# 准备、构建、安装与回归验证

## 干净环境

需要 Git、Python **3.11或更新**、完整 JDK21（`java` 与 `javac` 均可用），以及 GitHub、Maven Central、NeoForge、Mojang 和锁定上游构建仓库的网络访问。实测 JDK 为 Temurin21.0.12.1+1-LTS，Gradle Wrapper8.14。首次准备 GT 与 Minecraft 依赖需要数GB磁盘和可用内存；建议给 Gradle3GB、游戏4GB以上。代理/企业CA按本机环境配置，不应跳过TLS校验。

从交付 bundle 恢复包含历史与工作分支的仓库：

```sh
git clone mae2-1.21.1.bundle MAE2
cd MAE2
git switch dot/mae2-1.21.1-neoforge
java -version
javac -version
python3 --version
python3 scripts/prepare_dependencies.py
./gradlew --no-daemon clean build dependencyEvidence
python3 scripts/audit_jar.py
python3 scripts/run_gametests.py
```

也可从 `mae2-source.zip` 解压后执行相同构建命令；该源码快照不带 `.git`，GT/ModularUI 的检出仍由准备脚本完成。Windows 使用 `python`、`gradlew.bat`，脚本会自动选择 Windows Wrapper；本次没有实测 Windows。

准备脚本将：

1. 按锁定 URL 下载官方 AE2 和 GuideME，验证 SHA-256。
2. 检出 FortyTwoCn GT 的指定完整提交，拒绝错误提交或源码修改；检出指定 ModularUI 提交，应用并校验 GT 自带补丁和补丁结果。
3. 使用 `work/dependencies/m2` 隔离 Maven local，运行 ModularUI 的 `test publishToMavenLocal`，然后 GT 的 `jar verifyMixinConfigs`；构建配置强制官方 AE2 19.2.18。
4. 核对 GT 产物哈希和来源 manifest，拷到 `dependencies/`；从 GT 内嵌 JarJar 提取仅用于编译的库。运行时由 GT 自己加载这些内嵌库。

正常产物已存在且哈希吻合时可直接复用；需要重新源码构建时运行 `python3 scripts/prepare_dependencies.py --rebuild`。需要证明不依赖现有 Maven local 时，移走或删除本项目生成的 `work/dependencies/` 后重跑；用户全局 Maven 缓存不参与。不要删除用户自己的目录。脚本不依赖本次机器的 `/workspace`、JDK安装目录或未记录的本地库。

若构建所得 GT 哈希不同，脚本会失败并保留日志/检出目录供比对；先核对 JDK和解析依赖，不要把校验值随便改成新值。完整锁文件是 `dependencies.lock.json`。

## 构建、数据生成和测试

```sh
./gradlew --no-daemon clean build dependencyEvidence
python3 scripts/audit_jar.py
./gradlew --no-daemon runData
python3 scripts/run_gametests.py
```

- 可安装产物：`build/libs/mae2-2.1.0+mc1.21.1.jar`。`-sources.jar` 供源码查看，不放入 mods。
- JUnit 结果：`build/test-results/test/`、`build/reports/tests/test/`。
- 实际编译/运行依赖清单：`build/reports/dependency-evidence.json`。
- GameTest 完整日志及计数：`work/validation/gametests.log`、`gametests.json`。脚本核对源码注解数与实际完成/通过数；必须 `verified:true`，不能只看Gradle退出码。
- 当前完整测试应为42项GameTests和5项JUnit。所有前置是真正的目标 JAR，未用空壳替代。原11项GameTests包含在内，全部required。
- NeoForge生产模式不允许GameTests；用Wrapper `runGameTestServer`，不要在普通安装版服务器上加一个系统属性就宣称测试已执行。
- `runData` 后检查生成资源差异，再重新 `build` 使其入包。仓库已提交生成资源，因此普通构建不依赖先运行data任务。

开发客户端与专服：

```sh
./gradlew --no-daemon runClient
./gradlew --no-daemon runServer
# 已有本地服务器时可直接进服
./gradlew --no-daemon -PserverAddress=127.0.0.1:25565 runClient
```

工作目录分别是 `run/client`、`run/server`、`run/gameTestServer`。开发专服首次需按游戏提示阅读/接受 EULA，再正常启动。最终验收还使用了下节的标准安装版专服，并用仅加载最终JAR的独立MDG客户端运行验证。

## 安装目标环境

`mae2-runtime.zip` 中的 `mods/` 恰好包含以下4个文件；同时装到客户端和服务器：

```text
mae2-2.1.0+mc1.21.1.jar
appliedenergistics2-19.2.18.jar
gtceu-1.21.1-8.0.0.jar
guideme-21.1.1.jar
```

先执行 `sha256sum -c SHA256SUMS`（或用对应系统的SHA-256工具）检查交付文件；runtime包内也有校验清单。不要安装其他来源的同名GT8.0.0，不能仅按版本判断fork来源。无需单独装ModularUI、Registrate、Configuration、EvalEx，也无需JEI/EMI/LDLib。

专用服务器，空目录中：

```sh
curl -fL https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.248/neoforge-21.1.248-installer.jar -o neoforge-installer.jar
java -jar neoforge-installer.jar --installServer
# 阅读并接受Minecraft EULA后，在eula.txt写入eula=true
# 将上述4个JAR拷入mods/，按机器内存配置user_jvm_args.txt
./run.sh nogui
```

Windows 对应 `run.bat nogui`。等待 `Done`、加载新世界后在控制台执行 `stop` 正常保存退出，再次启动检验持久化。本次容器只做127.0.0.1离线本机连接；对外服务器保持正常正版认证及你自己的访问设置。GT开发默认启用高阶内容、生产默认关闭，复用开发验证世界时应同时复用对应GT配置；新世界使用默认配置即可。

客户端：在正常 Minecraft Launcher 安装 **1.21.1**，用同一个NeoForge21.1.248安装器安装客户端profile，选择Java21，将4个JAR放入该profile的mods目录。启动进入新世界或连接上述服务器。也可用Wrapper开发客户端复现功能测试，但这与普通安装版启动分别报告。

## 可复现的手工验收矩阵

自动用例的具体构造在 `src/main/java/stone/mae2/gametests/`。从玩家端复核：

1. 创造模式建立通电的AE2网络。放置普通EU/样板P2P和六类Multi；用记忆卡潜行右键源端复制、右键其他端粘贴，检查方向、输入输出和频率颜色。用原有调谐物品切换类型，重新拆装，再检查能力。
2. Faulty Card：潜行对空气切换AoE、Global、Incrementing、Über；普通对空气循环范围或递增/递减子模式。AoE仅同一面，Global仅同网络同类型。Über两角选区默认体积上限32768，测试直线/平面超限和未加载区块边界，确认没有强加载大片区块。
3. 使用4/16/64/256加速器分别组成CPU，并在界面或调试结果中核对线程。混合四档加原版应341；拆除原版应340；拆装高档、保存退出重进再次核对。
4. 普通与Multi样板P2P，各测试整块、部件供应器。先用分子装配室合成一批工作台，再用GT LV电炉配处理样板“矿石→锭”；提供EU、允许GT向输出端自动出料，提交AE2任务，确认任务完成、产物进入ME、CPU不再busy。GameTest里的4种GT组合可直接参考。
5. 准备两台独立加工设备和一份多种原料样板：确认整份样板不会拆到两个设备。将设备库存填满或只留部分空间，确认拒绝/排队行为，腾空间后只补剩余物料。更换样板、拆端点、改频率和重连检查配方刷新。
6. 开/关供应器原生阻挡；开启时对准供应器执行 `/mae2 blocking default|all|smart`。测试配方原料、非配方物料、GT编程电路；电路不应触发额外阻挡。保存/复制设置后重验。
7. 物品/流体多输入输出：记录两端总量，测试满载、拒绝、部分接收；模拟操作不改变总量，真实操作前后总量相等。流体用两个GT桶，合计2000mB，依次1500与500抽出。
8. EU接真实GT设备：32V正常入LV，反面不接收，同tick按设备安培限额返回；超额安培留在发送方。128V接LV应按指定GT规则过压；在独立测试区操作，避免炸毁其他验收装置。FE/EU断电、断线、重新通电后检查旧能力失效、守恒和缓冲。
9. 如使用EU nerf配置，记录有限AE电量与转移安培，核对税率；耗尽ME电力、保留欠费，充值后确认恢复，原有高压/低压平衡不变。自动测试已覆盖默认税费含电压、部分付款、欠费阻止接收和充电恢复；仍可扩展不同档位和配置因子的组合。
10. 在输出端保存尚未投完的原料，正常停服再启动；恢复设备后应投递一次。离开至区块确实卸载再回来，检查FE、EU电压、频率、颜色和队列。本次保存恢复夹具：FE12345、EU1001/128V、样板队列7钻石、绑定23653。
11. 客户端观察四档已成型CPU、云室玻璃和粒子、卡片颜色；打开AE2指南中的MAE2页面和3D场景，检查语言、配方和掉落。修改 `config/mae2-client.toml` 粒子倍率后重载/重启确认效果。
12. 大规模生产部署前另测同时存活的多端点吞吐和长时间tick开销。42项自动测试、单次1000重绑回收通过，不代替多小时、大型多方块/跨维度压力测试。

没有验证过的1.20.1 Forge整包升级、其他系统/驱动和长期压力场景，均未声明支持。
