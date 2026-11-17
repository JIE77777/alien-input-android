package io.github.alieninput.androidsemantic;
import io.github.alieninput.semantic.*;
import java.util.*;

public final class GlyphNames {
    private final Map<String,String> names=new HashMap<>();
    public GlyphNames(Lexicon lexicon){for(Lexicon.Entry entry:lexicon.entries())if(entry.glyphs.size()==1)names.putIfAbsent(entry.glyphs.get(0),entry.label);}
    public String name(String id){String n=names.get(id);if(n!=null)return n;String number=Numerals.decode(Collections.singletonList(id));return number==null?"外星字形":number;}
    public String group(List<String> ids){if(ids.size()==1)return name(ids.get(0));return "读图 · "+ids.size()+" 个字形";}
}
