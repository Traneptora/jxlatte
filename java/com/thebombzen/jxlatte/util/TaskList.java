package com.thebombzen.jxlatte.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import com.thebombzen.jxlatte.util.functional.ExceptionalFunction;
import com.thebombzen.jxlatte.util.functional.ExceptionalIntBiConsumer;
import com.thebombzen.jxlatte.util.functional.ExceptionalIntConsumer;
import com.thebombzen.jxlatte.util.functional.ExceptionalRunnable;
import com.thebombzen.jxlatte.util.functional.ExceptionalSupplier;
import com.thebombzen.jxlatte.util.functional.FunctionalHelper;

public class TaskList<T> {

    private List<CompletableFuture<? extends T>>[] tasks;
    private ExecutorService threadPool;

    public TaskList(ExecutorService threadPool) {
        this(threadPool, 1);
    }

    public TaskList(ExecutorService threadPool, int bins) {
        this.threadPool = threadPool;
        @SuppressWarnings("unchecked")
        List<CompletableFuture<? extends T>>[] tasks = new List[bins];
        this.tasks = tasks;
        for (int i = 0; i < bins; i++)
            tasks[i] = new ArrayList<>();
    }

    public void submit(ExceptionalRunnable r) {
        submit(0, r);
    }

    public void submit(int bin, ExceptionalRunnable r) {
        submit(bin, r.supply());
    }

    public void submit(ExceptionalSupplier<? extends T> s) {
        submit(0, s);
    }

    public void submit(int bin, ExceptionalSupplier<? extends T> s) {
        tasks[bin].add(CompletableFuture.supplyAsync(s, threadPool));
    }

    public <U> void submit(CompletableFuture<? extends U> supplier, ExceptionalFunction<? super U, ? extends T> f) {
        submit(0, supplier, f);
    }

    public <U> void submit(int bin, CompletableFuture<? extends U> supplier, ExceptionalFunction<? super U, ? extends T> f) {
        tasks[bin].add(supplier.thenApplyAsync(f, threadPool));
    }

    public void submit(int bin, int a, int b, ExceptionalIntBiConsumer consumer) {
        submit(bin, () -> consumer.consume(a, b));
    }

    public void submit(int a, int b, ExceptionalIntBiConsumer consumer) {
        submit(0, a, b, consumer);
    }

    public void submit(int bin, int a, ExceptionalIntConsumer consumer) {
        submit(bin, () -> consumer.consume(a));
    }

    public void submit(int a, ExceptionalIntConsumer consumer) {
        submit(0, a, consumer);
    }

    public List<T> collect(int bin) {
        List<T> results = new ArrayList<>();
        for (CompletableFuture<? extends T> future : tasks[bin])
            results.add(FunctionalHelper.join(future));
        tasks[bin].clear();
        return results;
    }

    public List<T> collect() {
        List<T> results = new ArrayList<>();
        for (int bin = 0; bin < tasks.length; bin++)
            results.addAll(collect(bin));
        return results;
    }
}
