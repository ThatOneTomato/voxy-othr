package me.cortex.voxy.client.compat;

import java.nio.file.Path;

/** NeoForge implementation: flashback is fabric-only. */
final class FlashbackCompatImpl {
  private FlashbackCompatImpl() {}

  static Path getReplayStoragePath() {
    return null;
  }
}
