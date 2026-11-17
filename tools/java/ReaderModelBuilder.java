package io.github.alieninput.androidsemantic;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import javax.imageio.ImageIO;
import static io.github.alieninput.androidsemantic.GlyphMath.*;

/** Compile the exact runtime features once, rather than rebuilding them on every phone. */
public final class ReaderModelBuilder {
    private static byte[] mask(Path path)throws IOException{BufferedImage im=ImageIO.read(path.toFile());if(im==null||im.getWidth()!=100||im.getHeight()!=100)throw new IOException(path.toString());byte[] out=new byte[10000];for(int y=0;y<100;y++)for(int x=0;x<100;x++)out[y*100+x]=(byte)(im.getRGB(x,y)>>>24);return out;}
    private static void floats(DataOutputStream out,float[] values)throws IOException{ByteBuffer bytes=ByteBuffer.allocate(values.length*4).order(ByteOrder.BIG_ENDIAN);bytes.asFloatBuffer().put(values);out.write(bytes.array());}
    private static void template(DataOutputStream out,int id,byte[] mask)throws IOException{out.writeInt(id);out.write(mask);float[] f=vector(mask,100,100,48,48,.6f);floats(out,f);floats(out,resizeNormalized(f,48,48,12,12));}
    public static void main(String[] args)throws Exception{
        Path assets=Paths.get(args[0]),fixture=Paths.get(args[1]);List<byte[]> numeric=new ArrayList<>();
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(assets.resolve("reader-model.agr")),131072),131072))){
            out.writeInt(0x41475231);out.writeInt(3120);
            for(String folder:new String[]{"glyphs","recognition-originals"})for(int i=1;i<=1344;i++)template(out,i-1,mask(assets.resolve(folder).resolve(String.format(Locale.ROOT,"F%04d.png",i))));
            for(int c=1;c<=6;c++)for(int p=4;p<=21;p++){String name="N"+c+"P"+p;int id=1344+(c-1)*18+p-4;byte[] raw=mask(fixture.resolve(name+".png"));byte[][] variants={raw,scaleMask(scaleMask(raw,100,100,50,50),50,50,100,100),mask(assets.resolve("recognition-numerals/"+name+"-50.png")),mask(assets.resolve("recognition-numerals/"+name+"-75.png"))};for(byte[] variant:variants){template(out,id,variant);numeric.add(variant);}}
            out.writeInt(432);int n=0;for(int c=1;c<=6;c++)for(int p=4;p<=21;p++){int id=1344+(c-1)*18+p-4,split=Math.round(100f*((p-1)*10+10)/(100+(p-1)*10));for(int k=0;k<4;k++){float[][] f=numberFeatures(numeric.get(n++),split);out.writeInt(id);out.writeInt(split);floats(out,f[0]);floats(out,f[1]);}}
        }
        System.out.println("Compiled 3120 templates and 432 numeral models ("+Files.size(assets.resolve("reader-model.agr"))+" bytes)");
    }
}
