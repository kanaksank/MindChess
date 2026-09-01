# Offline Chess (Android)

A fully offline, single-player chess app for Android. You play White against a
built-in AI opponent — no internet connection, ads, or accounts required
(the manifest requests zero permissions).

## Features
- Full chess rules: legal move generation, check/checkmate/stalemate detection,
  castling (kingside & queenside), en passant, and pawn promotion (auto-queens).
- Three difficulty levels, selectable before/between games:
  - **Easy** – shallow search, frequently plays a random legal move.
  - **Medium** – 2-ply minimax search with alpha-beta pruning.
  - **Hard** – 3-ply minimax search with alpha-beta pruning.
- Simple, clean board UI using Unicode chess glyphs (no image assets needed).
- Tap-to-select, tap-to-move interaction with legal-move highlighting.
- AI "thinks" on a background thread so the UI never freezes.

## How to build
1. Install **Android Studio** (Giraffe/Koala or newer; free from developer.android.com).
2. Open this folder (`ChessOffline/`) as a project: **File → Open**.
3. Android Studio will detect there's no `gradlew` wrapper jar checked in (binary
   files aren't included here) and will offer to regenerate it automatically —
   accept the prompt, or run **File → Sync Project with Gradle Files**.
4. Once Gradle sync finishes, click **Run ▶** with an emulator or a physical
   device connected. minSdk is 21 (Android 5.0+), so it runs on virtually any
   modern Android phone.
5. To get an installable APK directly: **Build → Build Bundle(s)/APK(s) → Build APK(s)**.
   The APK will be under `app/build/outputs/apk/debug/`.

No API keys, servers, or network access are needed anywhere in this project.

## Project structure
```
app/src/main/java/com/example/chessoffline/
  ChessEngine.kt   – board representation, rules, move generation, minimax AI
  ChessView.kt      – custom View that draws the board and handles touch input
  MainActivity.kt   – screen wiring: difficulty selector, turn flow, AI thread
app/src/main/res/   – layout & minimal resources (icon, strings, theme)
```

## Possible improvements
- Let the player pick a promotion piece (currently auto-promotes to Queen).
- Add move history / algebraic notation log and undo.
- Add a "flip board" option to play as Black.
- Persist game state across app restarts.
- Increase Hard mode's search depth (with iterative deepening + a move-ordering
  heuristic) for a stronger opponent — 3-ply keeps it fast on all devices, but
  4–5 ply is very achievable on modern phones with a bit more optimization.
