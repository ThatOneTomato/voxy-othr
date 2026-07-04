package me.cortex.voxy.client.core.model;

import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.world.other.Mapper;

public class ModelBakerySubsystem {
  // Redo to just make it request the block faces with the async texture download stream which
  // basicly solves all the render stutter due to the baking

  private final ModelStore storage;
  public final ModelFactory factory;
  private final Mapper mapper;

  // Sink mode (gl41metal): bakes run on the render thread inside tick(), so bake requests are
  // queued here instead of being executed directly from worker threads.
  private final AtomicInteger blockIdCount = new AtomicInteger();
  private final ConcurrentLinkedDeque<Integer> blockIdQueue = new ConcurrentLinkedDeque<>();

  private final Thread processingThread;
  private volatile boolean isRunning = true;
  private volatile Throwable processingThreadException;

  public ModelBakerySubsystem(Mapper mapper) {
    this(mapper, null);
  }

  public ModelBakerySubsystem(Mapper mapper, ModelOutputSink outputSink) {
    this.mapper = mapper;
    this.storage = outputSink == null ? new ModelStore() : null;
    this.factory = new ModelFactory(mapper, this.storage, outputSink);
    this.processingThread =
        new Thread(
            () -> { // TODO replace this with something good/integrate it into the async processor
              // so that we just have less threads overall
              while (this.isRunning) {
                this.factory.processAllThings();
                try {
                  // TODO: replace with LockSupport.park();
                  Thread.sleep(10);
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                }
              }
            },
            "Model factory processor");
    this.processingThread.setUncaughtExceptionHandler(
        (t, e) -> {
          this.isRunning = false;
          if (e == null) {
            e = new RuntimeException("unhandled excpetion not added");
          }
          this.processingThreadException = e;
        });
    this.processingThread.start();
  }

  public void tick(long totalBudget) {
    if (this.processingThreadException != null) {
      Logger.error(
          this.processingThreadException.getStackTrace().toString(),
          this.processingThreadException);
      throw new RuntimeException(this.processingThreadException);
    }
    long start = System.nanoTime();
    this.factory.processUploads();

    // Sink mode: drain queued bake requests on the render thread within the frame budget
    Integer i = this.blockIdQueue.poll();
    if (i != null) {
      int j = 0;
      int fbBinding = glGetInteger(GL_FRAMEBUFFER_BINDING);

      do {
        this.factory.addEntry(i);
        j++;
        if (4 < j && (totalBudget < (System.nanoTime() - start) + 50_000)) break;
        i = this.blockIdQueue.poll();
      } while (i != null);

      // This is done here as stops needing to set then unset the fb in the thing 1000x
      glBindFramebuffer(GL_FRAMEBUFFER, fbBinding);
      this.blockIdCount.addAndGet(-j);
    }
  }

  public void shutdown() {
    this.isRunning = false;
    try {
      this.processingThread.join();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    this.factory.free();
    if (this.storage != null) {
      this.storage.free();
    }
  }

  // This is on this side only and done like this as only worker threads call this code
  private final ReentrantLock seenIdsLock = new ReentrantLock();
  private final ReentrantLock enqueueLock = new ReentrantLock();
  private final IntOpenHashSet seenIds =
      new IntOpenHashSet(6000); // TODO: move to a lock free concurrent hashmap

  public void requestBlockBake(int blockId) {
    if (this.mapper.getBlockStateCount() <= blockId) {
      Logger.error(
          "Error, got bakeing request for out of range state id. StateId: "
              + blockId
              + " max id: "
              + this.mapper.getBlockStateCount(),
          new Exception());
      return;
    }
    this.seenIdsLock.lock();
    if (!this.seenIds.add(blockId)) {
      this.seenIdsLock.unlock();
      return;
    }
    this.seenIdsLock.unlock();

    if (this.storage == null) {
      // Sink mode: defer to the render thread (GL offscreen bakery)
      this.blockIdQueue.add(blockId);
      this.blockIdCount.incrementAndGet();
      return;
    }

    this.enqueueLock.lock();
    this.factory.addEntry(blockId);
    this.enqueueLock.unlock();
  }

  public void addBiome(Mapper.BiomeEntry biomeEntry) {
    this.factory.addBiome(biomeEntry);
  }

  public void addDebugData(List<String> debug) {
    debug.add(
        String.format(
            "MQ/IF/MC: %04d, %03d, %04d",
            this.blockIdCount.get(),
            this.factory.getInflightCount(),
            this.factory.getBakedCount())); // Model bake queue/in flight/model baked count
    debug.add("MB mode: " + this.factory.getBakeryMode());
  }

  public ModelStore getStore() {
    if (this.storage == null) {
      throw new IllegalStateException("Model store is not available for sink-mode model bakery");
    }
    return this.storage;
  }

  public boolean areQueuesEmpty() {
    return this.blockIdCount.get() == 0 && this.factory.getInflightCount() == 0;
  }

  public int getProcessingCount() {
    return this.blockIdCount.get() + this.factory.getInflightCount();
  }
}
