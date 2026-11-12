package io.github.alieninput.semantic;

import java.util.*;

/** A draft is a sequence of selected entries, not individual glyphs. */
public final class Draft {
    public static final int MAX_GLYPHS=120;
    private final List<Lexicon.Entry> items=new ArrayList<>();
    public List<Lexicon.Entry> items(){return Collections.unmodifiableList(new ArrayList<>(items));}
    /** Immutable snapshot shared by layout and lossless PNG metadata. */
    public List<List<String>> groups(){
        List<List<String>> result=new ArrayList<>();
        for(Lexicon.Entry e:items)result.add(e.glyphs);
        return Collections.unmodifiableList(result);
    }
    public List<String> glyphs(){
        List<String> result=new ArrayList<>();
        for(Lexicon.Entry e:items)result.addAll(e.glyphs);
        return Collections.unmodifiableList(result);
    }
    public boolean add(Lexicon.Entry e){
        Objects.requireNonNull(e);
        if(glyphs().size()+e.glyphs.size()>MAX_GLYPHS)return false;
        items.add(e);return true;
    }
    public boolean replace(int index,Lexicon.Entry e){
        Objects.requireNonNull(e);
        if(index<0||index>=items.size())return false;
        if(glyphs().size()-items.get(index).glyphs.size()+e.glyphs.size()>MAX_GLYPHS)return false;
        items.set(index,e);return true;
    }
    public void remove(int index){items.remove(index);}
    public void clear(){items.clear();}
}
