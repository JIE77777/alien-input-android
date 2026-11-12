package io.github.alieninput.semantic;

import java.io.*;
import java.net.URLDecoder;
import java.text.Normalizer;
import java.util.*;

/** Immutable, offline word lookup. Does not translate or infer sentence grammar. */
public final class Lexicon {
    public static final int MAX_QUERY_CODEPOINTS = 128;
    public enum Kind { EXACT, ALIAS, RELATED, PARTIAL, TYPO, NUMBER }

    public static final class Entry {
        public final String id, label, note, source;
        public final List<String> glyphs;
        private Entry(String id, String label, String sequence, String note, String source) {
            this.id=id; this.label=label; this.note=note; this.source=source;
            List<String> ids=Arrays.asList(sequence.split(","));
            if (ids.isEmpty() || ids.stream().anyMatch(g -> !Numerals.isGlyphId(g)))
                throw new IllegalArgumentException("Invalid glyph sequence: "+id);
            this.glyphs=Collections.unmodifiableList(new ArrayList<>(ids));
        }
    }
    public static final class Match {
        public final Entry entry;
        public final Kind kind;
        public final String matchedTerm, source;
        private Match(Entry entry, Kind kind, Term term) {
            this.entry=entry; this.kind=kind; this.matchedTerm=term.raw; this.source=term.source;
        }
    }
    private static final class Term {
        final String key, raw, source;
        final Entry entry;
        final Kind kind;
        Term(String raw, Entry entry, Kind kind, String source) {
            this.key=normalize(raw); this.raw=raw; this.entry=entry; this.kind=kind; this.source=source;
        }
    }
    private final Map<String,Entry> entries;
    private final Map<String,List<Term>> exact;
    private final List<Term> terms;

    public Lexicon(Reader reader) throws IOException {
        Map<String,Entry> loaded=new LinkedHashMap<>();
        List<String[]> pending=new ArrayList<>();
        try (BufferedReader br=new BufferedReader(reader)) {
            if (!"# alien-lexicon-v1".equals(br.readLine())) throw new IOException("Unsupported lexicon format");
            String line;
            try {
                while ((line=br.readLine())!=null) {
                    if (line.isEmpty()) continue;
                    String[] f=line.split("\t",-1);
                    for (int i=0;i<f.length;i++) f[i]=URLDecoder.decode(f[i],"UTF-8");
                    if (f.length==6 && f[0].equals("E")) {
                        if (f[1].isEmpty() || f[2].isEmpty() || loaded.containsKey(f[1])) throw new IOException("Invalid entry");
                        loaded.put(f[1],new Entry(f[1],f[2],f[3],f[4],f[5]));
                    } else if (f.length==5 && f[0].equals("T")) pending.add(f);
                    else throw new IOException("Invalid lexicon row");
                }
                Map<String,List<Term>> index=new HashMap<>();
                List<Term> all=new ArrayList<>();
                for (String[] f:pending) {
                    Entry e=loaded.get(f[3]);
                    Kind k=Kind.valueOf(f[1].toUpperCase(Locale.ROOT));
                    if (e==null || k.ordinal()>Kind.RELATED.ordinal()) throw new IOException("Invalid term reference");
                    Term t=new Term(f[2],e,k,f[4]);
                    if (t.key.isEmpty()) throw new IOException("Empty lookup term");
                    all.add(t);
                    index.computeIfAbsent(t.key,key->new ArrayList<>()).add(t);
                }
                entries=Collections.unmodifiableMap(loaded);
                exact=Collections.unmodifiableMap(index);
                terms=Collections.unmodifiableList(all);
            } catch (IllegalArgumentException ex) { throw new IOException("Invalid lexicon",ex); }
        }
    }

    public Collection<Entry> entries() { return entries.values(); }
    public Entry entry(String id) { return entries.get(id); }
    public static String normalize(String text) {
        return Normalizer.normalize(text==null?"":text,Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Z}]+"," ").trim();
    }

    /** Return all matches. UI pagination must not silently discard the remainder. */
    public List<Match> search(String query, Map<String,Integer> frequency) {
        if (query==null || query.codePointCount(0,query.length())>MAX_QUERY_CODEPOINTS) return Collections.emptyList();
        String key=normalize(query);
        if (key.isEmpty()) return Collections.emptyList();
        Map<String,Match> found=new HashMap<>();
        for (Term t:exact.getOrDefault(key,Collections.emptyList())) add(found,t,t.kind);
        // Numbers need value conversion; matching substrings of dictionary labels is not arithmetic.
        if(Numerals.isInteger(key)){
            if(!found.isEmpty())return Collections.unmodifiableList(new ArrayList<>(found.values()));
            List<String> glyphs=Numerals.encode(key);
            if(glyphs.isEmpty())return Collections.emptyList();
            String value=new java.math.BigInteger(key).toString();
            Entry entry=new Entry("NUM:"+value,value,String.join(",",glyphs),
                "十进制数值转为七进制数词；按数值转换，不是逐位替换。",
                "alien-input/v6.12.4/src/js/numbers.js#encodeInteger");
            return Collections.singletonList(new Match(entry,Kind.NUMBER,new Term(key,entry,Kind.NUMBER,entry.source)));
        }
        boolean han=key.codePoints().anyMatch(c -> Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN);
        int length=key.codePointCount(0,key.length());
        if (length>=(han?2:3)) for (Term t:terms) {
            if (t.kind==Kind.RELATED || t.key.equals(key)) continue;
            if (t.key.contains(key)) add(found,t,Kind.PARTIAL);
        }
        // Typos apply only to a single Latin word and only when direct lookup found nothing.
        if (found.isEmpty() && latinWord(key,4,32)) for (Term t:terms) {
            if (t.kind!=Kind.RELATED && latinWord(t.key,3,33) && oneEdit(key,t.key)) add(found,t,Kind.TYPO);
        }
        List<Match> result=new ArrayList<>(found.values());
        result.sort(Comparator.comparingInt((Match m)->m.kind.ordinal())
            .thenComparing(Comparator.comparingInt((Match m)->Math.max(0,frequency.getOrDefault(m.entry.id,0))).reversed())
            .thenComparing(m->m.entry.id));
        return Collections.unmodifiableList(result);
    }
    private static void add(Map<String,Match> found, Term t, Kind kind) {
        Match old=found.get(t.entry.id);
        if (old==null || kind.ordinal()<old.kind.ordinal()) found.put(t.entry.id,new Match(t.entry,kind,t));
    }
    private static boolean oneEdit(String a,String b) {
        if (Math.abs(a.length()-b.length())>1) return false;
        int i=0,j=0,edits=0;
        while (i<a.length() && j<b.length()) {
            if(a.charAt(i)==b.charAt(j)){i++;j++;continue;}
            if(++edits>1)return false;
            if(a.length()>=b.length())i++;
            if(b.length()>=a.length())j++;
        }
        return edits+(a.length()-i)+(b.length()-j)==1;
    }
    private static boolean latinWord(String s,int min,int max) {
        if(s.length()<min||s.length()>max)return false;
        for(int i=0;i<s.length();i++)if(s.charAt(i)<'a'||s.charAt(i)>'z')return false;
        return true;
    }
}
