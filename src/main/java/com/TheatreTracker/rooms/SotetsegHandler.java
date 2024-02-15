package com.TheatreTracker.rooms;

import com.TheatreTracker.TheatreTrackerConfig;
import com.TheatreTracker.TheatreTrackerPlugin;
import com.TheatreTracker.constants.LogID;
import com.TheatreTracker.constants.TOBRoom;
import com.TheatreTracker.utility.datautility.DataWriter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcSpawned;
import com.TheatreTracker.utility.RoomState;

import static com.TheatreTracker.constants.TobIDs.*;

@Slf4j
public class SotetsegHandler extends RoomHandler
{
    public RoomState.SotetsegRoomState roomState = RoomState.SotetsegRoomState.NOT_STARTED;
    private int soteEntryTick = -1;
    private int soteFirstMazeStart = -1;
    private int soteSecondMazeStart = -1;
    private int soteFirstMazeEnd = -1;
    private int soteSecondMazeEnd = -1;
    private int soteDeathTick = -1;
    private int deferTick = -1;
    private int lastRegion = -1;
    private final TheatreTrackerPlugin plugin;

    public SotetsegHandler(Client client, DataWriter clog, TheatreTrackerConfig config, TheatreTrackerPlugin plugin)
    {
        super(client, clog, config);
        this.plugin = plugin;
    }

    public boolean isActive()
    {
        return !(roomState == RoomState.SotetsegRoomState.NOT_STARTED || roomState == RoomState.SotetsegRoomState.FINISHED);
    }

    public String getName()
    {
        return "Sotetseg";
    }

    public void reset()
    {
        super.reset();
        accurateTimer = true;
        soteEntryTick = -1;
        roomState = RoomState.SotetsegRoomState.NOT_STARTED;
        soteFirstMazeStart = -1;
        soteSecondMazeStart = -1;
        soteFirstMazeEnd = -1;
        soteSecondMazeEnd = -1;
        soteDeathTick = -1;
        lastRegion = -1;
    }

    public void updateNpcSpawned(NpcSpawned event)
    {
        int id = event.getNpc().getId();
        if (id == SOTETSEG_ACTIVE || id == SOTETSEG_ACTIVE_HM || id == SOTETSEG_ACTIVE_SM)
        {
            if (lastRegion == SOTETSEG_UNDERWORLD)
            {
                if (roomState == RoomState.SotetsegRoomState.MAZE_1)
                {
                    endFirstMaze();
                } else if (roomState == RoomState.SotetsegRoomState.MAZE_2)
                {
                    endSecondMaze();
                }
            }
        }
    }

    public void updateAnimationChanged(AnimationChanged event)
    {
        if (event.getActor().getAnimation() == SOTETSEG_DEATH_ANIMATION)
        {
            endSotetseg();
        }
    }

    public void startSotetseg()
    {
        soteEntryTick = client.getTickCount();
        roomStartTick = client.getTickCount();
        deferTick = soteEntryTick + 2;
        roomState = RoomState.SotetsegRoomState.PHASE_1;
        clog.write(LogID.SOTETSEG_STARTED);
    }

    public void endSotetseg()
    {
        plugin.addDelayedLine(TOBRoom.SOTETSEG, client.getTickCount() - soteEntryTick, "Dead");
        soteDeathTick = client.getTickCount() + SOTETSEG_DEATH_ANIMATION_LENGTH;
        roomState = RoomState.SotetsegRoomState.FINISHED;
        clog.write(LogID.ACCURATE_SOTE_END);
        clog.write(LogID.SOTETSEG_ENDED, String.valueOf(soteDeathTick - soteEntryTick));
        plugin.liveFrame.setSoteFinished(soteDeathTick - soteEntryTick);
        sendTimeMessage("Wave 'Sotetseg phase 3' complete. Duration: ", soteDeathTick - soteEntryTick, soteDeathTick - soteSecondMazeEnd, false);
    }

    public void startFirstMaze()
    {
        soteFirstMazeStart = client.getTickCount();
        clog.write(LogID.SOTETSEG_FIRST_MAZE_STARTED, String.valueOf(soteFirstMazeStart - soteEntryTick));
        roomState = RoomState.SotetsegRoomState.MAZE_1;
        sendTimeMessage("Wave 'Sotetseg phase 1' complete. Duration: ", soteFirstMazeStart - soteEntryTick);
        plugin.addDelayedLine(TOBRoom.SOTETSEG, soteFirstMazeStart - soteEntryTick, "Maze1 Start");
    }

    public void endFirstMaze()
    {
        soteFirstMazeEnd = client.getTickCount();
        clog.write(LogID.SOTETSEG_FIRST_MAZE_ENDED, String.valueOf(soteFirstMazeEnd - soteEntryTick));
        roomState = RoomState.SotetsegRoomState.PHASE_2;
        sendTimeMessage("Wave 'Sotetseg maze 1' complete. Duration: ", soteFirstMazeEnd - soteEntryTick, soteFirstMazeEnd - soteFirstMazeStart);
        plugin.addDelayedLine(TOBRoom.SOTETSEG, soteFirstMazeEnd - soteEntryTick, "Maze1 End");
    }

    public void startSecondMaze()
    {
        soteSecondMazeStart = client.getTickCount();
        clog.write(LogID.SOTETSEG_SECOND_MAZE_STARTED, String.valueOf(soteSecondMazeStart - soteEntryTick));
        roomState = RoomState.SotetsegRoomState.MAZE_2;
        sendTimeMessage("Wave 'Sotetseg phase 2' complete. Duration: ", soteSecondMazeStart - soteEntryTick, soteSecondMazeStart - soteFirstMazeEnd);
        plugin.addDelayedLine(TOBRoom.SOTETSEG, soteSecondMazeStart - soteEntryTick, "Maze2 Start");
    }

    public void endSecondMaze()
    {
        soteSecondMazeEnd = client.getTickCount();
        clog.write(LogID.SOTETSEG_SECOND_MAZE_ENDED, String.valueOf(soteSecondMazeEnd - soteEntryTick));
        roomState = RoomState.SotetsegRoomState.PHASE_3;
        sendTimeMessage("Wave 'Sotetseg maze 2' complete. Duration: ", soteSecondMazeEnd - soteEntryTick, soteSecondMazeEnd - soteSecondMazeStart);
        plugin.addDelayedLine(TOBRoom.SOTETSEG, soteSecondMazeEnd - soteEntryTick, "Maze2 End");
    }

    public void updateGameTick(GameTick event)
    {
        lastRegion = client.isInInstancedRegion() ? WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID() : client.getLocalPlayer().getWorldLocation().getRegionID();

        if (client.getTickCount() == deferTick)
        {
            deferTick = -1;
            if (client.getVarbitValue(HP_VARBIT) == FULL_HP)
            {
                clog.write(LogID.ACCURATE_SOTE_START);
            }
        }
    }

    public void handleNPCChanged(int id)
    {
        if (id == SOTETSEG_ACTIVE || id == SOTETSEG_ACTIVE_HM || id == SOTETSEG_ACTIVE_SM)
        {
            if (roomState == RoomState.SotetsegRoomState.NOT_STARTED)
            {
                if (id == SOTETSEG_ACTIVE_HM)
                {
                    clog.write(LogID.IS_HARD_MODE);
                } else if (id == SOTETSEG_ACTIVE_SM)
                {
                    clog.write(LogID.IS_STORY_MODE);
                }
                startSotetseg();
            } else if (roomState == RoomState.SotetsegRoomState.MAZE_1)
            {
                endFirstMaze();
            } else if (roomState == RoomState.SotetsegRoomState.MAZE_2)
            {
                endSecondMaze();
            }
        } else if (id == SOTETSEG_INACTIVE || id == SOTETSEG_INACTIVE_HM || id == SOTETSEG_INACTIVE_SM)
        {
            if (roomState == RoomState.SotetsegRoomState.PHASE_1)
            {
                startFirstMaze();
            } else if (roomState == RoomState.SotetsegRoomState.PHASE_2)
            {
                startSecondMaze();
            }
        }
    }
}
