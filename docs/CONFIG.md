# Configuração do DynamicWinds (pt-BR)

O arquivo é `config/dynamicwinds.cfg`. Comentários gerados no arquivo usam inglês para seguir a convenção do ecossistema; este documento explica as opções em português. Os limites numéricos são validados durante o carregamento e valores corrigidos são registrados no console.

> Em multiplayer, o servidor é a autoridade para parâmetros de simulação. O cliente recebe seed e opções físicas do servidor; opções exclusivamente visuais permanecem locais.

| Categoria / chave | Padrão | Faixa / valores | Efeito e custo |
|---|---:|---|---|
| `client.enableParticles` | `true` | boolean | Partículas decorativas do vento. Aumenta custo gráfico. |
| `client.enableAudio` | `true` | boolean | Leitos, rajadas e áudio de tempestade; custo de áudio local. |
| `client.debugHud` | `false` | boolean | Mostra vetor e amostra de vento para depuração. |
| `gameplay.enablePush` | `true` | boolean | Aplica força do vento a entidades e itens; afeta gameplay. |
| `general.baseSpeedMultiplier` | `1.0` | 0–5 | Multiplicador do vento de fundo. Valores altos podem aumentar empurrões e dano. |
| `tornado.tornadoParticleRate` | `8` | 1–32 | Tentativas de partícula por tick perto de tornado. |
| `tornado.tornadoDebrisRate` | `1` | 0–4 | Frequência de detritos de tornado. Zero desliga detritos. |
| `tornado.tornadoMaxViewDistance` | `120` | 20–300 blocos | Distância de renderização de tornados. |
| `storms.enableStorms` | `true` | boolean | Liga tempestades naturais. Sistemas já registrados continuam até o fim do ciclo. |
| `storms.stormSpawnChance` | `0.35` | 0–1 | Chance de geração natural em cada janela de 24.000 ticks. |
| `storms.stormsInDimensions` | `[0]` | lista de IDs | Dimensões com geração natural habilitada; `[0]` é o Overworld. |
| `storms.northernHemisphere` | `true` | boolean | Define o sentido da rotação ciclônica. |
| `storms.stormForceWeather` | `true` | boolean | Permite que tempestades próximas forcem chuva/trovoada. |
| `storms.stormLightning` | `true` | boolean | Permite relâmpagos convectivos da tempestade. |
| `storms.stormRenderSky` | `true` | boolean | Desenha teto, parede e bandas de nuvem. |
| `storms.stormMaxViewDistance` | `420` | 64–2048 blocos | Distância máxima do teto de nuvens. |
| `storms.stormRainRate` | `16` | 0–64 partículas/tick | Chuva inclinada própria. Zero desliga a chuva adicional. |
| `destruction.enableDestruction` | `true` | boolean | Permite que vento arranque blocos; revise antes de habilitar em servidor público. |
| `destruction.destructionBlocksPerTick` | `24` | 0–256 | Teto global de blocos arrancados por tick. Zero impede quebra. |
| `destruction.destructionMaxPerStorm` | `60` | 0–10000 | Teto total de blocos por sistema de tempestade. |
| `destruction.destructionRadiusScale` | `1.0` | 0.1–3 | Escala do raio de busca ao redor do jogador. |
| `destruction.destructionDropItems` | `false` | boolean | Solta drops; aumenta risco de lag e entidades no chão. |
| `destruction.destructionProtectSpawn` | `true` | boolean | Protege a área de spawn. |
| `destruction.destructionWarnOnly` | `false` | boolean | Registra blocos que seriam quebrados, sem removê-los. Recomendado para ensaio inicial. |
| `destruction.destructionProtectedBlocks` | bedrock, obsidian | IDs `mod:bloco` | Blocos sempre protegidos. Também pode editar com `/wind protect add/remove/list`. |
| `destruction.destructionProtectedDimensions` | `[]` | lista de IDs | Dimensões completamente imunes à destruição. |
| `perf.maxParticles` | `400` | 50–5000 partículas/tick | Teto das partículas do mod; folhas/pólen degradam primeiro, depois detritos/spray; chuva de tempestade tem reserva prioritária. |

A GUI de configuração pode ser aberta pela tela de Mods do Forge. Ao fechar, as opções são revalidadas e gravadas. Para servidor dedicado, edite o arquivo do lado servidor e reinicie para aplicar a configuração inicial.
