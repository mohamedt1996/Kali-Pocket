package dev.kalipocket;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputType;
import android.util.Base64;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private final int BG=Color.rgb(10,16,24), CARD=Color.rgb(20,30,42), TEXT=Color.rgb(234,242,249), MUTED=Color.rgb(149,166,185), ACCENT=Color.rgb(87,229,188);
    private LinearLayout page, content, tabs;
    private TextView state;
    private SharedPreferences prefs;
    private final ExecutorService worker=Executors.newFixedThreadPool(3);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final JSONArray messages=new JSONArray();
    private String current="Home";
    private boolean busy=false, connected=false, desktop=false;
    private String lastStatus="Bridge offline · complete Setup to connect";
    private final Runnable poll=new Runnable(){ public void run(){ refresh(false); handler.postDelayed(this, 6000); }};

    @Override public void onCreate(Bundle bundle){
        super.onCreate(bundle);
        prefs=getSharedPreferences("pocket",MODE_PRIVATE);
        if(!prefs.contains("token")){byte[] b=new byte[32];new SecureRandom().nextBytes(b);prefs.edit().putString("token",Base64.encodeToString(b,Base64.NO_WRAP)).apply();}
        build();
    }
    @Override public void onResume(){super.onResume();handler.post(poll);}
    @Override public void onPause(){handler.removeCallbacks(poll);super.onPause();}
    @Override public void onDestroy(){worker.shutdownNow();super.onDestroy();}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density);}
    private String setting(String k,String d){return prefs.getString(k,d);}
    private GradientDrawable shape(int color,int border){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(18));if(border!=0)d.setStroke(dp(1),border);return d;}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(5),0,dp(7));return t;}
    private void title(LinearLayout l,String s){TextView t=text(s,23,TEXT);t.setTypeface(null,Typeface.BOLD);l.addView(t);}
    private void note(LinearLayout l,String s){l.addView(text(s,14,MUTED));}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(1);c.setPadding(dp(18),dp(15),dp(18),dp(15));c.setBackground(shape(CARD,Color.rgb(36,51,68)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(12),0,0);content.addView(c,p);return c;}
    private Button button(LinearLayout l,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(BG);b.setBackground(shape(ACCENT,0));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.setMargins(0,dp(12),0,dp(2));l.addView(b,p);b.setOnClickListener(v->action.run());return b;}
    private EditText field(LinearLayout l,String label,String value,boolean secret){note(l,label);EditText e=new EditText(this);e.setText(value);e.setTextColor(TEXT);e.setTextSize(15);e.setSingleLine(true);e.setPadding(dp(12),dp(9),dp(12),dp(9));e.setBackground(shape(BG,Color.rgb(52,72,91)));e.setInputType(secret?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);l.addView(e,new LinearLayout.LayoutParams(-1,dp(48)));return e;}
    private void build(){
        page=new LinearLayout(this);page.setOrientation(1);page.setBackgroundColor(BG);page.setPadding(dp(20),dp(14),dp(20),dp(10));
        // Respect Android 15 edge-to-edge system bars.
        page.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(dp(20),dp(14)+i.getSystemWindowInsetTop(),dp(20),dp(10)+i.getSystemWindowInsetBottom());return i;});
        TextView brand=text("◈  KALI POCKET",16,ACCENT);brand.setLetterSpacing(.15f);brand.setTypeface(null,Typeface.BOLD);page.addView(brand);
        state=text(lastStatus,12,MUTED);page.addView(state);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);content=new LinearLayout(this);content.setOrientation(1);content.setPadding(0,dp(10),0,dp(24));scroll.addView(content);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        tabs=new LinearLayout(this);page.addView(tabs);for(String tab:new String[]{"Home","Desktop","AI","Setup"}){TextView t=text(tab,14,MUTED);t.setGravity(Gravity.CENTER);t.setPadding(0,dp(15),0,dp(15));tabs.addView(t,new LinearLayout.LayoutParams(0,-2,1));t.setOnClickListener(v->show(tab));}
        setContentView(page);show(current);
    }
    private void show(String tab){current=tab;content.removeAllViews();for(int i=0;i<tabs.getChildCount();i++){TextView t=(TextView)tabs.getChildAt(i);t.setTextColor(t.getText().toString().equals(tab)?ACCENT:MUTED);}
        switch(tab){case "Home":home();break;case "Desktop":desktop();break;case "AI":ai();break;default:setup();}
    }
    private void home(){
        title(content,"Your pocket.\nA full Linux workspace.");note(content,"Kali desktop + your local AI, wherever your phone goes.");
        LinearLayout c=card();note(c,"01 / LINUX WORKSPACE");title(c,"NetHunter Rootless");note(c,"Run Kali user space through Termux. Use KeX for an interactive graphical desktop.");button(c,"Open desktop controls  →",()->show("Desktop"));
        c=card();note(c,"02 / CONNECTED INTELLIGENCE");title(c,"Keep your AI nearby");note(c,"Connect an Ollama or OpenAI-compatible LLM on the same Wi-Fi network. Chat stays with the server you choose.");button(c,"Open AI chat  →",()->show("AI"));
        c=card();title(c,"Connection");note(c,"Bridge: 127.0.0.1:8765\nDesktop: 127.0.0.1:5901\nLAN access: "+(prefs.getBoolean("lan",false)?"enabled in saved settings":"off in saved settings"));button(c,"Check connection",()->refresh(true));
    }
    private void desktop(){
        title(content,"Your Kali desktop");note(content,"KeX runs the XFCE graphical session inside NetHunter Rootless.");
        LinearLayout c=card();title(c,"Session controls");note(c,"Set a KeX password in Termux first: nh kex passwd. Start the session, then connect a VNC client to 127.0.0.1:5901.");
        button(c,"Start desktop",()->session("start"));button(c,"Open desktop viewer",this::openViewer);button(c,"Stop desktop",()->session("stop"));
        c=card();title(c,"Terminal");note(c,"Open Termux and enter nh for the Kali shell. Long-running sessions depend on Android keeping Termux alive.");button(c,"Open Termux",this::openTermux);
        c=card();title(c,"Graphics on this phone");note(c,"The experimental in-app viewer connects to the local KeX desktop. Touch and keyboard support need testing on a phone.");
    }
    private void ai(){
        title(content,"Local intelligence");note(content,"Model: "+setting("model","llama3.2")+" · chat only; no automatic shell execution.");
        LinearLayout c=card();
        if(messages.length()==0)note(c,"Ask your model a question about your Linux workspace. Configure the LAN server in Setup first.");
        for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);TextView who=text(m.optString("role").equals("user")?"YOU":"ASSISTANT",11,ACCENT);c.addView(who);TextView body=text(m.optString("content"),15,TEXT);body.setTextIsSelectable(true);c.addView(body);}
        EditText prompt=field(c,"Message","",false);prompt.setSingleLine(false);prompt.setMinLines(2);
        Button send=button(c,busy?"Waiting for model…":"Send message  ↑",()->{
            String value=prompt.getText().toString().trim();if(value.isEmpty()||busy)return;
            try{messages.put(new JSONObject().put("role","user").put("content",value));busy=true;show("AI");JSONArray copy=new JSONArray(messages.toString());worker.execute(()->{
                try{JSONObject result=request("/v1/chat",new JSONObject().put("messages",copy));handler.post(()->{try{messages.put(new JSONObject().put("role","assistant").put("content",result.getString("reply")));}catch(JSONException e){toast("Invalid response");}busy=false;if(current.equals("AI"))show("AI");});}
                catch(Exception e){handler.post(()->{busy=false;messages.remove(messages.length()-1);if(current.equals("AI"))show("AI");alert("Message not sent",e.getMessage());});}
            });}catch(JSONException e){toast(e.getMessage());}
        });send.setEnabled(!busy);
        button(c,"Clear conversation",()->{if(!busy){while(messages.length()>0)messages.remove(0);show("AI");}});
    }
    private void setup(){
        title(content,"Make it yours");note(content,"Android 8+ · no root required · a separate Kali installation is required.");
        LinearLayout c=card();title(c,"1. Install the runtime");note(c,"Install Termux from F-Droid or its official GitHub releases. Follow Kali’s official NetHunter Rootless guide, then run nh kex passwd in Termux. Allow several GB of free space.");
        button(c,"Official NetHunter Rootless guide",()->url("https://www.kali.org/docs/nethunter/nethunter-rootless/"));button(c,"Get Termux",()->url("https://f-droid.org/packages/com.termux/"));
        c=card();title(c,"2. Allow app commands");note(c,"In Termux, run the command below. Then return here and grant Kali Pocket permission to run commands.\n\nmkdir -p ~/.termux\necho 'allow-external-apps=true' >> ~/.termux/termux.properties\ntermux-reload-settings");
        button(c,"Copy Termux configuration",()->copy("mkdir -p ~/.termux\necho 'allow-external-apps=true' >> ~/.termux/termux.properties\ntermux-reload-settings"));
        c=card();title(c,"3. Configure your LLM");note(c,"Use the server’s OpenAI-compatible base URL, including /v1. For Ollama: http://192.168.1.20:11434/v1. The model must already be installed on the server.");
        EditText endpoint=field(c,"LLM base URL",setting("llm","http://192.168.1.20:11434/v1"),false);
        EditText model=field(c,"Model name",setting("model","llama3.2"),false);
        EditText key=field(c,"LLM API key (optional)",setting("key",""),true);
        Switch lan=new Switch(this);lan.setText("Allow bridge access from my LAN");lan.setTextColor(TEXT);lan.setChecked(prefs.getBoolean("lan",false));c.addView(lan);
        note(c,"LAN mode exposes the authenticated bridge on port 8765. Plain HTTP is intended for trusted Wi-Fi or a VPN; use an HTTPS tunnel on untrusted networks. KeX stays on localhost by default.");
        button(c,"Save connection settings",()->{
            String u=endpoint.getText().toString().trim();String m=model.getText().toString().trim();
            try{URI parsed=new URI(u);if(!("http".equals(parsed.getScheme())||"https".equals(parsed.getScheme()))||parsed.getHost()==null||parsed.getUserInfo()!=null||parsed.getQuery()!=null||parsed.getFragment()!=null||m.isEmpty())throw new Exception();
                prefs.edit().putString("llm",u.replaceAll("/+$"," ").trim()).putString("model",m).putString("key",key.getText().toString()).putBoolean("lan",lan.isChecked()).apply();toast("Saved. Install / restart bridge to apply.");
            }catch(Exception e){alert("Check settings","Enter a valid HTTP(S) base URL and a model name.");}
        });
        c=card();title(c,"4. Start the bridge");note(c,"Installs Python in Termux and starts the bundled bridge. Termux will open so you can review installation output. Keep Termux running; exempt it from battery optimization if Android stops it.");button(c,"Install / restart bridge",this::startBridge);button(c,"Check connection",()->refresh(true));
        c=card();title(c,"Connect another device");note(c,"Enable LAN access, save and restart the bridge. Use your phone’s Wi-Fi IP with port 8765. Every request needs Authorization: Bearer <token>. Copy the connection example below; it contains your private token.");button(c,"Copy LAN curl example",()->copy("curl -H 'Authorization: Bearer "+setting("token","")+"' http://PHONE_WIFI_IP:8765/v1/status"));
        button(c,"Rotate bridge token",()->new AlertDialog.Builder(this).setTitle("Rotate token?").setMessage("Existing clients will lose access after you restart the bridge.").setPositiveButton("Rotate",(d,w)->{byte[] b=new byte[32];new SecureRandom().nextBytes(b);prefs.edit().putString("token",Base64.encodeToString(b,Base64.NO_WRAP)).apply();toast("Token rotated. Restart the bridge to apply.");}).setNegativeButton("Cancel",null).show());
        note(content,"Independent prototype. Not affiliated with Kali Linux or Termux. Rootless Kali shares Android’s kernel: kernel modules, monitor mode and USB injection depend on device/kernel support.");
    }
    private void session(String action){worker.execute(()->{try{request("/v1/session/"+action,new JSONObject());handler.post(()->toast("Desktop "+action+" requested. Check status in a few seconds."));}catch(Exception e){handler.post(()->alert("Desktop action failed",e.getMessage()));}});}
    private void refresh(boolean visible){worker.execute(()->{try{JSONObject s=request("/v1/status",null);connected=true;desktop=s.optBoolean("desktop_running");lastStatus="● Bridge connected · "+(desktop?"desktop running":"desktop stopped")+(s.optBoolean("lan_enabled")?" · LAN on":" · local only");String error=s.optString("last_error","");handler.post(()->{state.setText(lastStatus);state.setTextColor(ACCENT);if(visible)alert("Bridge connected",s.optBoolean("nethunter_installed")?"NetHunter detected. "+(!error.isEmpty()&&!error.equals("null")?error:"Ready for session controls."):"Bridge is working. Install NetHunter Rootless to enable the desktop.");});}catch(Exception e){connected=false;lastStatus="○ Bridge offline · open Setup to connect";handler.post(()->{state.setText(lastStatus);state.setTextColor(MUTED);if(visible)alert("Bridge unavailable",e.getMessage());});}});}
    private JSONObject request(String path,JSONObject body)throws Exception{
        HttpURLConnection conn=(HttpURLConnection)new URL("http://127.0.0.1:8765"+path).openConnection();conn.setConnectTimeout(3000);conn.setReadTimeout(path.equals("/v1/chat")?95000:5000);conn.setRequestProperty("Authorization","Bearer "+setting("token",""));
        try{if(body!=null){conn.setRequestMethod("POST");conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json");try(OutputStream out=conn.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int code=conn.getResponseCode();InputStream in=code<400?conn.getInputStream():conn.getErrorStream();if(in==null)throw new IOException("Bridge HTTP "+code);String data=read(in,1100000);JSONObject json=new JSONObject(data);if(code>=400)throw new IOException(json.optString("error","Bridge HTTP "+code));return json;
        }finally{conn.disconnect();}
    }
    private String read(InputStream input,int max)throws IOException{try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException("Response too large");out.write(b,0,n);}return out.toString("UTF-8");}}
    private String encoded(String s){return Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);}
    private void startBridge(){
        try{getPackageManager().getPackageInfo("com.termux",0);}catch(Exception e){alert("Termux required","Install Termux from F-Droid, then follow Setup.");return;}
        if(checkSelfPermission("com.termux.permission.RUN_COMMAND")!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{"com.termux.permission.RUN_COMMAND"},70);return;}
        try{
            JSONObject config=new JSONObject().put("token",setting("token","")).put("llm_url",setting("llm","http://192.168.1.20:11434/v1")).put("model",setting("model","llama3.2")).put("api_key",setting("key","")).put("lan",prefs.getBoolean("lan",false));
            String python=read(getAssets().open("bridge.py"),100000);
            String script="set -eu\numask 077\ncommand -v python >/dev/null || pkg install -y python\nmkdir -p \"$HOME/.kali-pocket\"\n"+
                "printf '%s' '"+encoded(python)+"' | base64 -d > \"$HOME/.kali-pocket/bridge.py\"\n"+
                "printf '%s' '"+encoded(config.toString())+"' | base64 -d > \"$HOME/.kali-pocket/config.json\"\n"+
                "python \"$HOME/.kali-pocket/bridge.py\" --config \"$HOME/.kali-pocket/config.json\"\n";
            // Restart only this controller, identified by our private script path.
            String launch="pkill -f \"^python $HOME/.kali-pocket/bridge.py --config \" 2>/dev/null || true\n"+script;
            Intent i=new Intent("com.termux.RUN_COMMAND");i.setClassName("com.termux","com.termux.app.RunCommandService");i.putExtra("com.termux.RUN_COMMAND_PATH","/data/data/com.termux/files/usr/bin/bash");i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS",new String[]{"-c",launch});i.putExtra("com.termux.RUN_COMMAND_WORKDIR","/data/data/com.termux/files/home");i.putExtra("com.termux.RUN_COMMAND_BACKGROUND",false);i.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION","0");startService(i);toast("Bridge command sent. Check the Termux window.");
        }catch(Exception e){alert("Could not launch bridge",e.getMessage()+"\nCheck Termux external-app permission and configuration.");}
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==70&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startBridge();else if(r==70)alert("Permission needed","Termux requires RUN_COMMAND permission for the bridge installer. Enable it in Android app settings.");}
    private void openTermux(){Intent i=getPackageManager().getLaunchIntentForPackage("com.termux");if(i!=null)startActivity(i);else toast("Install Termux first");}
    private void openViewer(){startActivity(new Intent(this, DesktopActivity.class));}
    private void url(String value){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(value)));}catch(Exception e){toast("No browser available");}}
    private void copy(String value){((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Kali Pocket",value));toast("Copied to clipboard");}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private void alert(String title,String message){if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK",null).show();}
}
