package io.github.alieninput.androidsemantic;

import android.app.Instrumentation;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.graphics.Bitmap;
import android.content.Context;
import android.net.Uri;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.TextView;
import android.view.inputmethod.*;
import io.github.alieninput.semantic.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import com.osfans.trime.core.Rime;
import com.osfans.trime.core.CandidateProto;

/** Standalone test APK; never changes the user's selected keyboard. */
public final class DeviceChecks extends Instrumentation {
    private int checks;
    private void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();final Throwable[] error={null};
        try{
            runOnMainSync(()->{try{checkQueryConnection();checkPanel();}catch(Throwable e){error[0]=e;}});
            if(error[0]!=null)throw new AssertionError("Query editor",error[0]);
            Lexicon lexicon=new Lexicon(new InputStreamReader(getTargetContext().getAssets().open("alien/lexicon.tsv"),"UTF-8"));
            check(lexicon.search("你",Collections.emptyMap()).get(0).entry.id.equals("F0835"),"you lookup");
            check(lexicon.search("食物",Collections.emptyMap()).get(0).entry.id.equals("F1047"),"food lookup");
            AlienImages images=new AlienImages(getTargetContext());
            for(Lexicon.Entry e:lexicon.entries())if(e.glyphs.size()==1)check(images.glyph(e.glyphs.get(0))!=null,"glyph "+e.id);
            Bitmap preview=images.preview(Arrays.asList("F0835","F1050","F1047"));
            check(preview.getWidth()==480&&preview.getHeight()==208,"phrase preview geometry");
            check(preview.getPixel(0,0)==AlienPalette.load(getTargetContext()).background,"configured image background");preview.recycle();
            for(int count:new int[]{1,4,5,6,8,9,120}){
                Bitmap b=images.preview(Collections.nCopies(count,"F0835"));
                ImageLayout layout=new ImageLayout(count);
                check(b.getWidth()==480&&b.getHeight()==layout.height,"fixed four-slot canvas "+count);
                b.recycle();
            }
            File png=images.render(Arrays.asList("F0835","F1047"));check(png.length()>0,"PNG output");
            try(InputStream in=new FileInputStream(png)){
                PngMessage message=PngMessage.read(PngMessage.readBounded(in));
                check(message!=null&&message.groups.equals(Arrays.asList(Arrays.asList("F0835"),Arrays.asList("F1047"))),"PNG embedded code round trip");
            }
            Bitmap decoded=android.graphics.BitmapFactory.decodeFile(png.getAbsolutePath());check(decoded!=null,"metadata PNG remains displayable");decoded.recycle();
            checkGroupedImages(images,lexicon);
            String[] queries={"你","食物","你好","吃饭","food","foood","不存在的词"};
            long[] elapsed=new long[500];
            for(int i=0;i<elapsed.length;i++){long start=System.nanoTime();lexicon.search(queries[i%queries.length],Collections.emptyMap());elapsed[i]=System.nanoTime()-start;}
            Arrays.sort(elapsed);
            checkRime();
            result.putString("result",String.format(Locale.ROOT,"PASS %d device checks; query p50 %.2f ms, p95 %.2f ms",checks,elapsed[250]/1e6,elapsed[475]/1e6));
            finish(-1,result);
        }catch(Throwable e){StringWriter trace=new StringWriter();e.printStackTrace(new PrintWriter(trace));result.putString("failure",trace.toString());finish(1,result);}
    }
    private void checkGroupedImages(AlienImages images,Lexicon lexicon)throws Exception{
        Lexicon.Entry pair=lexicon.search("吃饭",Collections.emptyMap()).get(0).entry;
        Lexicon.Entry triple=null;
        for(Lexicon.Entry entry:lexicon.entries())if(entry.glyphs.size()==3){triple=entry;break;}
        check(pair.glyphs.size()==2&&triple!=null,"real two/three glyph words");
        Lexicon.Entry you=lexicon.search("你",Collections.emptyMap()).get(0).entry;
        Draft draft=new Draft();draft.add(pair);draft.add(triple);draft.add(you);
        ImageLayout layout=ImageLayout.forGroups(draft.groups());
        check(layout.y(0)==layout.y(1)&&layout.y(2)>layout.y(1)&&layout.y(2)==layout.y(5),"2+3+1 wraps as 2+4");
        Bitmap preview=images.previewDraft(draft);
        File file=images.renderDraft(draft);Bitmap decoded=android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath());
        check(preview.sameAs(decoded),"preview and PNG pixels identical with phrase wrapping");
        Bitmap flat=images.preview(draft.glyphs());check(!preview.sameAs(flat),"grouped renderer does not flatten phrase boundaries");flat.recycle();
        try(InputStream stream=new FileInputStream(file)){
            PngMessage message=PngMessage.read(PngMessage.readBounded(stream));
            check(message.groups.equals(draft.groups()),"PNG preserves groups and order after wrapping");
        }
        for(String number:new String[]{"0","1","2","3","4","8","9","1234","-1234","2401","123456","999999999999999999"}){
            Lexicon.Entry entry=lexicon.search(number,Collections.emptyMap()).get(0).entry;
            Draft numeric=new Draft();numeric.add(entry);
            for(String id:entry.glyphs)check(images.glyph(id)!=null,"render numeric glyph "+id);
            Bitmap b=images.previewDraft(numeric);File numericFile=images.renderDraft(numeric);
            Bitmap png=android.graphics.BitmapFactory.decodeFile(numericFile.getAbsolutePath());
            check(b.sameAs(png),"numeric preview/output "+number);png.recycle();
            try(InputStream in=new FileInputStream(numericFile)){
                check(PngMessage.read(PngMessage.readBounded(in)).groups.equals(numeric.groups()),"numeric PNG metadata "+number);
            }
            if(number.equals("1234")||number.equals("123456")){
                try(OutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"number-"+number+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}
            }
            b.recycle();
        }
        // Export a production-renderer sample for visual review, without touching any user's clipboard.
        File review=new File(getTargetContext().getFilesDir(),"grouped-layout.png");
        try(OutputStream out=new FileOutputStream(review)){preview.compress(Bitmap.CompressFormat.PNG,100,out);}
        preview.recycle();decoded.recycle();
        List<List<String>> snapshot=draft.groups();draft.remove(0);
        check(snapshot.size()==3&&draft.groups().size()==2,"draft snapshot stable during editing");
        draft.clear();for(int i=0;i<40;i++)check(draft.add(triple),"long draft word "+i);
        check(!draft.add(you),"long draft limit preserves existing content");
        Bitmap longImage=images.previewDraft(draft);
        check(longImage.getWidth()==480&&longImage.getHeight()==4512,"120 glyphs in intact three-glyph words");longImage.recycle();
        for(String palette:AlienPalette.IDS){
            getTargetContext().getSharedPreferences(AlienPalette.PREFERENCES,0).edit().putString(AlienPalette.KEY,palette).commit();
            Bitmap b=images.preview(Arrays.asList("F0908","F0835","F1050","F1047"));
            check(b.getPixel(0,0)==AlienPalette.forId(palette).background,"palette background "+palette);
            boolean ink=false;int bg=b.getPixel(0,0);
            for(int y=54;y<154&&!ink;y++)for(int x=22;x<122;x++)if(b.getPixel(x,y)!=bg){ink=true;break;}
            check(ink,"uniform glyph visible in "+palette);
            if(palette.equals("white")){
                try(OutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"uniform-layout.png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}
            }
            b.recycle();
        }
        getTargetContext().getSharedPreferences(AlienPalette.PREFERENCES,0).edit().remove(AlienPalette.KEY).commit();
    }
    private void checkQueryConnection(){
        final String[] confirmed={""};final int[] deletes={0},submits={0};
        QueryConnection q=new QueryConnection(new View(getTargetContext()),new QueryConnection.Listener(){
            public void changed(String text){confirmed[0]=text;}
            public void emptyBackspace(){deletes[0]++;}
            public void submit(){submits[0]++;}
        });
        q.setComposingText("ni",1);check(confirmed[0].isEmpty(),"unconfirmed pinyin excluded");
        q.commitText("你",1);check(confirmed[0].equals("你"),"Chinese confirmed");
        q.clear();check(confirmed[0].isEmpty(),"selection clears query");
        q.setComposingText("shiwu",1);q.commitText("食物",1);check(confirmed[0].equals("食物"),"next query independent");
        q.deleteSurroundingTextInCodePoints(1,0);check(confirmed[0].equals("食"),"query deletion");
        q.clear();q.deleteSurroundingTextInCodePoints(1,0);check(deletes[0]==1,"empty backspace targets draft");
        q.commitText("food",1);check(q.getTextBeforeCursor(10,0).toString().equals("food"),"surrounding text stays local");
        q.clear();q.commitText("😀",1);q.deleteSurroundingTextInCodePoints(1,0);check(confirmed[0].isEmpty(),"surrogate pair deleted together");
        q.setComposingText("food",1);q.finishComposingText();check(confirmed[0].equals("food"),"finish composition confirms text");
        q.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));check(submits[0]==1,"enter performs local query");
        check(!q.performContextMenuAction(android.R.id.paste),"no implicit clipboard bridge");
        q.clear();check(!q.commitText(String.join("",Collections.nCopies(129,"a")),1),"query length bounded");
        check(confirmed[0].isEmpty(),"rejected query leaves buffer unchanged");
        q.closeConnection();
    }
    private View find(View view,String text,boolean description){
        if(description&&text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(!description&&view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),text,description);if(found!=null)return found;}}
        return null;
    }
    private void click(View root,String value,boolean description){View v=find(root,value,description);check(v!=null,"visible action: "+value);v.performClick();}
    private void checkPanel(){
        final android.content.Intent[] launched={null};
        Context context=new android.content.ContextWrapper(getTargetContext()){
            @Override public void startActivity(android.content.Intent intent){launched[0]=intent;}
        };
        EditorInfo info=new EditorInfo();info.inputType=InputType.TYPE_CLASS_TEXT;info.contentMimeTypes=new String[]{"image/png"};
        final int[] sent={0};final boolean[] accept={false};final Uri[] last={null};
        BaseInputConnection real=new BaseInputConnection(new View(context),true){
            @Override public boolean commitContent(InputContentInfo content,int flags,Bundle options){sent[0]++;last[0]=content.getContentUri();return accept[0];}
        };
        real.commitText("existing",1);
        AlienPanel panel=new AlienPanel(new AlienPanel.Host(){
            public Context context(){return context;}
            public InputConnection editor(){return real;}
            public EditorInfo editorInfo(){return info;}
            public void resetComposition(Runnable done){done.run();}
            public boolean isComposing(){return false;}
            public Uri imageUri(File file){return new Uri.Builder().scheme("content").authority(context.getPackageName()+".alien-images").appendPath(file.getName()).build();}
        });
        panel.start(info,false);View root=panel.createView();check(root.getVisibility()==View.GONE,"alien panel initially collapsed");panel.toggleVisibility();check(panel.capturesInput(),"alien capture enabled");
        panel.connection.commitText("你",1);click(root,"外星候选 你 释义",true);
        panel.connection.commitText("food",1);click(root,"外星候选 食物 别名",true);
        panel.toggleVisibility();check(root.getVisibility()==View.GONE&&!panel.capturesInput(),"collapse restores ordinary input");
        panel.toggleVisibility();check(root.getVisibility()==View.VISIBLE&&find(root,"图片稿第 2 项 食物",true)!=null,"reopening preserves ordered draft");
        check(real.getEditable().toString().equals("existing"),"query text never reaches host editor");
        click(root,"插入图",false);check(sent[0]==1,"explicit PNG delivery attempted");
        check(find(root,"图片稿第 2 项 食物",true)!=null,"rejected image preserves draft");
        click(root,"图片稿第 2 项 食物",true);panel.connection.commitText("吃饭",1);
        click(root,"外星候选 吃喝 · 食物 别名",true);
        check(find(root,"图片稿第 2 项 吃喝 · 食物",true)!=null,"phrase replacement stays one item");
        panel.connection.deleteSurroundingTextInCodePoints(1,0);
        check(find(root,"图片稿第 2 项 吃喝 · 食物",true)==null,"backspace removes entire phrase");
        accept[0]=true;click(root,"插入图",false);check(sent[0]==2,"accepted PNG delivery");
        check(find(root,"图片稿为空",false)!=null,"accepted image clears draft");
        try(InputStream stream=context.getContentResolver().openInputStream(last[0])){check(stream!=null&&stream.read()==137,"PNG URI readable");}
        catch(IOException e){throw new AssertionError(e);}
        info.packageName="com.tencent.mm";panel.start(info,false);click(root,"外星",false);
        panel.connection.commitText("你",1);click(root,"外星候选 你 释义",true);
        panel.connection.commitText("食物",1);click(root,"外星候选 食物 释义",true);
        int before=sent[0];check(find(root,"插入图",false).getVisibility()==View.GONE,"WeChat hides incompatible direct insertion");click(root,"插入图",false);
        check(sent[0]==before,"WeChat bypasses editor content insertion");
        check(launched[0]==null,"WeChat never opens image share automatically");
        click(root,"分享",false);check(launched[0]!=null,"only explicit share opens chooser");
        android.content.Intent shared=launched[0].getParcelableExtra(android.content.Intent.EXTRA_INTENT);
        check("image/png".equals(shared.getType())&&shared.getPackage()==null,"explicit share uses generic PNG intent");
        check(!shared.hasExtra(android.content.Intent.EXTRA_TEXT),"image share never includes URI as text");
        Uri sharedUri=shared.getParcelableExtra(android.content.Intent.EXTRA_STREAM);
        try(InputStream stream=context.getContentResolver().openInputStream(sharedUri)){
            PngMessage message=PngMessage.read(PngMessage.readBounded(stream));
            check(message.groups.equals(Arrays.asList(Arrays.asList("F0835"),Arrays.asList("F1047"))),"share contains entire ordered draft");
        }catch(IOException e){throw new AssertionError(e);}
        panel.finish();panel.start(info,false);
        check(panel.enabled()&&find(root,"图片稿第 2 项 食物",true)!=null,"return from share preserves combined draft");
        panel.connection.commitText("食物",1);panel.start(info,false);check(!panel.enabled()&&panel.connection.confirmed().isEmpty(),"new editor resets mode and query");
        panel.start(info,false);panel.toggleVisibility();
        panel.connection.commitText("1234",1);click(root,"外星候选 1234 数值",true);
        check(find(root,"图片稿第 1 项 1234",true)!=null,"decimal digits produce selectable native number draft");
        check(real.getEditable().toString().equals("existing"),"numeric query stays out of host editor");
        info.inputType=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD;panel.start(info,false);
        check(!find(root,"外星",false).isEnabled(),"password editor disables alien mode");
        panel.finish();
    }
    private void copyAssets(String asset,File destination)throws IOException{
        String[] children=getTargetContext().getAssets().list(asset);
        if(children!=null&&children.length>0){destination.mkdirs();for(String name:children)copyAssets(asset+"/"+name,new File(destination,name));}
        else{destination.getParentFile().mkdirs();try(InputStream in=getTargetContext().getAssets().open(asset);OutputStream out=new FileOutputStream(destination)){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}}
    }
    private void checkRime()throws Exception{
        File shared=new File(getTargetContext().getFilesDir(),"rime-shared");File user=new File(getTargetContext().getFilesDir(),"rime-test-user");user.mkdirs();copyAssets("shared",shared);
        Bundle progress=new Bundle();progress.putString("stage","Deploying pinned Rime dictionaries and testing T9/full/double pinyin");sendStatus(0,progress);
        Rime.startupRime(shared.getAbsolutePath(),user.getAbsolutePath(),"alien-integration-test",true);
        check(Rime.deployed.await(120,TimeUnit.SECONDS)&&Rime.successful,"Rime deployment");
        try{
            String[][] cases={{"clover_jiugong","64","你"},{"clover_jiugong","74498","食物"},{"alien_t9","64","你"},{"luna_pinyin_simp","ni","你"},{"luna_pinyin_simp","shiwu","食物"},{"double_pinyin_flypy","ni","你"},{"double_pinyin_flypy","uiwu","食物"}};
            for(String[] example:cases){
                check(Rime.selectRimeSchema(example[0]),"select "+example[0]);
                check(example[0].equals(Rime.getCurrentRimeSchema()),"active schema "+example[0]);
                Rime.clearRimeComposition();
                for(char c:example[1].toCharArray())Rime.processRimeKey(c,0);
                CandidateProto[] candidates=Rime.getRimeCandidates(0,100);int at=-1;
                for(int i=0;i<candidates.length;i++)if(example[2].equals(candidates[i].text)){at=i;break;}
                check(at>=0,"candidate "+example[0]+" "+example[1]+" -> "+example[2]);
                check(Rime.selectRimeCandidate(at,true),"select Chinese candidate");
                check(example[2].equals(Rime.getRimeCommit().text),"Rime confirms expected word");
            }
        }finally{Rime.exitRime();}
    }
}
