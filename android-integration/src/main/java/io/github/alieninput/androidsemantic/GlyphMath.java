package io.github.alieninput.androidsemantic;
import java.util.*;

/** Pixel features shared by the build-time compiler and on-device matching. */
final class GlyphMath {
    static float[][] numberFeatures(byte[] image,int split){float[] bars=Arrays.copyOf(blur(image,100,100,.3f),split*100);unit(bars);float[] base=vector(crop(image,100,100,0,split,100,100-split),100,100-split,48,48,.6f);return new float[][]{bars,base};}
    static int[] top(float[] scores,int count){int[] ids=new int[count];Arrays.fill(ids,-1);for(int i=0;i<scores.length;i++){int j=count-1;if(ids[j]>=0&&scores[i]<=scores[ids[j]])continue;while(j>0&&(ids[j-1]<0||scores[i]>scores[ids[j-1]])){ids[j]=ids[j-1];j--;}ids[j]=i;}return ids;}
    static float dot(float[] a,float[] b){float s=0;for(int i=0;i<a.length;i++)s+=a[i]*b[i];return s;}
    static void unit(float[] a){double sum=0;for(float v:a)sum+=v*v;float scale=(float)(1/Math.max(1e-8,Math.sqrt(sum)));for(int i=0;i<a.length;i++)a[i]*=scale;}
    static float[] vector(byte[] image,int w,int h,int ow,int oh,float sigma){return resizeNormalized(blur(image,w,h,sigma),w,h,ow,oh);}
    static float[] blur(byte[] image,int w,int h,float sigma){int r=sigma>.4f?2:1;float[] k=new float[r*2+1];float sum=0;for(int i=-r;i<=r;i++){k[i+r]=(float)Math.exp(-i*i/(2*sigma*sigma));sum+=k[i+r];}for(int i=0;i<k.length;i++)k[i]/=sum;float[] tmp=new float[w*h],out=new float[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)for(int i=-r;i<=r;i++)tmp[y*w+x]+=(image[y*w+Math.max(0,Math.min(w-1,x+i))]&255)*k[i+r];for(int y=0;y<h;y++)for(int x=0;x<w;x++)for(int i=-r;i<=r;i++)out[y*w+x]+=tmp[Math.max(0,Math.min(h-1,y+i))*w+x]*k[i+r];return out;}
    static float[] resizeNormalized(float[] a,int w,int h,int ow,int oh){float[] out=new float[ow*oh];for(int y=0;y<oh;y++){float py=Math.max(0,Math.min(h-1,(y+.5f)*h/oh-.5f));int y0=(int)py,y1=Math.min(h-1,y0+1);float fy=py-y0;for(int x=0;x<ow;x++){float px=Math.max(0,Math.min(w-1,(x+.5f)*w/ow-.5f));int x0=(int)px,x1=Math.min(w-1,x0+1);float fx=px-x0;out[y*ow+x]=(a[y0*w+x0]*(1-fx)+a[y0*w+x1]*fx)*(1-fy)+(a[y1*w+x0]*(1-fx)+a[y1*w+x1]*fx)*fy;}}unit(out);return out;}
    static byte[] crop(byte[] a,int w,int h,int x,int y,int ow,int oh){byte[] out=new byte[ow*oh];for(int yy=0;yy<oh;yy++){int sy=y+yy;if(sy<0||sy>=h)continue;int left=Math.max(0,x),right=Math.min(w,x+ow);if(right>left)System.arraycopy(a,sy*w+left,out,yy*ow+left-x,right-left);}return out;}
    static void paste(byte[] out,int w,int h,byte[] a,int aw,int ah,int x,int y){for(int yy=0;yy<ah;yy++)if(y+yy>=0&&y+yy<h)System.arraycopy(a,yy*aw,out,(y+yy)*w+x,aw);}
    static byte[] scaleMask(byte[] source,int w,int h,int ow,int oh){
        byte[] out=new byte[ow*oh];for(int y=0;y<oh;y++){float py=Math.max(0,Math.min(h-1,(y+.5f)*h/oh-.5f));int y0=(int)py,y1=Math.min(h-1,y0+1);float fy=py-y0;for(int x=0;x<ow;x++){float px=Math.max(0,Math.min(w-1,(x+.5f)*w/ow-.5f));int x0=(int)px,x1=Math.min(w-1,x0+1);float fx=px-x0;float v=((source[y0*w+x0]&255)*(1-fx)+(source[y0*w+x1]&255)*fx)*(1-fy)+((source[y1*w+x0]&255)*(1-fx)+(source[y1*w+x1]&255)*fx)*fy;out[y*ow+x]=(byte)Math.round(v);}}return out;
    }
}
