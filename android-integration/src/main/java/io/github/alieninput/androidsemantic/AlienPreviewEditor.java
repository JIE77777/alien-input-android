package io.github.alieninput.androidsemantic;

import android.content.Context;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.view.inputmethod.*;
import android.widget.EditText;
import io.github.alieninput.semantic.PngMessage;
import java.io.InputStream;

/** Trime's built-in test field, with preview for this app's own generated PNGs. */
public final class AlienPreviewEditor extends EditText {
    public AlienPreviewEditor(Context context){super(context);setContentDescription("外星试写输入框");}
    @Override public boolean onTextContextMenuItem(int id){
        if(id==android.R.id.paste){
            ClipboardManager clipboard=(ClipboardManager)getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData data=clipboard.getPrimaryClip();
            if(data!=null&&data.getItemCount()>0&&data.getDescription().hasMimeType("image/png")&&data.getItemAt(0).getUri()!=null){
                InputConnection connection=onCreateInputConnection(new EditorInfo());
                if(connection!=null&&connection.commitContent(new InputContentInfo(data.getItemAt(0).getUri(),new ClipDescription("外星组合图",new String[]{"image/png"}),null),0,null))return true;
            }
        }
        return super.onTextContextMenuItem(id);
    }
    @Override public InputConnection onCreateInputConnection(EditorInfo info){
        InputConnection target=super.onCreateInputConnection(info);if(target==null)return null;
        info.contentMimeTypes=new String[]{"image/png"};
        return new InputConnectionWrapper(target,false){
            @Override public boolean commitContent(InputContentInfo content,int flags,Bundle options){
                if(content==null||!content.getDescription().hasMimeType("image/png")||
                   !(getContext().getPackageName()+".alien-images").equals(content.getContentUri().getAuthority()))return false;
                boolean permission=(flags&InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION)!=0;
                try{
                    if(permission)content.requestPermission();
                    byte[] bytes;try(InputStream in=getContext().getContentResolver().openInputStream(content.getContentUri())){bytes=PngMessage.readBounded(in);}
                    Bitmap image=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(image==null)return false;
                    float density=getResources().getDisplayMetrics().density;
                    float scale=Math.min(240*density/image.getWidth(),100*density/image.getHeight());
                    BitmapDrawable drawable=new BitmapDrawable(getResources(),image);
                    drawable.setBounds(0,0,Math.max(1,Math.round(image.getWidth()*scale)),Math.max(1,Math.round(image.getHeight()*scale)));
                    Editable text=getText();int start=Math.max(0,Math.min(getSelectionStart(),getSelectionEnd())),end=Math.max(start,Math.max(getSelectionStart(),getSelectionEnd()));
                    text.replace(start,end,"\ufffc");text.setSpan(new ImageSpan(drawable,ImageSpan.ALIGN_BASELINE),start,start+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);AlienPreviewEditor.this.setSelection(start+1);
                    setContentDescription("外星试写输入框，已接收图片");return true;
                }catch(Exception e){return false;}
                finally{if(permission)content.releasePermission();}
            }
        };
    }
}
