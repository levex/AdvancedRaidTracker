package com.advancedraidtracker.utility.datautility.datapoints;

import com.advancedraidtracker.constants.RaidRoom;
import com.advancedraidtracker.utility.datautility.DataPoint;
import com.advancedraidtracker.utility.datautility.MultiRoomDataPoint;
import com.advancedraidtracker.utility.datautility.MultiRoomPlayerDataPoint;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
@Slf4j
public class RoomDataManager
{
    Map<DataPoint, Integer> map;
    Map<MultiRoomDataPoint, Integer> roomSpecificMap;
    Map<MultiRoomPlayerDataPoint, Map<String, Integer>> roomPlayerSpecificMap;

    public RoomDataManager()
    {
        map = new HashMap<>();
        roomSpecificMap = new HashMap<>();
        roomPlayerSpecificMap = new HashMap<>();
    }

    public void increment(MultiRoomPlayerDataPoint point, String player)
    {
        incrementBy(point, 1, player);
    }

    public void increment(DataPoint point, String player)
    {
        incrementBy(point, 1, player);
    }

    public void incrementBy(DataPoint point, int value, String player)
    {
        map.merge(point, value, Integer::sum);
    }

    public void incrementBy(MultiRoomPlayerDataPoint point, int value, String player)
    {
        roomPlayerSpecificMap.getOrDefault(point, new HashMap<>()).merge(player, value, Integer::sum);
    }

    public void set(DataPoint point, int value)
    {
        map.put(point, value);
    }

    public void set(MultiRoomDataPoint point, int value)
    {
        roomSpecificMap.put(point, value);
    }

    public int get(DataPoint point)
    {
        return map.getOrDefault(point, -1);
    }

    public int get(String point)
    {
        return get(DataPoint.getValue(point)); // todo make more efficient
    }

    public void dwh(MultiRoomDataPoint point)
    {
        double defense = roomSpecificMap.getOrDefault(point, 0); //fix defense todo
        defense *= .7;
        roomSpecificMap.put(point, (int)defense);
    }

    public void bgs(MultiRoomDataPoint point, int damage)
    {
        int defense = roomSpecificMap.getOrDefault(point, 0); //todo fix defense
        defense = Math.max(defense-damage, 0);
        roomSpecificMap.put(point, defense);
    }

    public int get(MultiRoomDataPoint point, RaidRoom room)
    {
        return roomSpecificMap.getOrDefault(point, 0);
    }

    public void dumpValues() //used for testing only
    {
        log.info("DataPoint: ");
        for(DataPoint point : map.keySet())
        {
            log.info(point.name + ": " + map.get(point));
        }
        log.info("MultiRoomDataPoint: ");
        for(MultiRoomDataPoint point : roomSpecificMap.keySet())
        {
            log.info(point.name + ": " + roomSpecificMap.get(point));
        }
        log.info("MultiRoomPlayerDataPoint: ");
        for(MultiRoomPlayerDataPoint point : roomPlayerSpecificMap.keySet())
        {
            for(String player : roomPlayerSpecificMap.get(point).keySet())
            {
                log.info("Player: " + player + ", " + point.name() + ": " + roomPlayerSpecificMap.get(point).get(player));
            }
        }
    }

}
