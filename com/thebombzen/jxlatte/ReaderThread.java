package com.thebombzen.jxlatte;

import java.io.InputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ReaderThread extends Thread {

    private InputStream in;
    private BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
    private Throwable error = null;

    public ReaderThread(InputStream in) {
        this.in = in;
    }

    public BlockingQueue<byte[]> getQueue() {
        return queue;
    }

    public Throwable getLastError() {
        return error;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[4096];
        try {
            while (true) {
                int count = in.read(buffer);
                if (count > 0) {
                    byte[] buf = new byte[count];
                    System.arraycopy(buffer, 0, buf, 0, count);
                    queue.put(buf);
                } else {
                    queue.put(new byte[0]);
                    break;
                }
            }
        } catch (Throwable ex) {
            this.error = ex;
        } finally {
            try {
                in.close();
            } catch (Throwable ex) {
                if (error == null)
                    error = ex;
            }
        }
    }
}
