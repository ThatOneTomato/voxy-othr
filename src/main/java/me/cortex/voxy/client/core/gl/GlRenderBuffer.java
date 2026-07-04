package me.cortex.voxy.client.core.gl;

import static org.lwjgl.opengl.GL45C.*;

import me.cortex.voxy.common.util.TrackedObject;

public class GlRenderBuffer extends TrackedObject {
  public final int id;

  public GlRenderBuffer(int format, int width, int height) {
    this.id = glCreateRenderbuffers();
    glNamedRenderbufferStorage(this.id, format, width, height);
  }

  @Override
  public void free() {
    super.free0();
    glDeleteRenderbuffers(this.id);
  }
}
