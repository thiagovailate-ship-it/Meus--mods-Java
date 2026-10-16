package com.dynamicwinds.content.items;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.StormField;
import com.dynamicwinds.core.StormKind;
import com.dynamicwinds.core.StormSystem;
import com.dynamicwinds.integration.network.StormNetwork;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import java.util.List;

/**
 * Cajado da Catastrofe: invoca um sistema de tempo severo.
 *
 * <p>Uso rapido: clique direito cria o sistema escolhido a algumas dezenas de
 * blocos a frente (ele nasce ja formado, com ciclo de vida completo, e depois
 * vive sozinho - enfraquece sobre continente, se desloca e se dissipa). Agachado
 * + clique direito troca o tipo, do vortice ao furacao maior.</p>
 *
 * <p>A escolha fica gravada no proprio ItemStack (NBT), entao dois cajados
 * podem estar configurados para tipos diferentes ao mesmo tempo.</p>
 *
 * <p>O nasce-e-vive sozinho e o ponto central do design: o cajado nao mantem
 * nada "ligado". Ele entrega um sistema a fisica e acabou o trabalho dele, o que
 * significa que o resultado e reproduzivel e nao depende do jogador continuar
 * segurando o item.</p>
 */
public class ItemCatastropheStaff extends Item {

    /** Chave NBT com o tipo escolhido. */
    private static final String TAG_KIND = "dwStormKind";
    /** Chave NBT com o tick do ultimo uso. */
    private static final String TAG_LAST = "dwLastCast";
    /** Recarga entre invocacoes, em ticks (15 s). */
    private static final long COOLDOWN = 300L;
    /** Distancia de invocacao a frente do jogador, em blocos. */
    private static final double DIST_MIN = 34.0;
    private static final double DIST_SPAN = 22.0;
    /** O cajado entrega um sistema 30% mais forte que o natural. */
    private static final double INTENSITY = 1.3;

    public ItemCatastropheStaff() {
        setUnlocalizedName("catastropheStaff");
        setTextureName("dynamicwinds:catastrophe_staff");
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabTools);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        // o servidor decide tudo; o cliente apenas recebe o resultado pela rede,
        // que evita duas invocacoes diferentes rodando em paralelo
        if (world.isRemote) return stack;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        int kind = tag.hasKey(TAG_KIND) ? tag.getInteger(TAG_KIND) : StormKind.HURRICANE;
        if (kind < 0 || kind >= StormKind.COUNT) kind = StormKind.HURRICANE;

        if (player.isSneaking()) {
            kind = (kind + 1) % StormKind.COUNT;
            tag.setInteger(TAG_KIND, kind);
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA
                    + "[Cajado da Catastrofe] Tipo escolhido: "
                    + EnumChatFormatting.WHITE + StormKind.name(kind)
                    + EnumChatFormatting.GRAY + " (" + StormKind.category(kind) + ")"));
            return stack;
        }

        long now = world.getTotalWorldTime();
        long last = tag.getLong(TAG_LAST);
        if (now - last < COOLDOWN) {
            long wait = (COOLDOWN - (now - last)) / 20L;
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED
                    + "[Cajado da Catastrofe] Ainda se formando... aguarde " + wait + "s."));
            return stack;
        }

        // direcao para onde o jogador olha (yaw 0 = +Z)
        double yaw = Math.toRadians(player.rotationYaw);
        double dist = DIST_MIN + world.rand.nextDouble() * DIST_SPAN;
        double x = player.posX - Math.sin(yaw) * dist;
        double z = player.posZ + Math.cos(yaw) * dist;

        StormField field = Wind.stormsFor(world);
        if (field == null) return stack;
        StormSystem s = field.spawn(kind, now, x, z, yaw, INTENSITY);
        StormNetwork.broadcastSpawn(s);
        tag.setLong(TAG_LAST, now);

        player.addChatMessage(new ChatComponentText(EnumChatFormatting.GOLD
                + "[Cajado da Catastrofe] " + StormKind.name(kind)
                + " formado a frente: deficit central de "
                + String.format("%.1f", s.centralDeficitHpa(now)) + " hPa, vento de "
                + String.format("%.1f", s.vmaxAt(now)) + " m/s."));
        world.playSoundEffect(player.posX, player.posY, player.posZ,
                "ambient.weather.thunder", 2.0F, 0.55F);
        return stack;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        int kind = StormKind.HURRICANE;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey(TAG_KIND)) {
            kind = tag.getInteger(TAG_KIND);
            if (kind < 0 || kind >= StormKind.COUNT) kind = StormKind.HURRICANE;
        }
        list.add(EnumChatFormatting.GRAY + "Invoca: "
                + EnumChatFormatting.WHITE + StormKind.name(kind));
        list.add(EnumChatFormatting.DARK_GRAY + "Vento de pico: "
                + String.format("%.1f", StormKind.vmax(kind)) + " m/s ("
                + StormKind.category(kind) + ")");
        list.add(EnumChatFormatting.DARK_GRAY + "Clique direito: invocar");
        list.add(EnumChatFormatting.DARK_GRAY + "Agachado + clique: trocar tipo");
    }
}
