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
- [x] `VoxyClient`：5ffaf791/b0acfabc/1f54025c 均只是日志文案，M1 接线已覆盖
- [x] `VoxyRenderSystem`：gl41metal 后端注册 + ShaderPatchBridgePayload 通路
- [x] `ShaderLoader`：新增 `parse(id, glslVersion)` 重载（gl41metal offscreen bakery 依赖）
- [x] `RenderGenerationService`：`TaskPriorityMode` 枚举 + 构造重载（gl41metal 依赖）
- [x] `ModelBakerySubsystem`：sink 模式构造 + 渲染线程 `blockIdQueue` 排水
- [x] `ModelFactory`：sink 模式合并，与 `SoftwareModelTextureBakery` 并存（gl46 走 software，gl41metal 走 gl41offscreen+sink）
- [x] `ModelQueries`：153261e9 在 M4，此处不动
- [x] `LightMapHelper`：`bind` 改用 GL4.1 兼容的 `glActiveTexture`+`glBindTexture`
- [x] `IrisUtil`：cachedBindings、`getCapturedOrFallbackViewportParameters`、`irisBlockStateIds`、`captureShaderPatchBridgePayload`
- [x] `iris/IrisShaderPatch`：`IMPERSONATE_DISTANT_HORIZONS` + `CONSTRUCTING_PIPELINE_PATCH` ThreadLocal
- [x] `iris/IrisVoxyRenderPipelineData`：**不迁移**（reference 中为死代码，gl41metal 实际走 VoxySamplers fallback 链）
- [x] `iris/VoxyUniforms`：IMPERSONATE_DISTANT_HORIZONS 分支 + frame matrices API
- [x] `mixin/iris/MixinIrisRenderingPipeline`：stashPatchData/FRAME_BEGIN/PRE_TRANSLUCENT hooks，保留 gl46 原有注入
- [x] `mixin/iris/MixinIrisSamplers`：透传 renderTargets 给 VoxySamplers
- [x] `mixin/iris/MixinProgramSet`：makePatch 异常兜底
- [x] `mixin/iris/MixinLevelRenderer`：当前仓库已与 reference 1.21.1 版本一致，无需改动
- [x] `Mipper`：4e55c30a delta —— 本仓库同样存在 block light 双移位 bug（`/8` 后再 `<<4` 移出 light byte，LOD>0 方块光=0），已移植修复
- [x] `mixin/sodium/MixinRenderRegionManager`：**不迁移**。本分支上游已注释禁用该 mixin（fade-in 取消为视觉优化，非必需）
- [x] `client.voxy.mixins.json`：AccessorLightTexture/AccessorTextureAtlas 已注册；`MixinLayerLightSectionStorage` 为本分支上游主动删除（remove lighting synchronizer），不恢复
- [x] `build.gradle`：macOS 检测 + native (clang++/xcrun metal) 编译任务 + `-Dvoxy.gl41metal.*` 透传
- [x] `assets/voxy/shaders/bakery/`：补齐 `buffercopy.comp`/`bufferreorder.comp`/`position_tex.vsh`/`position_tex.fsh`（GlViewCapture/BudgetBufferRenderer 引用，运行时按需加载）
- [x] `VoxyMixinConfigPlugin`（iris 存在性门控）：**不迁移**。本分支 iris 为硬依赖，iris mixin 无条件注册，维持现状

### M3（0a426396..bd977639）——按用户决定与 M2 合并实施（M2 测试发现与 reference M2 期同样的问题）
- [x] `backend/gl41metal/` 全部 java + `src/main/native/gl41metal/` 全部 native/metal：直接换 bd977639 快照，
  重施两处本地适配（`LEGACY_VIEWPORT_SETUP` pass-through、`setRenderDistance(float)`+ceil(+1)）
- [x] 新增文件：`Gl41MetalChunkBoundRenderer`、`LoadedVolumeBound`、`quad_raster.metal`
- [x] `RenderDataFactory`：fluid bridge walls（YZ 水平桥接 + X 桥接 + 双向第二 pass，`-Dvoxy.fluidBridgeWalls` 开关）
- [x] `Gl41OffscreenModelTextureBakery`：换 bd977639 快照（fluid baking + glass brightness）
- [x] `ModelFactory`：depth mode 增加 `isFluid ||` 条件（lava 走 solid layer 也取 MIN）
- [x] `RenderStage`：新增 `TRANSLUCENT`（Iris beginTranslucents RETURN）；gl46 走 default pass-through
- [x] `IrisUtil`：`captureTranslucentShaderPatchBridgePayload` + `resolveTargetTextures`（after-translucent flip）+ `acquireBindings` 重构
- [x] `iris/IrisShaderPatch`：744ce6a8/752dafba 均为格式化噪音，不动
- [x] `iris/VoxyUniforms`：格式化噪音，不动
- [x] `iris/IrisBridgeShaderBindings`：直接换 bd977639 快照
- [x] `mixin/iris/MixinIrisRenderingPipeline`：新增 `voxy$injectTranslucentBridge`（beginTranslucents RETURN）
- [x] `mixin/iris/MixinIrisSamplers`：格式化噪音，不动
- [x] `mixin/iris/MixinStandardMacros`：`voxy.disableVoxyDefine` 开关 + 启用 DISTANT_HORIZONS 伪装 define（保留本分支 VOXY 版本号 define）
- [x] `build.gradle`：`debugTranslucent`/`boundVerticalMargin`/`boundHorizontalMargin`/`boundAddDelayFrames`/`fluidBridgeWalls` 属性

### M4（10611319..251adc57）+ HEAD 全面对齐（M2+M3 测试反馈：vanilla translucent 侧面缺失 + Iris 抖动/光照错误）
- [x] `backend/gl41metal/` 全部 java + native/metal + `assets/voxy/shaders/lod/gl41metal/`：换 HEAD (251adc57) 快照，
  重施两处本地适配（`LEGACY_VIEWPORT_SETUP` pass-through、`setRenderDistance(float)`+ceil(+1)）；已 diff 验证只剩这两处差异
- [x] `ModelFactory`/`ModelQueries`/`RenderDataFactory`：153261e9（anyFaceNeedsDiscard/needsAlphaDiscard/hasCutout AABB 位）
- [x] `build.gradle`：2af5bb9d（`xcrun metal -std=metal3.0`）+ `meshBatchSize` 属性
- [x] **`TextureUtils.u2fdepth` 深度 ×2**（侧面缺失主嫌疑）：reference 恒 ×2（其 mdic 也走 GL bakery，
  窗口深度落在 [0,0.5]）；本仓库 gl46 走软件光栅（全范围深度）不能乘。改为 `computeDepth(..., halfDepthRange)`
  参数化，`ModelFactory` 按 `gl41OffscreenBakery != null` 传入。深度错一半会让 `Math.round(offset*64)`
  编码的面偏移减半 → 流体面高度/侧壁位置错误
- [x] `IrisShaderPatch.getTAAShift()`：taaOffset 为 null 时回退 `{return vec2(0.0);}`（防 NPE/编译错）
- [x] `RenderGenerationService`：ServiceManager throttle 谓词（bakery 积压>400 且失败>500 时暂停 worker）
  + 重试路径 `Thread.sleep(1)` 退避（上游 5baf4ce2，对 gl41metal 渲染线程 bakery 尤其重要）
- [x] `VoxySamplers`：`addDynamicSampler` 改用 2 参重载（null GlSampler，与 reference HEAD 一致；
  之前强制 `new GlSampler(false,true,false,false)` 会给深度纹理套采样器对象）；保留本分支 gl46 私有
  fb 深度回退链（reference 只回退到 Iris renderTargets，因其无 IrisVoxyRenderPipeline）
- [x] `MixinRenderSectionManager.voxy$updateOnUpload`：尊重 `setInfo` 返回值（false 时提前返回 false）
- [x] 已核对无需改动：`IrisUtil`（除 Fabric 接线与本分支扩展外与 HEAD 一致）、`MixinIrisRenderingPipeline`
  注入点（FRAME_BEGIN@HEAD / PRE_TRANSLUCENT@beginHand RETURN / TRANSLUCENT@beginTranslucents RETURN 均一致，
  另保留 gl46 专用 LEGACY_VIEWPORT_SETUP 注入）、`ModelBakerySubsystem.tick` 已有 `i != null` 守卫、
  native 目录与 HEAD 逐字节一致

### M4 二轮（M4 测试反馈：vanilla 侧面仍缺失 + Iris 光照分界明显）
- [x] **`MixinIrisRenderingPipeline.voxy$injectPipeline` 按能力门控**（Iris 光照主嫌疑）：reference 的
  build.gradle 用 sourceSets exclude 把 `IrisVoxyRenderPipeline`/`IrisVoxyRenderPipelineData`/`VoxySamplers`/
  `MixinIris` 整体排除出编译（注释明确说明 GL46 Iris 管线在其环境不存在）；本仓库为保留 gl46 双后端而保留了
  这些类，导致 gl41metal + Iris 时仍会构建 GL46 Iris 管线数据（触碰 Iris renderTargets/customUniforms、
  在 Apple GL4.1 上编译 GL46 目标着色器）。现改为仅当 `Capabilities.compute && indirectParameters`
  （即 gl46 可被选中）时才构建
- [x] `Gl41MetalTerrainResources.setRenderDistance`：去掉从 Gl46 抄来的 `+1` 外圈（reference 直接透传 RD；
  ceil 仅用于本分支小数 RD → int）
- [x] Iris 版本核实：fabric 侧不存在 1.8.12（最新 release 为 1.8.8；1.8.14-beta 需 Sodium 0.8）→ 维持 1.8.8，
  reference 用的 1.8.12 为 neoforge 专版
- [x] 排查定位数据（`-Dvoxy.meshDiag=true` 诊断，结论：**Java 侧数据全部正确**）：
  - BAKE-DIAG：water 全 6 面 depths/writeCounts 正确（侧面 flush、UP 面高度随 level 正确、×2 修复生效）；
    glass 全 6 面存在（writeCount=65 边框）
  - MODEL-DIAG：faceModelData/metadata 逐位核对正确（tint/discard/offset/size 与 reference 语义一致）
  - MESH-DIAG：translucent bucket 各朝向 side quads 大量存在且编码正确（多 LOD 层均有）
  - debugTranslucent 探针（用户确认）：tgbuffer 仅水面顶部有 coverage、侧面无 → 丢失发生在
    native/Metal 光栅或其输入之后，而 native/metallib 与 reference 逐字节一致 → 待与 reference
    同场景探针对照确定是否真为分歧

### M4 三轮（用户确认 reference HEAD 同样缺侧面 → 定位为 reference 自身回归并修复）
- [x] **根因：`bd977639` 本身**（非其后的性能优化 commit）。该 commit 为修复玻璃双层过暗给
  translucent encoder 加了 `MTLCullModeFront`，其注释假设 "Water still works because the mesher
  generates separate top/bottom quads"——但 mesher 对同一 face 对的两个方向发同一顶点角序
  （不做 per-direction 绕序归一化，这正是 fragment 里存在 `faceFlip`/`[[front_facing]]` 推导逻辑的原因）。
  自然绕序使 quad 仅在 +Y/-Z/-X 视向下通过 cull：水顶面幸存，+Z/+X 侧面与 -Y 底面在其暴露方向被剔除。
  lava/普通玻璃走 opaque compute 光栅（无该 cull）故侧面正常，与症状完全吻合。
- [x] 修复（保留 bd977639 的单面渲染意图，改按语义 face 而非绕序判定）：
  - `traversal_pipeline.mm`：translucent encoder 改回 `MTLCullModeNone`
  - `quad_raster.metal` `voxy_translucent_fragment`：早期 `discard` 掉从存储 face 背面观察到的片元
    （复用原 faceFlip 判据），存活片元 `normalFace = face`，等价 vanilla GL_CULL_FACE/GL_BACK
  - reference HEAD 存在同一 bug，此修复为本仓库对 reference 的**有意分歧**（可回灌 reference）
- [x] 确认：本仓库已含 reference 全部性能优化（indexed drawing `10611319`、compute pre-pass
  `e0f80c17`、Metal3 mesh shaders `2af5bb9d`、coop load `74e7f6b8`），即 HEAD `251adc57` 对齐

### M4 四轮（用户反馈：多层染色/遮光玻璃 LOD 仍比近景暗，单层玻璃正常）
- [x] **根因：section 内绘制顺序未排序**。translucent 只按 section 曼哈顿距离从后往前排序，
  section 内部按 mesher 发射顺序（与视角无关）。玻璃盒子与其周围水面在同一 section 时：
  - tgbuffer0/1 的"最前层"是"最后画的赢"（blend off），可能记录成后墙甚至水面 → 合成相当于
    把水盖在玻璃前面（黄玻璃被压成橄榄色、遮光玻璃变纯黑，截图证实）
  - tgbufferAccum 的 premultiplied OVER 也依赖从后往前顺序，乱序时权重错误
- [x] 修复（本仓库对 reference 的**有意分歧**，可回灌）：
  - `quad_raster.metal` translucent fragment：用 Apple GPU framebuffer fetch（`[[color(0/1)]]`
    输入）读回当前 tgbuffer0/1，按深度比较"最近者胜"，前层提取与顺序无关
  - `quad_raster_pipeline.mm`：accum RGB 混合改为加法（`ONE, ONE`，可交换），alpha 保持
    `ONE, ONE_MINUS_SRC_ALPHA`（1-∏(1-a)，本身可交换）
  - `GlDistantTerrainBridge` 两处 GLSL（VANILLA_WATER_PATCH + behind-layers）：behind 贡献
    改为 `(accum.rgb - frontFlat) * (1 - frontAlpha)`，两层任意顺序下精确，≥3 层时最深层
    轻微偏亮（近似）。注：三轮中间曾按"OVER 语义"移除该因子，四轮改加法累积后恢复
- [x] 性能核查：framebuffer fetch 在 Apple TBDR 上读 tile memory（blending 本就触碰），加法
  混合与 OVER 同价，无新增 pass/draw；实测同场景 metalGPU 均值 4.1–5.6ms vs 修复前
  4.5–4.9ms，量级一致，无回归
- [x] 用户验收：效果可接受；水面无回归

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
