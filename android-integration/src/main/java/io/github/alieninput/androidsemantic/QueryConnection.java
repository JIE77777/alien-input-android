package io.github.alieninput.androidsemantic;

import android.text.Editable;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;

/** Isolated editor for IME queries. It has no reference to the receiving app. */
public final class QueryConnection extends BaseInputConnection {
    public interface Listener { void changed(String confirmed); void emptyBackspace(); void submit(); }
    private final Editable buffer=new SpannableStringBuilder();
    private final Listener listener;
    private int batch;
    private boolean dirty;
    public QueryConnection(View target,Listener listener){super(target,true);this.listener=listener;Selection.setSelection(buffer,0);}
    @Override public Editable getEditable(){return buffer;}
    public void clear(){buffer.clear();Selection.setSelection(buffer,0);notifyChanged();}
    public String confirmed(){
        int start=getComposingSpanStart(buffer),end=getComposingSpanEnd(buffer);
        if(start<0||end<0)return buffer.toString();
        return buffer.subSequence(0,Math.min(start,end)).toString()+buffer.subSequence(Math.max(start,end),buffer.length());
    }
    private void notifyChanged(){if(batch>0){dirty=true;return;}listener.changed(confirmed());}
    @Override public boolean beginBatchEdit(){batch++;return true;}
    @Override public boolean endBatchEdit(){if(batch>0)batch--;if(batch==0&&dirty){dirty=false;notifyChanged();}return true;}
    private boolean fits(CharSequence text){
        if(text==null)return false;
        int start=getComposingSpanStart(buffer),end=getComposingSpanEnd(buffer);
        int replaced=start>=0&&end>=0?Math.abs(end-start):Math.abs(Selection.getSelectionEnd(buffer)-Selection.getSelectionStart(buffer));
        return buffer.length()-replaced+text.length()<=128;
    }
    @Override public boolean commitText(CharSequence text,int cursor){
        if(!fits(text))return false;
        boolean ok=super.commitText(text,cursor);notifyChanged();return ok;
    }
    @Override public boolean setComposingText(CharSequence text,int cursor){
        if(!fits(text))return false;
        return super.setComposingText(text,cursor);
    }
    @Override public boolean finishComposingText(){boolean ok=super.finishComposingText();notifyChanged();return ok;}
    @Override public boolean deleteSurroundingText(int before,int after){
        if(buffer.length()==0){listener.emptyBackspace();return true;}
        boolean ok=super.deleteSurroundingText(before,after);notifyChanged();return ok;
    }
    @Override public boolean deleteSurroundingTextInCodePoints(int before,int after){
        if(buffer.length()==0){listener.emptyBackspace();return true;}
        boolean ok=super.deleteSurroundingTextInCodePoints(before,after);notifyChanged();return ok;
    }
    @Override public boolean sendKeyEvent(KeyEvent event){
        if(event.getAction()!=KeyEvent.ACTION_DOWN)return true;
        if(event.getKeyCode()==KeyEvent.KEYCODE_DEL)return deleteSurroundingTextInCodePoints(1,0);
        if(event.getKeyCode()==KeyEvent.KEYCODE_ENTER){listener.submit();return true;}
        if(event.isCtrlPressed()||event.isAltPressed()||event.isMetaPressed())return true;
        int cp=event.getUnicodeChar();
        if(cp>0&&Character.isValidCodePoint(cp))commitText(new String(Character.toChars(cp)),1);
        return true;
    }
    @Override public boolean performEditorAction(int action){listener.submit();return true;}
    @Override public boolean performContextMenuAction(int id){return false;}
    @Override public boolean requestCursorUpdates(int mode){return false;}
    @Override public ExtractedText getExtractedText(ExtractedTextRequest request,int flags){
        ExtractedText result=new ExtractedText();result.text=buffer.toString();
        result.selectionStart=Selection.getSelectionStart(buffer);result.selectionEnd=Selection.getSelectionEnd(buffer);
        result.partialStartOffset=-1;result.partialEndOffset=-1;return result;
    }
}
