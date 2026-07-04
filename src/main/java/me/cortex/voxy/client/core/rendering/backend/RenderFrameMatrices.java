package me.cortex.voxy.client.core.rendering.backend;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public record RenderFrameMatrices(Matrix4fc mvp, Matrix4fc modelView, Matrix4fc projection) {
    public RenderFrameMatrices {
        mvp = new Matrix4f(mvp);
        modelView = new Matrix4f(modelView);
        projection = new Matrix4f(projection);
    }

    public static RenderFrameMatrices identity() {
        Matrix4f identity = new Matrix4f();
        return new RenderFrameMatrices(identity, identity, identity);
    }
}
