package com.dynamicwinds.integration.commands;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.WindSample;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;

import java.util.Arrays;
import java.util.List;

public class WindCommand extends CommandBase {
    @Override
    public String getCommandName() { return "wind"; }

    @Override
    public String getCommandUsage(ICommandSender sender) { return "/wind <get|profile|debug>"; }

    @Override
    public List getCommandAliases() { return Arrays.asList("w"); }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayer)) return;
        EntityPlayer p = (EntityPlayer) sender;
        String sub = args.length > 0 ? args[0] : "get";
        if ("get".equals(sub)) {
            WindSample s = Wind.getWindAt(p.worldObj, p.posX, p.posY, p.posZ);
            p.addChatMessage(new ChatComponentText(String.format("Wind: (%.2f,%.2f,%.2f) speed=%.2f gust=%.2f",
                    s.vector.x, s.vector.y, s.vector.z, s.speed, s.gust)));
        } else if ("profile".equals(sub)) {
            p.addChatMessage(new ChatComponentText("Profile: log-law active"));
        } else if ("debug".equals(sub)) {
            p.addChatMessage(new ChatComponentText("Debug: on"));
        } else {
            p.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
        }
    }

    @Override
    public int getRequiredPermissionLevel() { return 0; }
}
