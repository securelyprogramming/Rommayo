package com.rommayo.app;

import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

/** Menu-only controller translation. Never attached to the emulation WebView. */
final class ControllerNavigation {
    interface Target { void direction(int key); void confirm(); void back(); void section(int delta); void search(); void recent(); }
    private final Target target;
    boolean swap, sticks = true;
    float deadZone = .30f;
    private int held, keyed, axisDirection;
    private final boolean[] keys = new boolean[4];
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable repeat = new Runnable() {
        @Override public void run() { if (held != 0) { target.direction(held); if (held != 0) handler.postDelayed(this, 130); } }
    };
    void stop() { held = keyed = axisDirection = 0; java.util.Arrays.fill(keys, false); handler.removeCallbacks(repeat); }
    private void updateDirection() {
        int direction = keyed != 0 ? keyed : axisDirection;
        if (held == direction) return;
        handler.removeCallbacks(repeat); held = direction;
        if (held != 0) { target.direction(held); if (held != 0) handler.postDelayed(repeat, 350); }
    }
    ControllerNavigation(Target target) { this.target = target; }
    boolean key(KeyEvent event) {
        int key = event.getKeyCode();
        if (key >= KeyEvent.KEYCODE_DPAD_UP && key <= KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) { keys[key - KeyEvent.KEYCODE_DPAD_UP] = true; keyed = key; }
            else if (event.getAction() == KeyEvent.ACTION_UP) {
                keys[key - KeyEvent.KEYCODE_DPAD_UP] = false; keyed = 0;
                for (int i = 0; i < keys.length; i++) if (keys[i]) { keyed = KeyEvent.KEYCODE_DPAD_UP + i; break; }
            }
            updateDirection(); return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_CENTER) { if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) target.confirm(); return true; }
        boolean mapped = key == KeyEvent.KEYCODE_BUTTON_A || key == KeyEvent.KEYCODE_BUTTON_B || key == KeyEvent.KEYCODE_BUTTON_X || key == KeyEvent.KEYCODE_BUTTON_Y || key == KeyEvent.KEYCODE_BUTTON_L1 || key == KeyEvent.KEYCODE_BUTTON_R1;
        if (!mapped) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0) return true;
        if (key == (swap ? KeyEvent.KEYCODE_BUTTON_B : KeyEvent.KEYCODE_BUTTON_A)) target.confirm();
        else if (key == (swap ? KeyEvent.KEYCODE_BUTTON_A : KeyEvent.KEYCODE_BUTTON_B)) target.back();
        else if (key == KeyEvent.KEYCODE_BUTTON_L1) target.section(-1);
        else if (key == KeyEvent.KEYCODE_BUTTON_R1) target.section(1);
        else if (key == KeyEvent.KEYCODE_BUTTON_X) target.search();
        else if (key == KeyEvent.KEYCODE_BUTTON_Y) target.recent();
        return true;
    }
    boolean motion(MotionEvent event) {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.getAction() != MotionEvent.ACTION_MOVE) return false;
        float x = event.getAxisValue(MotionEvent.AXIS_HAT_X), y = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if (sticks && Math.abs(x) < .5f && Math.abs(y) < .5f) {
            x = centered(event, MotionEvent.AXIS_X); y = centered(event, MotionEvent.AXIS_Y);
        }
        int direction = 0;
        if (Math.max(Math.abs(x), Math.abs(y)) >= deadZone) direction = Math.abs(x) > Math.abs(y) ? (x > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT) : (y > 0 ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_UP);
        axisDirection = direction; updateDirection();
        return true;
    }
    private float centered(MotionEvent event, int axis) {
        InputDevice device = event.getDevice(); InputDevice.MotionRange range = device == null ? null : device.getMotionRange(axis, event.getSource());
        float value = event.getAxisValue(axis); return Math.abs(value) <= Math.max(deadZone, range == null ? 0 : range.getFlat()) ? 0 : value;
    }
}
