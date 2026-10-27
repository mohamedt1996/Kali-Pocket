import dev.kalipocket.guest.VerifiedDownload;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;

public class VerifiedDownloadTest {
    static String hash(byte[] b) throws Exception {StringBuilder s=new StringBuilder();for(byte v:MessageDigest.getInstance("SHA-256").digest(b))s.append(String.format("%02x",v&255));return s.toString();}
    static void check(boolean ok) {if(!ok)throw new AssertionError();}
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("guest-test"); File file=dir.resolve("rootfs.tar.xz").toFile();byte[] original="verified data".getBytes("UTF-8");
        VerifiedDownload.copy(new ByteArrayInputStream(original),file,hash(original),1024,null);check(java.util.Arrays.equals(original,Files.readAllBytes(file.toPath())));
        try {VerifiedDownload.copy(new ByteArrayInputStream("bad".getBytes("UTF-8")),file,hash(original),1024,null);throw new AssertionError();} catch(IOException expected){}
        check(java.util.Arrays.equals(original,Files.readAllBytes(file.toPath())));
        try {VerifiedDownload.copy(new ByteArrayInputStream(original),file,hash(original),2,null);throw new AssertionError();} catch(IOException expected){}
        check(java.util.Arrays.equals(original,Files.readAllBytes(file.toPath())));
        try (java.util.stream.Stream<Path> stream=Files.list(dir)){check(stream.count()==1);}
        Files.delete(file.toPath());Files.delete(dir);System.out.println("VerifiedDownload tests passed");
    }
}
