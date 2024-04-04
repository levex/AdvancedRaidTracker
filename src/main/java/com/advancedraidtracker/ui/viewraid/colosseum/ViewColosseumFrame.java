package com.advancedraidtracker.ui.viewraid.colosseum;

import com.advancedraidtracker.AdvancedRaidTrackerConfig;
import com.advancedraidtracker.AdvancedRaidTrackerPlugin;
import com.advancedraidtracker.constants.RaidRoom;
import com.advancedraidtracker.rooms.col.ColosseumHandler;
import com.advancedraidtracker.ui.BaseFrame;
import com.advancedraidtracker.utility.Point;
import com.advancedraidtracker.utility.RoomUtil;
import com.advancedraidtracker.utility.UISwingUtility;
import com.advancedraidtracker.utility.datautility.ChartData;
import com.advancedraidtracker.utility.datautility.DataPoint;
import com.advancedraidtracker.utility.datautility.DataReader;
import com.advancedraidtracker.utility.datautility.datapoints.Raid;
import com.advancedraidtracker.utility.datautility.datapoints.col.Colo;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static com.advancedraidtracker.utility.UISwingUtility.*;
import static com.advancedraidtracker.utility.UISwingUtility.colorStr;
import static java.awt.Color.RED;

@Slf4j
public class ViewColosseumFrame extends BaseFrame
{
    String red = "<html><font color='#FF0000'>";
    String green = "<html><font color='#33FF33'>";
    String blue = "<html><font color='#6666DD'>";
    String orange = "<html><font color='#ddaa1c'>";
    String full;
    String soft;
    String dark;
    private final Colo colData;
    private final ItemManager itemManager;
    private final ChartData chartData;
    public ViewColosseumFrame(Colo colData, AdvancedRaidTrackerConfig config, ItemManager itemManager)
    {
        this.itemManager = itemManager;
        chartData = DataReader.getChartData(colData.getFilepath(), itemManager);
        Color c = config.fontColor();
        full = colorStr(c);
        soft = colorStr(c.darker());
        dark = colorStr(c.darker().darker());

        this.colData = colData;
        for(int i = 1; i < 13; i++)
        {
            log.info("Wave " + i + " spawn data: " + colData.getSpawnString(i));
        }
        setTitle("View Raid");
        setPreferredSize(new Dimension(800, 800));
        //add(new ViewColosseumPanel(colData, config));

        JPanel topContainer = getThemedPanel();
        topContainer.setPreferredSize(new Dimension(800, 150));
        topContainer.setLayout(new GridLayout(1, 2));
        topContainer.add(getSummaryBox());
        topContainer.add(getSolHereditBox());
        JPanel bottomContainer = getThemedPanel();
        bottomContainer.setPreferredSize(new Dimension(800, 600));
        bottomContainer.setLayout(new GridLayout(4,3));
        setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));
        for(int i = 0; i < 12; i++)
        {
            bottomContainer.add(getWaveBox(i+1));
        }
        add(topContainer);
        add(bottomContainer);
        setResizable(false);
        pack();
    }

    public JPanel getWaveBox(int wave)
    {
        String title = getColor(wave) + "Wave " + wave;
        int waveTime = colData.get("Wave " + wave + " Duration");
        if(waveTime > 0)
        {
            title += " - " + RoomUtil.time(waveTime);
        }
        JPanel container = getTitledPanel(title);
        container.setMaximumSize(new Dimension(266, 140));
        container.setLayout(new GridLayout(1, 2));
        JPanel base = getThemedPanel();
        base.setMaximumSize(new Dimension(140, 140));
        base.setLayout(new GridLayout(0, 1));
        String[] split = colData.getSpawnString(wave).split("");
        String spawnString = "https://supalosa.github.io/osrs-colosseum/?";
        final BufferedImage colImage = ImageUtil.loadImageResource(AdvancedRaidTrackerPlugin.class, "/com/advancedraidtracker/colosseum.png");
        Graphics2D g = (Graphics2D) colImage.getGraphics();
        g.setColor(RED);
        for(String s : split)
        {
            try
            {
                int ID = ColosseumHandler.mapToWebsiteIDs.getOrDefault(ColosseumHandler.getId(s), -1);
                Point p = ColosseumHandler.mapToWebsitePoint(ColosseumHandler.getCoordinates(s));
                spawnString += String.format("%02d", p.getX()) + String.format("%02d", p.getY()) + ID + ".";
                int size = ColosseumHandler.spawnSize.getOrDefault(ID, 0);
                g.setColor(ColosseumHandler.websiteColorMap.get(ID));
                g.drawRect(8*(p.getX()-1)-2, 8*(p.getY()-size), 8*size, 8*size);
            }
            catch (Exception ignore)
            {

            }
        }
        if(colData.highestWaveStarted >= wave)
        {
            base.add(getThemedLabel("<html>" + blue + "Invocations: "));
            for (String invo : colData.invocationsOffered.get(wave))
            {
                if (invo.equals(colData.invocationSelected.get(wave)))
                {
                    base.add(getThemedLabel("<html>&emsp" + green + ColosseumHandler.invoMap.get(Integer.parseInt(invo))));
                } else
                {
                    base.add(getThemedLabel("<html>&emsp" + UISwingUtility.fontColorString() + ColosseumHandler.invoMap.get(Integer.parseInt(invo))));
                }
            }
            base.add(getThemedLabel("<html>Prayer Used: " + blue + colData.get(DataPoint.PRAYER_USED, RaidRoom.getRoom("Wave " + wave))));
            base.add(getThemedLabel("<html>Damage Dealt: " + green + colData.get(DataPoint.DAMAGE_DEALT, RaidRoom.getRoom("Wave " + wave))));
            base.add(getThemedLabel("<html>Damage Received: " + red + colData.get(DataPoint.DAMAGE_RECEIVED, RaidRoom.getRoom("Wave " + wave))));
            int idleTicks = chartData.getIdleTicks(colData.getPlayerString(), RaidRoom.getRoom("Wave " + wave), colData.getScale());
            base.add(getThemedLabel("Idle Ticks: " + idleTicks));
            base.add(getThemedLabel("Skip Reinforce? No"));
            container.add(base);

            JLabel picLabel = new JLabel(new ImageIcon(colImage.getScaledInstance(128, 128, Image.SCALE_SMOOTH)));
            picLabel.setMaximumSize(new Dimension(140, 140));
            picLabel.setToolTipText("View spawn in website");
            picLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
            String finalSpawnString = spawnString;
            picLabel.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    try
                    {
                        Desktop.getDesktop().browse(new URI(finalSpawnString));
                    } catch (Exception ignored)
                    {

                    }
                }
            });
            container.add(picLabel);
        }
        return container;
    }

    public JPanel getSummaryBox()
    {
        String title = ((colData.isCompleted()) ? green : red) + "Summary - " + RoomUtil.time(colData.getChallengeTime());
        JPanel base = getTitledPanel(title);
        base.add(getThemedLabel("Total Idle Ticks: " + chartData.getIdleTicks(colData.getPlayerString(), colData.getScale())));
        return base;
    }

    public JPanel getSolHereditBox()
    {
        String title = (colData.isCompleted()) ? green : red;
        title += "Sol Heredit";
        title += (colData.get("Wave 12 Split") > 0) ? " - " + RoomUtil.time(colData.get("Wave 12 Duration")) : "";
        JPanel base = getTitledPanel(title);

        return base;
    }

    private String getColor(int wave)
    {
        String color = red;
        if(colData.get("Wave " + wave + " Duration") > 0)
        {
            color = green;
        }
        else if(colData.highestWaveStarted == wave)
        {
            color = orange;
        }
        return color;
    }

    private String getBodyColor(int wave)
    {
        String color = dark;
        if(colData.get("Wave " + wave + " Duration") > 0)
        {
            color = full;
        }
        return color;
    }
}
