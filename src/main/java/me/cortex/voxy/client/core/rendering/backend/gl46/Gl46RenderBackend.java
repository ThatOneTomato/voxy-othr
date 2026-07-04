package me.cortex.voxy.client.core.rendering.backend.gl46;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import me.cortex.voxy.client.TimingStatistics;
import me.cortex.voxy.client.VoxyClient;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.AbstractRenderPipeline;
import me.cortex.voxy.client.core.RenderPipelineFactory;
import me.cortex.voxy.client.core.RenderResourceReuse;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.GlTexture;
import me.cortex.voxy.client.core.model.ModelBakerySubsystem;
import me.cortex.voxy.client.core.rendering.ChunkBoundRenderer;
import me.cortex.voxy.client.core.rendering.RenderDistanceTracker;
import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.rendering.ViewportSelector;
import me.cortex.voxy.client.core.rendering.backend.BackendContext;
import me.cortex.voxy.client.core.rendering.backend.NoopRenderFrame;
import me.cortex.voxy.client.core.rendering.backend.RenderBackendId;
import me.cortex.voxy.client.core.rendering.backend.RenderFrame;
import me.cortex.voxy.client.core.rendering.backend.RenderFrameContext;
import me.cortex.voxy.client.core.rendering.backend.RenderFrameMatrices;
import me.cortex.voxy.client.core.rendering.backend.RenderStage;
import me.cortex.voxy.client.core.rendering.backend.RenderStageContext;
import me.cortex.voxy.client.core.rendering.backend.VoxyRenderBackend;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.client.core.rendering.hierachical.AsyncNodeManager;
import me.cortex.voxy.client.core.rendering.hierachical.HierarchicalOcclusionTraverser;
import me.cortex.voxy.client.core.rendering.hierachical.NodeCleaner;
import me.cortex.voxy.client.core.rendering.section.IUsesMeshlets;
import me.cortex.voxy.client.core.rendering.section.backend.AbstractSectionRenderer;
import me.cortex.voxy.client.core.rendering.section.backend.mdic.MDICSectionRenderer;
import me.cortex.voxy.client.core.rendering.section.geometry.BasicSectionGeometryData;
import me.cortex.voxy.client.core.rendering.section.geometry.IGeometryData;
import me.cortex.voxy.client.core.rendering.util.DownloadStream;
import me.cortex.voxy.client.core.rendering.util.PrintfDebugUtil;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.client.core.util.GPUTiming;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL11.glGetIntegerv;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30.glGetIntegeri;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER_BINDING;

// Mechanical relocation of the pre-abstraction VoxyRenderSystem rendering core (this repository's
// MDIC implementation). Behaviour is intentionally kept identical to the current branch, including
// the fog capture consumers, RenderResourceReuse geometry buffer reuse and lateStageCompile.
public final class Gl46RenderBackend implements VoxyRenderBackend {
    private final WorldEngine worldIn;

    private final ModelBakerySubsystem modelService;
    private final RenderGenerationService renderGen;
    private final IGeometryData geometryData;
    private final AsyncNodeManager nodeManager;
    private final NodeCleaner nodeCleaner;
    private final HierarchicalOcclusionTraverser traversal;

    private final RenderDistanceTracker renderDistanceTracker;
    public final ChunkBoundRenderer chunkBoundRenderer;

    private final ViewportSelector<?> viewportSelector;

    private final AbstractRenderPipeline pipeline;

    private RenderFrameMatrices lastFrameMatrices = RenderFrameMatrices.identity();

    private static AbstractSectionRenderer.Factory<?,? extends IGeometryData> getRenderBackendFactory() {
        //TODO: need todo a thing where selects optimal section render based on if supports the pipeline and geometry data type
        return MDICSectionRenderer.FACTORY;
    }

    public Gl46RenderBackend(BackendContext context) {
        WorldEngine world = context.world();
        Logger.info("Creating Voxy GL46 render backend");

        if (Minecraft.getInstance().options.renderDistance().get()<3) {
            String msg = "Voxy: Having a vanilla render distance of 2 can cause rare culling near the edge of your screen issues, please use 3 or more";
            Logger.warn(msg);
            Minecraft.getInstance().getChatListener().handleSystemMessage(Component.literal(msg), false);
        }

        //Fking HATE EVERYTHING AAAAAAAAAAAAAAAA
        int[] oldBufferBindings = new int[10];
        for (int i = 0; i < oldBufferBindings.length; i++) {
            oldBufferBindings[i] = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, i);
        }

        //wait for opengl to be finished, this should hopefully ensure all memory allocations are free
        glFinish();
        glFinish();

        this.worldIn = world;

        var backendFactory = getRenderBackendFactory();

        {
            this.modelService = new ModelBakerySubsystem(world.getMapper());
            this.renderGen = new RenderGenerationService(world, this.modelService, context.serviceManager(), IUsesMeshlets.class.isAssignableFrom(backendFactory.clz()));

            this.geometryData = new BasicSectionGeometryData(1<<20, RenderResourceReuse.getOrCreateGeometryBuffer());

            this.nodeManager = new AsyncNodeManager(1 << 21, this.geometryData, this.renderGen);
            this.nodeCleaner = new NodeCleaner(this.nodeManager);
            this.traversal = new HierarchicalOcclusionTraverser(this.nodeManager, this.nodeCleaner, this.renderGen);

            world.setDirtyCallback(this.nodeManager::worldEvent);

            Arrays.stream(world.getMapper().getBiomeEntries()).forEach(this.modelService::addBiome);
            world.getMapper().setBiomeCallback(this.modelService::addBiome);

            this.nodeManager.start();
        }

        this.pipeline = RenderPipelineFactory.createPipeline(this.nodeManager, this.nodeCleaner, this.traversal, this::frexStillHasWork);
        this.pipeline.setupExtraModelBakeryData(this.modelService);//Configure the model service

        //Late stage traversal compile for shaders with taa
        this.traversal.lateStageCompile(this.pipeline);


        var sectionRenderer = backendFactory.create(this.pipeline, this.modelService.getStore(), this.geometryData);
        this.pipeline.setSectionRenderer(sectionRenderer);
        this.viewportSelector = new ViewportSelector<>(sectionRenderer::createViewport);

        {
            int minSec = Minecraft.getInstance().level.getMinSection() >> 5;
            int maxSec = (Minecraft.getInstance().level.getMaxSection() - 1) >> 5;

            //Do some very cheeky stuff for MiB
            if (VoxyCommon.IS_MINE_IN_ABYSS) {//TODO: make this somehow configurable
                minSec = -8;
                maxSec = 7;
            }

            this.renderDistanceTracker = new RenderDistanceTracker(40,
                    minSec,
                    maxSec,
                    this.nodeManager::addTopLevel,
                    this.nodeManager::removeTopLevel);

            this.setRenderDistance(VoxyConfig.CONFIG.sectionRenderDistance);
        }

        this.chunkBoundRenderer = new ChunkBoundRenderer(this.pipeline);

        Logger.info("Voxy GL46 backend created with " + this.geometryData.getMaxCapacity() + " geometry capacity, using pipeline '" + this.pipeline.getClass().getSimpleName() + "' with renderer '" + sectionRenderer.getClass().getSimpleName() + "'");

        for (int i = 0; i < oldBufferBindings.length; i++) {
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, i, oldBufferBindings[i]);
        }

        for (int i = 0; i < 12; i++) {
            GlStateManager._activeTexture(GlConst.GL_TEXTURE0+i);
            GlStateManager._bindTexture(0);
            glBindSampler(i, 0);
        }
    }

    @Override
    public RenderBackendId id() {
        return RenderBackendId.GL46;
    }

    @Override
    public boolean rendersLodTerrain() {
        return true;
    }

    @Override
    public RenderFrame runFrameStage(RenderStage stage, RenderStageContext context, RenderFrame frame) {
        return switch (stage) {
            // Iris beginLevelRendering setup point: always (re)creates the frame, matching the
            // pre-abstraction CAPTURED_VIEWPORT_PARAMETERS.apply() behaviour.
            case LEGACY_VIEWPORT_SETUP -> this.setupFrame(context.frameContext());
            case LEGACY_OPAQUE -> {
                if (IrisUtil.irisShadowActive()) {
                    // During the Iris shadow pass the pre-abstraction getViewport() returned null,
                    // so nothing was rendered.
                    yield frame;
                }
                RenderFrame renderFrame = frame;
                if (renderFrame == null) {
                    renderFrame = this.setupFrame(context.frameContext());
                }
                this.renderOpaque(renderFrame);
                yield renderFrame;
            }
            default -> frame;
        };
    }

    @Override
    public RenderFrame setupFrame(RenderFrameContext context) {
        var viewport = this.getViewport();
        if (viewport == null) {
            return new NoopRenderFrame(RenderBackendId.GL46);
        }

        double cameraX = context.cameraX();
        double cameraY = context.cameraY();
        double cameraZ = context.cameraZ();

        //Do some very cheeky stuff for MiB
        if (VoxyCommon.IS_MINE_IN_ABYSS) {
            int sector = (((int)Math.floor(cameraX)>>4)+512)>>10;
            cameraX -= sector<<14;//10+4
            cameraY += (16+(256-32-sector*30))*16;
        }

        var voxyProjection = computeProjectionMat(context.matrices().projection());

        int width = context.viewportWidth();
        int height = context.viewportHeight();

        {//Apply render scaling factor
            var factor = this.pipeline.getRenderScalingFactor();
            if (factor != null) {
                width = (int) (width*factor[0]);
                height = (int) (height*factor[1]);
            }
        }

        viewport
                .setVanillaProjection(context.matrices().projection())
                .setProjection(voxyProjection)
                .setModelView(new Matrix4f(context.matrices().modelView()))
                .setCamera(cameraX, cameraY, cameraZ)
                .setScreenSize(width, height)
                .update();

        if (VoxyClient.getOcclusionDebugState()==0) {
            viewport.frameId++;
        }

        this.lastFrameMatrices = new RenderFrameMatrices(viewport.MVP, viewport.modelView, viewport.projection);
        return new Gl46Frame(viewport);
    }

    @Override
    public void renderOpaque(RenderFrame frame) {
        if (!(frame instanceof Gl46Frame gl46Frame)) {
            return;
        }
        Viewport<?> viewport = gl46Frame.viewport();
        if (viewport == null) {
            return;
        }
        if (viewport.width <= 0 || viewport.height <= 0) {
            return;//Only render on valid viewport
        }

        TimingStatistics.resetSamplers();

        TimingStatistics.all.start();
        GPUTiming.INSTANCE.marker();//Start marker
        TimingStatistics.main.start();

        //TODO: optimize
        int[] oldBufferBindings = new int[10];
        for (int i = 0; i < oldBufferBindings.length; i++) {
            oldBufferBindings[i] = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, i);
        }


        int oldFB = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        int boundFB = oldFB;

        int[] dims = new int[4];
        glGetIntegerv(GL_VIEWPORT, dims);

        glViewport(0,0, viewport.width, viewport.height);

        if (boundFB == 0) {
            throw new IllegalStateException("Cannot use the default framebuffer as cannot source from it");
        }

        this.pipeline.preSetup(viewport);

        TimingStatistics.E.start();
        if ((!VoxyClient.disableSodiumChunkRender())&&!IrisUtil.irisShadowActive()) {
            this.chunkBoundRenderer.render(viewport);
        } else {
            viewport.depthBoundingBuffer.clear(0);
        }
        TimingStatistics.E.stop();


        GPUTiming.INSTANCE.marker();
        //The entire rendering pipeline (excluding the chunkbound thing)
        this.pipeline.runPipeline(viewport, boundFB, dims[2], dims[3]);
        GPUTiming.INSTANCE.marker();


        TimingStatistics.main.stop();
        TimingStatistics.postDynamic.start();

        PrintfDebugUtil.tick();

        //As much dynamic runtime stuff here
        {
            //Tick upload stream (this is ok to do here as upload ticking is just memory management)
            UploadStream.INSTANCE.tick();

            while (this.renderDistanceTracker.setCenterAndProcess(viewport.cameraX, viewport.cameraZ) && VoxyClient.isFrexActive());//While FF is active, run until everything is processed
            TimingStatistics.H.start();
            //Done here as is allows less gl state resetup
            do { this.modelService.tick(900_000); } while (VoxyClient.isFrexActive() && !this.modelService.areQueuesEmpty());
            TimingStatistics.H.stop();
        }
        GPUTiming.INSTANCE.marker();
        TimingStatistics.postDynamic.stop();

        GPUTiming.INSTANCE.tick();

        glBindFramebuffer(GlConst.GL_FRAMEBUFFER, oldFB);
        glViewport(dims[0], dims[1], dims[2], dims[3]);

        {//Reset state manager stuffs
            glUseProgram(0);
            glEnable(GL_DEPTH_TEST);
            glDisable(GL_STENCIL_TEST);

            GlStateManager._glBindVertexArray(0);//Clear binding

            GlStateManager._activeTexture(GlConst.GL_TEXTURE1);
            for (int i = 0; i < 12; i++) {
                GlStateManager._activeTexture(GlConst.GL_TEXTURE0+i);
                GlStateManager._bindTexture(0);
                glBindSampler(i, 0);
            }

            IrisUtil.clearIrisSamplers();//Thanks iris (sigh)

            //TODO: should/needto actually restore all of these, not just clear them
            //Clear all the bindings
            for (int i = 0; i < oldBufferBindings.length; i++) {
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, i, oldBufferBindings[i]);
            }
        }

        TimingStatistics.all.stop();
    }

    public static float getRenderDistance() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance()*16;
    }

    private static Matrix4f computeProjectionMat(Matrix4fc base) {

        var proj = new Matrix4f(base);

        float near = getRenderDistance()<=32.0f?8f:16f;
        near = VoxyClient.disableSodiumChunkRender()?0.1f:near;

        float far = 16*3000;

        return proj
                .m22((far + near) / (near - far))
                .m32((far+far) * near / (near - far));
    }

    private boolean frexStillHasWork() {
        if (!VoxyClient.isFrexActive()) {
            return false;
        }
        //If frex is running we must tick everything to ensure correctness
        UploadStream.INSTANCE.tick();
        //Done here as is allows less gl state resetup
        this.modelService.tick(100_000_000);
        GL11.glFinish();
        return this.nodeManager.hasWork() || this.renderGen.getTaskCount()!=0 || !this.modelService.areQueuesEmpty();
    }

    @Override
    public void setRenderDistance(float renderDistance) {
        this.renderDistanceTracker.setRenderDistance((int) Math.ceil(renderDistance+1));//the +1 is to cover the outer ring of chunks when rendering a circle
    }

    private Viewport<?> getViewport() {
        if (IrisUtil.irisShadowActive()) {
            return null;
        }
        return this.viewportSelector.getViewport();
    }

    @Override
    public RenderFrameMatrices getLastFrameMatrices() {
        return this.lastFrameMatrices;
    }

    @Override
    public void onChunkTrackerReset() {
        this.chunkBoundRenderer.reset();
    }

    @Override
    public void onSectionRenderStateChanged(long sectionPos, boolean present) {
        if (present) {
            this.chunkBoundRenderer.addSection(sectionPos);
        } else {
            this.chunkBoundRenderer.removeSection(sectionPos);
        }
    }

    @Override
    public void addDebugInfo(List<String> debug) {
        debug.add("Voxy backend: GL46");
        debug.add("Buf/Tex [#/Mb]: [" + GlBuffer.getCount() + "/" + (GlBuffer.getTotalSize()/1_000_000) + "],[" + GlTexture.getCount() + "/" + (GlTexture.getEstimatedTotalSize()/1_000_000)+"]");
        {
            this.modelService.addDebugData(debug);
            this.renderGen.addDebugData(debug);
            this.nodeManager.addDebug(debug);
            this.pipeline.addDebug(debug);
        }
        {
            TimingStatistics.update();
            debug.add("Voxy frame runtime (millis): " + TimingStatistics.dynamic.pVal() + ", " + TimingStatistics.main.pVal()+ ", " + TimingStatistics.postDynamic.pVal()+ ", " + TimingStatistics.all.pVal());
            debug.add("Extra time: " + TimingStatistics.A.pVal() + ", " + TimingStatistics.B.pVal() + ", " + TimingStatistics.C.pVal() + ", " + TimingStatistics.D.pVal());
            debug.add("Extra 2 time: " + TimingStatistics.E.pVal() + ", " + TimingStatistics.F.pVal() + ", " + TimingStatistics.G.pVal() + ", " + TimingStatistics.H.pVal() + ", " + TimingStatistics.I.pVal());
        }
        debug.add(GPUTiming.INSTANCE.getDebug());
        PrintfDebugUtil.addToOut(debug);
    }

    @Override
    public void close() {
        Logger.info("Flushing download stream");
        DownloadStream.INSTANCE.flushWaitClear();
        Logger.info("Shutting down rendering");
        try {
            //Cleanup callbacks
            this.worldIn.setDirtyCallback(null);
            this.worldIn.getMapper().setBiomeCallback(null);
            this.worldIn.getMapper().setStateCallback(null);

            this.nodeManager.stop();

            this.modelService.shutdown();
            this.renderGen.shutdown();
            this.traversal.free();
            this.nodeCleaner.free();
            this.geometryData.free();
            if (((BasicSectionGeometryData)this.geometryData).isExternalGeometryBuffer) {
                RenderResourceReuse.giveBackGeometryBuffer(((BasicSectionGeometryData)this.geometryData).getGeometryBuffer());
            }

            this.chunkBoundRenderer.free();

            this.viewportSelector.free();
        } catch (Exception e) {Logger.error("Error shutting down renderer components", e);}
        Logger.info("Shutting down render pipeline");
        try {this.pipeline.free();} catch (Exception e){Logger.error("Error releasing render pipeline", e);}


        Logger.info("Flushing download stream");
        DownloadStream.INSTANCE.flushWaitClear();
    }
}
