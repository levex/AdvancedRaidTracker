package com.TheatreTracker.rooms;

import com.TheatreTracker.TheatreTrackerConfig;
import com.TheatreTracker.constants.NpcIDs;
import com.TheatreTracker.utility.DataWriter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.events.*;
import com.TheatreTracker.TheatreTrackerPlugin;
import com.TheatreTracker.utility.RoomState;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.TheatreTracker.constants.LogID.*;
import static com.TheatreTracker.constants.NpcIDs.*;

@Slf4j
public class MaidenHandler extends RoomHandler
{
    public RoomState.MaidenRoomState roomState;

    int maidenStartTick;
    int p70;
    int p50;
    int p30;
    int maidenDeathTick;
    NPC maidenNPC;

    public int deferVarbitCheck = -1;
    ArrayList<MaidenCrab> maidenCrabs = new ArrayList<>();


    public MaidenHandler(Client client, DataWriter clog, TheatreTrackerConfig config)
    {
        super(client, clog, config);
        p70 = -1;
        p50 = -1;
        p30 = -1;
        maidenStartTick = -1;
        maidenDeathTick = -1;
        accurateEntry = true;
    }

    public void reset()
    {
        accurateEntry = true;
        p70 = -1;
        p50 = -1;
        p30 = -1;
        maidenStartTick = -1;
        maidenDeathTick = -1;
    }

    public void startMaiden()
    {
        maidenStartTick = client.getTickCount();
        deferVarbitCheck = maidenStartTick+2;
    }

    public void proc70()
    {
        p70 = super.client.getTickCount();
        roomState = RoomState.MaidenRoomState.PHASE_2;
        if(maidenStartTick != -1)
            sendTimeMessage("Wave 'Maiden phase 1' complete! Duration: ", p70-maidenStartTick);
        clog.write(MAIDEN_70S, ""+(p70-maidenStartTick));

    }

    public void proc50()
    {
        p50 = super.client.getTickCount();
        roomState = RoomState.MaidenRoomState.PHASE_3;
        if(maidenStartTick != -1)
            sendTimeMessage("Wave 'Maiden phase 2' complete! Duration: ", p50-maidenStartTick, p50-p70);
        clog.write(MAIDEN_50S, ""+(p50-maidenStartTick));
    }

    public void proc30()
    {
        p30 = super.client.getTickCount();
        roomState = RoomState.MaidenRoomState.PHASE_4;
        if(maidenStartTick != -1)
            sendTimeMessage("Wave 'Maiden phase 3' complete! Duration: ", p30-maidenStartTick, p30-p50);
        clog.write(MAIDEN_30S, ""+(p30-maidenStartTick));
    }

    public void endMaiden()
    {
        roomState = RoomState.MaidenRoomState.FINISHED;
        maidenDeathTick = client.getTickCount()+7;
        if(maidenStartTick != -1)
            sendTimeMessage("Wave 'Maiden Skip' complete! Duration: ", maidenDeathTick-maidenStartTick,maidenDeathTick-p30, false);
        clog.write(301);
        clog.write(MAIDEN_0HP, ""+(client.getTickCount()-maidenStartTick));
    }


    public void updateAnimationChanged(AnimationChanged event)
    {
        if(event.getActor().getAnimation() == 8093)
        {
            endMaiden();
        }
    }

    public void updateNpcDespawned(NpcDespawned event)
    {
        NPC npc = event.getNpc();
        switch(npc.getId())
        {
            case NpcIDs.MAIDEN_P0:
            case NpcIDs.MAIDEN_P1:
            case NpcIDs.MAIDEN_P2:
            case NpcIDs.MAIDEN_P3:
            case NpcIDs.MAIDEN_PRE_DEAD:
            case NpcIDs.MAIDEN_DEAD:
            case NpcIDs.MAIDEN_P0_HM:
            case NpcIDs.MAIDEN_P1_HM:
            case NpcIDs.MAIDEN_P2_HM:
            case NpcIDs.MAIDEN_P3_HM:
            case NpcIDs.MAIDEN_PRE_DEAD_HM:
            case NpcIDs.MAIDEN_DEAD_HM:
            case NpcIDs.MAIDEN_P0_SM:
            case NpcIDs.MAIDEN_P1_SM:
            case NpcIDs.MAIDEN_P2_SM:
            case NpcIDs.MAIDEN_P3_SM:
            case NpcIDs.MAIDEN_PRE_DEAD_SM:
            case NpcIDs.MAIDEN_DEAD_SM:
                clog.write(MAIDEN_DESPAWNED, ""+(client.getTickCount()-maidenStartTick));
                break;
            case MAIDEN_MATOMENOS:
            case MAIDEN_MATOMENOS_HM:
            case MAIDEN_MATOMENOS_SM:
                maidenCrabs.removeIf(x -> x.crab.equals(event.getNpc()));
            default:
                break;
        }
    }

    public void updateNpcSpawned(NpcSpawned event)
    {

        NPC npc = event.getNpc();
        boolean story = false;
        switch(npc.getId())
        {
            case MAIDEN_P0_SM:
            case MAIDEN_P1_SM:
            case MAIDEN_P2_SM:
            case MAIDEN_P3_SM:
            case MAIDEN_PRE_DEAD_SM:
            case MAIDEN_DEAD_SM:
                story = true;
                clog.write(IS_STORY_MODE);
            case MAIDEN_P0_HM:
            case MAIDEN_P1_HM:
            case MAIDEN_P2_HM:
            case MAIDEN_P3_HM:
            case MAIDEN_PRE_DEAD_HM:
            case MAIDEN_DEAD_HM:
                if(!story)
                    clog.write(IS_HARD_MODE);
            case MAIDEN_P0:
            case MAIDEN_P1:
            case MAIDEN_P2:
            case MAIDEN_P3:
            case MAIDEN_DEAD:
                clog.write(MAIDEN_SPAWNED);
                maidenNPC = npc;
                startMaiden();
                break;
            case MAIDEN_MATOMENOS:
            case MAIDEN_MATOMENOS_HM:
            case MAIDEN_MATOMENOS_SM:
                MaidenCrab crab = new MaidenCrab(npc, TheatreTrackerPlugin.scale, identifySpawn(npc));
                logCrabSpawn(crab.description);
                maidenCrabs.add(crab);
                break;
            case NpcIDs.MAIDEN_BLOOD:
            case NpcIDs.MAIDEN_BLOOD_HM:
            case NpcIDs.MAIDEN_BLOOD_SM:
                clog.write(BLOOD_SPAWNED);
                break;
        }
    }

    public void handleNPCChanged(int id)
    {
        switch(id)
        {
            case MAIDEN_P1:
            case MAIDEN_P1_HM:
            case MAIDEN_P1_SM:
                proc70();
                break;
            case MAIDEN_P2:
            case MAIDEN_P2_HM:
            case MAIDEN_P2_SM:
                proc50();
                break;
            case MAIDEN_P3:
            case MAIDEN_P3_HM:
            case MAIDEN_P3_SM:
                proc30();
                break;
            case MAIDEN_DEAD:
            case MAIDEN_DEAD_HM:
            case MAIDEN_DEAD_SM:
                break;
        }
    }

    private void logCrabSpawn(String description) {
        clog.write(MATOMENOS_SPAWNED, description);
    }

    public void updateGameTick(GameTick event)
    {
        if(client.getTickCount() == deferVarbitCheck)
        {
            deferVarbitCheck = -1;
            if(client.getVarbitValue(HP_VARBIT) != FULL_HP)
            {
                accurateEntry = false;
            }
            else
            {
                accurateEntry = true;
                roomState = RoomState.MaidenRoomState.PHASE_1;
                clog.write(ACCURATE_MAIDEN_START);
            }
        }
        // Check for crabs that are leaking on this game tick
        List<MaidenCrab> leaked_crabs= maidenCrabs.stream().filter(crab -> (crab.getCrab().getWorldArea().distanceTo2D(maidenNPC.getWorldArea()) - 1 == 0) && (crab.health > 0)).collect(Collectors.toList());
        for (MaidenCrab crab: leaked_crabs) {
            { // TODO replace with distance method in MaidenCrab
                clog.write(CRAB_LEAK, crab.description, String.valueOf(crab.health));
                // TODO add mising parameters (room time, current maiden health)
                // TODO check what happens if someone hits a crab on the last tick possible - is hp correct? does sthis
            }
        }
        maidenCrabs.removeAll(leaked_crabs);
    }

    /**
     * Tracks crab hps
     */
    public void updateHitsplatApplied(HitsplatApplied event) {
        if (maidenCrabs.stream().map(x -> x.crab).collect(Collectors.toList()).contains(event.getActor())) {
            MaidenCrab crab = maidenCrabs.stream().filter(x -> x.crab.equals(event.getActor())).collect(Collectors.toList()).get(0);
            crab.health -= event.getHitsplat().getAmount();
        }
    }

    /**
     * Returns a string describing the spawn position of a maiden crab
     * @param npc
     * @return
     */
    private String identifySpawn(NPC npc)
    {
        int x = npc.getWorldLocation().getRegionX();
        int y = npc.getWorldLocation().getRegionY();
        String proc = "";
        if(x == 21 && y == 40)
        {
            return "N1";
        }
        if(x == 22 && y == 41)
        {
            clog.write(MAIDEN_SCUFFED, "N1");
            return "N1";
        }
        if(x == 25 && y == 40)
        {
            return "N2";
        }
        if(x == 26 && y == 41)
        {
            clog.write(MAIDEN_SCUFFED, "N2");
            return "N2";
        }
        if(x == 29 && y == 40)
        {
            return "N3";
        }
        if(x == 30 && y == 41)
        {
            clog.write(MAIDEN_SCUFFED, "N3");
            return "N3";
        }
        if(x == 33 && y == 40)
        {
            return "N4 (1)";
        }
        if(x == 34 && y == 41)
        {
            clog.write(MAIDEN_SCUFFED, "N4 (1)");
            return "N4 (1)";
        }
        if(x == 33 && y == 38)
        {
            return "N4 (2)";
        }
        if(x == 34 && y == 39)
        {
            clog.write(MAIDEN_SCUFFED, "N4 (2)");
            return "N4 (2)";
        }
        //
        if(x == 21 && y == 20)
        {
            return "S1";
        }
        if(x == 22 && y == 19)
        {
            clog.write(MAIDEN_SCUFFED, "S1");
            return "S1";
        }
        if(x == 25 && y == 20)
        {
            return "S2";
        }
        if(x == 26 && y == 19)
        {
            clog.write(MAIDEN_SCUFFED, "S2");
            return "S2";
        }
        if(x == 29 && y == 20)
        {
            return "S3";
        }
        if(x == 30 && y == 19)
        {
            clog.write(MAIDEN_SCUFFED, "S3");
            return "S3";
        }
        if(x == 33 && y == 20)
        {
            return "S4 (1)";
        }
        if(x == 34 && y == 19)
        {
            clog.write(MAIDEN_SCUFFED, "S4 (1)");
            return "S4 (1)";
        }
        if(x == 33 && y == 22)
        {
            return "S4 (2)";
        }
        if(x == 34 && y == 20)
        {
            clog.write(MAIDEN_SCUFFED, "S4 (2)");
            return "S4 (2)";
        }
        else throw new InvalidParameterException("Impossible crab spawn data at maiden");
    }

    private class MaidenCrab
    {
        @Getter
        NPC crab;
        int maxHealth;
        int health;
        String description;

        public MaidenCrab(NPC crab, int scale, String description)
        {
            switch (scale)
            {
                case 5: maxHealth = 100; break;
                case 4: maxHealth = 87; break;
                default: maxHealth = 75; break;
            }
            this.crab = crab;
            health = maxHealth;
            this.description = description;
        }
    }
}