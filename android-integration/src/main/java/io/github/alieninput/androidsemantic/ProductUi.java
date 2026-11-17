package io.github.alieninput.androidsemantic;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.*;
import android.widget.*;

/** Shared surfaces and typography for the keyboard panel and image reader. */
final class ProductUi {
    static final int BG=0xfff5f2ea, PAPER=0xfffffdf8, INK=0xff292a25, MUTED=0xff76786e;
    static final int LINE=0xffe2dfd4, ACCENT=0xffead59d, SOFT=0xffece8dc;
    final Context context;
    ProductUi(Context context){this.context=context;}
    int dp(int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
    LinearLayout column(){LinearLayout v=new LinearLayout(context);v.setOrientation(LinearLayout.VERTICAL);return v;}
    LinearLayout row(){LinearLayout v=new LinearLayout(context);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    TextView text(String value,int size,int color){TextView v=new TextView(context);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(false);v.setLineSpacing(dp(3),1);return v;}
    TextView title(String value,int size){TextView v=text(value,size,INK);v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    Button button(String label,boolean primary,Runnable action){Button v=new Button(context);v.setText(label);v.setTextSize(15);v.setAllCaps(false);v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xffaaa99f,primary?PAPER:INK}));v.setMinHeight(0);v.setMinimumHeight(0);v.setMinWidth(0);v.setMinimumWidth(0);v.setPadding(dp(14),0,dp(14),0);v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x20918a6a),shape(primary?INK:SOFT,14),null));v.setOnClickListener(w->action.run());return v;}
    LinearLayout card(){LinearLayout v=column();v.setPadding(dp(18),dp(18),dp(18),dp(18));v.setBackground(shape(PAPER,20));return v;}
    void gap(LinearLayout parent,int size){parent.addView(new View(context),new LinearLayout.LayoutParams(1,dp(size)));}
    void block(LinearLayout parent,View child,int height){parent.addView(child,new LinearLayout.LayoutParams(-1,height<0?height:dp(height)));}
    void space(View view,int bottom){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(bottom);view.setLayoutParams(lp);}
    LinearLayout screen(Activity activity){activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);activity.getWindow().setStatusBarColor(BG);activity.getWindow().setNavigationBarColor(BG);LinearLayout root=column();root.setBackgroundColor(BG);root.setPadding(dp(20),dp(12),dp(20),dp(16));root.setOnApplyWindowInsetsListener((v,i)->{root.setPadding(dp(20)+i.getSystemWindowInsetLeft(),dp(12)+i.getSystemWindowInsetTop(),dp(20)+i.getSystemWindowInsetRight(),dp(16)+i.getSystemWindowInsetBottom());return i;});return root;}
    LinearLayout header(Activity activity,String label){LinearLayout row=row();Button back=button("返回",false,activity::finish);row.addView(back,new LinearLayout.LayoutParams(dp(60),dp(42)));TextView title=title(label,19);title.setGravity(Gravity.CENTER);row.addView(title,new LinearLayout.LayoutParams(0,dp(42),1));row.addView(new View(context),new LinearLayout.LayoutParams(dp(60),1));return row;}
}
