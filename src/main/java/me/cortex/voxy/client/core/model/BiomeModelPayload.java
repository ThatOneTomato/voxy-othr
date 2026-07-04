package me.cortex.voxy.client.core.model;

import me.cortex.voxy.common.util.MemoryBuffer;

public record BiomeModelPayload(
    MemoryBuffer biomeColourBuffer, MemoryBuffer modelBiomeIndexPairs) {}
