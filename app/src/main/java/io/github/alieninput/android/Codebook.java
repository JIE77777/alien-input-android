package io.github.alieninput.android;

import java.io.*;
import java.util.*;

/** Platform-independent standard-mode matcher. Root identifiers are case-sensitive. */
public final class Codebook {
    public static final int MAX_ROOTS = 192;
    public static final class Entry {
        public final String id, label;
        public final int frequency;
        public final List<String[]> codes = new ArrayList<>();
        public String[] glyphs;
        private final List<List<String[]>> phraseParts=new ArrayList<>();
        private int[] suffix;
        Entry(String id, String label, int frequency) {
            this.id=id; this.label=label; this.frequency=frequency;
            glyphs=new String[]{id};
        }
    }
    public static final class Match {
        public final Entry entry;
        public final int missing;
        Match(Entry e, int n) { entry=e; missing=n; }
    }
    public final Map<String,Entry> glyphs = new LinkedHashMap<>();
    public final List<Entry> entries = new ArrayList<>();
    public final List<String[]> keys = new ArrayList<>();
    public Codebook(Reader glyphReader, Reader phraseReader, Reader keyReader) throws IOException {
        try (BufferedReader b=new BufferedReader(glyphReader)) {
            String l;
            while ((l=b.readLine())!=null) {
                String[] p=l.split("\t",-1);
                Entry e=new Entry(p[0],p[1],Integer.parseInt(p[2]));
                for(String c:p[3].split("\\|")) e.codes.add(c.split(","));
                glyphs.put(e.id,e); entries.add(e);
            }
        }
        try (BufferedReader b=new BufferedReader(phraseReader)) {
            String l;
            while ((l=b.readLine())!=null) {
                String[] p=l.split("\t",-1);
                Entry e=new Entry(p[0],p[1],Integer.parseInt(p[2]));
                e.glyphs=p[3].split(",");
                for(String id:e.glyphs) if(!glyphs.containsKey(id)) throw new IOException("Unknown glyph: "+id);
                e.suffix=new int[e.glyphs.length+1];
                for(String id:e.glyphs) e.phraseParts.add(choices(glyphs.get(id)));
                for(int j=e.glyphs.length-1;j>=0;j--){
                    int min=Integer.MAX_VALUE;for(String[] c:e.phraseParts.get(j))min=Math.min(min,c.length);
                    e.suffix[j]=min+e.suffix[j+1];
                }
                entries.add(e);
            }
        }
        try (BufferedReader b=new BufferedReader(keyReader)) {
            String l; while((l=b.readLine())!=null) keys.add(l.split("\t",-1));
        }
    }
    private int codeMissing(String[] code, List<String> input) {
        if(input.size()>code.length) return Integer.MAX_VALUE;
        for(int j=0;j<input.size();j++) if(!input.get(j).equals(code[j])) return Integer.MAX_VALUE;
        return code.length-input.size();
    }
    private List<String[]> choices(Entry e) {
        List<String[]> result=new ArrayList<>(e.codes);
        String first=e.codes.get(0)[0];
        boolean exists=false;
        for(String[] c:e.codes) if(c.length==1&&c[0].equals(first)) exists=true;
        if(!exists) result.add(new String[]{first});
        return result;
    }
    // Dynamic programming avoids the Cartesian product of alternate phrase spellings.
    private int phraseMissing(Entry e,List<String> input) {
        Set<Integer> positions=new HashSet<>(); positions.add(0);
        int best=Integer.MAX_VALUE;
        for(int j=0;j<e.glyphs.length && !positions.isEmpty();j++) {
            Set<Integer> next=new HashSet<>();
            for(int at:positions) for(String[] c:e.phraseParts.get(j)) {
                int n=Math.min(c.length,input.size()-at);
                boolean equal=true;
                for(int k=0;k<n;k++) if(!c[k].equals(input.get(at+k))) {equal=false;break;}
                if(!equal) continue;
                if(at+n==input.size()) best=Math.min(best,c.length-n+e.suffix[j+1]);
                else if(n==c.length) next.add(at+n);
            }
            positions=next;
        }
        return best;
    }
    public List<Match> match(List<String> input, Map<String,Integer> learned) {
        if(input.size()>MAX_ROOTS) return Collections.emptyList();
        List<Match> found=new ArrayList<>();
        for(Entry e:entries) {
            int missing=Integer.MAX_VALUE;
            if(input.isEmpty()) missing=0;
            else if(e.glyphs.length>1) missing=phraseMissing(e,input);
            else for(String[] c:e.codes) missing=Math.min(missing,codeMissing(c,input));
            if(missing!=Integer.MAX_VALUE) found.add(new Match(e,missing));
        }
        found.sort((a,b)->{
            int c=Integer.compare(a.missing,b.missing);
            if(c==0) c=Integer.compare(learned.getOrDefault(b.entry.id,0),learned.getOrDefault(a.entry.id,0));
            if(c==0) c=Integer.compare(b.entry.frequency,a.entry.frequency);
            if(c==0) c=a.entry.id.compareTo(b.entry.id);
            return c;
        });
        return found;
    }
    public String label(String id) {
        Entry e=glyphs.get(id);
        if(e!=null) return e.label;
        if("S-H3".equals(id)) return "三横";
        if("S-H".equals(id)) return "横";
        return id.startsWith("S-")?id.substring(2):id;
    }
    public String spelling(Entry e) {
        List<String> roots=new ArrayList<>();
        if(e.glyphs.length==1) roots.addAll(Arrays.asList(e.codes.get(0)));
        else for(String id:e.glyphs) roots.addAll(Arrays.asList(glyphs.get(id).codes.get(0)));
        StringBuilder s=new StringBuilder();
        for(String root:roots) for(String[] k:keys) {
            if(root.equals(k[2])) {s.append(k[0]);break;}
            if(root.equals(k[3])) {s.append(k[1]);break;}
        }
        return s.toString();
    }
}
