package me.cortex.voxy.client.core.rendering.geometry;

public interface GeometryData {
  int getSectionCount();

  void free();

  long getMaxCapacity();
}
