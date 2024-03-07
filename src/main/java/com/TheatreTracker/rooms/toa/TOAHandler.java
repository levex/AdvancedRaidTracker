package com.TheatreTracker.rooms.toa;

import com.TheatreTracker.constants.LogID;
import com.TheatreTracker.utility.datautility.DataWriter;
import net.runelite.api.Client;

public class TOAHandler
{
    private boolean started = false;
    public int startTick = -1;

    private final Client client;
    private final DataWriter clog;
    public TOAHandler(Client client, DataWriter clog)
    {
        this.client = client;
        this.clog = clog;
    }
    public boolean isActive()
    {
        return started;
    }
    public void reset()
    {
        started = false;
        startTick = -1;
    }

    public void start()
    {
        started = true;
        startTick = client.getTickCount();
        clog.addLine(LogID.TOA_TIMER_START, startTick);
    }
}
