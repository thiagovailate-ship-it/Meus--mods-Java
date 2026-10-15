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
    public String getCommandUsage(ICommandSender sender) { return "/wind <get|profile|debug|storm> [x] [y] [z]"; }

    @Override
    public List getCommandAliases() { return Arrays.asList("w"); }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayer)) return;
        EntityPlayer p = (EntityPlayer) sender;
        String sub = args.length > 0 ? args[0] : "get";

        if ("get".equals(sub)) {
            double x = p.posX, y = p.posY, z = p.posZ;
            if (args.length > 1) {
                try { x = Double.parseDouble(args[1]); } catch (NumberFormatException e) { sender.addChatMessage(new ChatComponentText("Invalid x")); return; }
            }
            if (args.length > 2) {
                try { y = Double.parseDouble(args[2]); } catch (NumberFormatException e) { sender.addChatMessage(new ChatComponentText("Invalid y")); return; }
            }
            if (args.length > 3) {
                try { z = Double.parseDouble(args[3]); } catch (NumberFormatException e) { sender.addChatMessage(new ChatComponentText("Invalid z")); return; }
            }
            WindSample s = Wind.getWindAt(p.worldObj, x, y, z);
            p.addChatMessage(new ChatComponentText(
                String.format("Wind at (%.1f,%.1f,%.1f): vec=(%.3f,%.3f,%.3f) speed=%.3f gust=%.3f",
                    x, y, z, s.vector.x, s.vector.y, s.vector.z, s.speed, s.gust)));
        } else if ("profile".equals(sub)) {
            p.addChatMessage(new ChatComponentText(
                "Wind profile: logarithmic law u(z)=u*/kappa*ln(z/z0). " +
                "Diurnal cycle: peak at noon (phase=0). Storm factor: 1+2.5*storm."));
        } else if ("debug".equals(sub)) {
            p.addChatMessage(new ChatComponentText(
                "Debug mode: enable via config (debugHud=true) to see HUD overlay."));
        } else if ("storm".equals(sub)) {
            boolean raining = p.worldObj.isRaining();
            boolean thundering = p.worldObj.isThundering();
            float rainStr = p.worldObj.rainingStrength;
            p.addChatMessage(new ChatComponentText(
                String.format("Weather: raining=%b thundering=%b rainStrength=%.2f",
                    raining, thundering, rainStr)));
        } else {
            p.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
        }
    }

    @Override
    public int getRequiredPermissionLevel() { return 0; }
}
