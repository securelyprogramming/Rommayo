package com.rommayo.app;

import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Native menu backdrop. Appearance dimming never touches the emulator canvas/video. */
final class NativeAppearance extends Drawable {
 static final int MIN_DIM=40,MAX_DIM=95,DEFAULT_DIM=82;
 static boolean mode(String value){return "off".equals(value)||"brand".equals(value)||"game".equals(value);}
 static String readMode(SharedPreferences prefs){try{String value=prefs.getString("appearanceMode","game");return mode(value)?value:"game";}catch(ClassCastException ignored){return "game";}}
 static int readDim(SharedPreferences prefs){try{return Math.max(MIN_DIM,Math.min(MAX_DIM,prefs.getInt("appearanceDim",DEFAULT_DIM)));}catch(ClassCastException ignored){return DEFAULT_DIM;}}
 static boolean save(SharedPreferences prefs,String mode,int dim){return mode(mode)&&dim>=MIN_DIM&&dim<=MAX_DIM&&prefs.edit().putString("appearanceMode",mode).putInt("appearanceDim",dim).commit();}
 final String mode,brand;final int dim;final Bitmap artwork;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
 NativeAppearance(String mode,int dim,String brand,Bitmap artwork){this.mode=mode;this.dim=dim;this.brand=brand;this.artwork=artwork;}
 @Override public void draw(Canvas canvas){Rect b=getBounds();boolean blue="rommayo".equals(brand);int base=blue?0xff081522:0xff07160f;canvas.drawColor(base);if("off".equals(mode))return;
  if("game".equals(mode)&&artwork!=null&&!artwork.isRecycled()){float scale=Math.max(b.width()/(float)artwork.getWidth(),b.height()/(float)artwork.getHeight());float width=artwork.getWidth()*scale,height=artwork.getHeight()*scale;paint.setColor(Color.WHITE);paint.setAlpha(255);canvas.drawBitmap(artwork,null,new RectF(b.centerX()-width/2,b.centerY()-height/2,b.centerX()+width/2,b.centerY()+height/2),paint);canvas.drawColor(Color.argb(Math.round(dim*2.55f),blue?8:7,blue?21:22,blue?34:15));return;}
  paint.setShader(new LinearGradient(b.left,b.top,b.right,b.bottom,new int[]{base,blue?0xff163553:0xff153c29,base},new float[]{0,.65f,1},Shader.TileMode.CLAMP));paint.setAlpha(Math.round((100-dim)*2.55f));canvas.drawRect(b,paint);paint.setShader(null);
  int saved=canvas.save();float scale=Math.min(b.width()/1000f,b.height()/600f);canvas.translate(b.centerX()+b.width()*.13f,b.centerY());canvas.scale(scale,scale);paint.setColor(blue?0xff8cc8ff:0xff8cecb0);paint.setAlpha(Math.round((100-dim)*1.9f));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(4);paint.setStrokeCap(Paint.Cap.ROUND);
  if(blue){canvas.rotate(-12);canvas.drawRoundRect(-220,-205,220,205,24,24,paint);canvas.drawRect(-160,-140,160,35,paint);for(int x=-130;x<=130;x+=26)canvas.drawLine(x,135,x,192,paint);canvas.drawLine(-110,80,110,80,paint);canvas.drawLine(-80,103,80,103,paint);}
  else{canvas.rotate(9);canvas.drawRoundRect(-265,-160,265,160,65,65,paint);canvas.drawRoundRect(-135,-112,135,112,14,14,paint);canvas.drawLine(-225,0,-155,0,paint);canvas.drawLine(-190,-35,-190,35,paint);canvas.drawCircle(192,-25,16,paint);canvas.drawCircle(225,20,16,paint);canvas.drawCircle(-255,-230,9,paint);canvas.drawLine(-255,-230,-55,-230,paint);canvas.drawLine(-55,-230,20,-190,paint);canvas.drawLine(265,200,65,200,paint);canvas.drawCircle(265,200,9,paint);}
  paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);canvas.restoreToCount(saved);
 }

 static android.app.AlertDialog settings(android.app.Activity activity,SharedPreferences prefs,String brand,java.util.function.Supplier<Bitmap> current,Runnable changed){
  float density=activity.getResources().getDisplayMetrics().density;
  android.widget.LinearLayout box=new android.widget.LinearLayout(activity);box.setOrientation(android.widget.LinearLayout.VERTICAL);box.setPadding((int)(18*density),(int)(10*density),(int)(18*density),(int)(10*density));
  android.widget.TextView note=new android.widget.TextView(activity);note.setText("Backgrounds on this device. Game art uses the highlighted game when available, with an original app background as the fallback.");note.setTextColor(0xffc5e8d2);box.addView(note);
  android.widget.RadioGroup modes=new android.widget.RadioGroup(activity);modes.setOrientation(android.widget.LinearLayout.HORIZONTAL);String[] values={"off","brand","game"},labels={"Off","Brand","Game art"};
  for(int i=0;i<values.length;i++){android.widget.RadioButton choice=new android.widget.RadioButton(activity);choice.setId(android.view.View.generateViewId());choice.setTag(values[i]);choice.setText(labels[i]);choice.setTextColor(0xffeffff4);modes.addView(choice,new android.widget.RadioGroup.LayoutParams(0,(int)(48*density),1));if(readMode(prefs).equals(values[i]))choice.setChecked(true);}box.addView(modes);
  android.widget.TextView label=new android.widget.TextView(activity);label.setTextColor(0xffc5e8d2);label.setText("Dim background · "+readDim(prefs)+"%");box.addView(label);
  android.widget.SeekBar dim=new android.widget.SeekBar(activity);dim.setMax(MAX_DIM-MIN_DIM);dim.setProgress(readDim(prefs)-MIN_DIM);dim.setContentDescription("Background dim percentage");box.addView(dim,new android.widget.LinearLayout.LayoutParams(-1,(int)(48*density)));
  android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(activity).setTitle("Appearance · this device").setView(box).setNegativeButton("Done",null).create();
  Runnable apply=()->{changed.run();if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(new NativeAppearance(readMode(prefs),readDim(prefs),brand,current.get()));};
  modes.setOnCheckedChangeListener((group,id)->{android.view.View chosen=group.findViewById(id);if(chosen!=null){save(prefs,(String)chosen.getTag(),readDim(prefs));apply.run();}});
  dim.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(android.widget.SeekBar bar){}public void onStopTrackingTouch(android.widget.SeekBar bar){}public void onProgressChanged(android.widget.SeekBar bar,int value,boolean user){save(prefs,readMode(prefs),MIN_DIM+value);label.setText("Dim background · "+(MIN_DIM+value)+"%");apply.run();}});
  return dialog;
 }
 @Override public void setAlpha(int alpha){}@Override public void setColorFilter(ColorFilter filter){}@Override public int getOpacity(){return PixelFormat.OPAQUE;}
}
