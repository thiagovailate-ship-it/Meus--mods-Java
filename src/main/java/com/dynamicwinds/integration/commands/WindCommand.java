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

import java.io.File;
import java.io.PrintWriter;
import java.io.FileWriter;
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
        } else if ("protect".equals(sub)) {
            doProtect(p, args);
        } else if ("destruction".equals(sub)) {
            doDestruction(p, args);
        } else if ("field".equals(sub)) {
            doField(p, args);
        } else if ("dump".equals(sub)) {
            doDump(p, args);
        } else if ("perf".equals(sub)) {
            doPerf(p);
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

        if ("where".equals(action)) {
            StormSystem nearest = field.nearest(time, p.posX, p.posZ);
            if (nearest == null) {
                p.addChatMessage(new ChatComponentText("Nenhuma tempestade ativa."));
            } else {
                double distance = nearest.distanceTo(time, p.posX, p.posZ);
                double speed = StormKind.translationSpeed(nearest.kind);
                double eta = speed > 0.0 ? distance / (speed * 20.0) : Double.POSITIVE_INFINITY;
                p.addChatMessage(new ChatComponentText(String.format(
                        "#%d distance=%.1f bearing=%.1fdeg ETA=%.1fs translation=%.3fm/s Vmax=%.1fm/s",
                        nearest.id, distance, Math.toDegrees(nearest.headingAt(time)), eta,
                        speed * 20.0, nearest.vmaxAt(time))));
            }
            return;
        }

        if ("spawn".equals(action)) {
            if (!senderHasPermission(p)) return;
            if (!com.dynamicwinds.integration.WindConfig.stormsAllowedInDimension(
                    world.provider.dimensionId)) {
                p.addChatMessage(new ChatComponentText("Tempestades desativadas nesta dimensao."));
                return;
            }
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
            double progress = args.length > 5 ? parsePhase(args[5]) : 0.0;
            if (progress < 0.0) {
                p.addChatMessage(new ChatComponentText("Fase invalida: genesis, maduro, maturo-dip ou dissipacao."));
                return;
            }
            StormSystem s = field.spawnAtProgress(kind, time, x, z, yaw, mul, progress);
com.dynamicwinds.integration.DynamicWindsData.captureWorld(world, field);
            StormNetwork.broadcastSpawn(s, world.provider.dimensionId);
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
            if (removed > 0) com.dynamicwinds.integration.DynamicWindsData.markWorldDirty(world);
            StormNetwork.broadcastClear(world.provider.dimensionId);
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

    private void doField(EntityPlayer player, String[] args) {
        World world = player.worldObj;
        StormField field = Wind.stormsFor(world);
        long time = world.getTotalWorldTime();
        StormSystem storm = field == null ? null : field.nearest(time, player.posX, player.posZ);
        if (storm == null) {
            player.addChatMessage(new ChatComponentText("Nenhuma tempestade ativa para inspecionar."));
            return;
        }
        int ground = Wind.surfaceHeightAt(world, (int) storm.centerX(time), (int) storm.centerZ(time));
        double energy = Wind.surfaceEnergyFor(world).energyAt(storm.centerX(time), storm.centerZ(time));
        double[] radii = {0.0, storm.rmaxAt(time) * 0.5, storm.rmaxAt(time),
                storm.rmaxAt(time) * 1.5, storm.outerRadius()};
        player.addChatMessage(new ChatComponentText("r  total  tangential  radial  vertical  deficit_hPa"));
        for (double radius : radii) {
            double x = storm.centerX(time) + radius;
            double z = storm.centerZ(time);
            com.dynamicwinds.core.Vec3 v = storm.wind(time, x, ground + 10.0, z, ground, energy);
            double radial = v.x;
            double tangential = v.z;
            player.addChatMessage(new ChatComponentText(String.format(
                    "%.1f  %.3f  %.3f  %.3f  %.3f  %.4f", radius, v.length(),
                    tangential, radial, v.y, storm.deficitHpa(time, x, z))));
        }
    }

    private void doDump(EntityPlayer player, String[] args) {
        if (!senderHasPermission(player)) return;
        int radius = args.length > 1 ? parseInteger(args[1], 32) : 32;
        int step = args.length > 2 ? parseInteger(args[2], 8) : 8;
        radius = Math.max(4, Math.min(128, radius));
        step = Math.max(2, Math.min(32, step));
        World world = player.worldObj;
        File file = new File(world.getSaveHandler().getWorldDirectory(), "dynamicwinds-dump.csv");
        try {
            PrintWriter out = new PrintWriter(new FileWriter(file));
            out.println("world_time,x,z,y,v_total,v_tangential,v_radial,wind_vertical,deficit_hpa,storm_speed,storm_kind,storm_id,spawn_tick,spawn_x,spawn_z,heading,turn_rate,north,intensity_mul,surface_y,energy");
            long time = world.getTotalWorldTime();
            int centerX = (int) Math.floor(player.posX), centerZ = (int) Math.floor(player.posZ);
            StormField field = Wind.stormsFor(world);
            SurfaceRoughness energy = Wind.surfaceEnergyFor(world);
            for (int x = centerX - radius; x <= centerX + radius; x += step) {
                for (int z = centerZ - radius; z <= centerZ + radius; z += step) {
                    double y = player.posY;
                    WindSample sample = Wind.getWindAt(world, x + 0.5, y, z + 0.5);
                    StormSystem storm = field == null ? null : field.strongestAt(time, x + 0.5, z + 0.5);
                    double tan = 0.0, radial = 0.0, vertical = 0.0, deficit = 0.0;
                    double stormSpeed = 0.0, spawnX = 0.0, spawnZ = 0.0, heading = 0.0;
                    double turnRate = 0.0, intensity = 0.0, energyAtCenter = 0.0;
                    long stormId = 0L, spawnTick = 0L;
                    int kind = -1, surface = Wind.surfaceHeightAt(world, x, z);
                    boolean north = true;
                    if (storm != null) {
                        energyAtCenter = energy.energyAt(storm.centerX(time), storm.centerZ(time));
                        com.dynamicwinds.core.Vec3 v = storm.wind(time, x + 0.5, y, z + 0.5,
                                surface, energyAtCenter);
                        double dx = x + 0.5 - storm.centerX(time), dz = z + 0.5 - storm.centerZ(time);
                        double length = Math.sqrt(dx * dx + dz * dz);
                        double ux = length == 0.0 ? 1.0 : dx / length;
                        double uz = length == 0.0 ? 0.0 : dz / length;
                        radial = v.x * ux + v.z * uz;
                        tan = -v.x * uz + v.z * ux;
                        vertical = v.y;
                        stormSpeed = v.length();
                        deficit = storm.deficitHpa(time, x + 0.5, z + 0.5);
                        kind = storm.kind;
                        stormId = storm.id;
                        spawnTick = storm.spawnTick;
                        spawnX = storm.spawnX;
                        spawnZ = storm.spawnZ;
                        heading = storm.heading0;
                        turnRate = storm.turnRate;
                        north = storm.north;
                        intensity = storm.intensityMul;
                    }
                    out.println(time + "," + (x + 0.5) + "," + (z + 0.5) + "," + y + "," + sample.speed
                            + "," + tan + "," + radial + "," + vertical + "," + deficit
                            + "," + stormSpeed + "," + kind + "," + stormId + "," + spawnTick
                            + "," + spawnX + "," + spawnZ + "," + heading + "," + turnRate
                            + "," + north + "," + intensity + "," + surface + "," + energyAtCenter);
                }
            }
            out.close();
            player.addChatMessage(new ChatComponentText("CSV salvo: " + file.getAbsolutePath()));
        } catch (Exception ex) {
            player.addChatMessage(new ChatComponentText("Falha ao gravar CSV: " + ex.getMessage()));
        }
    }

    private static int parseInteger(String text, int fallback) {
        try { return Integer.parseInt(text); } catch (NumberFormatException ex) { return fallback; }
    }

    private void doPerf(EntityPlayer player) {
        if (!senderHasPermission(player)) return;
        World world = player.worldObj;
        StormField field = Wind.stormsFor(world);
        int active = field == null ? 0 : field.activeAt(world.getTotalWorldTime()).size();
        player.addChatMessage(new ChatComponentText("[perf] "
                + com.dynamicwinds.integration.PerfTracker.summary()));
        player.addChatMessage(new ChatComponentText("[perf] dimension="
                + world.provider.dimensionId + " activeStorms=" + active + "; "
                + com.dynamicwinds.integration.DestructionHandler.statsSummary(
                        world.provider.dimensionId, world.getTotalWorldTime())));
    }

    private void doDestruction(EntityPlayer player, String[] args) {
        if (!senderHasPermission(player)) return;
        if (args.length < 2 || !"stats".equalsIgnoreCase(args[1])) {
            player.addChatMessage(new ChatComponentText("Use: /wind destruction stats"));
            return;
        }
        player.addChatMessage(new ChatComponentText(com.dynamicwinds.integration.DestructionHandler
                .statsSummary(player.worldObj.provider.dimensionId, player.worldObj.getTotalWorldTime())));
    }

    private void doProtect(EntityPlayer player, String[] args) {
        if (!senderHasPermission(player)) return;
        if (args.length < 2) {
            player.addChatMessage(new ChatComponentText("Use: /wind protect <add|remove|list> [mod:id]"));
            return;
        }
        String action = args[1].toLowerCase();
        String[] current = com.dynamicwinds.integration.WindConfig.destructionProtectedBlocks;
        if ("list".equals(action)) {
            player.addChatMessage(new ChatComponentText("Protected blocks: " + java.util.Arrays.toString(current)));
            return;
        }
        if (!"add".equals(action) && !"remove".equals(action)) {
            player.addChatMessage(new ChatComponentText("Use: /wind protect <add|remove|list> [mod:id]"));
            return;
        }
        if (args.length < 3) {
            player.addChatMessage(new ChatComponentText("Informe um ID de bloco, ex.: minecraft:obsidian"));
            return;
        }
        String name = args[2].toLowerCase();
        if ("add".equals(action) && net.minecraft.block.Block.blockRegistry.getObject(name) == null) {
            player.addChatMessage(new ChatComponentText("Bloco desconhecido: " + name));
            return;
        }
        java.util.ArrayList<String> updated = new java.util.ArrayList<String>(java.util.Arrays.asList(current));
        if ("add".equals(action)) {
            if (!updated.contains(name)) updated.add(name);
        } else {
            updated.remove(name);
        }
        com.dynamicwinds.integration.WindConfig.setProtectedBlocks(updated.toArray(new String[updated.size()]));
        player.addChatMessage(new ChatComponentText("Protected blocks saved: "
                + java.util.Arrays.toString(com.dynamicwinds.integration.WindConfig.destructionProtectedBlocks)));
    }

    private static double parsePhase(String phase) {
        if ("genesis".equalsIgnoreCase(phase) || "genese".equalsIgnoreCase(phase)) return 0.02;
        if ("maduro".equalsIgnoreCase(phase) || "mature".equalsIgnoreCase(phase)) return 0.50;
        if ("maturo-dip".equalsIgnoreCase(phase) || "mature-dip".equalsIgnoreCase(phase)) return 0.60;
        if ("dissipacao".equalsIgnoreCase(phase) || "dissipation".equalsIgnoreCase(phase)) return 0.88;
        return -1.0;
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
            return getListOfStringsMatchingLastWord(args, new String[]{"get", "profile", "debug", "storm", "protect", "destruction", "field", "dump", "perf"});
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
