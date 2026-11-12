package io.github.alieninput.semantic;

import java.io.*;
import java.math.BigInteger;
import java.util.*;

public final class NumeralsTest {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        Lexicon lexicon=new Lexicon(new InputStreamReader(new FileInputStream(args[0]),"UTF-8"));
        Map<String,BigInteger> values=new HashMap<>();
        String[][] rows={{"F1302","F1304","F1305","F1306"},{"F1310","F1311","F1313","F1314"},
            {"F1317","F1318","F1320","F1321"},{"F1324","F1325","F1327","F1328"},
            {"F1331","F1332","F1334","F1335"},{"F1338","F1339","F1341","F1342"}};
        values.put("F1301",BigInteger.ZERO);
        for(int d=0;d<6;d++)for(int p=0;p<4;p++)values.put(rows[d][p],BigInteger.valueOf(d+1).multiply(BigInteger.valueOf(7).pow(p)));
        for(int number=-2400;number<=2400;number++)verify(lexicon,String.valueOf(number),values);
        for(String number:new String[]{"2401","12345","20261112","999999999999999999","-999999999999999999","0001","+1","-0"})verify(lexicon,number,values);
        check(Numerals.encode("1234").equals(Arrays.asList("F1321","F1327","F1304","F1310")),"1234 = 3*343 + 4*49 + 1*7 + 2");
        check(Numerals.encode("2401").equals(Arrays.asList("N1P4")),"four bars above one");
        check(lexicon.search("１２３４",Collections.emptyMap()).get(0).entry.glyphs.equals(Numerals.encode("1234")),"fullwidth input");
        check(lexicon.search("1",Collections.emptyMap()).get(0).entry.glyphs.equals(Arrays.asList("F1302")),"annotated source label does not hide one");
        for(String invalid:new String[]{"1.5","1/2","abc","123a","1 2","","+","1000000000000000000"})
            check(Numerals.encode(invalid).isEmpty(),"unsupported value not guessed: "+invalid);
        for(String invalid:new String[]{"N0P4","N1P3","N7P4","N1P22","N1P04","N1P9999999999"})check(!Numerals.isGenerated(invalid),"bounded generated ID");
        List<List<String>> groups=Arrays.asList(Numerals.encode("123456"),Numerals.encode("-2401"));
        check(PngMessage.decode(PngMessage.encode(groups)).groups.equals(groups),"generated numerals survive encoded message");
        System.out.println("PASS "+checks+" native integer conversion and numeric search checks");
    }
    private static void verify(Lexicon lexicon,String number,Map<String,BigInteger> values){
        List<String> glyphs=Numerals.encode(number);BigInteger result=BigInteger.ZERO;boolean negative=false;
        for(String id:glyphs){
            if(id.equals("F0098")){negative=true;continue;}
            BigInteger value=values.get(id);
            if(value==null){check(Numerals.isGenerated(id),"known glyph "+id);value=BigInteger.valueOf(id.charAt(1)-'0').multiply(BigInteger.valueOf(7).pow(Integer.parseInt(id.substring(3))));}
            result=result.add(value);
        }
        if(negative)result=result.negate();check(result.equals(new BigInteger(number)),"numeric value roundtrip "+number);
        List<Lexicon.Match> matches=lexicon.search(number,Collections.emptyMap());check(!matches.isEmpty(),"numeric candidate "+number);
        check(matches.get(0).entry.glyphs.equals(glyphs),"numeric lookup sequence "+number);
    }
}
