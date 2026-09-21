package com.linkvideo.parser;

import org.junit.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class BatchRunnerTest {
    @Test public void failureInOneItemDoesNotPreventLaterItemsFromRunning() {
        List<Integer> visited = new ArrayList<>();
        List<Integer> failures = new ArrayList<>();
        BatchRunner.run(Arrays.asList(0, 1, 2), index -> {
            visited.add(index);
            if (index == 1) throw new IOException("该项失败");
        }, (index, error) -> failures.add(index));
        assertEquals(Arrays.asList(0, 1, 2), visited);
        assertEquals(Arrays.asList(1), failures);
    }

    @Test public void interruptionBeforeStartDoesNotRunAnyItem() {
        List<Integer> visited = new ArrayList<>();
        Thread.currentThread().interrupt();
        try {
            BatchRunner.run(Arrays.asList(0, 1), visited::add, (index, error) -> fail());
            assertTrue(visited.isEmpty());
        } finally { Thread.interrupted(); }
    }

    @Test public void interruptionAfterOneItemStopsRemainingItems() {
        List<Integer> visited = new ArrayList<>();
        try {
            BatchRunner.run(Arrays.asList(0, 1, 2), index -> {
                visited.add(index);
                Thread.currentThread().interrupt();
            }, (index, error) -> fail());
            assertEquals(Arrays.asList(0), visited);
        } finally { Thread.interrupted(); }
    }

    @Test public void interruptedExceptionCancelsInsteadOfTreatingItAsAnOrdinaryFailure() {
        List<Integer> visited = new ArrayList<>();
        try {
            BatchRunner.run(Arrays.asList(0, 1), index -> {
                visited.add(index);
                throw new InterruptedException();
            }, (index, error) -> fail());
            assertEquals(Arrays.asList(0), visited);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
}
