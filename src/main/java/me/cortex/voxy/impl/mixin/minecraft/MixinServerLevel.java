package me.cortex.voxy.impl.mixin.minecraft;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.function.BooleanSupplier;
import me.cortex.voxy.common.platform.PlatformAccess;
import me.cortex.voxy.impl.compat.sable.SableLodChunkManager;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerLevel.class, priority = 1100)
public abstract class MixinServerLevel {
  // SableLodChunkManager's bytecode references Sable types directly, so merely class-loading it
  // throws a NoClassDefFoundError (during verification, at the call site) when Sable is absent.
  @Unique
  private static final boolean VOXY$SABLE_INSTALLED = PlatformAccess.get().isModLoaded("sable");

  @Unique private final LongSet voxy$sableTrackedChunks = new LongOpenHashSet();
  @Unique private final LongSet voxy$sableTrackedHoldingChunks = new LongOpenHashSet();

  @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
  private void voxy$keepSableSublevelsLoaded(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
    if (!VOXY$SABLE_INSTALLED) return;
    SableLodChunkManager.updateTickets(
        (ServerLevel) (Object) this,
        this.voxy$sableTrackedChunks,
        this.voxy$sableTrackedHoldingChunks);
  }

  @Inject(method = "close", at = @At("HEAD"))
  private void voxy$releaseSableTickets(CallbackInfo ci) {
    if (!VOXY$SABLE_INSTALLED) return;
    SableLodChunkManager.clearTickets(
        (ServerLevel) (Object) this,
        this.voxy$sableTrackedChunks,
        this.voxy$sableTrackedHoldingChunks);
  }
}
