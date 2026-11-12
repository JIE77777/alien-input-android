package io.github.alieninput.androidsemantic;
import android.app.*;import android.os.*;import android.accessibilityservice.AccessibilityServiceInfo;import android.view.accessibility.*;import android.util.Xml;import org.xmlpull.v1.XmlSerializer;import android.graphics.Rect;import java.io.*;
public final class WindowProbe extends Instrumentation {
 private Bundle args;
 public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
 public void onStart(){Bundle out=new Bundle();try{
  UiAutomation ui=getUiAutomation();AccessibilityServiceInfo info=ui.getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS|AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;ui.setServiceInfo(info);SystemClock.sleep(350);
  String action=args.getString("action","dump"), selector=args.getString("selector","");
  if(action.equals("click")||action.equals("text")||action.equals("paste")){
   boolean done=false;
   for(AccessibilityWindowInfo w:ui.getWindows()){AccessibilityNodeInfo n=find(w.getRoot(),selector);if(n!=null){
    if(action.equals("text")){Bundle b=new Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,args.getString("value",""));done=n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b);}
    else if(action.equals("paste")){done=n.performAction(AccessibilityNodeInfo.ACTION_PASTE);}
    else {while(n!=null&&!n.isClickable())n=n.getParent();if(n!=null)done=n.performAction(AccessibilityNodeInfo.ACTION_CLICK);}
    break;
   }}out.putString("performed",String.valueOf(done));SystemClock.sleep(250);
  }
  StringWriter str=new StringWriter();XmlSerializer xml=Xml.newSerializer();xml.setOutput(str);xml.startTag(null,"windows");for(AccessibilityWindowInfo w:ui.getWindows()){xml.startTag(null,"window");xml.attribute(null,"type",String.valueOf(w.getType()));write(xml,w.getRoot());xml.endTag(null,"window");}xml.endTag(null,"windows");xml.flush();out.putString("tree",str.toString());finish(0,out);
 }catch(Exception e){out.putString("error",e.toString());finish(1,out);}}
 private boolean allowed(AccessibilityNodeInfo n){String p=String.valueOf(n.getPackageName());return p.equals("io.github.alieninput.android")||p.equals("io.github.alieninput.trime.debug");}
 private AccessibilityNodeInfo find(AccessibilityNodeInfo n,String q){if(n==null)return null;if(allowed(n)&&(q.contentEquals(n.getText()==null?"":n.getText())||q.contentEquals(n.getContentDescription()==null?"":n.getContentDescription())||(q.equals("@editor")&&"android.widget.EditText".contentEquals(n.getClassName()))))return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo f=find(n.getChild(i),q);if(f!=null)return f;}return null;}
 private void write(XmlSerializer x,AccessibilityNodeInfo n)throws Exception{if(n==null)return;if(!allowed(n)){for(int i=0;i<n.getChildCount();i++)write(x,n.getChild(i));return;}x.startTag(null,"node");x.attribute(null,"text",String.valueOf(n.getText()==null?"":n.getText()));x.attribute(null,"desc",String.valueOf(n.getContentDescription()==null?"":n.getContentDescription()));x.attribute(null,"class",String.valueOf(n.getClassName()));Rect r=new Rect();n.getBoundsInScreen(r);x.attribute(null,"bounds",r.toShortString());x.attribute(null,"clickable",String.valueOf(n.isClickable()));for(int i=0;i<n.getChildCount();i++)write(x,n.getChild(i));x.endTag(null,"node");}
}
