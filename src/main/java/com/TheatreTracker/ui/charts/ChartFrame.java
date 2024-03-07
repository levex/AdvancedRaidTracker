package com.TheatreTracker.ui.charts;

import com.TheatreTracker.*;
import com.TheatreTracker.ui.BaseFrame;
import com.TheatreTracker.utility.AdvancedRaidData;
import com.TheatreTracker.utility.datautility.DataPoint;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;

import javax.swing.*;
import java.awt.*;
import java.util.*;

@Slf4j
public class ChartFrame extends BaseFrame
{
    public ChartFrame(SimpleRaidData roomData, TheatreTrackerConfig config, ItemManager itemManager, ClientThread clientThread, ConfigManager configManager)
    {
        JTabbedPane basepane = new JTabbedPane();
        AdvancedRaidData raidData;
        if (roomData instanceof SimpleTOBData)
        {
            raidData = new AdvancedTOBData(AdvancedRaidData.getRaidStrings(roomData.getFilePath()), itemManager);
        } else
        {
            raidData = new AdvancedTOAData(AdvancedRaidData.getRaidStrings(roomData.getFilePath()), itemManager);
        }
        for (String s : raidData.attackData.keySet())
        {
            JPanel tab = new JPanel();
            tab.setLayout(new GridLayout(1, 2));
            JPanel chart = new JPanel();
            chart.setLayout(new BoxLayout(chart, BoxLayout.Y_AXIS));
            ChartPanel chartPanel = new ChartPanel(s, false, config, clientThread, configManager);
            chartPanel.setNPCMappings(raidData.npcIndexData.get(s));
            chartPanel.addAttacks(raidData.attackData.get(s));
            chartPanel.setRoomHP(raidData.hpData.get(s));
            chartPanel.setPlayers(roomData.getPlayersArray());
            chartPanel.enableWrap();
            chartPanel.setStartTick((s.contains("Verzik") || s.contains("Wardens")) ? //Just trust
                    (s.contains("P1") ? 1 : (s.contains("P2") ? roomData.getValue(s.replace('2', '1') + " Time") :
                            roomData.getValue(s.replace('3', '1') + " Time") + roomData.getValue(s.replace('3', '2') + " Time"))) : 1);
            chartPanel.setTick(((s.contains("Verzik") || s.contains("Wardens")) && !s.contains("P1"))
                    ? (s.contains("P2")) ? roomData.getValue(s + " Time") +
                    roomData.getValue(s.replace('2', '1') + " Time") :
                    roomData.getValue(s.substring(0, s.length()-2) + "Time") : roomData.getValue(s + " Time"));
            chartPanel.addThrallBoxes(raidData.thrallOutlineBoxes.get(s));

            Map<Integer, String> lines = new LinkedHashMap<>();
            ArrayList<Integer> autos = new ArrayList<>();
            if(roomData instanceof SimpleTOBData)
            {
                SimpleTOBData tobData = (SimpleTOBData) roomData;
                switch (s)
                {
                    case "Maiden":
                        lines.put(roomData.getValue(DataPoint.MAIDEN_70_SPLIT), "70s");
                        lines.put(roomData.getValue(DataPoint.MAIDEN_50_SPLIT), "50s");
                        lines.put(roomData.getValue(DataPoint.MAIDEN_30_SPLIT), "30s");
                        lines.put(roomData.getValue(DataPoint.MAIDEN_TOTAL_TIME), "Dead");
                        chartPanel.addMaidenCrabs(tobData.maidenCrabSpawn);
                        break;
                    case "Bloat":
                        for (Integer i : tobData.bloatDowns)
                        {
                            lines.put(i, "Down");
                            lines.put(i + 33, "Moving");
                        }
                        break;
                    case "Nylocas":
                        for (Integer i : tobData.nyloWaveStalled)
                        {
                            lines.put(i, "Stall");
                        }
                        lines.put(roomData.getValue(DataPoint.NYLO_LAST_WAVE), "Last Wave");
                        lines.put(roomData.getValue(DataPoint.NYLO_BOSS_SPAWN), "Boss Spawn");
                        for (int i = roomData.getValue(DataPoint.NYLO_BOSS_SPAWN) + 11; i < tobData.getNyloTime(); i += 10)
                        {
                            lines.put(i, "Phase");
                        }

                        for (Integer i : tobData.waveSpawns.keySet())
                        {
                            lines.put(tobData.waveSpawns.get(i), "W" + i);
                        }
                        break;
                    case "Sotetseg":
                        lines.put(roomData.getValue(DataPoint.SOTE_P1_SPLIT), "Maze1 Start");
                        lines.put(roomData.getValue(DataPoint.SOTE_M1_SPLIT), "Maze1 End");
                        lines.put(roomData.getValue(DataPoint.SOTE_P2_SPLIT), "Maze2 Start");
                        lines.put(roomData.getValue(DataPoint.SOTE_M2_SPLIT), "Maze2 End");
                        break;
                    case "Xarpus":
                        lines.put(roomData.getValue(DataPoint.XARP_SCREECH), "SCREECH");
                        for (int i = roomData.getValue(DataPoint.XARP_SCREECH) + 8; i < roomData.getValue("Xarpus Time"); i += 8)
                        {
                            lines.put(i, "Turn");
                        }
                        break;
                    case "Verzik P1":
                        Map<Integer, String> dawnDropsMap = new LinkedHashMap<>();
                        for (Integer i : tobData.dawnDrops)
                        {
                            dawnDropsMap.put(i, "X");
                        }
                        chartPanel.addRoomSpecificDatum(dawnDropsMap);
                        for (int i = 19; i < tobData.getValue("Verzik P1 Time"); i++)
                        {
                            if (i == 19 || (i - 19) % 14 == 0)
                            {
                                autos.add(i);
                            }
                        }
                        chartPanel.setRoomSpecificText("Dawn Appears");
                        break;
                    case "Verzik P2":
                        for (Integer i : tobData.redsProc)
                        {
                            lines.put(i, "Reds");
                            lines.put(i + 10, "Shield End");
                        }
                        for (Integer i : tobData.p2Crabs)
                        {
                            lines.put(i, "Crabs");
                        }
                        int lastreset = tobData.getValue(DataPoint.VERZIK_P1_SPLIT) + 11;
                        for (int i = lastreset; i < tobData.getValue(DataPoint.VERZIK_P2_SPLIT); i++)
                        {
                            boolean wasNextTick = false;
                            for (Integer j : tobData.redsProc)
                            {
                                if (i == j)
                                {
                                    lastreset = i + 11;
                                } else if (i == (j - 5) || i == (j - 1))
                                {
                                    wasNextTick = true;
                                }
                            }
                            if ((i - lastreset) % 4 == 0 && i >= lastreset && !wasNextTick)
                            {
                                autos.add(i);
                            }
                        }
                        break;
                    case "Verzik P3":
                        for (Integer i : tobData.websStart)
                        {
                            if (i % 2 == 0)
                                lines.put(i, "Webs");
                        }
                        for (Integer i : tobData.p3Crabs)
                        {
                            lines.put(i, "Crabs");
                        }
                        break;
                }
            }
            chartPanel.addLines(lines);
            chartPanel.addAutos(autos);
            chartPanel.redraw();
            basepane.addChangeListener(cl -> chartPanel.redraw());
            chart.add(chartPanel);
            tab.add(new JScrollPane(chart));
            basepane.add(s, tab);
            add(basepane);
            pack();
        }
    }
}
