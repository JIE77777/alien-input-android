package io.github.alieninput.androidsemantic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.*;
import java.nio.*;
import java.util.zip.GZIPInputStream;
import static io.github.alieninput.androidsemantic.GlyphMath.*;
import java.util.*;

/** Pixel-only reader for the production image layout. All work runs off the UI thread. */
public final class GlyphReader {
    public interface Progress { void update(int row,int total); }
    public static final class Symbol {
        public String id;
        public boolean confirmed;
        public final List<String> candidates;
        Symbol(String id,boolean confirmed,List<String> candidates){this.id=id;this.confirmed=confirmed;this.candidates=candidates;}
    }
    public static final class Row {
        public final List<Symbol> symbols;
        public final float score;
        Row(List<Symbol> symbols,float score){this.symbols=symbols;this.score=score;}
    }
    public static final class Result {
        public final List<Row> rows=new ArrayList<>();
        public boolean readable=true;
        public int count(){int n=0;for(Row r:rows)n+=r.symbols.size();return n;}
        public boolean confirmed(){if(!readable||rows.isEmpty())return false;for(Row r:rows)for(Symbol s:r.symbols)if(!s.confirmed)return false;return true;}
        public List<List<String>> groups(){List<List<String>> out=new ArrayList<>();for(Row r:rows){List<String> ids=new ArrayList<>();for(Symbol s:r.symbols)ids.add(s.id);out.add(ids);}return out;}
        public List<String> glyphs(){List<String> ids=new ArrayList<>();for(List<String> g:groups())ids.addAll(g);return ids;}
    }
    private static final int STATIC=1344, COUNT=1452, SIZE=100, FEATURE=48;
    private static GlyphReader cached;
    private static boolean registered;
    public static synchronized GlyphReader get(Context context)throws IOException{
        if(cached==null)cached=new GlyphReader(context.getApplicationContext());
        if(!registered){registered=true;context.getApplicationContext().registerComponentCallbacks(new android.content.ComponentCallbacks2(){
            public void onConfigurationChanged(android.content.res.Configuration c){}
            public void onLowMemory(){releaseCache();}
            public void onTrimMemory(int level){if(level>=TRIM_MEMORY_RUNNING_LOW&&level!=TRIM_MEMORY_UI_HIDDEN)releaseCache();}
        });}
        return cached;
    }
    private static synchronized void releaseCache(){cached=null;}
    private static final class Template {
        final int id;final byte[] mask;final float[] feature,coarse;
        Template(int id,byte[] mask,float[] feature,float[] coarse){this.id=id;this.mask=mask;this.feature=feature;this.coarse=coarse;}
    }
    private static final class NumberModel {
        final int id,split;final float[] bars,base;
        NumberModel(int id,int split,float[] bars,float[] base){this.id=id;this.split=split;this.bars=bars;this.base=base;}
    }
    private final List<Template> templates=new ArrayList<>();
    private final List<NumberModel> numbers=new ArrayList<>();
    private final String[] ids=new String[COUNT];
    private GlyphReader(Context context)throws IOException{
        for(int i=0;i<STATIC;i++)ids[i]=String.format(Locale.ROOT,"F%04d",i+1);
        for(int c=1;c<=6;c++)for(int p=4;p<=21;p++)ids[STATIC+(c-1)*18+p-4]="N"+c+"P"+p;
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(context.getAssets().open("alien/reader-model.agr")),131072))){
            if(in.readInt()!=0x41475231)throw new IOException("读图字库版本不匹配");
            int count=in.readInt();if(count!=3120)throw new IOException("读图字库不完整");
            for(int i=0;i<count;i++){interrupted();int id=in.readInt();byte[] mask=new byte[10000];in.readFully(mask);templates.add(new Template(id,mask,readFloats(in,2304),readFloats(in,144)));}
            int n=in.readInt();if(n!=432)throw new IOException("数字字库不完整");
            for(int i=0;i<n;i++){interrupted();int id=in.readInt(),split=in.readInt();if(id<STATIC||id>=COUNT||split<1||split>=100)throw new IOException("数字字库不完整");numbers.add(new NumberModel(id,split,readFloats(in,split*100),readFloats(in,2304)));}
        }
    }
    private static float[] readFloats(DataInputStream in,int n)throws IOException{byte[] bytes=new byte[n*4];in.readFully(bytes);float[] out=new float[n];ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asFloatBuffer().get(out);return out;}
    private static void interrupted()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("已取消");}
    private static byte[] alpha(Bitmap b){int[] pixels=new int[b.getWidth()*b.getHeight()];b.getPixels(pixels,0,b.getWidth(),0,0,b.getWidth(),b.getHeight());byte[] a=new byte[pixels.length];for(int i=0;i<a.length;i++)a[i]=(byte)(pixels[i]>>>24);return a;}
    public Result read(Bitmap source,Progress progress)throws IOException{
        int width=source.getWidth(),height=source.getHeight();if(width<240||width>4096||(long)width*height>16000000)throw new IOException("请选一张完整、清晰的外星消息图片");
        double canonical=height*480.0/width,tolerance=Math.max(2.1,528.0/width);int rows,h;
        if(Math.abs(canonical-208)<=tolerance){rows=1;h=208;}else{rows=(int)Math.round((canonical-32)/112);h=rows*112+32;if(rows<2||rows>48||Math.abs(canonical-h)>tolerance)throw new IOException("请选完整的外星消息图片，保留四周留白");}
        Bitmap scaled=Bitmap.createScaledBitmap(source,480,h,true);byte[] normalized=normalize(scaled);if(scaled!=source)scaled.recycle();
        Result result=new Result();
        for(int row=0;row<rows;row++){
            interrupted();if(progress!=null)progress.update(row+1,rows);int top=(h-rows*112)/2+6+row*112;
            byte[] band=crop(normalized,480,h,0,top-6,480,112);Row decoded=readRow(band);result.rows.add(decoded);
            if(decoded.symbols.isEmpty()||decoded.score<.70f)result.readable=false;
        }
        if(result.count()<1||result.count()>120)result.readable=false;
        for(int i=1;i<result.rows.size();i++)if(result.rows.get(i-1).symbols.size()+result.rows.get(i).symbols.size()<=4)result.readable=false;
        return result;
    }
    private static byte[] normalize(Bitmap b)throws IOException{
        int w=b.getWidth(),h=b.getHeight();int[] px=new int[w*h];b.getPixels(px,0,w,0,0,w,h);
        int[][] corner=new int[3][576];int at=0;
        for(int oy:new int[]{0,h-12})for(int ox:new int[]{0,w-12})for(int y=0;y<12;y++)for(int x=0;x<12;x++){
            int c=px[(oy+y)*w+ox+x];corner[0][at]=(c>>>16)&255;corner[1][at]=(c>>>8)&255;corner[2][at]=c&255;at++;
        }
        int[] bg=new int[3];for(int c=0;c<3;c++){Arrays.sort(corner[c]);bg[c]=corner[c][288];}
        int[] histogram=new int[443];float[] distances=new float[px.length];
        for(int i=0;i<px.length;i++){int c=px[i],r=((c>>>16)&255)-bg[0],g=((c>>>8)&255)-bg[1],v=(c&255)-bg[2];float d=(float)Math.sqrt(r*r+g*g+v*v);distances[i]=d;if(i>=16*w&&i<(h-16)*w)histogram[Math.min(442,Math.round(d))]++;}
        int target=(int)Math.ceil((h-32)*w*.998),sum=0,scale=0;for(;scale<443;scale++){sum+=histogram[scale];if(sum>=target)break;}
        if(scale<12)throw new IOException("没有找到清晰的字形，换一张图片试试");byte[] out=new byte[px.length];
        for(int i=0;i<out.length;i++){int n=Math.min(255,Math.round(distances[i]*255/scale));out[i]=(byte)(n<9?0:n);}return out;
    }
    private static final class Match {
        int template;float score,margin;boolean accepted;List<String> candidates;
    }
    private Match classify(byte[] mask){
        float[] f=vector(mask,100,100,48,48,.6f),coarse=resizeNormalized(f,48,48,12,12);
        int[] shortlist=new int[64];float[] shortlistScores=new float[64];Arrays.fill(shortlistScores,-1);Arrays.fill(shortlist,-1);
        for(int i=0;i<templates.size();i++){float s=dot(coarse,templates.get(i).coarse);if(s<=shortlistScores[63])continue;int j=63;while(j>0&&s>shortlistScores[j-1]){shortlistScores[j]=shortlistScores[j-1];shortlist[j]=shortlist[j-1];j--;}shortlistScores[j]=s;shortlist[j]=i;}
        float[] scores=new float[COUNT];int[] styles=new int[COUNT];Arrays.fill(styles,-1);
        for(int i:shortlist)if(i>=0){Template t=templates.get(i);float s=dot(f,t.feature);if(s>scores[t.id]){scores[t.id]=s;styles[t.id]=i;}}
        int[] order=top(scores,3);int chosen=order[0];Match m=new Match();m.score=scores[chosen];m.margin=m.score-scores[order[1]];m.accepted=m.score>=.91f&&m.margin>=.025f;
        if(chosen>=STATIC){
            float[] ns=new float[COUNT],bs=new float[COUNT],cs=new float[COUNT];Map<Integer,float[][]> cache=new HashMap<>();float[][] nf;
            for(NumberModel model:numbers){nf=cache.get(model.split);if(nf==null){nf=numberFeatures(mask,model.split);cache.put(model.split,nf);}float a=dot(nf[0],model.bars),b=dot(nf[1],model.base),s=(a+b)*.5f;if(s>ns[model.id]){ns[model.id]=s;bs[model.id]=a;cs[model.id]=b;}}
            int[] ranked=top(ns,2);int candidate=ranked[0];boolean listed=candidate==order[0]||candidate==order[1]||candidate==order[2];float bestStatic=0;for(int i=0;i<STATIC;i++)bestStatic=Math.max(bestStatic,scores[i]);
            m.accepted=listed&&scores[candidate]>=.91f&&scores[candidate]-bestStatic>=.025f&&ns[candidate]>=.94f&&ns[candidate]-ns[ranked[1]]>=.025f&&bs[candidate]>=.90f&&cs[candidate]>=.90f;
            if(listed)chosen=candidate;
        }
        m.template=styles[chosen];m.candidates=new ArrayList<>();m.candidates.add(ids[chosen]);for(int i:order)if(!m.candidates.contains(ids[i]))m.candidates.add(ids[i]);return m;
    }
    private static final class Hypothesis {float score;List<Symbol> symbols;String key;boolean accepted;}
    private List<Hypothesis> hypotheses(byte[] band){
        List<Hypothesis> out=new ArrayList<>();float[] observed=vector(band,480,112,240,56,.6f);
        for(int count=1;count<=4;count++){
            List<Symbol> symbols=new ArrayList<>();byte[] rendered=new byte[480*112];boolean valid=true;
            for(int col=0;col<count;col++){
                int x=(480-count*112)/2+6+col*112;byte[] tile=crop(band,480,112,x,6,100,100);int ink=0;for(byte b:tile)ink+=b&255;
                if(ink<255*12){valid=false;break;}Match m=classify(tile);if(m.template<0){valid=false;break;}
                Template t=templates.get(m.template);paste(rendered,480,112,t.mask,100,100,x,6);symbols.add(new Symbol(ids[t.id],m.accepted,m.candidates));
            }
            if(!valid)continue;Hypothesis h=new Hypothesis();h.symbols=symbols;h.score=dot(observed,vector(rendered,480,112,240,56,.6f));StringBuilder key=new StringBuilder();h.accepted=true;
            for(Symbol s:symbols){key.append(s.id).append(';');h.accepted&=s.confirmed;}h.key=key.toString();out.add(h);
        }
        return out;
    }
    private static List<Hypothesis> rank(List<Hypothesis> candidates){Map<String,Hypothesis> unique=new HashMap<>();for(Hypothesis h:candidates){Hypothesis old=unique.get(h.key);if(old==null||h.score>old.score)unique.put(h.key,h);}List<Hypothesis> result=new ArrayList<>(unique.values());result.sort((a,b)->Float.compare(b.score,a.score));return result;}
    private static boolean accepted(List<Hypothesis> ranked){return !ranked.isEmpty()&&ranked.get(0).score>=.90f&&ranked.get(0).accepted&&(ranked.size()==1||ranked.get(0).score-ranked.get(1).score>=.025f);}
    private static boolean aligned(List<Hypothesis> ranked){return !ranked.isEmpty()&&ranked.get(0).score>=.90f&&(ranked.size()==1||ranked.get(0).score-ranked.get(1).score>=.025f);}
    private Row readRow(byte[] band)throws IOException{
        List<Hypothesis> all=hypotheses(band),ranked=rank(all);
        if(!aligned(ranked))for(int dx=-2;dx<=2;dx++)for(int dy=-2;dy<=2;dy++){interrupted();if(dx==0&&dy==0)continue;all.addAll(hypotheses(crop(band,480,112,-dx,-dy,480,112)));}
        ranked=rank(all);if(ranked.isEmpty())return new Row(new ArrayList<>(),0);Hypothesis best=ranked.get(0);
        boolean layout=best.score>=.90f&&(ranked.size()==1||best.score-ranked.get(1).score>=.025f);
        if(!layout)for(Symbol s:best.symbols)s.confirmed=false;return new Row(best.symbols,best.score);
    }
}
