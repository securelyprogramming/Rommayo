package com.rommayo.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/** Run with am instrument on an isolated debug install; no ROM or account needed. */
public final class ControllerMenuSmoke extends Instrumentation {
    private Activity activity;
    private LinearLayout fixture;
    private GridView grid;
    private Button left, right;
    private Object library;
    private ViewGroup appRoot;
    private View first, moved;
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private void ui(Runnable action) {
        java.util.concurrent.atomic.AtomicReference<Throwable> error=new java.util.concurrent.atomic.AtomicReference<>();
        runOnMainSync(()->{try{action.run();}catch(Throwable failure){error.set(failure);}});
        if(error.get()!=null)throw new RuntimeException(error.get());
    }
    private void settle(View view) {
        for(int attempt=0;attempt<100;attempt++) {
            java.util.concurrent.atomic.AtomicBoolean ready=new java.util.concurrent.atomic.AtomicBoolean();
            ui(()->ready.set(view.isLaidOut()&&!view.isLayoutRequested()&&view.getWidth()>0&&activity.hasWindowFocus()));
            if(ready.get())return;
            SystemClock.sleep(20);
        }
        throw new AssertionError("Window did not lay out; wake and unlock the test device first");
    }
    private Object field(Object object, String name) {
        try { java.lang.reflect.Field f=object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
        catch (Exception error) { throw new RuntimeException(error); }
    }
    private void method(Object object, String name) {
        try { java.lang.reflect.Method m=object.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(object); }
        catch (Exception error) { throw new RuntimeException(error); }
    }
    private Button button(String name) { Button b=new Button(activity); b.setText(name); return b; }
    private void key(int code) {
        ui(()->{activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code));});
        waitForIdleSync();
    }
    private void axis(float x, float y, float hatX, float hatY) {
        ui(()->{
            MotionEvent.PointerProperties p=new MotionEvent.PointerProperties();p.id=0;
            MotionEvent.PointerCoords c=new MotionEvent.PointerCoords();c.setAxisValue(MotionEvent.AXIS_X,x);c.setAxisValue(MotionEvent.AXIS_Y,y);c.setAxisValue(MotionEvent.AXIS_HAT_X,hatX);c.setAxisValue(MotionEvent.AXIS_HAT_Y,hatY);
            long now=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(now,now,MotionEvent.ACTION_MOVE,1,new MotionEvent.PointerProperties[]{p},new MotionEvent.PointerCoords[]{c},0,0,1,1,0,0,InputDevice.SOURCE_JOYSTICK,0);
            check(activity.dispatchGenericMotionEvent(e),"Menu joystick event was not handled");e.recycle();
        });waitForIdleSync();
    }
    private View named(View view,String text) {
        if(view instanceof TextView && ((TextView)view).getText().toString().equals(text))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=named(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;}
        return null;
    }
    @Override public void onStart() {
        Bundle result=new Bundle();int code=0;
        try {
            activity=startActivitySync(new Intent().setClassName(getTargetContext(),"com.rommayo.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            library=field(activity,"nativeLibrary");appRoot=(ViewGroup)field(activity,"root");
            ui(()->{
                fixture=new LinearLayout(activity);fixture.setOrientation(LinearLayout.VERTICAL);
                LinearLayout row=new LinearLayout(activity);left=button("Left action");right=button("Right action");
                row.addView(left,new LinearLayout.LayoutParams(0,100,1));row.addView(right,new LinearLayout.LayoutParams(0,100,1));fixture.addView(row);
                grid=new GridView(activity);grid.setFocusable(true);grid.setFocusableInTouchMode(true);grid.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);grid.setNumColumns(3);grid.setAdapter(new BaseAdapter(){public int getCount(){return 12;}public Object getItem(int p){return p;}public long getItemId(int p){return p;}public View getView(int p,View old,ViewGroup parent){TextView t=new TextView(activity);t.setText("Test card "+p);t.setHeight(90);return t;}});
                fixture.addView(grid,new LinearLayout.LayoutParams(-1,400));activity.setContentView(fixture);left.requestFocusFromTouch();
            });waitForIdleSync();settle(fixture);
            ui(()->{check(left.requestFocusFromTouch(),"Initial button focus failed");ControllerFocus.move(fixture,fixture,grid,KeyEvent.KEYCODE_DPAD_RIGHT);check(right.hasFocus(),"Spatial movement did not reach right action; left="+left.hasFocus()+", focus="+fixture.findFocus()+", width="+left.getWidth());ControllerFocus.select(grid,0,true);});waitForIdleSync();settle(grid);waitForIdleSync();settle(fixture);
            ui(()->{check(grid.hasFocus(),"Initial grid highlight was lost after layout");ControllerFocus.move(fixture,fixture,grid,KeyEvent.KEYCODE_DPAD_RIGHT);check(grid.getSelectedItemPosition()==1,"Grid right did not select next card; selected="+grid.getSelectedItemPosition()+", children="+grid.getChildCount());ControllerFocus.move(fixture,fixture,grid,KeyEvent.KEYCODE_DPAD_DOWN);check(grid.getSelectedItemPosition()==4,"Grid down did not select next row");});
            // Force the same Android touch-mode transition as a screen tap.
            setInTouchMode(true);waitForIdleSync();SystemClock.sleep(50);
            ui(()->{fixture.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);fixture.setFocusableInTouchMode(true);fixture.requestFocus();fixture.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);check(!grid.hasFocus(),"Focus-loss precondition failed");ControllerFocus.ensure(fixture,fixture,grid);check(grid.hasFocus()&&!fixture.isInTouchMode(),"Controller failed to recover focus");activity.setContentView(appRoot);method(library,"settings");});waitForIdleSync();settle(appRoot);
            ui(()->{ControllerFocus.ensure(appRoot,(ViewGroup)field(library,"body"),null);first=appRoot.findFocus();check(first instanceof Switch,"Settings did not focus its first control");});
            key(KeyEvent.KEYCODE_DPAD_DOWN);ui(()->{moved=appRoot.findFocus();check(moved!=first&&moved instanceof Switch,"D-pad did not move settings focus");});
            axis(0,1,0,0);axis(0,0,0,0);ui(()->check(appRoot.findFocus()!=moved,"Left stick did not move settings focus"));
            axis(0,0,0,1);axis(0,0,0,0);ui(()->{moved=appRoot.findFocus();check(moved!=null,"HAT D-pad lost focus");});
            axis(0,1,0,0);SystemClock.sleep(620);axis(0,0,0,0);ui(()->{first=appRoot.findFocus();check(first!=moved,"Held stick did not repeat");});SystemClock.sleep(400);ui(()->check(first==appRoot.findFocus(),"Stick kept repeating after release"));
            ui(()->{View diagnostics=named(appRoot,"Controller check");check(diagnostics!=null,"Missing controller check action");diagnostics.requestFocusFromTouch();});
            key(KeyEvent.KEYCODE_BUTTON_A);ui(()->check("controller".equals(field(library,"screen")),"A did not open the focused action"));
            key(KeyEvent.KEYCODE_BUTTON_B);ui(()->check("settings".equals(field(library,"screen")),"B did not return from controller check"));
            result.putString("stream","PASS: spatial focus; grid columns/rows; focus recovery; actual settings D-pad, left stick, HAT, hold/release; A opens and B returns. Inputs were synthesized.\n");code=-1;
        } catch(Throwable error) { result.putString("stream","FAIL: "+error+"\n"); }
        finally { if(activity!=null)ui(()->activity.finish()); }
        finish(code,result);
    }
}
