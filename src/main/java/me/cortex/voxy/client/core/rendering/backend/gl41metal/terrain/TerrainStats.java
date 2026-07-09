package me.cortex.voxy.client.core.rendering.backend.gl41metal.terrain;

public record TerrainStats(
    int residentSections,
    long geometryBytes,
    long uploadedSections,
    long removedSections,
    long uploadedModels,
    long uploadedBiomes,
    long uploadedGeometryBytes,
    int validationResidentSections,
    int validationQuadRecords,
    int validationModelsReferenced,
    int validationMissingModels,
    int validationInvalidOffsets,
    int validationInvalidModelIds,
    long uploadedNodes,
    long uploadedTopNodes,
    long removedTopNodes,
    int topNodeCount,
    int traversalVisitedNodes,
    int traversalWorklistItems,
    int traversalRequestsEmitted,
    int traversalQuadCount,
    int traversalRequestsReady,
    int traversalWorklistCounter,
    int traversalCapacityFallbacks,
    int pendingRequests,
    int rasterInputQuads,
    int rasterProcessedQuads,
    int rasterOpaqueQuads,
    int rasterSkippedQuads,
    int rasterPixelsWritten,
    int rasterMissingModels,
    int rasterClippedQuads,
    int rasterWorkItems) {
  static TerrainStats fromNative(long[] values) {
    if (values.length < 33) {
      return new TerrainStats(
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0);
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
        (int) values[8],
        (int) values[9],
        (int) values[10],
        (int) values[11],
        (int) values[12],
        values[13],
        values[14],
        values[15],
        (int) values[16],
        (int) values[17],
        (int) values[18],
        (int) values[19],
        (int) values[20],
        (int) values[21],
        (int) values[22],
        (int) values[32],
        (int) values[23],
        (int) values[24],
        (int) values[25],
        (int) values[26],
        (int) values[27],
        (int) values[28],
        (int) values[29],
        (int) values[30],
        (int) values[31]);
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
        + ", uploadedModels="
        + this.uploadedModels
        + ", uploadedBiomes="
        + this.uploadedBiomes
        + ", validationResident="
        + this.validationResidentSections
        + ", validationQuads="
        + this.validationQuadRecords
        + ", validationModels="
        + this.validationModelsReferenced
        + ", missingModels="
        + this.validationMissingModels
        + ", invalidOffsets="
        + this.validationInvalidOffsets
        + ", invalidModelIds="
        + this.validationInvalidModelIds
        + ", topNodes="
        + this.topNodeCount
        + ", uploadedNodes="
        + this.uploadedNodes
        + ", traversalVisited="
        + this.traversalVisitedNodes
        + ", worklist="
        + this.traversalWorklistCounter
        + ", capacityFallbacks="
        + this.traversalCapacityFallbacks
        + ", requests="
        + this.traversalRequestsReady
        + ", emittedRequests="
        + this.traversalRequestsEmitted
        + ", traversalQuads="
        + this.traversalQuadCount
        + ", rasterInputQuads="
        + this.rasterInputQuads
        + ", rasterProcessedQuads="
        + this.rasterProcessedQuads
        + ", rasterOpaqueQuads="
        + this.rasterOpaqueQuads
        + ", rasterSkippedQuads="
        + this.rasterSkippedQuads
        + ", rasterPixels="
        + this.rasterPixelsWritten
        + ", rasterMissingModels="
        + this.rasterMissingModels
        + ", rasterClippedQuads="
        + this.rasterClippedQuads;
  }
}
