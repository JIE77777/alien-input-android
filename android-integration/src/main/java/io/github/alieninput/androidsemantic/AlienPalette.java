package io.github.alieninput.androidsemantic;

import android.content.Context;

/** Image colors are independent of the IME theme. Values sampled from game color functions. */
public final class AlienPalette {
    public static final String PREFERENCES="alien-image-settings", KEY="palette";
    public static final String[] IDS={"white","black","red","red2","orange","yellow","yellowgreen","green","infrablue"};
    public static final String[] LABELS={"游戏默认 · 米黄字 / 黑底","黑字 / 米黄底","红字 / 米黄底","黑字 / 米黄底（red2）","杏色字 / 棕底","黄字 / 深底","黄绿字 / 深绿底","绿字 / 深绿底","蓝紫字 / 深蓝底"};
    private static final int[] FOREGROUNDS={0xffffe6b3,0xff000000,0xffd90000,0xff000000,0xffffb366,0xffffd900,0xff66cc00,0xff33b333,0xff4d4d99};
    private static final int[] BACKGROUNDS={0xff000000,0xffffe6b3,0xffffe6b3,0xffffe6b3,0xff4d3300,0xff262600,0xff1a3300,0xff0d260d,0xff1a1a33};
    public final int foreground,background;
    public final String id;
    private AlienPalette(int index){id=IDS[index];foreground=FOREGROUNDS[index];background=BACKGROUNDS[index];}
    public static AlienPalette forId(String id){for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return new AlienPalette(i);return new AlienPalette(0);}
    public static AlienPalette load(Context context){return forId(context.getSharedPreferences(PREFERENCES,0).getString(KEY,"white"));}
}
