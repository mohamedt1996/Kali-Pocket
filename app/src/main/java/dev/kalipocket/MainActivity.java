package dev.kalipocket;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.text.InputType;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import org.json.*;

public final class MainActivity extends Activity {
 private TextView state;
 private boolean opened=false;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable poll=new Runnable(){public void run(){state.setText(SessionService.status);if(SessionService.ready&&!opened){opened=true;startActivity(new Intent(MainActivity.this,DesktopActivity.class));}handler.postDelayed(this,1000);}};
 private SharedPreferences prefs;
 public void onCreate(Bundle saved){super.onCreate(saved);prefs=getSharedPreferences("pocket",0);if(!prefs.contains("vnc_password")){SecureRandom r=new SecureRandom();byte[] token=new byte[32];r.nextBytes(token);StringBuilder p=new StringBuilder();for(int i=0;i<8;i++)p.append("ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".charAt(r.nextInt(55)));prefs.edit().putString("vnc_password",p.toString()).putString("token",android.util.Base64.encodeToString(token,android.util.Base64.NO_WRAP)).apply();}
  if(saved!=null)opened=saved.getBoolean("opened");
  LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(32,48,32,32);box.setBackgroundColor(Color.rgb(10,16,24));
  box.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(32,32+i.getSystemWindowInsetTop(),32,24+i.getSystemWindowInsetBottom());return i;});
  TextView title=label("Kali Pocket",32);box.addView(title);box.addView(label("Independent ARM64 runtime · Pad 7 desktop profile",16));
  state=label(SessionService.status,20);box.addView(state);
  box.addView(label("First launch downloads the official full Kali image (about 1.8 GB), verifies it, and installs it privately. Keep this app open, allow 16 GB free, and use Wi-Fi. Later launches open the desktop automatically.",16));
  button(box,"Start / retry",()->{opened=false;start();});
  button(box,"Open desktop",()->{if(SessionService.ready){opened=true;startActivity(new Intent(this,DesktopActivity.class));}else alert("Desktop",SessionService.status);});
  button(box,"Guest Tools",this::tools);
  button(box,"Stop desktop",()->{stopService(new Intent(this,SessionService.class));SessionService.status="Stopped";});
  ScrollView scroll=new ScrollView(this);scroll.addView(box);setContentView(scroll);
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},9);
  if(!SessionService.busy&&!SessionService.ready)start();
 }
 private TextView label(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextColor(Color.rgb(226,237,244));t.setTextSize(size);t.setPadding(0,16,0,16);return t;}
 private void button(LinearLayout box,String text,Runnable action){Button b=new Button(this);b.setAllCaps(false);b.setText(text);box.addView(b);b.setOnClickListener(v->action.run());}
 private void start(){if(!java.util.Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a")){alert("Unsupported device","This build requires ARM64 Android.");return;}startForegroundService(new Intent(this,SessionService.class));}
 protected void onResume(){super.onResume();handler.post(poll);}
 protected void onPause(){handler.removeCallbacks(poll);super.onPause();}
 protected void onSaveInstanceState(Bundle b){b.putBoolean("opened",opened);super.onSaveInstanceState(b);}
 private void tools(){new AlertDialog.Builder(this).setTitle("Guest Tools").setItems(new String[]{"Desktop and setup log","LAN / LLM settings","Copy LAN API token","Chat with LAN LLM"},(d,w)->{if(w==0)logs();if(w==1)settings();if(w==2){((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Kali Pocket token",prefs.getString("token","")));alert("Copied","Use Authorization: Bearer TOKEN with http://TABLET_IP:8765/v1/status. Restart the desktop after enabling LAN access.");}if(w==3)chat();}).show();}
 private void logs(){StringBuilder out=new StringBuilder(SessionService.status+"\n\n");for(String name:new String[]{"desktop.log","bridge.log"}){File f=new File(getFilesDir(),name);if(f.isFile())try(RandomAccessFile file=new RandomAccessFile(f,"r")){file.seek(Math.max(0,file.length()-20000));byte[] b=new byte[(int)(file.length()-file.getFilePointer())];file.readFully(b);out.append(name).append("\n").append(new String(b,StandardCharsets.UTF_8));}catch(IOException e){out.append(e.getMessage());}}alert("Guest logs",out.toString());}
 private EditText field(LinearLayout box,String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setSingleLine(true);box.addView(e);return e;}
 private void settings(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(24,16,24,16);EditText url=field(box,"OpenAI-compatible base URL",prefs.getString("llm","http://192.168.1.20:11434/v1"));EditText model=field(box,"Model",prefs.getString("model","llama3.2"));EditText key=field(box,"Optional API key",prefs.getString("key",""));key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);Switch lan=new Switch(this);lan.setText("Allow authenticated LAN access");lan.setChecked(prefs.getBoolean("lan",false));box.addView(lan);new AlertDialog.Builder(this).setTitle("LAN / LLM").setMessage("Use trusted Wi-Fi or a VPN; bridge traffic uses HTTP. Save, stop the desktop, and restart to apply.").setView(box).setPositiveButton("Save",(d,w)->{try{URI u=new URI(url.getText().toString().trim());if(!("http".equals(u.getScheme())||"https".equals(u.getScheme()))||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||model.getText().toString().trim().isEmpty())throw new Exception();prefs.edit().putString("llm",u.toString()).putString("model",model.getText().toString().trim()).putString("key",key.getText().toString()).putBoolean("lan",lan.isChecked()).apply();}catch(Exception e){alert("Invalid settings","Use an HTTP(S) URL without embedded credentials, query or fragment, and specify a model.");}}).setNegativeButton("Cancel",null).show();}
 private void chat(){EditText message=new EditText(this);new AlertDialog.Builder(this).setTitle("Ask LAN LLM").setView(message).setPositiveButton("Send",(d,w)->{String prompt=message.getText().toString();new Thread(()->{HttpURLConnection c=null;try{c=(HttpURLConnection)new URL("http://127.0.0.1:8765/v1/chat").openConnection();c.setConnectTimeout(5000);c.setReadTimeout(95000);c.setRequestMethod("POST");c.setRequestProperty("Authorization","Bearer "+prefs.getString("token",""));c.setRequestProperty("Content-Type","application/json");c.setDoOutput(true);JSONObject b=new JSONObject().put("messages",new JSONArray().put(new JSONObject().put("role","user").put("content",prompt)));try(OutputStream o=c.getOutputStream()){o.write(b.toString().getBytes("UTF-8"));}InputStream stream=c.getResponseCode()<400?c.getInputStream():c.getErrorStream();ByteArrayOutputStream data=new ByteArrayOutputStream();try(InputStream in=stream){byte[] buf=new byte[4096];for(int n;(n=in.read(buf))!=-1;){if(data.size()+n>1048576)throw new IOException("Response too large");data.write(buf,0,n);}}JSONObject result=new JSONObject(data.toString("UTF-8"));String answer=result.optString("reply",result.optString("error","No response"));handler.post(()->alert("LLM",answer));}catch(Exception e){handler.post(()->alert("LLM unavailable",e.getMessage()));}finally{if(c!=null)c.disconnect();}}).start();}).setNegativeButton("Cancel",null).show();}
 private void alert(String title,String text){if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("OK",null).show();}
}
