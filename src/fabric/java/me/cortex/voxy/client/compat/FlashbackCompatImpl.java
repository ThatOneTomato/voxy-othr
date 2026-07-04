package me.cortex.voxy.client.compat;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.record.FlashbackMeta;
import java.nio.file.Path;
import me.cortex.voxy.common.Logger;

/** Fabric implementation: flashback only ships on fabric. Only called when flashback is loaded. */
final class FlashbackCompatImpl {
  private FlashbackCompatImpl() {}

  static Path getReplayStoragePath() {
    ReplayServer replayServer = Flashback.getReplayServer();
    if (replayServer != null) {
      FlashbackMeta meta = replayServer.getMetadata();
      if (meta != null) {
        var path = ((FlashbackMetaAccess) meta).getVoxyPath();
        if (path != null) {
          Logger.info("Flashback replay server exists and meta exists");
          if (path.exists()) {
            Logger.info("Flashback voxy path exists in filesystem, using this as lod data source");
            return path.toPath();
          } else {
            Logger.warn("Flashback meta had voxy path saved but path doesnt exist");
          }
        }
      }
    }
    return null;
  }
}
