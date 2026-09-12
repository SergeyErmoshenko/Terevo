import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public class HaltParent {
    public static void main(String[] args) throws Exception {
        Path p = Path.of(args[0]);
        String javaHome = System.getProperty("java.home") + "/bin/java";
        Process proc = new ProcessBuilder(javaHome, "-cp", args[1], "HaltChild", args[0])
            .redirectErrorStream(true).start();
        BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        String line = r.readLine();
        System.out.println("from child: " + line);

        long start = System.nanoTime();
        boolean exited = proc.waitFor(15, TimeUnit.SECONDS);
        long waitMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println("waitFor(15s) returned=" + exited + " after " + waitMs + "ms, isAlive=" + proc.isAlive());

        long lockStart = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            FileChannel ch = FileChannel.open(p, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = ch.tryLock();
            if (lock != null) {
                long elapsedMs = (System.nanoTime() - lockStart) / 1_000_000;
                System.out.println("LOCK ACQUIRED after " + elapsedMs + "ms, attempt " + i);
                lock.release();
                ch.close();
                return;
            }
            ch.close();
            Thread.sleep(50);
        }
        System.out.println("lock never freed after 100 attempts (~5s)");
    }
}
