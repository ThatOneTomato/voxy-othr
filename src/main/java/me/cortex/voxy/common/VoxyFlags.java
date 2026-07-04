package me.cortex.voxy.common;

/** JVM-property backed debug/verification flags shared by every layer. */
public final class VoxyFlags {
  public static final boolean IS_MINE_IN_ABYSS = false;

  private VoxyFlags() {}

  // This is hardcoded like this because people do not understand what they are doing
  public static boolean isVerificationFlagOn(String name) {
    return isVerificationFlagOn(name, false);
  }

  public static boolean isVerificationFlagOn(String name, boolean defaultOn) {
    return System.getProperty("voxy." + name, defaultOn ? "true" : "false").equals("true");
  }
}
