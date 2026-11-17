package io.github.alieninput.androidsemantic;

import android.app.Activity;
import android.content.*;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import io.github.alieninput.semantic.*;
import java.util.*;

/** Two explicit tasks; the keyboard remains the writing surface. */
public final class HomeActivity extends Activity {
    private ProductUi ui;
    private Bitmap preview;
    @Override public void onCreate(Bundle state){super.onCreate(state);ui=new ProductUi(this);}
    @Override protected void onResume(){super.onResume();showHome();}
    private void settings(){startActivity(new Intent().setClassName(this,"com.osfans.trime.ui.main.MainActivity"));}
    private void write(){startActivity(new Intent().setClassName(this,"com.osfans.trime.ui.main.MainActivity").putExtra("alien_try_keyboard",true));}
    private Button action(String label,Runnable run){Button button=ui.button(label,false,run);button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x20918a6a),ui.shape(ProductUi.ACCENT,14),null));return button;}
    private void showHome(){
        Bitmap old=preview;preview=null;
        LinearLayout root=ui.screen(this),header=ui.row();header.addView(ui.title("外星人输入法",22),new LinearLayout.LayoutParams(0,-2,1));header.addView(ui.button("设置",false,this::settings),new LinearLayout.LayoutParams(ui.dp(64),ui.dp(42)));root.addView(header);ui.gap(root,20);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);LinearLayout body=ui.column();scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        InputMethodManager manager=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        String current=Settings.Secure.getString(getContentResolver(),Settings.Secure.DEFAULT_INPUT_METHOD);boolean selected=current!=null&&current.startsWith(getPackageName()+"/");boolean enabled=false;
        for(InputMethodInfo method:manager.getEnabledInputMethodList())if(getPackageName().equals(method.getPackageName())){enabled=true;break;}
        LinearLayout writing=ui.card();writing.addView(ui.title("写字",22));ui.gap(writing,8);writing.addView(ui.text("在键盘里选词，组合成外星图片。",14,ProductUi.MUTED));
        List<List<String>> groups=ImageDraftStore.read(this);boolean hasDraft=!groups.isEmpty();
        if(hasDraft){Draft draft=new Draft();for(List<String> group:groups)draft.add(Lexicon.Entry.fromImage(group,""));
            ui.gap(writing,18);writing.addView(ui.text("上次的图片稿 · "+draft.glyphs().size()+" 个字形",12,ProductUi.MUTED));ui.gap(writing,10);
            try{preview=new AlienImages(this).previewDraft(draft);ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackground(ui.shape(AlienPalette.load(this).background,12));image.setClipToOutline(true);image.setImageBitmap(preview);image.setContentDescription("上次的图片稿预览");ui.block(writing,image,112);}catch(Exception e){writing.addView(ui.text("图片稿已保存，可在键盘里继续编辑。",13,ProductUi.MUTED));}
        }
        ui.gap(writing,18);
        if(selected){ui.block(writing,action(hasDraft?"打开键盘，继续写":"打开键盘",this::write),50);ui.gap(writing,12);writing.addView(ui.text("聊天时，在键盘工具栏点「外星」即可使用。",12,ProductUi.MUTED));}
        else{writing.addView(ui.text(enabled?"先选择外星人输入法，再开始写字。":"首次使用，需要先启用外星人输入法。",13,ProductUi.MUTED));ui.gap(writing,12);ui.block(writing,action(enabled?"选择输入法":"启用输入法",enabled?manager::showInputMethodPicker:this::settings),50);}
        body.addView(writing);ui.gap(body,16);
        LinearLayout reading=ui.card();reading.addView(ui.title("读图",22));ui.gap(reading,8);reading.addView(ui.text("从相册或剪贴板读回外星字形。",14,ProductUi.MUTED));ui.gap(reading,18);ui.block(reading,ui.button("打开读图",false,()->startActivity(new Intent(this,ReadImageActivity.class))),50);body.addView(reading);ui.gap(body,16);
        setContentView(root);root.requestApplyInsets();if(old!=null)old.recycle();
    }
    @Override protected void onDestroy(){if(preview!=null)preview.recycle();super.onDestroy();}
}
