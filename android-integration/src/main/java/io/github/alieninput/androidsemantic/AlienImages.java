package io.github.alieninput.androidsemantic;

import android.content.Context;
import android.graphics.*;
import android.util.LruCache;
import java.io.*;
import java.util.*;
import io.github.alieninput.semantic.Draft;
import io.github.alieninput.semantic.ImageLayout;
import io.github.alieninput.semantic.PngMessage;
import io.github.alieninput.semantic.Numerals;

public final class AlienImages {
    private final Context context;
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(4*1024*1024){
        @Override protected int sizeOf(String id,Bitmap value){return value.getByteCount();}
    };
    public AlienImages(Context context){this.context=context;}
    public Bitmap glyph(String id){
        Bitmap value=cache.get(id);if(value!=null)return value;
        if(Numerals.isGenerated(id)){
            value=numeral(id);if(value!=null)cache.put(id,value);return value;
        }
        if(!Numerals.isGlyphId(id))return null;
        try(InputStream stream=context.getAssets().open("alien/glyphs/"+id+".png")){
            value=BitmapFactory.decodeStream(stream);
            if(value!=null)cache.put(id,value);
        }catch(IOException ignored){}
        return value;
    }
    /** The upstream productive numeral: coefficient glyph plus one upper bar per power of seven. */
    private Bitmap numeral(String id){
        Bitmap base=glyph(Numerals.baseGlyph(id));if(base==null)return null;
        int bars=Numerals.power(id),extra=(bars-1)*10,height=100+extra;
        // Supersample the vector bars, then downsample like the uniform raster glyphs.
        Bitmap large=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(large);
        float scale=400f/height;canvas.translate((400-100*scale)/2,0);canvas.scale(scale,scale);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        paint.setColor(Color.WHITE);paint.setStrokeWidth(3);paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawBitmap(base,null,new Rect(0,extra,100,extra+100),paint);
        for(int i=0;i<bars;i++)canvas.drawLine(6,6+i*10,94,6+i*10,paint);
        Bitmap result=Bitmap.createScaledBitmap(large,100,100,true);large.recycle();return result;
    }
    public Bitmap preview(List<String> ids)throws IOException{
        return previewGroups(singletons(ids));
    }
    public Bitmap previewDraft(Draft draft)throws IOException{return previewGroups(draft.groups());}
    private static List<List<String>> singletons(List<String> ids){
        List<List<String>> groups=new ArrayList<>();for(String id:ids)groups.add(Collections.singletonList(id));
        return groups;
    }
    private Bitmap previewGroups(List<List<String>> groups)throws IOException{
        ImageLayout layout=ImageLayout.forGroups(groups);
        List<String> ids=new ArrayList<>();for(List<String> group:groups)ids.addAll(group);
        AlienPalette palette=AlienPalette.load(context);
        Bitmap output=Bitmap.createBitmap(layout.width,layout.height,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(output);canvas.drawColor(palette.background);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        paint.setColorFilter(new PorterDuffColorFilter(palette.foreground,PorterDuff.Mode.SRC_IN));
        for(int i=0;i<ids.size();i++){
            Bitmap glyph=glyph(ids.get(i));if(glyph==null){output.recycle();throw new IOException("字形资源缺失");}
            int x=layout.x(i),y=layout.y(i);
            canvas.drawBitmap(glyph,null,new Rect(x,y,x+ImageLayout.GLYPH,y+ImageLayout.GLYPH),paint);
        }
        return output;
    }
    public File render(List<String> ids)throws IOException{
        return renderGroups(singletons(ids));
    }
    public File renderDraft(Draft draft)throws IOException{
        return renderGroups(draft.groups());
    }
    private File renderGroups(List<List<String>> groups)throws IOException{
        Bitmap bitmap=previewGroups(groups);
        File dir=new File(context.getCacheDir(),"alien-images");
        try{
            if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建图片目录");
            File[] old=dir.listFiles();
            if(old!=null)for(File file:old)if(System.currentTimeMillis()-file.lastModified()>7L*86400000)file.delete();
            File target=new File(dir,UUID.randomUUID()+".png");
            ByteArrayOutputStream encoded=new ByteArrayOutputStream();
            if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,encoded))throw new IOException("图片生成失败");
            try(FileOutputStream out=new FileOutputStream(target)){
                out.write(PngMessage.embed(encoded.toByteArray(),groups));
            }
            return target;
        }finally{bitmap.recycle();}
    }
}
