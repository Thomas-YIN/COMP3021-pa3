package hk.ust.cse.comp3021.pa3.util;

import hk.ust.cse.comp3021.pa3.model.Direction;
import hk.ust.cse.comp3021.pa3.model.GameState;
import hk.ust.cse.comp3021.pa3.model.MoveResult;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The Robot is an automated worker that can delegate the movement control of a player.
 * <p>
 * It implements the {@link MoveDelegate} interface and
 * is used by {@link hk.ust.cse.comp3021.pa3.view.panes.GameControlPane#delegateControl(MoveDelegate)}.
 */
public class Robot implements MoveDelegate {
    public enum Strategy {
        Random, Smart
    }

    private final AtomicBoolean robotEnabled = new AtomicBoolean(false);
    private static Direction lastDirection = null;
    private static MoveResult lastResult = null;
    /**
     * A generator to get the time interval before the robot makes the next move.
     */
    public static Generator<Long> timeIntervalGenerator = TimeIntervalGenerator.everySecond();

    /**
     * e.printStackTrace();
     * The game state of thee.printStackTrace(); player that the robot delegates.
     */
    private final GameState gameState;

    /**
     * The strategy of this instance of robot.
     */
    private final Strategy strategy;

    public Robot(GameState gameState) {
        this(gameState, Strategy.Random);
    }

    public Robot(GameState gameState, Strategy strategy) {
        this.strategy = strategy;
        this.gameState = gameState;
    }

    /**
     * TODO Start the delegation in a new thread.
     * The delegation should run in a separate thread.
     * This method should return immediately when the thread is started.
     * <p>
     * In the delegation of the control of the player,
     * the time interval between moves should be obtained from {@link Robot#timeIntervalGenerator}.
     * That is to say, the new thread should:
     * <ol>
     *   <li>Stop all existing threads by calling {@link Robot#stopDelegation()}</li>
     *   <li>Start a new thread. And inside the thread:</li>
     *   <ul>
     *      <li>Wait for some time (obtained from {@link TimeIntervalGenerator#next()}</li>
     *      <li>Make a move, call {@link Robot#makeMoveRandomly(MoveProcessor)} or
     *      {@link Robot#makeMoveSmartly(MoveProcessor)} according to {@link Robot#strategy}</li>
     *      <li>repeat</li>
     *   </ul>
     * </ol>
     * The started thread should be able to exit when {@link Robot#stopDelegation()} is called.
     * <p>
     *
     * @param processor The processor to make movements.
     */
    @Override
    public void startDelegation(@NotNull MoveProcessor processor) {
        this.stopDelegation();
        //Set the flag to be true
        robotEnabled.set(true);
        //Create new thread
        new Thread(() -> {
            while(true){
                synchronized(this){
                    notifyAll();
                    var interval = timeIntervalGenerator.next();
                    try{
                        Thread.sleep(interval);
                    }catch (InterruptedException e){
                        e.printStackTrace();
                    }
                    //Check if the robot has been disabled
                    try{
                        while(!robotEnabled.get())
                            wait();
                    }catch (InterruptedException e){
                        e.printStackTrace();
                    }
                    //If the robot is still running, make the move
                    if(this.strategy == Strategy.Random){
                        this.makeMoveRandomly(processor);
                    }else if(this.strategy == Strategy.Smart){
                        this.makeMoveSmartly(processor);
                    }
                }
            }
        }).start();
    }

    /**
     * TODO Stop the delegations, i.e., stop the thread of this instance.
     * When this method returns, the thread must have exited already.
     */
    @Override
    public void stopDelegation() {
        robotEnabled.set(false);
    }

    private MoveResult tryMove(Direction direction) {
        var player = gameState.getPlayer();
        if (player.getOwner() == null) {
            return null;
        }
        var r = gameState.getGameBoardController().tryMove(player.getOwner().getPosition(), direction, player.getId());
        return r;
    }

    /**
     * The robot moves randomly but rationally,
     * which means the robot will not move to a direction that will make the player die if there are other choices,
     * but for other non-dying directions, the robot just randomly chooses one.
     * If there is no choice but only have one dying direction to move, the robot will still choose it.
     * If there is no valid direction, i.e. can neither die nor move, the robot do not perform a move.
     * <p>
     * TODO modify this method if you need to do thread synchronization.
     *
     * @param processor The processor to make movements.
     */
    private void makeMoveRandomly(MoveProcessor processor) {
        var directions = new ArrayList<>(Arrays.asList(Direction.values()));
        Collections.shuffle(directions);
        Direction aliveDirection = null;
        Direction deadDirection = null;
        for (var direction :
                directions) {
            var result = tryMove(direction);
            if (result instanceof MoveResult.Valid.Alive) {
                aliveDirection = direction;
            } else if (result instanceof MoveResult.Valid.Dead) {
                deadDirection = direction;
            }
        }
        if (aliveDirection != null) {
            processor.move(aliveDirection);
        } else if (deadDirection != null) {
            processor.move(deadDirection);
        }
        //Stop the thread if the robot has died
        if(this.gameState.hasLost()){
            stopDelegation();
        }
    }

    /**
     * TODO implement this method
     * The robot moves with a smarter strategy compared to random.
     * This strategy is expected to beat random strategy in most of the time.
     * That is to say we will let random robot and smart robot compete with each other and repeat many (>10) times
     * (10 seconds timeout for each run).
     * You will get the grade if the robot with your implementation can win in more than half of the total runs
     * (e.g., at least 6 when total is 10).
     * <p>
     *
     * @param processor The processor to make movements.
     */
    private void makeMoveSmartly(MoveProcessor processor) {
        var directions = new ArrayList<>(Arrays.asList(Direction.values()));

        Direction aliveDirection = null;
        Direction deadDirection = null;
        Direction optimalDirection = null;
        Direction invalidDirection = null;
        Direction exclude = null;

        int maxNumGems = 0;
        Collections.shuffle(directions);

        if(lastDirection != null && !(lastResult instanceof MoveResult.Invalid)){
            if (lastDirection.compareTo(Direction.UP) == 0){
                exclude = Direction.DOWN;
            }else if(lastDirection.compareTo(Direction.DOWN) == 0){
                exclude = Direction.UP;
            }else if(lastDirection.compareTo(Direction.LEFT) == 0){
                exclude = Direction.RIGHT;
            }else {
                exclude = Direction.LEFT;
            }
        }

        directions.remove(exclude);
        for (var direction : directions) {
            var result = tryMove(direction);
            if (result instanceof MoveResult.Valid.Alive) {
                int thisNumGems = ((MoveResult.Valid.Alive) result).collectedGems.size();
                if(thisNumGems > maxNumGems){
                    maxNumGems = thisNumGems;
                    optimalDirection = direction;
                }else{
                    aliveDirection = direction;
                }
            } else if(result instanceof MoveResult.Invalid){
                invalidDirection = direction;
            } else if (result instanceof MoveResult.Valid.Dead) {
                deadDirection = direction;
            }
        }
        //Extra rule: do not go back to the same direction
        if(optimalDirection != null){
            lastDirection = optimalDirection;
            lastResult = tryMove(optimalDirection);
            processor.move(optimalDirection);
        }else if (aliveDirection != null) {
            lastDirection = aliveDirection;
            lastResult = tryMove(aliveDirection);
            processor.move(aliveDirection);
        } else if (invalidDirection != null){
            lastDirection = invalidDirection;
            lastResult = tryMove(invalidDirection);
            processor.move(invalidDirection);
        } else if (deadDirection != null) {
            lastDirection = deadDirection;
            lastResult = tryMove(deadDirection);
            processor.move(deadDirection);
        }
        //Stop the thread if the robot has died
        if(this.gameState.hasLost()){
            stopDelegation();
        }
    }

}
