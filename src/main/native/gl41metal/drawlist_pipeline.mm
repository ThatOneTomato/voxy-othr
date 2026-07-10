#include "gl41metal_internal.h"

using namespace gl41metal;

namespace gl41metal {

namespace {

constexpr uint64_t MAX_QUADS_PER_RANGE = 16380;

struct WorkItemHost {
  uint32_t meshId;
  uint32_t quadBase;
  uint32_t reserved;
  uint32_t lodAndQuadCount;
};

struct SectionMetaHost {
  uint32_t a[4];
  uint32_t b[4];
};

uint64_t absInt64(int64_t value) {
  return value < 0 ? static_cast<uint64_t>(-value) : static_cast<uint64_t>(value);
}

int32_t signExtend(uint32_t value, uint32_t bits) {
  uint32_t shift = 32u - bits;
  return static_cast<int32_t>(value << shift) >> shift;
}

int32_t extractSectionX(const SectionMetaHost& section) {
  return static_cast<int32_t>(section.a[1]) >> 4;
}

int32_t extractSectionY(const SectionMetaHost& section) {
  return static_cast<int32_t>(section.a[0]) >> 28;
}

int32_t extractSectionZ(const SectionMetaHost& section) {
  uint32_t packed = (section.a[0] & ((1u << 20u) - 1u)) | ((section.a[1] >> 28u) << 20u);
  return signExtend(packed, 24);
}

uint32_t extractDetail(const SectionMetaHost& section) { return section.a[0] >> 28u; }

uint32_t groupCount(const SectionMetaHost& section, uint32_t group) {
  switch (group) {
    case 0:
      return section.b[0] & 0xffffu;
    case 1:
      return section.b[0] >> 16u;
    case 2:
      return section.b[1] & 0xffffu;
    case 3:
      return section.b[1] >> 16u;
    case 4:
      return section.b[2] & 0xffffu;
    case 5:
      return section.b[2] >> 16u;
    case 6:
      return section.b[3] & 0xffffu;
    default:
      return section.b[3] >> 16u;
  }
}

uint32_t sectionFingerprint(const SectionMetaHost& section) {
  return (section.a[0] ^ (section.a[1] * 0x9e3779b9u)) & 0x00ffffffu;
}

struct RangeBuildStats {
  uint64_t workItemCount = 0;
  uint64_t rawRanges = 0;
  uint64_t mergedRanges = 0;
  uint64_t rangeQuads = 0;
  uint64_t acceptedQuadHint = 0;
  uint64_t maxRawRangeQuads = 0;
  uint64_t maxMergedRangeQuads = 0;
  uint64_t visibleWorkItems = 0;
  uint64_t writtenRanges = 0;
  uint64_t overflowRanges = 0;
  uint64_t snapshotMismatchItems = 0;
};

struct TranslucentRangeRecord {
  uint32_t sourceOffset;
  uint32_t quadCount;
  uint32_t bucket;
  uint32_t meshId;
  uint64_t distance;
  int32_t sectionX;
  int32_t sectionY;
  int32_t sectionZ;
  uint32_t detail;
};

void emitMergedRange(uint64_t start, uint64_t end, int32_t* counts, uintptr_t* indices,
                     int32_t* baseVertices, uint64_t capacity, RangeBuildStats* stats) {
  if (start == UINT64_MAX || end <= start) {
    return;
  }
  for (uint64_t chunkStart = start; chunkStart < end; chunkStart += MAX_QUADS_PER_RANGE) {
    uint64_t quadCount = std::min<uint64_t>(MAX_QUADS_PER_RANGE, end - chunkStart);
    stats->mergedRanges++;
    stats->maxMergedRangeQuads = std::max<uint64_t>(stats->maxMergedRangeQuads, quadCount);
    if (counts == nullptr || indices == nullptr || baseVertices == nullptr) {
      continue;
    }
    uint64_t outputIndex = stats->writtenRanges;
    if (outputIndex >= capacity) {
      stats->overflowRanges++;
      continue;
    }
    counts[outputIndex] = static_cast<int32_t>(quadCount * 6u);
    indices[outputIndex] = 0;
    baseVertices[outputIndex] = static_cast<int32_t>(chunkStart * 4u);
    stats->writtenRanges++;
  }
}

RangeBuildStats buildOpaqueRangesFromWorklist(TerrainResources* terrain, FrameResources* frame,
                                              int32_t* counts, uintptr_t* indices,
                                              int32_t* baseVertices, uint64_t capacity) {
  RangeBuildStats stats;
  const uint32_t* worklistCounter =
      reinterpret_cast<const uint32_t*>([frame->worklistCounter contents]);
  const WorkItemHost* worklist = reinterpret_cast<const WorkItemHost*>([frame->worklist contents]);
  const SectionMetaHost* sections =
      reinterpret_cast<const SectionMetaHost*>([terrain->sectionMetadata contents]);
  stats.workItemCount =
      std::min<uint32_t>(worklistCounter[0], static_cast<uint32_t>(terrain->maxWorklistItems));
  stats.acceptedQuadHint = worklistCounter[1];

  for (uint64_t i = 0; i < stats.workItemCount; i++) {
    const WorkItemHost& item = worklist[i];
    if ((item.lodAndQuadCount & 0x00ffffffu) == 0 ||
        item.meshId >= static_cast<uint32_t>(terrain->maxSections)) {
      continue;
    }
    const SectionMetaHost& section = sections[item.meshId];
    uint32_t lodLevel = item.lodAndQuadCount >> 24u;
    uint32_t sourceOffset = section.a[3];
    uint32_t groupMask = item.reserved & 0xffu;
    bool snapshotMatches = lodLevel == extractDetail(section) && item.quadBase == sourceOffset &&
                           (item.reserved >> 8u) == sectionFingerprint(section);
    if (!snapshotMatches) {
      stats.snapshotMismatchItems++;
      groupMask = 0xfeu;
    }
    uint64_t mergedStart = UINT64_MAX;
    uint64_t mergedEnd = UINT64_MAX;
    bool itemVisible = false;
    for (uint32_t group = 0; group < 8; group++) {
      uint32_t count = groupCount(section, group);
      bool visible = count > 0 && (groupMask & (1u << group)) != 0u;
      if (visible) {
        itemVisible = true;
        stats.rawRanges++;
        stats.rangeQuads += count;
        stats.maxRawRangeQuads = std::max<uint64_t>(stats.maxRawRangeQuads, count);
        uint64_t start = sourceOffset;
        uint64_t end = start + count;
        if (mergedStart == UINT64_MAX) {
          mergedStart = start;
          mergedEnd = end;
        } else if (start == mergedEnd) {
          mergedEnd = end;
        } else {
          emitMergedRange(mergedStart, mergedEnd, counts, indices, baseVertices, capacity, &stats);
          mergedStart = start;
          mergedEnd = end;
        }
      }
      sourceOffset += count;
    }
    emitMergedRange(mergedStart, mergedEnd, counts, indices, baseVertices, capacity, &stats);
    if (itemVisible) {
      stats.visibleWorkItems++;
    }
  }
  if (counts == nullptr || indices == nullptr || baseVertices == nullptr) {
    stats.writtenRanges = stats.mergedRanges;
  }
  return stats;
}

RangeBuildStats buildTranslucentRangesFromWorklist(TerrainResources* terrain, FrameResources* frame,
                                                   int32_t* counts, uintptr_t* indices,
                                                   int32_t* baseVertices, uint64_t capacity) {
  RangeBuildStats stats;
  const uint32_t* worklistCounter =
      reinterpret_cast<const uint32_t*>([frame->translucentWorklistCounter contents]);
  const WorkItemHost* worklist =
      reinterpret_cast<const WorkItemHost*>([frame->translucentWorklist contents]);
  const SectionMetaHost* sections =
      reinterpret_cast<const SectionMetaHost*>([terrain->sectionMetadata contents]);
  const SceneUniformHost* scene =
      reinterpret_cast<const SceneUniformHost*>([frame->sceneUniform contents]);
  stats.workItemCount =
      std::min<uint32_t>(worklistCounter[0], static_cast<uint32_t>(terrain->maxWorklistItems));
  stats.acceptedQuadHint = worklistCounter[1];
  if (stats.workItemCount == 0) {
    return stats;
  }

  std::vector<TranslucentRangeRecord> records;
  records.reserve(stats.workItemCount);
  for (uint64_t i = 0; i < stats.workItemCount; i++) {
    const WorkItemHost& item = worklist[i];
    uint32_t quadCount = item.lodAndQuadCount & 0x00ffffffu;
    if (quadCount == 0 || item.meshId >= static_cast<uint32_t>(terrain->maxSections)) {
      continue;
    }
    const SectionMetaHost& section = sections[item.meshId];
    uint32_t sourceOffset = section.a[3];
    if (sourceOffset == UINT32_MAX) {
      continue;
    }
    int32_t sectionX = extractSectionX(section);
    int32_t sectionY = extractSectionY(section);
    int32_t sectionZ = extractSectionZ(section);
    uint32_t detail = extractDetail(section);
    int64_t relativeX = static_cast<int64_t>(sectionX) - (scene->baseSectionFrame[0] >> detail);
    int64_t relativeY = static_cast<int64_t>(sectionY) - (scene->baseSectionFrame[1] >> detail);
    int64_t relativeZ = static_cast<int64_t>(sectionZ) - (scene->baseSectionFrame[2] >> detail);
    uint64_t dist = (absInt64(relativeX) + absInt64(relativeY) + absInt64(relativeZ)) << detail;
    uint32_t bucket = static_cast<uint32_t>(TRANSLUCENT_BUCKET_COUNT - 1) -
                      static_cast<uint32_t>(std::min<uint64_t>(
                          dist, static_cast<uint64_t>(TRANSLUCENT_BUCKET_COUNT - 1)));
    records.push_back(
        {sourceOffset, quadCount, bucket, item.meshId, dist, sectionX, sectionY, sectionZ, detail});
    stats.rangeQuads += quadCount;
    stats.maxRawRangeQuads = std::max<uint64_t>(stats.maxRawRangeQuads, quadCount);
  }
  stats.rawRanges = records.size();
  stats.visibleWorkItems = records.size();
  if (records.empty()) {
    return stats;
  }

  std::sort(records.begin(), records.end(),
            [](const TranslucentRangeRecord& a, const TranslucentRangeRecord& b) {
              if (a.bucket != b.bucket) return a.bucket < b.bucket;
              // GL46 buckets clamp all very-distant sections into bucket 0. Refine those ties by
              // the same Manhattan distance so the draw order is stable instead of inheriting Metal
              // traversal's atomic append order.
              if (a.distance != b.distance) return a.distance > b.distance;
              if (a.detail != b.detail) return a.detail > b.detail;
              if (a.sectionX != b.sectionX) return a.sectionX < b.sectionX;
              if (a.sectionY != b.sectionY) return a.sectionY < b.sectionY;
              if (a.sectionZ != b.sectionZ) return a.sectionZ < b.sectionZ;
              return a.meshId < b.meshId;
            });

  for (const TranslucentRangeRecord& record : records) {
    emitMergedRange(record.sourceOffset,
                    static_cast<uint64_t>(record.sourceOffset) + record.quadCount, counts, indices,
                    baseVertices, capacity, &stats);
  }
  if (counts == nullptr || indices == nullptr || baseVertices == nullptr) {
    stats.writtenRanges = stats.mergedRanges;
  }
  return stats;
}

}  // namespace

}  // namespace gl41metal

extern "C" {

JNIEXPORT jint JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_buildOpaqueRanges(
    JNIEnv* env, jclass, jlong handle, jint slotIndex, jlong countsAddress, jlong indicesAddress,
    jlong baseVerticesAddress, jint capacity, jlong countersAddress) {
  NativeContext* context = requireContext(env, handle);
  if (context == nullptr) {
    return 0;
  }
  if (slotIndex < 0 || slotIndex >= static_cast<jint>(context->slots.size())) {
    throwJava(env, "GL41Metal range builder received an invalid slot index");
    return 0;
  }
  if (capacity <= 0 || countsAddress == 0 || indicesAddress == 0 || baseVerticesAddress == 0 ||
      countersAddress == 0) {
    throwJava(env, "GL41Metal range builder received invalid output arguments");
    return 0;
  }
  TerrainResources* terrain = context->terrain.get();
  Slot& slot = context->slots[slotIndex];
  FrameResources* frame = slot.frame.get();
  if (terrain == nullptr || frame == nullptr || frame->worklistCounter == nil ||
      frame->worklist == nil || frame->sceneUniform == nil || terrain->sectionMetadata == nil) {
    return 0;
  }

  RangeBuildStats stats = buildOpaqueRangesFromWorklist(
      terrain, frame, reinterpret_cast<int32_t*>(countsAddress),
      reinterpret_cast<uintptr_t*>(indicesAddress), reinterpret_cast<int32_t*>(baseVerticesAddress),
      static_cast<uint64_t>(capacity));
  uint64_t* counters = reinterpret_cast<uint64_t*>(countersAddress);
  counters[0] = stats.writtenRanges;
  counters[1] = stats.overflowRanges;
  counters[2] = stats.rangeQuads;
  counters[3] = stats.maxMergedRangeQuads;
  counters[4] = stats.workItemCount;
  counters[5] = stats.rawRanges;
  counters[6] = stats.mergedRanges;
  counters[7] = stats.visibleWorkItems;
  counters[8] = stats.snapshotMismatchItems;
  return static_cast<jint>(std::min<uint64_t>(stats.writtenRanges, static_cast<uint64_t>(INT_MAX)));
}

JNIEXPORT jint JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_buildTranslucentRanges(
    JNIEnv* env, jclass, jlong handle, jint slotIndex, jlong countsAddress, jlong indicesAddress,
    jlong baseVerticesAddress, jint capacity, jlong countersAddress) {
  NativeContext* context = requireContext(env, handle);
  if (context == nullptr) {
    return 0;
  }
  if (slotIndex < 0 || slotIndex >= static_cast<jint>(context->slots.size())) {
    throwJava(env, "GL41Metal translucent range builder received an invalid slot index");
    return 0;
  }
  if (capacity <= 0 || countsAddress == 0 || indicesAddress == 0 || baseVerticesAddress == 0 ||
      countersAddress == 0) {
    throwJava(env, "GL41Metal translucent range builder received invalid output arguments");
    return 0;
  }
  TerrainResources* terrain = context->terrain.get();
  Slot& slot = context->slots[slotIndex];
  FrameResources* frame = slot.frame.get();
  if (terrain == nullptr || frame == nullptr || frame->translucentWorklistCounter == nil ||
      frame->translucentWorklist == nil || frame->sceneUniform == nil ||
      terrain->sectionMetadata == nil) {
    return 0;
  }

  RangeBuildStats stats = buildTranslucentRangesFromWorklist(
      terrain, frame, reinterpret_cast<int32_t*>(countsAddress),
      reinterpret_cast<uintptr_t*>(indicesAddress), reinterpret_cast<int32_t*>(baseVerticesAddress),
      static_cast<uint64_t>(capacity));
  uint64_t* counters = reinterpret_cast<uint64_t*>(countersAddress);
  counters[0] = stats.writtenRanges;
  counters[1] = stats.overflowRanges;
  counters[2] = stats.rangeQuads;
  counters[3] = stats.workItemCount;
  counters[4] = stats.mergedRanges;
  counters[5] = stats.maxMergedRangeQuads;
  counters[6] = stats.visibleWorkItems;
  return static_cast<jint>(std::min<uint64_t>(stats.writtenRanges, static_cast<uint64_t>(INT_MAX)));
}

}  // extern "C"
