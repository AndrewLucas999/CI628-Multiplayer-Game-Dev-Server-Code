/*
 * The MIT License (MIT)
 *
 * FXGL - JavaFX Game Library
 *
 * Copyright (c) 2015-2017 AlmasB (almaslvl@gmail.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.almasb.fxglgames.pong;

import com.almasb.fxgl.animation.Interpolators;
import com.almasb.fxgl.app.ApplicationMode;
import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.core.collection.PropertyMap;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.dsl.components.EffectComponent;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.SpawnData;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.net.*;
import com.almasb.fxgl.physics.CollisionHandler;
import com.almasb.fxgl.physics.HitBox;
import com.almasb.fxgl.ui.UI;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static com.almasb.fxgl.dsl.FXGL.*;
import static com.almasb.fxglgames.pong.NetworkMessages.*;
import static com.almasb.fxglgames.pong.NetworkMessages.POWERUP_HIT_WALL_DOWN;

/**
 * A simple clone of Pong.
 * Sounds from https://freesound.org/people/NoiseCollector/sounds/4391/ under CC BY 3.0.
 *
 * @author Almas Baimagambetov (AlmasB) (almaslvl@gmail.com)
 */
public class PongApp extends GameApplication implements MessageHandler<String> {

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setTitle("Pong");
        settings.setVersion("1.0");
        settings.setFontUI("pong.ttf");
        settings.setApplicationMode(ApplicationMode.DEBUG);
    }

    // Player 1
    private Entity player1;

    // Player 2
    private Entity player2;

    // Main Ball
    private Entity ball;

    // Powerup ball
    private Entity powerUp;

    private BatComponent player1Bat;
    private BatComponent player2Bat;

    // Player 1 connection
    private Connection player1Connection;

    // Player 2 connection
    private Connection player2Connection;
    private Server<String> server;
    @Override
    protected void initInput() {
        getInput().addAction(new UserAction("Up1") {

            // Moving on every frame
            @Override
            protected void onAction() {
                player1Bat.up();
            }

            // When the user lets go it will stop action
            @Override
            protected void onActionEnd() {
                player1Bat.stop();
            }
        }, KeyCode.W);


        getInput().addAction(new UserAction("Down1") {
            // Moving on every frame
            @Override
            protected void onAction() {
                player1Bat.down();
            }

            // When the user lets go it will stop action
            @Override
            protected void onActionEnd() {
                player1Bat.stop();
            }
        }, KeyCode.S);


        // Second Player
        getInput().addAction(new UserAction("Up2") {
            @Override
            protected void onAction() {
                player2Bat.up();
            }

            @Override
            protected void onActionEnd() {
                player2Bat.stop();
            }
        }, KeyCode.I);

        getInput().addAction(new UserAction("Down2") {
            @Override
            protected void onAction() {
                player2Bat.down();
            }

            @Override
            protected void onActionEnd() {
                player2Bat.stop();
            }
        }, KeyCode.K);
    }

    @Override
    protected void initGameVars(Map<String, Object> vars) {
        vars.put("player1score", 0);
        vars.put("player2score", 0);
    }

    private MessageReaderS readerS;
    @Override
    protected void initGame() {

        Writers.INSTANCE.addTCPWriter(String.class, outputStream -> new MessageWriterS(outputStream));
        Readers.INSTANCE.addTCPReader(String.class, in -> {
            readerS = new MessageReaderS(in);
            return readerS;
        });

        server = getNetService().newTCPServer(55555, new ServerConfig<>(String.class));

        server.setOnConnected(connection -> {
            connection.addMessageHandlerFX(this);

            // Check if connection has already been made
            if(player1Connection == null){
                player1Connection = connection;
                player1Connection.getLocalSessionData().setValue("ID","ID1");
            }
            else if(player2Connection == null){
                player2Connection = connection;
                player2Connection.getLocalSessionData().setValue("ID","ID2");
            }
        });

        getGameWorld().addEntityFactory(new PongFactory());
        getGameScene().setBackgroundColor(Color.rgb(0, 0, 5));

        initScreenBounds();
        initGameObjects();

        var t = new Thread(server.startTask()::run);
        t.setDaemon(true);
        t.start();
    }

    @Override
    protected void initPhysics() {
        // TODO - Use this to set the gravity i.e. Ability to mess with the player.
        getPhysicsWorld().setGravity(0, 0);

        getPhysicsWorld().addCollisionHandler(new CollisionHandler(EntityType.BALL, EntityType.WALL) {
            @Override
            protected void onHitBoxTrigger(Entity a, Entity b, HitBox boxA, HitBox boxB) {
                if (boxB.getName().equals("LEFT")) {
                    inc("player2score", +1);

                    server.broadcast("SCORES," + geti("player1score") + "," + geti("player2score"));

                    server.broadcast(HIT_WALL_LEFT);
                } else if (boxB.getName().equals("RIGHT")) {
                    inc("player1score", +1);

                    server.broadcast("SCORES," + geti("player1score") + "," + geti("player2score"));

                    server.broadcast(HIT_WALL_RIGHT);
                } else if (boxB.getName().equals("TOP")) {
                    server.broadcast(HIT_WALL_UP);
                } else if (boxB.getName().equals("BOT")) {
                    server.broadcast(HIT_WALL_DOWN);
                }

                getGameScene().getViewport().shakeTranslational(5);
            }
        });

        CollisionHandler ballBatHandler = new CollisionHandler(EntityType.BALL, EntityType.PLAYER_BAT) {
            @Override
            protected void onCollisionBegin(Entity a, Entity bat) {
                playHitAnimation(bat);
                server.broadcast(bat == player1 ? BALL_HIT_BAT1 : BALL_HIT_BAT2);
            }
        };

        // Handling physics for PowerUp and Bat
        CollisionHandler batPowerUpHandler = new CollisionHandler(EntityType.POWERUP, EntityType.PLAYER_BAT) {
            @Override
            protected void onCollisionBegin(Entity powerUp, Entity bat) {
                playHitAnimation(bat);
                powerUp.removeFromWorld();
                sendMessage("POWERUP_DESTROYED");
                runOnce(() -> {
                    spawnPowerUp();
                }, Duration.seconds(7));
                var speedUpEffect = new SpeedUpEffect(Duration.millis(3000));
                if (bat == player1){
                    bat.getComponent(EffectComponent.class).startEffect(speedUpEffect);
                }
                else if(bat == player2){
                    bat.getComponent(EffectComponent.class).startEffect(speedUpEffect);
                }
                server.broadcast(bat == player1 ? POWERUP_HIT_BAT1 : POWERUP_HIT_BAT2);

            }
        };

        // Power Up and Wall collision handler
        getPhysicsWorld().addCollisionHandler(new CollisionHandler(EntityType.POWERUP, EntityType.WALL) {
            @Override
            protected void onHitBoxTrigger(Entity a, Entity b, HitBox boxA, HitBox boxB) {
                if (boxB.getName().equals("LEFT")) {
                    server.broadcast(POWERUP_HIT_WALL_LEFT);
                } else if (boxB.getName().equals("RIGHT")) {
                    server.broadcast(POWERUP_HIT_WALL_RIGHT);
                } else if (boxB.getName().equals("TOP")) {
                    server.broadcast(POWERUP_HIT_WALL_UP);
                } else if (boxB.getName().equals("BOT")) {
                    server.broadcast(POWERUP_HIT_WALL_DOWN);
                }

                getGameScene().getViewport().shakeTranslational(5);
            }
        });

        getPhysicsWorld().addCollisionHandler(batPowerUpHandler);
        getPhysicsWorld().addCollisionHandler(batPowerUpHandler.copyFor(EntityType.POWERUP, EntityType.ENEMY_BAT));

        getPhysicsWorld().addCollisionHandler(ballBatHandler);
        getPhysicsWorld().addCollisionHandler(ballBatHandler.copyFor(EntityType.BALL, EntityType.ENEMY_BAT));
    }

    @Override
    protected void initUI() {
        MainUIController controller = new MainUIController();
        UI ui = getAssetLoader().loadUI("main.fxml", controller);

        controller.getLabelScorePlayer().textProperty().bind(getip("player1score").asString());
        controller.getLabelScoreEnemy().textProperty().bind(getip("player2score").asString());

        getGameScene().addUI(ui);
    }

    @Override
    protected void onUpdate(double tpf) {
        if (!server.getConnections().isEmpty()) {
            var message = "GAME_DATA," + player1.getX() + "," + player1.getY() + "," + player2.getX() + "," +
                    player2.getY() + "," + ball.getX() + "," + ball.getY() + "," + ball.getDouble("radius") ;

            // When the power up ball is still active it will send the coordinates and radius of power up
            if(powerUp.isActive()){
                var powerUpMessage = "GAME_DATA_PU," + powerUp.getX() + "," + powerUp.getY() + "," +
                        powerUp.getDouble("powerUpRadius");
                sendMessage(powerUpMessage);
            }
            sendMessage(message);
        }
    }

    private void initScreenBounds() {
        Entity walls = entityBuilder()
                .type(EntityType.WALL)
                .collidable()
                .buildScreenBounds(150);

        getGameWorld().addEntity(walls);
    }

    private void initGameObjects() {
        // setting radius
        double radius = 30;
        ball = spawn("ball", new SpawnData(getAppWidth() / 2 - 5 , getAppHeight() / 2 - 5).put("radius", radius));
        player1 = spawn("bat", new SpawnData(getAppWidth() / 4, getAppHeight() / 2 - 30).put("isPlayer", true));
        player2 = spawn("bat", new SpawnData(3 * getAppWidth() / 4 - 20, getAppHeight() / 2 - 30).put("isPlayer", false));

        // Initialise spawning powerup
        spawnPowerUp();

        player1Bat = player1.getComponent(BatComponent.class);
        player2Bat = player2.getComponent(BatComponent.class);

    }

    private void spawnPowerUp(){
        double powerUpRadius = 30;
        server.broadcast("POWERUP_CREATED");
        powerUp = spawn("powerUp", new SpawnData(getAppWidth()/2 - powerUpRadius , getAppHeight() / 2 - powerUpRadius).put("powerUpRadius", powerUpRadius));
    }
    /// Animation for the recoil animation after being hit for a bat
    private void playHitAnimation(Entity bat) {
        animationBuilder()
                .autoReverse(true)
                .duration(Duration.seconds(0.5))
                .interpolator(Interpolators.BOUNCE.EASE_OUT())
                .rotate(bat)
                .from(FXGLMath.random(-25, 25))
                .to(0)
                .buildAndPlay();
    }

    // TODO - handle different connection
    @Override
    public void onReceive(Connection<String> connection, String message) {
        System.out.println(message);
        var tokens = message.split(",");
        Arrays.stream(tokens).skip(1).forEach(key -> {
            // Player 1
            if(connection.getLocalSessionData().getString("ID").equals("ID1")){
                if(message.contains("DISCONNECT")){
                    System.out.println(message);
                    sendMessage("DISCONNECTING");
                    player1Connection.terminate();

                    try {
                        readerS.messages.put("");
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    player1Connection = null;

                }

                if(key.endsWith("_DOWN")){
                    if (key.substring(0, 1).contains("W")) {
                        getInput().mockKeyPress(KeyCode.valueOf(key.substring(0, 1)));
                    } else if (key.substring(0, 1).contains("S")) {
                        getInput().mockKeyPress(KeyCode.valueOf(key.substring(0, 1)));
                    }
                }
                else if(key.endsWith("_UP")){
                    getInput().mockKeyRelease(KeyCode.valueOf(key.substring(0, 1)));
                }

            }
            // Player 2
            else if(connection.getLocalSessionData().getString("ID").equals("ID2") ){
                if(message.contains("DISCONNECT")){
                    System.out.println(message);
                    sendMessage("DISCONNECTING");
                    player2Connection.terminate();

                    try {
                        readerS.messages.put("");
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    player2Connection=null;
                }
                if(key.endsWith("_DOWN")) {
                    if (key.substring(0, 1).contains("I")) {
                        getInput().mockKeyPress(KeyCode.valueOf(key.substring(0, 1)));
                    } else if (key.substring(0, 1).contains("K")) {
                        getInput().mockKeyPress(KeyCode.valueOf(key.substring(0, 1)));
                    }
                }
                else if(key.endsWith("_UP")){
                    getInput().mockKeyRelease(KeyCode.valueOf(key.substring(0, 1)));
                }
            }

        });
    }

    static class MessageWriterS implements TCPMessageWriter<String> {

        private OutputStream os;
        private PrintWriter out;

        MessageWriterS(OutputStream os) {
            this.os = os;
            out = new PrintWriter(os, true);
        }

        @Override
        public void write(String s) throws Exception {
            out.print(s.toCharArray());
            out.flush();
        }
    }

    // Function to broadcast messages in one location
    public void sendMessage(String message){
        server.broadcast(message);
    }

    static class MessageReaderS implements TCPMessageReader<String> {

        private BlockingQueue<String> messages = new ArrayBlockingQueue<>(50);

        private InputStreamReader in;

        MessageReaderS(InputStream is) {
            in =  new InputStreamReader(is);

            var t = new Thread(() -> {
                try {

                    char[] buf = new char[36];

                    int len;

                    while ((len = in.read(buf)) > 0) {
                        var message = new String(Arrays.copyOf(buf, len));

                        System.out.println("Recv message: " + message);

                        messages.put(message);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            t.setDaemon(true);
            t.start();
        }

        @Override
        public String read() throws Exception {
            return messages.take();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
