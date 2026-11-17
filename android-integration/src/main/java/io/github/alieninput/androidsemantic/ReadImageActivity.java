package io.github.alieninput.androidsemantic;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import io.github.alieninput.semantic.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Local image-reading flow shared by the IME and the app home. */
public final class ReadImageActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Future<?> task;
    private final Handler main=new Handler(Looper.getMainLooper());
    private LinearLayout content,results;
    private TextView status,numbers;
    private Button add,select,paste;
    private ImageView preview;
    private Bitmap bitmap;
    private GlyphReader.Result result;
    private PngMessage embedded;
    private GlyphNames names;
    private AlienImages images;
    private ProductUi ui;
    private int generation;
    private List<List<String>> explicitGroups;
    @Override public void onCreate(Bundle state){super.onCreate(state);images=new AlienImages(this);ui=new ProductUi(this);
        content=ui.screen(this);content.addView(ui.header(this,"读图"));ui.gap(content,18);
        content.addView(ui.text("读出图片里的字形，加入键盘图片稿。",14,ProductUi.MUTED));ui.gap(content,16);
        LinearLayout actions=row();select=button("选择图片",this::pick);paste=button("粘贴图片",this::paste);actions.addView(select,new LinearLayout.LayoutParams(0,dp(52),1));actions.addView(new View(this),new LinearLayout.LayoutParams(dp(10),1));actions.addView(paste,new LinearLayout.LayoutParams(0,dp(52),1));content.addView(actions);ui.gap(content,14);
        status=text("选择聊天中保存的完整图片。",13);content.addView(status);ui.gap(content,10);
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);LinearLayout body=column();preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setMaxHeight(dp(220));preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setClipToOutline(true);preview.setBackground(ui.shape(0xff090a08,16));preview.setVisibility(View.GONE);preview.setContentDescription("所选外星图片");body.addView(preview,new LinearLayout.LayoutParams(-1,-2));
        numbers=text("",15);body.addView(numbers);results=column();body.addView(results);
        LinearLayout empty=ui.card();empty.addView(ui.title("从相册或剪贴板读取",18));ui.gap(empty,12);empty.addView(ui.text("读出的字形会按行排列。\n点字形可以更换候选，橙色项等你确认。",14,ProductUi.MUTED));ui.gap(empty,18);empty.addView(ui.text("保留四周留白，更容易读清。",12,ProductUi.MUTED));results.addView(empty);
        scroll.addView(body);content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));ui.gap(content,12);
        add=ui.button("加入图片稿",true,this::addDraft);add.setEnabled(false);content.addView(add,new LinearLayout.LayoutParams(-1,dp(52)));setContentView(content);content.requestApplyInsets();
    }
    private void pick(){
        if(Build.VERSION.SDK_INT>=33){try{startActivityForResult(new Intent("android.provider.action.PICK_IMAGES").setType("image/*"),42);return;}catch(ActivityNotFoundException ignored){}}
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
        try{startActivityForResult(intent,42);}catch(ActivityNotFoundException e){status.setText("没有找到相册，请复制图片后点粘贴图片。");}
    }
    @Override protected void onActivityResult(int request,int code,Intent data){super.onActivityResult(request,code,data);if(request==42&&code==RESULT_OK&&data!=null&&data.getData()!=null)read(data.getData());}
    private void paste(){ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);ClipData clip=clipboard.getPrimaryClip();Uri uri=null;if(clip!=null)for(int i=0;i<clip.getItemCount();i++){Uri candidate=clip.getItemAt(i).getUri();if(candidate!=null&&"content".equals(candidate.getScheme())){uri=candidate;break;}}
        if(uri==null){status.setText("剪贴板里没有图片。也可以先保存图片，再从相册选择。");return;}read(uri);}
    private void read(Uri uri){
        if(!"content".equals(uri.getScheme())){status.setText("请从相册重新选择这张图片。");return;}
        final int token=++generation;if(task!=null)task.cancel(true);result=null;embedded=null;explicitGroups=null;results.removeAllViews();numbers.setText("");add.setEnabled(false);status.setText("正在读取图片…");select.setEnabled(true);paste.setEnabled(true);
        task=worker.submit(()->{Bitmap decoded=null;try{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("无法打开这张图片，请重新选择");byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>16*1024*1024)throw new IOException("图片较大，请选择保存后的外星消息图片");bytes.write(buffer,0,n);}}
            byte[] data=bytes.toByteArray();BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);
            if(bounds.outWidth<1||bounds.outHeight<1||(long)bounds.outWidth*bounds.outHeight>16000000)throw new IOException("请选一张完整的外星消息图片");
            decoded=BitmapFactory.decodeByteArray(data,0,data.length);if(decoded==null)throw new IOException("无法打开这张图片，请重新选择");
            PngMessage message=null;try{message=PngMessage.read(data);}catch(IOException ignored){}
            if(names==null)names=new GlyphNames(new Lexicon(new InputStreamReader(getAssets().open("alien/lexicon.tsv"),"UTF-8")));
            Bitmap image=decoded;GlyphReader reader=GlyphReader.get(this);GlyphReader.Result read=reader.read(image,(row,total)->main.post(()->{if(token==generation&&!isFinishing())status.setText("正在读第 "+row+" / "+total+" 行…");}));PngMessage metadata=message;
            main.post(()->{if(token!=generation||isFinishing()){image.recycle();return;}if(bitmap!=null)bitmap.recycle();bitmap=image;preview.setImageBitmap(bitmap);preview.setVisibility(View.VISIBLE);result=read;embedded=metadata;showResult();});decoded=null;
        }catch(Exception e){String message=e instanceof IOException?e.getMessage():"这张图片暂时没读清，换一张试试";main.post(()->{if(token==generation&&!isFinishing())status.setText(message);});}finally{if(decoded!=null)decoded.recycle();}});
    }
    private void showResult(){results.removeAllViews();explicitGroups=null;if(result==null||!result.readable){status.setText("这张图片没读清，请选择完整的外星消息图片。");add.setEnabled(false);return;}
        int rowIndex=0;for(GlyphReader.Row line:result.rows){TextView heading=text("第 "+(++rowIndex)+" 行",13);results.addView(heading);LinearLayout tiles=row();for(GlyphReader.Symbol symbol:line.symbols){LinearLayout tile=column();tile.setGravity(Gravity.CENTER);tile.setPadding(dp(3),dp(6),dp(3),dp(6));tile.setBackground(ui.shape(symbol.confirmed?ProductUi.PAPER:0xfff5dfb3,12));
                ImageView glyph=new ImageView(this);glyph.setImageBitmap(images.glyph(symbol.id));glyph.setColorFilter(0xff302a23);tile.addView(glyph,new LinearLayout.LayoutParams(dp(52),dp(52)));
                TextView label=text(names.name(symbol.id),12);label.setGravity(Gravity.CENTER);label.setMaxLines(2);tile.addView(label);if(!symbol.confirmed){TextView confirm=text("点选确认",11);confirm.setGravity(Gravity.CENTER);tile.addView(confirm);}tile.setContentDescription("读图字形 "+names.name(symbol.id)+(symbol.confirmed?" 已确认":" 待确认"));tile.setOnClickListener(v->choose(symbol));LinearLayout.LayoutParams tileParams=new LinearLayout.LayoutParams(0,-2,1);tileParams.setMargins(dp(3),dp(4),dp(3),dp(4));tiles.addView(tile,tileParams);}
            results.addView(tiles);}
        int pending=0;for(GlyphReader.Row line:result.rows)for(GlyphReader.Symbol symbol:line.symbols)if(!symbol.confirmed)pending++;
        status.setText(pending==0?"已读出 "+result.count()+" 个字形 · "+result.rows.size()+" 行，点字形可以修改。":"还有 "+pending+" 个字形需要确认，点橙色方块选择。");add.setEnabled(pending==0);
        String value=Numerals.decode(result.glyphs());if(value!=null){numbers.setText("合为数字 · "+value+"  ›");numbers.setBackground(ui.shape(ProductUi.SOFT,12));numbers.setPadding(dp(12),dp(12),dp(12),dp(12));numbers.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("合为一个数字？").setMessage("将这些字形作为 "+value+" 加入图片稿。").setPositiveButton("合为数字",(d,w)->{explicitGroups=Collections.singletonList(result.glyphs());numbers.setText("作为数字加入："+value);}).setNegativeButton("保留分行",null).show());}else{numbers.setText("");numbers.setOnClickListener(null);}
    }
    private void choose(GlyphReader.Symbol symbol){LinearLayout choices=column();AlertDialog dialog=new AlertDialog.Builder(this).setTitle("选择这个字形").setView(choices).setNegativeButton("取消",null).create();for(String id:symbol.candidates){LinearLayout choice=row();choice.setGravity(Gravity.CENTER_VERTICAL);ImageView glyph=new ImageView(this);glyph.setImageBitmap(images.glyph(id));glyph.setColorFilter(0xff302a23);choice.addView(glyph,new LinearLayout.LayoutParams(dp(56),dp(56)));TextView label=text(names.name(id),16);choice.addView(label);choice.setPadding(dp(16),dp(8),dp(16),dp(8));choice.setContentDescription("选择 "+names.name(id));choice.setOnClickListener(v->{symbol.id=id;symbol.confirmed=true;dialog.dismiss();showResult();});choices.addView(choice);}dialog.show();}
    private void addDraft(){if(result==null||!result.confirmed())return;List<List<String>> groups=explicitGroups==null?result.groups():explicitGroups;
        if(explicitGroups==null&&embedded!=null){List<String> flat=new ArrayList<>();for(List<String> group:embedded.groups)flat.addAll(group);if(flat.equals(result.glyphs()))groups=embedded.groups;}
        if(!ImageDraftStore.append(this,groups)){new AlertDialog.Builder(this).setMessage("图片稿已有内容，加入后会超过 120 个字形。请先整理图片稿。").setPositiveButton("知道了",null).show();return;}
        Toast.makeText(this,"已加入图片稿，回到键盘继续编辑",Toast.LENGTH_SHORT).show();finish();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);return v;}
    private TextView text(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(0xff302a23);v.setPadding(0,dp(6),0,dp(6));return v;}
    private Button button(String label,Runnable action){return ui.button(label,false,action);}
    @Override public void finish(){if(getIntent().getBooleanExtra("from_ime",false))finishAndRemoveTask();else super.finish();}
    @Override protected void onDestroy(){generation++;if(task!=null)task.cancel(true);worker.shutdownNow();if(bitmap!=null){preview.setImageDrawable(null);bitmap.recycle();bitmap=null;}super.onDestroy();}
}
