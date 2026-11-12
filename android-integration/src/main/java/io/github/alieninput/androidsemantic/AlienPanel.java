package io.github.alieninput.androidsemantic;

import android.content.*;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import io.github.alieninput.semantic.*;
import java.io.*;
import java.util.*;

/** Host-neutral Android panel. Only explicit image output uses the real editor connection. */
public final class AlienPanel {
    public interface Host {
        Context context();
        InputConnection editor();
        EditorInfo editorInfo();
        void resetComposition(Runnable completed);
        boolean isComposing();
        Uri imageUri(File file);
    }
    private final Host host;
    private final Context context;
    private final AlienImages images;
    private final Draft draft=new Draft();
    public final QueryConnection connection;
    private final Map<String,Integer> frequency=new HashMap<>();
    private Lexicon lexicon;
    private LinearLayout root,body,candidates,draftRow;
    private HorizontalScrollView draftScroll;
    private PopupWindow previewPopup;
    private TextView queryLabel,hint;
    private Button toggle,output;
    private String shareReturnPackage;
    private boolean enabled,transitioning,allowed=true,learn=true;
    private int page,replace=-1,generation;
    private List<Lexicon.Match> matches=Collections.emptyList();
    private String query="",error="";
    public AlienPanel(Host host){
        this.host=host;context=host.context();images=new AlienImages(context);
        connection=new QueryConnection(new View(context),new QueryConnection.Listener(){
            public void changed(String value){if(!transitioning){query=value;page=0;search();}}
            public void emptyBackspace(){removeLast();}
            public void submit(){search();}
        });
        // Construct once per service. Entries are immutable and searches are local.
        try{lexicon=new Lexicon(new InputStreamReader(context.getAssets().open("alien/lexicon.tsv"),"UTF-8"));}
        catch(IOException e){error="词典加载失败，请重新安装完整构建";allowed=false;}
        loadFrequency();
    }
    public boolean capturesInput(){return enabled||transitioning;}
    public boolean enabled(){return enabled;}
    private void loadFrequency(){
        frequency.clear();
        for(Map.Entry<String,?> e:context.getSharedPreferences("alien-ranking",0).getAll().entrySet())
            if(e.getValue() instanceof Integer)frequency.put(e.getKey(),(Integer)e.getValue());
    }
    public void start(EditorInfo info,boolean restarting){
        dismissPreview();
        boolean resume=shareReturnPackage!=null&&shareReturnPackage.equals(info.packageName);
        shareReturnPackage=null;
        generation++;transitioning=false;enabled=resume;if(!resume)draft.clear();replace=-1;connection.clear();
        int type=info.inputType&InputType.TYPE_MASK_CLASS,v=info.inputType&InputType.TYPE_MASK_VARIATION;
        boolean password=type==InputType.TYPE_CLASS_TEXT&&(v==InputType.TYPE_TEXT_VARIATION_PASSWORD||v==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD||v==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD);
        allowed=lexicon!=null&&!password&&type==InputType.TYPE_CLASS_TEXT;
        learn=(info.imeOptions&EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)==0;
        refresh();
    }
    public void finish(){dismissPreview();generation++;enabled=false;transitioning=false;if(shareReturnPackage==null)draft.clear();replace=-1;connection.clear();refresh();}
    public void toggleVisibility(){toggle();}
    private void toggle(){
        if(!allowed){toast(error.isEmpty()?"此输入框使用普通输入":error);return;}
        if(transitioning)return;
        if(host.isComposing()){toast("请先选定或清除当前拼音，再切换模式");return;}
        final boolean next=!enabled;
        final int token=++generation;
        transitioning=true;refresh();
        host.resetComposition(()->{
            if(generation!=token)return;
            enabled=next;transitioning=false;search();
        });
    }
    public View createView(){
        root=column();root.setBackgroundColor(Color.rgb(241,244,248));
        boolean compact=context.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout top=row();toggle=button("外星",this::toggle);weighted(top,toggle,compact?30:36);
        weighted(top,button("清空",()->{if(!enabled)return;resetQuery();draft.clear();replace=-1;refresh();}),compact?30:36);
        weighted(top,button("预览",this::preview),compact?30:36);
        weighted(top,button("复制图",this::copyImage),compact?30:36);
        weighted(top,button("编码",this::copyCode),compact?30:36);
        output=button("插入图",()->send(false));weighted(top,output,compact?30:36);
        weighted(top,button("分享",()->send(true)),compact?30:36);root.addView(top);
        body=column();root.addView(body);
        LinearLayout queryRow=row();queryLabel=text("",13);weighted(queryRow,queryLabel,30);
        queryRow.addView(button("重输",this::resetQuery),new LinearLayout.LayoutParams(dp(55),dp(30)));body.addView(queryRow);
        LinearLayout results=row();results.addView(button("‹",()->{page=Math.max(0,page-1);refresh();}),new LinearLayout.LayoutParams(dp(28),dp(compact?48:78)));
        candidates=row();results.addView(candidates,new LinearLayout.LayoutParams(0,dp(compact?48:78),1));
        results.addView(button("›",()->{if((page+1)*4<matches.size())page++;refresh();}),new LinearLayout.LayoutParams(dp(28),dp(compact?48:78)));body.addView(results);
        hint=text("",11);body.addView(hint);
        draftScroll=new HorizontalScrollView(context);draftRow=row();draftScroll.addView(draftRow);body.addView(draftScroll,new LinearLayout.LayoutParams(-1,dp(40)));
        refresh();return root;
    }
    private void resetQuery(){
        if(!enabled)return;
        final int token=++generation;transitioning=true;
        host.resetComposition(()->{if(token!=generation)return;connection.clear();transitioning=false;query="";search();});
    }
    private void search(){
        matches=lexicon==null?Collections.emptyList():lexicon.search(query,learn?frequency:Collections.emptyMap());refresh();
    }
    private void choose(Lexicon.Entry entry){
        if(!enabled||transitioning)return;
        if(host.isComposing()){toast("请先确认当前中文候选");return;}
        boolean appended=replace<0;
        boolean ok=replace>=0?draft.replace(replace,entry):draft.add(entry);
        if(!ok){toast("每张图片最多 120 个字形");return;}
        replace=-1;
        if(learn&&!entry.id.startsWith("NUM:")){int n=Math.min(100000,frequency.getOrDefault(entry.id,0)+1);frequency.put(entry.id,n);context.getSharedPreferences("alien-ranking",0).edit().putInt(entry.id,n).apply();}
        resetQuery();refresh();
        if(appended&&draftScroll!=null)draftScroll.post(()->draftScroll.fullScroll(View.FOCUS_RIGHT));
    }
    private void removeLast(){
        List<Lexicon.Entry> items=draft.items();
        if(!items.isEmpty()){draft.remove(items.size()-1);replace=-1;refresh();}
    }
    private void refresh(){
        if(root==null)return;
        root.setVisibility(enabled||transitioning?View.VISIBLE:View.GONE);
        output.setVisibility(isWechat(host.editorInfo())?View.GONE:View.VISIBLE);
        toggle.setText(transitioning?"切换中":enabled?"收起":"外星");toggle.setEnabled(allowed&&!transitioning);
        body.setVisibility(enabled?View.VISIBLE:View.GONE);
        if(!enabled)return;
        queryLabel.setText((replace>=0?"替换第 "+(replace+1)+" 项 · ":"")+(query.isEmpty()?"中文选词；英文、数字可直接输入":query));
        candidates.removeAllViews();
        for(int i=page*4;i<Math.min(matches.size(),page*4+4);i++){
            Lexicon.Match m=matches.get(i);LinearLayout tile=column();tile.setGravity(Gravity.CENTER);
            int shown=Math.min(4,m.entry.glyphs.size());
            LinearLayout glyphs=row();glyphs.setGravity(Gravity.CENTER);
            for(String id:m.entry.glyphs.subList(0,shown))glyphs.addView(glyph(id,shown==4?20:28));tile.addView(glyphs);
            TextView label=text(m.entry.label,11);label.setSingleLine(true);label.setGravity(Gravity.CENTER);tile.addView(label);
            TextView reason=text(kind(m.kind)+(m.entry.glyphs.size()>3?" · "+m.entry.glyphs.size()+"字形":""),10);reason.setGravity(Gravity.CENTER);tile.addView(reason);
            tile.setContentDescription("外星候选 "+m.entry.label+" "+kind(m.kind));
            tile.setOnClickListener(v->choose(m.entry));
            tile.setOnLongClickListener(v->{toast(m.entry.label+" · "+kind(m.kind)+"："+m.matchedTerm+(m.entry.note.isEmpty()?"":"\n"+m.entry.note));return true;});
            candidates.addView(tile,new LinearLayout.LayoutParams(0,-1,1));
        }
        hint.setText(query.isEmpty()?"逐词点选组合；点稿件中的词可替换，长按可删除":matches.isEmpty()?(Numerals.isInteger(Lexicon.normalize(query))?"整数最多支持 18 位十进制数字":"没有匹配结果，试试别的词或短语"):"共 "+matches.size()+" 项 · "+(page+1)+" / "+Math.max(1,(matches.size()+3)/4)+" 页 · 长按查看释义提示");
        draftRow.removeAllViews();List<Lexicon.Entry> items=draft.items();
        for(int i=0;i<items.size();i++){
            final int at=i;Lexicon.Entry e=items.get(i);LinearLayout tile=row();tile.setBackgroundColor(i==replace?0xffc5ddff:Color.WHITE);
            for(String id:e.glyphs)tile.addView(glyph(id,32));
            tile.setContentDescription("图片稿第 "+(i+1)+" 项 "+e.label);
            tile.setOnClickListener(v->{if(!enabled||transitioning)return;replace=replace==at?-1:at;resetQuery();refresh();});
            tile.setOnLongClickListener(v->{if(transitioning)return true;draft.remove(at);replace=-1;refresh();return true;});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-1);lp.setMargins(dp(3),0,dp(3),0);draftRow.addView(tile,lp);
        }
        if(items.isEmpty())draftRow.addView(text("图片稿为空",12));
    }
    private String kind(Lexicon.Kind kind){switch(kind){case NUMBER:return "数值";case EXACT:return "释义";case ALIAS:return "别名";case RELATED:return "相关";case PARTIAL:return "部分匹配";default:return "拼写相近";}}
    private void dismissPreview(){if(previewPopup!=null){PopupWindow popup=previewPopup;previewPopup=null;popup.dismiss();}}
    private void preview(){
        dismissPreview();
        if(draft.items().isEmpty()){toast("先点选外星候选");return;}
        // Render in a keyboard popup so opening preview does not leave the input session.
        try{
            android.graphics.Bitmap bitmap=images.previewDraft(draft);
            ImageLayout layout=ImageLayout.forGroups(draft.groups());
            ImageView image=new ImageView(context);image.setImageBitmap(bitmap);image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            ScrollView scroll=new ScrollView(context);scroll.setBackgroundColor(AlienPalette.load(context).background);
            scroll.addView(image,new android.widget.FrameLayout.LayoutParams(-1,-2));
            LinearLayout container=column();container.setBackgroundColor(Color.WHITE);
            TextView summary=text(layout.count+" 个字形 · "+layout.rows+" 行",12);summary.setGravity(Gravity.CENTER);
            LinearLayout header=row();header.addView(summary,new LinearLayout.LayoutParams(0,dp(32),1));
            header.addView(button("关闭",this::dismissPreview),new LinearLayout.LayoutParams(dp(52),dp(32)));container.addView(header);
            container.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            // A focusable IME popup makes Android finish the real editor session and lose its draft.
            PopupWindow popup=new PopupWindow(container,Math.min(root.getWidth(),dp(400)),dp(272),false);
            popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
            popup.setOnDismissListener(()->{image.setImageDrawable(null);if(previewPopup==popup)previewPopup=null;});
            previewPopup=popup;
            popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.WHITE));popup.setOutsideTouchable(true);popup.setElevation(dp(8));
            popup.showAtLocation(root,Gravity.CENTER,0,0);
        }catch(Exception e){dismissPreview();toast("暂时无法预览图片");}
    }
    private void send(boolean share){
        if(!enabled||transitioning)return;
        if(!query.isEmpty()||host.isComposing()){toast("请先选定当前词语，或点重输清除查询");return;}
        if(draft.items().isEmpty()){toast("先点选外星候选");return;}
        EditorInfo info=host.editorInfo();boolean supports=false;
        if(info!=null&&info.contentMimeTypes!=null)for(String type:info.contentMimeTypes)if(ClipDescription.compareMimeTypes("image/png",type))supports=true;
        if(!share&&isWechat(info)){toast("请用复制图，然后在微信中粘贴");return;}
        if(!share&&!supports){toast("此输入框未声明图片支持，请用复制图或分享");return;}
        try{
            Uri uri=host.imageUri(images.renderDraft(draft));
            if(share){
                Intent send=imageShareIntent(uri,null);
                shareReturnPackage=info==null?null:info.packageName;
                context.startActivity(Intent.createChooser(send,"分享外星文字").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }else{
                InputConnection target=host.editor();
                boolean ok=target!=null&&target.commitContent(new InputContentInfo(uri,new ClipDescription("外星文字",new String[]{"image/png"}),null),InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,null);
                if(ok){draft.clear();replace=-1;refresh();}else toast("应用没有接收图片，稿件已保留");
            }
        }catch(Exception e){shareReturnPackage=null;toast("图片操作未完成，稿件已保留");}
    }
    // Hide direct insertion in WeChat after the reported URI-as-text behavior.
    // Clipboard output remains an explicit user action; never launch sharing automatically.
    static boolean isWechat(EditorInfo info){return info!=null&&"com.tencent.mm".equals(info.packageName);}
    static Intent imageShareIntent(Uri uri,String targetPackage){
        Intent send=new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(new ClipData(new ClipDescription("外星组合图片",new String[]{"image/png"}),new ClipData.Item(uri)));
        if(targetPackage!=null)send.setPackage(targetPackage);
        return send;
    }
    private void copyCode(){
        if(draft.items().isEmpty()){toast("先点选外星候选");return;}
        ((ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("外星字形编码",PngMessage.encode(draft.groups())));
        toast("已复制可恢复的字形编码");
    }
    private void copyImage(){
        if(!enabled||transitioning)return;
        if(!query.isEmpty()||host.isComposing()){toast("请先选定当前词语，或点重输清除查询");return;}
        if(draft.items().isEmpty()){toast("先点选外星候选");return;}
        try{
            Uri uri=host.imageUri(images.renderDraft(draft));
            ClipboardManager clipboard=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newUri(context.getContentResolver(),"外星文字组合图",uri));
            toast("组合图片已复制，可在支持图片粘贴的应用中粘贴");
        }catch(Exception e){toast("图片复制未完成，稿件已保留");}
    }
    private int dp(int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    private LinearLayout row(){LinearLayout r=new LinearLayout(context);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private LinearLayout column(){LinearLayout r=new LinearLayout(context);r.setOrientation(LinearLayout.VERTICAL);return r;}
    private TextView text(String value,int size){TextView t=new TextView(context);t.setText(value);t.setTextSize(size);t.setTextColor(0xff152338);t.setPadding(dp(4),0,dp(4),0);return t;}
    private Button button(String label,Runnable action){Button b=new Button(context);b.setText(label);b.setTextSize(11);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(0,0,0,0);b.setOnClickListener(v->action.run());return b;}
    private void weighted(LinearLayout row,View view,int height){row.addView(view,new LinearLayout.LayoutParams(0,dp(height),1));}
    private ImageView glyph(String id,int size){ImageView v=new ImageView(context);v.setImageBitmap(images.glyph(id));v.setColorFilter(0xff152338,android.graphics.PorterDuff.Mode.SRC_IN);v.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size)));v.setContentDescription(id);return v;}
    private void toast(String message){Toast.makeText(context,message,Toast.LENGTH_SHORT).show();}
}
