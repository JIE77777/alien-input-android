package io.github.alieninput.androidsemantic;
import android.app.Instrumentation;
import android.os.Bundle;
import android.graphics.*;
import org.json.*;
import io.github.alieninput.semantic.*;
import java.io.*;
import java.util.*;

public final class RecognitionChecks extends Instrumentation {
 private Bundle args;
 public void onCreate(Bundle value){super.onCreate(value);args=value;start();}
 public void onStart(){Bundle out=new Bundle();try{
  long start=System.nanoTime();GlyphReader reader=GlyphReader.get(getTargetContext());long loaded=System.nanoTime();
  StringWriter text=new StringWriter();try(Reader input=new InputStreamReader(getTargetContext().getAssets().open("reader-checks/manifest.json"),"UTF-8")){char[] b=new char[8192];int n;while((n=input.read(b))!=-1)text.write(b,0,n);}
  JSONArray samples=new JSONArray(text.toString()),details=new JSONArray();int count=0,exact=0,rejected=0,wrong=0,mustMiss=0;long max=0,total=0;
  boolean full="full".equals(args.getString("suite","smoke"));
  for(int i=0;i<samples.length();i++){
   JSONObject sample=samples.getJSONObject(i);if(!full&&!sample.optBoolean("smoke"))continue;
   List<String> expected=new ArrayList<>();JSONArray ids=sample.getJSONArray("glyphs");for(int j=0;j<ids.length();j++)expected.add(ids.getString(j));
   Bitmap bitmap;try(InputStream in=getTargetContext().getAssets().open("reader-checks/"+sample.getString("file"))){bitmap=BitmapFactory.decodeStream(in);}
   long t=System.nanoTime();GlyphReader.Result result=null;String failure="";try{result=reader.read(bitmap,null);}catch(IOException e){failure=e.getMessage();}bitmap.recycle();long elapsed=(System.nanoTime()-t)/1000000;max=Math.max(max,elapsed);total+=elapsed;
   boolean accepted=result!=null&&result.confirmed(),ok=accepted&&result.glyphs().equals(expected);count++;if(ok)exact++;else if(accepted)wrong++;else rejected++;
   boolean recoverable=result!=null&&result.readable&&result.glyphs().equals(expected);
   if(sample.optBoolean("must")&&!(sample.optBoolean("interactive")?recoverable:ok))mustMiss++;
   JSONObject record=new JSONObject();record.put("file",sample.getString("file"));record.put("accepted",accepted);record.put("interactive",sample.optBoolean("interactive"));record.put("exact",ok);record.put("ms",elapsed);record.put("expected",ids);if(result!=null){record.put("actual",new JSONArray(result.glyphs()));record.put("readable",result.readable);JSONArray pending=new JSONArray();for(GlyphReader.Row row:result.rows)for(GlyphReader.Symbol s:row.symbols)if(!s.confirmed)pending.put(s.id);record.put("pending",pending);}else record.put("error",failure);details.put(record);
   if(count%25==0){Bundle progress=new Bundle();progress.putString("progress",count+" images, "+exact+" exact, "+wrong+" wrong, "+rejected+" uncertain");sendStatus(0,progress);}
  }
  // Exercise persistent draft append and preservation, independently of keyboard UI.
  ImageDraftStore.save(getTargetContext(),Collections.emptyList());List<List<String>> first=Collections.singletonList(Arrays.asList("F0835","F1047"));ImageDraftStore.save(getTargetContext(),first);
  if(!ImageDraftStore.append(getTargetContext(),Collections.singletonList(Numerals.encode("123456")))||ImageDraftStore.read(getTargetContext()).size()!=2)throw new AssertionError("Draft append");
  String before=ImageDraftStore.code(getTargetContext());if(ImageDraftStore.append(getTargetContext(),Collections.singletonList(Collections.nCopies(120,"F0835")))||!before.equals(ImageDraftStore.code(getTargetContext())))throw new AssertionError("Draft overflow mutated content");ImageDraftStore.save(getTargetContext(),Collections.emptyList());
  JSONObject report=new JSONObject();report.put("suite",full?"full":"smoke");report.put("total",count);report.put("exact",exact);report.put("rejected",rejected);report.put("wrong",wrong);report.put("required_misses",mustMiss);report.put("load_ms",(loaded-start)/1000000);report.put("mean_ms",count==0?0:total/count);report.put("max_ms",max);report.put("records",details);
  try(Writer file=new OutputStreamWriter(new FileOutputStream(new File(getTargetContext().getFilesDir(),"reader-"+(full?"full":"smoke")+".json")),"UTF-8")){file.write(report.toString(2));}
  JSONObject summary=new JSONObject(report.toString());summary.remove("records");out.putString("result",summary.toString());finish(wrong==0&&mustMiss==0?-1:1,out);
 }catch(Throwable e){StringWriter error=new StringWriter();e.printStackTrace(new PrintWriter(error));out.putString("failure",error.toString());finish(1,out);}}
}
