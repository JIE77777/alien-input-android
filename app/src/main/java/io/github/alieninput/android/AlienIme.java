package io.github.alieninput.android;

import android.content.*;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.*;

public final class AlienIme extends InputMethodService {
    private Codebook book;
    private LinearLayout panel, candidates, keyArea, draft;
    private TextView status, composition;
    private final List<String> roots=new ArrayList<>(), staged=new ArrayList<>();
    private final Map<String,Integer> learned=new HashMap<>();
    private List<Codebook.Match> matches=Collections.emptyList();
    private int page=0, candidatePage=0, mode=0; // 0 PNG, 1 Chinese gloss, 2 stable ID
    private boolean alternate=false, sensitive=false, ascii=false;
    private boolean compact=false;
    private static final String[] MODES={"图片", "释义", "编号"};
    @Override public void onCreate(){super.onCreate();book=Assets.book(this);
        for(Map.Entry<String,?> e:getSharedPreferences("ranking",0).getAll().entrySet())
            if(e.getValue() instanceof Integer) learned.put(e.getKey(),(Integer)e.getValue());
        mode=getSharedPreferences("settings",0).getInt("mode",0);if(mode<0||mode>2)mode=0;
    }
    @Override public boolean onEvaluateFullscreenMode(){return false;}
    @Override public View onCreateInputView(){
        compact=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        panel=Ui.column(this);panel.setBackgroundColor(Ui.BG);panel.setPadding(4,5,4,5);
        panel.setOnApplyWindowInsetsListener((v,insets)->{
            panel.setPadding(4,5,4,Math.max(5,insets.getSystemWindowInsetBottom()));return insets;
        });
        LinearLayout bar=Ui.row(this);
        Ui.weight(bar,Ui.button(this,"🌐 切换",()->((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker()),compact?28:32);
        Ui.weight(bar,Ui.button(this,"输出模式",this::cycleMode),compact?28:32);
        Ui.weight(bar,Ui.button(this,"ABC / 字根",()->{if(!staged.isEmpty()){toast("请先插入、分享或清空图片稿");return;}ascii=!ascii;roots.clear();candidatePage=0;refresh();}),compact?28:32);
        Ui.weight(bar,Ui.button(this,"清空",()->{roots.clear();staged.clear();candidatePage=0;refresh();}),compact?28:32);
        panel.addView(bar);
        status=Ui.text(this,"",compact?10:12,Ui.MUTED);status.setPadding(8,3,8,3);status.setSingleLine(true);panel.addView(status);
        composition=Ui.text(this,"",compact?11:13,Ui.INK);composition.setPadding(8,2,8,2);composition.setSingleLine(true);panel.addView(composition);
        LinearLayout candidateRow=Ui.row(this);
        Button prev=Ui.button(this,"‹",()->{candidatePage=Math.max(0,candidatePage-1);refreshCandidates();});candidateRow.addView(prev,new LinearLayout.LayoutParams(Ui.dp(this,32),Ui.dp(this,compact?44:72)));
        candidates=Ui.row(this);candidateRow.addView(candidates,new LinearLayout.LayoutParams(0,Ui.dp(this,compact?44:72),1));
        Button next=Ui.button(this,"›",()->{if((candidatePage+1)*4<matches.size())candidatePage++;refreshCandidates();});candidateRow.addView(next,new LinearLayout.LayoutParams(Ui.dp(this,32),Ui.dp(this,compact?44:72)));panel.addView(candidateRow);
        HorizontalScrollView scroller=new HorizontalScrollView(this);scroller.setHorizontalScrollBarEnabled(false);
        draft=Ui.row(this);scroller.addView(draft);panel.addView(scroller,new LinearLayout.LayoutParams(-1,Ui.dp(this,compact?0:32)));
        keyArea=Ui.column(this);panel.addView(keyArea);
        LinearLayout bottom=Ui.row(this);
        Ui.weight(bottom,Ui.button(this,"⌫",this::backspace),compact?34:44);
        Ui.weight(bottom,Ui.button(this,"空格 / 选字",this::space),compact?34:44);
        Ui.weight(bottom,Ui.button(this,"分享图",()->sendImage(true)),compact?34:44);
        Ui.weight(bottom,Ui.button(this,"插入 / ↵",this::enter),compact?34:44);
        panel.addView(bottom);refresh();return panel;
    }
    @Override public void onStartInput(EditorInfo info,boolean restarting){
        super.onStartInput(info,restarting);learned.clear();
        for(Map.Entry<String,?> e:getSharedPreferences("ranking",0).getAll().entrySet()) if(e.getValue() instanceof Integer) learned.put(e.getKey(),(Integer)e.getValue());
        roots.clear();staged.clear();candidatePage=0;alternate=false;
        int type=info.inputType&InputType.TYPE_MASK_CLASS, variation=info.inputType&InputType.TYPE_MASK_VARIATION;
        sensitive=(type==InputType.TYPE_CLASS_TEXT&&(variation==InputType.TYPE_TEXT_VARIATION_PASSWORD||variation==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD||variation==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (type==InputType.TYPE_CLASS_NUMBER&&variation==InputType.TYPE_NUMBER_VARIATION_PASSWORD)||
            (info.imeOptions&EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)!=0;
        ascii=sensitive||type==InputType.TYPE_CLASS_NUMBER||type==InputType.TYPE_CLASS_PHONE||type==InputType.TYPE_CLASS_DATETIME;
        refresh();
    }
    @Override public void onFinishInput(){roots.clear();staged.clear();matches=Collections.emptyList();super.onFinishInput();}
    private void cycleMode(){
        if(!staged.isEmpty()){toast("请先插入、分享或清空图片稿");return;}
        mode=(mode+1)%3;getSharedPreferences("settings",0).edit().putInt("mode",mode).apply();refresh();
    }
    private void refresh(){
        matches=ascii?Collections.emptyList():book.match(roots,sensitive?Collections.emptyMap():learned);
        if(panel==null)return;
        ((View)candidates.getParent()).setVisibility(ascii?View.GONE:View.VISIBLE);
        composition.setVisibility(ascii?View.GONE:View.VISIBLE);
        ((View)draft.getParent()).setVisibility(ascii||compact?View.GONE:View.VISIBLE);
        if(candidatePage*4>=matches.size())candidatePage=0;
        status.setText((ascii?"英文直输":MODES[mode]+"模式 · "+(mode==0?(supportsImage()?"此输入框支持插图":"此输入框未声明插图支持，可分享图片"):"点击候选直接输入"))+(sensitive?" · 不学习":""));
        StringBuilder s=new StringBuilder();for(String id:roots){if(s.length()>0)s.append(" · ");s.append(book.label(id));}
        composition.setText((staged.isEmpty()?"":"图片稿 "+staged.size()+" 字 · ")+(roots.isEmpty()?(ascii?"英文直输 · 可用 🌐 切换其他键盘":"选字根组字，或浏览候选 · "+matches.size()+" 项"):s.toString()));
        refreshCandidates();renderKeys();
        draft.removeAllViews();
        if(staged.isEmpty()) {TextView t=Ui.text(this,mode==0?"图片稿：选中的字会排列在这里":"释义是研究注释，编号是可复制的普通文本",12,Ui.MUTED);draft.addView(t);}
        else for(String id:staged)draft.addView(Ui.glyph(this,id,36));
    }
    private void refreshCandidates(){
        if(candidates==null)return;candidates.removeAllViews();
        for(int j=0;j<4;j++) {
            int at=candidatePage*4+j;
            if(at>=matches.size()){Ui.weight(candidates,new View(this),compact?40:68);continue;}
            Codebook.Match match=matches.get(at);Codebook.Entry e=match.entry;
            LinearLayout tile=Ui.column(this);tile.setGravity(Gravity.CENTER);tile.setBackground(Ui.bg(Color.WHITE,Ui.dp(this,8)));
            LinearLayout images=Ui.row(this);images.setGravity(Gravity.CENTER);
            for(int k=0;k<Math.min(2,e.glyphs.length);k++)images.addView(Ui.glyph(this,e.glyphs[k],compact?20:(e.glyphs.length>1?26:34)));
            tile.addView(images);
            TextView label=Ui.text(this,e.label,11,Ui.INK);label.setSingleLine(true);label.setGravity(Gravity.CENTER);tile.addView(label);
            TextView code=Ui.text(this,book.spelling(e),9,Ui.MUTED);code.setSingleLine(true);if(!compact)tile.addView(code);
            tile.setContentDescription(e.label+"，"+e.id+"，按键 "+book.spelling(e));
            tile.setOnClickListener(v->choose(e));tile.setOnLongClickListener(v->{toast(e.label+" · "+e.id+" · 拆码 "+book.spelling(e));return true;});Ui.weight(candidates,tile,compact?40:68);
        }
    }
    private void renderKeys(){
        keyArea.removeAllViews();
        if(ascii){
            String[] rows=alternate?new String[]{"!@#$%^&*()","QWERTYUIOP","ASDFGHJKL","ZXCVBNM.,?!"}:new String[]{"1234567890","qwertyuiop","asdfghjkl","zxcvbnm.,?!"};
            for(String chars:rows){LinearLayout row=Ui.row(this);for(int j=0;j<chars.length();j++){String s=chars.substring(j,j+1);Ui.weight(row,Ui.button(this,s,()->commit(s)),compact?30:42);}keyArea.addView(row);}
        } else {
            int rows=compact?2:4, pages=(book.keys.size()+rows*6-1)/(rows*6);
            page=Math.min(page,pages-1);
            int start=page*rows*6;
            for(int r=0;r<rows;r++){
                LinearLayout row=Ui.row(this);
                for(int col=0;col<6;col++){
                    int idx=start+r*6+col;
                    if(idx>=book.keys.size()){Ui.weight(row,new View(this),compact?30:44);continue;}
                    String[] key=book.keys.get(idx);String id=key[alternate?3:2],symbol=key[alternate?1:0];
                    LinearLayout tile=Ui.column(this);tile.setGravity(Gravity.CENTER);tile.setBackground(Ui.bg(Color.WHITE,Ui.dp(this,8)));
                    tile.addView(Ui.glyph(this,id,compact?16:27));TextView t=Ui.text(this,symbol+" "+book.label(id),9,Ui.INK);t.setSingleLine(true);tile.addView(t);
                    tile.setContentDescription(symbol+"，"+book.label(id));tile.setOnClickListener(v->addRoot(id));
                    tile.setOnLongClickListener(v->{toast(symbol+" · "+book.label(id)+" · "+id);return true;});Ui.weight(row,tile,compact?30:44);
                }keyArea.addView(row);
            }
        }
        LinearLayout nav=Ui.row(this);
        Ui.weight(nav,Ui.button(this,alternate?"Shift 已开":"Shift / 副根",()->{alternate=!alternate;renderKeys();}),compact?28:36);
        Ui.weight(nav,Ui.button(this,ascii?"@":("字根 "+(page+1)+" / "+(compact?4:2)+" · 翻页"),()->{
            if(ascii){commit("@");}else{page=(page+1)%(compact?4:2);renderKeys();}
        }),compact?28:36);
        if(ascii) {Ui.weight(nav,Ui.button(this,"-",()->commit("-")),36);Ui.weight(nav,Ui.button(this,"_",()->commit("_")),36);}
        keyArea.addView(nav);
    }
    private void addRoot(String id){if(roots.size()>=Codebook.MAX_ROOTS){toast("拆码过长，请先选字");return;}roots.add(id);candidatePage=0;refresh();}
    private void choose(Codebook.Entry entry){
        if(ascii)return;
        if(mode==0){if(staged.size()+entry.glyphs.length>120){toast("每张图最多 120 字，请先插入或分享");return;}staged.addAll(Arrays.asList(entry.glyphs));}
        else if(!commit(mode==1?entry.label:serialize(entry.glyphs)))return;
        if(!sensitive){int n=Math.min(100000,learned.getOrDefault(entry.id,0)+1);learned.put(entry.id,n);getSharedPreferences("ranking",0).edit().putInt(entry.id,n).apply();}
        roots.clear();candidatePage=0;refresh();
    }
    static String serialize(String[] ids){StringBuilder s=new StringBuilder();for(String id:ids)s.append('[').append(id).append(']');return s.toString();}
    private boolean commit(String text){InputConnection ic=getCurrentInputConnection();return ic!=null&&ic.commitText(text,1);}
    private void backspace(){
        if(!roots.isEmpty()){roots.remove(roots.size()-1);candidatePage=0;refresh();}
        else if(!staged.isEmpty()){staged.remove(staged.size()-1);refresh();}
        else {InputConnection ic=getCurrentInputConnection();if(ic!=null){CharSequence selected=ic.getSelectedText(0);if(selected!=null&&selected.length()>0)ic.commitText("",1);else ic.deleteSurroundingTextInCodePoints(1,0);}}
    }
    private void space(){if(!ascii&&!roots.isEmpty()){if(!matches.isEmpty())choose(matches.get(0).entry);else toast("无候选，请退格修改字根");}else if(!staged.isEmpty())toast("请继续选字，或插入图片");else commit(" ");}
    private void enter(){
        if(!roots.isEmpty()){space();return;}
        if(!staged.isEmpty()){sendImage(false);return;}
        InputConnection ic=getCurrentInputConnection();EditorInfo info=getCurrentInputEditorInfo();if(ic==null||info==null)return;
        int action=info.imeOptions&EditorInfo.IME_MASK_ACTION;
        if((info.imeOptions&EditorInfo.IME_FLAG_NO_ENTER_ACTION)==0&&action!=EditorInfo.IME_ACTION_NONE&&action!=EditorInfo.IME_ACTION_UNSPECIFIED)ic.performEditorAction(action);else commit("\n");
    }
    private boolean supportsImage(){EditorInfo e=getCurrentInputEditorInfo();if(e==null||e.contentMimeTypes==null)return false;for(String m:e.contentMimeTypes)if(ClipDescription.compareMimeTypes("image/png",m))return true;return false;}
    private void sendImage(boolean share){
        if(staged.isEmpty()){toast("先点选候选，把字加入图片稿");return;}
        if(!roots.isEmpty()){toast("先选定当前候选，或清除未完成拆码");return;}
        if(!share&&!supportsImage()){toast("此应用不支持键盘插图，请点「分享图」");return;}
        try {
            Uri uri=Assets.render(this,staged);
            if(share){
                Intent send=new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);send.setClipData(ClipData.newRawUri("Alien message",uri));
                Intent chooser=Intent.createChooser(send,"分享外星文字图片").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(chooser); // PNG remains cached; the editor session controls in-memory draft lifetime.
            } else {
                InputConnection ic=getCurrentInputConnection();
                boolean accepted=ic!=null&&ic.commitContent(new InputContentInfo(uri,new ClipDescription("Alien message",new String[]{"image/png"}),null),InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,null);
                if(accepted){staged.clear();refresh();}else toast("应用没有接收图片，稿件已保留，可使用分享");
            }
        }catch(Exception e){toast("图片操作未完成，稿件已保留");}
    }
    @Override public boolean onKeyDown(int code,KeyEvent event){
        if(event.isCtrlPressed()||event.isAltPressed()||event.isMetaPressed())return super.onKeyDown(code,event);
        if(code==KeyEvent.KEYCODE_DEL){backspace();return true;}
        if(code==KeyEvent.KEYCODE_SPACE){space();return true;}
        if(code==KeyEvent.KEYCODE_ENTER){enter();return true;}
        if(code==KeyEvent.KEYCODE_TAB&&!ascii){candidatePage=(candidatePage+1)*4<matches.size()?candidatePage+1:0;refreshCandidates();return true;}
        int ch=event.getUnicodeChar();
        if(!ascii&&ch!=0)for(String[] k:book.keys){if(k[0].equals(Character.toString((char)ch))){addRoot(k[2]);return true;}if(k[1].equals(Character.toString((char)ch))){addRoot(k[3]);return true;}}
        return super.onKeyDown(code,event);
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
