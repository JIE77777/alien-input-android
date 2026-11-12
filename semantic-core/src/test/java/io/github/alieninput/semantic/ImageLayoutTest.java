package io.github.alieninput.semantic;

import java.util.*;

public final class ImageLayoutTest {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        for(int n=1;n<=120;n++)verify(Collections.nCopies(n,1));
        for(int n=1;n<=40;n++)verify(Collections.nCopies(n,3));
        // Actual cases: a two-glyph word must move intact; three-glyph words cannot share a row.
        verify(Arrays.asList(1,1,1,2));verify(Arrays.asList(2,2,2));verify(Arrays.asList(3,3));
        verify(Arrays.asList(1,3,2,1));verify(Arrays.asList(7,2));verify(Arrays.asList(120));
        ImageLayout five=new ImageLayout(5),six=new ImageLayout(6);
        check(five.y(2)==five.y(0)&&five.y(3)>five.y(2),"five singles balance as 3+2");
        check(six.y(2)==six.y(0)&&six.y(3)>six.y(2),"six singles balance as 3+3");
        ImageLayout words=new ImageLayout(Arrays.asList(1,1,1,2));
        check(words.y(3)==words.y(4)&&words.y(3)>words.y(2),"two-glyph word intact");
        check(new ImageLayout(4).height==208&&new ImageLayout(4).width==480,"tested WeChat ratio");
        check(new ImageLayout(1).x(0)==190&&new ImageLayout(1).y(0)==54,"single glyph centered both ways");
        check(new ImageLayout(8).height==256,"two rows compact");
        check(new ImageLayout(Collections.nCopies(40,3)).height==4512,"maximum phrase-heavy draft bounded");
        Random random=new Random(20261112);
        for(int t=0;t<250;t++){
            List<Integer> groups=new ArrayList<>();int n=2+random.nextInt(9);
            for(int i=0;i<n;i++)groups.add(1+random.nextInt(3));
            ImageLayout layout=verify(groups);int[] optimal=brute(groups,0);
            check(layout.rows==optimal[0]&&cost(layout)==optimal[1],"optimal rows and balance: "+groups);
        }
        for(int bad:new int[]{0,-1,121,Integer.MAX_VALUE}){
            try{new ImageLayout(bad);throw new AssertionError("accepted bad count");}catch(IllegalArgumentException expected){checks++;}
        }
        for(List<Integer> bad:Arrays.asList(Collections.<Integer>emptyList(),Arrays.asList(0),Arrays.asList(1,-1),Arrays.asList(120,1))){
            try{new ImageLayout(bad);throw new AssertionError("accepted bad group");}catch(IllegalArgumentException expected){checks++;}
        }
        try{five.x(-1);throw new AssertionError("bad index");}catch(IndexOutOfBoundsException expected){checks++;}
        try{five.y(5);throw new AssertionError("bad index");}catch(IndexOutOfBoundsException expected){checks++;}
        System.out.println("PASS "+checks+" four-slot grouped layout checks");
    }
    private static ImageLayout verify(List<Integer> groups){
        ImageLayout a=new ImageLayout(groups);int count=0;
        for(int size:groups){
            if(size<=4)check(a.y(count)==a.y(count+size-1),"word split: "+groups);
            count+=size;
        }
        check(a.count==count&&a.width==480&&a.height>=208,"canvas/count");
        check(a.y(0)==a.height-a.y(count-1)-100,"vertical centering");
        int start=0,rows=0;
        for(int i=0;i<count;i++){
            check(a.x(i)>=16&&a.x(i)+100<=a.width-16&&a.y(i)>=16&&a.y(i)+100<=a.height-16,"clipped glyph");
            if(i>0)check(a.y(i)>=a.y(i-1),"reading order");
            if(i==count-1||a.y(i+1)!=a.y(i)){
                check(i-start+1<=4,"row capacity");
                check(a.x(start)==a.width-a.x(i)-100,"horizontal centering");
                for(int j=start+1;j<=i;j++)check(a.x(j)-a.x(j-1)==112,"consistent spacing");
                rows++;start=i+1;
            }
        }
        check(rows==a.rows,"row count");return a;
    }
    private static int cost(ImageLayout a){
        int result=0,start=0;
        for(int i=0;i<a.count;i++)if(i==a.count-1||a.y(i+1)!=a.y(i)){
            int spare=4-(i-start+1);result+=spare*spare;start=i+1;
        }
        return result;
    }
    // Independent exhaustive partitions of small cases establish the best row count/cost.
    private static int[] brute(List<Integer> groups,int start){
        if(start==groups.size())return new int[]{0,0};
        int[] best={Integer.MAX_VALUE,Integer.MAX_VALUE};int used=0;
        for(int end=start;end<groups.size();end++){
            used+=groups.get(end);if(used>4)break;
            int[] tail=brute(groups,end+1);int lines=1+tail[0],cost=(4-used)*(4-used)+tail[1];
            if(lines<best[0]||lines==best[0]&&cost<best[1])best=new int[]{lines,cost};
        }
        return best;
    }
}
