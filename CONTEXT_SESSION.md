# Contexto da Sessão - DynamicWinds Mod

## Visão Geral

DynamicWinds é um mod para Minecraft Java Edition 1.7.10 (Forge) que implementa um sistema de vento dinâmico determinístico. O mod calcula vetores de vento baseados em seed, tempo e posição, sem necessidade de sincronização cliente-servidor.

## Branch e Commit Atual

- **Branch**: `arena/98cf32d5-meus-mods-java`
- **Commit mais recente**: `ef26df6` - fix: construtor de ParticleIcons publico (+ HUD de tempestade e README)
- **Repo**: https://github.com/thiagovailate-ship-it/Meus--mods-Java

## Build e CI

- **CI**: GitHub Actions `.github/workflows/build.yml`
- **JDK**: 25 (necessário para RFG 2.0.2)
- **Gradle**: 9.2.0 (via wrapper incluído no repo)
- **Build**: `./gradlew build --no-daemon --stacktrace`
- **Status CI**: ✅ BUILD SUCCESSFUL (run 38838801206, com toda a expansão de tempestades)
- **Link CI**: https://github.com/thiagovailate-ship-it/Meus--mods-Java/actions/runs/38838801206
- **Leitura de falhas**: em run que falha, o workflow commita `build_head.log` na própria
  branch (commit `ci: build head log [skip ci]`). Antes de ler o log, confira o status do
  run: ele fica desatualizado depois do próximo build verde. Baixar o log pelo `gh` retorna
  `EOF`, então o arquivo no repositório é o caminho confiável.

## Arquitetura do Mod

### Módulos
- `core/` - Física pura (sem dependência Minecraft)
- `api/` - Facade Wind.getWindAt()
- `integration/` - Engine, config, comandos, push de entidades
- `client/` - Partículas, áudio, HUD, renderização, GUI
- `content/` - Blocos, tile entities, receitas
- `tools/` - Geradores reproduzíveis (texturas e prancha de prévia), Python puro

### Tempestades severas (expansão)

`core/` ganhou o subsistema de tempo severo, todo em Java puro: `StormKind` (tabelas por
tipo), `VortexBalance` (perfil de Rankine modificado e o campo de pressão integrado do
balanço do vento gradiente), `StormSystem` (um sistema com ciclo de vida, trajetória,
inflow de Ekman, parede do olho e olho), `StormField` (naturais por seed + invocados em
tempo de execução) e `SurfaceRoughness` (energia da superfície, que produz o landfall).

Quatro tipos, na linha da escala Saffir-Simpson:

| tipo | Vmax (m/s) | Rmax (blocos) | raio externo | duração | déficit central |
|---|---|---|---|---|---|
| vórtice | 28 | 9 | 150 | ~3 min | 10,4 hPa |
| tempestade tropical | 24 | 55 | 260 | ~10 min | 7,9 hPa |
| furacão | 46 | 85 | 400 | ~15 min | 29,3 hPa |
| furacão maior | 72 | 130 | 560 | ~20 min | 71,8 hPa |

Os testes de `StormPhysicsTest` são o contrato: pico do perfil e continuidade em `Rmax`,
faixas de déficit por tipo, integral analítica conferida contra integração numérica de
200 000 passos, continuidade do ciclo de vida, velocidade de deslocamento constante,
olho calmo versus parede violenta, subsidiência versus ascendência, inversão de rotação por
hemisfério, landfall, determinismo e ciclo de vida dos sistemas invocados.

### Integração das tempestades

- `StormWeather` - chuva, trovoada e relâmpagos comandados pelo sistema (o relâmpago só cai
  na banda convectiva, nunca no olho). Sem sistema alcançando ninguém, o mod para de forçar
  e o clima volta ao ciclo do jogo.
- `DestructionHandler` - dano pesado sem tabela de raio: cada bloco é consultado no mesmo
  campo de vento e quebra se passar de `v_critico = 9 + 24·dureza` m/s; troncos cedem em
  cadeia, entidades expostas levam dano, baús/máquinas e o spawn são preservados.
- `StormNetwork`/`PacketStormSync` - sistemas invocados são transmitidos levando só os
  parâmetros de nascimento (a evolução é derivada deles mais o relógio); o handler apenas
  enfileira, e a fila é drenada no tick do cliente para não correr com o VFX.
- `EntityPushHandler` - separa vento de fundo (que pode ser ocluído por um muro) do vento
  da tempestade (que vem de longe e não pode ser ocluído), com razão área/massa aproximada.
- `/wind storm [list|spawn|clear]` e o **Cajado da Catástrofe** (`content/items/`), que
  invoca o sistema escolhido; a escolha do tipo fica no NBT do item.

### VFX das tempestades

- `client/storm/StormRenderer` (reescrito) - teto de nuvem em anel com o olho aberto
  (alpha zera em `r < rmax`), rodapé pendurado na parede do olho, parede em cilindro duplo
  (ou funil cônico inclinado para trás, no vórtice), 3 bandas espirais em espiral
  logarítmica, camada alta de gelo quando intensidade > 0,35, LOD 72/48/24 a 160/420 b,
  desaparecimento gradual por distância, clarão do relâmpago por vértice e desenho de trás
  para frente. Todas as condições de degeneração são checadas **antes** de
  `startDrawingQuads` (nenhum return com lote aberto) e a unidade de textura é salva e
  devolvida em volta do `bindTexture`.
- `client/storm/StormClientState` - sistema dominante recalculado uma vez por tick, para
  névoa, nuvem, som e partícula não pegarem instantes diferentes e tremerem entre si.
- `client/storm/StormFogHandler` - névoa escurece e fecha dentro da tempestade, e o olho
  reabre a visibilidade.
- `client/audio/StormAudio` - ronco contínuo e trovões com volume e frequência tirados do
  mesmo estado que alimenta o resto do VFX.
- `client/particles/RainStreakParticle` - chuva inclinada pelo vento, lida do campo no
  ponto de nascimento; no olho ela sai quase vertical, como deve.
- `client/particles/ParticleIcons` - registra os sprites próprios no `TextureStitchEvent.Pre`.
  Sem isso, toda partícula com `getFXLayer() == 1` desenhava um ícone qualquer do atlas de
  itens: as texturas existiam e nunca apareciam.

### Renderização dos instrumentos (client/render/tesr/)
- `AbstractBlockTESR` - base: matriz, estado de OpenGL, blending, brilho por nível de luz
- `BoxMesh` - tabela de faces/winding do cubo; **sem** dependência do Minecraft (testável)
- `AtlasCells` - (coluna, linha) do atlas → UV, com a inversão de `v`
- `TextureAtlas` - emissão dos quads (immediate mode) com sombreamento por face
- `TESRWindVane`, `TESRWindsock`, `TESRAnemometer` - modelos
- `RenderTypes.DYNAMICWINDS_INSTRUMENT` - tipo de render próprio: o bloco não entra na
  malha do chunk (quem desenha é o TESR) e o `ItemBlock` cai no caminho de icône 2D

### Modelo de Vento (core/)
- `CurlNoise.java` - Campo de vento divergência-zero (curl de potencial multi-octave)
- `PerlinNoise.java` - ruído base
- `WindModel.java` - combina curl + pressão + perfil logarítmico + ciclo diurno + rajadas + tornado
- `WindSample.java` - resultado (vetor, velocidade, rajada)
- `WorldContext.java` - seed, tempo, altura superfície, storm, surfaceClass, baseSpeedMul
- `PressureField.java` - direção horizontal dominante
- `LogProfile.java` - perfil logarítmico u(z) = (u*/κ) * ln(z/z0)
- `DiurnalCycle.java` - fator dia/noite, pico ao meio-dia
- `OrnsteinUhlenbeck.java` - fator de rajada (reutiliza PerlinNoise)
- `SurfaceClass.java` - classes de superfície e z0
- `TornadoField.java` - ciclo de vida completo, distribuição espacial

### Integração (integration/)
- `Wind.java` - facade, consulta clima do mundo (isRaining, isThundering, rainingStrength)
- `WindEngine.java` - tick a cada 5 ticks, cache por dimensão
- `WindConfig.java` - todas as opções de configuração (enableParticles, enablePush, enableAudio, baseSpeedMultiplier, debugHud, tornadoParticleRate, tornadoDebrisRate, tornadoMaxViewDistance)
- `EntityPushHandler.java` - aplica força a entidades (F = 0.5*ρ*v²*coeff), com clipagem e arrasto
- `HeightmapCache.java` - cache 8x8, não limpa a cada tick
- `WindCommand.java` - /wind get|profile|debug|storm

### Client (client/)
- `ClientEvents.java` - registra eventos, TESRs via reflection
- `WindVFX.java` - partículas decorativas ao redor do jogador
- `TornadoVFX.java` - partículas de tornado (campo cacheado por seed)
- `WindAudio.java` - áudio ambiente, rajadas, rustle
- `DebugHud.java` - HUD de depuração (se debugHud=true)
- `particles/` - Leaf, Pollen, Dust, Swirl, Gust, Tornado, Debris, WindParticle
- `render/` - TESRs (WindVane, Windsock, Anemometer)

### Content (content/)
- `ContentRegistry.java` - registra blocos e tile entities
- `blocks/` - BlockWindVane, BlockWindsock, BlockAnemometer
- `tileentities/` - TileEntityWindVane (angle), TileEntityWindsock (inflation)
- `tesr/` - TESRWindVane, TESRWindsock, TESRAnemometer (não implementado)

## Problemas Resolvidos

1. **Java/Gradle/RFG incompatibilidade**: RFG 2.0.2 compilado com JDK 25, precisa de JDK 25 no CI
2. **LogProfile.java javadoc**: `*` isolado quebrando compilação
3. **MixinEffectRenderer**: classe com anotações ausentes, substituída por stub
4. **TornadoVFX import**: import faltante em ClientEvents
5. **PositionedSoundRecord API**: create() não existe em 1.7.10, usar construtor direto
6. **CurlNoise divergência**: adicionava ruído direto em cx, violando propriedade. Corrigido com derivadas de potencial
7. **DiurnalCycle fase**: offset -PI/2 causava pico ao pôr do sol. Corrigido para pico ao meio-dia
8. **OrnsteinUhlenbeck alocação**: criava novo PerlinNoise por chamada. Corrigido com reutilização
9. **EntityPushHandler multiplicação**: multiplicava velocidade de itens por 1.5. Corrigido para força limitada com clipagem
10. **BlockAnemometer cast inseguro**: IBlockAccess -> World sem instanceof. Corrigido
11. **Configuração Forge API**: getDouble com assinatura diferente. Corrigido com Property
12. **WindCommand parseDouble**: CommandBase.parseDouble assinatura diferente. Corrigido com Double.parseDouble
13. **TESR API 1.7.10**: métodos WorldRenderer/Tessellator diferentes das versões modernas. Corrigido com API correta
14. **TileEntitySpecialRenderer generics**: classe não genérica no 1.7.10 compilado. Corrigido removendo generics
15. **isGlobalRenderer inexistente**: removido dos TESRs
16. **renderTileEntityAt assinatura**: 4 parâmetros no 1.7.10 (sem destroyStage). Corrigido
17. **UV texture mapping**: TESRs usando apenas GL11.glColor3f. Adicionado glTexCoord2f para mapeamento de textura

## Texturas e Assets

### Blocos
- `textures/blocks/wind_vane.png` (64x64, cinza metálico com seta)
- `textures/blocks/windsock.png` (64x64, laranja manga)
- `textures/blocks/anemometer.png` (64x64, base + copos)
- `models/block/wind_vane.json`, `windsock.json`, `anemometer.json`

### Partículas
- `textures/particle/leaf.png`, `pollen.png`, `dust.png`, `snowflake.png`, `raindrop.png`, `swirl.png`, `gust.png`, `rainsplash.png`, `tornado.png`, `debris.png`
- Novas das tempestades: `storm_rain.png` (8x16, raja alongada) e `spray.png` (8x8, respingo)

### Ambiente
- `textures/environment/storm_clouds.png` (128x128, teto de nuvem repetível — o gerador
  ganhou value noise periódico e fbm, porque ruído comum não repete e apareceriam emendas
  retas atravessando o céu)
- `textures/environment/storm_wall.png` (64x64, parede do olho / cortina de chuva)
- **As duas são isentas de gradiente vertical, de propósito.** Elas repetem sobre a malha
  (o teto repete ~3x no anel e ~2,4x no raio), então qualquer rampa de luminância ou de
  alpha embutida na imagem vira faixa visível no céu — foi o caso até esta sessão. O
  claro/escuro vem do renderizador, por vértice; a imagem dá só a estrutura. O "levantar"
  da nuvem vem de uma terceira oitava de fbm (`_fbm(cv, x, y, 40.0, size, 2, s=203)`), não
  de um degradê.

### Item
- `textures/items/catastrophe_staff.png` (16x16, haste de obsidiana com enrolado e orbe em
  espiral; a versão em anéis concêntricos lia como alvo de tiro, não como vórtice)

### Sons
- `sounds.json` + `wind_ambience.ogg`, `wind_gust.ogg`, `anemometer_tick.ogg`, `windsock_flap.ogg`, `rain_wind.ogg`, `thunder_rumble.ogg`, `leaf_rustle.ogg`

### Lang
- `lang/en_US.lang`, `lang/pt_BR.lang`

## O que Funciona

- ✅ Compilação CI passa
- ✅ Testes unitários passam (12 testes)
- ✅ Mod inicializa sem erros de mixin (mixins.json vazio)
- ✅ Wind.getWindAt() retorna resultado determinístico
- ✅ Curl noise tem divergência ~0 por construção
- ✅ Diurnal cycle pico ao meio-dia
- ✅ Rajadas com OU reuse
- ✅ Clima integrado (storm de 0..1)
- ✅ Tornados com ciclo de vida e distribuição espacial
- ✅ Configuração conectada ao comportamento
- ✅ Comandos funcionais
- ✅ Partículas spawnadas com UV mapping (GL11 direto)
- ✅ Blocos registrados com receitas
- ✅ Tile entities calculam angle/inflation
- ✅ Wind vane e windsock têm TESRs com textura UV mapping

## O que Precisa de Validação no Jogo

- Renderização visual das texturas (não testável aqui)
- Céu das tempestades: forma, cor, ordem de desenho e clarão do relâmpago. A geometria foi
  verificada por espelho em Python (`tools/preview_storm.py --check`, ver
  `docs/storm_shape.png`), mas OpenGL não existe neste ambiente: o que falta é olhar
  (`stormRenderSky=false` desliga sem remover o resto do VFX)
- Girando do wind vane no jogo
- Inflação visual da manga
- Partículas aparecendo corretamente
- Áudio funcionando
- HUD de depuração
- Anemômetro emitindo sinal redstone

## O que Precisa de Implementação/Revisão

### Resolvido na sessão seguinte (commit `50e0a19`, ver SPEC_TEXTURE_IMPROVEMENT.md)
1. **TESRAnemometer.java** - implementado (poste, rotor de 3 conchas, mostrador, bandeirola) + `TileEntityAnemometer`
2. **Qualidade das texturas** - atlas 4x4 de 128x128 com células de 32px, gerados por `tools/gen_textures.py`
3. **UV mapping refinado** - cada face usa sua célula; `BoxMesh`/`AtlasCells`/`TextureAtlas` + `BoxMeshTest`
   (a versão anterior tinha 4 das 6 faces com winding invertido e era descartada pelo culling)
4. **Registro de TESR** - a reflexão usava `Field.get(null)` em campo de instância e falhava sempre;
   agora usa `ClientRegistry` (API do FML, remapeada no reobf)
5. **Icône de inventário** - blocos usam tipo de render próprio, então precisam de sprite 2D (`*_item.png`)
6. **Renderização da manga** - deixou de ser placeholder; anéis com bandas, inflação e queda da ponta
7. **Modelos JSON para blocos** - mantidos; não são usados no mundo com tipo de render próprio
8. **Mixin de chuva/neve** - segue não implementado (mod inicializa sem, mas não modifica precipitation)

### Documentação
- `README.md` - atualizado com descrição do mod
- `RELATORIO_ENTREGA.md` - relatório completo das correções
- `SPEC_TEXTURE_IMPROVEMENT.md` - spec para melhorar texturas (criado nesta sessão)

## Arquivos Relevantes para Editar

### Texturas (fonte da verdade)
- `tools/gen_textures.py` - gera todas as texturas; `textures/blocks/*.png` e
  `textures/particle/*.png` são artefatos deste script
- `tools/preview_textures.py` - gera `docs/textures_preview.png`

### TESRs
- `src/main/java/com/dynamicwinds/client/render/tesr/TESRWindVane.java`
- `src/main/java/com/dynamicwinds/client/render/tesr/TESRWindsock.java`
- `src/main/java/com/dynamicwinds/client/render/tesr/TESRAnemometer.java`
- `src/main/java/com/dynamicwinds/client/render/tesr/BoxMesh.java` (winding/UV)
- `src/test/java/com/dynamicwinds/client/render/BoxMeshTest.java` (travas no CI)

### Modelos/Assets
- `src/main/resources/assets/dynamicwinds/models/block/*.json`
- `src/main/resources/assets/dynamicwinds/textures/blocks/*.png` (gerados)
- `src/main/resources/assets/dynamicwinds/textures/particle/*.png` (gerados)

### Blocos
- `src/main/java/com/dynamicwinds/content/RenderTypes.java` (tipo de render próprio)
- `src/main/java/com/dynamicwinds/content/blocks/*.java` (ícones, hitbox, som, aba)

### Configuração Build
- `.github/workflows/build.yml`
- `build.gradle.kts`
- `gradle.properties`
- `gradle/wrapper/`

## Notas Técnicas

### API de Renderização 1.7.10
- Tessellator: `Tessellator.instance`, `startDrawingQuads()` (= `startDrawing(7)`), `draw()`,
  `addVertexWithUV(x, y, z, u, v)`, `setNormal(x, y, z)`, `setColorRGBA_F(r, g, b, a)`
- WorldRenderer: `begin(glMode, vertexFormat)`, `pos(double,double,double)`, `color(float,float,float,float)`, `endVertex()`
- VertexFormat: pacote `net.minecraft.client.renderer.vertex` existe no MCP mas NÃO disponível no build (erro de compilação)
- Portanto, usar GL11 direto com `glBegin(GL_QUADS)`, `glTexCoord2f()`, `glVertex3f()`, `glEnd()`, `glColor3f()`

#### Duas armadilhas do Tessellator (conferidas no fonte, não por tentativa)
1. `startDrawing` **lança** `IllegalStateException("Already tesselating!")` se já houver lote
   aberto, e `draw()` **lança** `"Not tesselating!"` se não houver. Logo: **nenhum método pode
   retornar entre `startDrawingQuads()` e `draw()`** — toda condição de degeneração (raio
   nulo, altura nula, alpha zero) tem de ser checada **antes** de abrir o lote. Foi
   exatamente esse o defeito corrigido no `StormRenderer`.
2. `setNormal` é estado **persistente** do lote (vale até o `draw()`), então normal e
   formato têm de ser definidos antes do primeiro vértice. Cor (`setColorRGBA_F`) é por
   vértice e deve ser chamada antes de cada um — isso é uso correto, não erro.
3. `bindTexture` liga na unidade de textura **ativa**, e durante o render do mundo a unidade
   ativa é a do lightmap (`GL_TEXTURE1`). Sem `GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)` +
   `OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit)` (e devolver no fim), a nuvem
   sai texturizada com o mapa de luz.

### Mixins
- `dynamicwinds.mixins.json` vazio (sem mixins ativos)
- Classes de mixin (MixinEntityRainFX, MixinEntitySnowShovelFX, MixinRenderGlobal, MixinEffectRenderer) são helpers estáticos, não mixins reais
- Sem dependência SpongePowered Mixin no build.gradle.kts

### TileEntitySpecialRenderer 1.7.10
- Assinatura: `public abstract void renderTileEntityAt(T te, double x, double y, double z, float partialTicks, int destroyStage)`
- Mas no build compilado: `renderTileEntityAt(TileEntity, double, double, double, float)` (4 params)
- No build: classe não é genérica (`TileEntitySpecialRenderer`, não `TileEntitySpecialRenderer<T>`)
- `isGlobalRenderer()` não existe no 1.7.10

### Reflection para registro de TESR
```java
Field field = TileEntityRendererDispatcher.class.getDeclaredField("mapSpecialRenderers");
field.setAccessible(true);
Map<Class<? extends TileEntity>, TileEntitySpecialRenderer> map = (Map) field.get(null);
map.put(TileEntityWindVane.class, new TESRWindVane());
```

## Áudio (sessão de 2026-10-15)

### APIs de áudio do 1.7.10 (conferidas no código-fonte, não por tentativa)
Fonte usada: `xgraza/nebula-1.7.2` (SRG) + mapeamentos `1.7.10 stable` do
Minecraft-Deobfuscator3000 (nomes MCP). Cuidado: `leijurv/MineBot/mcp918` é **1.8.9**, não 1.7.10 —
tem `BlockPos`/`ITickableSound`/`SoundCategory` que enganam.

- `ISound`: `getPositionedSoundLocation()` (é esse o nome no 1.7.10; `getSoundLocation` só existe do 1.8
  em diante), `canRepeat()`, `getRepeatDelay()`, `getVolume()`, `getPitch()`, `getXPosF()/getYPosF()/getZPosF()`,
  `getAttenuationType()` → `ISound.AttenuationType.NONE|LINEAR`.
- `ITickableSound extends ISound, net.minecraft.server.gui.IUpdatePlayerListBox` (o tick do 1.7.10 é
  `update()`, via `IUpdatePlayerListBox`; `net.minecraft.util.ITickable` é 1.8+). Tem `isDonePlaying()`.
- `SoundManager.updateAllSounds()` chama `update()` de cada som tickável e, em seguida, **reaplica volume,
  altura e posição** do canal — é isso que permite mixar um loop em tempo real.
- Loop real: `playSound` cria a fonte com `canRepeat() && getRepeatDelay() == 0` como flag de repetição
  do OpenAL. Fonte em fluxo (`stream: true`) relê o arquivo a cada volta → emenda audível. Por isso os
  leitos do mod são bufferizados.
- `volume == 0` faz o som ser **descartado** (`"Skipped playing sound... volume was zero"`): um leito tem
  de nascer com ganho mínimo (0,02) e subir.
- Volume > 1 **estica o raio audível** do canal (`16 * volume` blocos, modelo linear): é assim que o
  trovão distante é posicionado no ponto da descarga e ainda assim é ouvido.
- `SoundHandler`: `playSound(ISound)`, `stopSound(ISound)`, `isSoundPlaying(ISound)`.
- `sounds.json`: `category` precisa ser um dos nomes do `SoundCategory` (master, music, record, weather,
  block, hostile, neutral, player, ambient) — um valor inválido derruba o arquivo **inteiro** (a exceção é
  capturada em `SoundHandler` e o `sounds.json` todo é descartado).
- `EntityLightningBolt.onUpdate()` já toca `ambient.weather.thunder` com volume 10000. Ou seja: o relâmpago
  do mod já tem som próprio do jogo — o áudio do mod é a camada do resto do sistema, não uma duplicata.

### Arquitetura de áudio do mod
- `core` não tem áudio; tudo vive em `client/audio`:
  - `AmbientBed` — leito contínuo (loop do OpenAL, ganho suavizado, abrigo, posição do jogador).
  - `AudioBeds` — pedido por tick (`beginTick`/`request`/`tick`), fade de 2,5 s, canal recriado quando o
    gerenciador recusa, e o modelo de **abrigo** (profundidade sob o teto → `1 - 0,06·prof`, piso 0,12).
  - `WindAudio` — 3 faixas cruzadas por pesos triangulares (brisa/forte/tempestade), rajadas por `gust`,
    folhagem por varredura de `Material.leaves`, e altura (pitch) que **cai** com o déficit central.
  - `StormAudio` — chuva em 3 regimes (`rain_light`/`rain_heavy`/`rain_wind`) com o olho seco, trovão
    agendado a 343 m/s com timbre por distância, detritos acima de 20 m/s, e o clarão.
  - `InstrumentAudio` — clique por volta do anemômetro e estalo da manga (os dois sons estavam órfãos).
- `StormClientState`: `rainStrength()` (banda^0,8 · intensidade · secagem do olho, mais a chuva do jogo em
  peso 0,45) e `flash(power)`/`flashStrength()`; a névoa acende no clarão (`StormFogHandler`).

### Gerador de sons (`tools/gen_sounds.py`)
- `pip install --target /tmp/aud numpy soundfile` (PyPI é alcançável nesta sandbox; libsndfile 1.2.2 escreve
  Vorbis). Rodar `PYTHONPATH=/tmp/aud python3 tools/gen_sounds.py` reproduz os bytes.
- Método: primeiro a forma (envelope/eventos), depois a **cor** — e a cor é conferida por medida, não por
  ouvido. Métrica usada: fração de energia por faixa (<100 Hz, 100–400, 400–1,5k, 1,5k–5k, >5k) + rolloff 85%.
- Dois erros reais que só apareceram na medida:
  1. ruído branco moldado: as faixas agudas dominavam só por largura de banda → faltava inclinação 1/f na fonte;
  2. **contínuo (DC)**: ruído moldado carrega offset constante, que concentra energia nos primeiros bins —
     o trovão "próximo" ficava com 80% da energia abaixo de 100 Hz e quase nada de estalo, apesar do estalo
     existir no sinal. `dc_block()` (highpass em 20 Hz) resolveu; as bandas finais batem com o alvo.
- `loopify()` faz crossfade **e** casa o nível das pontas (sem isso, material com eventos esparsos, como
  folhagem, dá salto de nível no loop).
- 14 arquivos, 44,1 kHz mono Vorbis (blocos 256/2048, EOS presente, 3 cabeçalhos na ordem), ~1,0 MB no total.
- Não há `silence.ogg`: sem como parar canal com fade no 1.7.10, o arquivo era peso morto.

### Pendências de áudio
- `rain_wind` mudou de papel: agora é a terceira faixa da chuva (a cortina da parede do olho), não mais o
  "ronco" do sistema. O rugido sai da faixa forte do vento, com a altura amarrada ao déficit central.
- O clique do anemômetro tem intervalo mínimo de 5 ticks: acima de ~4 voltas por segundo os cliques reais
  se fundem num zumbido, e um motor de som não faz essa fusão.

## Links Úteis

- CI: https://github.com/thiagovailate-ship-it/Meus--mods-Java/actions
- Run mais recente (áudio): https://github.com/thiagovailate-ship-it/Meus--mods-Java/actions/runs/38842089801
- Repo: https://github.com/thiagovailate-ship-it/Meus--mods-Java
