package io.github.alieninput.androidsemantic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.*;
import java.lang.ref.WeakReference;
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
    private static WeakReference<GlyphReader> cached=new WeakReference<>(null);
    public static synchronized GlyphReader get(Context context)throws IOException{
        GlyphReader reader=cached.get();if(reader==null){reader=new GlyphReader(context.getApplicationContext());cached=new WeakReference<>(reader);}return reader;
    }
    private static final class Template {
        final int id;final byte[] mask;final float[] feature,coarse;
        Template(int id,byte[] mask){this.id=id;this.mask=mask;feature=vector(mask,100,100,48,48,.6f);coarse=resizeNormalized(feature,48,48,12,12);}
    }
    private static final class NumberModel {
        final int id,split;final float[] bars,base;
        NumberModel(int id,int split,byte[] mask){this.id=id;this.split=split;float[][] f=numberFeatures(mask,split);bars=f[0];base=f[1];}
    }
    private final List<Template> templates=new ArrayList<>();
    private final List<NumberModel> numbers=new ArrayList<>();
    private final String[] ids=new String[COUNT];
    private GlyphReader(Context context)throws IOException{
        for(int i=0;i<STATIC;i++)ids[i]=String.format(Locale.ROOT,"F%04d",i+1);
        for(int c=1;c<=6;c++)for(int p=4;p<=21;p++)ids[STATIC+(c-1)*18+p-4]="N"+c+"P"+p;
        for(int style=0;style<2;style++)for(int i=0;i<STATIC;i++){
            interrupted();String dir=style==0?"alien/glyphs/":"alien/recognition-originals/";
            try(InputStream in=context.getAssets().open(dir+ids[i]+".png")){
                Bitmap b=BitmapFactory.decodeStream(in);if(b==null||b.getWidth()!=100||b.getHeight()!=100)throw new IOException("字库加载失败");
                templates.add(new Template(i,alpha(b)));b.recycle();
            }
        }
        AlienImages images=new AlienImages(context);
        for(int i=STATIC;i<COUNT;i++){
            interrupted();Bitmap b=images.glyph(ids[i]);if(b==null)throw new IOException("数字字库加载失败");byte[] mask=alpha(b);
            templates.add(new Template(i,mask));int power=Integer.parseInt(ids[i].substring(3));int split=Math.round(100f*((power-1)*10+10)/(100+(power-1)*10));
            numbers.add(new NumberModel(i,split,mask));
            Bitmap half=Bitmap.createScaledBitmap(b,50,50,true),restored=Bitmap.createScaledBitmap(half,100,100,true);
            byte[] reduced=alpha(restored);numbers.add(new NumberModel(i,split,reduced));templates.add(new Template(i,reduced));half.recycle();restored.recycle();
            for(int size:new int[]{50,75})try(InputStream in=context.getAssets().open("alien/recognition-numerals/"+ids[i]+"-"+size+".png")){
                Bitmap variant=BitmapFactory.decodeStream(in);if(variant==null)throw new IOException("数字模板加载失败");byte[] reducedVariant=alpha(variant);numbers.add(new NumberModel(i,split,reducedVariant));templates.add(new Template(i,reducedVariant));variant.recycle();
            }
        }
    }
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
    private static float[][] numberFeatures(byte[] image,int split){float[] bars=blur(image,100,100,.3f);Arrays.fill(bars,split*100,bars.length,0);unit(bars);float[] base=vector(crop(image,100,100,0,split,100,100-split),100,100-split,48,48,.6f);return new float[][]{bars,base};}
    private static int[] top(float[] scores,int count){int[] ids=new int[count];Arrays.fill(ids,-1);for(int i=0;i<scores.length;i++){int j=count-1;if(ids[j]>=0&&scores[i]<=scores[ids[j]])continue;while(j>0&&(ids[j-1]<0||scores[i]>scores[ids[j-1]])){ids[j]=ids[j-1];j--;}ids[j]=i;}return ids;}
    private static float dot(float[] a,float[] b){float s=0;for(int i=0;i<a.length;i++)s+=a[i]*b[i];return s;}
    private static void unit(float[] a){double sum=0;for(float v:a)sum+=v*v;float scale=(float)(1/Math.max(1e-8,Math.sqrt(sum)));for(int i=0;i<a.length;i++)a[i]*=scale;}
    private static float[] vector(byte[] image,int w,int h,int ow,int oh,float sigma){return resizeNormalized(blur(image,w,h,sigma),w,h,ow,oh);}
    private static float[] blur(byte[] image,int w,int h,float sigma){int r=sigma>.4f?2:1;float[] k=new float[r*2+1];float sum=0;for(int i=-r;i<=r;i++){k[i+r]=(float)Math.exp(-i*i/(2*sigma*sigma));sum+=k[i+r];}for(int i=0;i<k.length;i++)k[i]/=sum;float[] tmp=new float[w*h],out=new float[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)for(int i=-r;i<=r;i++)tmp[y*w+x]+=(image[y*w+Math.max(0,Math.min(w-1,x+i))]&255)*k[i+r];for(int y=0;y<h;y++)for(int x=0;x<w;x++)for(int i=-r;i<=r;i++)out[y*w+x]+=tmp[Math.max(0,Math.min(h-1,y+i))*w+x]*k[i+r];return out;}
    private static float[] resizeNormalized(float[] a,int w,int h,int ow,int oh){float[] out=new float[ow*oh];for(int y=0;y<oh;y++){float py=Math.max(0,Math.min(h-1,(y+.5f)*h/oh-.5f));int y0=(int)py,y1=Math.min(h-1,y0+1);float fy=py-y0;for(int x=0;x<ow;x++){float px=Math.max(0,Math.min(w-1,(x+.5f)*w/ow-.5f));int x0=(int)px,x1=Math.min(w-1,x0+1);float fx=px-x0;out[y*ow+x]=(a[y0*w+x0]*(1-fx)+a[y0*w+x1]*fx)*(1-fy)+(a[y1*w+x0]*(1-fx)+a[y1*w+x1]*fx)*fy;}}unit(out);return out;}
    private static byte[] crop(byte[] a,int w,int h,int x,int y,int ow,int oh){byte[] out=new byte[ow*oh];for(int yy=0;yy<oh;yy++){int sy=y+yy;if(sy<0||sy>=h)continue;int left=Math.max(0,x),right=Math.min(w,x+ow);if(right>left)System.arraycopy(a,sy*w+left,out,yy*ow+left-x,right-left);}return out;}
    private static void paste(byte[] out,int w,int h,byte[] a,int aw,int ah,int x,int y){for(int yy=0;yy<ah;yy++)if(y+yy>=0&&y+yy<h)System.arraycopy(a,yy*aw,out,(y+yy)*w+x,aw);}
}
