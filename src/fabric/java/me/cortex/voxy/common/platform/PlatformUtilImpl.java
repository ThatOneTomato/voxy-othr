package me.cortex.voxy.common.platform;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.record.FlashbackMeta;
import me.cortex.voxy.client.compat.IFlashbackMeta;
import me.cortex.voxy.common.Logger;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class PlatformUtilImpl implements PlatformUtil {
    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public Path getModRootPath(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(container -> container.getRootPaths().get(0))
                .orElse(null);
    }

    @Override
    public String getModVersion(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse(null);
    }

    @Override
    public Path getReplayStoragePath(boolean flashbackInstalled) {
        if (!flashbackInstalled) {
            return null;
        }
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer != null) {
            FlashbackMeta meta = replayServer.getMetadata();
            if (meta != null) {
                var path = ((IFlashbackMeta)meta).getVoxyPath();
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

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isDedicatedServer() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
    }
}
