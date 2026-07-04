package me.cortex.voxy.client.core.rendering.backend;

import me.cortex.voxy.common.thread.ServiceManager;
import me.cortex.voxy.common.world.WorldEngine;

public record BackendContext(
    WorldEngine world, ServiceManager serviceManager, RenderBackendSelection selection) {}
