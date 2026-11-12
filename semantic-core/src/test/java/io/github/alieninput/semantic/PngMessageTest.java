package io.github.alieninput.semantic;
import java.io.*;
import java.util.*;
public final class PngMessageTest {
    public static void main(String[] args)throws Exception{
        byte[] plain;try(InputStream in=new FileInputStream(args[0])){plain=PngMessage.readBounded(in);}
        List<List<String>> groups=Arrays.asList(Arrays.asList("F0835"),Arrays.asList("F1050","F1047"));
        String code=PngMessage.encode(groups);
        if(!PngMessage.decode(code).groups.equals(groups))throw new AssertionError("text code round trip");
        byte[] png=PngMessage.embed(plain,groups);
        if(!PngMessage.read(png).groups.equals(groups))throw new AssertionError("PNG round trip");
        if(PngMessage.read(plain)!=null)throw new AssertionError("absent metadata");
        if(!Arrays.equals(png,PngMessage.embed(png,groups)))throw new AssertionError("metadata replacement deterministic");
        png[20]^=1;
        try{PngMessage.read(png);throw new AssertionError("corrupt PNG accepted");}catch(IOException expected){}
        for(String bad:Arrays.asList("ALIEN2|v6.12.4|F0835","ALIEN1|unknown|F0835","ALIEN1|v6.12.4|","ALIEN1|v6.12.4|../../secret","ALIEN1|v6.12.4|F0835;")){
            try{PngMessage.decode(bad);throw new AssertionError("bad code accepted");}catch(IOException expected){}
        }
        System.out.println("PASS PNG/text round trip, phrase boundaries, absent metadata, replacement and invalid inputs");
    }
}
