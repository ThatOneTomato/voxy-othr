# gl41metal 迁移对账清单

来源：`.reference/voxy-neoforge`（NeoForge 1.21.1，分支 `neoforge-1.21.1`）。
迁移范围：`f453d4e8^..251adc57`（"Introduce GL41 render backend abstraction" 起共 53 个 commit），
squash 为 4 个实施里程碑。gl41 纯 CPU 后端不迁移。

## 决策记录

- 共享文件：把 reference 的功能性 delta 移植到当前仓库文件上，保留本分支修复（fog/cloud/water color 等）。
- 新增文件：直接 copy 对应快照版本，只做 Fabric/平台接线修改。
- model 子系统：保留 `SoftwareModelTextureBakery` 供 gl46 路径；sink 模式（gl41metal 用）直接 copy reference 实现。
- 命名：reference 的 `mdic` 在本仓库命名为 `gl46`（`-Dvoxy.renderBackend=gl46`）；删除 GL41 纯 CPU 分支。
- gl46 行为以当前仓库为准（reference 改造后的 mdic 未经运行验证），抽象层重构只做机械搬运。
- Iris 依赖：1.8.8 或 1.8.12 均可（mixin 注入点以实际编译/运行验证为准）。

## 快照点

| 里程碑 | reference commit | 内容 |
|---|---|---|
| M1/M2 | `c8f66271` | 后端抽象 + gl41metal 到 translucent 之前（worktree: `/tmp/ref-c8f66271`） |
| M3 | `bd977639` | translucent 完成（worktree: `/tmp/ref-bd977639`） |
| M4 | `251adc57` | 性能优化（HEAD，直接用 `.reference/voxy-neoforge`） |

格式化 commit（`48e11745` java、`295d797d` glsl prettier）不迁移；仅被这两个 commit 碰过的文件
（如 `hierachical/NodeManager`、`AsyncNodeManager`、`HierarchicalOcclusionTraverser` 等全部 gl46 核心）
**无功能差异，不动**。

## A. 新增文件（直接 copy）

### M1
- [ ] `client/core/rendering/backend/`：`VoxyRenderBackend` `RenderBackendId` `RenderBackendSelection` `RenderBackendSelector` `RenderStage` `RenderStageContext` `RenderFrame` `RenderFrameContext` `RenderFrameMatrices` `RenderFrameStageState` `BackendContext` `ShaderPatchBridgePayload` `NoopRenderFrame`（改名 MDIC→GL46，删 GL41 分支）
- [ ] `backend/gl46/`（对应 reference `backend/mdic/` 的 `MdicRenderBackend`/`MdicFrame` 结构，内容用当前仓库 `VoxyRenderSystem` 的渲染逻辑机械搬运）

### M2
- [ ] `backend/gl41metal/`（c8f66271 全部 java 文件）
- [ ] `src/main/native/gl41metal/`（c8f66271 版本，`.mm`/`.h`/`.metal`）
- [ ] `client/iris/IrisBridgeShaderBindings.java`
- [ ] `client/core/rendering/hierachical/MetalNodeSyncHost.java`（需对当前 NodeManager 编译适配）
- [ ] `client/core/util/RingUtil.java`
- [ ] `client/core/model/ModelOutputSink.java` `BakedModelPayload.java` `BiomeModelPayload.java`
- [ ] `client/core/model/bakery/CpuModelTextureBakery.java` `Gl41OffscreenModelTextureBakery.java`（+ 依赖的 `BudgetBufferRenderer`/`GlViewCapture`，当前仓库没有，一并 copy）
- [ ] `client/mixin/minecraft/AccessorLightTexture.java` `AccessorTextureAtlas.java`
- [ ] `client/mixin/iris/IrisRenderingPipelineAccessor.java`（当前仓库已有，比对补 delta）
- [ ] `assets/voxy/shaders/lod/gl41metal/interop_bridge.vert`

## B. 共享文件功能 delta（逐文件对账）

来自 commit→文件映射（已排除格式化 commit 与 backend/native/gl41 专属文件）。

### M1（f453d4e8 + 4eea87c1 时代的接线，取 c8f66271 快照对照）
- [ ] `VoxyRenderSystem`：重构为 facade（f453d4e8, 00030d63, 4eea87c1, 71e6ca03, d93e7b08 中非 gl41metal 部分）；**gl46 逻辑保持当前仓库行为**
- [ ] `VoxyClient`：backend selection 接线（f453d4e8 部分）
- [ ] `Capabilities`：openGl41 等探测（f453d4e8）
- [ ] `IrisUtil`：f453d4e8 + 4eea87c1 部分
- [ ] `mixin/sodium/MixinDefaultChunkRenderer`：f453d4e8 + 64573728 + 4eea87c1（stage 分发）
- [ ] `mixin/sodium/MixinRenderSectionManager`：f453d4e8 + 64573728（section state 事件）
- [ ] `mixin/sodium/MixinRenderRegionManager`：f453d4e8（当前仓库没有此 mixin，按需新增）
- [ ] `mixin/minecraft/MixinLevelRenderer`：4eea87c1（stage hooks）
- [ ] `mixin/iris/MixinIrisRenderingPipeline`：4eea87c1 部分（stage hooks）
- [ ] `client.voxy.mixins.json`：新增条目
- [ ] `VoxyMixinConfigPlugin`（4eea87c1）→ 合并进当前 `ClientVoxyMixinPlugin`
- [ ] `build.gradle`/`gradle.properties`：`-Dvoxy.renderBackend` 透传（f453d4e8）
- [ ] `RenderPipelineFactory`/`AbstractRenderPipeline`/`NormalRenderPipeline`/`IrisVoxyRenderPipeline`：确认在 facade 重构后的归属（reference 将其保留为 gl46/mdic 内部实现）

### M2（00030d63..c8f66271）
- [ ] `VoxyClient`：5ffaf791（interop 探测）、b0acfabc、1f54025c
- [ ] `VoxyRenderSystem`：gl41metal 后端注册 + ShaderPatchBridgePayload 通路
- [ ] `ShaderLoader`：c4edf3f3 delta（来自 gl41 工作，验证 gl41metal 是否依赖）
- [ ] `RenderGenerationService`：34b484f8 delta（来自 gl41 工作，验证 gl41metal 是否依赖）
- [ ] `ModelBakerySubsystem`：sink 模式构造（c4edf3f3, 5ac25662, 524ac9ef）
- [ ] `ModelFactory`：sink 模式合并（c4edf3f3, 5ac25662, dda65c92, 524ac9ef, 4fd1f953；M3/M4 另有）**——最高风险项，与 SoftwareModelTextureBakery 并存**
- [ ] `ModelQueries`：153261e9 在 M4
- [ ] `LightMapHelper`：4fd1f953
- [ ] `IrisUtil`：71e6ca03, 1f54025c, 4fd1f953, f654702e, d93e7b08
- [ ] `iris/IrisShaderPatch`：71e6ca03
- [ ] `iris/IrisVoxyRenderPipelineData`：1f54025c
- [ ] `iris/VoxyUniforms`：f453d4e8, d93e7b08
- [ ] `mixin/iris/MixinIrisRenderingPipeline`：71e6ca03, 4fd1f953, d93e7b08
- [ ] `mixin/iris/MixinIrisSamplers`：71e6ca03, d93e7b08
- [ ] `mixin/iris/MixinProgramSet`：71e6ca03
- [ ] `mixin/iris/MixinLevelRenderer`：1f54025c
- [ ] `Mipper`：4e55c30a delta（验证具体内容）
- [ ] `client.voxy.mixins.json`：71e6ca03, 1f54025c, f654702e
- [ ] `build.gradle`：native 编译/打包任务 + `-Dvoxy.gl41metal.*` 透传（loom 侧重写）

### M3（0a426396..bd977639）
- [ ] `RenderDataFactory`：f9461f86 + 0ef318d0（fluid bridge walls，`-Dvoxy.fluidBridgeWalls` 开关）
- [ ] `ModelFactory` + `Gl41OffscreenModelTextureBakery`：086ed8a0（fluid baking）
- [ ] `ModelFactory`：bd977639（translucent glass brightness）
- [ ] `IrisUtil`：086ed8a0, 79073f6d, 752dafba
- [ ] `iris/IrisShaderPatch`：744ce6a8, 752dafba
- [ ] `iris/VoxyUniforms`：752dafba
- [ ] `iris/IrisBridgeShaderBindings`：4e55c30a(M2 末) 之后的 d93e7b08/752dafba 增量（直接换 bd977639 快照）
- [ ] `mixin/iris/MixinIrisRenderingPipeline`：086ed8a0, 744ce6a8
- [ ] `mixin/iris/MixinIrisSamplers`：744ce6a8, 752dafba
- [ ] `mixin/iris/MixinStandardMacros`：bd977639
- [ ] `build.gradle`：086ed8a0, f9461f86, 752dafba 属性

### M4（10611319..251adc57）
- [ ] `ModelFactory`：153261e9
- [ ] `ModelQueries`：153261e9
- [ ] `RenderDataFactory`：153261e9
- [ ] `build.gradle`：2af5bb9d（Metal 3 mesh shader 编译选项）

## C. 基线分歧（两仓库实现不同、非本次迁移引入）

忽略空白比对（当前仓库 vs reference `f453d4e8^`）：

- 19 个文件仅基线不同且 reference 侧无功能改动（NeoForge 平台差异：`MixinMinecraft`、`MixinWindow`、
  `Taskbar`、`MixinBlockableEventLoop`、`LZ4Compressor` 等）→ 不动。
- 89 个文件基线不同 + reference 侧又被格式化/修改；其中有功能 delta 的均已列入 B 节，其余为格式化噪音 → 不动。
- 结构性分歧（需人工对照，已在 B 节标注）：
  - model 子系统：本仓库 `SoftwareModelTextureBakery`（软件光栅化）vs reference GL `ModelTextureBakery`
    + `CpuModelTextureBakery` + `Gl41OffscreenModelTextureBakery` + `ModelOutputSink` 双模式。
  - `VoxyRenderSystem`：本分支 fog/`RenderDistanceTracker` 逻辑 vs reference facade 化。
  - 本仓库有而 reference 无：`ClientSessionEvents`、`VoxyConfigScreenPages`、`RenderResourceReuse`、`SSAO`、
    `ARGB`、`ClientVoxyMixinPlugin`、`MixinDebugScreenOverlay`、`MixinGameRenderer`、sodium 的
    `MixinCloudRenderer`/`MixinShaderLoader`/`MixinSodiumOptionsGUI`/`MixinSodiumWorldRendererVS` 等
    （m3t4f1v3 分支特性，保留）。

## 测试记录

| 里程碑 | build | runClient | 结果 |
|---|---|---|---|
| M1 | | | |
| M2 | | | |
| M3 | | | |
| M4 | | | |
