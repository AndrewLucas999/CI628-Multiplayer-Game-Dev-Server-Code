package com.almasb.fxglgames.pong;

import com.almasb.fxgl.dsl.components.Effect;
import com.almasb.fxgl.entity.Entity;
import javafx.util.Duration;

// Temporary Buff to the entity which does something when beginning and on end
public class SpeedUpEffect extends Effect {
    public SpeedUpEffect(Duration duration) {
        super(duration);
    }

    @Override
    public void onStart(Entity entity) {
        var batComponent=entity.getComponent(BatComponent.class);
        batComponent.setBAT_SPEED(700);
    }
    @Override
    public void onEnd(Entity entity) {
        var batComponent=entity.getComponent(BatComponent.class);
        batComponent.setBAT_SPEED(420);
    }


}
