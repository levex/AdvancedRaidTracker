package com.cTimers.filters;

import com.cTimers.filters.cFilterCondition;
import lombok.extern.slf4j.Slf4j;
import com.cTimers.cRoomData;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
@Slf4j
public class cFilterPlayers extends cFilterCondition
{
    private ArrayList<String> players;
    private int operator;
    private String stringValue;
    public cFilterPlayers(String players, int operator, String val)
    {
        this.players = new ArrayList<String>();
        String[] playerNames = players.split(",");
        for (String playerName : playerNames) {
            this.players.add(playerName.replaceAll(String.valueOf((char) 160), String.valueOf((char) 32)).toLowerCase());
        }
        this.operator = operator;
        stringValue = val;
    }

    private boolean cleanContains(cRoomData data, String player)
    {
        for(String p : data.players)
        {
            if(p.replaceAll(String.valueOf((char) 160), String.valueOf((char) 32)).toLowerCase().equals(player))
            {
                return true;
            }
        }
        return false;
    }
    @Override
    public String toString()
    {
        return stringValue;
    }
    @Override
    public boolean evaluate(cRoomData data)
    {
        switch(operator)
        {
            case 0:
                if(data.players.size() == players.size())
                {
                    for(String p : players)
                    {
                        if(!cleanContains(data, p))
                        {
                            return false;
                        }
                    }
                    return true;
                }
                return false;
            case 1:
                boolean flag = true;
                for(String p : players)
                {
                    if(!cleanContains(data, p))
                    {
                        flag = false;
                    }
                }
                return flag;
            case 2:
                for(String p : players)
                {
                    if(cleanContains(data, p))
                    {
                        return true;
                    }
                }
                return false;
            case 3:
                for(String p : players)
                {
                    if(!cleanContains(data, p))
                    {
                        return true;
                    }
                }
                return false;
            case 4:
                for(String p : players)
                {
                    if(cleanContains(data, p))
                    {
                        return false;
                    }
                }
                return true;
        }
        return false;
    }

    public String getFilterCSV()
    {
        String playerStr = "";
        for(String s : players)
        {
            playerStr += s;
            playerStr += ",";
        }
        playerStr = StringUtils.substring(playerStr, 0, playerStr.length()-1);
        return "2-" + operator + "-" + playerStr + "-" + stringValue;
    }
}
