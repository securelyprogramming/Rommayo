# Android handhelds & controllers

Android 10+ is required. The main library uses native Android Views and calls the RomM API directly. The app reuses its saved session cookie, without saving your password. Emulation runs in RomM's web players. Full RomM gives access to features that do not yet have a native screen.

The cover grid and native buttons work with touch, a D-pad or a controller. Landscape uses a navigation rail; portrait uses a bottom bar. Short screens hide the search field until requested and use smaller cards.

| Input | Native menu action |
| --- | --- |
| D-pad / left stick | Move focus |
| A | Open the focused game/control |
| B | Back |
| X | Search |
| Y | Recently played |
| LB / RB | Switch Library, Recent and Settings |

In Settings, swap A/B actions, disable stick navigation, adjust the stick dead zone (15–75%), enable compact cards or lock landscape. The controller check screen lists attached controllers and shows button and axis events. USB and Bluetooth controller pairing uses Android's system settings. Controllers with built-in Android mappings, including many Xbox-style and retro handheld controls, use the same input path.

These are **menu settings**. When a web player is active, controller inputs pass through unchanged. Use the player settings to map gameplay buttons; native A/B swapping does not remap games. Save progress before returning to the library. Some browser gamepad APIs require HTTPS; plain HTTP on a LAN can browse successfully while gameplay controller support is unavailable.

Validation used a Galaxy S24 Ultra and Retroid Pocket 5 with preserved release sessions and injected Android button and axis events. The owner confirmed physical D-pad menu navigation on the Retroid, including opening the Recent platform filter with A, moving through it with the D-pad, selecting with a fresh A press and cancelling with B. Physical left-stick menu and Xbox controller gameplay checks remain outstanding. There is no universal compatibility claim for every controller, core or game. Keep Android System WebView updated.

Native menu navigation uses an explicit focus highlight. D-pad and left-stick input moves between cards, filters, tabs and settings; A opens the focused item and B returns. Hold a direction to repeat. Touch and controller input can be mixed. The menu dead zone and A/B swap affect native menu actions. [Contributor input checks](android-controller-tests.md).

In EmulatorJS, the top-left **Hide/Show touch controls** button remembers whether the virtual D-pad and buttons should be visible. The player menu remains reachable. Save/export first, then use EmulatorJS **Exit** and confirm to return to the native library. **Cancel** stays in the game.
