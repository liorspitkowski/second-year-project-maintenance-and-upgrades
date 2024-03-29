/***************************************************************************
 *                   (C) Copyright 2012-2016 - Stendhal                    *
 ***************************************************************************
 ***************************************************************************
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 ***************************************************************************/
package games.stendhal.server.entity.mapstuff.block;

import java.awt.geom.Rectangle2D;
import java.util.List;

import org.apache.log4j.Logger;

import games.stendhal.server.entity.mapstuff.chest.Chest;
import games.stendhal.common.Direction;
import games.stendhal.common.Rand;
import games.stendhal.common.constants.SoundLayer;
import games.stendhal.server.core.engine.SingletonRepository;
import games.stendhal.server.core.engine.StendhalRPZone;
import games.stendhal.server.core.events.MovementListener;
import games.stendhal.server.core.events.TurnListener;
import games.stendhal.server.core.events.ZoneEnterExitListener;
import games.stendhal.server.entity.ActiveEntity;
import games.stendhal.server.entity.Entity;
import games.stendhal.server.entity.RPEntity;
import games.stendhal.server.entity.player.Player;
import games.stendhal.server.events.SoundEvent;
import marauroa.common.game.RPObject;

/**
 * A solid, movable block on a map. It can have different apearances,
 * for example a farm cart.
 *
 * @author madmetzger
 */
public class MovableChest extends Chest implements ZoneEnterExitListener,
        MovementListener, TurnListener {

    private static final Logger logger = Logger.getLogger(Block.class);

    /** number of seconds until another attempt to rest the block to its original position is attempted */
    static final int RESET_AGAIN_DELAY = 10;


    private int startX;
    private int startY;
    private boolean multi;

   private final List<String> sounds;

    private boolean resetBlock = false;
    private boolean wasMoved = false;

    public MovableChest(int x, int y) {
        this.multi = true;
        this.startX = x;
        this.startY = y;
        sounds = null;
    }

    /**
     * Resets the block position to its initial state
     */
    public void reset() {
        wasMoved = false;
        this.setPosition(startX, startY);
        SingletonRepository.getTurnNotifier().dontNotify(this);
        this.notifyWorldAboutChanges();
    }

    /**
     * Push this Block into a given direction
     *
     * @param p
     * @param d
     *            the direction, this block is pushed into
     */
    
    public void push(Player p, Direction d) {
        if (!this.mayBePushed(d)) {
            return;
        }
        int x = getXAfterPush(d);
        int y = getYAfterPush(d);
        this.setPosition(x, y);
        wasMoved = true;
        this.sendSound();
        this.notifyWorldAboutChanges();
        if (logger.isDebugEnabled()) {
            logger.debug("Block [" + this.getID().toString() + "] pushed to (" + this.getX() + "," + this.getY() + ").");
        }
    }

    /**
     * should the block reset to its original position after some time?
     *
     * @param resetBlock true, if the block should be reset; false otherwise
     */
    public void setResetBlock(boolean resetBlock) {
        this.resetBlock = resetBlock;
    }

    private void sendSound() {
        if (this.sounds != null && !this.sounds.isEmpty()) {
            SoundEvent e = new SoundEvent(Rand.rand(sounds), SoundLayer.AMBIENT_SOUND);
            this.addEvent(e);
            this.notifyWorldAboutChanges();
        }
    }

    public int getYAfterPush(Direction d) {
        return this.getY() + d.getdy();
    }

    public int getXAfterPush(Direction d) {
        return this.getX() + d.getdx();
    }

    private boolean wasPushed() {
        boolean xChanged = this.getInt("x") != this.startX;
        boolean yChanged = this.getInt("y") != this.startY;
        return xChanged || yChanged;
    }

    private boolean mayBePushed(Direction d) {
        boolean pushed = wasPushed();
        int newX = this.getXAfterPush(d);
        int newY = this.getYAfterPush(d);

        if (!multi && pushed) {
            return false;
        }
        if (this.isOpen() == true) {
        	return false;
        }
        // additional checks: new position must be free
        
        boolean collision = this.getZone().collides(this, newX, newY);

        return !collision;
    }


    @Override
    public void onEntered(ActiveEntity entity, StendhalRPZone zone, int newX, int newY) {
        // do nothing
    }

    @Override
    public void onExited(ActiveEntity entity, StendhalRPZone zone, int oldX, int oldY) {
        if (logger.isDebugEnabled()) {
            logger.debug("Block [" + this.getID().toString() + "] notified about entity [" + entity + "] exiting [" + zone.getName() + "].");
        }
        resetInPlayerlessZone(zone, entity);
    }

    @Override
    public void onMoved(ActiveEntity entity, StendhalRPZone zone, int oldX,
            int oldY, int newX, int newY) {
        // do nothing on move
    }

    @Override
    public void onEntered(RPObject object, StendhalRPZone zone) {
        // do nothing
    }

    @Override
    public void onExited(RPObject object, StendhalRPZone zone) {
        if (logger.isDebugEnabled()) {
            logger.debug("Block [" + this.getID().toString() + "] notified about object [" + object + "] exiting [" + zone.getName() + "].");
        }
        resetInPlayerlessZone(zone, object);
    }

    private void resetInPlayerlessZone(StendhalRPZone zone, RPObject object) {
        if (!resetBlock || !wasMoved) {
            return;
        }

        // reset to initial position if zone gets empty of players
        final List<Player> playersInZone = zone.getPlayers();
        int numberOfPlayersInZone = playersInZone.size();
        if (numberOfPlayersInZone == 0 || numberOfPlayersInZone == 1 && playersInZone.contains(object)) {
            resetIfInitialPositionFree();
        }
    }

    @Override
    public boolean isObstacle(Entity entity) {
        if (entity instanceof RPEntity) {
            return true;
        }

        return super.isObstacle(entity);
    }

    @Override
    public void beforeMove(ActiveEntity entity, StendhalRPZone zone, int oldX,
            int oldY, int newX, int newY) {
        if (entity instanceof Player) {
            Rectangle2D oldA = new Rectangle2D.Double(oldX, oldY, entity.getWidth(), entity.getHeight());
            Rectangle2D newA = new Rectangle2D.Double(newX, newY, entity.getWidth(), entity.getHeight());
            Direction d = Direction.getAreaDirectionTowardsArea(oldA, newA);
            this.push((Player) entity, d);
        }
    }

    @Override
    public void onTurnReached(int currentTurn) {
        resetIfInitialPositionFree();
    }

    @Override
    public void onAdded(StendhalRPZone zone) {
        super.onAdded(zone);
        this.startX = getX();
        this.startY = getY();
        zone.addMovementListener(this);
        zone.addZoneEnterExitListener(this);
    }

    @Override
    public void onRemoved(StendhalRPZone zone) {
        super.onRemoved(zone);
        zone.removeMovementListener(this);
        zone.removeZoneEnterExitListener(this);
    }

    /**
     * Reset to initial position if no collision there, try again later if not
     * possible
     */
    private void resetIfInitialPositionFree() {
        if (!this.getZone().collides(this, this.startX, this.startY)) {
            this.reset();
        } else {
            // try again in a few moments
            SingletonRepository.getTurnNotifier().notifyInSeconds(RESET_AGAIN_DELAY, this);
        }
    }
}