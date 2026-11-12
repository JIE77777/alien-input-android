package io.github.alieninput.android;
import java.io.*;
import java.util.*;
public final class CodebookTest {
    static void require(boolean p,String m){if(!p)throw new AssertionError(m);}
    public static void main(String[] args)throws Exception{
        String p=args[0];Codebook b=new Codebook(new FileReader(p+"/glyphs.tsv"),new FileReader(p+"/phrases.tsv"),new FileReader(p+"/keys.tsv"));
        require(b.glyphs.size()==1344,"glyph count");require(b.keys.size()==47,"47 key pairs");
        Set<String> roots=new HashSet<>();for(String[] k:b.keys){roots.add(k[2]);roots.add(k[3]);}require(roots.size()==94,"94 distinct roots");
        int routes=0,phrases=0;
        for(Codebook.Entry e:b.glyphs.values())for(String[] code:e.codes){
            for(String root:code)require(roots.contains(root),"unreachable root: "+root);
            List<Codebook.Match> matches=b.match(Arrays.asList(code),Collections.emptyMap());
            require(matches.stream().anyMatch(m->m.entry==e&&m.missing==0),"lost route "+e.id+Arrays.toString(code));
            boolean prefixSeen=false;for(Codebook.Match m:matches){if(m.missing>0)prefixSeen=true;else require(!prefixSeen,"exact must precede prefix");}
            routes++;
        }
        require(routes==2033,"route count");
        for(Codebook.Entry e:b.entries)if(e.glyphs.length>1){
            List<String> full=new ArrayList<>(),shortCode=new ArrayList<>(),alternate=new ArrayList<>();
            for(String id:e.glyphs){Codebook.Entry g=b.glyphs.get(id);full.addAll(Arrays.asList(g.codes.get(0)));shortCode.add(g.codes.get(0)[0]);alternate.addAll(Arrays.asList(g.codes.get(g.codes.size()-1)));}
            for(List<String> code:Arrays.asList(full,shortCode,alternate))require(b.match(code,Collections.emptyMap()).stream().anyMatch(m->m.entry==e&&m.missing==0),"lost phrase route "+e.id);
            phrases++;
        }
        require(b.match(Arrays.asList("NOT_A_ROOT"),Collections.emptyMap()).isEmpty(),"invalid root");
        List<String> longCode=new ArrayList<>();for(int j=0;j<193;j++)longCode.add("F0001");require(b.match(longCode,Collections.emptyMap()).isEmpty(),"composition bound");
        require(!b.match(Collections.emptyList(),Collections.emptyMap()).isEmpty(),"browse mode");
        Map<String,Integer> learning=new HashMap<>();learning.put("F0001",100);
        require(b.match(Arrays.asList("F0001"),learning).get(0).entry.id.equals("F0001"),"learning ranking");
        require(!b.spelling(b.glyphs.get("F0001")).isEmpty(),"human-readable spelling");
        // Exhaustive independent oracle on an ambiguous tiny phrase lexicon.
        Codebook tiny=new Codebook(new StringReader("F0001\tfirst\t1\ta,b|x\nF0002\tsecond\t1\tc|a,c\n"),
            new StringReader("W:test\tpair\t1\tF0001,F0002\n"),new StringReader("a\tA\ta\tx\nb\tB\tb\tc\n"));
        List<List<String>> oracle=new ArrayList<>();
        for(String route:new String[]{"a,b,c","a,b,a,c","x,c","x,a,c","a,c","a,a,c"})oracle.add(Arrays.asList(route.split(",")));
        List<List<String>> inputs=new ArrayList<>();inputs.add(new ArrayList<>());
        for(int length=1;length<=5;length++) {
            List<List<String>> previous=new ArrayList<>(inputs);
            for(List<String> prefix:previous)if(prefix.size()==length-1)for(String root:new String[]{"a","b","c","x"}){List<String> seq=new ArrayList<>(prefix);seq.add(root);inputs.add(seq);}
        }
        for(List<String> input:inputs)if(!input.isEmpty()){
            int expected=Integer.MAX_VALUE;
            for(List<String> route:oracle)if(input.size()<=route.size()&&route.subList(0,input.size()).equals(input))expected=Math.min(expected,route.size()-input.size());
            int actual=Integer.MAX_VALUE;
            for(Codebook.Match m:tiny.match(input,Collections.emptyMap()))if(m.entry.id.equals("W:test"))actual=m.missing;
            require(expected==actual,"phrase oracle mismatch "+input+": "+expected+" vs "+actual);
        }
        System.out.println("PASS: ambiguous phrase oracle (1364 nonempty inputs).");
        System.out.println("PASS: 1344 glyphs, 2033 routes, "+phrases+" phrases (full / short / alternate), exact ordering, bounds, learning and 94 reachable roots.");
    }
}
