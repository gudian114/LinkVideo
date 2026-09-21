package com.linkvideo.parser;

import java.util.List;

final class BatchRunner {
    interface Task { void run(int index) throws Exception; }
    interface Failure { void failed(int index, Exception error); }

    private BatchRunner() { }

    static void run(List<Integer> targets, Task task, Failure failure) {
        for (int index : targets) {
            if (Thread.currentThread().isInterrupted()) return;
            try {
                task.run(index);
            } catch (Exception error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                if (Thread.currentThread().isInterrupted()) return;
                failure.failed(index, error);
            }
        }
    }
}
