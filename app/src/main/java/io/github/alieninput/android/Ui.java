package io.github.alieninput.android;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
final class Ui {
    static final int BG=0xffeef2ec, INK=0xff1d352d, GREEN=0xff176b59, MUTED=0xff607267;
    static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static LinearLayout column(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(1);return v;}
    static LinearLayout row(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(0);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    static TextView text(Context c,String s,int size,int color){TextView v=new TextView(c);v.setText(s);v.setTextSize(size);v.setTextColor(color);return v;}
    static GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    static Button button(Context c,String label,Runnable click){
        Button b=new Button(c);b.setText(label);b.setTextSize(13);b.setTextColor(INK);b.setAllCaps(false);
        b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);
        b.setPadding(2,0,2,0);b.setBackground(bg(Color.WHITE,dp(c,9)));
        b.setOnClickListener(v->click.run());return b;
    }
    static void weight(LinearLayout row,View v,int height){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(row.getContext(),height),1);p.setMargins(2,2,2,2);row.addView(v,p);}
    static ImageView glyph(Context c,String id,int size){ImageView v=new ImageView(c);v.setImageBitmap(Assets.bitmap(c,id));v.setScaleType(ImageView.ScaleType.FIT_CENTER);v.setBackgroundColor(Color.WHITE);v.setLayoutParams(new LinearLayout.LayoutParams(dp(c,size),dp(c,size)));return v;}
}
