package me.cortex.voxy.client.core.rendering.geometry;

import me.cortex.voxy.client.core.rendering.building.BuiltSection;

import java.util.function.Consumer;

public interface GeometryManager {
    int uploadSection(BuiltSection section);
    int uploadReplaceSection(int oldId, BuiltSection section);
    void removeSection(int id);

    void downloadAndRemove(int id, Consumer<BuiltSection> callback);
}
