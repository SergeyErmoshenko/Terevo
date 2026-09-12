import java.nio.channels.*;
import java.nio.file.*;

public class HaltChild {
    public static void main(String[] args) throws Exception {
        Path p = Path.of(args[0]);
        FileChannel ch = FileChannel.open(p, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock = ch.tryLock();
        System.out.println("child locked=" + (lock != null));
        System.out.flush();
        Thread.sleep(200);
        Runtime.getRuntime().halt(0);
    }
}
