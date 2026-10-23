package com.rommayo.app;

import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;

/** Explicit spatial focus for controller input, including after touch interaction. */
final class ControllerFocus {
    static android.graphics.drawable.Drawable selector(int color, int width, int radius) {
        android.graphics.drawable.GradientDrawable outline=new android.graphics.drawable.GradientDrawable();
        outline.setColor(android.graphics.Color.TRANSPARENT);outline.setCornerRadius(radius);outline.setStroke(width,color);
        android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},outline);
        states.addState(new int[]{android.R.attr.state_pressed},outline);
        states.addState(new int[]{},new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        return states;
    }
    static void select(android.widget.GridView grid, int position, boolean controller) {
        grid.setSelection(position);
        grid.post(() -> {
            if (!grid.isShown()) return;
            if (controller) grid.requestFocusFromTouch(); else grid.requestFocus();
        });
    }
    static void start(ViewGroup content, View preferred) {
        if (usable(preferred) && preferred.requestFocusFromTouch()) return;
        for (View candidate : content.getFocusables(View.FOCUS_FORWARD)) if (usable(candidate) && candidate.requestFocusFromTouch()) return;
    }
    static View ensure(ViewGroup root, ViewGroup content, View preferred) {
        View focus = root.findFocus();
        if (usable(focus)) {
            focus.requestFocusFromTouch();
            return focus;
        }
        if (usable(preferred) && preferred.requestFocusFromTouch()) return preferred;
        for (View candidate : content.getFocusables(View.FOCUS_FORWARD)) {
            if (usable(candidate) && candidate.requestFocusFromTouch()) return candidate;
        }
        for (View candidate : root.getFocusables(View.FOCUS_FORWARD)) {
            if (usable(candidate) && candidate.requestFocusFromTouch()) return candidate;
        }
        return null;
    }
    private static boolean usable(View view) {
        return view != null && view.isShown() && view.isEnabled() && view.isFocusable()
            && (!(view instanceof ViewGroup) || view instanceof AbsListView)
            && (!(view instanceof AbsListView) || ((AbsListView)view).getCount() > 0);
    }
    static void move(ViewGroup root, ViewGroup content, View preferred, int key) {
        boolean hadFocus = root.findFocus() != null;
        View focus = ensure(root, content, preferred);
        if (focus == null || !hadFocus) return;
        // Lists/grids, text fields and sliders first get their normal internal movement.
        boolean consumed = focus.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
        focus.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
        if (consumed) return;
        int direction;
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP: direction = View.FOCUS_UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: direction = View.FOCUS_DOWN; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: direction = View.FOCUS_LEFT; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: direction = View.FOCUS_RIGHT; break;
            default: return;
        }
        View next = focus.focusSearch(direction);
        if (!usable(next) || !inside(root, next)) return;
        if (next.requestFocusFromTouch()) {
            Rect bounds = new Rect(); next.getDrawingRect(bounds);
            next.requestRectangleOnScreen(bounds, false);
        }
    }
    private static boolean inside(ViewGroup root, View view) {
        for (android.view.ViewParent parent = view.getParent(); parent != null; parent = parent.getParent()) if (parent == root) return true;
        return false;
    }
}
