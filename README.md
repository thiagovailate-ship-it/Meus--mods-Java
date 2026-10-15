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
- **Instrumentos**: wind vane (orientação), windsock (inflação), anemometer (saída redstone 0..15, cast seguro de IBlockAccess).
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

## Limitações conhecidas

- Mixins de renderização de chuva/neve não implementados (o mod inicializa sem falhas).
- Renderização de windsock (TESR) é placeholder; deformação visual não concluída.
- Validação no jogo (runClient/runServer) não foi executada neste ambiente (sem toolchain local); testes de runtime são pendentes de validação manual.
- Escala de velocidade do vento é relativa ao modelo; não há calibração formal para m/s do mundo real.

## Repositório

- Branch de desenvolvimento: `arena/696b4eb5-meus-mods-java`
- CI: https://github.com/thiagovailate-ship-it/Meus--mods-Java/actions
