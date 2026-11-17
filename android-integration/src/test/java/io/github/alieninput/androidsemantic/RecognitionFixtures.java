package io.github.alieninput.androidsemantic;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import io.github.alieninput.semantic.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Exports deterministic production-renderer images; no UI, clipboard, IME or network access. */
public final class RecognitionFixtures extends Instrumentation {
    private File directory;
    private JSONArray samples=new JSONArray();
    public void onCreate(Bundle args){super.onCreate(args);start();}
    private void save(Bitmap bitmap,File file)throws IOException{
        file.getParentFile().mkdirs();
        try(OutputStream out=new FileOutputStream(file)){
            if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("PNG failed");
        }
    }
    private void sample(String name,AlienImages images,Draft draft,String number)throws Exception{
        Bitmap b=images.previewDraft(draft);
        save(b,new File(directory,name+".png"));
        JSONObject sample=new JSONObject();sample.put("file",name+".png");sample.put("groups",new JSONArray(draft.groups()));
        sample.put("glyphs",new JSONArray(draft.glyphs()));sample.put("width",b.getWidth());sample.put("height",b.getHeight());
        ImageLayout layout=ImageLayout.forGroups(draft.groups());JSONArray counts=new JSONArray();
        int last=-1,count=0;for(int i=0;i<layout.count;i++){int y=layout.y(i);if(y!=last&&count>0){counts.put(count);count=0;}last=y;count++;}counts.put(count);
        sample.put("row_counts",counts);if(number!=null)sample.put("number",number);samples.put(sample);b.recycle();
    }
    public void onStart(){Bundle result=new Bundle();try{
        Context context=getTargetContext();directory=new File(context.getFilesDir(),"recognition-fixtures");directory.mkdirs();
        AlienImages images=new AlienImages(context);
        for(int c=1;c<=6;c++)for(int p=4;p<=21;p++){
            String id="N"+c+"P"+p;Bitmap mask=images.glyph(id);if(mask==null)throw new IOException(id);
            save(mask,new File(directory,"glyphs/"+id+".png"));
        }
        // Synthetic entries supply exact sequences; layout and all drawing still use production classes.
        StringBuilder text=new StringBuilder("# alien-lexicon-v1\n");
        String[] sequences={"F0835","F1050,F1047","F0001,F0834,F1047","F0902,F0834,F1305,F1302"};
        for(int i=0;i<sequences.length;i++)text.append("E\tfixture").append(i).append("\tfixture").append(i).append("\t").append(sequences[i]).append("\t\tfixture\n");
        Lexicon lexicon=new Lexicon(new StringReader(text.toString()));
        int[][] patterns={{2,3,1},{3,3},{4,1,4},{1,4,1,4,1},{2,3,2,3,2},{3,3,3,3,3},{4,4,4,4,4,4},{1,1,1,1,1},{2,2,2,2,2}};
        for(int i=0;i<patterns.length;i++){
            Draft draft=new Draft();for(int size:patterns[i])draft.add(lexicon.entry("fixture"+(size-1)));
            sample("layout-"+i,images,draft,null);
        }
        Draft longest=new Draft();for(int i=0;i<40;i++)longest.add(lexicon.entry("fixture2"));sample("layout-120",images,longest,null);
        for(String number:new String[]{"0","1","2","3","4","5","6","7","8","49","50","343","1234","-1234","2401","123456","-123456","999999999999999999","100000000000000000","558545864083284007"}){
            Draft draft=new Draft();draft.add(lexicon.search(number,Collections.emptyMap()).get(0).entry);sample("number-"+number,images,draft,number);
        }
        Draft mixed=new Draft();mixed.add(lexicon.entry("fixture0"));mixed.add(lexicon.search("123456",Collections.emptyMap()).get(0).entry);mixed.add(lexicon.entry("fixture1"));
        sample("mixed-number-words",images,mixed,null);
        JSONObject manifest=new JSONObject();manifest.put("producer","Android AlienImages.previewDraft / glyph; production ImageLayout and Numerals");manifest.put("samples",samples);manifest.put("dynamic_glyphs",108);
        try(Writer out=new OutputStreamWriter(new FileOutputStream(new File(directory,"manifest.json")),"UTF-8")){out.write(manifest.toString(2));}
        result.putString("result","PASS exported 108 dynamic glyphs and "+samples.length()+" production message images");finish(-1,result);
    }catch(Throwable error){StringWriter out=new StringWriter();error.printStackTrace(new PrintWriter(out));result.putString("failure",out.toString());finish(1,result);}}
}
