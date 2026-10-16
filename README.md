# DynamicWinds

Mod de vento dinâmico para Minecraft Java Edition 1.7.10 (Forge).

## O que o mod implementa

- **Campo de vento determinístico**: `Wind.getWindAt(world, x, y, z)` retorna `WindSample` (vetor, velocidade, rajada) calculado a partir da seed do mundo, tempo e posição. Cliente e servidor calculam o mesmo resultado — sem pacotes de sincronização.
- **Modelo físico**: curl de ruído (divergência ~0), perfil logarítmico vertical, direção de pressão rotativa, ciclo diurno (pico ao meio-dia), fator de rajada (Ornstein-Uhlenbeck com ruído reutilizado), contribuição de tornado.
- **Clima**: intensidade de tempestade derivada do estado do mundo (`isRaining`, `isThundering`, `rainingStrength`). Tempestades aumentam a velocidade do vento.
- **Tornados**: ciclo de vida completo (formação, maturidade, dissolução), distribuição espacial pela seed, vortex de Rankine com updraft e inflow radial. `TornadoVFX` emite partículas com emissão limitada por distância e taxa configurável.
- **Partículas visuais**: folhas, pólen, poeira, redemoinhos, rajadas e detritos, espawados ao redor do jogador conforme velocidade local.
- **Áudio**: som ambiente de vento, sons de rajada e rustle de folhas, com controle por `enableAudio`.
- **Entidades e itens**: força de vento aplicada com dinâmica de fluidos (0.5 * rho * v² * coeff), clipagem de velocidade e arrasto. Itens não têm multiplicação de velocidade.
- **Instrumentos**: wind vane (orientação), windsock (inflação), anemometer (saída redstone 0..15 e rotor visual). Os três blocos são desenhados por TESR, com atlas próprio por face.
- **Configuração**: `WindConfig` com `enableParticles`, `enablePush`, `enableAudio`, `baseSpeedMultiplier`, `debugHud`, `tornadoParticleRate`, `tornadoDebrisRate`, `tornadoMaxViewDistance`. Todas carregadas do arquivo de configuração e conectadas ao comportamento.
- **Comandos**: `/wind get|profile|debug|storm [x] [y] [z]`.
- **HUD de depuração**: mostra vetor, velocidade e rajada quando `debugHud` está ativo.

## Build e CI

- JDK 25 + Gradle 9.2.0 (wrapper fornecido) + RetroFuturaGradle 2.0.2.
- CI: GitHub Actions `.github/workflows/build.yml` (ubuntu-latest, JDK 25, `./gradlew build --no-daemon --stacktrace`).
- Resultado: `BUILD SUCCESSFUL`, jar em `build/libs/`, testes JUnit 5 passam.

## Requisitos

- Minecraft Java Edition 1.7.10 com Forge.
- Para compilar localmente: JDK 25 (para RFG 2.0.2), Gradle 9.2.0 (ou usar o wrapper incluído).

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

## Limitações conhecidas

- Mixins de renderização de chuva/neve não implementados (o mod inicializa sem falhas).
- Validação no jogo (runClient/runServer) não foi executada neste ambiente (sem toolchain local); testes de runtime são pendentes de validação manual.
- Escala de velocidade do vento é relativa ao modelo; não há calibração formal para m/s do mundo real.
- Os modelos JSON dos blocos existem, mas não são usados no mundo: com o tipo de render
  próprio quem desenha o bloco é o TESR.

## Repositório

- Branch de desenvolvimento: `arena/696b4eb5-meus-mods-java`
- CI: https://github.com/thiagovailate-ship-it/Meus--mods-Java/actions
