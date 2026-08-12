package knowflow.sanjin.modules.conversation.service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 在独立线程池中执行一次 Generation，提供取消（中断）与总超时兜底。
 *
 * <p>流式场景用 {@code cancelled} 标志区分用户取消与自然超时；超时后中断执行线程， {@code Subscription.cancel()} 会随之关闭底层 OkHttp
 * 流。取消标志对执行线程可见， 每次写事件与读取 chunk 后都检查，保证取消与超时能被及时响应。
 */
@Component
public class GenerationExecutor {

  private static final Logger log = LoggerFactory.getLogger(GenerationExecutor.class);

  private static final ThreadFactory THREAD_FACTORY =
      new ThreadFactory() {
        private final AtomicLong n = new AtomicLong();

        @Override
        public Thread newThread(Runnable r) {
          Thread t = new Thread(r, "generation-" + n.incrementAndGet());
          t.setDaemon(true);
          return t;
        }
      };

  private final ExecutorService executor;
  private final ScheduledExecutorService scheduler;
  private final Duration totalTimeout;

  /** assistantMessageId -> 该次任务的取消标志与线程。 */
  private final Map<Long, ActiveTask> tasks = new ConcurrentHashMap<>();

  public GenerationExecutor(GenerationProperties properties) {
    this.totalTimeout = properties.getTotalTimeout();
    this.executor =
        Executors.newFixedThreadPool(Math.max(1, properties.getMaxConcurrency()), THREAD_FACTORY);
    this.scheduler = Executors.newScheduledThreadPool(1, THREAD_FACTORY);
  }

  /** 提交一次生成任务；返回可取消标志引用（true 表示已取消/超时）。 */
  public AtomicReference<Boolean> submit(long assistantMessageId, Runnable task) {
    AtomicReference<Boolean> cancelled = new AtomicReference<>(false);
    ActiveTask at = new ActiveTask(cancelled);
    tasks.put(assistantMessageId, at);

    ScheduledFuture<?> timeout =
        scheduler.schedule(
            () -> {
              at.cancelled.set(true);
              ActiveTask cur = tasks.remove(assistantMessageId);
              if (cur != null) {
                Thread t = cur.thread;
                if (t != null) {
                  t.interrupt();
                }
              }
            },
            totalTimeout.toMillis(),
            TimeUnit.MILLISECONDS);

    executor.execute(
        () -> {
          at.thread = Thread.currentThread();
          try {
            // 总是运行任务：若取消恰在启动前触发，streamer 通过 isCancelled 在起点感知并走取消终结路径，
            // 避免 finalizer 永不执行导致 active slot 泄漏。
            task.run();
          } catch (Throwable t) {
            // 超时取消/中断时执行线程被打断；执行体内部负责把失败状态写库。
            log.debug("Generation task {} interrupted", assistantMessageId);
            Thread.currentThread().interrupt();
          } finally {
            timeout.cancel(false);
            tasks.remove(assistantMessageId);
            at.thread = null;
          }
        });
    return at.cancelled;
  }

  /** 请求取消：置位取消标志并中断执行线程；返回 false 表示本进程已没有对应任务。 */
  public boolean cancel(long assistantMessageId) {
    ActiveTask at = tasks.get(assistantMessageId);
    if (at == null) {
      return false;
    }
    at.cancelled.set(true);
    Thread t = at.thread;
    if (t != null) {
      t.interrupt();
    }
    return true;
  }

  /** 查询指定 generation 是否已被取消/超时（供 streamer 在每个事件前检查）。 */
  public boolean isCancelled(long assistantMessageId) {
    ActiveTask at = tasks.get(assistantMessageId);
    return at != null && at.cancelled.get();
  }

  private static final class ActiveTask {
    final AtomicReference<Boolean> cancelled;
    volatile Thread thread;

    ActiveTask(AtomicReference<Boolean> cancelled) {
      this.cancelled = cancelled;
    }
  }
}
