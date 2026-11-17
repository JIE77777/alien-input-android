package io.github.alieninput.androidsemantic;

import android.app.*;
import android.content.*;
import android.content.ClipboardManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import io.github.alieninput.semantic.*;
import java.io.*;
import java.util.*;

/** A standalone writing surface backed by the same draft as the keyboard. */
public final class WriteMessageActivity extends Activity {
    private ProductUi ui;private AlienImages images;private Lexicon lexicon;private GlyphNames names;
    private final Draft draft=new Draft();private LinearLayout candidates,items;private EditText query;private ImageView preview;
    private TextView caption,matchStatus;private Button copy;private Bitmap bitmap;private int replace=-1,page=0;private boolean composing,compact;
    private List<Lexicon.Match> matches=Collections.emptyList();
    @Override public void onCreate(Bundle state){super.onCreate(state);ui=new ProductUi(this);images=new AlienImages(this);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        try{lexicon=new Lexicon(new InputStreamReader(getAssets().open("alien/lexicon.tsv"),"UTF-8"));names=new GlyphNames(lexicon);}catch(IOException e){Toast.makeText(this,"字库暂时无法打开，请重新启动应用",Toast.LENGTH_LONG).show();finish();return;}
        LinearLayout root=ui.screen(this);root.addView(ui.header(this,"写消息"));ui.gap(root,14);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);LinearLayout body=ui.column();scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setBackground(ui.shape(0xff090a08,18));preview.setClipToOutline(true);preview.setContentDescription("消息预览，点击放大");preview.setOnClickListener(v->showPreview());ui.block(body,preview,158);ui.gap(body,10);
        caption=ui.text("",13,ProductUi.MUTED);body.addView(caption);ui.gap(body,8);HorizontalScrollView strip=new HorizontalScrollView(this);strip.setHorizontalScrollBarEnabled(false);items=ui.row();strip.addView(items);ui.block(body,strip,46);ui.gap(body,18);
        query=new EditText(this){@Override public InputConnection onCreateInputConnection(EditorInfo info){InputConnection target=super.onCreateInputConnection(info);if(target==null)return null;return new InputConnectionWrapper(target,false){@Override public boolean finishComposingText(){boolean ok=super.finishComposingText();post(()->search());return ok;}@Override public boolean commitText(CharSequence text,int cursor){boolean ok=super.commitText(text,cursor);post(()->search());return ok;}};}};
        query.setTextSize(17);query.setTextColor(ProductUi.INK);query.setHintTextColor(ProductUi.MUTED);query.setHint("输入词语，如「你」「食物」");query.setSingleLine(true);query.setPrivateImeOptions("alien-compose");query.setImeOptions(EditorInfo.IME_ACTION_DONE);query.setPadding(ui.dp(16),0,ui.dp(16),0);query.setBackground(ui.shape(ProductUi.PAPER,14));ui.block(body,query,54);
        query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){}public void afterTextChanged(Editable s){page=0;search();}});
        query.setOnEditorActionListener((v,a,event)->{if(a==EditorInfo.IME_ACTION_DONE){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(query.getWindowToken(),0);return true;}return false;});
        ui.gap(body,12);matchStatus=ui.text("",12,ProductUi.MUTED);body.addView(matchStatus);ui.gap(body,10);candidates=ui.column();body.addView(candidates);ui.gap(body,16);
        LinearLayout actions=ui.row();Button read=ui.button("读图",false,()->{query.clearFocus();startActivity(new Intent(this,ReadImageActivity.class));});actions.addView(read,new LinearLayout.LayoutParams(ui.dp(78),ui.dp(52)));View gap=new View(this);actions.addView(gap,new LinearLayout.LayoutParams(ui.dp(10),1));copy=ui.button("复制图片",true,this::copyImage);actions.addView(copy,new LinearLayout.LayoutParams(0,ui.dp(52),1));ui.gap(root,10);root.addView(actions);
        root.setFocusableInTouchMode(true);root.requestFocus();setContentView(root);root.requestApplyInsets();search();
        root.getViewTreeObserver().addOnGlobalLayoutListener(()->{boolean next=root.getHeight()-root.getPaddingTop()-root.getPaddingBottom()<ui.dp(600);if(next!=compact){compact=next;preview.setVisibility(compact?View.GONE:View.VISIBLE);caption.setVisibility(compact?View.GONE:View.VISIBLE);page=0;search();}});
    }
    @Override protected void onResume(){super.onResume();if(names==null)return;draft.clear();for(List<String> group:ImageDraftStore.read(this))draft.add(Lexicon.Entry.fromImage(group,names.group(group)));replace=-1;refreshDraft();}
    private void search(){if(lexicon==null)return;composing=BaseInputConnection.getComposingSpanStart(query.getText())>=0;String term=query.getText().toString().trim();matches=composing?Collections.emptyList():lexicon.search(term,Collections.emptyMap());candidates.removeAllViews();
        if(term.isEmpty()){matchStatus.setText(replace>=0?"选一个新词，替换第 "+(replace+1)+" 项":"中文、英文和整数都可以；一次选一个词。");LinearLayout suggestions=ui.row();for(String example:new String[]{"你","食物","50"}){Button chip=ui.button(example,false,()->query.setText(example));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,ui.dp(44),1);lp.setMargins(0,0,ui.dp(8),0);suggestions.addView(chip,lp);}candidates.addView(suggestions);return;}
        matchStatus.setText(composing?"先选定中文，再挑选字形":matches.isEmpty()?"没有找到，试试更短的词或近义词":"点选加入消息 · "+matches.size()+" 个候选");
        int pageSize=compact?3:6;for(int start=page*pageSize;start<Math.min(matches.size(),page*pageSize+pageSize);start+=3){LinearLayout row=ui.row();for(int i=start;i<Math.min(matches.size(),start+3);i++){Lexicon.Entry entry=matches.get(i).entry;LinearLayout tile=ui.card();tile.setPadding(ui.dp(6),ui.dp(12),ui.dp(6),ui.dp(10));tile.setGravity(Gravity.CENTER);LinearLayout glyphs=ui.row();glyphs.setGravity(Gravity.CENTER);int n=Math.min(entry.glyphs.size(),3);for(int j=0;j<n;j++){ImageView glyph=new ImageView(this);glyph.setImageBitmap(images.glyph(entry.glyphs.get(j)));glyph.setColorFilter(ProductUi.INK);glyphs.addView(glyph,new LinearLayout.LayoutParams(ui.dp(n>1?26:38),ui.dp(40)));}tile.addView(glyphs);ui.gap(tile,8);TextView label=ui.text(entry.label,13,ProductUi.INK);label.setMaxLines(1);label.setEllipsize(TextUtils.TruncateAt.END);tile.addView(label);tile.setContentDescription("选择 "+entry.label);tile.setOnClickListener(v->choose(entry));tile.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle(entry.label).setMessage(entry.note.isEmpty()?entry.label:entry.note).setPositiveButton("加入消息",(d,w)->choose(entry)).setNegativeButton("返回",null).show();return true;});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,ui.dp(100),1);lp.setMargins(0,0,ui.dp(7),ui.dp(8));row.addView(tile,lp);}for(int i=row.getChildCount();i<3;i++)row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));candidates.addView(row);}
        if(matches.size()>pageSize){LinearLayout paging=ui.row();Button prev=ui.button("上一页",false,()->{page--;search();}),next=ui.button("下一页",false,()->{page++;search();});prev.setEnabled(page>0);next.setEnabled((page+1)*pageSize<matches.size());paging.addView(prev,new LinearLayout.LayoutParams(0,ui.dp(40),1));TextView count=ui.text((page+1)+" / "+((matches.size()+pageSize-1)/pageSize),12,ProductUi.MUTED);count.setGravity(Gravity.CENTER);paging.addView(count,new LinearLayout.LayoutParams(ui.dp(60),-2));paging.addView(next,new LinearLayout.LayoutParams(0,ui.dp(40),1));candidates.addView(paging);}
    }
    private void choose(Lexicon.Entry entry){if(composing)return;boolean ok=replace>=0?draft.replace(replace,entry):draft.add(entry);if(!ok){Toast.makeText(this,"一条消息最多容纳 120 个字形",Toast.LENGTH_SHORT).show();return;}replace=-1;ImageDraftStore.save(this,draft.groups());query.setText("");refreshDraft();}
    private void refreshDraft(){items.removeAllViews();copy.setEnabled(!draft.items().isEmpty());caption.setText(draft.items().isEmpty()?"你的来信会出现在这里":draft.glyphs().size()+" 个字形 · 已保存，点词条可修改");
        Bitmap old=bitmap;bitmap=null;try{if(!draft.items().isEmpty())bitmap=images.previewDraft(draft);}catch(IOException ignored){}preview.setImageBitmap(bitmap);if(old!=null)old.recycle();
        for(int i=0;i<draft.items().size();i++){final int at=i;Lexicon.Entry entry=draft.items().get(i);Button chip=ui.button(entry.label,false,()->new AlertDialog.Builder(this).setTitle(entry.label).setItems(new String[]{"替换这个词","删除这个词"},(dialog,which)->{if(which==0){replace=at;query.setText("");query.requestFocus();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(query,InputMethodManager.SHOW_IMPLICIT);}else{draft.remove(at);replace=-1;ImageDraftStore.save(this,draft.groups());refreshDraft();search();}}).show());chip.setTextSize(13);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,ui.dp(40));lp.rightMargin=ui.dp(8);items.addView(chip,lp);}
        if(draft.items().isEmpty())items.addView(ui.text("从下面选一个词开始。",13,ProductUi.MUTED));
    }
    private void showPreview(){if(bitmap==null)return;ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setAdjustViewBounds(true);ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(AlienPalette.load(this).background);scroll.addView(image);new AlertDialog.Builder(this).setView(scroll).setPositiveButton("复制图片",(d,w)->copyImage()).setNegativeButton("继续写",null).show();}
    private void copyImage(){if(draft.items().isEmpty())return;try{File file=images.renderDraft(draft);Uri uri=new Uri.Builder().scheme("content").authority(getPackageName()+".alien-images").appendPath(file.getName()).build();((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newUri(getContentResolver(),"外星来信",uri));Toast.makeText(this,"图片已复制，回到聊天中粘贴即可",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"图片未能复制，消息已保留",Toast.LENGTH_SHORT).show();}}
    @Override protected void onDestroy(){if(bitmap!=null){preview.setImageDrawable(null);bitmap.recycle();}super.onDestroy();}
}
