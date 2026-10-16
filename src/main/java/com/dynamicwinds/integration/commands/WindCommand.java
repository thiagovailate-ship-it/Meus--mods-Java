package com.dynamicwinds.integration.commands;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.StormField;
import com.dynamicwinds.core.StormKind;
import com.dynamicwinds.core.StormSystem;
import com.dynamicwinds.core.SurfaceRoughness;
import com.dynamicwinds.core.WindSample;
import com.dynamicwinds.integration.network.StormNetwork;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Comandos do mod.
 *
 * <pre>
 * /wind get [x y z]          vento no ponto
 * /wind profile              explica o modelo fisico em uma linha
 * /wind debug                como ligar o HUD
 * /wind storm                clima local e sistemas ativos
 * /wind storm spawn &lt;tipo&gt; [dist] [intensidade]   (operador)
 * /wind storm clear          remove os sistemas invocados (operador)
 * </pre>
 */
public class WindCommand extends CommandBase {

    /** Nivel de permissao exigido para invocar/limpar sistemas. */
    private static final int OP_LEVEL = 2;

    @Override
    public String getCommandName() { return "wind"; }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/wind <get|profile|debug|storm> - 'storm' aceita spawn/clear";
    }

    @Override
    public List getCommandAliases() { return Arrays.asList("w"); }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayer)) return;
        EntityPlayer p = (EntityPlayer) sender;
        String sub = args.length > 0 ? args[0] : "get";

        if ("get".equals(sub)) {
            doGet(p, args);
        } else if ("profile".equals(sub)) {
            p.addChatMessage(new ChatComponentText(
                "Perfil de vento: u(z)=u*/k*ln(z/z0) na camada limite, ciclo diario, rajadas de "
                + "Ornstein-Uhlenbeck e, por cima, sistemas severos com perfil de Rankine "
                + "modificado e campo de pressao integrado do balanco do vento gradiente."));
        } else if ("debug".equals(sub)) {
            p.addChatMessage(new ChatComponentText(
                "Debug mode: enable via config (debugHud=true) to see HUD overlay."));
        } else if ("storm".equals(sub)) {
            doStorm(p, args);
        } else {
            p.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
        }
    }

    // ------------------------------------------------------------------

    private void doGet(EntityPlayer p, String[] args) {
        double x = p.posX, y = p.posY, z = p.posZ;
        String[] names = {"x", "y", "z"};
        double[] values = {x, y, z};
        for (int i = 0; i < 3 && args.length > i + 1; i++) {
            try {
                values[i] = Double.parseDouble(args[i + 1]);
            } catch (NumberFormatException e) {
                p.addChatMessage(new ChatComponentText("Valor invalido para " + names[i]));
                return;
            }
        }
        WindSample s = Wind.getWindAt(p.worldObj, values[0], values[1], values[2]);
        p.addChatMessage(new ChatComponentText(String.format(
            "Vento em (%.1f, %.1f, %.1f): vec=(%.3f, %.3f, %.3f) velocidade=%.2f m/s rajada=%.2f",
            values[0], values[1], values[2], s.vector.x, s.vector.y, s.vector.z, s.speed, s.gust)));
    }

    private void doStorm(EntityPlayer p, String[] args) {
        World world = p.worldObj;
        String action = args.length > 1 ? args[1] : "list";
        StormField field = Wind.stormsFor(world);
        if (field == null) {
            p.addChatMessage(new ChatComponentText("Modelo de vento indisponivel."));
            return;
        }
        long time = world.getTotalWorldTime();

        if ("spawn".equals(action)) {
            if (!senderHasPermission(p)) return;
            int kind = args.length > 2 ? parseKind(args[2]) : StormKind.HURRICANE;
            if (kind < 0) {
                p.addChatMessage(new ChatComponentText("Tipo invalido. Use: vortice, tropical, "
                        + "furacao, maior (ou 0 a 3)."));
                return;
            }
            double dist = 40.0;
            if (args.length > 3) {
                try { dist = Double.parseDouble(args[3]); } catch (NumberFormatException e) { dist = 40.0; }
            }
            double mul = 1.0;
            if (args.length > 4) {
                try { mul = Double.parseDouble(args[4]); } catch (NumberFormatException e) { mul = 1.0; }
            }
            mul = Math.max(0.1, Math.min(3.0, mul));

            double yaw = Math.toRadians(p.rotationYaw);
            double x = p.posX - Math.sin(yaw) * dist;
            double z = p.posZ + Math.cos(yaw) * dist;
            StormSystem s = field.spawn(kind, time, x, z, yaw, mul);
            StormNetwork.broadcastSpawn(s);
            p.addChatMessage(new ChatComponentText(EnumChatFormatting.GOLD + "Sistema #" + s.id
                    + " (" + StormKind.name(kind) + ") formado em "
                    + String.format("%.0f, %.0f", x, z)
                    + " - deficit " + String.format("%.1f", s.centralDeficitHpa(time))
                    + " hPa, pico " + String.format("%.1f", s.vmaxAt(time)) + " m/s."));
            return;
        }

        if ("clear".equals(action)) {
            if (!senderHasPermission(p)) return;
            int removed = field.dynamicCount();
            field.clearDynamic();
            StormNetwork.broadcastClear();
            p.addChatMessage(new ChatComponentText("Sistemas invocados removidos: " + removed + "."));
            return;
        }

        // listagem
        SurfaceRoughness energy = Wind.surfaceEnergyFor(world);
        List<StormSystem> active = field.activeAt(time, energy);
        StormSystem nearest = field.nearest(time, p.posX, p.posZ);
        p.addChatMessage(new ChatComponentText(String.format(
            "Clima: chovendo=%b trovejando=%b | sistemas ativos=%d (invocados=%d)",
            world.isRaining(), world.isThundering(), active.size(), field.dynamicCount())));
        if (nearest != null) {
            p.addChatMessage(new ChatComponentText(String.format(
                "Mais proximo: %s a %.0f blocos - %s",
                nearest.describe(time), nearest.distanceTo(time, p.posX, p.posZ),
                nearest.playerSpawned ? "invocado" : "natural")));
        }
        for (int i = 0; i < active.size(); i++) {
            StormSystem s = active.get(i);
            p.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + " - #" + s.id + " "
                    + s.describe(time) + " a " + String.format("%.0f",
                        s.distanceTo(time, p.posX, p.posZ)) + " blocos"));
        }
    }

    /** Traduz nome ou numero de tipo. Retorna -1 se nao reconhecer. */
    private int parseKind(String arg) {
        if (arg == null) return -1;
        String a = arg.toLowerCase();
        if ("0".equals(a) || "vortice".equals(a) || "vórtice".equals(a) || "tornado".equals(a)) {
            return StormKind.VORTEX;
        }
        if ("1".equals(a) || "tropical".equals(a) || "ts".equals(a)) {
            return StormKind.TROPICAL_STORM;
        }
        if ("2".equals(a) || "furacao".equals(a) || "furacão".equals(a) || "hurricane".equals(a)) {
            return StormKind.HURRICANE;
        }
        if ("3".equals(a) || "maior".equals(a) || "major".equals(a) || "cat5".equals(a)) {
            return StormKind.MAJOR_HURRICANE;
        }
        return -1;
    }

    private boolean senderHasPermission(ICommandSender sender) {
        if (sender.canCommandSenderUseCommand(OP_LEVEL, "wind")) return true;
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED
                + "Sem permissao: e preciso ser operador."));
        return false;
    }

    /** Lista de nomes para o autocompletar. */
    private static final List<String> TYPES = new ArrayList<String>(Arrays.asList(
            "vortice", "tropical", "furacao", "maior"));

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, new String[]{"get", "profile", "debug", "storm"});
        }
        if (args.length == 2 && "storm".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, new String[]{"list", "spawn", "clear"});
        }
        if (args.length == 3 && "storm".equals(args[0]) && "spawn".equals(args[1])) {
            return getListOfStringsMatchingLastWord(args, TYPES.toArray(new String[TYPES.size()]));
        }
        return null;
    }

    @Override
    public int getRequiredPermissionLevel() { return 0; }
}
