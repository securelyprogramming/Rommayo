package com.rommayo.app;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Android views for the primary app experience, independent of the RomM web frontend. */
final class NativeLibrary implements ControllerNavigation.Target, InputManager.InputDeviceListener {
    private static final int BG = Color.rgb(14, 21, 30), PANEL = Color.rgb(27, 40, 56), INK = Color.rgb(236, 245, 255), MUTED = Color.rgb(165, 186, 207), ACCENT = Color.rgb(103, 196, 255);
    private final MainActivity activity;
    private final LinearLayout root;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final InputManager inputs;
    private final ControllerNavigation controls;
    private RommApi api;
    private LinearLayout body, navigation;
    private TextView status, hint, diagnostic;
    private EditText search;
    private GridView grid;
    private final List<JSONObject> games = new ArrayList<>();
    private JSONArray platforms = new JSONArray();
    private String section = "Library", screen = "login", query = "", platformName = "All platforms";
    private int platform = -1, offset, total, generation;
    private boolean loading, hasMore, restoring;
    private JSONObject selectedGame;
    private int restoreSelection;
    private boolean shortScreen() { return activity.getResources().getConfiguration().screenHeightDp < 480; }
    private boolean compact() { return shortScreen() || prefs.getBoolean("compact", false); }
    private Runnable pendingSearch;
    private int selection;

    NativeLibrary(MainActivity activity, LinearLayout root, SharedPreferences prefs) {
        this.activity = activity; this.root = root; this.prefs = prefs;
        controls = new ControllerNavigation(this); readControls();
        inputs = (InputManager) activity.getSystemService(android.content.Context.INPUT_SERVICE);
        inputs.registerInputDeviceListener(this, handler); applyOrientation();
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(activity); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setPadding(dp(4), dp(6), dp(4), dp(6)); return view;
    }
    private GradientDrawable shape(int color, boolean focus) {
        GradientDrawable background = new GradientDrawable(); background.setColor(color); background.setCornerRadius(dp(5));
        if (focus) background.setStroke(dp(3), ACCENT); return background;
    }
    private StateListDrawable focusBackground() {
        StateListDrawable state = new StateListDrawable();
        state.addState(new int[]{android.R.attr.state_pressed}, shape(Color.rgb(49, 91, 120), true));
        state.addState(new int[]{android.R.attr.state_focused}, shape(Color.rgb(35, 64, 86), true));
        state.addState(new int[]{android.R.attr.state_selected}, shape(Color.rgb(35, 64, 86), true));
        state.addState(new int[]{}, shape(PANEL, false)); return state;
    }
    private Button button(String label, Runnable action) {
        Button view = new Button(activity); view.setText(label); view.setTextColor(INK); view.setTextSize(14); view.setAllCaps(false);
        view.setMinHeight(dp(48)); view.setMinimumHeight(dp(48)); view.setPadding(dp(12), dp(6), dp(12), dp(6));
        view.setBackground(focusBackground()); view.setOnClickListener(v -> action.run());
        view.setOnFocusChangeListener((v, focused) -> v.setElevation(focused ? dp(6) : 0)); return view;
    }
    private LinearLayout column() { LinearLayout view = new LinearLayout(activity); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private void add(LinearLayout parent, View view) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(5), 0, dp(5)); parent.addView(view, lp); }
    private void rowButton(LinearLayout row, String label, Runnable action) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.setMargins(dp(3), 0, dp(3), 0); row.addView(button(label, action), lp); }
    private void cancelPage() { controls.stop(); generation++; if (pendingSearch != null) handler.removeCallbacks(pendingSearch); loading = false; }
    private void frame(String title, boolean tabs) {
        cancelPage(); root.removeAllViews(); root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.rgb(27, 43, 59), BG, BG}));
        boolean rail = tabs && shortScreen() && activity.getResources().getConfiguration().screenWidthDp >= 600;
        LinearLayout stage = new LinearLayout(activity); stage.setOrientation(rail ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout main = column();
        if (rail) {
            LinearLayout side = column(); side.setBackgroundColor(Color.rgb(23, 29, 37)); side.setPadding(dp(8), dp(14), dp(8), dp(10));
            TextView logo = text("ROMMAYO", 15, INK); logo.setTypeface(null, Typeface.BOLD); logo.setLetterSpacing(.05f); add(side, logo);
            add(side, text("YOUR NEXT\nSAVE POINT", 10, MUTED));
            navigation = side; stage.addView(side, new LinearLayout.LayoutParams(dp(124), -1));
            add(side, navButton("Library", "Library", () -> switchSection("Library")));
            add(side, navButton("Recent", "Recent", () -> switchSection("Recent")));
            add(side, navButton("Settings", "Settings", this::settings));
            stage.addView(main, new LinearLayout.LayoutParams(0, -1, 1));
        } else stage.addView(main, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout header = new LinearLayout(activity); header.setGravity(android.view.Gravity.CENTER_VERTICAL); header.setPadding(dp(16), dp(shortScreen() ? 3 : 10), dp(16), dp(shortScreen() ? 3 : 8));
        LinearLayout wordmark = column(); TextView name = text(rail ? title : "ROMMAYO", rail ? 19 : 20, INK); name.setTypeface(null, Typeface.BOLD); name.setLetterSpacing(.06f); name.setPadding(0, 0, 0, 0); wordmark.addView(name);
        if (!rail) { TextView caption = text(title + "  /  YOUR NEXT SAVE POINT", 10, ACCENT); caption.setLetterSpacing(.10f); caption.setPadding(0, dp(3), 0, 0); wordmark.addView(caption); }
        header.setContentDescription("ROMMAYO  /  " + title); header.addView(wordmark, new LinearLayout.LayoutParams(0, -2, 1));
        TextView badge = text(tabs ? "● CONNECTED" : "PUBLIC ALPHA", 10, tabs ? Color.rgb(167, 216, 115) : ACCENT); badge.setLetterSpacing(.04f); header.addView(badge); main.addView(header);
        body = column(); body.setPadding(dp(14), dp(4), dp(14), dp(6)); main.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        if (tabs && !rail) {
            navigation = new LinearLayout(activity); navigation.setPadding(dp(8), dp(7), dp(8), dp(7)); navigation.setBackgroundColor(Color.rgb(23, 29, 37));
            for (String destination : new String[]{"Library", "Recent", "Settings"}) {
                Button item = navButton(destination, destination, () -> { if (destination.equals("Settings")) settings(); else switchSection(destination); });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1); lp.setMargins(dp(4), 0, dp(4), 0); navigation.addView(item, lp);
            }
            root.addView(navigation);
        }
        hint = text(controllerHint(), 11, MUTED); hint.setSingleLine(true); hint.setEllipsize(android.text.TextUtils.TruncateAt.END); hint.setPadding(dp(12), dp(4), dp(12), dp(4)); root.addView(hint);
        root.post(() -> { if (!controllerNames().isEmpty()) ControllerFocus.start(body, screen.equals("library") ? grid : null); });
    }
    private Button navButton(String title, String destination, Runnable action) {
        Button item = button(title, action); item.setContentDescription(destination); item.setTextSize(13); item.setLetterSpacing(.05f);
        boolean selected = (screen.equals("settings") || screen.equals("controller")) ? destination.equals("Settings") : destination.equals(section);
        item.setTextColor(selected ? ACCENT : MUTED); item.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        if (selected) { StateListDrawable state = new StateListDrawable(); state.addState(new int[]{android.R.attr.state_focused}, shape(Color.rgb(35, 64, 86), true)); state.addState(new int[]{android.R.attr.state_pressed}, shape(Color.rgb(49, 91, 120), true)); GradientDrawable active = shape(Color.rgb(36, 60, 80), false); active.setStroke(dp(1), Color.rgb(54, 98, 128)); state.addState(new int[]{}, active); item.setBackground(state); }
        return item;
    }
    private String controllerHint() {
        int count = controllerNames().size();
        return (count == 0 ? "Touch or connect a controller" : count + " controller(s) connected") + "  ·  " + (controls.swap ? "B open · A back" : "A open · B back") + "  ·  LB/RB sections";
    }
    void login(String message) {
        endSession(); screen = "login"; frame("CONNECT", false);
        ScrollView scroll = new ScrollView(activity); LinearLayout form = column(); scroll.setFillViewport(true); scroll.addView(form); body.addView(scroll);
        add(form, text("Your library. Your devices.", 30, INK));
        add(form, text("Connect directly to your RomM server. Your password is used for sign-in; the app keeps the session cookie.", 16, MUTED));
        EditText address = field(form, "RomM URL or IP", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setText(prefs.getString("lastServer", activity.currentServer()));
        EditText user = field(form, "Username", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        EditText password = field(form, "Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        CheckBox remember = new CheckBox(activity); remember.setText("Remember login on this device"); remember.setTextColor(INK); remember.setChecked(prefs.getBoolean("rememberLogin", true)); add(form, remember);
        add(form, text("Stores the session cookie, never your password. Uncheck to require sign-in after restarting the app.", 13, MUTED));
        CheckBox trust = new CheckBox(activity); trust.setText("I trust this server and only use content I am authorized to play."); trust.setTextColor(INK); add(form, trust);
        CheckBox http = new CheckBox(activity); http.setText("Allow unencrypted HTTP on my trusted local network."); http.setTextColor(INK); add(form, http);
        TextView error = text(message == null ? "" : message, 15, ACCENT); add(form, error);
        Button signIn = button("Connect to RomM", () -> {
            if (loading) return;
            try {
                String normalized = ServerPolicy.normalize(address.getText().toString());
                if (!trust.isChecked()) throw new Exception("Confirm that you trust this server.");
                if (normalized.startsWith("http:") && !http.isChecked()) throw new Exception("Confirm the unencrypted HTTP connection.");
                String username = user.getText().toString(), secret = password.getText().toString();
                if (username.isEmpty() || username.contains(":") || secret.isEmpty()) throw new Exception("Enter your RomM username and password.");
                activity.beginSession(normalized, remember.isChecked());
                if (api != null) api.close(); api = new RommApi(normalized); loading = true; error.setText("Signing in…");
                api.login(username, secret, (result, issue) -> {
                    loading = false; password.setText("");
                    if (issue != null) { error.setText(issue); return; }
                    hideKeyboard(); connected(normalized);
                });
            } catch (Exception issue) { error.setText(issue.getMessage()); }
        }); add(form, signIn);
        add(form, button("Full RomM sign-in / SSO", () -> {
            try {
                String normalized = ServerPolicy.normalize(address.getText().toString());
                if (!trust.isChecked() || (normalized.startsWith("http:") && !http.isChecked())) { error.setText("Confirm server trust and HTTP if needed."); return; }
                activity.beginSession(normalized, remember.isChecked()); if (api != null) api.close(); api = new RommApi(normalized); activity.openWeb("");
            } catch (Exception issue) { error.setText(issue.getMessage()); }
        }));
        add(form, text("Rommayo provides no games or BIOS files. Downloads from your server are at your own risk. Independent companion to RomM.", 13, MUTED));
    }
    private EditText field(LinearLayout parent, String label, int type) {
        add(parent, text(label, 14, MUTED)); EditText view = new EditText(activity); view.setSingleLine(true); view.setInputType(type); view.setTextColor(INK); view.setTextSize(16); view.setMinHeight(dp(48)); view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ACCENT)); add(parent, view); return view;
    }
    void endSession() { cancelPage(); if (api != null) { api.close(); api = null; } games.clear(); platforms = new JSONArray(); }
    void restore(String address) { if (api != null) api.close(); api = new RommApi(address);  library(); }
    private void connected(String address) {  platforms = new JSONArray(); query = ""; platform = -1; library(); }
    void fromWeb() { if (api == null && !activity.currentServer().isEmpty()) api = new RommApi(activity.currentServer()); library(); }
    private void switchSection(String value) { hideKeyboard(); section = value; library(); }
    private void hideKeyboard() { View focus = activity.getCurrentFocus(); if (focus != null) ((InputMethodManager) activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(), 0); }
    private void library() {
        if (api == null) { login(null); return; }
        screen = "library"; frame(section.toUpperCase(java.util.Locale.ROOT), true); restoring = true;
        search = new EditText(activity); search.setTextColor(INK); search.setTextSize(15); search.setHintTextColor(MUTED); search.setHint("Search your games  ·  X"); search.setSingleLine(true); search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH); search.setText(query); add(body, search);
        if (shortScreen() && query.isEmpty()) search.setVisibility(View.GONE);
        LinearLayout filters = new LinearLayout(activity); if (shortScreen()) rowButton(filters, "Search · X", this::search); rowButton(filters, platformName, this::platformPicker); rowButton(filters, "Full RomM", () -> activity.openWeb("")); add(body, filters);
        TextView shelf = text(section.equals("Recent") ? "PICK UP WHERE YOU LEFT OFF" : "ALL GAMES", 13, INK); shelf.setTypeface(null, Typeface.BOLD); shelf.setLetterSpacing(.09f); if (!shortScreen()) body.addView(shelf);
        status = text("Loading your library…", 12, MUTED); body.addView(status);
        grid = new GridView(activity); grid.setFocusable(true); grid.setFocusableInTouchMode(true); grid.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS); grid.setNumColumns(GridView.AUTO_FIT); grid.setColumnWidth(dp(compact() ? 118 : 152)); grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH); grid.setHorizontalSpacing(dp(10)); grid.setVerticalSpacing(dp(10)); grid.setPadding(dp(4), dp(4), dp(4), dp(8)); grid.setClipToPadding(false); grid.setDrawSelectorOnTop(true); grid.setSelector(ControllerFocus.selector(ACCENT,dp(3),dp(6))); grid.setCacheColorHint(Color.TRANSPARENT);
        grid.setAdapter(adapter); body.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        grid.setOnItemClickListener((parent, view, position, id) -> { selection = position; detail(games.get(position)); });
        grid.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int state) {}
            @Override public void onScroll(AbsListView view, int first, int visible, int count) { if (count > 0 && first + visible >= count - 8 && hasMore && !loading) load(false); }
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (restoring) return; query = s.toString(); if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
                pendingSearch = () -> load(true); handler.postDelayed(pendingSearch, 350);
            }
            public void afterTextChanged(Editable s) {}
        });
        search.setOnEditorActionListener((v, action, event) -> { hideKeyboard(); load(true); grid.requestFocus(); return true; });
        restoring = false; load(true);
        if (platforms.length() == 0) { final RommApi current = api; api.get("api/platforms", (result, error) -> { if (current != api || error != null) return; try { platforms = new JSONArray(result); } catch (Exception ignored) {} }); }
    }
    private void load(boolean reset) {
        if (!screen.equals("library")) return;
        if (reset) { generation++; offset = 0; games.clear(); adapter.notifyDataSetChanged(); selection = 0; }
        else if (loading || !hasMore) return;
        final int request = generation; loading = true; status.setText("Loading…");
        String path = "api/roms?limit=60&offset=" + offset + "&with_char_index=false&with_filter_values=false&with_rom_id_index=false&search_term=" + android.net.Uri.encode(query);
        if (platform >= 0) path += "&platform_ids=" + platform;
        if (section.equals("Recent")) path += "&last_played=true&order_by=last_played&order_dir=desc";
        api.get(path, (result, error) -> {
            if (request != generation || !screen.equals("library")) return;
            loading = false;
            if (error != null) { status.setText(error); hasMore = false; status.setFocusable(true); status.setOnClickListener(v -> load(true)); status.setContentDescription(error + " Tap to retry."); return; }
            try {
                JSONObject page = new JSONObject(result); JSONArray items = page.getJSONArray("items"); total = page.optInt("total", games.size() + items.length());
                for (int i = 0; i < items.length(); i++) games.add(items.getJSONObject(i)); offset = games.size(); hasMore = items.length() > 0 && offset < total;
                adapter.notifyDataSetChanged(); status.setText(total == 0 ? "No games found. Change your search or platform." : total + " games  ·  " + games.size() + " loaded" + (hasMore ? "  ·  scroll for more" : ""));
                if (reset && grid != null && !(activity.getCurrentFocus() instanceof EditText)) { ControllerFocus.select(grid, Math.min(restoreSelection, Math.max(0, games.size() - 1)), !controllerNames().isEmpty()); restoreSelection = 0; }
            } catch (Exception issue) { status.setText("This RomM version returned an unsupported response. Open Full RomM."); hasMore = false; }
        });
    }
    private final BaseAdapter adapter = new BaseAdapter() {
        @Override public int getCount() { return games.size(); }
        @Override public Object getItem(int position) { return games.get(position); }
        @Override public long getItemId(int position) { return games.get(position).optLong("id"); }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            LinearLayout card; ImageView image; TextView title, subtitle;
            if (recycled instanceof LinearLayout) { card = (LinearLayout) recycled; image = (ImageView) card.getChildAt(0); title = (TextView) card.getChildAt(1); subtitle = (TextView) card.getChildAt(2); }
            else {
                card = column(); card.setPadding(dp(7), dp(7), dp(7), dp(7)); card.setBackground(focusBackground());
                image = new ImageView(activity); image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setBackgroundColor(Color.rgb(38, 55, 72)); card.addView(image, new LinearLayout.LayoutParams(-1, dp(compact() ? 88 : 154)));
                title = text("", 14, INK); title.setMaxLines(2); title.setMinLines(2); title.setLineSpacing(0, 1.05f); title.setEllipsize(android.text.TextUtils.TruncateAt.END); title.setTypeface(null, Typeface.BOLD); card.addView(title);
                subtitle = text("", 10, MUTED); subtitle.setSingleLine(true); subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END); card.addView(subtitle);
            }
            JSONObject game = games.get(position); String name = name(game); title.setText(name); subtitle.setText(game.optString("platform_display_name")); card.setContentDescription(name + ", " + game.optString("platform_display_name"));
            image.setImageDrawable(null); image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); image.setTag(game.optLong("id")); ImageView target = image; long id = game.optLong("id");
            api.cover(game.optString("path_cover_small"), bitmap -> { if (Long.valueOf(id).equals(target.getTag())) target.setImageBitmap(bitmap); }); return card;
        }
    };
    private String name(JSONObject game) { String value = game.optString("name", ""); return value.isEmpty() || value.equals("null") ? game.optString("fs_name_no_ext", "Untitled") : value; }
    private void platformPicker() {
        String[] names = new String[platforms.length() + 1]; names[0] = "All platforms";
        for (int i = 0; i < platforms.length(); i++) names[i + 1] = platforms.optJSONObject(i).optString("display_name", platforms.optJSONObject(i).optString("name"));
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Choose platform").setItems(names, (d, which) -> { platform = which == 0 ? -1 : platforms.optJSONObject(which - 1).optInt("id"); platformName = names[which]; library(); }).setNegativeButton("Cancel", null).create(); controllerDialog(dialog); dialog.show();
    }
    private void detail(JSONObject game) {
        hideKeyboard(); selectedGame = game; screen = "detail"; frame("GAME", true); int request = generation;
        ScrollView scroll = new ScrollView(activity); LinearLayout content = column(); scroll.addView(content); body.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        add(content, button("‹ Back to " + section.toLowerCase(java.util.Locale.ROOT), this::returnLibrary));
        TextView heading = text(name(game), 29, INK); heading.setTypeface(null, Typeface.BOLD); add(content, heading);
        add(content, text(game.optString("platform_display_name"), 16, ACCENT));
        ImageView art = new ImageView(activity); art.setScaleType(ImageView.ScaleType.FIT_CENTER); content.addView(art, new LinearLayout.LayoutParams(-1, dp(190))); api.cover(game.optString("path_cover_large"), art::setImageBitmap);
        long id = game.optLong("id");
        add(content, button("Play · EmulatorJS", () -> activity.openWeb("rom/" + id + "/ejs")));
        add(content, button("Open in Full RomM · other players & tools", () -> activity.openWeb("rom/" + id)));
        add(content, text("Player and controller compatibility depend on the system, game and your RomM configuration. Map gameplay buttons in the player settings.", 13, MUTED));
        TextView description = text("Loading game details…", 16, MUTED); add(content, description);
        api.get("api/roms/" + id, (result, error) -> {
            if (request != generation) return;
            if (error != null) { description.setText(error); return; }
            try { JSONObject info = new JSONObject(result); String summary = info.optString("summary", ""); description.setText(summary.isEmpty() || summary.equals("null") ? "No description available for this game." : summary); }
            catch (Exception issue) { description.setText("Open Full RomM for more game details."); }
        });
        content.getChildAt(0).requestFocus();
    }
    private void returnLibrary() { restoreSelection = selection; library(); /* Fresh server data includes recent play activity. */ }
    void settings() {
        screen = "settings"; frame("SETTINGS", true);
        ScrollView scroll = new ScrollView(activity); LinearLayout content = column(); scroll.addView(content); body.addView(scroll);
        add(content, text("Handheld & controller", 25, INK));
        toggle(content, "Swap A/B menu actions", "swap", false, () -> { readControls(); hint.setText(controllerHint()); });
        toggle(content, "Left stick navigates menus", "sticks", true, this::readControls);
        add(content, text("Stick dead zone (menu navigation only)", 15, MUTED)); TextView dead = text("", 14, ACCENT); add(content, dead);
        SeekBar zone = new SeekBar(activity); zone.setMin(15); zone.setMax(75); zone.setProgress(prefs.getInt("deadzone", 30)); dead.setText(zone.getProgress() + "%");
        zone.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onStartTrackingTouch(SeekBar v) {} public void onStopTrackingTouch(SeekBar v) {} public void onProgressChanged(SeekBar v, int value, boolean user) { dead.setText(value + "%"); prefs.edit().putInt("deadzone", value).apply(); readControls(); } }); add(content, zone);
        toggle(content, "Compact cards for small screens", "compact", false, () -> {});
        toggle(content, "Lock landscape for handheld play", "landscape", false, this::applyOrientation);
        add(content, button("Controller check", this::controllerCheck));
        add(content, text("D-pad / left stick: move focus\n" + (controls.swap ? "B: open · A: back" : "A: open · B: back") + "\nX: search · Y: recent games\nLB / RB: Library, Recent, Settings\n\nThese mappings control native menus. In games, inputs pass directly to the web player; use its controller mapping screen. Connect USB or Bluetooth controllers through Android settings.", 15, MUTED));
        add(content, text("Server", 24, INK)); add(content, text(activity.currentServer(), 15, MUTED));
        add(content, button("Full RomM", () -> activity.openWeb("")));
        add(content, text(prefs.getBoolean("rememberLogin", true) ? "Login is remembered on this device." : "Login lasts until the app restarts.", 14, MUTED));
        add(content, button("Log out", () -> {
            AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Log out of RomM?").setMessage("End this device’s session. Your local game saves and exported files are preserved.").setNegativeButton("Stay", null).setPositiveButton("Log out", (d, which) -> { activity.logOut(); loginProgress(); }).create(); controllerDialog(dialog); dialog.show();
        }));
        add(content, button("Sign in again / switch server", () -> login(null)));
        add(content, button("Forget this device’s sessions", () -> {
            AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Forget saved sessions?").setMessage("Clear saved servers and session cookies on this device?").setNegativeButton("Cancel", null).setPositiveButton("Forget", (d, which) -> activity.forgetSessions()).create(); controllerDialog(dialog); dialog.show();
        }));
        content.getChildAt(1).requestFocus();
    }
    private void loginProgress() { screen = "login"; frame("LOGGING OUT", false); add(body, text("Ending this device’s session…", 18, INK)); }
    private void toggle(LinearLayout content, String label, String key, boolean fallback, Runnable changed) {
        Switch view = new Switch(activity); view.setText(label); view.setTextColor(INK); view.setTextSize(16); view.setMinHeight(dp(52)); view.setPadding(dp(8), dp(4), dp(8), dp(4)); view.setBackground(focusBackground()); view.setChecked(prefs.getBoolean(key, fallback)); view.setOnCheckedChangeListener((v, value) -> { prefs.edit().putBoolean(key, value).apply(); changed.run(); }); add(content, view);
    }
    private void readControls() { controls.swap = prefs.getBoolean("swap", false); controls.sticks = prefs.getBoolean("sticks", true); controls.deadZone = prefs.getInt("deadzone", 30) / 100f; }
    private void applyOrientation() { activity.setRequestedOrientation(prefs.getBoolean("landscape", false) ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED); }
    private List<String> controllerNames() {
        List<String> names = new ArrayList<>(); for (int id : InputDevice.getDeviceIds()) { InputDevice device = InputDevice.getDevice(id); if (device != null && (device.supportsSource(InputDevice.SOURCE_GAMEPAD) || device.supportsSource(InputDevice.SOURCE_JOYSTICK))) names.add(device.getName()); } return names;
    }
    private void controllerCheck() {
        screen = "controller"; frame("CONTROLLER CHECK", true);
        add(body, text("Connected input devices", 24, INK));
        diagnostic = text("", 16, ACCENT); add(body, diagnostic); refreshDevices();
        add(body, text("Press buttons, move the left stick or D-pad to see Android input events. B (or A if swapped) returns. This checks menu input; gameplay mapping is handled by the player.", 16, MUTED));
        add(body, button("Back to settings", this::settings));
    }
    private void refreshDevices() { if (hint != null) hint.setText(controllerHint()); if (screen.equals("controller") && diagnostic != null) { List<String> names = controllerNames(); diagnostic.setText(names.isEmpty() ? "No controller detected. Connect one using USB or Bluetooth." : android.text.TextUtils.join("\n", names)); } }
    @Override public void onInputDeviceAdded(int id) { refreshDevices(); }
    @Override public void onInputDeviceRemoved(int id) { controls.stop(); refreshDevices(); }
    @Override public void onInputDeviceChanged(int id) { refreshDevices(); }
    boolean key(KeyEvent event) {
        if (screen.equals("controller")) {
            if (event.getKeyCode() == (controls.swap ? KeyEvent.KEYCODE_BUTTON_A : KeyEvent.KEYCODE_BUTTON_B)) { if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) settings(); return true; }
            if (event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_DPAD) || KeyEvent.isGamepadButton(event.getKeyCode())) { if (event.getAction() == KeyEvent.ACTION_DOWN) diagnostic.setText("Button: " + KeyEvent.keyCodeToString(event.getKeyCode()) + "\nDevice: " + (event.getDevice() == null ? "injected / keyboard" : event.getDevice().getName())); return true; }
        }
        return controls.key(event);
    }
    boolean motion(MotionEvent event) {
        if (screen.equals("controller") && event.isFromSource(InputDevice.SOURCE_JOYSTICK)) { diagnostic.setText(String.format(java.util.Locale.ROOT, "Left stick  X %.2f  Y %.2f\nD-pad  X %.0f  Y %.0f", event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y), event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y))); return true; }
        return controls.motion(event);
    }
    @Override public void direction(int key) { ControllerFocus.move(root, body, screen.equals("library") ? grid : null, key); }
    @Override public void confirm() {
        View view = ControllerFocus.ensure(root, body, screen.equals("library") ? grid : null);
        if (view instanceof GridView) { int position = grid.getSelectedItemPosition(); if (position >= 0 && position < games.size()) { selection = position; detail(games.get(position)); } }
        else if (view != null) view.performClick();
    }
    @Override public void back() { if (screen.equals("login")) activity.confirmExit(); else if (screen.equals("controller")) settings(); else if (!screen.equals("library")) returnLibrary(); else if (!query.isEmpty()) { query = ""; library(); } else activity.confirmExit(); }
    @Override public void section(int delta) { if (api == null || screen.equals("login")) return; int index = screen.equals("settings") ? 2 : section.equals("Recent") ? 1 : 0; index = (index + delta + 3) % 3; if (index == 2) settings(); else switchSection(index == 0 ? "Library" : "Recent"); }
    @Override public void search() { if (!screen.equals("library")) { if (api == null) return; library(); } search.setVisibility(View.VISIBLE); search.requestFocus(); ((InputMethodManager) activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).showSoftInput(search, InputMethodManager.SHOW_IMPLICIT); }
    @Override public void recent() { if (api != null && !screen.equals("login")) switchSection("Recent"); }
    void controllerDialog(AlertDialog dialog) {
        controls.stop();
        // Ignore the release of the A/B press which opened this dialog.
        // Only a press begun inside this window may activate or dismiss it.
        final boolean[] confirmPressed={false}, backPressed={false};
        dialog.setOnShowListener(d -> { ViewGroup decor=(ViewGroup)dialog.getWindow().getDecorView(); decor.post(() -> ControllerFocus.ensure(decor,decor,dialog.getListView()!=null?dialog.getListView():dialog.getButton(AlertDialog.BUTTON_NEGATIVE))); });
        dialog.setOnKeyListener((d,key,event) -> {
            if(key>=KeyEvent.KEYCODE_DPAD_UP && key<=KeyEvent.KEYCODE_DPAD_RIGHT) {
                if(event.getAction()==KeyEvent.ACTION_DOWN) { ViewGroup decor=(ViewGroup)dialog.getWindow().getDecorView(); ControllerFocus.move(decor,decor,dialog.getListView(),key); }
                return true;
            }
            if(key==(controls.swap?KeyEvent.KEYCODE_BUTTON_A:KeyEvent.KEYCODE_BUTTON_B)) {
                if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) backPressed[0]=true;
                else if(event.getAction()==KeyEvent.ACTION_UP) { boolean dismiss=backPressed[0]&&!event.isCanceled();backPressed[0]=false;if(dismiss)dialog.dismiss(); }
                return true;
            }
            if(key==(controls.swap?KeyEvent.KEYCODE_BUTTON_B:KeyEvent.KEYCODE_BUTTON_A)) {
                if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) confirmPressed[0]=true;
                else if(event.getAction()==KeyEvent.ACTION_UP) {
                    boolean activate=confirmPressed[0]&&!event.isCanceled();confirmPressed[0]=false;
                    View focus=dialog.getCurrentFocus();
                    if(activate&&focus!=null) {
                        if(focus instanceof ListView) { ListView list=(ListView)focus;int selected=list.getSelectedItemPosition();if(selected>=0)list.performItemClick(list.getSelectedView(),selected,list.getItemIdAtPosition(selected)); }
                        else focus.performClick();
                    }
                }
                return true;
            }
            return false;
        });
    }
    void stopInput() { controls.stop(); }
    void suspend() { cancelPage(); }
    void configurationChanged() {
        if (screen.equals("library")) { restoreSelection = grid == null ? 0 : Math.max(0, grid.getFirstVisiblePosition()); library(); }
        else if (screen.equals("settings")) settings();
        else if (screen.equals("controller")) controllerCheck();
        else if (screen.equals("detail") && selectedGame != null) detail(selectedGame);
    }
    void close() { cancelPage(); inputs.unregisterInputDeviceListener(this); if (api != null) api.close(); }
}
