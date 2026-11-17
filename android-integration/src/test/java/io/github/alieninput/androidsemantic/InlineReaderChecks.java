package io.github.alieninput.androidsemantic;
import android.app.Instrumentation;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import io.github.alieninput.semantic.*;
import java.io.*;
import java.util.*;

public final class InlineReaderChecks extends Instrumentation {
 private KeyboardImageReader reader;
 public void onCreate(Bundle args){super.onCreate(args);start();}
 private View find(View root,String name,boolean prefix){String desc=root.getContentDescription()==null?"":root.getContentDescription().toString();if(prefix?desc.startsWith(name):desc.equals(name))return root;if(root instanceof TextView&&name.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=find(((ViewGroup)root).getChildAt(i),name,prefix);if(found!=null)return found;}return null;}
 private View pending(View root){if(root.getContentDescription()!=null&&root.getContentDescription().toString().endsWith("待确认"))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=pending(((ViewGroup)root).getChildAt(i));if(found!=null)return found;}return null;}
 private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 public void onStart(){Bundle out=new Bundle();try{
  android.content.Context context=new ContextThemeWrapper(getTargetContext(),android.R.style.Theme_Material_Light_NoActionBar);GlyphNames names=new GlyphNames(new Lexicon(new InputStreamReader(context.getAssets().open("alien/lexicon.tsv"),"UTF-8")));boolean[] added={false};
  ImageDraftStore.save(context,Collections.singletonList(Collections.singletonList("F0835")));
  runOnMainSync(()->reader=new KeyboardImageReader(context,names,new AlienImages(context),()->added[0]=true,()->{}));
  byte[] sample;try(InputStream in=context.getAssets().open("reader-checks/number-999999999999999999-half.jpg")){sample=ImageInput.bytes(in);}
  runOnMainSync(()->reader.start(()->sample));long deadline=System.currentTimeMillis()+15000;boolean[] busy={true};while(busy[0]&&System.currentTimeMillis()<deadline){Thread.sleep(25);runOnMainSync(()->busy[0]=reader.isReading());}check(!busy[0],"inline reader completed");
  runOnMainSync(()->{View add=find(reader.view,"加入图片稿",false);check(!add.isEnabled(),"uncertain image requires confirmation");View uncertain=pending(reader.view);check(uncertain!=null,"uncertain glyph highlighted on first page");uncertain.performClick();View first=find(reader.view,"读图字形 ",true);check(first!=null,"inline candidates visible");first.performClick();check(add.isEnabled(),"confirmed image can append");CheckBox number=(CheckBox)find(reader.view,"合为一个数字：999999999999999999",false);check(number!=null,"integer grouping offered");number.setChecked(true);add.performClick();check(added[0],"callback returns to composing panel");List<List<String>> groups=ImageDraftStore.read(context);check(groups.size()==2&&groups.get(0).equals(Collections.singletonList("F0835"))&&groups.get(1).equals(Numerals.encode("999999999999999999")),"original draft plus explicitly grouped number preserved");});
  out.putString("result","PASS inline reading: uncertain candidate confirmation, explicit numeral grouping, append preserves draft");finish(-1,out);
 }catch(Throwable e){StringWriter error=new StringWriter();e.printStackTrace(new PrintWriter(error));out.putString("failure",error.toString());finish(1,out);}finally{if(reader!=null)runOnMainSync(()->reader.cancel());ImageDraftStore.save(getTargetContext(),Collections.emptyList());}}
}
