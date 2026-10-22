# Findings
- Existing app defaults turnSpeed=70, but TcpManager ignores steeringSpeed. Existing protocol already includes max_turn_speed_ratio.
- Board mixes x/y independently. Use fixed M=.15, speed_ratio=1, y=direction*speed/M*(1-abs(x)), max_turn_speed_ratio=gain.
- This new proportional mix fits wheel envelope: abs(y)+abs(x)*gain<=1 for speed<=M and gain<=1.
- Current remote settings writes implicitly enable disc; new drive/turn slider must not invoke those writes.
- Android SDK/JDK17 installed; adb lists emulator-5554 and 127.0.0.1:16384. Use isolated library instrumentation screenshots, not production activity/network.
- Reference images: exec-ce4eced3-25c3-42ec-be8d-8de6dc3672d1.png (remote), exec-3418d0c2-b49e-4c4d-ac81-a13a8871a49d.png (map).
- Existing ManualControlSession tests protect immediate zero and late STOP ACK ordering; retain and extend equivalent shared controller guarantees.
## Taller scanning map follow-up
- The lower full-width control deck consumed map height even though travel and steering could occupy side columns. Extracted existing pads for reuse, preserving the remote deck layout and all existing input semantics.
- Scanning map now uses weighted center height with fixed left/right pads. A compact bottom status/stop footer aligns with cancel/next; pinch/pan remains inside the map. Actual 1600x966 captures show ~500px map height, previously ~350px.
- Common 18 unit tests and all 4 existing native integration tests passed; full production/test APK build succeeded. No controller or protocol changes in this layout follow-up.

## Global header alignment follow-up
- Existing Home header places emergency at TopCenter and status at TopEnd with a 14dp gap plus 36dp close slot, inside 24dp horizontal/14dp vertical screen insets. Scanning redesign had moved emergency to the far right.
- Shared GlobalTopBar now renders both pages with the original Home positions/emergency presentation. Scanning reserves the close slot without introducing another navigation action; map/footer input logic unchanged.
