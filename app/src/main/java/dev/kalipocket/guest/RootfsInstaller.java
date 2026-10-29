package dev.kalipocket.guest;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import org.apache.commons.compress.archivers.tar.*;
import org.tukaani.xz.XZInputStream;

/** Transactional extraction of the pinned official ARM64 full NetHunter image. */
public final class RootfsInstaller {
 public static final String URL="https://kali.download/nethunter-images/current/rootfs/kali-nethunter-rootfs-full-arm64.tar.xz";
 public static final String SHA="cc677eac68d6699873b1d617dda2957506665e71f9910923584248c80e1ec007";
 public interface Progress {void update(String status);}
 public static void install(File files, Progress progress)throws Exception {
  File root=new File(files,"kali");if(new File(root,".pocket-installed").isFile())return;
  File stage=new File(files,"kali-staging");deleteTree(stage.toPath());
  if(files.getUsableSpace()<16L*1024*1024*1024)throw new IOException("First setup requires at least 16 GB of free space.");
  File archive=new File(files,"kali-download.tar.xz");
  boolean cached=false;
  if(archive.isFile()){progress.update("Checking previously downloaded Kali image…");java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(archive)){byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)digest.update(b,0,n);}StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format("%02x",b&255));cached=hash.toString().equals(SHA);}
  if(!cached){
  progress.update("Downloading official Kali ARM64 image (about 1.8 GB)…");
  HttpURLConnection connection=(HttpURLConnection)new URL(URL).openConnection();connection.setConnectTimeout(20000);connection.setReadTimeout(30000);
  try{if(connection.getResponseCode()!=200)throw new IOException("Kali download HTTP "+connection.getResponseCode());
   try(InputStream in=connection.getInputStream()) {final long[] previous={0};VerifiedDownload.copy(in,archive,SHA,3L*1024*1024*1024,n->{if(n-previous[0]>8*1024*1024){previous[0]=n;progress.update("Downloading Kali: "+n/(1024*1024)+" MB");}});}
  }finally{connection.disconnect();}
  }
  Files.createDirectories(stage.toPath());
  progress.update("Verified download. Extracting Kali…");
  try(InputStream in=new XZInputStream(new BufferedInputStream(new FileInputStream(archive)))){extract(in,stage.toPath(),progress);}
  for(String required:new String[]{"usr/bin/env","usr/bin/bash"})if(!new File(stage,required).isFile())throw new IOException("Kali archive is missing "+required);
  Files.write(new File(stage,".pocket-installed").toPath(),SHA.getBytes("UTF-8"));
  if(root.exists())throw new IOException("An incomplete Kali directory already exists; keep it for recovery before retrying.");
  Files.move(stage.toPath(),root.toPath(),StandardCopyOption.ATOMIC_MOVE);Files.delete(archive.toPath());
 }
 public static void extract(InputStream source, Path root, Progress progress)throws Exception {
  root=root.toAbsolutePath().normalize();long total=0;int count=0;List<String[]> hardlinks=new ArrayList<>();
  try(TarArchiveInputStream tar=new TarArchiveInputStream(source)){TarArchiveEntry entry;byte[] buffer=new byte[65536];
   while((entry=tar.getNextTarEntry())!=null){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Setup cancelled");
    String name=strip(entry.getName());if(name.isEmpty())continue;Path target=safe(root,name);parents(root,target.getParent());
    if(entry.isDirectory()){Files.createDirectories(target);continue;}
    if(entry.isSymbolicLink()){Files.createSymbolicLink(target,Paths.get(entry.getLinkName()));continue;}
    if(entry.isLink()){hardlinks.add(new String[]{name,strip(entry.getLinkName())});continue;}
    if(!entry.isFile())continue; // Device nodes are supplied by Android's /dev bind.
    if(entry.getSize()<0||entry.getSize()>2L*1024*1024*1024||total+entry.getSize()>16L*1024*1024*1024)throw new IOException("Unexpected rootfs size");total+=entry.getSize();
    try(OutputStream out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){for(int n;(n=tar.read(buffer))!=-1;)out.write(buffer,0,n);}
    Set<PosixFilePermission> permissions=EnumSet.noneOf(PosixFilePermission.class);PosixFilePermission[] bits={PosixFilePermission.OTHERS_EXECUTE,PosixFilePermission.OTHERS_WRITE,PosixFilePermission.OTHERS_READ,PosixFilePermission.GROUP_EXECUTE,PosixFilePermission.GROUP_WRITE,PosixFilePermission.GROUP_READ,PosixFilePermission.OWNER_EXECUTE,PosixFilePermission.OWNER_WRITE,PosixFilePermission.OWNER_READ};
    for(int b=0;b<9;b++)if((entry.getMode()&(1<<b))!=0)permissions.add(bits[b]);Files.setPosixFilePermissions(target,permissions);
    if(++count%1500==0)progress.update("Extracting Kali: "+count+" files");
   }
  }
  for(String[] link:hardlinks){Path dest=safe(root,link[0]),src=safe(root,link[1]);parents(root,dest.getParent());parents(root,src.getParent());if(!Files.isRegularFile(src,LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid archive hardlink");Files.createLink(dest,src);}
 }
 static String strip(String name)throws IOException {while(name.startsWith("./"))name=name.substring(2);if(name.equals("kali-arm64")||name.equals("kali-arm64/"))return "";if(!name.startsWith("kali-arm64/"))throw new IOException("Unexpected archive prefix");return name.substring(11);}
 static Path safe(Path root,String name)throws IOException {Path p=root.resolve(name).normalize();if(!p.startsWith(root)||p.equals(root))throw new IOException("Unsafe archive path");return p;}
 static void parents(Path root,Path parent)throws IOException {Path current=root;for(Path part:root.relativize(parent)){current=current.resolve(part);if(Files.isSymbolicLink(current))throw new IOException("Archive entry follows a symlink");if(!Files.exists(current))Files.createDirectory(current);}}
 public static void deleteTree(Path root)throws IOException {if(!Files.exists(root,LinkOption.NOFOLLOW_LINKS))return;try(java.util.stream.Stream<Path> paths=Files.walk(root)){for(Path p:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(p);}}
}
