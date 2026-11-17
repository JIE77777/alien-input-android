package io.github.alieninput.androidsemantic;

import android.content.Context;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import io.github.alieninput.semantic.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.Future;

/** A compact reader surface hosted inside the keyboard, including candidate confirmation. */
final class KeyboardImageReader {
    private final Context context;private final ProductUi ui;private final AlienImages images;private final GlyphNames names;
    private final Runnable added,gallery;private final Handler main=new Handler(Looper.getMainLooper());
    final LinearLayout view;private final LinearLayout tiles,paging;private final TextView status,pageLabel;private final Button add,previous,next;private final CheckBox number;
    private ImageInput.Decoded decoded;private GlyphReader.Symbol choosing;private Future<?> task;private int token,page;private boolean reading;
    KeyboardImageReader(Context context,GlyphNames names,AlienImages images,Runnable added,Runnable gallery){this.context=context;this.names=names;this.images=images;this.added=added;this.gallery=gallery;ui=new ProductUi(context);view=ui.column();view.setPadding(ui.dp(6),0,ui.dp(6),ui.dp(4));
        LinearLayout actions=ui.row();action(actions,"粘贴图片",this::paste,false);action(actions,"从相册选",()->{cancel();gallery.run();},false);view.addView(actions);
        status=ui.text("复制图片后点粘贴图片，也可以从相册选择。",12,ProductUi.MUTED);status.setPadding(ui.dp(4),ui.dp(6),ui.dp(4),ui.dp(4));status.setMaxLines(2);view.addView(status);
        tiles=ui.row();view.addView(tiles,new LinearLayout.LayoutParams(-1,ui.dp(72)));
        paging=ui.row();previous=action(paging,"上一页",()->{if(choosing!=null)choosing=null;else page--;show();},false);pageLabel=ui.text("",12,ProductUi.MUTED);pageLabel.setGravity(Gravity.CENTER);paging.addView(pageLabel,new LinearLayout.LayoutParams(0,ui.dp(30),1));next=action(paging,"下一页",()->{page++;show();},false);view.addView(paging);
        number=new CheckBox(context);number.setTextSize(12);number.setTextColor(ProductUi.INK);number.setMinimumHeight(0);number.setPadding(0,0,0,0);view.addView(number,new LinearLayout.LayoutParams(-1,ui.dp(30)));add=ui.button("加入图片稿",false,this::append);view.addView(add,new LinearLayout.LayoutParams(-1,ui.dp(36)));show();
    }
    private Button action(LinearLayout row,String label,Runnable action,boolean primary){Button b=ui.button(label,primary,action);b.setTextSize(12);b.setPadding(ui.dp(4),0,ui.dp(4),0);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,ui.dp(34),1);lp.setMargins(ui.dp(2),ui.dp(2),ui.dp(2),ui.dp(2));row.addView(b,lp);return b;}
    void cancel(){token++;if(task!=null)task.cancel(true);if(reading)status.setText("读取已取消，可以重新选图。");reading=false;}
    boolean isReading(){return reading;}
    void paste(){Uri uri=ImageInput.clipboard(context);if(uri==null){status.setText("剪贴板里没有图片，可从相册选择。");return;}start(()->ImageInput.read(context,uri));}
    void pending(){if(!reading&&ImageInput.hasPending(context))start(()->ImageInput.consume(context));}
    interface Source{byte[] read()throws IOException;}
    void start(Source source){cancel();int generation=token;reading=true;decoded=null;choosing=null;page=0;number.setChecked(false);show();status.setText("正在读图…");
        task=ImageInput.WORKER.submit(()->{try{ImageInput.Decoded result=ImageInput.decode(context,source.read(),(row,total)->main.post(()->{if(token==generation)status.setText("正在读第 "+row+" / "+total+" 行…");}));main.post(()->{if(token!=generation)return;reading=false;decoded=result;show();});}catch(Exception e){String message=e instanceof IOException?e.getMessage():"这张图片没读清，请换一张试试";main.post(()->{if(token==generation){reading=false;status.setText(message==null?"读取已取消":message);}});}});
    }
    private List<GlyphReader.Symbol> symbols(){List<GlyphReader.Symbol> out=new ArrayList<>();if(decoded!=null)for(GlyphReader.Row row:decoded.result.rows)out.addAll(row.symbols);return out;}
    private void show(){tiles.removeAllViews();boolean readable=decoded!=null&&decoded.result.readable;List<GlyphReader.Symbol> symbols=symbols();add.setEnabled(readable&&decoded.result.confirmed()&&choosing==null);add.setVisibility(readable?View.VISIBLE:View.GONE);paging.setVisibility(readable&&(symbols.size()>4||choosing!=null)?View.VISIBLE:View.GONE);number.setVisibility(View.GONE);
        if(!readable){if(decoded!=null)status.setText("没读清，请选择保留四周留白的完整图片。");TextView empty=ui.text("读出的字形会显示在这里",13,ProductUi.MUTED);empty.setGravity(Gravity.CENTER);tiles.addView(empty,new LinearLayout.LayoutParams(-1,-1));return;}
        int pending=0;for(GlyphReader.Symbol s:symbols)if(!s.confirmed)pending++;
        status.setText(choosing!=null?"选择正确的字形":pending>0?"读出 "+symbols.size()+" 个字形 · "+pending+" 个待确认":"读出 "+symbols.size()+" 个字形 · 点字形可修改");
        if(choosing!=null){GlyphReader.Symbol selected=choosing;for(String id:selected.candidates)tile(id,false,()->{selected.id=id;selected.confirmed=true;choosing=null;number.setChecked(false);show();});previous.setText("返回结果");previous.setEnabled(true);next.setVisibility(View.INVISIBLE);pageLabel.setText("候选");return;}
        int total=(symbols.size()+3)/4;page=Math.max(0,Math.min(page,total-1));for(int i=page*4;i<Math.min(symbols.size(),page*4+4);i++){GlyphReader.Symbol symbol=symbols.get(i);tile(symbol.id,!symbol.confirmed,()->{choosing=symbol;show();});}
        previous.setText("上一页");previous.setEnabled(page>0);next.setVisibility(View.VISIBLE);next.setEnabled(page+1<total);pageLabel.setText((page+1)+" / "+total);
        String value=Numerals.decode(decoded.result.glyphs());if(value!=null){number.setVisibility(View.VISIBLE);number.setText("合为一个数字："+value);}
    }
    private void tile(String id,boolean pending,Runnable action){LinearLayout box=ui.column();box.setGravity(Gravity.CENTER);box.setBackground(ui.shape(pending?0xfff5dfb3:ProductUi.PAPER,10));ImageView glyph=new ImageView(context);glyph.setImageBitmap(images.glyph(id));glyph.setColorFilter(ProductUi.INK);box.addView(glyph,new LinearLayout.LayoutParams(ui.dp(40),ui.dp(40)));TextView label=ui.text(names.name(id)+(pending?" · 确认":""),11,ProductUi.INK);label.setGravity(Gravity.CENTER);label.setMaxLines(1);box.addView(label);box.setContentDescription("读图字形 "+names.name(id)+(pending?" 待确认":" 已确认"));box.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(ui.dp(3),0,ui.dp(3),0);tiles.addView(box,lp);}
    private void append(){if(decoded==null||!decoded.result.confirmed()||choosing!=null)return;List<List<String>> groups=decoded.result.groups();if(number.isChecked()&&Numerals.decode(decoded.result.glyphs())!=null)groups=Collections.singletonList(decoded.result.glyphs());else if(decoded.metadata!=null){List<String> flat=new ArrayList<>();for(List<String> g:decoded.metadata.groups)flat.addAll(g);if(flat.equals(decoded.result.glyphs()))groups=decoded.metadata.groups;}
        if(!ImageDraftStore.append(context,groups)){status.setText("加入后超过 120 个字形，请先整理图片稿。");return;}decoded=null;show();status.setText("已加入图片稿");added.run();
    }
}
