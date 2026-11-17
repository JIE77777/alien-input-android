package io.github.alieninput.androidsemantic;

import android.content.Context;
import android.content.SharedPreferences;
import io.github.alieninput.semantic.*;
import java.io.IOException;
import java.util.*;

/** A local image draft survives editor changes and the image-reading activity. */
public final class ImageDraftStore {
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("alien-image-draft",0);}
    public static String code(Context c){return prefs(c).getString("code","");}
    public static synchronized List<List<String>> read(Context c){try{String s=code(c);return s.isEmpty()?new ArrayList<>():PngMessage.decode(s).groups;}catch(IOException e){return new ArrayList<>();}}
    public static synchronized void save(Context c,List<List<String>> groups){String value=groups.isEmpty()?"":PngMessage.encode(groups);if(!value.equals(code(c)))prefs(c).edit().putString("code",value).commit();}
    public static synchronized boolean append(Context c,List<List<String>> groups){List<List<String>> all=new ArrayList<>(read(c));int count=0;for(List<String> g:all)count+=g.size();for(List<String> g:groups)count+=g.size();if(count>Draft.MAX_GLYPHS)return false;all.addAll(groups);save(c,all);return true;}
}
