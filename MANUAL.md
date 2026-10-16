# Manual do DynamicWinds

DynamicWinds adiciona vento determinístico, instrumentos meteorológicos e sistemas de tempo severo ao Minecraft Java 1.7.10 (Forge). A física usa seed, posição e relógio do mundo; em multiplayer, pacotes do servidor sincronizam seed, configuração física e tempestades ativas.

## Instalação

1. Instale Minecraft 1.7.10 com Forge compatível com a distribuição do mod.
2. Copie o JAR do DynamicWinds para `mods/` no cliente e no servidor.
3. Inicie uma vez para criar `config/dynamicwinds.cfg`.
4. Em servidor, leia `docs/CONFIG.md`; configure `destructionWarnOnly=true` antes de decidir se quer permitir destruição real.

## Comandos

Todos os comandos começam com `/wind` (alias `/w`).

- `/wind get [x y z]`: vetor e velocidade no ponto. Use em conjunto com o HUD de debug para comparar amostras.
- `/wind profile`: resumo do modelo físico.
- `/wind debug`: informa como habilitar o HUD de depuração.
- `/wind storm list`: lista sistemas ativos e distâncias.
- `/wind storm spawn <vortice|tropical|furacao|maior> [distância] [intensidade] [fase]`: invoca um sistema (OP nível 2). Fases: `genesis`, `maduro`, `maturo-dip`, `dissipacao`.
- `/wind storm where`: distância, rumo, ETA aproximada e velocidade de translação do sistema mais próximo.
- `/wind storm clear`: remove sistemas invocados da dimensão atual (OP nível 2).
- `/wind field`: tabela radial para inspecionar o perfil do sistema próximo.
- `/wind dump [raio] [passo]`: grava CSV no diretório do save. Rode `python3 tools/check_field.py <arquivo.csv>` para comparar o componente de tempestade com a equação de referência.
- `/wind protect add|remove|list <mod:bloco>`: edita a lista persistente de blocos protegidos.
- `/wind destruction stats`: resumo de blocos quebrados/avisados no último minuto.

## Cajado da Catástrofe

O cajado pode ser criado com estrela do Nether, vara de blaze e pérola do Ender. Agache e clique direito para alternar o tipo; clique direito normal invoca o tipo escolhido. O cooldown é de 15 segundos. A tempestade é independente do item após a invocação. A lista `stormsInDimensions` também restringe o uso do cajado.

## Barômetro

Crie com ferro, redstone e bússola. Clique direito para ler vento e déficit de pressão, incluindo tendência dos últimos 200 ticks. Agache + clique alterna unidade entre m/s, km/h e hPa. Déficit crescente corresponde à pressão caindo.

## HUD e instrumentos

O alerta de tempestade aparece quando um sistema está próximo/previsto. `debugHud=true` mostra os números usados na simulação. Catavento, manga de vento e anemômetro mostram direção/força; o anemômetro fornece sinal de redstone.

## Multiplayer e servidor

- O servidor envia seed/configuração física e snapshots de tempestades por dimensão.
- Sistemas naturais são gerados no servidor e replicados; o cliente não cria uma simulação concorrente.
- Por padrão, tempestades naturais ocorrem no Overworld (`stormsInDimensions=[0]`).
- Para testar destruição, use modo de aviso e confira `/wind destruction stats`; proteja blocos com `/wind protect add` antes de ligar dano real.
- O mod protege TEs, instrumentos e área de spawn; não substitui um mod de claims/anti-griefing.

## Perguntas frequentes

**Por que a tempestade não aparece no Nether?** A dimensão -1 não está na lista padrão. Adicione o ID à `stormsInDimensions` se quiser habilitá-la.

**Por que a configuração física do cliente parece não ser respeitada?** No multiplayer, o servidor é autoridade para simulação; apenas opções visuais locais continuam sendo controladas pelo cliente.

**A tempestade sobrevive a reinício?** Os sistemas vivos são salvos como parâmetros imutáveis e reconstruídos pelo relógio do mundo; valide com um backup de save antes de usar em produção.

**A tempestade altera a chuva vanilla?** Clima forçado é opcional; a precipitação inclinada também usa partículas próprias.

## Limitações conhecidas

- A aprovação visual final requer Minecraft 1.7.10 com OpenGL/Forge. O roteiro está em `docs/PLAYTEST.md`; resultados e capturas ainda precisam ser preenchidos em uma sessão de jogo real.
- O modelo é analítico e jogável, não CFD nem escala geográfica literal.
