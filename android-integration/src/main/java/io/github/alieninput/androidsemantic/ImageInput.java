package io.github.alieninput.androidsemantic;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.util.AtomicFile;
import io.github.alieninput.semantic.PngMessage;
import java.io.*;
import java.util.concurrent.*;

/** Image transport shared by the keyboard reader and the system photo-picker bridge. */
final class ImageInput {
    static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    static final class Decoded {
        final GlyphReader.Result result;final PngMessage metadata;
        Decoded(GlyphReader.Result result,PngMessage metadata){this.result=result;this.metadata=metadata;}
    }
    static byte[] bytes(InputStream in)throws IOException{if(in==null)throw new IOException("无法打开图片，请重新选择");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(out.size()+n>16*1024*1024)throw new IOException("图片较大，请选择聊天中保存的原图");out.write(buffer,0,n);}return out.toByteArray();}
    static byte[] read(Context c,Uri uri)throws IOException{if(!"content".equals(uri.getScheme()))throw new IOException("请从相册重新选择图片");try(InputStream in=c.getContentResolver().openInputStream(uri)){return bytes(in);}}
    static Uri clipboard(Context c){ClipData clip=((ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE)).getPrimaryClip();if(clip!=null)for(int i=0;i<clip.getItemCount();i++){Uri uri=clip.getItemAt(i).getUri();if(uri!=null&&"content".equals(uri.getScheme()))return uri;}return null;}
    private static AtomicFile pending(Context c){return new AtomicFile(new File(c.getCacheDir(),"alien-read-pending.bin"));}
    static synchronized void store(Context c,byte[] bytes)throws IOException{AtomicFile f=pending(c);FileOutputStream out=f.startWrite();try{out.write(bytes);f.finishWrite(out);}catch(IOException e){f.failWrite(out);throw e;}}
    static synchronized boolean hasPending(Context c){return pending(c).getBaseFile().exists();}
    static synchronized byte[] consume(Context c)throws IOException{AtomicFile f=pending(c);try(InputStream in=f.openRead()){return bytes(in);}finally{f.delete();}}
    static Decoded decode(Context context,byte[] data,GlyphReader.Progress progress)throws IOException{
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);
        if(bounds.outWidth<1||bounds.outHeight<1||(long)bounds.outWidth*bounds.outHeight>16000000)throw new IOException("请选完整的外星消息图片");
        Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length);if(bitmap==null)throw new IOException("无法打开这张图片");
        try{PngMessage metadata=null;try{metadata=PngMessage.read(data);}catch(IOException ignored){}return new Decoded(GlyphReader.get(context).read(bitmap,progress),metadata);}finally{bitmap.recycle();}
    }
}
