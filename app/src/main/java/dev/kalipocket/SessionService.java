package dev.kalipocket;

import android.app.*;
import android.content.*;
import android.os.*;
import dev.kalipocket.guest.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

public final class SessionService extends Service {
 public static volatile String status="Ready";
 public static volatile boolean busy=false,ready=false;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private volatile java.lang.Process desktop,bridge;
 private PowerManager.WakeLock wake;
 public IBinder onBind(Intent i){return null;}
 public void onCreate(){super.onCreate();NotificationManager n=getSystemService(NotificationManager.class);n.createNotificationChannel(new NotificationChannel("kali","Kali desktop",NotificationManager.IMPORTANCE_LOW));}
 public int onStartCommand(Intent intent,int flags,int id){
  if(intent!=null&&"stop".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
  startForeground(1,new Notification.Builder(this,"kali").setContentTitle("Kali Pocket").setContentText("Local Linux desktop is active").setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(open).build());
  if(busy||ready)return START_NOT_STICKY;
  busy=true;wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"KaliPocket:setup");wake.acquire(2*60*60*1000L);
  worker.execute(()->{try{
   RootfsInstaller.install(getFilesDir(),s->status=s);
   File root=new File(getFilesDir(),"kali"),tools=new File(root,"opt/kali-pocket");tools.mkdirs();
   copyAsset("start-desktop.sh",new File(tools,"start-desktop.sh"));copyAsset("bridge.py",new File(tools,"bridge.py"));
   File resolv=new File(root,"etc/resolv.conf");Files.deleteIfExists(resolv.toPath());Files.write(resolv.toPath(),"nameserver 1.1.1.1\nnameserver 8.8.8.8\n".getBytes("UTF-8"));
   status="Starting Kali desktop (1800 × 1200)…";
   String password=getSharedPreferences("pocket",0).getString("vnc_password","");
   NativeRuntime runtime=new NativeRuntime(this);desktop=runtime.start(root,Arrays.asList("/bin/bash","/opt/kali-pocket/start-desktop.sh",password),new File(getFilesDir(),"desktop.log"));
   boolean connected=false;
   for(int attempt=0;attempt<120;attempt++){
    if(Thread.currentThread().isInterrupted())throw new InterruptedException();
    if(!desktop.isAlive())throw new IOException("Desktop exited. Open Guest Tools → logs for details.");
    try(Socket s=new Socket()){s.connect(new InetSocketAddress("127.0.0.1",5901),500);s.setSoTimeout(1000);byte[] banner=new byte[12];if(s.getInputStream().read(banner)>0&&new String(banner,"US-ASCII").startsWith("RFB ")){connected=true;break;}}catch(IOException ignored){}
    Thread.sleep(1000);
   }
   if(!connected)throw new IOException("Desktop did not become ready within two minutes.");
   JSONObject cfg=new JSONObject().put("token",pref("token","")).put("llm_url",pref("llm","http://192.168.1.20:11434/v1")).put("model",pref("model","llama3.2")).put("api_key",pref("key","")).put("lan",getSharedPreferences("pocket",0).getBoolean("lan",false));
   Files.write(new File(tools,"config.json").toPath(),cfg.toString().getBytes("UTF-8"));
   bridge=runtime.start(root,Arrays.asList("/usr/bin/python3","/opt/kali-pocket/bridge.py","--config","/opt/kali-pocket/config.json"),new File(getFilesDir(),"bridge.log"));
   ready=true;busy=false;status="Kali desktop ready";
   int result=desktop.waitFor();ready=false;status="Desktop stopped (exit "+result+")";
  }catch(Exception e){status="Setup/session failed: "+e.getMessage();ready=false;}finally{busy=false;if(wake!=null&&wake.isHeld())wake.release();stopSelf();}});
  return START_NOT_STICKY;
 }
 private String pref(String key,String fallback){return getSharedPreferences("pocket",0).getString(key,fallback);}
 private void copyAsset(String asset,File dest)throws IOException{try(InputStream in=getAssets().open(asset)){Files.copy(in,dest.toPath(),StandardCopyOption.REPLACE_EXISTING);}}
 public void onDestroy(){NativeRuntime.stop(desktop);NativeRuntime.stop(bridge);worker.shutdownNow();ready=false;busy=false;if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();}
}
