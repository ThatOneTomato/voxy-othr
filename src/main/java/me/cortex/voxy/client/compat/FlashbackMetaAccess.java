package me.cortex.voxy.client.compat;

import java.io.File;

public interface FlashbackMetaAccess {
  void setVoxyPath(File path);

  File getVoxyPath();
}
