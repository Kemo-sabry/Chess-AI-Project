# Chess Game

A JavaFX chess application with multiple play modes and a built-in AI engine.

## Features

- **Three play modes:** Player vs Player, Player vs AI, and AI vs AI
- **Two AI types:**
  - *Random* — picks a legal move at random
  - *Alpha-Beta* — minimax search with alpha-beta pruning and configurable depth
- **Move ordering** — captures and check-giving moves are searched first to improve pruning efficiency
- **Immediate mate detection** — the AI recognizes and plays checkmate in one without spending search time
- **Pawn promotion** — pawns automatically promote to queens on reaching the back rank
- **Legal move highlighting** — click a piece to see all legal squares highlighted in yellow
- **Configurable AI depth** — set search depth (in plies) independently for each side in AI vs AI mode

## Requirements

- Java 17 or later
- JavaFX 17 or later (must be on the module path)
- A build tool such as Maven or Gradle (recommended), or manual `javac`/`java` with JavaFX JARs

## Project Structure

```
src/
└── main/java/com/example/chess/
    ├── AI.java           # Random and Alpha-Beta AI agents
    ├── ChessBoard.java   # Board state, move validation, UI rendering
    ├── GameSettings.java # Settings dialog (play mode, AI type, depth, colors)
    ├── Move.java         # Move data class (fromRow/Col → toRow/Col)
    └── Piece.java        # Piece data class (type + color)
```

## Getting Started

### With Maven

Add the JavaFX plugin to your `pom.xml` and run:

```bash
mvn javafx:run
```

### With Gradle

```bash
./gradlew run
```

### Manually

```bash
javac --module-path /path/to/javafx/lib --add-modules javafx.controls \
      -d out src/main/java/com/example/chess/*.java

java --module-path /path/to/javafx/lib --add-modules javafx.controls \
     -cp out com.example.chess.Main
```

## How to Play

1. Launch the app — a **Game Settings** dialog appears.
2. Choose a play mode:
   - **Player vs Player** — two humans share the keyboard/mouse.
   - **Player vs AI** — choose your color, AI type, and search depth.
   - **AI vs AI** — watch two AI agents play; set a depth for each side.
3. Click **Start**.
4. In Player vs Player / Player vs AI modes:
   - Click a piece to select it (legal destinations highlight in yellow).
   - Click a highlighted square to move, or click another own piece to re-select.
5. The game announces check, checkmate, or stalemate via a dialog.

## AI Details

### Random Agent
Picks uniformly at random from all legal moves. Useful as a baseline or for very fast AI vs AI games.

### Alpha-Beta Agent
A negamax-style minimax search with alpha-beta pruning.

**Evaluation function** (`evaluateBoardSimple`):
- Material balance using standard piece values (P=100, N=320, B=330, R=500, Q=900, K=20000)
- Mobility bonus: +3 per legal move advantage

**Move ordering** (enabled by default):
- Captures ordered by victim piece value (MVV heuristic)
- Check-giving moves receive a +5000 bonus, ensuring they are searched early

**Depth guide** (approximate, depends on position complexity):

| Depth | Typical response time |
|-------|-----------------------|
| 2     | Instant               |
| 4     | < 1 second            |
| 6     | Several seconds       |
| 8+    | May be slow           |

## Known Limitations

- No castling or en passant support
- Pawn promotion is always to a queen (no under-promotion)
- No draw-by-repetition or fifty-move rule detection
- No move history or undo in the UI
