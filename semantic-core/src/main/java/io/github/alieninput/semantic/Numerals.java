package io.github.alieninput.semantic;

import java.math.BigInteger;
import java.util.*;

/** Integer subset of alien-input v6.12.4 src/js/numbers.js; decimal input, native radix-seven output. */
public final class Numerals {
    public static final int MAX_DECIMAL_DIGITS=18, MAX_POWER=21;
    private static final String[][] ROWS={
        {},{"F1302","F1304","F1305","F1306"},{"F1310","F1311","F1313","F1314"},
        {"F1317","F1318","F1320","F1321"},{"F1324","F1325","F1327","F1328"},
        {"F1331","F1332","F1334","F1335"},{"F1338","F1339","F1341","F1342"}
    };
    private Numerals(){}
    public static boolean isInteger(String text){return text!=null&&text.matches("[+-]?[0-9]+");}
    public static List<String> encode(String text){
        if(!isInteger(text))return Collections.emptyList();
        String digits=(text.startsWith("-")||text.startsWith("+"))?text.substring(1):text;
        if(digits.length()>MAX_DECIMAL_DIGITS)return Collections.emptyList();
        BigInteger value=new BigInteger(text);
        if(value.signum()==0)return Collections.singletonList("F1301");
        String base7=value.abs().toString(7);
        List<String> result=new ArrayList<>();
        if(value.signum()<0)result.add("F0098");
        for(int i=0;i<base7.length();i++){
            int digit=base7.charAt(i)-'0',power=base7.length()-1-i;
            if(digit!=0)result.add(power<4?ROWS[digit][power]:"N"+digit+"P"+power);
        }
        return Collections.unmodifiableList(result);
    }
    public static boolean isGenerated(String id){
        if(id==null||!id.matches("N[1-6]P[1-9][0-9]?"))return false;
        int power=Integer.parseInt(id.substring(3));return power>=4&&power<=MAX_POWER;
    }
    public static boolean isGlyphId(String id){return id!=null&&(id.matches("F\\d{4}")||isGenerated(id));}
    public static String baseGlyph(String id){
        if(!isGenerated(id))throw new IllegalArgumentException("Invalid generated numeral");
        return ROWS[id.charAt(1)-'0'][0];
    }
    public static int power(String id){
        if(!isGenerated(id))throw new IllegalArgumentException("Invalid generated numeral");
        return Integer.parseInt(id.substring(3));
    }
}
