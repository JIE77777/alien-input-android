package io.github.alieninput.semantic;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;

/** Versioned glyph code carried in a standard PNG tEXt chunk, with no query text. */
public final class PngMessage {
    private static final byte[] SIGNATURE={(byte)137,80,78,71,13,10,26,10};
    private static final String PREFIX="ALIEN1|v6.12.4|";
    private static final String KEY="AlienInput";
    public static final int MAX_PNG_BYTES=16*1024*1024;
    public final List<List<String>> groups;
    private PngMessage(List<List<String>> groups){
        List<List<String>> copy=new ArrayList<>();
        for(List<String> group:groups)copy.add(Collections.unmodifiableList(new ArrayList<>(group)));
        this.groups=Collections.unmodifiableList(copy);
    }
    public static String encode(List<List<String>> groups){
        if(groups.isEmpty())throw new IllegalArgumentException("Empty message");
        StringBuilder code=new StringBuilder(PREFIX);int count=0;
        for(List<String> group:groups){
            if(group.isEmpty())throw new IllegalArgumentException("Empty group");
            if(code.length()>PREFIX.length())code.append(';');
            for(int i=0;i<group.size();i++){
                String id=group.get(i);
                if(!Numerals.isGlyphId(id)||++count>120)throw new IllegalArgumentException("Invalid glyph sequence");
                if(i>0)code.append(',');code.append(id);
            }
        }
        return code.toString();
    }
    public static PngMessage decode(String code)throws IOException{
        if(code==null||!code.startsWith(PREFIX)||code.length()>4096)throw new IOException("Unsupported message code");
        List<List<String>> groups=new ArrayList<>();
        for(String group:code.substring(PREFIX.length()).split(";",-1))groups.add(Arrays.asList(group.split(",",-1)));
        try{encode(groups);}catch(IllegalArgumentException e){throw new IOException("Invalid glyph code",e);}
        return new PngMessage(groups);
    }
    private static final class Chunk {
        final String type;
        final byte[] data;
        Chunk(String type,byte[] data){this.type=type;this.data=data;}
        boolean message(){return type.equals("tEXt")&&data.length>KEY.length()&&new String(data,0,KEY.length()+1,StandardCharsets.US_ASCII).equals(KEY+"\0");}
    }
    private static List<Chunk> chunks(byte[] png)throws IOException{
        if(png.length>MAX_PNG_BYTES||png.length<20)throw new IOException("Invalid PNG size");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(png));byte[] sig=new byte[8];in.readFully(sig);
        if(!Arrays.equals(sig,SIGNATURE))throw new IOException("Not a PNG");
        List<Chunk> result=new ArrayList<>();boolean end=false;
        while(in.available()>0){
            int length=in.readInt();if(length<0||length>in.available()-8)throw new IOException("Invalid chunk size");
            byte[] type=new byte[4];in.readFully(type);byte[] data=new byte[length];in.readFully(data);int crc=in.readInt();
            CRC32 actual=new CRC32();actual.update(type);actual.update(data);if((int)actual.getValue()!=crc)throw new IOException("PNG CRC mismatch");
            String name=new String(type,StandardCharsets.US_ASCII);
            if(result.isEmpty()&&(!name.equals("IHDR")||length!=13))throw new IOException("Missing PNG header");
            result.add(new Chunk(name,data));
            if(name.equals("IEND")){if(length!=0||in.available()!=0)throw new IOException("Invalid PNG end");end=true;break;}
        }
        if(!end)throw new IOException("Incomplete PNG");return result;
    }
    private static void writeChunk(DataOutputStream out,Chunk chunk)throws IOException{
        byte[] type=chunk.type.getBytes(StandardCharsets.US_ASCII);CRC32 crc=new CRC32();crc.update(type);crc.update(chunk.data);
        out.writeInt(chunk.data.length);out.write(type);out.write(chunk.data);out.writeInt((int)crc.getValue());
    }
    public static byte[] embed(byte[] png,List<List<String>> groups)throws IOException{
        byte[] text=(KEY+"\0"+encode(groups)).getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(buffer);out.write(SIGNATURE);
        for(Chunk chunk:chunks(png)){
            if(chunk.message())continue;
            if(chunk.type.equals("IEND"))writeChunk(out,new Chunk("tEXt",text));
            writeChunk(out,chunk);
        }
        return buffer.toByteArray();
    }
    /** Returns null when the PNG has no embedded message; never guesses from pixels. */
    public static PngMessage read(byte[] png)throws IOException{
        PngMessage message=null;
        for(Chunk chunk:chunks(png))if(chunk.message()){
            if(message!=null)throw new IOException("Duplicate message metadata");
            message=decode(new String(chunk.data,KEY.length()+1,chunk.data.length-KEY.length()-1,StandardCharsets.US_ASCII));
        }
        return message;
    }
    public static byte[] readBounded(InputStream in)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=in.read(buffer))!=-1){if(out.size()+n>MAX_PNG_BYTES)throw new IOException("PNG too large");out.write(buffer,0,n);}
        return out.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("Pass a PNG path");
        try(InputStream in=new FileInputStream(args[0])){
            PngMessage message=read(readBounded(in));
            System.out.println(message==null?"No AlienInput metadata":encode(message.groups));
        }
    }
}
