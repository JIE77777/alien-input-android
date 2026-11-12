package io.github.alieninput.android;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.util.LruCache;
import java.io.*;
import java.util.*;

public final class Assets {
    private static Codebook book;
    private static final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(4*1024*1024) {
        @Override protected int sizeOf(String key,Bitmap value) { return value.getByteCount(); }
    };
    public static synchronized Codebook book(Context c) {
        if(book==null) try {
            book=new Codebook(new InputStreamReader(c.getAssets().open("glyphs.tsv"),"UTF-8"),
                new InputStreamReader(c.getAssets().open("phrases.tsv"),"UTF-8"),
                new InputStreamReader(c.getAssets().open("keys.tsv"),"UTF-8"));
        } catch(IOException e) { throw new IllegalStateException("Cannot load codebook",e); }
        return book;
    }
    public static Bitmap bitmap(Context c,String id) {
        Bitmap b=cache.get(id); if(b!=null) return b;
        if(!id.matches("F\\d{4}|S-(H|H3|SLASH|BACKSLASH)")) return null;
        try(InputStream in=c.getAssets().open("glyphs/"+id+".png")) {
            b=BitmapFactory.decodeStream(in);
        } catch(IOException ex) {
            if(id.equals("S-H")||id.equals("S-H3")) {
                b=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);
                Canvas canvas=new Canvas(b); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(Color.BLACK);p.setStrokeWidth(4);
                if(id.equals("S-H")) canvas.drawLine(8,50,92,50,p);
                else for(int y:new int[]{24,50,76}) canvas.drawLine(8,y,92,y,p);
            }
        }
        if(b!=null) cache.put(id,b);
        return b;
    }
    public static Uri render(Context c,List<String> ids) throws IOException {
        if(ids.isEmpty()||ids.size()>120) throw new IOException("稿件长度应为 1–120 个字形");
        final int cell=112, cols=Math.min(8,ids.size()), rows=(ids.size()+cols-1)/cols;
        Bitmap out=Bitmap.createBitmap(cols*cell+32,rows*cell+32,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(out); canvas.drawColor(Color.WHITE);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        for(int i=0;i<ids.size();i++) {
            Bitmap glyph=bitmap(c,ids.get(i));
            if(glyph==null) {out.recycle();throw new IOException("缺少字形 "+ids.get(i));}
            int x=16+i%cols*cell,y=16+i/cols*cell;
            canvas.drawBitmap(glyph,null,new Rect(x,y,x+100,y+100),paint);
        }
        File dir=new File(c.getCacheDir(),"shared");
        if(!dir.isDirectory()&&!dir.mkdirs()) throw new IOException("无法创建图片目录");
        File[] files=dir.listFiles();
        if(files!=null) for(File old:files) if(System.currentTimeMillis()-old.lastModified()>7L*86400000) old.delete();
        File file=new File(dir,UUID.randomUUID().toString()+".png");
        try(FileOutputStream stream=new FileOutputStream(file)) {
            if(!out.compress(Bitmap.CompressFormat.PNG,100,stream)) throw new IOException("无法生成图片");
        } finally {out.recycle();}
        return new Uri.Builder().scheme("content").authority(ImageProvider.AUTHORITY).appendPath(file.getName()).build();
    }
}
