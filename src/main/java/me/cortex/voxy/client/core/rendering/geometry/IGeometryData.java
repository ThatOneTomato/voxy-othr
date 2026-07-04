package me.cortex.voxy.client.core.rendering.geometry;

public interface IGeometryData {
    int getSectionCount();
    void free();
    long getMaxCapacity();
}
