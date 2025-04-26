package com.aurorain.shardmaster.web;

import com.aurorain.shardmaster.ShardClient;
import io.vertx.core.shareddata.impl.SharedDataImpl;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

@Slf4j
@Component
public class CommandExecutor {
    private final BlockingQueue<Runnable> commandQueue = new LinkedBlockingQueue<>();
    private final ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();

    @PostConstruct
    public void init() {
        singleThreadExecutor.execute(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    log.info("Task executing in thread: {} (ID: {})",
                            Thread.currentThread().getName(),
                            Thread.currentThread().getId());
                    Runnable command = commandQueue.take();
                    command.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Command executor interrupted", e);
                } catch (Exception e) {
                    log.error("Error executing command", e);
                }
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        singleThreadExecutor.shutdownNow();
    }

    public <T> T execute(Callable<T> task) throws Exception {
        FutureTask<T> futureTask = new FutureTask<>(task);
        commandQueue.put(futureTask);
        return futureTask.get(); // 阻塞直到任务完成
    }
}