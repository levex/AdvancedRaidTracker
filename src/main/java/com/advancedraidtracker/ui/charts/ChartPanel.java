package com.advancedraidtracker.ui.charts;

import com.advancedraidtracker.AdvancedRaidTrackerConfig;
import com.advancedraidtracker.constants.RaidRoom;
import com.advancedraidtracker.constants.TobIDs;
import com.advancedraidtracker.utility.*;
import com.advancedraidtracker.utility.Point;
import com.advancedraidtracker.utility.weapons.PlayerAnimation;
import com.advancedraidtracker.utility.weapons.AnimationDecider;
import com.advancedraidtracker.utility.wrappers.*;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static com.advancedraidtracker.utility.UISwingUtility.drawStringRotated;

@Slf4j
public class ChartPanel extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener
{
    private final int TITLE_BAR_PLUS_TAB_HEIGHT = 63;
    private boolean shouldWrap;
    private BufferedImage img;
    int scale;
    int boxCount;
    int boxHeight;
    int boxWidth;
    private int windowHeight = 600;
    int instanceTime = 0;

    int selectedTick = -1;
    String selectedPlayer = "";

    int selectedRow = -1;
    boolean checkBoxHovered = false;
    boolean checkBox2Hovered = false;
    boolean checkBox3Hovered = false;

    int startTick;
    public int endTick;
    @Setter
    List<String> players = new ArrayList<>();
    String room;
    @Setter
    String roomSpecificText = "";
    private int fontHeight;
    public boolean finished = false;
    private final boolean live;
    private final ArrayList<Integer> autos = new ArrayList<>();
    private Map<Integer, String> NPCMap = new HashMap<>();
    private final ArrayList<DawnSpec> dawnSpecs = new ArrayList<>();
    private final ArrayList<ThrallOutlineBox> thrallOutlineBoxes = new ArrayList<>();
    private final ArrayList<OutlineBox> outlineBoxes = new ArrayList<>();
    private final Map<Integer, String> specific = new HashMap<>();
    private final Map<Integer, String> lines = new HashMap<>();
    private final ArrayList<String> crabDescriptions = new ArrayList<>();

    @Setter
    private Map<Integer, Integer> roomHP = new HashMap<>();
    Map<String, Integer> playerOffsets = new LinkedHashMap<>();
    private final Map<PlayerDidAttack, String> actions = new HashMap<>();

    private ConfigManager configManager;

    @Setter
    private boolean isActive = false;

    public boolean shouldDraw()
    {
        return !live || isActive;
    }
    private ItemManager itemManager;

    public void enableWrap()
    {
        shouldWrap = true;
        recalculateSize();
    }

    private int boxesToShow = 1;

    public void setSize(int x, int y)
    {
        windowHeight = y;
        if(isActive || !live)
        {
            boxesToShow = Math.min(1+((y-TITLE_BAR_PLUS_TAB_HEIGHT-scale)/boxHeight), boxCount);
            if (img != null)
            {
                img.flush();
            }
            img = new BufferedImage(x, y, BufferedImage.TYPE_INT_ARGB);
            recalculateSize();
        }
    }

    private final AdvancedRaidTrackerConfig config;

    public void addRoomSpecificData(int tick, String data)
    {
        specific.put(tick, data);
    }

    public void addRoomSpecificDatum(Map<Integer, String> specificData)
    {
        specific.putAll(specificData);
    }

    public void addLine(int tick, String lineInfo)
    {
        lines.put(tick, lineInfo);
    }

    public void addLines(Map<Integer, String> lineData)
    {
        lines.putAll(lineData);
    }

    public void addRoomHP(int tick, int hp)
    {
        roomHP.put(tick, hp);
    }

    public void addAuto(int autoTick)
    {
        autos.add(autoTick);
    }

    public void addAutos(ArrayList<Integer> autos)
    {
        this.autos.addAll(autos);
    }

    public void addThrallBox(ThrallOutlineBox thrallOutlineBox)
    {
        thrallOutlineBoxes.add(thrallOutlineBox);
    }

    public void addThrallBoxes(List<ThrallOutlineBox> outlineBoxes)
    {
        thrallOutlineBoxes.addAll(outlineBoxes);
    }

    public void addDawnSpec(DawnSpec dawnSpec)
    {
        this.dawnSpecs.add(dawnSpec);
    }

    public void addDawnSpecs(ArrayList<DawnSpec> dawnSpecs)
    {
        this.dawnSpecs.addAll(dawnSpecs);
        drawGraph();
    }

    public void setRoomFinished(int tick)
    {
        finished = true;
        if (tick - endTick < 10)
        {
            endTick = tick;
        }
        drawGraph();
    }

    public void resetGraph()
    {
        playerWasOnCD.clear();
        currentBox = 0;
        currentScrollOffset = 0;
        endTick = 0;
        startTick = 0;
        selectedRow = -1;
        selectedTick = -1;
        selectedPlayer = "";
        outlineBoxes.clear();
        autos.clear();
        lines.clear();
        specific.clear();
        dawnSpecs.clear();
        thrallOutlineBoxes.clear();
        players.clear();
        playerOffsets.clear();
        crabDescriptions.clear();
        actions.clear();
        roomHP.clear();
        NPCMap.clear();
        finished = false;
        recalculateSize();
    }

    public void addMaidenCrabs(List<String> crabDescriptions)
    {
        this.crabDescriptions.addAll(crabDescriptions);
    }

    public void addMaidenCrab(String description)
    {
        crabDescriptions.add(description);
    }

    public void addNPCMapping(int index, String name)
    {
        NPCMap.put(index, name);
    }

    public void setNPCMappings(Map<Integer, String> mapping)
    {
        this.NPCMap = mapping;
    }

    public void redraw()
    {
        recalculateSize();
    }

    public void addAttack(PlayerDidAttack attack)
    {
        if (clientThread != null)
        {
            if (config.useUnkitted())
            {
                attack.useUnkitted();
            }
            clientThread.invoke(() ->
            {
                attack.setIcons(itemManager);
            });
            clientThread.invoke(attack::setWornNames);
        }
        PlayerAnimation playerAnimation = AnimationDecider.getWeapon(attack.animation, attack.spotAnims, attack.projectile, attack.weapon);
        if (playerAnimation != PlayerAnimation.UNDECIDED)
        {
            boolean isTarget = RoomUtil.isPrimaryBoss(attack.targetedID) && attack.targetedID != -1;
            String targetString = playerAnimation.name + ": ";
            String targetName = getBossName(attack.targetedID, attack.targetedIndex, attack.tick);
            if (targetName.equals("?"))
            {
                targetString += attack.targetName;
            } else
            {
                targetString += targetName;
            }
            actions.put(attack, targetString);
            String additionalText = "";
            if (targetString.contains("(on w"))
            {
                additionalText = targetString.substring(targetString.indexOf("(on w") + 5);
                additionalText = "s" + additionalText.substring(0, additionalText.indexOf(")"));
            } else if (targetString.contains("small") || targetString.contains("big"))
            {
                additionalText = getShortenedString(targetString, playerAnimation.name.length());
            } else if (targetString.contains("70s") || targetString.contains("50s") || targetString.contains("30s"))
            {
                String shortenedString = targetString.substring(playerAnimation.name.length() + 2);
                shortenedString = shortenedString.substring(0, 2);
                String proc = targetString.substring(targetString.indexOf("0s") - 1, targetString.indexOf("0s") + 1);

                additionalText = proc + shortenedString;
            }
            outlineBoxes.add(new OutlineBox(attack, playerAnimation.shorthand, playerAnimation.color, isTarget, additionalText, playerAnimation, playerAnimation.attackTicks, RaidRoom.getRoom(this.room)));
            for(int i = attack.tick; i < attack.tick+playerAnimation.attackTicks; i++)
            {
                playerWasOnCD.put(attack.player, i);
            }
        }
    }

    private static String getShortenedString(String targetString, int index)
    {
        String shortenedString = targetString.substring(index + 3);
        shortenedString = shortenedString.substring(0, shortenedString.indexOf(" "));
        if (targetString.contains("east small"))
        {
            shortenedString += "e";
        } else if (targetString.contains("south small"))
        {
            shortenedString += "s";
        } else if (targetString.contains("west small"))
        {
            shortenedString += "w";
        } else if (targetString.contains("east big"))
        {
            shortenedString += "E";
        } else if (targetString.contains("south big"))
        {
            shortenedString += "S";
        } else if (targetString.contains("west big"))
        {
            shortenedString += "W";
        }
        return shortenedString;
    }

    public void addLiveAttack(PlayerDidAttack attack)
    {
        attack.tick += endTick;
        addAttack(new PlayerDidAttack(attack.itemManager, attack.player, attack.animation, attack.tick, attack.weapon, attack.projectile, attack.spotAnims, attack.targetedIndex, attack.targetedID, attack.targetName, attack.wornItems));
    }

    public void addAttacks(Collection<PlayerDidAttack> attacks)
    {
        for (PlayerDidAttack attack : attacks)
        {
            addAttack(attack);
        }
    }
    public void incrementTick()
    {
        endTick++;
        if (endTick % 50 == 0 || endTick == 1)
        {
            recalculateSize();
        } else
        {
            drawGraph();
        }
    }

    public void setTick(int tick)
    {
        endTick = tick;
        recalculateSize();
    }

    public void setStartTick(int tick)
    {
        startTick = tick;
        recalculateSize();
    }

    public void recalculateSize()
    {
        if(!shouldDraw())
        {
            return;
        }
        try
        {
            scale = config.chartScaleSize();
            setBackground(config.primaryDark());
        } catch (Exception ignored)
        {

        }
        int length = endTick - startTick;
        boxCount = (length / 50);
        if (boxCount % 50 != 0)
        {
            boxCount++;
        }
        if (boxCount < 1)
        {
            boxCount = 1;
        }
        boxHeight = ((players.size() + 3) * scale);
        boxWidth = (100 + (scale*51));
        boxesToShow = Math.min(1+((windowHeight-TITLE_BAR_PLUS_TAB_HEIGHT-scale)/boxHeight), boxCount);
        drawGraph();
    }

    public void sendToBottom()
    {
        recalculateSize();
        if(TITLE_BAR_PLUS_TAB_HEIGHT+scale+boxCount*boxHeight > img.getHeight())
        {
            currentBox = boxCount - 1 - boxesToShow + 1;
            int lastBoxEnd = (boxesToShow * boxHeight) + scale + TITLE_BAR_PLUS_TAB_HEIGHT;
            currentScrollOffset = (currentBox * boxHeight) + (lastBoxEnd - img.getHeight());
        }
    }

    private final ClientThread clientThread;

    public ChartPanel(String room, boolean isLive, AdvancedRaidTrackerConfig config, ClientThread clientThread, ConfigManager configManager, ItemManager itemManager)
    {
        this.itemManager = itemManager;
        this.configManager = configManager;
        this.config = config;
        this.clientThread = clientThread;
        scale = 26;
        live = isLive;
        this.room = room;
        startTick = 0;
        endTick = 0;
        shouldWrap = true;
        boxWidth = 100 + scale * 51;
        img = new BufferedImage(boxWidth+10, 600, BufferedImage.TYPE_INT_ARGB);
        recalculateSize();
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);
        if (img != null)
        {
            g.drawImage(img, 0, 0, null);
        }
    }

    @Override
    public Dimension getPreferredSize()
    {
        return new Dimension(img.getWidth(), img.getHeight());
    }

    private Rectangle getStringBounds(Graphics2D g2, String str)
    {
        FontRenderContext frc = g2.getFontRenderContext();
        GlyphVector gv = g2.getFont().createGlyphVector(frc, str);
        return gv.getPixelBounds(null, (float) 0, (float) 0);
    }

    private int getStringWidth(Graphics2D g, String str)
    {
        FontRenderContext frc = g.getFontRenderContext();
        GlyphVector gv = g.getFont().createGlyphVector(frc, str);
        return gv.getPixelBounds(null, 0, 0).width;
    }

    int getYOffset(int tick)
    {
        return ((((tick - startTick) / 50) * boxHeight) + 20)-(currentScrollOffset);
    }

    int getXOffset(int tick)
    {
        return (shouldWrap) ? ((tick - startTick) % 50) * scale : tick * scale;
    }

    private void drawTicks(Graphics2D g)
    {
        if(room.equals("Nylocas"))
        {
            for(Integer i : lines.keySet())
            {
                if(lines.get(i).equals("W1"))
                {
                    instanceTime = i%4;
                }
            }
        }
        for (int i = startTick; i < endTick; i++)
        {
            if(shouldTickBeDrawn(i))
            {
                int xOffset = getXOffset(i);
                int yOffset = getYOffset(i);
                yOffset += scale;
                //g.drawLine(100 + xOffset + scale, yOffset - (fontHeight / 2), 100 + xOffset + scale, yOffset + boxHeight - (2 * scale) + 10);
                g.setColor(config.fontColor());
                Font oldFont = g.getFont();
                g.setFont(oldFont.deriveFont(10.0f));
                int strWidth = getStringBounds(g, String.valueOf(i)).width;
                int stallsUntilThisPoint = 0;
                if (room.equals("Nylocas"))
                {
                    for (Integer s : lines.keySet())
                    {
                        if (s < (i - 3))
                        {
                            if (lines.get(s).equals("Stall"))
                            {
                                stallsUntilThisPoint++;
                            }
                        }
                    }
                }
                int ticks = i - (stallsUntilThisPoint * 4);
                String tick = (config.useTimeOnChart()) ? RoomUtil.time(ticks) : String.valueOf(ticks);
                if(tick.endsWith("s"))
                {
                    tick = tick.substring(0, tick.length()-1);
                }
                if(config.useTimeOnChart())
                {
                    drawStringRotated(g, tick, 100 + xOffset + (scale / 2) - (strWidth / 2), yOffset + (fontHeight / 2) - 3, config.fontColor());
                }
                else
                {
                    g.drawString(tick, 100 + xOffset + (scale / 2) - (strWidth / 2), yOffset + (fontHeight / 2) - 3);
                }
                g.setFont(oldFont);
            }
        }
    }

    private void drawAutos(Graphics2D g)
    {
        for (Integer i : autos)
        {
            if(shouldTickBeDrawn(i))
            {
                g.setColor(new Color(255, 80, 80, 60));
                int xOffset = getXOffset(i);
                int yOffset = getYOffset(i);
                g.fillRoundRect(xOffset + 100, yOffset + 10, scale, boxHeight - scale, 7, 7);
            }
        }
    }

    private void drawGraphBoxes(Graphics2D g)
    {
        for (int i = 0; i < boxesToShow; i++)
        {
            int startX = 100;
            int startY = boxHeight * i + 30 - (currentScrollOffset-(currentBox*boxHeight));
            int endX = boxWidth - scale;
            int endY = startY + boxHeight;
            g.setColor(config.boxColor());

            if(startY > 5)
            {
                g.drawLine(startX, startY + scale, endX, startY + scale);
            }
            g.drawLine(startX, (startY > 5) ? startY + scale : scale+5, startX, endY - scale);
            if(endY-scale > 5 + scale)
            {
                g.drawLine(startX, endY - scale, endX, endY - scale);
            }
            g.drawLine(endX, endY - scale, endX, (startY > 5) ? startY + scale : scale+5);
        }
    }

    public static BufferedImage getScaledImage(BufferedImage image, int width, int height) throws IOException
    {
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();

        double scaleX = (double) width / imageWidth;
        double scaleY = (double) height / imageHeight;
        AffineTransform scaleTransform = AffineTransform.getScaleInstance(scaleX, scaleY);
        AffineTransformOp bilinearScaleOp = new AffineTransformOp(scaleTransform, AffineTransformOp.TYPE_BILINEAR);

        return bilinearScaleOp.filter(
                image,
                new BufferedImage(width, height, image.getType()));
    }

    private static BufferedImage createFlipped(BufferedImage image)
    {
        AffineTransform at = new AffineTransform();
        at.concatenate(AffineTransform.getScaleInstance(1, -1));
        at.concatenate(AffineTransform.getTranslateInstance(0, -image.getHeight()));
        return createTransformed(image, at);
    }

    private static BufferedImage createTransformed(
            BufferedImage image, AffineTransform at)
    {
        BufferedImage newImage = new BufferedImage(
                image.getWidth(), image.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = newImage.createGraphics();
        g.transform(at);
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return newImage;
    }

    private void drawYChartColumn(Graphics2D g)
    {
        g.setColor(config.fontColor());
        for (int i = 0; i < players.size(); i++)
        {
            playerOffsets.put(players.get(i), i);
            for (int j = currentBox; j < currentBox+boxesToShow; j++)
            {
                g.setColor(Color.DARK_GRAY);
                g.setColor(config.primaryLight());
                int nameRectsY = (j*boxHeight)+((i+2)*scale)+10-3-currentScrollOffset;
                if(nameRectsY > scale + 5)
                {
                    g.fillRoundRect(5, nameRectsY, 90, scale - 6, 10, 10);
                }
                g.setColor(config.fontColor());
                Font oldFont = g.getFont();
                g.setFont(FontManager.getRunescapeBoldFont());
                int width = getStringWidth(g, players.get(i));
                int margin = 5;
                int subBoxWidth = 90;
                int textPosition = margin + (subBoxWidth - width) / 2;
                int yPosition = ((j * boxHeight) + ((i + 2) * scale) + (fontHeight) / 2) + (scale/2) + 8-(currentScrollOffset);
                if(yPosition > scale+5)
                {
                    g.drawString(players.get(i), textPosition, yPosition);
                }

                if (i == 0)
                {
                    if(room.equals("Nylocas"))
                    {
                        roomSpecificText = "Instance Time";
                    }
                    int textYPosition = j * boxHeight + ((players.size() + 2) * scale) + (fontHeight / 2) + 20 - currentScrollOffset;
                    if(textYPosition > scale + 5)
                    {
                        g.drawString(roomSpecificText, 5, textYPosition); //todo why does scroll not render correctly?
                    }
                }
                g.setFont(oldFont);
            }
        }

    }

    private void drawRoomSpecificData(Graphics2D g)
    {
        if(room.equals("Nylocas"))
        {
            for (int i = startTick; i < endTick; i++)
            {
                if(shouldTickBeDrawn(i))
                {
                    int xOffset = getXOffset(i);
                    int yOffset = getYOffset(i);
                    xOffset += 100;
                    yOffset += (playerOffsets.size() + 2) * scale - 10;
                    g.setColor(config.fontColor());
                    String time = String.valueOf(((i + instanceTime) % 4) + 1);
                    int strWidth = getStringBounds(g, time).width;
                    if(yOffset > scale + 5)
                    {
                        g.drawString(time, xOffset + (scale / 2) - (strWidth / 2), yOffset + (fontHeight / 2) + 10);
                    }
                }
            }
        }
        else
        {
            for (Integer i : specific.keySet())
            {
                int xOffset = getXOffset(i);
                int yOffset = getYOffset(i);
                xOffset += 100;
                yOffset += (playerOffsets.size() + 2) * scale - 10 - currentScrollOffset;
                g.setColor(config.fontColor());
                int strWidth = getStringBounds(g, "X").width;
                if(yOffset > scale + 5)
                {
                    g.drawString("X", xOffset + (scale / 2) - (strWidth / 2), yOffset + (fontHeight / 2) + 10);
                }
            }
        }
    }

    private void drawDawnSpecs(Graphics2D g)
    {
        for (DawnSpec dawnSpec : dawnSpecs)
        {
            String damage = String.valueOf(dawnSpec.getDamage());
            if (dawnSpec.getDamage() != -1)
            {
                int xOffset = (shouldWrap) ? ((dawnSpec.tick - startTick - 2) % 50) * scale : (dawnSpec.tick + 2) * scale;
                int yOffset = getYOffset(dawnSpec.tick);
                xOffset += 100;
                yOffset += (playerOffsets.size() + 3) * scale - 10;
                g.setColor(config.fontColor());
                int textOffset = (scale / 2) - (getStringBounds(g, damage).width) / 2;
                if(yOffset > scale + 5)
                {
                    g.drawString(damage, xOffset + textOffset, yOffset + (fontHeight / 2) + 10);
                }
            }
        }
    }

    public static BufferedImage createDropShadow(BufferedImage image)
    {
        BufferedImage shadow = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2 = shadow.createGraphics();
        g2.drawImage(image, 0, 0, null);

        g2.setComposite(AlphaComposite.SrcIn);
        g2.setColor(new Color(0, 0, 0, 128));
        g2.fillRect(0, 0, shadow.getWidth(), shadow.getHeight());

        g2.dispose();
        return shadow;
    }

    private void drawPrimaryBoxes(Graphics2D g)
    {
        for (OutlineBox box : outlineBoxes)
        {
            if (shouldTickBeDrawn(box.tick))
            {
                int xOffset = 100 + ((shouldWrap) ? ((box.tick - startTick) % 50) * scale : box.tick * scale);
                if (playerOffsets.get(box.player) == null)
                {
                    continue;
                }
                int yOffset = ((playerOffsets.get(box.player) + 1) * scale + 30) + (((shouldWrap) ? ((box.tick - startTick) / 50) * boxHeight : 30)-currentScrollOffset);
                if(yOffset > scale + 5)
                {
                    if (config != null && config.useIconsOnChart())
                    {
                        try
                        {
                            if (box.playerAnimation.attackTicks != -1)
                            {
                                int opacity = config.iconBackgroundOpacity();
                                opacity = Math.min(255, opacity);
                                opacity = Math.max(0, opacity);
                                if(config.attackBoxColor().equals(Color.WHITE))
                                {
                                    g.setColor(new Color(box.color.getRed(), box.color.getGreen(), box.color.getBlue(), opacity));
                                }
                                else
                                {
                                    g.setColor(config.attackBoxColor());
                                }
                                g.fillRoundRect(xOffset + 2, yOffset + 2, scale - 3, scale - 3, 5, 5);
                                BufferedImage scaled = getScaledImage(box.attack.img, (scale - 2), (scale - 2));
                                if(box.playerAnimation.equals(PlayerAnimation.HAMMER_BOP) || box.playerAnimation.equals(PlayerAnimation.BGS_WHACK))
                                {
                                    g.drawImage(createFlipped(createDropShadow(scaled)), xOffset + 3, yOffset + 3, null);
                                    g.drawImage(createFlipped(scaled), xOffset + 2, yOffset + 1, null);
                                }
                                else
                                {
                                    g.drawImage(createDropShadow(scaled), xOffset + 3, yOffset + 3, null);
                                    g.drawImage(scaled, xOffset + 2, yOffset + 1, null);
                                }
                            }
                        } catch (Exception e)
                        {

                        }
                    } else
                    {
                        int opacity = 100;
                        if (config != null)
                        {
                            opacity = config.letterBackgroundOpacity();
                            opacity = Math.min(255, opacity);
                            opacity = Math.max(0, opacity);
                        }
                        g.setColor(new Color(box.color.getRed(), box.color.getGreen(), box.color.getBlue(), opacity));
                        g.fillRoundRect(xOffset + 2, yOffset + 2, scale - 3, scale - 3, 5, 5);
                        g.setColor((box.primaryTarget) ? Color.WHITE : new Color(0, 190, 255));
                        int textOffset = (scale / 2) - (getStringWidth(g, box.letter) / 2);
                        int primaryOffset = yOffset + (box.additionalText.isEmpty() ? (fontHeight / 2) : 0);
                        g.drawString(box.letter, xOffset + textOffset - 1, primaryOffset + (scale / 2) + 1);
                        if (!box.additionalText.isEmpty())
                        {
                            Font f = g.getFont();
                            g.setFont(f.deriveFont(10.0f));
                            textOffset = (scale / 2) - (getStringWidth(g, box.additionalText) / 2);
                            g.setColor(Color.WHITE);
                            g.drawString(box.additionalText, xOffset + textOffset, yOffset + scale - 3);
                            g.setFont(f);
                        }
                    }
                    box.createOutline();
                    g.setColor(box.outlineColor);
                    g.drawRoundRect(xOffset + 1, yOffset + 1, scale - 2, scale - 2, 5, 5);
                }
            }
        }
    }

    private void drawMarkerLines(Graphics2D g)
    {
        if (!live || finished)
        {
            for (Integer i : lines.keySet())
            {
                if(shouldTickBeDrawn(i))
                {
                    int xOffset = getXOffset(i);
                    int yOffset = getYOffset(i);
                    xOffset += 100;
                    yOffset += (scale / 2);
                    g.setColor(config.markerColor());
                    g.drawLine(xOffset, yOffset, xOffset, yOffset + boxHeight - 20);
                    int stringLength = getStringBounds(g, lines.get(i)).width;
                    g.setColor(config.fontColor());
                    if(yOffset > scale + 5 )
                    {
                        g.drawString(lines.get(i), xOffset - (stringLength / 2), yOffset - 6);
                    }
                }
            }
        }
    }

    private void drawThrallBoxes(Graphics2D g)
    {
        for (ThrallOutlineBox box : thrallOutlineBoxes)
        {
            g.setColor(new Color(box.getColor().getRed(), box.getColor().getGreen(), box.getColor().getBlue(), 30));

            int maxTick = getMaxTick(box.owner, box.spawnTick);
            int lastEndTick = box.spawnTick;
            while (lastEndTick < maxTick && shouldTickBeDrawn(lastEndTick))
            {
                int yOffset = getYOffset(lastEndTick);
                try
                {
                    yOffset += (playerOffsets.get(box.owner) + 1) * scale + 10;
                } catch (Exception e)
                {
                    break;
                }
                int currentEndTick = (shouldWrap) ? lastEndTick + (50 - (lastEndTick % 50) + (startTick % 50)) : maxTick;
                if (currentEndTick > maxTick)
                {
                    currentEndTick = maxTick;
                }
                int xOffsetStart = (shouldWrap) ? ((lastEndTick - startTick) % 50) * scale : (lastEndTick - 1) * scale;
                xOffsetStart += 100;
                int xOffsetEnd = (shouldWrap) ? ((currentEndTick - startTick - 1) % 50) * scale : (currentEndTick - 1) * scale;
                xOffsetEnd += 100;
                lastEndTick = currentEndTick;
                if(yOffset > scale + 5)
                {
                    g.fillRect(xOffsetStart, yOffset + 1, xOffsetEnd - xOffsetStart + scale, scale - 2);
                }
            }
        }
    }

    /**
     *  Finds the highest tick that doesn't overlap if they summoned a thrall in the future before this one would naturally expire
     * @param owner player who summoned this thrall
     * @param startTick tick the thrall was summoned
     * @return
     */
    private int getMaxTick(String owner, int startTick)
    {
        int maxTick = startTick + 99;
        for (ThrallOutlineBox boxCompare : thrallOutlineBoxes)
        {
            if (owner.equalsIgnoreCase(boxCompare.owner))
            {
                if (boxCompare.spawnTick > startTick && boxCompare.spawnTick < (startTick + 99))
                {
                    maxTick = boxCompare.spawnTick;
                }
            }
        }
        if (endTick < maxTick)
        {
            maxTick = endTick;
        }
        return maxTick;
    }

    private void drawSelectedOutlineBox(Graphics2D g)
    {
        if (selectedTick != -1 && !selectedPlayer.equalsIgnoreCase(""))
        {
            g.setColor(config.fontColor());
            int xOffset = 100 + ((shouldWrap) ? ((selectedTick - startTick) % 50) * scale : selectedTick * scale);
            int yOffset = ((playerOffsets.get(selectedPlayer) + 1) * scale + 30) + (((shouldWrap) ? ((selectedTick - startTick) / 50) * boxHeight : 30)-currentScrollOffset);
            if(yOffset > scale + 5)
            {
                g.drawRect(xOffset, yOffset, scale, scale);
            }
        }
    }

    private Point getPoint(int tick, String player)
    {
        return new Point(
                100 + ((shouldWrap) ? ((tick - startTick) % 50) * scale : tick * scale),
                ((playerOffsets.get(player) + 1) * scale + 10) + (((shouldWrap) ? ((tick - startTick) / 50) * boxHeight : 0)-currentScrollOffset));
    }

    private void drawSelectedRow(Graphics2D g)
    {
        if (selectedRow != -1)
        {
            g.setColor(config.fontColor());
            int xOffset = 100 + getXOffset(selectedRow);
            int yOffset = 10 + getYOffset(selectedRow);
            g.drawRect(xOffset, yOffset, scale, scale * (players.size() + 2));

            int selectedTickHP = -1;
            try
            {
                selectedTickHP = roomHP.get(selectedRow + 1);
            } catch (Exception ignored)
            {

            }
            int offset = -1;
            switch (room)
            {
                case "Maiden":
                case "Verzik P3":
                    offset = 7;
                    break;
                case "Bloat":
                case "Sotetseg":
                case "Xarpus":
                    offset = 3;
                    break;
                case "Nylocas":
                    offset = 5;
                    offset += (4 - ((offset + selectedRow) % 4));
                    offset -= 2;
                    break;
            }
            String bossWouldHaveDied = (offset != -1) ? "Melee attack on this tick killing would result in: " + RoomUtil.time(selectedRow + 1 + offset + 1) + " (Quick death: " + RoomUtil.time(selectedRow + offset + 1) + ")" : "";
            String HPString = "Boss HP: " + ((selectedTickHP == -1) ? "-" : RoomUtil.varbitHPtoReadable(selectedTickHP));
            HoverBox hoverBox = new HoverBox(HPString, config);
            if (offset != -1)
            {
                hoverBox.addString(bossWouldHaveDied);
            }
            int xPosition = xOffset + scale;
            if (xPosition + hoverBox.getWidth(g) > img.getWidth())
            {
                xPosition = xPosition - hoverBox.getWidth(g) - 3 * scale;
            }
            int yPosition = yOffset;
            if(yPosition + hoverBox.getHeight(g) > img.getHeight())
            {
                yPosition = yPosition - hoverBox.getHeight(g) - 3*scale;
            }
            hoverBox.setPosition(xPosition, yPosition);
            hoverBox.draw(g);
        }
    }


    private void drawHoverBox(Graphics2D g)
    {
        for (PlayerDidAttack action : actions.keySet())
        {
            if (action.tick == selectedTick && action.player.equals(selectedPlayer) && shouldTickBeDrawn(action.tick))
            {
                Point location = getPoint(action.tick, action.player);
                HoverBox hoverBox = new HoverBox(actions.get(action), config);
                hoverBox.addString("");
                for (String item : action.wornItemNames)
                {
                    hoverBox.addString("." + item);
                }
                int xPosition = location.getX() + 10;
                if (xPosition + hoverBox.getWidth(g) > img.getWidth()) //box would render off screen
                {
                    xPosition = xPosition - hoverBox.getWidth(g) - scale * 2; //render to the left side of the selected action
                }
                int yPosition = location.getY() - 10;
                if(yPosition + hoverBox.getHeight(g) > (img.getHeight()-2*TITLE_BAR_PLUS_TAB_HEIGHT)) //box would render off screen
                {
                    yPosition -= (yPosition + hoverBox.getHeight(g) - (img.getHeight()-TITLE_BAR_PLUS_TAB_HEIGHT-10)); //render bottom aligned to window+10
                }
                hoverBox.setPosition(xPosition, yPosition);
                hoverBox.draw(g);
            }
        }
    }

    private void drawMaidenCrabs(Graphics2D g)
    {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(new Color(230, 20, 20, 200));
        if (room.equals("Maiden"))
        {
            for (Integer tick : lines.keySet())
            {
                if (lines.get(tick).equals("Dead"))
                {
                    continue;
                }
                String proc = lines.get(tick);
                int xOffset = 100 + getXOffset(tick + 1);
                int yOffset = 10 + getYOffset(tick + 1);
                if(yOffset <= scale + 5)
                {
                    continue;
                }
                yOffset -= scale;
                int crabOffsetX = 0;
                int crabOffsetY;

                crabOffsetY = 11;
                g.drawOval(xOffset + crabOffsetX, yOffset + crabOffsetY, 7, 7);

                crabOffsetY = 20;
                g.drawOval(xOffset + crabOffsetX, yOffset + crabOffsetY, 7, 7);

                crabOffsetX = 9;
                crabOffsetY = 11;
                g.drawOval(xOffset + crabOffsetX, yOffset + crabOffsetY, 7, 7);

                crabOffsetX = 9;
                crabOffsetY = 20;
                g.drawOval(xOffset + crabOffsetX, yOffset + crabOffsetY, 7, 7);

                crabOffsetX = 18;
                crabOffsetY = 11;
                g.drawOval(xOffset + crabOffsetX, yOffset + crabOffsetY, 7, 7);

                crabOffsetX = 18;
                crabOffsetY = 20;
                g.drawOval(xOffset + crabOffsetX, yOffset + crabOffsetY, 7, 7);

                crabOffsetX = 27;
                crabOffsetY = 2;
                g.drawOval(xOffset + crabOffsetX, yOffset + crabOffsetY, 7, 7);

                crabOffsetX = 27;
                crabOffsetY = 20;
                g.drawOval(xOffset + crabOffsetX, yOffset + crabOffsetY, 7, 7);

                crabOffsetX = 27;
                crabOffsetY = 11;
                g.drawOval(xOffset + crabOffsetX, yOffset + crabOffsetY, 7, 7);

                crabOffsetX = 27;
                crabOffsetY = 29;
                g.drawOval(xOffset + crabOffsetX, yOffset + crabOffsetY, 7, 7);


                for (String crab : crabDescriptions)
                {
                    if (crab.contains(proc))
                    {
                        xOffset = 100 + getXOffset(tick + 1);
                        yOffset = 10 + getYOffset(tick + 1);
                        crabOffsetX = 0;
                        crabOffsetY = 0;
                        if (crab.contains("N1"))
                        {
                            crabOffsetY = 11;
                        } else if (crab.contains("S1"))
                        {
                            crabOffsetY = 20;
                        } else if (crab.contains("N2"))
                        {
                            crabOffsetX = 9;
                            crabOffsetY = 11;
                        } else if (crab.contains("S2"))
                        {
                            crabOffsetX = 9;
                            crabOffsetY = 20;
                        } else if (crab.contains("N3"))
                        {
                            crabOffsetX = 18;
                            crabOffsetY = 11;
                        } else if (crab.contains("S3"))
                        {
                            crabOffsetX = 18;
                            crabOffsetY = 20;
                        } else if (crab.contains("N4 (1)"))
                        {
                            crabOffsetX = 27;
                            crabOffsetY = 2;
                        } else if (crab.contains("S4 (1)"))
                        {
                            crabOffsetX = 27;
                            crabOffsetY = 20;
                        } else if (crab.contains("N4 (2)"))
                        {
                            crabOffsetX = 27;
                            crabOffsetY = 11;
                        } else if (crab.contains("S4 (2)"))
                        {
                            crabOffsetX = 27;
                            crabOffsetY = 29;
                        }
                        crabOffsetY -= scale;
                        if(crab.startsWith("s"))
                        {
                            g.setColor(new Color(220, 200, 0, 200));
                        }
                        else
                        {
                            g.setColor(new Color(230, 20, 20, 200));
                        }
                        g.fillOval(xOffset + crabOffsetX, yOffset + crabOffsetY, 7, 7);
                        g.setColor(new Color(230, 20, 20, 200));
                    }
                }
            }
        }
    }

    private void drawRoomTime(Graphics2D g)
    {
        Font oldFont = g.getFont();
        g.setColor(config.fontColor());
        g.setFont(FontManager.getRunescapeBoldFont());
        g.drawString("Time " + RoomUtil.time(endTick), 5, 20);
        g.setFont(oldFont);
    }

    private void drawCheckBox(Graphics2D g)
    {
        Font oldFont = g.getFont();
        g.setColor(config.fontColor());
        g.setFont(FontManager.getRunescapeBoldFont());
        g.drawString("Use Icons? ", 100, 20);
        if (!checkBoxHovered)
        {
            g.setColor(config.boxColor());
        }

        g.drawRect(180, 2, 20, 20);
        if (checkBoxHovered)
        {
            g.setColor(config.boxColor().brighter().brighter().brighter());
            g.fillRect(181, 3, 19, 19);
        }
        g.setColor(config.fontColor());
        if (config.useIconsOnChart())
        {
            g.drawString("x", 186, 17);
        }
        g.setFont(oldFont);
    }

    private void drawCheckBox2(Graphics2D g)
    {
        Font oldFont = g.getFont();
        g.setColor(config.fontColor());
        g.setFont(FontManager.getRunescapeBoldFont());
        g.drawString("Use Time? ", 210, 20);
        if (!checkBox2Hovered)
        {
            g.setColor(config.boxColor());
        }

        g.drawRect(290, 2, 20, 20);
        if (checkBox2Hovered)
        {
            g.setColor(config.boxColor().brighter().brighter().brighter());
            g.fillRect(291, 3, 19, 19);
        }
        g.setColor(config.fontColor());
        if (config.useTimeOnChart())
        {
            g.drawString("x", 296, 17);
        }
        g.setFont(oldFont);
    }

    private void drawCheckBox3(Graphics2D g)
    {
        Font oldFont = g.getFont();
        g.setColor(config.fontColor());
        g.setFont(FontManager.getRunescapeBoldFont());
        g.drawString("Show Config? ", 320, 20);
        if (!checkBox3Hovered)
        {
            g.setColor(config.boxColor());
        }

        g.drawRect(410, 2, 20, 20);
        if (checkBox3Hovered)
        {
            g.setColor(config.boxColor().brighter().brighter().brighter());
            g.fillRect(411, 3, 19, 19);
        }
        g.setColor(config.fontColor());
        if (config.showConfigOnChart())
        {
            g.drawString("x", 416, 17);
        }
        g.setFont(oldFont);
    }

    private void drawBaseBoxes(Graphics2D g)
    {
        for (int i = startTick; i < endTick; i++)
        {
            if(shouldTickBeDrawn(i))
            {
                for (int j = 0; j < playerOffsets.size(); j++)
                {
                    int xOffset = 100 + ((shouldWrap) ? ((i - startTick) % 50) * scale : i * scale);
                    if (playerOffsets.get(players.get(j)) == null)
                    {
                        continue;
                    }
                    shouldWrap = true;
                    int yOffset = ((playerOffsets.get(players.get(j)) + 1) * scale + 30) + ((((i - startTick) / 50) * boxHeight)-currentScrollOffset);
                    g.setColor(config.primaryMiddle());
                    if(!playerWasOnCD.get(players.get(j)).contains(i))
                    {
                        g.setColor(config.idleColor());
                    }
                    if(yOffset > scale + 5)
                    {
                        g.fillRoundRect(xOffset + 2, yOffset + 2, scale - 3, scale - 3, 5, 5);
                    }
                }
            }
        }
    }

    private final Multimap<String, Integer> playerWasOnCD = ArrayListMultimap.create();

    public boolean shouldTickBeDrawn(int tick)
    {
        return tick >= (startTick + currentBox*50) && tick < (startTick + ((currentBox+boxesToShow)*50)) && tick >= startTick && tick <= endTick;
    }

    private void drawGraph()
    {
        if(!shouldDraw())
        {
            return;
        }
        Graphics2D g = (Graphics2D) img.getGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        RenderingHints qualityHints = new RenderingHints(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        qualityHints.put(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHints(qualityHints);
        Color oldColor = g.getColor();

        g.setColor(config.primaryDark());
        g.fillRect(0, 0, img.getWidth(), img.getHeight());


        fontHeight = getStringBounds(g, "a").height;
        g.setColor(Color.WHITE);
        drawTicks(g);
        drawGraphBoxes(g);
        drawBaseBoxes(g);
        drawYChartColumn(g);
        drawRoomSpecificData(g);
        drawDawnSpecs(g);
        drawPrimaryBoxes(g);
        drawAutos(g);
        drawMarkerLines(g);
        drawThrallBoxes(g);
        drawMaidenCrabs(g);
        drawSelectedOutlineBox(g);
        drawSelectedRow(g);
        drawHoverBox(g);
        drawRoomTime(g);
        if(config.showConfigOnChart())
        {
            drawCheckBox(g);
            drawCheckBox2(g);
            drawCheckBox3(g);
        }

        g.setColor(oldColor);
        g.dispose();
        repaint();
    }

    public void getTickHovered(int x, int y)
    {
        y = y + currentScrollOffset;
        if (y > 20)
        {
            int boxNumber = (y - 20) / boxHeight;
            if (x > 100)
            {
                int tick = startTick + (50 * boxNumber + ((x - 100) / scale));
                int playerOffsetPosition = (((y - 30 - scale) % boxHeight) / scale);
                if (playerOffsetPosition >= 0 && playerOffsetPosition < players.size() && (y - 30 - scale > 0))
                {
                    selectedTick = tick;
                    selectedPlayer = players.get(playerOffsetPosition);
                    selectedRow = -1;
                } else if (y % boxHeight < 30 + scale)
                {
                    selectedRow = tick;
                    selectedPlayer = "";
                    selectedTick = -1;
                } else
                {
                    selectedPlayer = "";
                    selectedTick = -1;
                    selectedRow = -1;
                }
            } else
            {
                selectedPlayer = "";
                selectedTick = -1;
                selectedRow = -1;
            }
            drawGraph();
        }
    }

    private int currentBox = 0;
    private int currentScrollOffset = 0;
    @Override
    public void mouseWheelMoved(MouseWheelEvent e) //manually implement scrolling
    {
        if(e.getWheelRotation() < 0) //top of the first box aligns to top if you scroll up
        {
            currentBox = Math.max(0, currentBox-1);
            currentScrollOffset = currentBox*boxHeight;
        }
        else //bottom of the bottom box aligns to the bottom if you scroll down
        {
            if(TITLE_BAR_PLUS_TAB_HEIGHT+scale+boxCount*boxHeight > img.getHeight()) //no need to scroll at all if all boxes fit on screen, boxes would jump to bottom and leave dead space
            {
                int lastBox = currentBox + boxesToShow - 1;
                lastBox = Math.min(lastBox + 1, boxCount - 1);
                currentBox = lastBox - boxesToShow + 1;
                int lastBoxEnd = (boxesToShow * boxHeight) + scale + TITLE_BAR_PLUS_TAB_HEIGHT;
                currentScrollOffset = (currentBox * boxHeight) + (lastBoxEnd - img.getHeight());
            }
        }
        recalculateSize();
    }

    @Override
    public void mouseClicked(MouseEvent e)
    {
        if (checkBoxHovered)
        {
            configManager.setConfiguration("Advanced Raid Tracker", "useIconsOnChart", !config.useIconsOnChart());
            drawGraph();
        }
        else if(checkBox2Hovered)
        {
            configManager.setConfiguration("Advanced Raid Tracker", "useTimeOnChart", !config.useTimeOnChart());
            drawGraph();
        }
        else if(checkBox3Hovered)
        {
            configManager.setConfiguration("Advanced Raid Tracker", "showConfigOnChart", !config.showConfigOnChart());
            drawGraph();
        }
    }

    @Override
    public void mousePressed(MouseEvent e)
    {

    }


    @Override
    public void mouseReleased(MouseEvent e)
    {
    }

    @Override
    public void mouseEntered(MouseEvent e)
    {
    }

    @Override
    public void mouseExited(MouseEvent e)
    {
        selectedPlayer = "";
        selectedTick = -1;
        selectedRow = -1;
        drawGraph();
    }

    @Override
    public void mouseDragged(MouseEvent e)
    {
    }

    @Override
    public void mouseMoved(MouseEvent e)
    {
        checkBoxHovered = e.getX() >= 180 && e.getX() <= 200 && e.getY() >= 2 && e.getY() <= 22;
        checkBox2Hovered = e.getX() >= 290 && e.getX() <= 310 && e.getY() >= 2 && e.getY() <= 22;
        checkBox3Hovered = e.getX() >= 410 && e.getX() <= 430 && e.getY() >= 2 && e.getY() <= 22;
        getTickHovered(e.getX(), e.getY());
        drawGraph();
    }

    public String getBossName(int id, int index, int tick)
    {
        try
        {
            switch (id)
            {
                case TobIDs.MAIDEN_P0:
                case TobIDs.MAIDEN_P1:
                case TobIDs.MAIDEN_P2:
                case TobIDs.MAIDEN_P3:
                case TobIDs.MAIDEN_PRE_DEAD:
                case TobIDs.MAIDEN_P0_HM:
                case TobIDs.MAIDEN_P1_HM:
                case TobIDs.MAIDEN_P2_HM:
                case TobIDs.MAIDEN_P3_HM:
                case TobIDs.MAIDEN_PRE_DEAD_HM:
                case TobIDs.MAIDEN_P0_SM:
                case TobIDs.MAIDEN_P1_SM:
                case TobIDs.MAIDEN_P2_SM:
                case TobIDs.MAIDEN_P3_SM:
                case TobIDs.MAIDEN_PRE_DEAD_SM:
                    return "Maiden (" + RoomUtil.varbitHPtoReadable(roomHP.get(tick + 1)) + ")";
                case TobIDs.BLOAT:
                case TobIDs.BLOAT_HM:
                case TobIDs.BLOAT_SM:
                    return "Bloat (" + RoomUtil.varbitHPtoReadable(roomHP.get(tick)) + ")";
                case TobIDs.NYLO_BOSS_MELEE:
                case TobIDs.NYLO_BOSS_RANGE:
                case TobIDs.NYLO_BOSS_MAGE:
                case TobIDs.NYLO_BOSS_MELEE_HM:
                case TobIDs.NYLO_BOSS_RANGE_HM:
                case TobIDs.NYLO_BOSS_MAGE_HM:
                case TobIDs.NYLO_BOSS_MELEE_SM:
                case TobIDs.NYLO_BOSS_RANGE_SM:
                case TobIDs.NYLO_BOSS_MAGE_SM:
                    return "Nylo Boss (" + RoomUtil.varbitHPtoReadable(roomHP.get(tick)) + ")";
                case TobIDs.XARPUS_P23:
                case TobIDs.XARPUS_P23_HM:
                case TobIDs.XARPUS_P23_SM:
                    return "Xarpus (" + RoomUtil.varbitHPtoReadable(roomHP.get(tick)) + ")";
                case TobIDs.VERZIK_P1:
                case TobIDs.VERZIK_P2:
                case TobIDs.VERZIK_P3:
                case TobIDs.VERZIK_P1_HM:
                case TobIDs.VERZIK_P2_HM:
                case TobIDs.VERZIK_P3_HM:
                case TobIDs.VERZIK_P1_SM:
                case TobIDs.VERZIK_P2_SM:
                case TobIDs.VERZIK_P3_SM:
                    return "Verzik (" + RoomUtil.varbitHPtoReadable(roomHP.get(tick)) + ")";
            }
            for (Integer i : NPCMap.keySet())
            {
                if (i == index)
                {
                    String hp = "-1";
                    try
                    {
                        hp = RoomUtil.varbitHPtoReadable(roomHP.get(tick));
                    } catch (Exception ignored
                    )
                    {
                    }
                    return NPCMap.get(i) + " (Boss: " + hp + ")";
                }
            }
            return "?";
        } catch (Exception e)
        {
            return "?";
        }
    }

}