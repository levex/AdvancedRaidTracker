package com.TheatreTracker.rooms.toa;

import com.TheatreTracker.TheatreTrackerConfig;
import com.TheatreTracker.TheatreTrackerPlugin;
import com.TheatreTracker.utility.datautility.DataWriter;
import net.runelite.api.Client;

public class WardensHandler extends TOARoomHandler
{
    public WardensHandler(Client client, DataWriter clog, TheatreTrackerConfig config, TheatreTrackerPlugin plugin)
    {
        super(client, clog, config, plugin);
    }
}
