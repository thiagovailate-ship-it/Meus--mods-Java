# Contexto da Sessão - DynamicWinds Mod

## Visão Geral

DynamicWinds é um mod para Minecraft Java Edition 1.7.10 (Forge) que implementa um sistema de vento dinâmico determinístico. O mod calcula vetores de vento baseados em seed, tempo e posição, sem necessidade de sincronização cliente-servidor.

## Branch e Commit Atual

- **Branch**: `arena/696b4eb5-meus-mods-java`
- **Commit mais recente**: `7f51f63` - feat: add UV texture mapping to TESRs
- **Repo**: https://github.com/thiagovailate-ship-it/Meus--mods-Java

## Build e CI

- **CI**: GitHub Actions `.github/workflows/build.yml`
- **JDK**: 25 (necessário para RFG 2.0.2)
- **Gradle**: 9.2.0 (via wrapper incluído no repo)
- **Build**: `./gradlew build --no-daemon --stacktrace`
- **Status CI**: ✅ BUILD SUCCESSFUL (run 38830747771)
- **Link CI**: https://github.com/thiagovailate-ship-it/Meus--mods-Java/actions/runs/38830747771

## Arquitetura do Mod

### Módulos
- `core/` - Física pura (sem dependência Minecraft)
- `api/` - Facade Wind.getWindAt()
- `integration/` - Engine, config, comandos, push de entidades
- `client/` - Partículas, áudio, HUD, renderização, GUI
- `content/` - Blocos, tile entities, receitas
- `tools/` - Geradores reproduzíveis (texturas e prancha de prévia), Python puro

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
- Tessellator: `Tessellator.instance`, `getWorldRenderer()`, `draw()`
- WorldRenderer: `begin(glMode, vertexFormat)`, `pos(double,double,double)`, `color(float,float,float,float)`, `endVertex()`
- VertexFormat: pacote `net.minecraft.client.renderer.vertex` existe no MCP mas NÃO disponível no build (erro de compilação)
- Portanto, usar GL11 direto com `glBegin(GL_QUADS)`, `glTexCoord2f()`, `glVertex3f()`, `glEnd()`, `glColor3f()`

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

## Links Úteis

- CI: https://github.com/thiagovailate-ship-it/Meus--mods-Java/actions
- Run mais recente: https://github.com/thiagovailate-ship-it/Meus--mods-Java/actions/runs/38830747771
- Repo: https://github.com/thiagovailate-ship-it/Meus--mods-Java
