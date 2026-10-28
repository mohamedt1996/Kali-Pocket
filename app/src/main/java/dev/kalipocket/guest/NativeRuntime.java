package dev.kalipocket.guest;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Runs the upstream Android PRoot binary shipped in this APK, without Termux IPC. */
public final class NativeRuntime {
    private static final java.util.Map<Process,Integer> pids=new java.util.concurrent.ConcurrentHashMap<>();
    private final Context context;
    public NativeRuntime(Context context) { this.context = context.getApplicationContext(); }

    public Process start(File rootfs, List<String> guestCommand, File log) throws IOException {
        if (guestCommand.isEmpty() || !guestCommand.get(0).startsWith("/"))
            throw new IOException("Guest executable must have an absolute path");
        if (!new File(rootfs, "usr/bin/env").isFile())
            throw new IOException("Kali root filesystem is not installed");
        File nativeDir = new File(context.getApplicationInfo().nativeLibraryDir);
        File proot = new File(nativeDir, "libproot.so");
        if (!proot.canExecute()) throw new IOException("Bundled PRoot is unavailable for this architecture");
        File libs = new File(context.getFilesDir(), "runtime-libs");
        File temp = new File(context.getCacheDir(), "proot");
        File guestTmp = new File(context.getCacheDir(), "guest-tmp");
        for (File dir : Arrays.asList(libs,temp,guestTmp))
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create runtime directory");
        symlink(new File(nativeDir,"libtalloc.so"), new File(libs,"libtalloc.so.2"));
        symlink(new File(nativeDir,"libandroid-shmem.so"), new File(libs,"libandroid-shmem.so"));
        ArrayList<String> command = new ArrayList<>(Arrays.asList(proot.getAbsolutePath(),
            "--link2symlink", "-0", "-r", rootfs.getAbsolutePath(), "-w", "/root",
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-b", guestTmp.getAbsolutePath()+":/tmp",
            "-b", guestTmp.getAbsolutePath()+":/dev/shm",
            "/usr/bin/env", "-i", "HOME=/root", "USER=root", "LANG=C.UTF-8",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color"));
        command.addAll(guestCommand);
        File pidFile=File.createTempFile("proot-pid-", ".txt", context.getCacheDir());
        ArrayList<String> wrapped=new ArrayList<>(Arrays.asList("/system/bin/sh","-c","echo $$ > \"$1\"; shift; exec \"$@\"","kali-pocket",pidFile.getAbsolutePath()));
        wrapped.addAll(command);
        ProcessBuilder builder = new ProcessBuilder(wrapped);
        Map<String,String> env = builder.environment();
        env.remove("LD_PRELOAD");
        env.put("LD_LIBRARY_PATH", libs.getAbsolutePath()+":"+nativeDir.getAbsolutePath());
        env.put("PROOT_LOADER",new File(nativeDir,"libproot-loader.so").getAbsolutePath());
        env.put("PROOT_LOADER_32",new File(nativeDir,"libproot-loader32.so").getAbsolutePath());
        env.put("PROOT_TMP_DIR",temp.getAbsolutePath());
        builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log));
        Process process=builder.start();
        try {
            for(int attempt=0;attempt<100;attempt++){
                String pid=new String(java.nio.file.Files.readAllBytes(pidFile.toPath()),java.nio.charset.StandardCharsets.US_ASCII).trim();
                if(!pid.isEmpty()){pids.put(process,Integer.parseInt(pid));break;}
                if(!process.isAlive())break;
                try{Thread.sleep(10);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
            }
        } finally {pidFile.delete();}
        return process;
    }
    public static void stop(Process process){
        if(process==null)return;
        Integer pid=pids.remove(process);
        if(!process.isAlive())return;
        // PRoot ignores SIGTERM. SIGQUIT invokes its tracee cleanup handler.
        if(pid!=null){try{Os.kill(pid,android.system.OsConstants.SIGQUIT);return;}catch(ErrnoException ignored){}}
        process.destroyForcibly();
    }
    private static void symlink(File target, File link) throws IOException {
        if (link.exists()) return;
        try { Os.symlink(target.getAbsolutePath(),link.getAbsolutePath()); }
        catch (ErrnoException e) { throw new IOException("Cannot prepare runtime libraries",e); }
    }
}
