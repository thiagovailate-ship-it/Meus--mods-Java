# DynamicWinds

Mod de vento dinâmico para Minecraft Java Edition 1.7.10 (Forge).

## O que o mod implementa

- **Campo de vento determinístico**: `Wind.getWindAt(world, x, y, z)` retorna vetor, velocidade e rajada; o servidor envia seed/configuração de física ao cliente. Determinismo é mecanismo da simulação, não substituto de sincronização.
- **Autoridade e persistência**: o cliente não gera tempestades naturais; recebe snapshots por dimensão. Parâmetros de nascimento dos sistemas vivos são guardados em `WorldSavedData`.
- **Barômetro e alerta**: item portátil mostra déficit/tendência; HUD avisa quando uma tempestade se aproxima.
- Manual: [`MANUAL.md`](MANUAL.md); opções: [`docs/CONFIG.md`](docs/CONFIG.md); roteiro visual: [`docs/PLAYTEST.md`](docs/PLAYTEST.md); relatório: [`RELATORIO_ENTREGA.md`](RELATORIO_ENTREGA.md).
- **Modelo físico**: curl de ruído (divergência ~0), perfil logarítmico vertical, direção de pressão rotativa, ciclo diurno (pico ao meio-dia), fator de rajada (Ornstein-Uhlenbeck com ruído reutilizado), contribuição de tornado.
- **Tempestades severas**: quatro tipos — vórtice (tornado/tromba d'água), tempestade tropical, furacão e furacão maior — com ciclo de vida completo (gênese, intensificação, maturidade com ciclo de substituição da parede do olho, dissipação) e trajetória em arco.
- **Clima**: intensidade de tempestade derivada do estado do mundo (`isRaining`, `isThundering`, `rainingStrength`). As tempestades severas *comandam* o clima: chuva, trovoada e relâmpagos passam a ser consequência do sistema, e o mod para de forçar quando nenhum sistema alcança ninguém.
- **Partículas visuais**: folhas, pólen, poeira, redemoinhos, rajadas e detritos, espawados ao redor do jogador conforme velocidade local.
- **Áudio**: som ambiente de vento, sons de rajada e rustle de folhas, com controle por `enableAudio`.
- **Entidades e itens**: força de vento aplicada com dinâmica de fluidos (0.5 * rho * v² * coeff), clipagem de velocidade e arrasto. Itens não têm multiplicação de velocidade.
- **Instrumentos**: wind vane (orientação), windsock (inflação), anemometer (saída redstone 0..15 e rotor visual). Os três blocos são desenhados por TESR, com atlas próprio por face.
- **Cajado da Catástrofe**: item que invoca um sistema pronto, que depois vive sozinho sob a física. Agachado + clique direito troca o tipo (a escolha fica no NBT do item, então dois cajados podem ter tipos diferentes).
- **Destruição**: dano pesado derivado do campo de vento, não de uma tabela de raio (ver a seção "Física das tempestades").
- **Configuração**: vento, VFX, dimensões permitidas, teto de destruição, modo de aviso e lista de blocos protegidos; opções e limites em [`docs/CONFIG.md`](docs/CONFIG.md).
- **Comandos**: `/wind get|profile|debug|storm|field|dump|perf|protect|destruction`; sintaxe completa em [`MANUAL.md`](MANUAL.md).
- **HUD de depuração**: mostra vetor, velocidade e rajada, mais categoria, déficit de pressão, vento de pico, distância ao centro e densidade da névoa quando `debugHud` está ativo.

## Física das tempestades

O núcleo (`com.dynamicwinds.core`) é Java puro, sem nenhuma referência ao Minecraft, e é
onde vive a física. A regra do projeto é: **a arte não desenha nada que a física não
diga**. O raio do olho é o raio do vento máximo, o sense de rotação é o do campo, o
alcance dos efeitos é o raio externo do sistema, e a destruição consulta o mesmo campo de
vento que o resto do mod.

Os quatro sistemas, com os números que o código usa na maturidade (`StormKind`):

| tipo | vento máximo | `Rmax` | raio externo | duração | categoria |
| --- | --- | --- | --- | --- | --- |
| vórtice (tornado / tromba d'água) | 28 m/s (100 km/h) | 9 b | 150 b | ~3,3 min | EF0-1 |
| tempestade tropical | 24 m/s (86 km/h) | 55 b | 260 b | ~10 min | TS |
| furacão | 46 m/s (166 km/h) | 85 b | 400 b | ~15 min | Cat 1-2 |
| furacão maior | 72 m/s (259 km/h) | 130 b | 560 b | ~20 min | Cat 3+ |

O pedido de "furacão de 3 níveis" é a coluna das categorias: tempestade tropical, furacão e
furacão maior. O vórtice é o quarto tipo, de outra escala (`Rmax` de 9 blocos contra 55 a 130
dos ciclones) — e por isso é o único que vira funil no céu, em vez de parede de olho. O
rótulo do vórtice é EF e não "tornado categoria X" de propósito: a escala EF mede rajada de
3 segundos, e 28 m/s sustentados no modelo ficam na casa dos 100 km/h.

- **Perfil de vento** (`VortexBalance`): Rankine modificado — rotação de corpo sólido
  dentro do raio de vento máximo e decaimento em lei de potência fora, contínuo em
  `Rmax` e com pico exatamente `Vmax`.
- **Campo de pressão**: não é uma curva ajustada à mão. É a integral do balanço do vento
  gradiente, `dp/dr = ρ(v²/r + f·v)`, sobre o *mesmo* perfil de vento, resolvida
  analiticamente. Os déficits centrais que saem disso ficam nas faixas observadas: 7,9 hPa
  (tempestade tropical), 29,3 (furacão) e 71,8 (furacão maior). O termo de Coriolis é
  mantido explícito por fidelidade — ele contribui menos de 0,1% (a escala é
  ciclostrófica), mas é o que define o sentido de rotação por hemisfério.
- **Estrutura do campo**: inflow em espiral de Ekman (6° a 26° conforme o raio),
  ascendência na parede do olho, subsidiência dentro do olho, translação do sistema somada
  ao campo, ganho dentro da camada limite e decaimento acima do topo da coluna.
- **Ciclo de vida**: gênese, intensificação, maturidade e dissipação em `smoothstep` — sem
  salto de vento na troca de fase — e o campo se **expande ao enfraquecer**, como em um
  ciclone real.
- **Landfall**: a energia da superfície (oceano quente versus continente, por bioma da
  posição central) entra no sistema, que perde força em terra.
- **Escala, dita com honestidade**: 1 bloco = 1 metro para velocidade, então `Vmax` está em
  m/s (72 m/s ≈ 260 km/h). Os *raios* são comprimidos cerca de 300x em relação a um
  ciclone real — um furacão verdadeiro tem `Rmax` de 30 a 60 km — para caber em uma área
  jogável. O que se preserva é a relação vento-pressão, que é o que a escala acerta.
- **Sem CFD**: simulação de fluido de verdade é impossível a 20 TPS em blocos. O que existe
  aqui é meteorologia padrão (Rankine modificado, balanço do vento gradiente, lei
  logarítmica, inflow de Ekman) travada por testes, com a calibração documentada em vez de
  escondida.
- **Destruição**: cada bloco candidato é consultado no mesmo campo de vento e quebra se
  passar de `v_critico = 9 + 24·dureza` m/s (folhagem ≈ 14, terra ≈ 21, tronco ≈ 57,
  obsidiana praticamente nunca). Daí saem de graça: o olho fica intacto, a faixa
  destrutiva é a parede do olho e migra com ela, e o landfall enfraquece a destruição
  junto com o sistema. Os exemplos abaixo usam valores de dureza exercitados por
  `DestructionRulesTest`, que imprime a tabela no log de Gradle:

| dureza | `v_critico` |
| ---: | ---: |
| 0,0 (piso) | 9 m/s |
| 0,2 (folhagem, aproximado) | 13,8 m/s |
| 0,5 (terra, aproximado) | 21 m/s |
| 1,5 (pedra, aproximado) | 45 m/s |
| 2,0 (tronco, aproximado) | 57 m/s |
| 50,0 (obsidiana, aproximado) | 1209 m/s |

## Céu das tempestades

A nuvem do sistema é geometria própria, desenhada no `RenderWorldLastEvent`, e **não** um
disco fixo: cada peça sai das mesmas grandezas que o campo de vento usa, então a forma
acompanha a física.

| peça | de onde vem |
| --- | --- |
| parede do olho | cilindro duplo em `r = 1,02·rmax` e `1,45·rmax`, do solo ao teto |
| funil (vórtice) | cone de `0,42·rmax` (base) a `2,6·rmax` (topo), perfil `f^0,75`, inclinado para **trás** em relação ao deslocamento |
| bandas espirais | 3 braços em espiral logarítmica, `base + spin·ln(r/r_in)·1,6`, de `1,15·rmax` a `1,02·r_out` |
| rodapé | cortina pendurada na parede do olho; é ela que dá volume ao teto visto de baixo |
| teto | anel de `rmax·{1,35 (vórtice) \| 0,9}` até `1,04·r_out`, 5 anéis radiais com barriga de 18 blocos |
| camada alta | concha de gelo acima do teto (`rmax·1,05` a `1,16·r_out`), só com intensidade > 0,35 |

- **O olho é consequência, não desenho à parte.** O alpha do teto zera em `r < rmax`, a
  mesma `rmax` que governa o vento máximo; quando o sistema enfraquece, `rmax` cresce e o
  olho abre junto — como um furacão em dissipação.
- **Sentido de rotação** é `spin = +1` no hemisfério norte e `−1` no sul, o mesmo `spin` do
  campo de vento: nuvem e vento giram para o mesmo lado ou a cena se contradiz.
- **Teto limitado pelo mundo**: `deckY = min(solo + 0,55·topo, 214)`. Em terreno muito alto
  não sobra altura para o funil, e aí a guarda pula a peça em vez de gerar geometria
  invertida.
- **Nível de detalhe**: 72 / 48 / 24 segmentos a 160 / 420 blocos, com desaparecimento
  gradual por distância em vez de corte seco.
- **Clarão do relâmpago** entra por vértice e lava o tom para o branco; vem do mesmo
  disparo que agenda o trovão — a luz chega na hora, o som a 343 m/s.
- **Duas regras do `Tessellator` do 1.7.10** que custam caro: `startDrawing` *lança exceção*
  se já houver lote aberto, então nenhum método retorna entre abrir e `draw()` (toda
  condição de degeneração é checada antes); e `bindTexture` liga na unidade de textura
  **ativa**, que no render do mundo é a do lightmap (`GL_TEXTURE1`) — por isso a unidade é
  salva, trocada para a padrão e devolvida no fim, senão a nuvem sai texturizada com o mapa
  de luz.
- **Conferência sem abrir o jogo**: `tools/preview_storm.py` espelha essa geometria em
  Python e gera `docs/storm_shape.png` (4 vistas de topo na mesma escala, 2 cortes
  verticais e o perfil de alpha). `python3 tools/preview_storm.py --check` varre 252
  combinações de tipo × intensidade × altura de solo e separa **guarda** (decisão de não
  desenhar, sempre antes do lote) de **problema** (geometria invertida ou nula — erro). É o
  substituto possível para rodar `runClient`, que não existe neste ambiente.

## Áudio

- Os 14 sons do mod são **gerados por script** (`tools/gen_sounds.py`, numpy + soundfile):
  nenhum arquivo binário vem de fora do repositório e rodar o script reproduz os mesmos
  bytes. Todos são Vorbis 44,1 kHz mono.
- O espectro não é chute: o gerador é ajustado **por medida** contra um alvo de energia por
  faixa (vento e trovão pesados no grave, chuva e folhagem no agudo). Duas armadilhas reais
  foram corrigidas assim — o ruído branco fazia as faixas agudas dominarem só por largura de
  banda, e o contínuo (offset DC) do ruído moldado concentrava energia nos primeiros bins,
  deixando o trovão "próximo" com 80% da energia abaixo de 100 Hz e quase nada de estalo.
- Os leitos (vento, chuva) são **loops de verdade**: `canRepeat()` com `getRepeatDelay() == 0`
  faz o gerenciador de som do 1.7.10 criar a fonte já em repetição — sem re-disparar o
  arquivo e sem emenda. Por isso os arquivos não são "steamados": fonte em fluxo relê o
  arquivo a cada volta e a emenda reaparece.
- O volume é mixado em tempo real: cada leito é um `ITickableSound`, e o gerenciador
  reaplica volume, altura e posição do canal a cada tick.
- A escolha de faixa é uma **interpolação**, não um degrau: a velocidade do vento vira uma
  posição contínua entre brisa / vento forte / tempestade, com pesos triangulares (no máximo
  duas faixas soando, somando 1). A chuva segue a intensidade no ponto, que cresce para
  dentro e **seca no olho** — onde o ar desce e não há nuvem carregada em cima.
- O trovão respeita a física da propagação: o clarão é instantâneo e o som sai **a 343 m/s**,
  então uma descarga na parede do olho é vista agora e ouvida quase um segundo depois, do
  lado onde caiu, com o nível caindo com a distância e o timbre trocando de estalo (perto)
  para rolar (médio) e só grave (longe) — que é o que a absorção do ar faz.
- O rugido fica mais grave quando a tempestade é maior: um vórtice de centenas de blocos
  desloca a turbulência para frequências mais baixas, pelo mesmo motivo que o jato de um
  avião é agudo e o de um furacão é grave.
- **Abrigo**: a profundidade sob o teto abafa (mais o agudo que o grave, por isso o trovão
  atenua pela raiz enquanto vento e chuva atenuam cheio).

## Receitas

As quatro receitas são moldes 3x3 comuns (bancada). O molde é lido de cima para baixo,
como aparece na bancada; `·` é casa vazia.

```
Catavento (windVane)        Manga de vento (windsock)     Anemômetro (anemometer)
·  V  ·                     ·  L  ·                       ·  R  ·
·  V  ·                     ·  L  ·                       F  C  F
B  B  B                     ·  V  ·                       ·  F  ·
V = vara, B = barra de ferro  L = lã, V = vara             R = redstone, F = barra de ferro,
                                                           C = comparador
```

```
Cajado da Catástrofe
·  ·  E
·  A  ·
P  ·  ·
E = estrela do Nether, A = vara de blaze, P = pérola do Enderman
```

O molde do cajado é o único com as três casas na diagonal: a estrela do Nether dá o
"toque final" no canto superior, a vara de blaze fica no centro (a energia térmica que o
ciclone consome) e a pérola do Enderman no canto inferior. Como o `Items.comparator` do
1.7.10 já exige duas barras de ferro laterais, o anemômetro é a receita mais cara das três
de instrumento.

## Testes

`src/test/java/com/dynamicwinds/core/StormPhysicsTest.java` trava o contrato da física:
pico do perfil e continuidade em `Rmax`, faixas de déficit por tipo, equivalência entre a
integral analítica e uma integração numérica de `dp/dr` em 200 000 passos, continuidade do
ciclo de vida, velocidade de deslocamento constante da trajetória (sem acumular erro de
passo), olho calmo versus parede do olho violenta, subsidiência versus ascendência,
inversão de rotação por hemisfério, landfall, determinismo e ciclo de vida dos sistemas
invocados.

## Build e CI

- JDK 25 + Gradle 9.2.0 (wrapper fornecido) + RetroFuturaGradle 2.0.2.
- CI: GitHub Actions `.github/workflows/build.yml` (ubuntu-latest, JDK 25, `./gradlew build --no-daemon --stacktrace`).
- Resultado: `BUILD SUCCESSFUL`, jar em `build/libs/`, testes JUnit 5 passam.

## Requisitos

- Minecraft Java Edition 1.7.10 com Forge.
- Para compilar localmente: JDK 25 (para RFG 2.0.2), Gradle 9.2.0 (ou usar o wrapper incluído).

## Céu da tempestade (renderizador)

O céu da tempestade não é um overlay: é geometria desenhada no mundo, no
`RenderWorldLastEvent`, toda ela derivada do sistema (`client/storm/StormRenderer`).
São cinco peças, do centro para fora:

| peça | o que é | de onde saem os números |
| --- | --- | --- |
| teto de nuvem | anel de 5 faixas entre `rmax·0,9` (vórtice: `rmax·1,35`) e `outer·1,04` | `rmax` do vento e o raio externo do tipo; o alpha zera em `r < rmax`, que é o olho aberto |
| rodapé | cortina pendurada na borda interna do teto, descendo `24·(0,55 + 0,45·intensidade)` blocos | dá volume ao teto visto de baixo |
| parede do olho | dois cilindros (alpha 0,60 e 0,26) do solo até o teto | raios `rmax·1,02` e `rmax·1,45`; no vórtice viram um funil cônico que se inclina contra a direção do deslocamento |
| bandas espirais | 3 braços em espiral logarítmica (`s(r) = s0 + spin·ln(r/rIn)·1,6`) | giram no mesmo sentido do campo (o `spin` do hemisfério) |
| camada alta | concha mais clara acima do teto, só com intensidade maior que 0,35 | `rmax·1,05` a `outer·1,16`, no topo da coluna do tipo |

- **Desenho de trás para frente** e escrita de profundidade desligada: o sistema mais
  distante é desenhado primeiro, senão duas tempestades sobrepostas apareceriam com a de
  trás por cima.
- **Nível de detalhe** por distância: 72 / 48 / 24 segmentos a 160 / 420 blocos, com o
  conjunto desaparecendo suavemente em vez de cortar seco.
- **O clarão do relâmpago** entra por vértice: a cor da nuvem é lavada para o branco no
  mesmo disparo que agenda o trovão (que chega depois, a 343 m/s), e a névoa clareia junto.
- **A altura do teto é limitada pelo mundo** (`min(solo + 0,55·topo, 214)`): em terreno
  muito alto não sobra altura para o funil, e aí a peça é simplesmente pulada.
- **`stormRenderSky`** desliga o céu sem remover o resto do VFX.

### Duas armadilhas do Tessellator que custam caro

1. `startDrawingQuads()` **lança exceção** (`Already tesselating!`) se já houver um lote
   aberto, e o lote só fecha no `draw()`. Por isso **nenhum método daqui retorna entre
   abrir e fechar**: toda condição de degeneração (raio nulo, altura nula, alpha zero) é
   checada antes de abrir o lote. Era exatamente esse o defeito da versão anterior.
2. O formato do vértice é fixado por flags de estado: `setNormal` vale do ponto em que é
   chamado até o fim do lote, então normal, cor e textura são definidos antes do primeiro
   vértice (a cor é por vértice e é definida antes de cada um).

E uma do lado do OpenGL: **`bindTexture` liga na unidade de textura ativa**, e durante o
render do mundo a unidade ativa é a do lightmap (`GL_TEXTURE1`). Sem salvar a unidade,
trocar para a padrão e devolver no fim, a nuvem sai texturizada com o mapa de luz.

### Conferindo sem abrir o jogo

`tools/preview_storm.py` repete essa geometria em Python e monta
`docs/storm_shape.png`: quatro vistas de topo (uma por tipo, na mesma escala), dois cortes
verticais (furacão maior e vórtice, com o funil inclinado) e o perfil de alpha do teto. O
modo `--check` varre 252 combinações (tipo × intensidade × altura de solo) e separa
**guarda** (o renderizador decide não desenhar a peça, sempre antes do lote) de
**problema** (geometria invertida ou nula). Resultado atual: nenhum problema; a única
guarda que dispara é a do funil, em terreno acima de y≈200, onde o teto bate no limite do
mundo.

## Texturas e renderização

- Texturas de bloco são **atlas 4x4 de 128x128** (células de 32px). Cada face escolhe
  sua célula, em vez de repetir a textura inteira esticada.
- As texturas são **geradas por script** (`tools/gen_textures.py`, Python puro, sem
  Pillow/numpy). Rodar o script reproduz os mesmos bytes, então a arte fica revisável
  em diff e não depende de edição manual de imagem.
- `tools/preview_textures.py` monta `docs/textures_preview.png` com a grade das células,
  para inspecionar as texturas sem abrir o jogo.
- `BoxMesh`/`AtlasCells`/`TextureAtlas` concentram geometria e UV; `BoxMeshTest` trava no
  CI o winding das faces (a versão anterior tinha 4 das 6 faces com normal para dentro e
  era descartada pelo culling) e os limites do atlas.
- Os instrumentos usam um tipo de render próprio: o bloco não entra na malha estática do
  chunk (quem desenha é o TESR) e o `ItemBlock` usa os ícones 2D `*_item.png`.

## Servidor e multiplayer

- O servidor é autoridade para tempestades naturais e envia snapshots por dimensão; sistemas invocados são replicados e persistidos em `WorldSavedData`.
- `/wind get` apresenta a amostra do servidor e, em seguida, uma comparação cliente/servidor no mesmo tick (`rounded@0.1=MATCH` é o aceite numérico).
- `stormsInDimensions` é `[0]` por padrão. Destruição só ocorre em dimensões habilitadas; use `destructionWarnOnly=true` antes de permitir quebra real.
- `destructionProtectedDimensions=[]` mantém todas as dimensões sem exceção de proteção, e `destructionProtectedBlocks` protege blocos identificados por registry ID.
- `perf.maxParticles=400` limita as partículas do mod por tick e reserva prioridade para a chuva da tempestade. `/wind perf` reporta partículas vivas quando cliente e servidor compartilham a JVM (integrated); num servidor dedicado, o contador do lado servidor não enxerga a JVM gráfica separada.

| categoria | opções principais | padrão | faixa / observação |
| --- | --- | --- | --- |
| `general` | `baseSpeedMultiplier` | `1.0` | `0..5` |
| `storms` | `stormSpawnChance`, `stormsInDimensions`, `stormRainRate` | `0.35`, `[0]`, `16` | chance `0..1`; chuva `0..64` |
| `destruction` | `enableDestruction`, `destructionWarnOnly`, `destructionBlocksPerTick`, `destructionMaxPerStorm` | `true`, `false`, `24`, `60` | teto por tick `0..256`; por tempestade `0..10000` |
| `perf` | `maxParticles` | `400` | `50..5000` partículas/tick |

A tabela completa e os custos estão em [`docs/CONFIG.md`](docs/CONFIG.md); para os passos operacionais, veja [`MANUAL.md`](MANUAL.md).

## Limitações conhecidas

- Mixins de renderização de chuva/neve não implementados; a chuva inclinada pelo vento é
  resolvida com partícula própria (`RainStreakParticle`), que lê a velocidade do campo no
  ponto de nascimento — sem depender da biblioteca de Mixin.
- A calibração de `destructionBlocksPerTick` e das taxas de partícula foi feita por
  estimativa física, não por playtest: os valores estão no config para ajuste direto.
- Validação no jogo (runClient/runServer) não foi executada neste ambiente (sem toolchain local); testes de runtime são pendentes de validação manual.
- Escala de velocidade do vento é relativa ao modelo; não há calibração formal para m/s do mundo real.
- Os modelos JSON dos blocos existem, mas não são usados no mundo: com o tipo de render
  próprio quem desenha o bloco é o TESR.

## Estado da validação de release

A fase 1.3.0 está em andamento. O CI `38892649192` passou no head `3d951c0` (build, 71 testes e verificações Python); a versão permanece 1.2.0. O playtest real de Minecraft/Forge, as nove capturas e os veredictos ainda estão pendentes. Não considere a release concluída até preencher `docs/PLAYTEST.md` com evidência de jogo.

## Repositório

- Branch de desenvolvimento desta sessão: `arena/3ea9d885-meus-mods-java`
- CI: https://github.com/thiagovailate-ship-it/Meus--mods-Java/actions
