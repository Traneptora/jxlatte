package com.thebombzen.jxlatte.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TaskList<T> {

    private List<List<CompletableFuture<? extends T>>> tasks;


    public TaskList() {
        this(1);
    }

    public TaskList(int bins) {
        tasks = new ArrayList<>(bins);
        for (int i = 0; i < bins; i++)
            tasks.add(new ArrayList<>());
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
        tasks.get(bin).add(CompletableFuture.supplyAsync(s.uncheck()));
    }

    public List<T> collect(int bin) {
        List<T> results = new ArrayList<>();
        for (CompletableFuture<? extends T> future : tasks.get(bin)) {
            results.add(FunctionalHelper.join(future));
        }
        tasks.get(bin).clear();
        return results;
    }

    public List<T> collect() {
        List<T> results = new ArrayList<>();
        for (int bin = 0; bin < tasks.size(); bin++) {
            results.addAll(collect(bin));
        }
        return results;
    }
}
