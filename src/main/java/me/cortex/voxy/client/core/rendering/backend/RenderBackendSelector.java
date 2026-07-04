package me.cortex.voxy.client.core.rendering.backend;

import me.cortex.voxy.client.core.gl.Capabilities;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.Gl41MetalSupport;
import me.cortex.voxy.client.core.rendering.backend.gl46.Gl46Support;
import me.cortex.voxy.common.Logger;

import java.util.Locale;

public final class RenderBackendSelector {
    public static final String PROPERTY = "voxy.renderBackend";

    private RenderBackendSelector() {}

    public static RenderBackendSelection select(Capabilities capabilities) {
        String requested = System.getProperty(PROPERTY, "auto").trim().toLowerCase(Locale.ROOT);
        RenderBackendSelection selection =
                switch (requested) {
                    case "auto" -> selectAuto(capabilities, requested);
                    case "gl46", "mdic" -> selectGl46(capabilities, requested, true);
                    case "gl41metal" -> selectGl41Metal(capabilities, requested, true);
                    default -> {
                        Logger.error(
                                "Unknown Voxy render backend '"
                                        + requested
                                        + "'. Valid values are: auto, gl46, gl41metal");
                        yield new RenderBackendSelection(
                                RenderBackendId.DISABLED,
                                requested,
                                "Unknown backend id; valid values are auto, gl46, gl41metal",
                                true);
                    }
                };

        Logger.info(
                "Selected Voxy render backend: "
                        + selection.id()
                        + " (requested='"
                        + selection.requested()
                        + "', forced="
                        + selection.forced()
                        + ", reason='"
                        + selection.reason()
                        + "')");
        return selection;
    }

    private static RenderBackendSelection selectAuto(Capabilities capabilities, String requested) {
        String gl46UnsupportedReason = getGl46UnsupportedReason(capabilities);
        if (gl46UnsupportedReason == null) {
            return new RenderBackendSelection(
                    RenderBackendId.GL46, requested, "GL46 capabilities are available", false);
        }
        if (!capabilities.openGl41) {
            return new RenderBackendSelection(
                    RenderBackendId.DISABLED,
                    requested,
                    "GL46 unavailable: " + gl46UnsupportedReason + "; OpenGL 4.1 is not available",
                    false);
        }

        String gl41MetalUnsupportedReason = getGl41MetalUnsupportedReason();
        if (gl41MetalUnsupportedReason == null) {
            return new RenderBackendSelection(
                    RenderBackendId.GL41METAL,
                    requested,
                    "GL46 unavailable: "
                            + gl46UnsupportedReason
                            + "; GL41Metal capabilities are available",
                    false);
        }

        return new RenderBackendSelection(
                RenderBackendId.DISABLED,
                requested,
                "GL46 unavailable: "
                        + gl46UnsupportedReason
                        + "; GL41Metal unavailable: "
                        + gl41MetalUnsupportedReason,
                false);
    }

    private static RenderBackendSelection selectGl46(
            Capabilities capabilities, String requested, boolean forced) {
        String unsupportedReason = getGl46UnsupportedReason(capabilities);
        if (unsupportedReason != null) {
            Logger.error("Cannot use Voxy GL46 backend: " + unsupportedReason);
            return new RenderBackendSelection(
                    RenderBackendId.DISABLED,
                    requested,
                    "Forced GL46 unavailable: " + unsupportedReason,
                    forced);
        }
        return new RenderBackendSelection(
                RenderBackendId.GL46, requested, "Forced GL46 backend selected", forced);
    }

    private static RenderBackendSelection selectGl41Metal(
            Capabilities capabilities, String requested, boolean forced) {
        if (!capabilities.openGl41) {
            Logger.error("Cannot use Voxy GL41Metal backend: OpenGL 4.1 is not available");
            return new RenderBackendSelection(
                    RenderBackendId.DISABLED,
                    requested,
                    "Forced GL41Metal unavailable: OpenGL 4.1 is not available",
                    forced);
        }
        String unsupportedReason = getGl41MetalUnsupportedReason();
        if (unsupportedReason == null) {
            return new RenderBackendSelection(
                    RenderBackendId.GL41METAL,
                    requested,
                    "Forced GL41Metal backend selected; macOS Metal/OpenGL shared texture diagnostics are available",
                    forced);
        }
        Logger.error("Cannot use Voxy GL41Metal backend: " + unsupportedReason);
        return new RenderBackendSelection(
                RenderBackendId.DISABLED,
                requested,
                "Forced GL41Metal unavailable: " + unsupportedReason,
                forced);
    }

    private static String getGl46UnsupportedReason(Capabilities capabilities) {
        return Gl46Support.getUnsupportedReason(capabilities);
    }

    private static String getGl41MetalUnsupportedReason() {
        return Gl41MetalSupport.getUnsupportedReason();
    }
}
