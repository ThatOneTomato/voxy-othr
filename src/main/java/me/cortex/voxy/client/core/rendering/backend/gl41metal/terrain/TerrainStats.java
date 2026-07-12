package me.cortex.voxy.client.core.rendering.backend.gl41metal.terrain;

record TerrainStats(
    int residentSections,
    long geometryBytes,
    long uploadedSections,
    long removedSections,
    long uploadedNodes,
    long uploadedTopNodes,
    long removedTopNodes,
    int topNodeCount,
    int pendingRequests) {
  static TerrainStats fromNative(long[] values) {
    if (values.length < 9) {
      return new TerrainStats(0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
    return new TerrainStats(
        (int) values[0],
        values[1],
        values[2],
        values[3],
        values[4],
        values[5],
        values[6],
        (int) values[7],
        (int) values[8]);
  }

  String compact() {
    return "resident="
        + this.residentSections
        + ", geometryBytes="
        + this.geometryBytes
        + ", uploadedSections="
        + this.uploadedSections
        + ", removedSections="
        + this.removedSections
        + ", uploadedNodes="
        + this.uploadedNodes
        + ", topNodes="
        + this.topNodeCount
        + ", uploadedTopNodes="
        + this.uploadedTopNodes
        + ", removedTopNodes="
        + this.removedTopNodes
        + ", pendingRequests="
        + this.pendingRequests;
  }
}
