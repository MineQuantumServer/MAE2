> **Minecraft 1.21.1 / NeoForge 移植分支**：`dot/mae2-1.21.1-neoforge`。
> 使用官方 AE2 19.2.18 和指定 FortyTwoCn GT 源码构建的8.0.0；前置不能按同名版本随意替换。
> [准备、构建、安装和测试](docs/BUILDING-1.21.1.md) · [功能验收、依赖基线与行为差异](docs/PORTING-1.21.1.md) · [依赖锁定](dependencies.lock.json)。
> 从干净环境执行 `python3 scripts/prepare_dependencies.py`，然后 `./gradlew clean build`；需要 JDK21、Python3.11+、Git和网络。

# Modern AE2 Additions
A mod all about random additions. Mainly going to be whatever I feel like adding to the game to help with AE2.

As a note, this mod is most likely going to be using mixins kinda heavily which
might make it incompatible with other mods trying to do the same
thing. Sometimes the mixins just have to be done in a way that's incompatible.

Pull requests are welcome, either for new features or bug fixes, as long as new
features are relatively tame and within vanilla AE2's scope. Maybe for bug fixes
make an issue first to link with the PR and make sure its really a bug. If you
want ideas peruse the TODO list at TODO.org, its got new feature ideas, and
optimizations to implement

# Discord
Speaking of pull requests, I've taken up residence in the Omicron Industries
discord. Come in and talk about the mod or yell at me to do stuff. (really do
complain at me to get stuff done, I mainly work on what people want, so if no
one says anything I'll never prioritize it). Discord link is here ->
https://discord.gg/5Uw7UzGXsX

Along with code additions, if some artist wants to work for free and make assets
for MAE2, I'd welcome them. Even if you just think the colors of something is
wrong, I'd take it. I'm terrible with graphics.

# Credits
Code are all files under `src/main/java/`. Resources are all files under
`src/main/resources/` and `src/generated/resources/`
## Code
All code in this repository is licensed under the LGPL (see `LICENSE.txt`),
unless noted otherwise. Each files licensing will be noted in the copyright header.

Some code used in this repository is copied/edited from the original
Applied Energistics 2. This should only be code in `@Overwrite` mixins or classes inside
the `appeng` package.

## Resources
Currently every texture/model in here is just a color shifted version of AE2's
texture. Full credits to the original artists, not gonna take credit for hue
shifting a crafting cpu
- (c) 2020, [Ridanisaurus Rid](https://github.com/Ridanisaurus/), (c) 2013 - 2020 AlgorithmX2 et al
- [![License](https://img.shields.io/badge/License-CC%20BY--NC--SA%203.0-yellow.svg?style=flat-square)](https://creativecommons.org/licenses/by-nc-sa/3.0/)

All other resources (including new textures/models if added) are licensed under [Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International](https://creativecommons.org/licenses/by-nc-sa/4.0/).
