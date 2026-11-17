package io.github.alieninput.androidsemantic;

import android.app.Activity;
import android.content.*;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import io.github.alieninput.semantic.*;
import java.util.*;

/** The launcher opens the two everyday tasks, with keyboard configuration secondary. */
public final class HomeActivity extends Activity {
    private ProductUi ui;
    private Bitmap preview;
    @Override public void onCreate(Bundle state){super.onCreate(state);ui=new ProductUi(this);}
    @Override protected void onResume(){super.onResume();showHome();}
    private void settings(){startActivity(new Intent().setClassName(this,"com.osfans.trime.ui.main.MainActivity"));}
    private void showHome(){
        LinearLayout root=ui.screen(this),header=ui.row();header.addView(ui.title("外星人输入法",22),new LinearLayout.LayoutParams(0,-2,1));header.addView(ui.button("设置",false,this::settings),new LinearLayout.LayoutParams(ui.dp(64),ui.dp(42)));root.addView(header);ui.gap(root,18);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);LinearLayout body=ui.column();scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        ui.block(body,ui.title("写下你的\n外星来信",32),-2);ui.gap(body,12);ui.block(body,ui.text("用熟悉的词语，表达另一种语言。",15,ProductUi.MUTED),-2);ui.gap(body,24);
        List<List<String>> groups=ImageDraftStore.read(this);boolean hasDraft=!groups.isEmpty();Draft draft=new Draft();for(List<String> group:groups)draft.add(Lexicon.Entry.fromImage(group,""));
        LinearLayout message=ui.card();message.setPadding(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(16));ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackground(ui.shape(0xff090a08,14));image.setClipToOutline(true);
        Bitmap old=preview;preview=null;try{AlienImages images=new AlienImages(this);preview=hasDraft?images.previewDraft(draft):images.preview(Arrays.asList("F0902","F0834","F1305","F1302"));image.setImageBitmap(preview);}catch(Exception ignored){}if(old!=null)old.recycle();image.setContentDescription(hasDraft?"当前图片稿预览":"外星文字示例");ui.block(message,image,164);ui.gap(message,14);
        TextView caption=ui.text(hasDraft?"未发出的来信 · "+draft.glyphs().size()+" 个字形":"从一个词开始，慢慢写成一条消息。",13,ProductUi.MUTED);caption.setGravity(Gravity.CENTER);message.addView(caption);ui.block(body,message,-2);ui.gap(body,14);
        ui.block(body,ui.button(hasDraft?"继续写这条消息":"写一条消息",true,()->startActivity(new Intent(this,WriteMessageActivity.class))),54);ui.gap(body,14);
        LinearLayout read=ui.card();LinearLayout readLine=ui.row();LinearLayout readText=ui.column();readText.addView(ui.title("读一张来信",18));ui.gap(readText,6);readText.addView(ui.text("从图片找回字形，接着写。",14,ProductUi.MUTED));readLine.addView(readText,new LinearLayout.LayoutParams(0,-2,1));readLine.addView(ui.text("›",30,ProductUi.MUTED));read.addView(readLine);read.setContentDescription("读一张来信");read.setFocusable(true);read.setOnClickListener(v->startActivity(new Intent(this,ReadImageActivity.class)));body.addView(read);ui.gap(body,22);
        String current=Settings.Secure.getString(getContentResolver(),Settings.Secure.DEFAULT_INPUT_METHOD);boolean selected=current!=null&&current.startsWith(getPackageName()+"/");
        LinearLayout keyboard=ui.row();LinearLayout info=ui.column();info.addView(ui.title(selected?"键盘已就绪":"也可以直接在聊天里写",14));ui.gap(info,5);info.addView(ui.text(selected?"在键盘上点「外星」开始写消息":"启用键盘，随时展开外星面板",12,ProductUi.MUTED));keyboard.addView(info,new LinearLayout.LayoutParams(0,-2,1));Button keyboardAction=ui.button(selected?"切换":"启用",false,()->{if(selected)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker();else settings();});keyboard.addView(keyboardAction,new LinearLayout.LayoutParams(ui.dp(64),ui.dp(42)));body.addView(keyboard);ui.gap(body,26);
        TextView footer=ui.text("《外星来信》玩家制作 · 文字与读图均在本机处理",11,ProductUi.MUTED);footer.setGravity(Gravity.CENTER);body.addView(footer);ui.gap(body,8);setContentView(root);root.requestApplyInsets();
    }
    @Override protected void onDestroy(){if(preview!=null)preview.recycle();super.onDestroy();}
}
