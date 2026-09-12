import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public class LockParent {
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
        proc.waitFor();
        long killMs = (System.nanoTime() - killStart) / 1_000_000;
        System.out.println("process.waitFor() (no timeout) returned after " + killMs + "ms, exitValue=" + proc.exitValue());

        long start = System.nanoTime();
        for (int i = 0; i < 200; i++) {
            FileChannel ch = FileChannel.open(p, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = ch.tryLock();
            if (lock != null) {
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                System.out.println("parent acquired lock after additional " + elapsedMs + "ms, attempt " + i);
                lock.release();
                ch.close();
                return;
            }
            ch.close();
            Thread.sleep(50);
        }
        System.out.println("parent NEVER acquired lock after 200 attempts (~10s) beyond waitFor");
    }
}
