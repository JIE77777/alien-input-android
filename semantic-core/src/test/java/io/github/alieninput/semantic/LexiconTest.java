package io.github.alieninput.semantic;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class LexiconTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static Lexicon.Match first(Lexicon l,String q){List<Lexicon.Match> m=l.search(q,Collections.emptyMap());check(!m.isEmpty(),q);return m.get(0);}
    public static void main(String[] args)throws Exception{
        Lexicon l=new Lexicon(new InputStreamReader(new FileInputStream(args[0]),StandardCharsets.UTF_8));
        check(l.entries().size()==2264,"full inventory");
        check(first(l,"你").entry.id.equals("F0835"),"you");
        check(first(l,"食物").entry.id.equals("F1047"),"food");
        check(first(l,"你好").entry.id.equals("F0908"),"greeting");
        check(first(l," ＦＯＯＤ  ").entry.id.equals("F1047"),"unicode normalization");
        check(first(l,"食品").kind==Lexicon.Kind.ALIAS,"alias explanation");
        check(first(l,"吃饭").entry.glyphs.equals(Arrays.asList("F1050","F1047")),"phrase order");
        check(first(l,"foood").kind==Lexicon.Kind.TYPO,"conservative English typo");
        check(l.search("上尖角",Collections.emptyMap()).isEmpty(),"shape alias excluded");
        check(l.search("这是一句不存在的整句查询",Collections.emptyMap()).isEmpty(),"no invented sentence translation");
        check(l.search("",Collections.emptyMap()).isEmpty(),"no arbitrary empty results");
        check(l.search(String.join("",Collections.nCopies(129,"你")),Collections.emptyMap()).isEmpty(),"bounded input");
        check(l.search("1",Collections.emptyMap()).stream().noneMatch(m->m.entry.id.equals("F0124")),"fraction not split into meanings");
        Map<String,Integer> frequency=new HashMap<>();frequency.put("F1050",100000);
        List<Lexicon.Match> food=l.search("食物",frequency);
        check(food.get(0).entry.id.equals("F1047"),"exact beats frequent related");
        check(food.stream().anyMatch(m->m.entry.id.equals("F1050")&&m.kind==Lexicon.Kind.RELATED),"related stays marked");
        for(Lexicon.Entry e:l.entries()){
            List<Lexicon.Match> matches=l.search(e.label,Collections.emptyMap());
            check(matches.stream().anyMatch(m->m.entry.id.equals(e.id)&&m.kind==Lexicon.Kind.EXACT),"own label reachable: "+e.id);
            Set<String> seen=new HashSet<>();
            for(Lexicon.Match m:matches)check(seen.add(m.entry.id),"deduplicated: "+m.entry.id);
            for(String g:e.glyphs)check(l.entry(g)!=null,"glyph reference "+g);
        }
        Draft draft=new Draft();
        draft.add(l.entry("F0835"));draft.add(l.entry("W:F1050.F1047"));
        check(draft.glyphs().equals(Arrays.asList("F0835","F1050","F1047")),"draft order");
        draft.remove(1);check(draft.glyphs().size()==1,"whole phrase removed");
        check(draft.replace(0,l.entry("W:F1050.F1047")),"replace selection");
        check(draft.items().size()==1&&draft.glyphs().size()==2,"replacement atomic");
        draft.clear();for(int i=0;i<120;i++)check(draft.add(l.entry("F0835")),"within limit");
        check(!draft.add(l.entry("F1047")),"limit rejects append");
        check(!draft.replace(0,l.entry("W:F1050.F1047")),"limit rejects replacement");
        check(draft.glyphs().size()==120,"failed operation unchanged");
        for(String bad:new String[]{"# other\n", "# alien-lexicon-v1\nT\texact\tfoo\tmissing\tsource\n"}){
            try {new Lexicon(new StringReader(bad));throw new AssertionError("invalid data accepted");}
            catch(IOException expected){checks++;}
        }
        long[] times=new long[1000];String[] queries={"你","食物","你好","吃饭","food","foood","不存在的词"};
        for(int i=0;i<times.length;i++){long t=System.nanoTime();l.search(queries[i%queries.length],Collections.emptyMap());times[i]=System.nanoTime()-t;}
        Arrays.sort(times);
        System.out.printf(Locale.ROOT,"PASS %d checks; %d entries; host lookup p50 %.2f ms, p95 %.2f ms%n",checks,l.entries().size(),times[500]/1e6,times[950]/1e6);
    }
}
