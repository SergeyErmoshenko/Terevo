import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.channels.*;
import java.nio.file.*;

public class LockParent3 {
    public static void main(String[] args) throws Exception {
        Path p = Path.of(args[0]);
        String javaHome = System.getProperty("java.home") + "/bin/java";
        Process proc = new ProcessBuilder(javaHome, "-cp", args[1], "LockChild", args[0])
            .redirectErrorStream(true).start();
        BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        String line = r.readLine();
        System.out.println("from child: " + line);

        long killStart = System.nanoTime();
        proc.destroyForcibly();

        // Poll lock availability concurrently, independent of waitFor().
        Thread poller = new Thread(() -> {
            try {
                for (int i = 0; i < 240; i++) {
                    FileChannel ch = FileChannel.open(p, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    FileLock lock = ch.tryLock();
                    if (lock != null) {
                        long elapsedMs = (System.nanoTime() - killStart) / 1_000_000;
                        System.out.println("LOCK FREED after " + elapsedMs + "ms since destroyForcibly(), attempt " + i);
                        lock.release();
                        ch.close();
                        return;
                    }
                    ch.close();
                    Thread.sleep(500);
                }
                System.out.println("lock never freed after 240 attempts (~120s)");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        poller.setDaemon(true);
        poller.start();

        boolean exited = proc.waitFor(120, java.util.concurrent.TimeUnit.SECONDS);
        long killMs = (System.nanoTime() - killStart) / 1_000_000;
        System.out.println("waitFor(120s) returned=" + exited + " after " + killMs + "ms, isAlive=" + proc.isAlive());

        poller.join(125000);
    }
}
