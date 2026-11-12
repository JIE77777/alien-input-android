package io.github.alieninput.android;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.io.*;

public final class MainActivity extends Activity {
    private TextView state;
    private ImageView preview;
    private Bitmap lastPreview;
    private Uri lastImage;
    private RichEditText editor;
    @Override public void onCreate(Bundle bundle){
        super.onCreate(bundle);getWindow().setStatusBarColor(Ui.BG);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Ui.BG);
        LinearLayout root=Ui.column(this);root.setPadding(Ui.dp(this,24),Ui.dp(this,24),Ui.dp(this,24),Ui.dp(this,28));
        scroll.addView(root);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            int left=insets.getSystemWindowInsetLeft(),top=insets.getSystemWindowInsetTop(),right=insets.getSystemWindowInsetRight(),bottom=insets.getSystemWindowInsetBottom();
            if(android.os.Build.VERSION.SDK_INT>=30){
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
                android.graphics.Insets ime=insets.getInsets(WindowInsets.Type.ime());
                left=bars.left;top=bars.top;right=bars.right;bottom=Math.max(bars.bottom,ime.bottom);
            }
            boolean changed=scroll.getPaddingBottom()!=bottom;
            scroll.setPadding(left,top,right,bottom);
            if(changed&&editor!=null&&editor.hasFocus())editor.post(()->editor.requestRectangleOnScreen(new Rect(0,0,editor.getWidth(),editor.getHeight()),true));
            return insets;
        });
        TextView eyebrow=Ui.text(this,"ALIEN INPUT  /  ANDROID",12,Ui.GREEN);eyebrow.setLetterSpacing(.12f);root.addView(eyebrow);
        TextView title=Ui.text(this,"把外星文字\n带到你的键盘。",32,Ui.INK);title.setPadding(0,16,0,12);root.addView(title);
        TextView desc=Ui.text(this,"1,344 个字形 · 字根组字 · 完全离线\n基于 Message From Aliens 社区项目 v6.12.4",14,Ui.MUTED);desc.setLineSpacing(5,1);root.addView(desc);
        state=Ui.text(this,"",13,Ui.GREEN);state.setPadding(0,24,0,10);root.addView(state);
        addButton(root,"①  在系统设置中启用",()->startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        addButton(root,"②  切换到 Alien 外星输入法",()->((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker());
        TextView heading=Ui.text(this,"试写一条消息",20,Ui.INK);heading.setPadding(0,22,0,8);root.addView(heading);
        TextView tip=Ui.text(this,"点击下方输入框调出键盘。图片模式先选字，再点「插入」；释义和编号模式会直接输入文本。",13,Ui.MUTED);root.addView(tip);
        editor=new RichEditText();editor.setTextSize(18);editor.setMinLines(2);editor.setMaxLines(5);editor.setHint("在这里试用外星输入法…");
        editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editor.setImeOptions(EditorInfo.IME_ACTION_NONE);root.addView(editor,new LinearLayout.LayoutParams(-1,-2));
        preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setBackgroundColor(Color.WHITE);preview.setContentDescription("接收到的外星文字图片");
        root.addView(preview,new LinearLayout.LayoutParams(-1,-2));
        addButton(root,"分享试写图片",()->{if(lastImage==null){Toast.makeText(this,"请先在图片模式插入字形",Toast.LENGTH_SHORT).show();return;}share(lastImage);});
        addButton(root,"清除本机候选学习",()->new AlertDialog.Builder(this).setTitle("清除候选学习？").setMessage("恢复默认候选排序，不影响已分享的图片。切换到其他输入法后再切回即可生效。").setNegativeButton("取消",null).setPositiveButton("清除",(d,w)->{getSharedPreferences("ranking",0).edit().clear().apply();Toast.makeText(this,"已清除，请重新切换输入法",Toast.LENGTH_LONG).show();}).show());
        TextView help=Ui.text(this,"使用说明\n\n• 字根共两页，Shift 切换主根 / 副根；长按键或候选查看说明。\n• 先选字根，再点候选。左右箭头可翻阅全部候选。\n• 图片稿最多 120 字，退格逐字撤回；切换输入框会清空未发送稿件。\n• 外星字是图片，不是 Unicode 字符。目标应用需支持键盘插图，否则请点「分享图」。\n•「释义」输出中文研究注释；「编号」输出 [F0001] 形式的可复制文本。\n• 本应用无网络权限；候选排序仅存本机，密码及禁止学习的输入框不会学习。",13,Ui.MUTED);help.setLineSpacing(4,1);help.setPadding(0,20,0,16);root.addView(help);
        addButton(root,"开源许可与素材归属",this::licenses);
        root.addView(Ui.text(this,"社区二次开发 · 0.1.0 测试版\n非游戏官方产品；字形与游戏素材权利归原作者。",11,Ui.MUTED));
    }
    @Override protected void onResume(){super.onResume();InputMethodManager imm=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);boolean enabled=false;for(InputMethodInfo i:imm.getEnabledInputMethodList())if(getPackageName().equals(i.getPackageName()))enabled=true;state.setText(enabled?"✓ 已启用 · 可以切换并试写":"还差两步，启用你的外星键盘");}
    private void addButton(LinearLayout parent,String text,Runnable action){Button b=Ui.button(this,text,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,Ui.dp(this,48));p.setMargins(0,5,0,5);parent.addView(b,p);}
    private void share(Uri uri){Intent send=new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);send.setClipData(ClipData.newRawUri("Alien message",uri));startActivity(Intent.createChooser(send,"分享外星文字"));}
    private void licenses(){StringBuilder text=new StringBuilder();for(String file:new String[]{"LICENSE.txt","THIRD_PARTY_NOTICES.txt"})try(BufferedReader r=new BufferedReader(new InputStreamReader(getAssets().open(file),"UTF-8"))){String line;while((line=r.readLine())!=null)text.append(line).append('\n');text.append('\n');}catch(IOException e){text.append("无法读取许可文件");}new AlertDialog.Builder(this).setTitle("许可与素材归属").setMessage(text.toString()).setPositiveButton("关闭",null).show();}
    @Override protected void onDestroy(){if(lastPreview!=null)lastPreview.recycle();super.onDestroy();}
    private final class RichEditText extends EditText {
        RichEditText(){super(MainActivity.this);}
        @Override public InputConnection onCreateInputConnection(EditorInfo out){
            InputConnection target=super.onCreateInputConnection(out);if(target==null)return null;
            out.contentMimeTypes=new String[]{"image/png"};
            return new InputConnectionWrapper(target,false){
                @Override public boolean commitContent(InputContentInfo content,int flags,Bundle opts){
                    if(content==null||!content.getDescription().hasMimeType("image/png"))return false;
                    boolean permission=(flags&InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION)!=0;
                    try {
                        if(permission)content.requestPermission();
                        Uri source=content.getContentUri();
                        // Own provider only: this demo does not accept arbitrary external image payloads.
                        if(!ImageProvider.AUTHORITY.equals(source.getAuthority()))return false;
                        Bitmap next;
                        try(InputStream stream=getContentResolver().openInputStream(source)){next=BitmapFactory.decodeStream(stream);}
                        if(next==null)return false;
                        preview.setImageBitmap(next);if(lastPreview!=null)lastPreview.recycle();lastPreview=next;lastImage=source;preview.post(()->preview.requestRectangleOnScreen(new Rect(0,0,preview.getWidth(),preview.getHeight()),true));return true;
                    }catch(Exception e){return false;}
                    finally {if(permission)content.releasePermission();}
                }
            };
        }
    }
}
