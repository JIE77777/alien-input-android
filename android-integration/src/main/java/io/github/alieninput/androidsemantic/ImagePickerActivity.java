package io.github.alieninput.androidsemantic;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.Toast;

/** Opens the system picker only; recognition and confirmation stay in the IME. */
public final class ImagePickerActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);if(state!=null)return;
        if(Build.VERSION.SDK_INT>=33)try{startActivityForResult(new Intent("android.provider.action.PICK_IMAGES").setType("image/*"),42);return;}catch(ActivityNotFoundException ignored){}
        try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),42);}catch(ActivityNotFoundException e){Toast.makeText(this,"没有找到相册，请复制图片后在外星栏读取",Toast.LENGTH_SHORT).show();finish();}
    }
    @Override protected void onActivityResult(int request,int code,Intent data){super.onActivityResult(request,code,data);if(request!=42)return;if(code!=RESULT_OK||data==null||data.getData()==null){finish();return;}Uri uri=data.getData();
        ImageInput.WORKER.submit(()->{String error=null;try{ImageInput.store(this,ImageInput.read(this,uri));}catch(Exception e){error="图片未能打开，请重新选择";}String message=error;runOnUiThread(()->{if(message!=null)Toast.makeText(this,message,Toast.LENGTH_SHORT).show();finish();});});
    }
    @Override public void finish(){finishAndRemoveTask();AlienPanel.pickerFinished();}
}
