package io.github.alieninput.androidsemantic;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import io.github.alieninput.semantic.*;
import java.util.*;

/** Interaction checks against the actual product activity in an isolated application. */
public final class ProductChecks extends Instrumentation {
    private Activity activity;
    public void onCreate(Bundle args){super.onCreate(args);start();}
    private View find(View root,String name){if(name.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;if(root instanceof TextView&&name.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View v=find(((ViewGroup)root).getChildAt(i),name);if(v!=null)return v;}return null;}
    private EditText editor(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){EditText found=editor(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}return null;}
    private void require(boolean condition,String label){if(!condition)throw new AssertionError(label);}
    private void open(){activity=startActivitySync(new Intent(getTargetContext(),WriteMessageActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();}
    public void onStart(){Bundle out=new Bundle();android.os.Handler timeout=new android.os.Handler(android.os.Looper.getMainLooper());Runnable fail=()->{Bundle error=new Bundle();error.putString("failure","Product activity did not complete within 30 seconds");finish(1,error);};timeout.postDelayed(fail,30000);try{
        ImageDraftStore.save(getTargetContext(),Collections.singletonList(Collections.singletonList("F0835")));open();
        runOnMainSync(()->{View root=activity.getWindow().getDecorView();EditText input=editor(root);require(input!=null,"query field");require("alien-compose".equals(input.getPrivateImeOptions()),"ordinary keyboard input in standalone composer");input.setText("123456");View candidate=find(root,"选择 123456");require(candidate!=null,"numeric candidate available");require(ImageDraftStore.read(getTargetContext()).size()==1,"search must not modify draft");candidate.performClick();require(ImageDraftStore.read(getTargetContext()).size()==2,"chosen word appends once");require(ImageDraftStore.read(getTargetContext()).get(1).equals(Numerals.encode("123456")),"number glyphs preserved");find(root,"复制图片").performClick();ClipData clip=((android.content.ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE)).getPrimaryClip();require(clip!=null&&clip.getItemAt(0).getUri()!=null&&clip.getDescription().hasMimeType("image/png"),"copies an image URI");activity.finish();});waitForIdleSync();open();
        runOnMainSync(()->{View root=activity.getWindow().getDecorView();require(ImageDraftStore.read(getTargetContext()).size()==2,"draft persists across activity recreation");require(find(root,"复制图片").isEnabled(),"saved message remains copyable");activity.finish();});
        out.putString("result","PASS product writing: search, numeric selection, shared draft, image copy, recreation");finish(-1,out);
    }catch(Throwable e){out.putString("failure",e.toString());finish(1,out);}finally{timeout.removeCallbacks(fail);if(activity!=null)runOnMainSync(()->activity.finish());ImageDraftStore.save(getTargetContext(),Collections.emptyList());}}
}
