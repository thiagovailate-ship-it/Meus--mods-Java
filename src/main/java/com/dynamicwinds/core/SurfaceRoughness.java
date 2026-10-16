package com.dynamicwinds.core;

/**
 * Fator de energia disponivel para um sistema de tempestade, conforme a
 * superficie sob ele.
 *
 * <p>Uma tempestade tropical se alimenta de agua quente: sobre o oceano ela
 * mantem a intensidade, sobre continente ela perde forca por atrito e por falta
 * de fluxo de calor. O nucleo de fisica nao conhece o Minecraft, entao quem
 * chama informa esse fator - veja a implementacao em
 * {@code com.dynamicwinds.api.Wind}.</p>
 */
public interface SurfaceRoughness {

    /**
     * @param x coordenada X do centro do sistema
     * @param z coordenada Z do centro do sistema
     * @return fator de 0 a 1 (1.0 = oceano aberto, ~0.45 = continente)
     */
    double energyAt(double x, double z);

    /** Trata qualquer superficie como oceano (usado no VFX e em testes). */
    SurfaceRoughness OPEN_WATER = new SurfaceRoughness() {
        @Override
        public double energyAt(double x, double z) {
            return 1.0;
        }
    };
}
