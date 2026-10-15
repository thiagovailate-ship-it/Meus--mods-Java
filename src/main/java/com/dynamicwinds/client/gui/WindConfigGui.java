package com.dynamicwinds.client.gui;

import com.dynamicwinds.integration.WindConfig;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.config.GuiConfig;

import java.util.ArrayList;
import java.util.List;

public class WindConfigGui extends GuiConfig {
    public WindConfigGui(GuiScreen parent) {
        super(parent, buildElements(), "dynamicwinds", false, false, "DynamicWinds Configuration");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List buildElements() {
        List list = new ArrayList();
        if (WindConfig.cfg != null) {
            for (String cat : WindConfig.cfg.getCategoryNames()) {
                list.add(new ConfigElement(WindConfig.cfg.getCategory(cat)));
            }
        }
        return list;
    }
}
