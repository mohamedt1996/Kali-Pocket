import dev.kalipocket.guest.RootfsInstaller;
import java.io.*;
import java.nio.file.*;
import org.apache.commons.compress.archivers.tar.*;
import org.tukaani.xz.XZInputStream;

public class InstallerChecks {
 static byte[] archive(String name,String link)throws Exception{
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();
  try(TarArchiveOutputStream out=new TarArchiveOutputStream(bytes)){
   if(link!=null){TarArchiveEntry e=new TarArchiveEntry("kali-arm64/link",TarConstants.LF_SYMLINK);e.setLinkName(link);out.putArchiveEntry(e);out.closeArchiveEntry();}
   TarArchiveEntry e=new TarArchiveEntry(name);e.setSize(1);e.setMode(0755);out.putArchiveEntry(e);out.write(42);out.closeArchiveEntry();
  }return bytes.toByteArray();
 }
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("rootfs-check-");
  try{
   RootfsInstaller.extract(new ByteArrayInputStream(archive("kali-arm64/usr/bin/test",null)),root,s->{});
   if(!Files.isExecutable(root.resolve("usr/bin/test")))throw new AssertionError("mode lost");
   for(String name:new String[]{"kali-arm64/../../escape","wrong-prefix/file"}){try{RootfsInstaller.extract(new ByteArrayInputStream(archive(name,null)),root,s->{});throw new AssertionError("Unsafe path accepted");}catch(IOException expected){}}
   try{RootfsInstaller.extract(new ByteArrayInputStream(archive("kali-arm64/link/outside","/tmp")),root,s->{});throw new AssertionError("Symlink escape accepted");}catch(IOException expected){}
  }finally{RootfsInstaller.deleteTree(root);}
  System.out.println("Archive extraction and path-escape tests passed");
  if(args.length>0){Path real=Paths.get(args[1]);Files.createDirectories(real);try(InputStream in=new XZInputStream(new BufferedInputStream(new FileInputStream(args[0])))){RootfsInstaller.extract(in,real,System.out::println);}for(String p:new String[]{"usr/bin/env","usr/bin/bash","usr/bin/Xtigervnc","usr/bin/startxfce4","usr/bin/python3"})if(!Files.exists(real.resolve(p)))throw new AssertionError("Missing "+p);System.out.println("Official full Kali image extracted and desktop programs found");}
 }
}
