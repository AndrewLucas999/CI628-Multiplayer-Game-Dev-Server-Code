package com.almasb.fxglgames.pong;

import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.physics.PhysicsComponent;
import javafx.geometry.Point2D;

import static com.almasb.fxgl.dsl.FXGL.*;
import static java.lang.Math.abs;
import static java.lang.Math.signum;

public class PowerUpComponent extends Component {
    private PhysicsComponent physics;

    @Override
    public void onUpdate(double tpf){
        limitVelocity();
        checkOffscreen();
    }

    // Limits the minimum speed of the Powerup ball
    private void limitVelocity() {
        // we don't want the ball to move too slow in X direction
        if (abs(physics.getVelocityX()) < 5 * 30) {
            physics.setVelocityX(signum(physics.getVelocityX()) * 5 * 30);
        }

        // we don't want the ball to move too fast in Y direction
        if (abs(physics.getVelocityY()) > 5 * 30 * 2) {
            physics.setVelocityY(signum(physics.getVelocityY()) * 5 * 30);
        }
    }

    // Check if power up ball goes out of screen
    private void checkOffscreen() {
        if (getEntity().getBoundingBoxComponent().isOutside(getGameScene().getViewport().getVisibleArea())) {
            physics.overwritePosition(new Point2D(
                    getAppWidth() / 2,
                    getAppHeight() / 2
            ));
        }
    }
}
