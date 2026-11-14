import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * 2-Frame Retro Playground & Safal Shop Explorer.
 * Frame 0: An interactive playground with swings, sand pit, slide, see-saw,
 * jumping mechanics (SPACE), and real 2D physics balls.
 * Frame 1: "Safal" grocery & confectionery shop to buy and eat chocolates.
 */
public class app extends Application {
    private static final int TILE = 48;
    private static final int COLS = 20;
    private static final int ROWS = 12;
    private static final int WIDTH = COLS * TILE; // 960
    private static final int HEIGHT = ROWS * TILE; // 576
    private static final double PLAYER_SIZE = 42;
    private static final double BASE_SPEED = 150.0;
    private static final double GRAVITY = 950.0;
    private static final double JUMP_IMPULSE = 380.0;

    // Assets
    private final Image grass = image("assets/kenney_tiny-town/Tiles/tile_0000.png");
    private final Image tree = image("assets/kenney_tiny-town/Tiles/tile_0003.png");
    private final Image shrub = image("assets/kenney_tiny-town/Tiles/tile_0007.png");
    private final Image pavement = image("assets/kenney_rpg-urban-pack/Tiles/tile_0013.png");
    private final Image hero = image("assets/kenney_rpg-urban-pack/Tiles/tile_0024.png");
    private final Image buntyHero = image("assets/kenney_rpg-urban-pack/Tiles/tile_0132.png");

    // Input & Screen
    private final Set<KeyCode> keys = new HashSet<>();
    private Pane rootContainer;
    private Pane mapLayer;
    private Pane hudLayer;
    private StackPane pauseOverlay;

    // Player state
    private ImageView player;
    private Ellipse playerShadow;
    private double playerX;
    private double playerY;
    private double jumpZ = 0.0;
    private double jumpVelZ = 0.0;
    private boolean isJumping = false;
    private boolean onSwing = false; // Player swinging state
    private int activeSwing = 0; // 0 = left swing, 1 = right swing
    private int playerFacing = 1; // 1 = Right, -1 = Left

    // BUNTY NPC
    private ImageView bunty;
    private double buntyX;
    private double buntyY;
    private double buntyTargetX;
    private double buntyTargetY;
    private double buntySpeed = 75.0;
    private double buntyPauseTimer = 1.0;
    private boolean buntyMoving = false;
    private Label buntyNameTag;
    private boolean buntyRespondedToPlayerSeeSaw = false;

    private enum BuntyActivity {
        WANDERING,
        GOING_TO_SWING,
        USING_SWING,
        GOING_TO_SEESAW,
        USING_SEESAW,
        GOING_TO_BALL,
        PLAYING_BALL
    }

    private double buntyBallKickCooldown = 0;
    private BuntyActivity buntyActivity = BuntyActivity.WANDERING;
    private double buntyActivityTimer = 0;
    private int buntyChosenSwing = 0;
    private PlaygroundBall buntyTargetBall = null;
    private boolean buntyUsingSwing = false;
    private boolean buntyUsingSeeSaw = false;

    // Persistent Economy & Inventory
    private int coins = 0;
    private int chocolates = 0;
    private double sugarRushTimer = 0.0;
    private double swingCoinTimer = 0.0; // Timer for awarding coins while swinging

    // HUD Elements
    private Text coinsText;
    private Text chocolatesText;
    private Text boostText;
    private Text toastText;
    private Text interactionPrompt;
    private double toastTimer = 0.0;

    // World state
    private boolean[][] walkable;
    private final List<Exit> exits = new ArrayList<>();
    private int currentRoom = 0; // 0 = Playground, 1 = Safal Shop
    private double transitionCooldown = 0.0; // Cooldown to prevent instant bounce-back between rooms
    private boolean isPaused = false;

    // --- See-Saw State & Positioning ---
    private boolean onSeeSaw = false;
    private double seeSawPhase = 0.0;
    private double seeSawAngle = 0.0;

    // Exact map coordinates where your see-saw is located in the bottom-left
    private double seeSawX = 120.0; // Adjust if needed based on your map setup
    private double seeSawY = 750.0; // Adjust if needed based on your map setup

    // Group reference for the rotating see-saw plank
    private Group seeSawPlankGroup;

    // Playground Interactive Physics
    private final List<PlaygroundBall> balls = new ArrayList<>();
    // Per-swing independent pendulum state: phase advances each frame, peak decays
    // when not ridden
    private double leftSwingPhase = 0.0; // current oscillation phase (radians)
    private double leftSwingPeak = 0.0; // 0 = stationary, 1 = full swing
    private double rightSwingPhase = 0.0;
    private double rightSwingPeak = 0.0;
    private double swingX = 1.5 * TILE;
    private double swingY = 1.5 * TILE;
    private Line leftRope1, leftRope2;
    private Line rightRope1, rightRope2;
    private Group leftSeatGroup;
    private Group rightSeatGroup;
    private Ellipse leftSeatShadow;
    private Ellipse rightSeatShadow;
    private Group swingHandsGroup;
    private Circle leftHandL;
    private Circle leftHandR;

    // Shop zones
    private boolean nearCounter = false;

    @Override

    public void start(Stage stage) {
        rootContainer = new Pane();
        rootContainer.setPrefSize(WIDTH, HEIGHT);
        rootContainer.setStyle("-fx-background-color: #1e1e24;");

        mapLayer = new Pane();
        mapLayer.setPrefSize(WIDTH, HEIGHT);

        hudLayer = new Pane();
        hudLayer.setPrefSize(WIDTH, HEIGHT);
        // Keep hudLayer interactive for HUD buttons while allowing world clicks through
        hudLayer.setPickOnBounds(false);

        rootContainer.getChildren().addAll(mapLayer, hudLayer);
        buildHUD();

        // Start in the playground
        loadRoom(0, 3, 6);

        // Create pause overlay (initially hidden)
        createPauseOverlay();

        Scene scene = new Scene(rootContainer, WIDTH, HEIGHT);

        scene.setOnKeyPressed(event -> {
            KeyCode code = event.getCode();

            boolean firstPress = keys.add(code);

            // Pause / Resume using ESC
            if (code == KeyCode.ESCAPE && firstPress) {
                isPaused = !isPaused;

                // Show or hide pause menu
                pauseOverlay.setVisible(isPaused);

                if (isPaused) {
                    keys.clear();
                    System.out.println("Game Paused");
                } else {
                    System.out.println("Game Resumed");
                }

                return;
            }

            // Ignore other keyboard actions while paused
            if (isPaused) {
                return;
            }

            // Dismount with SPACE
            if (code == KeyCode.SPACE && onSwing) {
                double dismountAnchorX = (activeSwing == 0) ? (swingX + 45) : (swingX + 105);
                onSwing = false;
                if (swingHandsGroup != null)
                    swingHandsGroup.setVisible(false);
                if (playerShadow != null)
                    playerShadow.setVisible(true);
                player.setScaleY(1.0);
                player.setScaleX(playerFacing);
                jumpVelZ = 240;
                isJumping = true;
                playerX = dismountAnchorX - PLAYER_SIZE / 2;
                playerY = (swingY + 66) + 28;
                showToast("Hopped off the swing!", Color.web("#ffd166"));
                return;
            }

            // Dismount see-saw using SPACE
            if (code == KeyCode.SPACE && onSeeSaw) {
                toggleSeeSaw();
                return;
            }

            // Jumping mechanic
            if (code == KeyCode.SPACE && !isJumping) {
                isJumping = true;
                jumpVelZ = JUMP_IMPULSE;
            }

            // Buy chocolate
            if (code == KeyCode.E && currentRoom == 1 && nearCounter) {
                buyChocolate();
            }

            // Mount / Dismount See-Saw or Swing
            if (code == KeyCode.E && currentRoom == 0) {

                boolean wasOnSeeSaw = onSeeSaw;

                // Try see-saw interaction first
                toggleSeeSaw();

                // If we were seated or have just mounted, don't trigger swing
                if (wasOnSeeSaw || onSeeSaw) {
                    return;
                }

                // Otherwise, try swing interaction
                toggleSwing();
            }

            // Eat chocolate
            if (code == KeyCode.C) {
                eatChocolate();
            }
        });

        scene.setOnKeyReleased(event -> keys.remove(event.getCode()));

        stage.setTitle("Retro Playground & Safal Shop (16/64-Bit)");
        stage.setResizable(false);
        stage.setScene(scene);
        stage.show();
        rootContainer.requestFocus();

        new AnimationTimer() {
            private long previous;

            @Override
            public void handle(long now) {
                if (previous == 0)
                    previous = now;

                double seconds = Math.min(0.04, (now - previous) / 1_000_000_000.0);
                previous = now;

                gameLoop(seconds);
            }
        }.start();
    }

    // ==========================================
    // HUD & INTERACTION UI
    // ==========================================
    private void buildHUD() {
        hudLayer.getChildren().clear();

        // Top HUD Bar background
        Rectangle bar = new Rectangle(0, 0, WIDTH, 42);
        bar.setFill(Color.web("#14161d", 0.88));
        bar.setStroke(Color.web("#363a4d"));
        bar.setStrokeWidth(1.5);

        // Stats Container
        HBox stats = new HBox(25);
        stats.setLayoutX(16);
        stats.setLayoutY(11);
        stats.setAlignment(Pos.CENTER_LEFT);

        coinsText = new Text("💰 Coins: ₹" + coins);
        coinsText.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        coinsText.setFill(Color.web("#ffd166"));

        chocolatesText = new Text("🍫 Chocolates: " + chocolates);
        chocolatesText.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        chocolatesText.setFill(Color.web("#f4a261"));

        boostText = new Text("⚡ Speed Boost: --");
        boostText.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        boostText.setFill(Color.web("#06d6a0"));

        stats.getChildren().addAll(coinsText, chocolatesText, boostText);

        // Controls Helper Text
        Text controls = new Text("[WASD/Arrows] Move   [SPACE] Jump   [E] Buy   [C] Eat");
        controls.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        controls.setFill(Color.web("#a0aec0"));
        controls.setLayoutX(WIDTH - 530);
        controls.setLayoutY(26);

        // Restart Button with thematic 8/16-bit pixel loop icon
        Button restartBtn = createRestartButton();
        restartBtn.setLayoutX(WIDTH - 105);
        restartBtn.setLayoutY(5);

        // Toast Notification Banner (Centered near top)
        toastText = new Text("");
        toastText.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        toastText.setFill(Color.web("#ffffff"));
        toastText.setVisible(false);

        // Interaction Prompt (for buying)
        interactionPrompt = new Text("");
        interactionPrompt.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        interactionPrompt.setFill(Color.web("#ffe66d"));
        DropShadow glow = new DropShadow(8, Color.web("#000000", 0.9));
        interactionPrompt.setEffect(glow);
        interactionPrompt.setVisible(false);

        hudLayer.getChildren().addAll(bar, stats, controls, restartBtn, toastText, interactionPrompt);
    }

    private void showToast(String message, Color color) {
        toastText.setText(message);
        toastText.setFill(color);
        toastText.setLayoutX(WIDTH / 2.0 - (message.length() * 4.2));
        toastText.setLayoutY(75);
        toastText.setVisible(true);
        toastTimer = 2.5;
    }

    private void buyChocolate() {
        if (coins >= 20) {
            coins -= 20;
            chocolates++;
            showToast("✨ Bought 1 Delicious Dairy Milk! (-₹20)", Color.web("#48cae4"));
            updateHUDStats();
        } else {
            showToast("❌ Not enough coins! Need ₹20.", Color.web("#ef476f"));
        }
    }

    private void eatChocolate() {
        if (chocolates > 0) {
            chocolates--;
            sugarRushTimer = 6.0; // 6 seconds of speed boost
            showToast("🍫 Chomp chomp! SUGAR RUSH ACTIVATED! ⚡ (+60% Speed)", Color.web("#ffb703"));
            spawnSparkles(playerX + PLAYER_SIZE / 2, playerY + PLAYER_SIZE / 2);
            updateHUDStats();
        } else {
            showToast("You don't have any chocolates! Visit the Safal Shop.", Color.web("#f77f00"));
        }
    }

    private void updateHUDStats() {
        coinsText.setText("💰 Coins: ₹" + coins);
        chocolatesText.setText("🍫 Chocolates: " + chocolates);
        if (sugarRushTimer > 0) {
            boostText.setText(String.format("⚡ Speed Boost: %.1fs", sugarRushTimer));
            boostText.setFill(Color.web("#06d6a0"));
        } else {
            boostText.setText("⚡ Speed Boost: --");
            boostText.setFill(Color.web("#718096"));
        }
    }

    private void spawnSparkles(double x, double y) {
        for (int i = 0; i < 10; i++) {
            Circle spark = new Circle(x, y, 4 + Math.random() * 4, Color.web(i % 2 == 0 ? "#ffd166" : "#06d6a0"));
            mapLayer.getChildren().add(spark);
            double angle = Math.random() * 2 * Math.PI;
            double dist = 25 + Math.random() * 35;
            double targetX = x + Math.cos(angle) * dist;
            double targetY = y + Math.sin(angle) * dist;

            javafx.animation.TranslateTransition tt = new javafx.animation.TranslateTransition(Duration.millis(500),
                    spark);
            tt.setToX(targetX - x);
            tt.setToY(targetY - y);

            FadeTransition ft = new FadeTransition(Duration.millis(500), spark);
            ft.setFromValue(1.0);
            ft.setToValue(0.0);
            ft.setOnFinished(e -> mapLayer.getChildren().remove(spark));

            tt.play();
            ft.play();
        }
    }

    // ==========================================
    // ROOM LOADING & TRANSITIONS
    // ==========================================
    private void loadRoom(int nextRoom, int tileX, int tileY) {
        currentRoom = nextRoom;
        walkable = new boolean[ROWS][COLS];
        exits.clear();
        balls.clear();
        mapLayer.getChildren().clear();
        transitionCooldown = 0.6; // Cooldown prevents instant accidental re-triggering
        onSwing = false;
        leftSwingPhase = 0.0;
        leftSwingPeak = 0.0;
        rightSwingPhase = 0.0;
        rightSwingPeak = 0.0;

        // Fill background
        if (currentRoom == 0) {
            fillPlaygroundGrass(mapLayer);
            buildPlayground(mapLayer);
        } else {
            fillShopTiles(mapLayer);
            buildSafalShop(mapLayer);
        }

        // Create player shadow and sprite
        playerShadow = new Ellipse(tileX * TILE + PLAYER_SIZE / 2, tileY * TILE + PLAYER_SIZE - 2, 16, 7);
        playerShadow.setFill(Color.web("#000000", 0.45));
        mapLayer.getChildren().add(playerShadow);

        player = sprite(hero, tileX * TILE + 3, tileY * TILE + 3, PLAYER_SIZE);
        playerX = player.getLayoutX();
        playerY = player.getLayoutY();
        jumpZ = 0;
        jumpVelZ = 0;
        isJumping = false;
        mapLayer.getChildren().add(player);

        // Create Bunty only in the playground
        if (currentRoom == 0) {

            buntyX = 8.0 * TILE;
            buntyY = 6.0 * TILE;

            buntyTargetX = buntyX;
            buntyTargetY = buntyY;

            buntyPauseTimer = 1.0;
            buntyMoving = false;

            // Create Bunty with his own character skin
            bunty = sprite(buntyHero, buntyX, buntyY, PLAYER_SIZE);

            bunty.setFitWidth(36);
            bunty.setFitHeight(36);

            // Create Bunty's name tag
            buntyNameTag = new Label("Bunty");

            buntyNameTag.setStyle(
                    "-fx-background-color: #ffffff;" +
                            "-fx-text-fill: #263238;" +
                            "-fx-font-size: 11px;" +
                            "-fx-font-weight: bold;" +
                            "-fx-background-radius: 8px;" +
                            "-fx-padding: 3px 7px;");

            // Add character and name tag
            mapLayer.getChildren().addAll(bunty, buntyNameTag);

        } else {
            bunty = null;
            buntyNameTag = null;
        }

        // Add swing hands overlay on top of player in playground
        if (currentRoom == 0 && swingHandsGroup != null) {
            swingHandsGroup.setVisible(false);
            mapLayer.getChildren().add(swingHandsGroup);
        }

        // Add ball nodes to mapLayer if in playground
        for (PlaygroundBall b : balls) {
            mapLayer.getChildren().addAll(b.shadow, b.renderGroup);
        }

        updateHUDStats();
    }

    // bunty activity selection
    private void chooseBuntyActivity() {

        int choice = (int) (Math.random() * 3);

        if (choice == 0) {
            // Choose a random swing
            buntyChosenSwing = Math.random() < 0.5 ? 0 : 1;
            buntyTargetX = swingX + (buntyChosenSwing == 0 ? 45 : 105);
            buntyTargetY = swingY + 90;
            buntyActivity = BuntyActivity.GOING_TO_SWING;

        } else if (choice == 1) {
            // Go to the see-saw
            buntyTargetX = seeSawX + 36;
            buntyTargetY = seeSawY + 65;
            buntyActivity = BuntyActivity.GOING_TO_SEESAW;

        } else {
            // Find a ball to play with
            if (balls.isEmpty()) {
                buntyActivity = BuntyActivity.WANDERING;
                buntyPauseTimer = 1.5;
                return;
            }

            buntyTargetBall = balls.get(
                    (int) (Math.random() * balls.size()));

            buntyTargetX = buntyTargetBall.x;
            buntyTargetY = buntyTargetBall.y;

            buntyActivity = BuntyActivity.GOING_TO_BALL;
        }

        // Start moving towards the selected activity
        buntyMoving = true;
    }

    // ==========================================
    // FRAME 0: PLAYGROUND GENERATION
    // ==========================================
    private void fillPlaygroundGrass(Pane map) {
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                map.getChildren().add(sprite(grass, x * TILE, y * TILE, TILE));
                // Grass is generally walkable in the playground!
                walkable[y][x] = true;
            }
        }
    }

    private void buildPlayground(Pane map) {
        // Perimeter wooden playground fence
        for (int x = 0; x < COLS; x++) {
            if (x < 18 || x > 19) { // Leave gap at the east exit
                fencePost(map, x * TILE, 0);
                walkable[0][x] = false;
            }
            fencePost(map, x * TILE, (ROWS - 1) * TILE + 28);
            walkable[ROWS - 1][x] = false;
        }
        for (int y = 1; y < ROWS - 1; y++) {
            fencePost(map, 0, y * TILE);
            walkable[y][0] = false;
            if (y < 7 || y > 10) { // Opening on east side for Safal shop
                fencePost(map, (COLS - 1) * TILE + 28, y * TILE);
                walkable[y][COLS - 1] = false;
            }
        }

        // Paved Stone Pathways leading through the park
        for (int x = 1; x < COLS - 1; x++) {
            roadTile(map, x, 8);
            roadTile(map, x, 9);
        }
        for (int y = 2; y < 8; y++) {
            roadTile(map, 6, y);
            roadTile(map, 14, y);
        }

        // 1. Dual Swings Set (Top Left: cols 1..4, rows 1..3)
        drawSwings(map, 1.5 * TILE, 1.5 * TILE);

        // 2. Sand Pod / Sand Pit (Center Top: cols 8..12, rows 1..5)
        drawSandPit(map, 8 * TILE, 1.5 * TILE, 5 * TILE, 4 * TILE);

        // 3. Playground Slide (Top Right: cols 15..18, rows 1..4)
        drawSlide(map, 15.5 * TILE, 1.5 * TILE);

        // 4. See-Saw (Bottom Left: cols 2..5, rows 10..11)
        drawSeeSaw(map, 2.5 * TILE, 10.2 * TILE);

        // 5. Merry-Go-Round / Spinner (Bottom Center: cols 10..12, rows 10..11)
        drawRoundabout(map, 11 * TILE, 10.5 * TILE);

        // Benches, Lamps & Greenery
        drawBench(map, 5 * TILE, 7 * TILE + 8);
        drawBench(map, 13 * TILE, 7 * TILE + 8);
        drawLamp(map, 6 * TILE - 12, 7 * TILE);
        drawLamp(map, 14 * TILE + 20, 7 * TILE);

        // Trees & Shrubs
        tree(map, 0, 4);
        tree(map, 0, 6);
        tree(map, 7, 1);
        tree(map, 13, 1);
        shrub(map, 5, 6);
        shrub(map, 13, 6);
        shrub(map, 8, 10);
        shrub(map, 14, 10);

        // Add 2 Real-Physics Balls
        // Ball 1: Classic Soccer Ball
        PlaygroundBall soccer = new PlaygroundBall(8.5 * TILE, 7.5 * TILE, 13, true, Color.web("#f8f9fa"),
                Color.web("#212529"));
        // Ball 2: Bouncy Red Playground Dodgeball
        PlaygroundBall dodgeball = new PlaygroundBall(11.5 * TILE, 7.8 * TILE, 14, false, Color.web("#e63946"),
                Color.web("#a81d28"));
        balls.add(soccer);
        balls.add(dodgeball);

        // Exit Arrow leading to Safal Shop (East side, row 8 & 9)
        arrow(map, 19, 8, Direction.EAST, "SAFAL SHOP →");
        exits.add(new Exit(19, 8, Direction.EAST, 1, 4, 9)); // Spawn safely at col 4, row 9 in Safal Shop
        arrow(map, 19, 9, Direction.EAST, "SAFAL SHOP →");
        exits.add(new Exit(19, 9, Direction.EAST, 1, 4, 9));
    }

    private void fencePost(Pane map, double x, double y) {
        Rectangle post = new Rectangle(x, y, 16, 20);
        post.setFill(Color.web("#a06a42"));
        post.setStroke(Color.web("#58361e"));
        post.setStrokeWidth(1.5);
        post.setArcWidth(4);
        post.setArcHeight(4);
        map.getChildren().add(post);
    }

    // ==========================================
    // BUNTY NPC MOVEMENT

    private boolean moveBuntyToward(double seconds) {

        double dx = buntyTargetX - buntyX;
        double dy = buntyTargetY - buntyY;

        double distance = Math.hypot(dx, dy);

        // Destination reached
        if (distance < 8) {
            buntyMoving = false;
            return true;
        }

        // Calculate movement direction
        double moveX = dx / distance;
        double moveY = dy / distance;

        double movement = Math.min(buntySpeed * seconds, distance);

        buntyX += moveX * movement;
        buntyY += moveY * movement;

        bunty.setLayoutX(buntyX);
        bunty.setLayoutY(buntyY);

        // Face the direction of movement
        if (Math.abs(moveX) > 0.1) {
            bunty.setScaleX(moveX < 0 ? -1 : 1);
        }

        // Update name tag
        if (buntyNameTag != null) {
            buntyNameTag.setLayoutX(
                    buntyX + 18 - buntyNameTag.getWidth() / 2);
            buntyNameTag.setLayoutY(buntyY - 18);
        }

        return false;
    }

    // ==========================================
    private void updateBunty(double seconds) {

        if (bunty == null || currentRoom != 0) {
            return;
        }

        buntyActivityTimer -= seconds;

        switch (buntyActivity) {

            case WANDERING:

                // Join the player on the see-saw
                if (onSeeSaw && !buntyUsingSeeSaw &&
                        !buntyRespondedToPlayerSeeSaw) {

                    buntyRespondedToPlayerSeeSaw = true;

                    buntyTargetX = seeSawX + 36;
                    buntyTargetY = seeSawY + 65;

                    buntyMoving = true;
                    buntyActivity = BuntyActivity.GOING_TO_SEESAW;
                    break;
                }

                // Allow Bunty to join again after the player leaves
                if (!onSeeSaw) {
                    buntyRespondedToPlayerSeeSaw = false;
                }

                if (!buntyMoving) {
                    buntyPauseTimer -= seconds;

                    if (buntyPauseTimer <= 0) {
                        chooseBuntyActivity();
                    }
                } else {
                    if (moveBuntyToward(seconds)) {
                        buntyPauseTimer = 1.0 + Math.random() * 2.0;
                        buntyActivityTimer = 0;
                    }
                }
                break;

            case GOING_TO_SWING:

            case GOING_TO_SEESAW:

            case GOING_TO_BALL:

                // Avoid the swing currently occupied by the player
                if (buntyActivity == BuntyActivity.GOING_TO_SWING &&
                        onSwing && activeSwing == buntyChosenSwing) {

                    buntyChosenSwing = buntyChosenSwing == 0 ? 1 : 0;

                    buntyTargetX = swingX +
                            (buntyChosenSwing == 0 ? 45 : 105);

                    buntyTargetY = swingY + 90;
                }
                if (buntyActivity == BuntyActivity.GOING_TO_BALL &&
                        buntyTargetBall != null) {

                    buntyTargetX = buntyTargetBall.x;
                    buntyTargetY = buntyTargetBall.y;
                }

                if (moveBuntyToward(seconds)) {

                    buntyMoving = false;
                    buntyActivityTimer = 3.0 + Math.random() * 3.0;

                    if (buntyActivity == BuntyActivity.GOING_TO_SWING) {
                        buntyUsingSwing = true;
                        buntyActivity = BuntyActivity.USING_SWING;

                    } else if (buntyActivity == BuntyActivity.GOING_TO_SEESAW) {
                        buntyUsingSeeSaw = true;
                        buntyActivity = BuntyActivity.USING_SEESAW;

                    } else {
                        buntyActivity = BuntyActivity.PLAYING_BALL;
                    }
                }
                break;

            case USING_SWING:

                double swingAnchorX = swingX +
                        (buntyChosenSwing == 0 ? 45 : 105);

                double swingPhase = buntyChosenSwing == 0
                        ? leftSwingPhase
                        : rightSwingPhase;

                double swingPeak = buntyChosenSwing == 0
                        ? leftSwingPeak
                        : rightSwingPeak;

                double seatY = swingY + 66 +
                        Math.sin(swingPhase) * swingPeak * 24.0;

                buntyX = swingAnchorX - 18;
                buntyY = seatY - 32;

                bunty.setLayoutX(buntyX);
                bunty.setLayoutY(buntyY);

                if (buntyNameTag != null) {
                    buntyNameTag.setLayoutX(
                            buntyX + 18 - buntyNameTag.getWidth() / 2);
                    buntyNameTag.setLayoutY(buntyY - 18);
                }

                if (buntyActivityTimer <= 0) {

                    buntyUsingSwing = false;

                    buntyActivity = BuntyActivity.WANDERING;
                    buntyPauseTimer = 1.0;
                }
                break;

            case USING_SEESAW:

                if (!seeSawPlankGroup.getChildren().contains(bunty)) {

                    mapLayer.getChildren().remove(bunty);
                    seeSawPlankGroup.getChildren().add(bunty);

                    bunty.setLayoutX(51);
                    bunty.setLayoutY(-27);
                }

                if (buntyNameTag != null) {

                    javafx.geometry.Point2D buntyPosition = mapLayer.sceneToLocal(bunty.localToScene(0, 0));

                    buntyNameTag.setLayoutX(
                            buntyPosition.getX() + 18
                                    - buntyNameTag.getWidth() / 2);

                    buntyNameTag.setLayoutY(
                            buntyPosition.getY() - 18);
                }

                if (buntyActivityTimer <= 0 && !onSeeSaw) {

                    buntyUsingSeeSaw = false;

                    seeSawPlankGroup.getChildren().remove(bunty);
                    mapLayer.getChildren().add(bunty);

                    buntyX = seeSawX + 90;
                    buntyY = seeSawY + 40;

                    bunty.setLayoutX(buntyX);
                    bunty.setLayoutY(buntyY);

                    buntyActivity = BuntyActivity.WANDERING;
                    buntyPauseTimer = 1.0;
                }
                break;

            case PLAYING_BALL:

                if (buntyTargetBall == null ||
                        !balls.contains(buntyTargetBall)) {

                    buntyTargetBall = null;
                    buntyMoving = false;
                    buntyActivity = BuntyActivity.WANDERING;
                    buntyPauseTimer = 1.0;
                    break;
                }

                buntyBallKickCooldown -= seconds;

                // Follow the moving ball
                buntyTargetX = buntyTargetBall.x - 18;
                buntyTargetY = buntyTargetBall.y - 18;

                double dx = buntyTargetBall.x - (buntyX + 18);
                double dy = buntyTargetBall.y - (buntyY + 18);

                double distance = Math.hypot(dx, dy);

                if (distance > 28) {

                    buntyMoving = true;
                    moveBuntyToward(seconds);

                } else {

                    buntyMoving = false;

                    // Kick the ball periodically
                    if (buntyBallKickCooldown <= 0) {

                        if (distance > 0) {
                            buntyTargetBall.vx += (dx / distance) * 180;
                            buntyTargetBall.vy += (dy / distance) * 180;
                        }

                        buntyBallKickCooldown = 0.9;
                    }
                }

                // Finish this play session
                if (buntyActivityTimer <= 0) {

                    buntyTargetBall = null;
                    buntyMoving = false;

                    buntyActivity = BuntyActivity.WANDERING;
                    buntyPauseTimer = 1.5;
                }

                break;
        }
    }

    private void toggleSeeSaw() {

        double px = playerX + PLAYER_SIZE / 2.0;
        double py = playerY + PLAYER_SIZE / 2.0;

        // Check whether the player is close enough to the see-saw
        boolean nearSeeSaw = Math.abs(px - (seeSawX + 36)) < 55 &&
                Math.abs(py - (seeSawY + 20)) < 55;

        // MOUNT THE SEE-SAW
        if (!onSeeSaw && nearSeeSaw) {

            onSeeSaw = true;
            seeSawPhase = 0.0;

            // Stop jumping when seated
            isJumping = false;
            jumpVelZ = 0;

            // Remove player from the normal map layer
            mapLayer.getChildren().remove(player);

            // Add player to the rotating plank group
            if (!seeSawPlankGroup.getChildren().contains(player)) {
                seeSawPlankGroup.getChildren().add(player);
            }

            // Position player on the left seat
            player.setLayoutX(-15);
            player.setLayoutY(-27);

            player.setScaleX(1.0);
            player.setScaleY(1.0);
            player.setVisible(true);

            if (playerShadow != null) {
                playerShadow.setVisible(false);
            }

            showToast(
                    "You sat on the see-saw!",
                    Color.web("#06d6a0"));
        }

        // DISMOUNT THE SEE-SAW
        else if (onSeeSaw) {

            onSeeSaw = false;

            // Remove player from the rotating group
            seeSawPlankGroup.getChildren().remove(player);

            // Return player to the normal map layer
            if (!mapLayer.getChildren().contains(player)) {
                mapLayer.getChildren().add(player);
            }

            // Reset player position beside the see-saw
            playerX = seeSawX + 90;
            playerY = seeSawY;

            player.setLayoutX(playerX);
            player.setLayoutY(playerY);

            player.setScaleX(playerFacing);
            player.setScaleY(1.0);

            // Small hop after dismounting
            jumpVelZ = 240;
            isJumping = true;

            if (playerShadow != null) {
                playerShadow.setVisible(true);
            }

            showToast(
                    "Hopped off the see-saw!",
                    Color.web("#ffd166"));
        }
    }

    private void drawSwings(Pane map, double x, double y) {
        swingX = x;
        swingY = y;

        // Ground Shadows under the swings (both swings have the exact same drop shadow)
        double shadowY = y + 66 + 16;
        leftSeatShadow = new Ellipse(x + 45, shadowY, 16, 5);
        leftSeatShadow.setFill(Color.web("#000000", 0.35));

        rightSeatShadow = new Ellipse(x + 105, shadowY, 16, 5);
        rightSeatShadow.setFill(Color.web("#000000", 0.35));

        map.getChildren().addAll(leftSeatShadow, rightSeatShadow);

        Group swingSet = new Group();

        // Swing A-frame posts (Left and Right) with wooden/green posts matching user's
        // sketch
        // Left A-frame legs
        Polygon leftA1 = new Polygon(x + 4, y + 4, x - 8, y + 92, x, y + 92, x + 8, y + 4);
        leftA1.setFill(Color.web("#3d6a4a"));
        leftA1.setStroke(Color.web("#23402c"));
        leftA1.setStrokeWidth(1);

        Polygon leftA2 = new Polygon(x + 8, y + 4, x + 20, y + 92, x + 28, y + 92, x + 12, y + 4);
        leftA2.setFill(Color.web("#4a7c59"));
        leftA2.setStroke(Color.web("#23402c"));
        leftA2.setStrokeWidth(1);

        Rectangle leftBar = new Rectangle(x - 2, y + 54, 26, 5);
        leftBar.setFill(Color.web("#2b4a34"));

        // Right A-frame legs
        Polygon rightA1 = new Polygon(x + 138, y + 4, x + 126, y + 92, x + 134, y + 92, x + 142, y + 4);
        rightA1.setFill(Color.web("#3d6a4a"));
        rightA1.setStroke(Color.web("#23402c"));
        rightA1.setStrokeWidth(1);

        Polygon rightA2 = new Polygon(x + 142, y + 4, x + 154, y + 92, x + 162, y + 92, x + 146, y + 4);
        rightA2.setFill(Color.web("#4a7c59"));
        rightA2.setStroke(Color.web("#23402c"));
        rightA2.setStrokeWidth(1);

        Rectangle rightBar = new Rectangle(x + 132, y + 54, 26, 5);
        rightBar.setFill(Color.web("#2b4a34"));

        // Top crossbar
        Rectangle topBar = new Rectangle(x - 12, y - 2, 174, 9);
        topBar.setFill(Color.web("#203a27"));
        topBar.setStroke(Color.web("#142418"));
        topBar.setStrokeWidth(1);
        topBar.setArcWidth(4);
        topBar.setArcHeight(4);

        swingSet.getChildren().addAll(leftA1, leftA2, leftBar, rightA1, rightA2, rightBar, topBar);
        map.getChildren().add(swingSet);

        // Left Swing Ropes (chains)
        leftRope1 = new Line(x + 33, y + 7, x + 33, y + 66);
        leftRope1.setStroke(Color.web("#d1d5db"));
        leftRope1.setStrokeWidth(2.2);

        leftRope2 = new Line(x + 57, y + 7, x + 57, y + 66);
        leftRope2.setStroke(Color.web("#d1d5db"));
        leftRope2.setStrokeWidth(2.2);

        // Left Swing 3D Seat (Red)
        leftSeatGroup = new Group();
        Rectangle seatLTop = new Rectangle(-15, -4, 30, 6);
        seatLTop.setFill(Color.web("#e63946"));
        seatLTop.setStroke(Color.web("#ba181b"));
        seatLTop.setStrokeWidth(1);
        seatLTop.setArcWidth(3);
        seatLTop.setArcHeight(3);

        Rectangle seatLFront = new Rectangle(-15, 2, 30, 4);
        seatLFront.setFill(Color.web("#800f2f"));
        seatLFront.setArcWidth(2);
        seatLFront.setArcHeight(2);

        Circle bracketL1 = new Circle(-12, -1, 2, Color.web("#f1faee"));
        Circle bracketL2 = new Circle(12, -1, 2, Color.web("#f1faee"));
        leftSeatGroup.getChildren().addAll(seatLTop, seatLFront, bracketL1, bracketL2);
        leftSeatGroup.setLayoutX(x + 45);
        leftSeatGroup.setLayoutY(y + 66);

        // Right Swing Ropes (chains)
        rightRope1 = new Line(x + 93, y + 7, x + 93, y + 66);
        rightRope1.setStroke(Color.web("#d1d5db"));
        rightRope1.setStrokeWidth(2.2);

        rightRope2 = new Line(x + 117, y + 7, x + 117, y + 66);
        rightRope2.setStroke(Color.web("#d1d5db"));
        rightRope2.setStrokeWidth(2.2);

        // Right Swing 3D Seat (Blue)
        rightSeatGroup = new Group();
        Rectangle seatRTop = new Rectangle(-15, -4, 30, 6);
        seatRTop.setFill(Color.web("#0077b6"));
        seatRTop.setStroke(Color.web("#023e8a"));
        seatRTop.setStrokeWidth(1);
        seatRTop.setArcWidth(3);
        seatRTop.setArcHeight(3);

        Rectangle seatRFront = new Rectangle(-15, 2, 30, 4);
        seatRFront.setFill(Color.web("#03045e"));
        seatRFront.setArcWidth(2);
        seatRFront.setArcHeight(2);

        Circle bracketR1 = new Circle(-12, -1, 2, Color.web("#f1faee"));
        Circle bracketR2 = new Circle(12, -1, 2, Color.web("#f1faee"));
        rightSeatGroup.getChildren().addAll(seatRTop, seatRFront, bracketR1, bracketR2);
        rightSeatGroup.setLayoutX(x + 105);
        rightSeatGroup.setLayoutY(y + 66);

        map.getChildren().addAll(leftRope1, leftRope2, rightRope1, rightRope2, leftSeatGroup, rightSeatGroup);

        // Hand grips for seated character
        leftHandL = new Circle(0, 0, 3.5, Color.web("#f3be76"));
        leftHandL.setStroke(Color.web("#8b5a2b"));
        leftHandL.setStrokeWidth(1);

        leftHandR = new Circle(0, 0, 3.5, Color.web("#f3be76"));
        leftHandR.setStroke(Color.web("#8b5a2b"));
        leftHandR.setStrokeWidth(1);

        swingHandsGroup = new Group(leftHandL, leftHandR);
        swingHandsGroup.setVisible(false);

        // Block walkable grid under the swing structure
        int startCol = (int) (x / TILE);
        int startRow = (int) (y / TILE);
        for (int r = startRow; r <= startRow + 1; r++) {
            for (int c = startCol; c <= startCol + 2; c++) {
                if (r < ROWS && c < COLS)
                    walkable[r][c] = false;
            }
        }
    }

    private void drawSandPit(Pane map, double x, double y, double w, double h) {
        // Wooden rim
        Rectangle rim = new Rectangle(x, y, w, h);
        rim.setFill(Color.web("#b07d4b"));
        rim.setStroke(Color.web("#754b24"));
        rim.setStrokeWidth(4);
        rim.setArcWidth(10);
        rim.setArcHeight(10);

        // Sand fill
        Rectangle sand = new Rectangle(x + 6, y + 6, w - 12, h - 12);
        sand.setFill(Color.web("#edd892"));

        // Texture specks
        Group specks = new Group();
        for (int i = 0; i < 20; i++) {
            Circle dot = new Circle(x + 15 + (i * 27) % (w - 30), y + 15 + (i * 39) % (h - 30), 2,
                    Color.web("#d1ba75"));
            specks.getChildren().add(dot);
        }

        // Toy Bucket and Shovel inside sand pit
        Circle bucket = new Circle(x + w * 0.35, y + h * 0.45, 9, Color.web("#e63946"));
        bucket.setStroke(Color.web("#800f2f"));
        Rectangle shovelHandle = new Rectangle(x + w * 0.65, y + h * 0.5, 3, 16);
        shovelHandle.setFill(Color.web("#ffb703"));
        Rectangle shovelHead = new Rectangle(x + w * 0.65 - 4, y + h * 0.5 + 12, 11, 8);
        shovelHead.setFill(Color.web("#fb8500"));

        Text sign = new Text("SAND POD");
        sign.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        sign.setFill(Color.web("#8c5a2b"));
        sign.setLayoutX(x + w / 2.0 - 30);
        sign.setLayoutY(y + 20);

        map.getChildren().addAll(rim, sand, specks, bucket, shovelHandle, shovelHead, sign);
    }

    private void drawSlide(Pane map, double x, double y) {
        Group slide = new Group();

        // Tower ladder / platform
        Rectangle platform = new Rectangle(x, y, 28, 70);
        platform.setFill(Color.web("#3a86ff"));
        platform.setStroke(Color.web("#1e40af"));
        platform.setStrokeWidth(2);

        // Ladder rungs
        for (int i = 12; i < 65; i += 12) {
            Line rung = new Line(x + 3, y + i, x + 25, y + i);
            rung.setStroke(Color.web("#ffd166"));
            rung.setStrokeWidth(3);
            slide.getChildren().add(rung);
        }

        // Yellow chute extending downwards
        Polygon chute = new Polygon(
                x + 28, y + 10,
                x + 75, y + 80,
                x + 65, y + 84,
                x + 28, y + 22);
        chute.setFill(Color.web("#ffb703"));
        chute.setStroke(Color.web("#fb8500"));
        chute.setStrokeWidth(2);

        // Landing soft mat
        Rectangle mat = new Rectangle(x + 60, y + 78, 30, 16);
        mat.setFill(Color.web("#06d6a0"));
        mat.setArcWidth(4);
        mat.setArcHeight(4);

        slide.getChildren().addAll(platform, chute, mat);
        map.getChildren().add(slide);

        int sc = (int) (x / TILE);
        int sr = (int) (y / TILE);
        if (sr < ROWS && sc < COLS)
            walkable[sr][sc] = false;
        if (sr + 1 < ROWS && sc < COLS)
            walkable[sr + 1][sc] = false;
    }

    private void drawSeeSaw(Pane map, double x, double y) {

        // Keep the coordinates synchronized with the actual drawing position
        seeSawX = x;
        seeSawY = y;

        // Fulcrum (does not rotate)
        Polygon base = new Polygon(
                x + 35, y + 16,
                x + 25, y + 36,
                x + 45, y + 36);
        base.setFill(Color.web("#495057"));

        // Create a group for everything that should rotate
        seeSawPlankGroup = new Group();

        // Main plank
        Rectangle plank = new Rectangle(0, 20, 72, 7);
        plank.setFill(Color.web("#d90429"));

        // Two seats
        Rectangle leftSeat = new Rectangle(3, 13, 18, 7);
        leftSeat.setFill(Color.web("#8d0801"));

        Rectangle rightSeat = new Rectangle(51, 13, 18, 7);
        rightSeat.setFill(Color.web("#8d0801"));

        // Handles
        Circle h1 = new Circle(8, 12, 4, Color.web("#ffd166"));
        Circle h2 = new Circle(64, 12, 4, Color.web("#ffd166"));

        seeSawPlankGroup.getChildren().addAll(
                plank,
                leftSeat,
                rightSeat,
                h1,
                h2);

        // Position the rotating group at the see-saw's world coordinates
        seeSawPlankGroup.setLayoutX(x);
        seeSawPlankGroup.setLayoutY(y);

        // Add the stationary base and rotating plank group to the map
        map.getChildren().addAll(base, seeSawPlankGroup);
    }

    private void drawRoundabout(Pane map, double cx, double cy) {
        Circle base = new Circle(cx, cy, 28, Color.web("#06d6a0"));
        base.setStroke(Color.web("#073b4c"));
        base.setStrokeWidth(3);

        Circle inner = new Circle(cx, cy, 14, Color.web("#ffd166"));
        Line bar1 = new Line(cx - 24, cy, cx + 24, cy);
        bar1.setStroke(Color.web("#ef476f"));
        bar1.setStrokeWidth(3);
        Line bar2 = new Line(cx, cy - 24, cx, cy + 24);
        bar2.setStroke(Color.web("#ef476f"));
        bar2.setStrokeWidth(3);

        map.getChildren().addAll(base, inner, bar1, bar2);
    }

    private void drawBench(Pane map, double x, double y) {
        Rectangle seat = new Rectangle(x, y, 42, 10);
        seat.setFill(Color.web("#7d5239"));
        seat.setStroke(Color.web("#4d3a32"));
        seat.setStrokeWidth(2);
        Rectangle back = new Rectangle(x, y - 8, 42, 6);
        back.setFill(Color.web("#c18a53"));
        map.getChildren().addAll(seat, back);
    }

    private void drawLamp(Pane map, double x, double y) {
        Rectangle pole = new Rectangle(x + 4, y, 4, 34);
        pole.setFill(Color.web("#343a40"));
        Circle lantern = new Circle(x + 6, y - 2, 7, Color.web("#fff3b0"));
        lantern.setStroke(Color.web("#343a40"));
        lantern.setStrokeWidth(2);
        map.getChildren().addAll(pole, lantern);
    }

    // ==========================================
    // FRAME 1: SAFAL SHOP GENERATION
    // ==========================================
    private void fillShopTiles(Pane map) {
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                Rectangle tile = new Rectangle(x * TILE, y * TILE, TILE, TILE);
                tile.setFill(Color.web((x + y) % 2 == 0 ? "#edf2f7" : "#e2e8f0"));
                tile.setStroke(Color.web("#cbd5e0"));
                tile.setStrokeWidth(0.5);
                map.getChildren().add(tile);
                walkable[y][x] = true;
            }
        }
    }

    private void buildSafalShop(Pane map) {
        // Perimeter Shop Walls
        for (int x = 0; x < COLS; x++) {
            wallBlock(map, x, 0);
            walkable[0][x] = false;
            wallBlock(map, x, ROWS - 1);
            walkable[ROWS - 1][x] = false;
        }
        for (int y = 1; y < ROWS - 1; y++) {
            wallBlock(map, 0, y);
            walkable[y][0] = false;
            wallBlock(map, COLS - 1, y);
            walkable[y][COLS - 1] = false;
        }

        // Iconic Green "SAFAL" Brand Board (Top Wall center)
        Rectangle signBg = new Rectangle(6 * TILE, 8, 8 * TILE, 46);
        signBg.setFill(Color.web("#2d6a4f")); // Safal iconic dark green
        signBg.setStroke(Color.web("#d8f3dc"));
        signBg.setStrokeWidth(3);
        signBg.setArcWidth(8);
        signBg.setArcHeight(8);

        Text signText = new Text("SAFAL CONVENIENCE & CHOCOLATES");
        signText.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
        signText.setFill(Color.web("#ffffff"));
        signText.setLayoutX(6 * TILE + 28);
        signText.setLayoutY(37);

        map.getChildren().addAll(signBg, signText);

        // Shopkeeper Counter (cols 6..10, row 3)
        for (int col = 6; col <= 10; col++) {
            drawCounterDesk(map, col * TILE, 3 * TILE);
            walkable[3][col] = false;
        }

        // Shopkeeper NPC ("Kaka") behind the counter
        drawShopkeeper(map, 8 * TILE + 6, 2 * TILE + 4);
        walkable[2][8] = false;

        // Big Chocolate Display Shelves (Right side: cols 13..17, rows 2..5)
        drawChocolateRacks(map, 13 * TILE, 2 * TILE, 5 * TILE, 4 * TILE);
        for (int r = 2; r <= 5; r++) {
            for (int c = 14; c <= 17; c++) {
                walkable[r][c] = false;
            }
        }

        // Fruit & Veggie crates (Left side: cols 1..3, rows 2..5)
        drawProduceSection(map, 1 * TILE + 10, 2 * TILE);
        for (int r = 2; r <= 5; r++) {
            for (int c = 1; c <= 3; c++) {
                walkable[r][c] = false;
            }
        }

        // Decorative Floor Mats / Rugs
        Rectangle rug = new Rectangle(6 * TILE, 5 * TILE, 5 * TILE, 2 * TILE);
        rug.setFill(Color.web("#52b788", 0.35));
        rug.setStroke(Color.web("#2d6a4f"));
        rug.setStrokeWidth(1.5);
        map.getChildren().add(rug);

        // Return Exit Arrow (Bottom Left: col 1, row 9 & 10)
        arrow(map, 1, 9, Direction.WEST, "← PLAYGROUND");
        exits.add(new Exit(1, 9, Direction.WEST, 0, 16, 8)); // Spawn safely at col 16, row 8 in Playground
        arrow(map, 1, 10, Direction.WEST, "← PLAYGROUND");
        exits.add(new Exit(1, 10, Direction.WEST, 0, 16, 9));
    }

    private void wallBlock(Pane map, int col, int row) {
        Rectangle w = new Rectangle(col * TILE, row * TILE, TILE, TILE);
        w.setFill(Color.web("#374151"));
        w.setStroke(Color.web("#1f2937"));
        w.setStrokeWidth(2);
        map.getChildren().add(w);
    }

    private void drawCounterDesk(Pane map, double x, double y) {
        Rectangle desk = new Rectangle(x, y, TILE, TILE);
        desk.setFill(Color.web("#8b5a2b"));
        desk.setStroke(Color.web("#58361e"));
        desk.setStrokeWidth(2);

        // Cash register on center tile
        if (x == 8 * TILE) {
            Rectangle register = new Rectangle(x + 10, y + 8, 28, 22);
            register.setFill(Color.web("#4a5568"));
            Rectangle screen = new Rectangle(x + 14, y + 12, 20, 10);
            screen.setFill(Color.web("#81e6d9"));
            map.getChildren().addAll(desk, register, screen);
            return;
        }
        map.getChildren().add(desk);
    }

    private void drawShopkeeper(Pane map, double x, double y) {
        // Cute pixel shopkeeper sprite
        ImageView shopkeeper = sprite(hero, x, y, PLAYER_SIZE);
        Text nameTag = new Text("Kaka");
        nameTag.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        nameTag.setFill(Color.web("#f1faee"));
        nameTag.setLayoutX(x + 6);
        nameTag.setLayoutY(y - 4);
        map.getChildren().addAll(shopkeeper, nameTag);
    }

    private void drawChocolateRacks(Pane map, double x, double y, double w, double h) {
        // Large shelf unit
        Rectangle shelf = new Rectangle(x, y, w, h);
        shelf.setFill(Color.web("#4a2810"));
        shelf.setStroke(Color.web("#2d1808"));
        shelf.setStrokeWidth(3);

        Text shelfTitle = new Text("CHOCOLATE CORNER");
        shelfTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        shelfTitle.setFill(Color.web("#ffe66d"));
        shelfTitle.setLayoutX(x + 20);
        shelfTitle.setLayoutY(y + 22);

        Group bars = new Group();
        // Shelf lines with chocolate bars
        for (int r = 0; r < 3; r++) {
            double lineY = y + 42 + r * 42;
            Line divider = new Line(x + 8, lineY, x + w - 8, lineY);
            divider.setStroke(Color.web("#8b5a2b"));
            divider.setStrokeWidth(3);
            bars.getChildren().add(divider);

            // Row 0: Cadbury Dairy Milk (Purple)
            // Row 1: KitKat (Red)
            // Row 2: 5 Star (Golden)
            Color barColor = (r == 0) ? Color.web("#4b2991") : (r == 1) ? Color.web("#d90429") : Color.web("#ffb703");
            for (int b = 0; b < 6; b++) {
                Rectangle choco = new Rectangle(x + 16 + b * 32, lineY - 26, 22, 24);
                choco.setFill(barColor);
                choco.setStroke(Color.web("#ffffff", 0.5));
                choco.setStrokeWidth(1);
                choco.setArcWidth(4);
                choco.setArcHeight(4);
                bars.getChildren().add(choco);
            }
        }

        map.getChildren().addAll(shelf, shelfTitle, bars);
    }

    private void drawProduceSection(Pane map, double x, double y) {
        // Crates of fruits & vegetables
        Group crates = new Group();
        String[] titles = { "🍅 Tomatoes", "🥕 Carrots", "🥔 Potatoes", "🍏 Apples" };
        Color[] produceColors = { Color.web("#e63946"), Color.web("#f77f00"), Color.web("#d4a373"),
                Color.web("#588157") };

        for (int i = 0; i < 4; i++) {
            double cy = y + i * 44;
            Rectangle crate = new Rectangle(x, cy, 110, 36);
            crate.setFill(Color.web("#a06a42"));
            crate.setStroke(Color.web("#58361e"));
            crate.setStrokeWidth(2);

            Text label = new Text(titles[i]);
            label.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
            label.setFill(produceColors[i]);
            label.setLayoutX(x + 12);
            label.setLayoutY(cy + 22);

            crates.getChildren().addAll(crate, label);
        }
        map.getChildren().add(crates);
    }

    // Creates a retro styled restart button with an arrow-loop pixel graphic icon
    private Button createRestartButton() {
        Button btn = new Button("RESET");
        btn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        btn.setStyle(
                "-fx-background-color: #2b3042;" +
                        "-fx-text-fill: #ffd166;" +
                        "-fx-border-color: #ffd166;" +
                        "-fx-border-width: 1.5;" +
                        "-fx-border-radius: 4;" +
                        "-fx-background-radius: 4;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 3 8 3 6;");

        // Pixel-themed reload/refresh loop icon
        Group icon = new Group();
        Circle arc = new Circle(7, 7, 5);
        arc.setFill(Color.TRANSPARENT);
        arc.setStroke(Color.web("#ffd166"));
        arc.setStrokeWidth(2);

        // Gap in circle to make it look like a loop
        Rectangle gap = new Rectangle(7, 0, 7, 7);
        gap.setFill(Color.web("#2b3042"));

        // Arrow head on the loop
        Polygon arrowHead = new Polygon(
                7, 0,
                12, 4,
                7, 8);
        arrowHead.setFill(Color.web("#ffd166"));

        icon.getChildren().addAll(arc, gap, arrowHead);
        btn.setGraphic(icon);

        btn.setOnMouseEntered(e -> btn.setStyle(
                "-fx-background-color: #ffd166;" +
                        "-fx-text-fill: #14161d;" +
                        "-fx-border-color: #ffffff;" +
                        "-fx-border-width: 1.5;" +
                        "-fx-border-radius: 4;" +
                        "-fx-background-radius: 4;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 3 8 3 6;"));

        btn.setOnMouseExited(e -> btn.setStyle(
                "-fx-background-color: #2b3042;" +
                        "-fx-text-fill: #ffd166;" +
                        "-fx-border-color: #ffd166;" +
                        "-fx-border-width: 1.5;" +
                        "-fx-border-radius: 4;" +
                        "-fx-background-radius: 4;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 3 8 3 6;"));

        btn.setOnAction(e -> restartGame());
        return btn;
    }

    private void restartGame() {
        coins = 0;
        chocolates = 0;
        sugarRushTimer = 0.0;
        swingCoinTimer = 0.0;
        onSwing = false;
        leftSwingPhase = 0.0;
        leftSwingPeak = 0.0;
        rightSwingPhase = 0.0;
        rightSwingPeak = 0.0;
        if (swingHandsGroup != null) {
            swingHandsGroup.setVisible(false);
        }
        if (playerShadow != null) {
            playerShadow.setVisible(true);
        }
        if (player != null) {
            player.setScaleY(1.0);
            player.setScaleX(playerFacing);
        }
        isPaused = false;
        if (pauseOverlay != null) {
            pauseOverlay.setVisible(false);
        }
        keys.clear();
        loadRoom(0, 3, 6);
        showToast("🔄 Game Restarted! Welcome to the Playground!", Color.web("#48cae4"));
        rootContainer.requestFocus();
    }

    private void createPauseOverlay() {
        pauseOverlay = new StackPane();
        pauseOverlay.setPrefSize(WIDTH, HEIGHT);

        pauseOverlay.setStyle(
                "-fx-background-color: rgba(0, 0, 0, 0.70);");

        Label pauseTitle = new Label("GAME PAUSED");
        pauseTitle.setStyle(
                "-fx-font-size: 36px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: white;");

        Button resumeButton = new Button("RESUME");
        resumeButton.setStyle(
                "-fx-font-size: 18px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 10 35 10 35;" +
                        "-fx-background-color: #ffd166;" +
                        "-fx-text-fill: #222222;" +
                        "-fx-background-radius: 8;");

        resumeButton.setOnAction(event -> {
            isPaused = false;
            pauseOverlay.setVisible(false);
            rootContainer.requestFocus();
        });

        VBox menu = new VBox(25);
        menu.setAlignment(Pos.CENTER);
        menu.getChildren().addAll(pauseTitle, resumeButton);

        pauseOverlay.getChildren().add(menu);

        pauseOverlay.setVisible(false);

        rootContainer.getChildren().add(pauseOverlay);
    }

    // ==========================================
    // GAME LOOP & PHYSICS UPDATE
    // ==========================================
    private void gameLoop(double seconds) {

        // Stop game updates while paused
        if (isPaused) {
            return;
        }

        if (transitionCooldown > 0)
            transitionCooldown -= seconds;
        updateToast(seconds);
        updateSugarRush(seconds);
        handlePlayerMovement(seconds);
        handlePlayerJump(seconds);

        if (currentRoom == 0) {

            // Update Bunty's independent movement
            updateBunty(seconds);

            // Existing playground physics
            updatePlaygroundPhysics(seconds);

        } else {
            updateShopInteraction();
        }

        checkExits();
    }

    private void updateToast(double seconds) {
        if (toastTimer > 0) {
            toastTimer -= seconds;
            if (toastTimer <= 0) {
                toastText.setVisible(false);
            }
        }
    }

    private void updateSugarRush(double seconds) {
        if (sugarRushTimer > 0) {
            sugarRushTimer -= seconds;
            if (sugarRushTimer <= 0) {
                sugarRushTimer = 0;
                showToast("Sugar rush wore off!", Color.web("#e0e1dd"));
            }
            updateHUDStats();
        }
    }

    private void handlePlayerMovement(double seconds) {

        // On see saw
        if (onSeeSaw)
            return;

        if (onSwing) {
            // Use active swing's own phase and anchor
            double phase = (activeSwing == 0) ? leftSwingPhase : rightSwingPhase;
            double anchorX = (activeSwing == 0) ? (swingX + 45) : (swingX + 105);

            double toAndFro = Math.sin(phase);
            double seatY = (swingY + 66) + toAndFro * 24.0;
            double scale = 1.0 + toAndFro * 0.14;

            // Character seated on the swing board
            playerX = anchorX - PLAYER_SIZE / 2;
            playerY = seatY - 26 * scale;

            player.setLayoutX(playerX);
            player.setLayoutY(playerY);
            player.setScaleX(playerFacing * scale);
            player.setScaleY(0.84 * scale); // seated posture

            // Hand grips on ropes
            if (swingHandsGroup != null) {
                swingHandsGroup.setVisible(true);
                leftHandL.setCenterX(anchorX - 12 * scale);
                leftHandL.setCenterY(seatY - 14 * scale);
                leftHandR.setCenterX(anchorX + 12 * scale);
                leftHandR.setCenterY(seatY - 14 * scale);
            }

            // Ground shadow beneath swing seat
            playerShadow.setCenterX(anchorX);
            playerShadow.setCenterY(swingY + 82 + toAndFro * 7);
            playerShadow.setRadiusX(16 * scale);
            playerShadow.setRadiusY(6 * (1.0 - Math.abs(toAndFro) * 0.25));
            playerShadow.setOpacity(Math.max(0.18, 0.40 - Math.abs(toAndFro) * 0.15));

            // Swinging earns coins every 1.5 seconds!
            swingCoinTimer += seconds;
            if (swingCoinTimer >= 1.5) {
                swingCoinTimer = 0.0;
                coins += 5;
                updateHUDStats();
                spawnCoinSparkle(anchorX, seatY - 20);
                showToast("✨ +₹5 Coins earned from swinging! 🎪", Color.web("#ffd166"));
            }
            return;
        }

        double currentSpeed = BASE_SPEED * (sugarRushTimer > 0 ? 1.6 : 1.0);

        // Check if player is standing in the Sand Pit
        if (currentRoom == 0 && isInsideSandPit(playerX + PLAYER_SIZE / 2, playerY + PLAYER_SIZE / 2)) {
            currentSpeed *= 0.62; // Walking in sand slows you down!
        }

        double dx = 0, dy = 0;
        if (keys.contains(KeyCode.LEFT) || keys.contains(KeyCode.A)) {
            dx -= currentSpeed * seconds;
            playerFacing = -1;
        }
        if (keys.contains(KeyCode.RIGHT) || keys.contains(KeyCode.D)) {
            dx += currentSpeed * seconds;
            playerFacing = 1;
        }
        if (keys.contains(KeyCode.UP) || keys.contains(KeyCode.W))
            dy -= currentSpeed * seconds;
        if (keys.contains(KeyCode.DOWN) || keys.contains(KeyCode.S))
            dy += currentSpeed * seconds;

        // Flip character sprite horizontally
        player.setScaleX(playerFacing);

        // Diagonal normalization
        if (dx != 0 && dy != 0) {
            dx *= 0.7071;
            dy *= 0.7071;
        }

        if (dx != 0 && canStandAt(playerX + dx, playerY))
            playerX += dx;
        if (dy != 0 && canStandAt(playerX, playerY + dy))
            playerY += dy;

        player.setLayoutX(playerX);
        player.setLayoutY(playerY - jumpZ);

        // Anchor the foot shadow to the ground
        playerShadow.setCenterX(playerX + PLAYER_SIZE / 2);
        playerShadow.setCenterY(playerY + PLAYER_SIZE - 2);
    }

    private void handlePlayerJump(double seconds) {
        // on see saw
        if (onSeeSaw)
            return;

        if (isJumping) {
            jumpVelZ -= GRAVITY * seconds;
            jumpZ += jumpVelZ * seconds;

            if (jumpZ <= 0) {
                jumpZ = 0;
                jumpVelZ = 0;
                isJumping = false;
                spawnLandingDust(playerX + PLAYER_SIZE / 2, playerY + PLAYER_SIZE);
            }

            // Shrink and soften ground shadow as player jumps higher
            double shadowScale = Math.max(0.4, 1.0 - (jumpZ / 120.0));
            playerShadow.setRadiusX(16 * shadowScale);
            playerShadow.setRadiusY(7 * shadowScale);
            playerShadow.setOpacity(Math.max(0.18, 0.45 - (jumpZ / 260.0)));
        } else {
            playerShadow.setRadiusX(16);
            playerShadow.setRadiusY(7);
            playerShadow.setOpacity(0.45);
        }
    }

    private void spawnLandingDust(double x, double y) {
        for (int i = 0; i < 4; i++) {
            Circle dust = new Circle(x + (i - 1.5) * 8, y - 2, 3 + Math.random() * 2, Color.web("#d7c49e", 0.6));
            mapLayer.getChildren().add(dust);
            FadeTransition ft = new FadeTransition(Duration.millis(300), dust);
            ft.setFromValue(0.6);
            ft.setToValue(0.0);
            ft.setOnFinished(e -> mapLayer.getChildren().remove(dust));
            ft.play();
        }
    }

    private void spawnCoinSparkle(double x, double y) {
        for (int i = 0; i < 5; i++) {
            Circle coinSpark = new Circle(x + (Math.random() - 0.5) * 20, y - 5 - Math.random() * 15,
                    3 + Math.random() * 2.5, Color.web("#ffd166"));
            coinSpark.setStroke(Color.web("#e76f51"));
            coinSpark.setStrokeWidth(1);
            mapLayer.getChildren().add(coinSpark);

            javafx.animation.TranslateTransition tt = new javafx.animation.TranslateTransition(Duration.millis(600),
                    coinSpark);
            tt.setToY(-25 - Math.random() * 20);

            FadeTransition ft = new FadeTransition(Duration.millis(600), coinSpark);
            ft.setFromValue(1.0);
            ft.setToValue(0.0);
            ft.setOnFinished(e -> mapLayer.getChildren().remove(coinSpark));

            tt.play();
            ft.play();
        }
    }

    private void toggleSwing() {
        double px = playerX + PLAYER_SIZE / 2;
        double py = playerY + PLAYER_SIZE / 2;

        double anchorLeftX = swingX + 45;
        double anchorRightX = swingX + 105;
        double restY = swingY + 66;

        boolean nearLeft = (Math.abs(px - anchorLeftX) < 32 &&
                Math.abs(py - (restY + 20)) < 36);

        boolean nearRight = (Math.abs(px - anchorRightX) < 32 &&
                Math.abs(py - (restY + 20)) < 36);

        double anchorX = nearRight ? anchorRightX : anchorLeftX;

        if (!onSwing && (nearLeft || nearRight)) {

            int selectedSwing = nearLeft ? 0 : 1;

            // Check whether Bunty is already using this swing
            if (buntyUsingSwing && buntyChosenSwing == selectedSwing) {
                showToast("Bunty is using this swing!", Color.web("#ffd166"));
                return;
            }

            onSwing = true;
            activeSwing = selectedSwing;
            swingCoinTimer = 0.0;

            showToast("Wheeee! Swinging high on the swings! (Earn coins while swinging!) 🎪",
                    Color.web("#48cae4"));

        } else if (onSwing) {

            anchorX = (activeSwing == 0) ? anchorLeftX : anchorRightX;

            onSwing = false;

            if (swingHandsGroup != null)
                swingHandsGroup.setVisible(false);

            player.setScaleY(1.0);
            player.setScaleX(playerFacing);

            jumpVelZ = 240;
            isJumping = true;

            playerX = anchorX - PLAYER_SIZE / 2;
            playerY = restY + 28;

            showToast("Hopped off the swing!", Color.web("#ffd166"));
        }
    }

    private boolean isInsideSandPit(double x, double y) {
        return x >= 8 * TILE && x <= 13 * TILE && y >= 1.5 * TILE && y <= 5.5 * TILE;
    }

    private void updatePlaygroundPhysics(double seconds) {
        // 1. Independent Per-Swing Pendulum Physics
        // ─────────────────────────────────────────
        // Each swing has its own phase (position in cycle) and peak (amplitude
        // envelope).
        // Peak decays multiplicatively each frame → smooth exponential slow-down from
        // whatever angle the swing was at when the player hopped off, never restarting
        // the arc from scratch.
        //
        // Decay constant: Math.pow(0.30, seconds) → swing stops in ~3 s after dismount.
        // Phase advances continuously (even at peak=0 it's just sin(phase)*0 = 0).

        // --- Left Swing ---
        if ((onSwing && activeSwing == 0) ||
                (buntyUsingSwing && buntyChosenSwing == 0)) {
            leftSwingPeak = 1.0; // held at full while riding
            leftSwingPhase += seconds * 3.4; // faster cadence while riding
        } else {
            leftSwingPeak = leftSwingPeak * Math.pow(0.30, seconds); // exponential decay ~3 s
            if (leftSwingPeak < 0.005)
                leftSwingPeak = 0.0;
            leftSwingPhase += seconds * 2.0;
        }

        // --- Right Swing (identical behavior, independent) ---
        if ((onSwing && activeSwing == 1) ||
                (buntyUsingSwing && buntyChosenSwing == 1)) {
            rightSwingPeak = 1.0;
            rightSwingPhase += seconds * 3.4;
        } else {
            rightSwingPeak = rightSwingPeak * Math.pow(0.30, seconds);
            if (rightSwingPeak < 0.005)
                rightSwingPeak = 0.0;
            rightSwingPhase += seconds * 2.0;
        }

        final double MAX_AMP = 24.0; // max pixel swing arc
        double anchorLeftX = swingX + 45;
        double anchorRightX = swingX + 105;
        double topY = swingY + 7;
        double restY = swingY + 66;

        // ---- LEFT SWING ----
        double sinL = Math.sin(leftSwingPhase);
        double offsetL = sinL * leftSwingPeak * MAX_AMP; // pixel offset from rest
        double seatYLeft = restY + offsetL;
        double scaleLeft = 1.0 + sinL * leftSwingPeak * 0.14;

        if (leftRope1 != null) {
            leftRope1.setStartX(anchorLeftX - 12);
            leftRope1.setStartY(topY);
            leftRope1.setEndX(anchorLeftX - 12 * scaleLeft);
            leftRope1.setEndY(seatYLeft);

            leftRope2.setStartX(anchorLeftX + 12);
            leftRope2.setStartY(topY);
            leftRope2.setEndX(anchorLeftX + 12 * scaleLeft);
            leftRope2.setEndY(seatYLeft);

            leftSeatGroup.setLayoutX(anchorLeftX);
            leftSeatGroup.setLayoutY(seatYLeft);
            leftSeatGroup.setScaleX(scaleLeft);
            leftSeatGroup.setScaleY(scaleLeft);

            if (leftSeatShadow != null) {
                leftSeatShadow.setCenterX(anchorLeftX);
                leftSeatShadow.setCenterY(restY + 16 + offsetL * 0.3);
                leftSeatShadow.setRadiusX(16 * scaleLeft);
                leftSeatShadow.setRadiusY(5 * (1.0 - Math.abs(sinL * leftSwingPeak) * 0.25));
                leftSeatShadow.setOpacity(Math.max(0.15, 0.35 - Math.abs(sinL * leftSwingPeak) * 0.12));
            }
        }

        // ---- RIGHT SWING (independent) ----
        double sinR = Math.sin(rightSwingPhase);
        double offsetR = sinR * rightSwingPeak * MAX_AMP;
        double seatYR = restY + offsetR;
        double scaleRight = 1.0 + sinR * rightSwingPeak * 0.14;

        if (rightRope1 != null) {
            rightRope1.setStartX(anchorRightX - 12);
            rightRope1.setStartY(topY);
            rightRope1.setEndX(anchorRightX - 12 * scaleRight);
            rightRope1.setEndY(seatYR);

            rightRope2.setStartX(anchorRightX + 12);
            rightRope2.setStartY(topY);
            rightRope2.setEndX(anchorRightX + 12 * scaleRight);
            rightRope2.setEndY(seatYR);

            rightSeatGroup.setLayoutX(anchorRightX);
            rightSeatGroup.setLayoutY(seatYR);
            rightSeatGroup.setScaleX(scaleRight);
            rightSeatGroup.setScaleY(scaleRight);

            if (rightSeatShadow != null) {
                rightSeatShadow.setCenterX(anchorRightX);
                rightSeatShadow.setCenterY(restY + 16 + offsetR * 0.3);
                rightSeatShadow.setRadiusX(16 * scaleRight);
                rightSeatShadow.setRadiusY(5 * (1.0 - Math.abs(sinR * rightSwingPeak) * 0.25));
                rightSeatShadow.setOpacity(Math.max(0.15, 0.35 - Math.abs(sinR * rightSwingPeak) * 0.12));
            }
        }

        // Swing interaction prompt
        double px = playerX + PLAYER_SIZE / 2;
        double py = playerY + PLAYER_SIZE / 2;
        boolean nearSwing = (Math.abs(px - anchorLeftX) < 32 && Math.abs(py - (restY + 20)) < 36)
                || (Math.abs(px - anchorRightX) < 32 && Math.abs(py - (restY + 20)) < 36);

        if (onSwing) {
            interactionPrompt.setText("💬 Press [E] or [SPACE] to Hop Off");
            interactionPrompt.setLayoutX(WIDTH / 2.0 - 165);
            interactionPrompt.setLayoutY(HEIGHT - 45);
            interactionPrompt.setVisible(true);
        } else if (nearSwing) {
            interactionPrompt.setText("💬 Press [E] to Sit on Swing!");
            interactionPrompt.setLayoutX(WIDTH / 2.0 - 130);
            interactionPrompt.setLayoutY(HEIGHT - 45);
            interactionPrompt.setVisible(true);
        } else if (interactionPrompt.getText().contains("Swing")) {
            interactionPrompt.setVisible(false);
        }

        // 2. Real Ball 2D Physics Update
        for (PlaygroundBall ball : balls) {
            ball.update(seconds, isInsideSandPit(ball.x, ball.y));

            // Collision with boundaries (inside the fenced playground)
            double minX = TILE + ball.radius;
            double maxX = (COLS - 1) * TILE - ball.radius;
            double minY = TILE + ball.radius;
            double maxY = (ROWS - 1) * TILE - ball.radius;

            if (ball.x < minX) {
                ball.x = minX;
                ball.vx = -ball.vx * 0.75;
            }
            if (ball.x > maxX && (ball.y < 7 * TILE || ball.y > 10 * TILE)) {
                ball.x = maxX;
                ball.vx = -ball.vx * 0.75;
            }
            if (ball.y < minY) {
                ball.y = minY;
                ball.vy = -ball.vy * 0.75;
            }
            if (ball.y > maxY) {
                ball.y = maxY;
                ball.vy = -ball.vy * 0.75;
            }

            // Collision with Player (Kicking the ball!)
            double bdx = ball.x - px;
            double bdy = ball.y - py;
            double dist = Math.hypot(bdx, bdy);
            double minDist = ball.radius + PLAYER_SIZE * 0.4;

            if (dist < minDist && !onSwing) {
                if (dist == 0)
                    dist = 0.1;
                double nx = bdx / dist;
                double ny = bdy / dist;

                // Push ball out of player
                ball.x = px + nx * minDist;
                ball.y = py + ny * minDist;

                // Impart velocity
                double kickForce = 220.0;
                if (isJumping && jumpZ < 30) {
                    kickForce = 340.0; // Jumping slam kick!
                }
                ball.vx += nx * kickForce;
                ball.vy += ny * kickForce;
            }
        }

        // 3. Ball-to-Ball Elastic Collisions
        if (balls.size() >= 2) {
            PlaygroundBall b1 = balls.get(0);
            PlaygroundBall b2 = balls.get(1);
            double cdx = b2.x - b1.x;
            double cdy = b2.y - b1.y;
            double cdist = Math.hypot(cdx, cdy);
            double totalRadius = b1.radius + b2.radius;
            if (cdist < totalRadius && cdist > 0.001) {
                double cnx = cdx / cdist;
                double cny = cdy / cdist;
                double overlap = 0.5 * (totalRadius - cdist);
                b1.x -= cnx * overlap;
                b1.y -= cny * overlap;
                b2.x += cnx * overlap;
                b2.y += cny * overlap;

                double dvx = b1.vx - b2.vx;
                double dvy = b1.vy - b2.vy;
                double vn = dvx * cnx + dvy * cny;
                if (vn > 0) {
                    double impulse = vn * 0.85;
                    b1.vx -= impulse * cnx;
                    b1.vy -= impulse * cny;
                    b2.vx += impulse * cnx;
                    b2.vy += impulse * cny;
                }
            }
        }
        // =====================================================================
        // 4. See-Saw Rocking Physics & Animation
        // =====================================================================
        if (onSeeSaw && buntyUsingSeeSaw) {

            // Both characters: fast rocking
            seeSawPhase += seconds * 5.0;
            seeSawAngle = Math.sin(seeSawPhase) * 16.0;

        } else if (onSeeSaw || buntyUsingSeeSaw) {

            // One character: slow rocking
            seeSawPhase += seconds * 2.5;
            seeSawAngle = Math.sin(seeSawPhase) * 8.0;

        } else {

            // Nobody sitting: gradually settle to rest
            seeSawAngle *= Math.pow(0.08, seconds);

            if (Math.abs(seeSawAngle) < 0.05) {
                seeSawAngle = 0;
            }
        }

        if (seeSawPlankGroup != null) {
            seeSawPlankGroup.setRotate(seeSawAngle);
        }

        if (seeSawPlankGroup != null) {
            seeSawPlankGroup.setRotate(seeSawAngle);
        }

        if (seeSawPlankGroup != null) {
            seeSawPlankGroup.setRotate(seeSawAngle);
        }

    }

    private void updateShopInteraction() {
        // Distance to the counter or chocolate shelves
        double px = playerX + PLAYER_SIZE / 2;
        double py = playerY + PLAYER_SIZE / 2;

        // Counter zone: cols 6..10, row 4
        boolean inCounterZone = (px >= 5.5 * TILE && px <= 11.5 * TILE && py >= 3.2 * TILE && py <= 5.2 * TILE);
        // Chocolate shelves zone: cols 13..15, rows 3..6
        boolean inChocoZone = (px >= 12.0 * TILE && px <= 15.5 * TILE && py >= 2.5 * TILE && py <= 6.5 * TILE);

        nearCounter = inCounterZone || inChocoZone;

        if (nearCounter) {
            interactionPrompt.setText("💬 Press [E] to Buy Chocolate (₹20)");
            interactionPrompt.setLayoutX(WIDTH / 2.0 - 165);
            interactionPrompt.setLayoutY(HEIGHT - 45);
            interactionPrompt.setVisible(true);
        } else {
            interactionPrompt.setVisible(false);
        }
    }

    private boolean canStandAt(double x, double y) {
        int col = (int) ((x + PLAYER_SIZE / 2) / TILE);
        int row = (int) ((y + PLAYER_SIZE / 2) / TILE);
        return col >= 0 && col < COLS && row >= 0 && row < ROWS && walkable[row][col];
    }

    private void checkExits() {
        if (transitionCooldown > 0)
            return;
        int col = (int) ((playerX + PLAYER_SIZE / 2) / TILE);
        int row = (int) ((playerY + PLAYER_SIZE / 2) / TILE);
        for (Exit exit : exits) {
            if (col == exit.x && row == exit.y) {
                loadRoom(exit.nextRoom, exit.spawnX, exit.spawnY);
                return;
            }
        }
    }

    // ==========================================
    // DRAWING HELPERS & BALL CLASS
    // ==========================================
    private void roadTile(Pane map, int x, int y) {
        map.getChildren().add(sprite(pavement, x * TILE, y * TILE, TILE));
        walkable[y][x] = true;
    }

    private void tree(Pane map, int x, int y) {
        map.getChildren().add(sprite(tree, x * TILE, y * TILE, TILE));
        walkable[y][x] = false;
    }

    private void shrub(Pane map, int x, int y) {
        map.getChildren().add(sprite(shrub, x * TILE, y * TILE, TILE));
        walkable[y][x] = false;
    }

    private void arrow(Pane map, int x, int y, Direction direction, String label) {
        double left = x * TILE, top = y * TILE;
        Polygon marker = switch (direction) {
            case EAST -> new Polygon(left + 10, top + 8, left + 39, top + 24, left + 10, top + 40);
            case NORTH -> new Polygon(left + 8, top + 39, left + 24, top + 10, left + 40, top + 39);
            case WEST -> new Polygon(left + 39, top + 8, left + 10, top + 24, left + 39, top + 40);
            case SOUTH -> new Polygon(left + 8, top + 10, left + 24, top + 39, left + 40, top + 10);
        };
        marker.setFill(Color.web("#ffe66d"));
        marker.setStroke(Color.web("#4a0e17"));
        marker.setStrokeWidth(4);
        map.getChildren().add(marker);

        walkable[y][x] = true;
    }

    private ImageView sprite(Image image, double x, double y, double size) {
        ImageView view = new ImageView(image);
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(false);
        view.setSmooth(false);
        view.setLayoutX(x);
        view.setLayoutY(y);
        return view;
    }

    private static Image image(String path) {
        return new Image(new File(path).toURI().toString());
    }

    // 2D Real Physics Ball Entity
    private static class PlaygroundBall {
        double x, y;
        double vx = 0, vy = 0;
        double radius;
        boolean isSoccer;
        Group renderGroup = new Group();
        Ellipse shadow;

        public PlaygroundBall(double startX, double startY, double r, boolean soccer, Color mainColor,
                Color strokeColor) {
            this.x = startX;
            this.y = startY;
            this.radius = r;
            this.isSoccer = soccer;

            shadow = new Ellipse(x, y + radius - 2, radius * 0.9, radius * 0.35);
            shadow.setFill(Color.web("#000000", 0.35));

            Circle ball = new Circle(0, 0, radius, mainColor);
            ball.setStroke(strokeColor);
            ball.setStrokeWidth(2);
            renderGroup.getChildren().add(ball);

            if (isSoccer) {
                // Soccer ball patches
                Polygon patch = new Polygon(-3, -2, 0, -5, 3, -2, 2, 2, -2, 2);
                patch.setFill(Color.web("#212529"));
                renderGroup.getChildren().add(patch);
            } else {
                // Dodgeball stripe highlight
                Circle highlight = new Circle(-radius * 0.35, -radius * 0.35, radius * 0.25, Color.web("#ffffff", 0.6));
                renderGroup.getChildren().add(highlight);
            }

            renderGroup.setLayoutX(x);
            renderGroup.setLayoutY(y);
        }

        public void update(double seconds, boolean inSand) {
            x += vx * seconds;
            y += vy * seconds;

            // Rolling friction (higher friction in sand pit)
            double frictionFactor = inSand ? 0.70 : 0.88;
            double decay = Math.pow(frictionFactor, seconds * 60.0);
            vx *= decay;
            vy *= decay;

            // Stop microscopic drifting
            if (Math.abs(vx) < 1.5)
                vx = 0;
            if (Math.abs(vy) < 1.5)
                vy = 0;

            renderGroup.setLayoutX(x);
            renderGroup.setLayoutY(y);
            shadow.setCenterX(x);
            shadow.setCenterY(y + radius - 2);
        }
    }

    private enum Direction {
        EAST, NORTH, WEST, SOUTH
    }

    private record Exit(int x, int y, Direction direction, int nextRoom, int spawnX, int spawnY) {
    }

    public static void main(String[] args) {
        launch(args);
    }
}