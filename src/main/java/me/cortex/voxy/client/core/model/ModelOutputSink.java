package me.cortex.voxy.client.core.model;

public interface ModelOutputSink extends AutoCloseable {
  void uploadModel(BakedModelPayload payload);

  default void uploadBiomeData(BiomeModelPayload payload) {}

  @Override
  default void close() {}
}
