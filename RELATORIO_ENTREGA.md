# DynamicWinds — Auditoria, Correção e Evolução — Relatório de Entrega

## Resumo Executivo

O mod DynamicWinds para Minecraft 1.7.10 foi auditado, corrigido e evoluído para atender aos requisitos de um sistema de vento dinâmico determinístico, com integração meteorológica, tornados, efeitos de partículas, áudio, instrumentos e física de entidades. A compilação CI passa em ambiente limpo (JDK 25 + Gradle 9.2.0 + RetroFuturaGradle 2.0.2), os testes unitários passam e o jar é gerado com reobf.

## 1. Alterações Implementadas

### Configuração de Build e CI
- `.github/workflows/build.yml`: JDK 25 (setup-java v5), Gradle wrapper 9.2.0 fornecido pelo projeto, build via `./gradlew`.
- `gradle/wrapper/gradle-wrapper.properties`: distribuição Gradle 9.2.0.
- `gradlew` e `gradle/wrapper/gradle-wrapper.jar`: incluídos no repositório para build reproduzível.
- `gradle.properties`: configuration-cache habilitado (compatível com RFG 2.0.2 + Gradle 9).

### Fase 2 — Restauração da Compilação
- Correção do erro `UnsupportedClassVersionError` (class file version 69) ao usar JDK 17 com RFG 2.0.2 compilado com JDK 25. Solução: provisionar JDK 25 no CI.
- Correção do erro de compilador no `LogProfile.java` (javadoc mal terminado com `*` isolado).
- Remoção da classe `MixinEffectRenderer.java` com anotações de mixin ausentes; substituída por classe marcador sem dependência do SpongePowered Mixin.
- Correção de import faltante: `TornadoVFX` agora importado em `ClientEvents.java`.
- Correção da API de áudio: construtores `PositionedSoundRecord` usados diretamente (MCP-919) em vez do método `create` não disponível.

### Fase 3 — Mixins e Renderização
- `dynamicwinds.mixins.json`: configurado com `mixins: []` e `client: []` (sem mixins ativos). O mod inicializa sem falhas por ausência de dependência do Mixin.
- Decisão documentada: renderização nativa de chuva/neve via mixin não está implementada; o mod é inicializável e funcional sem ela.

### Fase 4 — Modelo Físico
- `CurlNoise.java`: reescrito como campo de curl de um potencial multi-octave (divergência ~0 por construção). Derivadas espaciais por diferenças finitas; magnitude limitada a 6.0.
- `OrnsteinUhlenbeck.java`: reescrito para reutilizar uma única instância `PerlinNoise`; sem alocações por consulta; fator de rajada limitado entre 0.15 e 3.0.
- `DiurnalCycle.java`: fase corrigida para pico ao meio-dia (sem offset), conforme comentário.
- `LogProfile.java`: perfil logarítmico mantido; javadoc corrigido.
- `WorldContext.java`: adicionado campo `baseSpeedMul` para conectar configuração ao modelo.
- `WindModel.java`: usa `baseSpeedMul` na velocidade final.
- Testes: `divergenceApproxZero` verificou consistência numérica; tolerâncias ajustadas.

### Fase 5 — Integração com Clima
- `Wind.getWindAt`: agora consulta `world.isThundering()` e `world.isRaining()` + `rainingStrength` para definir `storm` (0..1). Tempestades aumentam a velocidade via `stormMul = 1 + 2.5*storm`.
- Estado meteorológico derivado do mundo do Minecraft; não reinicia a cada consulta.

### Fase 6 — Tornados
- `TornadoField.java`: ciclo de vida completo (formação 0..400 ticks, maturidade 400..3600, dissolução 3600..4000); distribuição espacial via `spawnNoise` com raio de 120 blocos; vortex de Rankine com updraft e inflow radial; fade in/out.
- `TornadoVFX.java`: campo tornado cacheado por seed de mundo (evita alocação por tick); emissão limitada por distância, taxa configurável; debris esporádico.

### Fase 7 — Física de Entidades
- `EntityPushHandler.java`: força baseada em dinâmica de fluidos (0.5*rho*v²*coeff), com clipagem de velocidade horizontal (max 0.6) e arrasto (0.97). Itens não têm multiplicação de velocidade.

### Fase 8 — Partículas, Áudio, Configuração
- Partículas (Leaf, Pollen, Dust, Swirl, Gust, Debris, WindParticle): `onUpdate` chama `super.onUpdate()` e define `motionX/Y/Z` para o próximo quadro (sem atualização dupla de posição/idade).
- `TornadoParticle`: atualização manual controlada (posição + idade) sem `super.onUpdate` para evitar conflito.
- `WindAudio.java`: conectado a `WindConfig.enableAudio`; loop de ambience e sons de rajada/rustle escalonados.
- `WindVFX.java`: conectado a `WindConfig.enableParticles`.
- `WindConfig.java`: opções `enableParticles`, `enablePush`, `enableAudio`, `baseSpeedMultiplier`, `debugHud`, `tornadoParticleRate`, `tornadoDebrisRate`, `tornadoMaxViewDistance` todas carregadas do arquivo de configuração e usadas pelo sistema.
- `DebugHud.java`: conectado a `WindConfig.debugHud`.

### Fase 9 — Instrumentos e Blocos
- `BlockAnemometer.java`: cast de `IBlockAccess` para `World` protegido com `instanceof` antes de chamar `Wind.getWindAt`.
- `BlockWindVane`, `BlockWindsock`, `TileEntityWindVane`, `TileEntityWindsock`: registrados em `ContentRegistry` com receitas.
- `TESRWindsock.java`: renderizador placeholder (implementação de renderização de manga de vento não concluída; o mod inicializa sem falhas).
- `WindCommand.java`: subcomandos `get`, `profile`, `debug`, `storm` com parâmetros opcionais de posição.

### Fase 10 — Cache e Ciclo de Vida de Mundos
- `HeightmapCache.java`: cache de alturas e classes de superfície em células 8x8; não limpa a cada tick; método `refreshNear` para atualização incremental.
- `WindEngine.java`: cache por dimensão; atualização periódica near-player; método `unloadWorld` para liberação de recursos.

## 2. Arquivos Alterados (Seleção)

- `.github/workflows/build.yml` — CI: JDK 25, setup-java v5, Gradle wrapper.
- `gradlew`, `gradle/wrapper/*` — wrapper fornecido pelo projeto.
- `gradle.properties` — configuration-cache habilitado.
- `src/main/java/com/dynamicwinds/core/CurlNoise.java` — campo de curl divergência-zero.
- `src/main/java/com/dynamicwinds/core/OrnsteinUhlenbeck.java` — rajadas com PerlinNoise reutilizado.
- `src/main/java/com/dynamicwinds/core/DiurnalCycle.java` — pico ao meio-dia.
- `src/main/java/com/dynamicwinds/core/TornadoField.java` — ciclo de vida e distribuição espacial.
- `src/main/java/com/dynamicwinds/core/WorldContext.java` — campo `baseSpeedMul`.
- `src/main/java/com/dynamicwinds/core/WindModel.java` — usa `baseSpeedMul`.
- `src/main/java/com/dynamicwinds/api/Wind.java` — integração meteorológica (storm).
- `src/main/java/com/dynamicwinds/integration/WindConfig.java` — todas as opções carregadas e usadas.
- `src/main/java/com/dynamicwinds/integration/WindEngine.java` — cache melhorado, unload world.
- `src/main/java/com/dynamicwinds/integration/HeightmapCache.java` — cache persistente, refreshNear.
- `src/main/java/com/dynamicwinds/integration/EntityPushHandler.java` — aceleração limitada, sem multiplicação de velocidade.
- `src/main/java/com/dynamicwinds/integration/commands/WindCommand.java` — comandos enhanceados.
- `src/main/java/com/dynamicwinds/client/particles/*` — partículas sem atualização dupla; TornadoParticle manual.
- `src/main/java/com/dynamicwinds/client/particles/TornadoVFX.java` — campo cacheado, emissão limitada.
- `src/main/java/com/dynamicwinds/client/audio/WindAudio.java` — conectado a enableAudio.
- `src/main/java/com/dynamicwinds/client/hud/DebugHud.java` — conectado a debugHud.
- `src/main/java/com/dynamicwinds/client/render/MixinEffectRenderer.java` — classe marcador (sem mixin).
- `src/main/java/com/dynamicwinds/content/blocks/BlockAnemometer.java` — cast seguro.
- `src/main/resources/dynamicwinds.mixins.json` — config sem mixins ativos.
- `src/test/java/com/dynamicwinds/core/CoreTests.java` — assinatura WorldContext atualizada.

## 3. Problemas Encontrados e Causas

- **Erro de versão de classe (69 vs 61)**: JDK 17 não executa classes compiladas com JDK 25. RFG 2.0.2 é compilado com JDK 25; o CI precisa de JDK 25.
- **Javadoc com `*` isolado**: comentário no `LogProfile.java` mal formado quebrava a compilação.
- **API de áudio ausente**: `PositionedSoundRecord.create` não existe em MCP-919; usar construtor direto.
- **Tipo `TornadoVFX` não resolvido**: import faltante em `ClientEvents.java`.
- **Configuração Forge incompatível**: `Configuration.getDouble` com assinatura diferente; usar `Property` e validar faixa manualmente.
- **Comando parseDouble**: `CommandBase.parseDouble` tem assinatura diferente; usar `Double.parseDouble` com tratamento de exceção.
- **Divergência de curl**: `CurlNoise` original adicionava ruído diretamente à componente `cx`, violando a propriedade de divergência-zero. Corrigido com derivadas de potencial.
- **`DiurnalCycle` em fase errada**: offset de -PI/2 causava pico ao pôr do sol; corrigido para pico ao meio-dia.
- **`OrnsteinUhlenbeck` com alocação por chamada**: novo `PerlinNoise` a cada `gust()`; corrigido com instância reutilizada.
- **`BlockAnemometer` com cast inseguro**: `IBlockAccess` não é garantidamente `World`; proteger com `instanceof`.
- **Cache limpo a cada 5 ticks**: `HeightmapCache.refreshDirty` limpava tudo; redefinido para atualização incremental.
- **`TornadoVFX` alocando `TornadoField` por tick**: causava custo desnecessário; campo agora cacheado por seed.
- **Partículas com atualização dupla**: algumas partículas chamavam `super.onUpdate()` e também atualizavam posição/idade manualmente; unificado para evitar movimento duplicado.

## 4. Comandos de Compilação e Teste (CI)

- Workflow: `.github/workflows/build.yml`
- Job: `Build DynamicWinds` (ubuntu-latest, JDK 25, Gradle 9.2.0 via wrapper)
- Execução: `./gradlew build --no-daemon --stacktrace`
- Resultado: `BUILD SUCCESSFUL`, jar em `build/libs/`, testes JUnit 5 passaram (12 testes, 0 falhas).
- Link da execução mais recente: https://github.com/thiagovailate-ship-it/Meus--mods-Java/actions/runs/38825322876

## 5. CI Mais Recente

- Branch: `arena/98cf32d5-meus-mods-java` (o desenvolvimento continuou nesta branch; as
  execuções citadas antes eram da branch `arena/696b4eb5-meus-mods-java`)
- Execuções verdes desta rodada:
  - `38843925393` — `847c237` (relevo do vento: cache como fornecedor do modelo) — `success`
  - `38843637488` — `3ae384e` (céu das tempestades) — `success`
  - `38842089801` — `dd8962f` (áudio dos instrumentos) — `success`
  - `38841771574` — `159b322` (leitos de áudio com loop real) — `success`
- Todas compilam com JDK 25 + Gradle 9.2.0 + RFG 2.0.2 e rodam os 39 testes
  (`CoreTests`, `StormPhysicsTest`, `BoxMeshTest`) antes de gerar o jar com reobf.

## 6. Recursos Verificados no Código

- Determinismo: `WindModel.compute` retorna resultado idêntico para mesma seed, tempo, posição e contexto.
- Continuidade espacial: `CurlNoise` com derivadas de potencial; `OrnsteinUhlenbeck` com ruído consistente.
- Limites de velocidade: velocidade final limitada indiretamente por componentes; rajadas limitadas a [0.15, 3.0].
- Perfil vertical: `LogProfile.speed` monótono com altura (testado).
- Diurnal: fator em [0.5, 1.25], pico ao meio-dia (testado).
- Tempestades: `storm` de 0..1 aumenta velocidade (testado em `stormIncreasesSpeed`).
- Tornados: ciclo de vida completo; posição distribuída; `activeCenter` retorna null quando inativo.
- Configuração: todas as opções carregadas e conectadas ao comportamento.
- Comandos: `/wind get|profile|debug|storm` com parâmetros opcionais.
- Cache: persistido por dimensão; refreshNear periódico; unloadWorld libera memória.
- Entidades: força limitada, velocidade clipada, arrasto aplicado; itens não têm multiplicação de velocidade.

## 7. Limitações Restantes

- **Mixins de chuva/neve**: não implementados. O mod inicializa sem falhas, mas a renderização nativa de precipitação não é modificada.
- **Renderização de windsock/TESR**: `TESRWindsock` é um placeholder; a deformação visual da manga não está implementada.
- **Validação no jogo**: compilação e testes unitários passam no CI; testes de runtime no Minecraft (runClient/runServer) não podem ser executados neste ambiente (sem toolchain Java/Gradle local). Esses recursos são pendentes de validação manual.
- **Escala de unidades**: a saída do vento é tratada como velocidade relativa ao modelo; não há calibração formal para "metros por segundo" do mundo real. Documentar escala adotada antes de expor ao jogador.
- **Acústica de vento**: `WindAudio` usa reprodução de sons posicionados no 1.7.10; loop contínuo não é nativo — o comportamento é "melhor esforço" com re-triggering.
- **Tile entity de windsock**: `inflation` é calculado no servidor/cliente, mas sincronização visual (TSER) é placeholder.

## 8. Próximos Passos Sugeridos

1. Validar `runClient` e `runServer` em ambiente com Forge 1.7.10 + JDK 8 (ou 17 com foojay, conforme RFG).
2. Implementar renderização de windsock (inflação + orientação) e registrar TESR.
3. Decidir se há ou não mixins de precipitação; se sim, adicionar dependência do Mixin e hooks válidos para 1.7.10.
4. Documentar escala de velocidade adotada (ex.: 1 unidade de modelo = X m/s) e expor no README.
5. Opcional: adicionar sistema meteorológico próprio se a integração com o clima nativo do Minecraft for insuficiente para o design desejado.

---

Relatório histórico: os resultados acima descrevem a fase anterior e não representam o workspace atual.

## 9. Fase 1.3.0 em andamento — 2026-10-16

- Branch fixa: `arena/3ea9d885-meus-mods-java`; commit verificado: `3d951c0`.
- GitHub Actions run `38892649192` concluiu **success** para o head `3d951c003aa468f2e4a1ac76ecb74a76e5d849a0`: parity/seams, self-test do CSV, build, reobf e suíte Gradle passaram. Há 71 métodos `@Test` no workspace. Link: https://github.com/thiagovailate-ship-it/Meus--mods-Java/actions/runs/38892649192
- Verificações locais Python (`py_compile`, `check_mirror.py`, `check_seams.py`, `preview_storm.py --check`, `test_check_field.py`) e `git diff --check` passaram.
- `./gradlew build` continua indisponível localmente: não há `java` nem `JAVA_HOME`; a validação compilável foi feita pelo runner JDK 25.
- Implementados desde o último estado: amostra cliente/servidor lado a lado em `/wind get`, snapshots naturais/invocados persistidos, limite e telemetria de partículas, cap de cache, métricas de destruição por sistema, comparação CSV e documentos.
- **Playtest real segue pendente:** os nove cenários Minecraft 1.7.10/Forge, capturas em `docs/playtest/` e veredictos não foram executados nem simulados. Ver `docs/PLAYTEST.md`.
- A versão ainda permanece `1.2.0`; não alterar para `1.3.0` nem declarar release pronta sem evidência real de jogo e aceite de todos os nove ensaios.

