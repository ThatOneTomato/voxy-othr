package me.cortex.voxy.client.core.rendering.backend.gl46;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.rendering.backend.gl46.util.HiZBuffer;

/**
 * GL46-side viewport state: the HiZ occlusion pyramid and the traversal render list are
 * consumed exclusively by the gl46 traversal/pipeline machinery, so they live here rather
 * than on the shared {@link Viewport}.
 */
public abstract class Gl46Viewport<A extends Gl46Viewport<A>> extends Viewport<A> {
    public final HiZBuffer hiZBuffer = new HiZBuffer();

    @Override
    protected void delete0() {
        this.hiZBuffer.free();
        super.delete0();
    }

    public abstract GlBuffer getRenderList();
}
