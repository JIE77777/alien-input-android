package io.github.alieninput.semantic;

import java.util.*;

/** Four fixed-width slots; keep words together and balance rows without changing reading order. */
public final class ImageLayout {
    public static final int COLUMNS=4, CELL=112, MARGIN=16, GLYPH=100, MIN_HEIGHT=208;
    public final int count,rows,width,height;
    private final int[] xs,ys;

    public ImageLayout(int count){this(singletons(count));}

    public static ImageLayout forGroups(List<? extends List<?>> groups){
        Objects.requireNonNull(groups,"groups");
        List<Integer> sizes=new ArrayList<>();
        for(List<?> group:groups)sizes.add(Objects.requireNonNull(group,"group").size());
        return new ImageLayout(sizes);
    }

    private static List<Integer> singletons(int count){
        if(count<1||count>Draft.MAX_GLYPHS)throw new IllegalArgumentException("请选择 1–120 个字形");
        return Collections.nCopies(count,1);
    }

    /** A future word longer than a whole row is split only because it cannot fit intact. */
    public ImageLayout(List<Integer> groupSizes){
        Objects.requireNonNull(groupSizes,"groupSizes");
        List<Integer> units=new ArrayList<>();
        int total=0;
        for(Integer size:groupSizes){
            if(size==null||size<1||size>Draft.MAX_GLYPHS-total)
                throw new IllegalArgumentException("请选择 1–120 个字形，词组不能为空");
            total+=size;
            int remaining=size;
            while(remaining>COLUMNS){units.add(COLUMNS);remaining-=COLUMNS;}
            units.add(remaining);
        }
        if(total==0)throw new IllegalArgumentException("请选择 1–120 个字形");
        count=total;
        int n=units.size();
        int[] lines=new int[n+1],cost=new int[n+1],next=new int[n];
        // First minimize row count, then squared spare slots. Ties favor a fuller earlier row.
        for(int i=n-1;i>=0;i--){
            lines[i]=Integer.MAX_VALUE;cost[i]=Integer.MAX_VALUE;
            int used=0;
            for(int j=i;j<n&&(used+=units.get(j))<=COLUMNS;j++){
                int candidateLines=1+lines[j+1];
                int candidateCost=(COLUMNS-used)*(COLUMNS-used)+cost[j+1];
                if(candidateLines<lines[i]||(candidateLines==lines[i]&&candidateCost<=cost[i])){
                    lines[i]=candidateLines;cost[i]=candidateCost;next[i]=j+1;
                }
            }
        }
        rows=lines[0];width=COLUMNS*CELL+2*MARGIN;height=Math.max(MIN_HEIGHT,rows*CELL+2*MARGIN);
        xs=new int[count];ys=new int[count];
        int glyph=0,row=0;
        for(int start=0;start<n;row++){
            int end=next[start],length=0;
            for(int j=start;j<end;j++)length+=units.get(j);
            int left=(width-length*CELL)/2+(CELL-GLYPH)/2;
            int top=(height-rows*CELL)/2+(CELL-GLYPH)/2+row*CELL;
            for(int column=0;column<length;column++){xs[glyph]=left+column*CELL;ys[glyph++]=top;}
            start=end;
        }
    }
    public int x(int index){check(index);return xs[index];}
    public int y(int index){check(index);return ys[index];}
    private void check(int index){if(index<0||index>=count)throw new IndexOutOfBoundsException();}
}
