package dev.kalipocket;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.*;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import java.io.*;
import java.net.Socket;
import java.security.GeneralSecurityException;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/** Minimal RFB 3.8 viewer for a local KeX server. Network operations stay off the UI thread. */
public final class DesktopActivity extends Activity {
    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;
    private DesktopView desktop;
    private TextView status;
    private volatile boolean closed;
    private final Object sendLock = new Object();
    private int[] pixelBuffer=new int[0];
    private byte[] rowBuffer=new byte[0];
    private long lastFrame;
    private boolean rightClick=false;
    private final java.util.concurrent.ExecutorService events = java.util.concurrent.Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        root.setBackgroundColor(Color.rgb(10,16,24));
        status = new TextView(this); status.setTextColor(Color.WHITE); status.setPadding(16,16,16,16);
        status.setText("Connecting to local desktop…"); root.addView(status);
        desktop = new DesktopView(); root.addView(desktop, new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions = new LinearLayout(this);
        Button keyboard = new Button(this); keyboard.setText("Keyboard");
        keyboard.setOnClickListener(v -> { desktop.requestFocus(); ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(desktop, InputMethodManager.SHOW_IMPLICIT); });
        Button back = new Button(this); back.setText("Back"); back.setOnClickListener(v -> finish());
        actions.addView(keyboard, new LinearLayout.LayoutParams(0,-2,1)); actions.addView(back, new LinearLayout.LayoutParams(0,-2,1)); root.addView(actions);
        LinearLayout extra=new LinearLayout(this);
        for(String label:new String[]{"Right click","Scroll ↑","Scroll ↓","Esc","Tab","Ctrl"}){
            Button b=new Button(this);b.setText(label);b.setAllCaps(false);extra.addView(b,new LinearLayout.LayoutParams(0,-2,1));
            b.setOnClickListener(v->{switch(label){case "Right click":rightClick=!rightClick;b.setSelected(rightClick);break;case "Scroll ↑":pointer(8,desktop.lastX,desktop.lastY);pointer(0,desktop.lastX,desktop.lastY);break;case "Scroll ↓":pointer(16,desktop.lastX,desktop.lastY);pointer(0,desktop.lastX,desktop.lastY);break;case "Esc":key(0xff1b,true);key(0xff1b,false);break;case "Tab":key(0xff09,true);key(0xff09,false);break;case "Ctrl":b.setSelected(!b.isSelected());key(0xffe3,b.isSelected());break;}});
        }
        root.addView(extra);
        setContentView(root);
        String secret=getSharedPreferences("pocket",MODE_PRIVATE).getString("vnc_password", "");
        new Thread(() -> connect(secret), "rfb-reader").start();
    }
    private void connect(String password) {
        try {
            Socket s=new Socket(); socket=s; s.connect(new java.net.InetSocketAddress("127.0.0.1",5901),5000); s.setSoTimeout(15000);
            input=new DataInputStream(new BufferedInputStream(s.getInputStream())); output=new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
            byte[] version=new byte[12]; input.readFully(version); String ver=new String(version, "US-ASCII");
            if(!ver.startsWith("RFB 003.")) throw new IOException("Unsupported VNC protocol");
            int minor=Integer.parseInt(ver.substring(8,11));
            if(minor<7) throw new IOException("VNC server must support RFB 3.7 or 3.8");
            output.write((minor>=8 ? "RFB 003.008\n" : "RFB 003.007\n").getBytes("US-ASCII")); output.flush();
            int count=input.readUnsignedByte(); if(count==0) throw new IOException(readReason());
            boolean none=false, vnc=false; for(int i=0;i<count;i++){int type=input.readUnsignedByte();none|=type==1;vnc|=type==2;}
            if(vnc){output.writeByte(2);output.flush();byte[] challenge=new byte[16];input.readFully(challenge);output.write(encryptChallenge(password,challenge));output.flush();if(input.readInt()!=0) throw new IOException("VNC authentication failed");}
            else if(none){output.writeByte(1);output.flush();if(minor>=8 && input.readInt()!=0)throw new IOException("VNC connection refused");}
            else throw new IOException("Unsupported VNC authentication");
            output.writeByte(1); output.flush(); int width=input.readUnsignedShort(), height=input.readUnsignedShort();
            byte[] format=new byte[16]; input.readFully(format); int nameLength=input.readInt(); if(nameLength<0||nameLength>65536)throw new IOException("Invalid desktop name"); byte[] name=new byte[nameLength];input.readFully(name);
            validateDimensions(width,height); desktop.resize(width,height);
            synchronized(sendLock){
                output.writeByte(0);output.write(new byte[3]); output.writeByte(32);output.writeByte(24);output.writeByte(0);output.writeByte(1);output.writeShort(255);output.writeShort(255);output.writeShort(255);output.writeByte(16);output.writeByte(8);output.writeByte(0);output.write(new byte[3]);
                output.writeByte(2);output.writeByte(0);output.writeShort(1);output.writeInt(0);output.flush();
            }
            s.setSoTimeout(0); runOnUiThread(() -> status.setText("Kali Pocket · Pad 7 profile · touch to click and drag")); requestUpdate(false,width,height);
            while(!closed){int type=input.readUnsignedByte();if(type==0) frame();else if(type==2){}else if(type==3){input.skipBytes(3);int len=input.readInt();if(len<0||len>1048576)throw new IOException("Invalid clipboard length");byte[] skip=new byte[len];input.readFully(skip);}else throw new IOException("Unsupported VNC message " + type);}
        } catch(Exception e) { if(!closed)runOnUiThread(() -> status.setText("Desktop disconnected: " + e.getMessage())); }
        finally {closeSocket();}
    }
    private String readReason() throws IOException {int n=input.readInt();if(n<0||n>4096)throw new IOException("Invalid server error");byte[] b=new byte[n];input.readFully(b);return new String(b,"UTF-8");}
    private static byte[] encryptChallenge(String password,byte[] challenge)throws GeneralSecurityException {
        byte[] key=new byte[8], src=password.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        for(int i=0;i<Math.min(8,src.length);i++){int b=src[i]&255;int r=0;for(int j=0;j<8;j++)r=(r<<1)|((b>>j)&1);key[i]=(byte)r;}
        Cipher cipher=Cipher.getInstance("DES/ECB/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"DES"));return cipher.doFinal(challenge);
    }
    private void frame()throws IOException {
        input.readUnsignedByte(); int count=input.readUnsignedShort();
        for(int i=0;i<count;i++){int x=input.readUnsignedShort(),y=input.readUnsignedShort(),w=input.readUnsignedShort(),h=input.readUnsignedShort(),enc=input.readInt();
            if(enc!=0 || w<=0 || h<=0 || x+w>desktop.width || y+h>desktop.height)throw new IOException("Unsupported or invalid framebuffer update");
            if(pixelBuffer.length<w*h)pixelBuffer=new int[w*h];if(rowBuffer.length<w*4)rowBuffer=new byte[w*4];int[] colors=pixelBuffer;byte[] row=rowBuffer;for(int yy=0;yy<h;yy++){input.readFully(row,0,w*4);for(int xx=0;xx<w;xx++){int k=xx*4;colors[yy*w+xx]=Color.rgb(row[k+2]&255,row[k+1]&255,row[k]&255);}}
            synchronized(desktop){desktop.bitmap.setPixels(colors,0,w,x,y,w,h);}
        }
        desktop.postInvalidate();long elapsed=android.os.SystemClock.uptimeMillis()-lastFrame;if(elapsed<33)android.os.SystemClock.sleep(33-elapsed);lastFrame=android.os.SystemClock.uptimeMillis();requestUpdate(true,desktop.width,desktop.height);
    }
    private void validateDimensions(int w,int h)throws IOException {if(w<=0||h<=0||w>4096||h>4096||(long)w*h>10000000)throw new IOException("Desktop dimensions too large");}
    private void requestUpdate(boolean incremental,int w,int h)throws IOException {synchronized(sendLock){output.writeByte(3);output.writeByte(incremental?1:0);output.writeShort(0);output.writeShort(0);output.writeShort(w);output.writeShort(h);output.flush();}}
    private void pointer(int mask,int x,int y){events.execute(()->{try{synchronized(sendLock){if(output!=null){output.writeByte(5);output.writeByte(mask);output.writeShort(x);output.writeShort(y);output.flush();}}}catch(IOException ignored){}});}
    private void key(int key,boolean down){events.execute(()->{try{synchronized(sendLock){if(output!=null){output.writeByte(4);output.writeByte(down?1:0);output.writeShort(0);output.writeInt(key);output.flush();}}}catch(IOException ignored){}});}
    private void closeSocket(){try{if(socket!=null)socket.close();}catch(IOException ignored){}}
    @Override protected void onDestroy(){closed=true;closeSocket();events.shutdownNow();super.onDestroy();}
    private final class DesktopView extends View {
        Bitmap bitmap; int width,height,lastX,lastY; float scale,offsetX,offsetY; long lastMotion;
        DesktopView(){super(DesktopActivity.this);setFocusableInTouchMode(true);}
        synchronized void resize(int w,int h){width=w;height=h;bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);postInvalidate();}
        @Override protected synchronized void onDraw(Canvas c){super.onDraw(c);c.drawColor(Color.BLACK);if(bitmap==null)return;scale=Math.min((float)getWidth()/width,(float)getHeight()/height);offsetX=(getWidth()-width*scale)/2;offsetY=(getHeight()-height*scale)/2;c.save();c.translate(offsetX,offsetY);c.scale(scale,scale);c.drawBitmap(bitmap,0,0,null);c.restore();}
        @Override public boolean onCheckIsTextEditor(){return true;}
        @Override public android.view.inputmethod.InputConnection onCreateInputConnection(android.view.inputmethod.EditorInfo info){
            info.inputType=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
            info.imeOptions=android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI;
            return new android.view.inputmethod.BaseInputConnection(this,false){
                @Override public boolean commitText(CharSequence value,int cursor){for(int i=0;i<value.length();){int c=Character.codePointAt(value,i);i+=Character.charCount(c);int sym=c<=255?c:0x01000000|c;key(sym,true);key(sym,false);}return true;}
                @Override public boolean deleteSurroundingText(int before,int after){for(int i=0;i<before;i++){key(0xff08,true);key(0xff08,false);}return true;}
                @Override public boolean sendKeyEvent(KeyEvent event){return event.getAction()==KeyEvent.ACTION_DOWN?onKeyDown(event.getKeyCode(),event):onKeyUp(event.getKeyCode(),event);}
            };
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent event){if(bitmap==null||scale==0)return false;int x=Math.max(0,Math.min(width-1,(int)((event.getX()-offsetX)/scale)));int y=Math.max(0,Math.min(height-1,(int)((event.getY()-offsetY)/scale)));lastX=x;lastY=y;int a=event.getActionMasked();if(a==MotionEvent.ACTION_DOWN){requestFocus();pointer(rightClick?4:1,x,y);return true;}if(a==MotionEvent.ACTION_MOVE){long now=android.os.SystemClock.uptimeMillis();if(now-lastMotion<16)return true;lastMotion=now;pointer(rightClick?4:1,x,y);return true;}if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){pointer(0,x,y);return true;}return true;}
        @Override public boolean onKeyDown(int code,android.view.KeyEvent e){int k=keysym(code,e);if(k==0)return super.onKeyDown(code,e);key(k,true);return true;}
        @Override public boolean onKeyUp(int code,android.view.KeyEvent e){int k=keysym(code,e);if(k==0)return super.onKeyUp(code,e);key(k,false);return true;}
        private int keysym(int code,android.view.KeyEvent e){switch(code){case KeyEvent.KEYCODE_CTRL_LEFT:case KeyEvent.KEYCODE_CTRL_RIGHT:return 0xffe3;case KeyEvent.KEYCODE_ALT_LEFT:case KeyEvent.KEYCODE_ALT_RIGHT:return 0xffe9;case KeyEvent.KEYCODE_SHIFT_LEFT:case KeyEvent.KEYCODE_SHIFT_RIGHT:return 0xffe1;case KeyEvent.KEYCODE_DEL:return 0xff08;case KeyEvent.KEYCODE_ENTER:return 0xff0d;case KeyEvent.KEYCODE_TAB:return 0xff09;case KeyEvent.KEYCODE_ESCAPE:return 0xff1b;case KeyEvent.KEYCODE_DPAD_LEFT:return 0xff51;case KeyEvent.KEYCODE_DPAD_UP:return 0xff52;case KeyEvent.KEYCODE_DPAD_RIGHT:return 0xff53;case KeyEvent.KEYCODE_DPAD_DOWN:return 0xff54;default:int u=e.getUnicodeChar();return u>0 && u<=255?u:0;}}
    }
}
