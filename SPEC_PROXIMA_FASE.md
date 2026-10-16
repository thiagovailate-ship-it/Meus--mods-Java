# Spec da próxima fase — consistência, persistência e polimento de release

Alvo: **1.3.0** · branch `arena/98cf32d5-meus-mods-java` · ponto de partida: `d7a2db8`
(CI verde, 39 testes, texturas e áudio congelados por número)

---

## 0. Resumo

A fase anterior entregou o que foi pedido — quatro sistemas de tempestade com física
travada por testes, VFX completo (partículas, céu, som, relâmpago), destruição pesada,
cajado e comandos — e terminou com o que dá para provar sem abrir o jogo: geometria do céu
espelhada em Python, emendas de textura medidas, espectro de áudio medido por banda.

Esta fase não é "mais features". É a fase em que o mod passa a **ser digno de release**:

1. **Consistência** — o cliente hoje calcula outro campo que o servidor (semente 0 contra a
   semente real). O HUD, as partículas, os instrumentos e o áudio do cliente saem de um
   mundo que não é o do servidor, enquanto empurrão, destruição e clima usam o outro.
2. **Persistência** — tempestades não sobrevivem a um restart. As naturais são recriadas
   (e mudam se a config mudar); as do cajado simplesmente desaparecem.
3. **Validação em jogo** — nada deste mod foi jamais executado. Há nove itens na lista de
   "precisa validar no jogo" e todos os que dependem de olhar continuam abertos.
4. **Polimento de release** — aviso ao jogador, barômetro, config documentada e validada,
   orçamento de desempenho medido, manual e changelog.

O critério de sucesso da fase é simples de dizer e difícil de fingir: **com o mesmo save,
o servidor e o cliente concordam; depois de reiniciar, a tempestade continua onde estava;
e existe um roteiro de playtest com critério de aceite por item.**

---

## 1. Ponto de partida verificado

### 1.1 O que está pronto e verde

| item | estado |
| --- | --- |
| Física | `core/{StormKind,VortexBalance,StormSystem,StormField,WindModel,...}` — Java puro, sem Minecraft |
| Integração | `StormWeather`, `DestructionHandler`, `EntityPushHandler`, `StormNetwork`, `HeightmapCache` como fornecedor de relevo |
| Jogador | `ItemCatastropheStaff`, `/wind get|profile|debug|storm`, `DebugHud`, config GUI genérica |
| VFX | `StormRenderer` (6 lotes, LOD 72/48/24), `StormFogHandler`, 8 partículas com `ParticleIcons`, `RainStreakParticle` |
| Áudio | 5 classes, 14 sons (1.017.308 bytes), leitos com loop real, trovão atrasado por `d/343` |
| Texturas | 97.772 bytes, geradas por script determinístico, emenda ≤ 0,74 |
| Testes | 39 (`CoreTests` 12, `StormPhysicsTest` 22, `BoxMeshTest` 5) |

### 1.2 Baseline medido (o que uma regressão quebra)

- Emenda das texturas de ambiente: nuvens ×0,27/×0,74 · parede ×0,39/×0,17 (limite 1,6).
- Geometria do céu: 252 combinações tipo × intensidade × solo sem nenhum problema; a única
  guarda que dispara é a do funil, em solo acima de y≈200.
- Áudio: `wind_ambience` 8/4/20/40/28 (%) por faixa, `rain_heavy` 0,8/4,4/20,7/42/29,1;
  costura de loop ≤ 0,4 dB; DC ≈ 0.
- Física: `RHO=1.15`, `OMEGA=7.2921e-5`, latitude 18° → `f=4.507e-5`; continuidade em `Rmax`
  Δ≈2e-7; pico exatamente `Vmax`; olho em 0,90 do pico.

### 1.3 Defeitos e lacunas encontrados na revisão (com a evidência)

| # | defeito | evidência | gravidade |
| --- | --- | --- | --- |
| D-A | **A semente do cliente é 0.** `NetHandlerPlayClient` cria o mundo do cliente com `new WorldSettings(0L, ...)` (1.7.2, linha 161; idêntico no 1.7.10) e `World.getSeed()` é `worldInfo.getSeed()`. `Wind.modelFor` chaveia por semente: o cliente monta o modelo com 0, o servidor com a semente real. | fonte do 1.7.2 conferido; `Wind.modelFor(World)` no repo usa `world.getSeed()` | **alta** — quebra a afirmação do README e faz HUD/partícula/instrumento/áudio do cliente derivarem de outro campo |
| D-B | **Tempestades existem em toda dimensão.** `Wind.MODELS` é chaveado só por semente, e todas as dimensões de um mundo compartilham `worldInfo` — logo o Nether herda as tempestades do Overworld (com o teto de rocha no lugar do céu e destruição de netherrack). | `Wind.modelFor` (`getSeed()`) + `WindEngine.tick` por mundo | média |
| D-C | **Nada é persistido.** Não há `WorldSavedData`, `MapStorage` nem NBT de sistema no projeto. As naturais são recriadas pela semente (mudam se `stormSpawnChance` ou o hemisfério mudar); as do cajado morrem no restart. | ausência de uso das APIs; `StormNetwork` só emite spawn/remove/clear | **alta** para servidor |
| D-D | **Nenhuma sincronização de estado ao entrar.** Um jogador que entra no meio de uma tempestade natural não recebe nada: confia na geração local — que, pela D-A, usa outra semente. | `StormNetwork` não tem envio de lista completa | alta |
| D-E | **A geração local no cliente é a fonte da verdade.** Não há noção de "o servidor manda"; o cliente gera e o pacote apenas acrescenta. Com configs diferentes, servidor e cliente divergem. | `Wind.stormsFor` chamado dos dois lados | alta |
| D-F | **Config parcialmente validada e sem comentários.** `WindConfig` mistura `Property` com clamp (`pSpeed`, `pChance`, `pView`, `pScale`) com `getBoolean(..., "storms", true, "...")` inline; não há `addCustomCategoryComment`. Um `destructionBlocksPerTick` absurdo ou uma chance maior que 1 passa direto. | `WindConfig.load` | média |
| D-G | **Desempenho não medido.** `StormRenderer.litColour` aloca um `float[3]` por vértice (4 por quad × 72 segmentos × 5 anéis, por sistema e por frame) e `terrainTop` faz 9 `getHeightValue` por frame por sistema. | leitura do renderizador | média |
| D-H | **Paridade Java ↔ Python sem trava.** `tools/preview_storm.py` e `gen_sounds.py` espelham constantes do Java; nada impede que divirjam no próximo commit. | docstring do próprio tool | média |
| D-J | **Id de sistema colide entre natural e invocado.** O id natural <i>é</i> o slot (`StormField.buildNatural` usa `slot` como id; `slot = tempo/24000`, logo vale 1 quando o tempo passa de 24000), e `nextDynamicId` começa em **1** — o primeiro sistema do cajado nasce com o id de um natural. `remove` por id, deduplicação na persistência e a lista do cliente podem agir no sistema errado. | `StormField:58` (`new AtomicLong(1L)`) e `buildNatural` | **alta** — bloqueia a dedup de D2 |
| D-I | **Textos de tempestade não são localizados.** `StormKind.NAME_PT/NAME_EN` escolhem por parâmetro booleano, não pelo idioma do jogo; nomes com acento saem errados na HUD em `en_US`. | `StormKind.name(int, boolean)` | baixa |

---

## 2. Objetivo da fase e critérios de aceite globais

Ao final da fase, tudo abaixo precisa ser verdade e verificável:

1. **Consistência**: com o mesmo save, `Wind.getWindAt` no cliente e no servidor devolve o
   mesmo vetor (tolerância 0 em m/s arredondado a 1 casa) para a mesma posição e tick —
   conferido por um comando que imprime lado a lado.
2. **Persistência**: reiniciar o servidor mantém sistema, tipo, idade, posição e a lista de
   sistemas invocados por cajado; carregar uma cópia do save em outra máquina dá o mesmo
   estado.
3. **Autoridade**: `StormField` do cliente é alimentado só por pacote quando `world.isRemote`.
4. **Validação**: cada um dos nove itens de "precisa validar no jogo" com roteiro, critério
   objetivo e resultado registrado em `CONTEXT_SESSION.md`.
5. **Paridade**: `python3 tools/check_mirror.py` falha se uma constante espelhada divergir;
   roda no CI junto com `check_seams.py` e `preview_storm.py --check`.
6. **Desempenho**: `/wind perf` reporta o custo por tick; servidor ≤ 2 ms/tick com 3
   sistemas e destruição ligada, cliente ≤ 1,5 ms/frame com 2 sistemas visíveis.
7. **Documentação**: `MANUAL.md`, `CHANGELOG.md`, README e `RELATORIO_ENTREGA.md`
   atualizados; versão 1.3.0 nos dois lugares (build e `@Mod`).

---

## 3. Frentes

### F1 — Validação em jogo (playtest instrumentado)

**Por quê.** Nenhum item visual foi validado: céu, névoa, chuva inclinada, partículas,
áudio, instrumentos, HUD. Sem isso, o mod não passa de "compila e os testes passam".

**Desenho.**

- **Comandos de inspeção** (operador):
  - `/wind storm spawn <tipo> [dist] [intensidade] [fase]` — `fase` ∈ `genese | maduro |
    maturo-dip | dissipacao`; implementado deslocando `spawnTick` (o sistema é imutável, então
    a fase é escolhida sem tocar na física: `spawnTick = now − progresso·duração`).
  - `/wind storm where` — distância, rumo, ETA, velocidade de translação e `vmax` atual.
  - `/wind field [raio]` — tabela no chat com `v(r)`, déficit e vento vertical em anéis, para
    conferir a forma do perfil sem instrumento externo.
  - `/wind dump <raio> <passo>` — CSV em `saves/<mundo>/dynamicwinds-dump.csv` com
    `x,z,y,v_total,v_tangencial,v_radial,vento_vertical,deficit_hpa` a partir do campo real.
- **Comparador** `tools/check_field.py` — lê o CSV e compara com o espelho Python do campo
  de tempestade (álgebra fechada, sem ruído): tolerância relativa 1e-6; além disso confere
  invariantes exatas (pico em `Rmax` = `Vmax`, continuidade em `Rmax`, déficit monotônico).
- **Roteiro de playtest** (`docs/PLAYTEST.md`, pt-BR) com os nove itens, cada um com: como
  provocar (comando), o que olhar, critério de aceite, e onde registrar o resultado.
  - céu: forma, olho, bandas girando no sentido do campo, clarão coerente com o trovão;
  - funil: base estreita tocando o solo, topo largo, inclinação contra o deslocamento;
  - névoa: fecha dentro, abre no olho;
  - chuva inclinada: quase vertical no olho, inclinada na parede;
  - partículas: as 8 aparecem com o ícone próprio (nenhum sprite de item);
  - áudio: leitos cruzando sem emenda, trovão chegando depois do clarão;
  - instrumentos: catavento, manga e anemômetro coerentes com `/wind get`; sinal de redstone
    do anemômetro em 0..15 acompanhando a velocidade;
  - HUD de depuração e HUD de aviso (F5);
  - desempenho: `/wind perf` antes/depois de uma tempestade entrar em cena.
- **Capturas**: `docs/playtest/*.png` (uma por item visual), referenciadas no playtest.

**Critérios de aceite.** (a) cada item do roteiro com resultado "passou" ou "falhou + causa"
registrado; (b) `check_field.py` verde no CSV gerado em jogo; (c) nenhum item sem
veredicto.

**Riscos.** Não há como rodar o cliente neste ambiente: o roteiro é escrito para o usuário
executar. Mitigação: comandos determinísticos, CSV e o comparador — quem roda só precisa
olhar e apertar o comando.

### F2 — Paridade Java ↔ Python travada no CI

**Por quê.** O espelho é a única verificação possível de geometria e áudio aqui. Sem trava,
a próxima mudança no Java deixa o espelho mentindo — e pior, deixando a suíte "verde".

**Desenho.**

- `tools/check_mirror.py`: extrai do Java, por expressão regular ancorada, os valores que o
  Python repete e compara com o dicionário do espelho:
  - `StormKind`: `VMAX`, `RMAX`, `ROUT`, `TOP`, `ALPHA`, `DURATION`, `SPEED`;
  - `StormRenderer`: `DECK_FRACTION`, `DECK_DROP`, `SKIRT_DROP`, `SKY_LIMIT`, `BAND_ARMS`,
    `BAND_STEPS`, `SEG_NEAR/MID/FAR`, `LOD_NEAR/MID`, e a forma de `alphaDeck`;
  - `StormAudio`: `THUNDER_PERIOD`, `THUNDER_RANGE`, `RANGE_PER_VOLUME`, `CLOSE_DISTANCE`,
    `MID_DISTANCE`, `SOUND_SPEED`;
  - `gen_textures.py` ↔ `tools/check_seams.py` (limite de emenda declarado num único lugar).
- Falha com diff legível (`esperado no Java: 0.55 / no espelho: 0.5`) e código de saída 1.
- **CI**: novo passo no workflow antes do build:
  `python3 tools/check_mirror.py && python3 tools/check_seams.py && python3 tools/preview_storm.py --check`.
  Sem dependência externa (Python puro + stdlib, já disponível no runner).

**Critérios de aceite.** (a) alterar uma constante no Java sem mexer no espelho faz o CI
falhar; (b) os três comandos rodam no CI em menos de 30 s; (c) o passo aparece no log do
workflow.

### F3 — Consistência cliente/servidor e persistência

**Por quê.** D-A, D-B, D-C, D-D, D-E: o mod hoje tem duas físicas (uma por lado) e memória
de peixe.

**Desenho.**

1. **Semente e config sincronizadas (D-A, D-F).**
   - `network/PacketWorldSync` (S→C, enviado no login e na troca de dimensão): `long seed`,
     `boolean north`, `double spawnChance`, `boolean forceWeather`, `int rainRate`,
     `boolean stormsEnabled`, `boolean destructionEnabled`.
   - Cliente: `WindConfig.applyServer(seeds/config)` grava os valores de física e
     `Wind.setSyncedSeed(world, seed)`; `Wind.modelFor` passa a usar a semente sincronizada
     quando existir, caindo para `world.getSeed()` só quando não houver servidor
     (single-player com o servidor integrado **tem** pacote, então o caminho "semente 0"
     deixa de existir na prática).
   - Regra documentada no README: *determinismo é o mecanismo de simulação, não o de
     sincronização*.
2. **Autoridade do servidor (D-E).** `StormField` ganha `setAuthoritative(boolean)`; em
   `world.isRemote` o campo **não gera** sistemas naturais — só recebe pacotes. O gerador
   natural roda apenas no servidor. Resultado: uma tempestade, uma verdade.
3. **Lista completa ao entrar (D-D).** `StormNetwork.sendFullList(EntityPlayerMP)`, disparado
   em `PlayerLoggedInEvent` e em `ServerConfigurationManager`/troca de dimensão; o pacote
   leva o campo `reset` para o cliente limpar o que tinha (evita fantasma de outra dimensão).
4. **Escopo por dimensão (D-B).** `Wind.MODELS` passa a chavear por `(seed, dimensionId)`;
   novo `stormsInDimensions` (lista de ids, padrão `[0]`); o gerador natural, o clima forçado
   e a destruição respeitam a lista. O renderizador/partículas já são por mundo, então nada
   muda no cliente além de não receber pacote de dimensão sem tempestade.
5. **Espaço de ids (D-J).** O id natural passa a ser o slot **não negativo** (`>= 0`) e o id
   dinâmico passa a ser negativo (`nextDynamicId` continua contando a partir de 1, mas o id
   emitido é `-n`). Assim `remove` por id, dedup de persistência e lista do cliente nunca
   confundem um tornado natural com um invocado, e o sinal do id diz quem o criou sem campo
   extra no pacote.
6. **Persistência (D-C).** `DynamicWindsData extends WorldSavedData`, guardando um registro
   por sistema com **os parâmetros imutáveis de nascimento** (`kind`, `id`, `spawnTick`,
   `spawnX`, `spawnZ`, `heading0`, `turnRate`, `north`, `intensityMul`, `playerSpawned`) — que
   é exatamente o que `StormSystem` precisa para renascer idêntico. Estado derivado
   (intensidade, raio, posição atual) **não** é salvo: é função do tick.
   - No `load`: restaurar a lista; o gerador natural pula ids já existentes (id natural = slot,
     então a deduplicação é por id e o espaço de ids agora é disjunto por sinal).
   - No `save`: gravar em `ServerStoppingEvent` e a cada N minutos (evita perder tudo em
     crash), com `markDirty()` no spawn/remoção.
   - A API exata (`MapStorage` global versus por dimensão no 1.7.10) **é confirmada no fonte
     do 1.7.x antes de escrever**, como foi feito com o áudio e o renderizador.
6. **Testes (F8)** cobrem o ida-e-volta dos registros, a deduplicação por id e a ausência de
   geração no cliente.

**Critérios de aceite.** (a) `/wind get` no cliente e no servidor, lado a lado, iguais; (a2) nenhum id de sistema invocado coincide com um id natural em 10.000 ticks de simulação (teste de faixa, não de amostra);
(b) reiniciar o servidor no meio de um `MAJOR` e o sistema continua com a mesma idade e
posição; (c) cajado → reiniciar → continua; (d) Nether sem tempestade com a lista padrão;
(e) entrar no meio de uma tempestade mostra o HUD correto em até 1 s.

**Riscos.** Salvar demais (escrita por tick) — mitigado por gravação periódica + em parada.
Save antigo sem o dado — o load simplesmente não encontra o registro e cai no
comportamento atual (compatível).

### F4 — Controle de destruição e proteção

**Por quê.** A destruição é "dano pesado" por escolha do usuário; num servidor isso precisa
de freio explícito, auditável e configurável — hoje só existe teto por tick, proteção de
spawn e "não quebra TE/instrumento".

**Desenho.**

- `destructionProtectedBlocks` — lista de ids (`minecraft:bedrock`, `minecraft:obsidian`,
  `minecraft:chest`, ...), checada antes do teste de vento.
- `destructionProtectedDimensions` — padrão `[]` (nenhuma); combinado com
  `stormsInDimensions`.
- `destructionMaxPerStorm` — teto de blocos por sistema (o teto por tick continua).
- **Modo de aviso** (`destructionWarnOnly`): calcula e registra o que quebraria, sem quebrar
  — é o que se roda num servidor novo antes de ligar a destruição de verdade.
- `/wind protect add|remove|list <bloco>` — edita a lista e salva a config na hora.
- `/wind destruction stats` — blocos do último minuto, por sistema e por tipo de bloco.
- Tabela de `v_critico` no README (`hardness → m/s`), gerada por um teste que percorre uma
  lista de blocos e imprime a tabela, para não haver número escrito à mão.

**Critérios de aceite.** (a) com `destructionWarnOnly`, nenhum bloco muda e o log lista o
que quebraria; (b) obsidiana, bedrock, TEs e instrumentos intactos sob `MAJOR` a 20 blocos;
(c) `destructionMaxPerStorm` respeitado (contador visível em `/wind destruction stats`);
(d) `/wind protect add` sobrevive a restart.

### F5 — Experiência do jogador

**Por quê.** Hoje o jogador tem `/wind` (operador) e um HUD de depuração. Falta o que faz a
tempestade *existir* para quem joga: aviso, leitura e idioma.

**Desenho.**

1. **`client/hud/StormAlertHud`** — canto da tela, só quando há sistema alcançando o jogador:
   nome + categoria, distância (blocos), rumo relativo à direção em que o jogador olha,
   **ETA** = distância / (`translationSpeed`·20) segundos, vento máximo atual em km/h e o
   pior vento *previsto no ponto do jogador* na passagem. Três níveis: `aviso` (fora da
   faixa destrutiva), `alerta` (dentro do raio externo) e `emergência` (a parede do olho a
   menos de 60 blocos ou ETA < 60 s), com cor e piscada. Tudo derivado do campo.
2. **Barômetro** (`ItemBarometer` + textura gerada + receita + lang): clique direito mostra
   déficit central e a **tendência** nas últimas 200 ticks. A tendência sai de graça do
   determinismo: o item amostra o campo no tick atual e no tick −200, sem guardar NBT.
   Agachado + clique alterna unidade (m/s, km/h, hPa).
3. **Idioma (D-I)** — nomes e textos de tempestade por chave de lang
   (`storm.dynamicwinds.vortex.nome`, `storm.dynamicwinds.major.nivel`), resolvidas por
   `StatCollector`, com `pt_BR.lang` e `en_US.lang` completos. Comandos e log continuam em
   texto estável (inglês ASCII), que é o que se procura no log.
4. **Morte por tempestade por tipo**: mensagem própria por categoria em vez da genérica.

**Critérios de aceite.** (a) ETA dentro de 5% do valor real medido em jogo; (b) os três
níveis de aviso disparam exatamente no critério; (c) barômetro lê o déficit e a tendência
com o sinal correto (caindo antes da chegada, subindo na saída); (d) trocar o idioma do jogo
troca os textos sem reiniciar o mod.

### F6 — Configuração

**Por quê.** D-F: opções sem comentário, sem clamp e sem validação central.

**Desenho.**

- Todo parâmetro lido como `Property` + `cfg.addCustomCategoryComment(categoria, texto)`,
  com ordem e agrupamento estáveis (`client`, `gameplay`, `tornado`, `storms`, `destruction`,
  `perf`).
- `WindConfig.sanitize()` roda no fim do load e no save da GUI: corrige e **registra no log**
  cada valor ajustado (nada de corrigir em silêncio).
- `WindConfigGui` (hoje um `GuiConfig` genérico de 27 linhas que lista as categorias) passa a
  ter descrição por categoria e revalidação ao salvar; a GUI já cobre os grupos novos
  automaticamente, então o trabalho é de texto e validação, não de tela nova.
- Documento `docs/CONFIG.md` (pt-BR) explicando cada opção, o padrão, o efeito e o custo —
  incluindo a nota de que comentários do arquivo ficam em inglês (convenção do ecossistema) e
  a explicação em português.

**Critérios de aceite.** (a) config gerada tem todos os grupos comentados; (b) valor fora da
faixa é corrigido com linha de log; (c) nenhuma opção existe no código sem documentação.

### F7 — Desempenho

**Por quê.** D-G: alocação por vértice no renderizador, 9 leituras de altura por frame por
sistema, partículas sem teto global, custo nunca medido.

**Desenho.**

- **Zero alocação no caminho de render**: `litColour` deixa de devolver `float[]` e escreve
  num buffer de 3 posições mantido pela classe (o render é single-thread).
- `terrainTop` cacheado por sistema com validade de 20 ticks (o relevo sob uma tempestade não
  muda a cada frame; quando muda, 1 s de atraso é invisível) e invalidado quando o sistema
  anda mais de 16 blocos.
- **Teto global de partículas** com ordem de degradação declarada (primeiro pólen/folha,
  depois detritos, nunca a chuva do olho), configurável em `perf.maxParticles`.
- `/wind perf` — ms/tick médio e pico (janela de 200 ticks), sistemas ativos, amostras de
  vento, blocos destruídos/min, partículas vivas, e a contagem de `getHeightValue` evitadas
  pelo cache (prova de que o cache trabalha).
- Orçamento declarado na spec e conferido no playtest: servidor ≤ 2 ms/tick (3 sistemas +
  destruição), cliente ≤ 1,5 ms/frame (2 sistemas visíveis).

**Critérios de aceite.** (a) `/wind perf` com números antes/depois; (b) contador de
alocações zero por frame no caminho do céu (medido com log de GC em soak de 60 s);
(c) orçamento respeitado.

### F8 — Testes e dívida técnica

**Desenho.** Novos testes (núcleo é livre de Minecraft, então são rápidos):

| teste | o que trava |
| --- | --- |
| `StormPersistenceTest` | ida-e-volta do registro NBT por tipo, incluindo `playerSpawned` e `intensityMul` |
| `StormFieldDedupTest` | natural + persistido com o mesmo id não duplica; id dinâmico nunca cai no espaço do natural |
| `DestructionRulesTest` | `v_critico` por classe de bloco, lista de protegidos, teto por sistema |
| `HeightmapCellTest` | célula por `floorDiv` em coordenadas negativas, leitura pelo centro, teto de células |
| `MirrorParityTest` | (Java) não; a paridade é do `check_mirror.py` |
| `ConfigSanitizeTest` | clamp + log de cada faixa |
| `StormPhaseTest` | `spawnTick` deslocado produz a fase pedida sem violar continuidade |

- Revisão de dívida: `Wind.MODELS` por `(seed, dimensão)` (F3), `surfaceClassFor` por nome de
  bioma (`biomeName.toLowerCase()`) — trocar por `biome.biomeID`/`temperature` quando o
  mapeamento por nome falhar em mods de bioma, com fallback e teste.

**Critérios de aceite.** Suíte ≥ 55 testes, todos verdes no CI, sem teste que dependa de
relógio de parede ou de `System.nanoTime`.

### F9 — Documentação e entrega

- `MANUAL.md` (pt-BR): instalação, comandos, cajado, barômetro, HUD, avisos, config, modo
  servidor (proteção e limites), perguntas frequentes ("por que a tempestade não aparece no
  Nether").
- `CHANGELOG.md`: 1.0.0 (auditoria) → 1.1.0 (tempestades) → 1.2.0 (VFX, áudio, texturas) →
  1.3.0 (esta fase).
- `docs/PLAYTEST.md` (F1) e `docs/CONFIG.md` (F6).
- README: link para o manual, seção "Servidor e multiplayer", tabela de configuração.
- `RELATORIO_ENTREGA.md`: fase 4 com os números medidos nesta rodada.
- Versão **1.3.0** em `build.gradle.kts` e `DynamicWinds.VERSION` (hoje 1.2.0 nos dois).

**Critérios de aceite.** Um leitor que nunca viu o mod consegue: instalar, invocar uma
tempestade, ler o HUD, e decidir se liga a destruição no seu servidor — sem abrir o código.

---

## 4. Fora de escopo (e por quê)

| item | por que fica fora |
| --- | --- |
| Mixins para chuva/neve e nuvens vanilla | o mod não tem framework de mixin ativo por decisão de risco; a chuva inclinada já é resolvida por partícula própria |
| CFD / solver de fluido | impraticável a 20 TPS em blocos; a modelagem analítica é a escolha documentada |
| Port para outras versões do Minecraft | a base é 1.7.10 com MCP específico; cada porta é um projeto |
| Radar, sismógrafo, abrigo construível | features novas, não polimento: entram numa fase de conteúdo |
| Anti-griefing por claim (proteção de terreno) | não existe API no 1.7.10; depende de mods de proteção de terceiros |
| Escala geográfica realista (Rmax de 30 km) | incompatível com uma área jogável; a compressão é documentada e o que se preserva é a relação vento-pressão |

---

## 5. Riscos

| risco | impacto | mitigação |
| --- | --- | --- |
| Sem playtest local (nem JDK, nem OpenGL) | itens visuais podem voltar errados | roteiro com critério objetivo + CSV + comparador; nada de "parece bom" |
| API do 1.7.10 confirmada por leitura, não por compilação local | erro só aparece no CI | confirmar cada símbolo no fonte do 1.7.x antes de escrever (prática já adotada: áudio, renderizador, `Tessellator`) |
| Persistência muda o estado de um save existente | perda de tempestade em curso | gravação periódica + no `ServerStoppingEvent`; load ausente cai no comportamento atual |
| Sincronizar config do servidor pode surpreender quem mexe no cliente | jogador acha que a config dele "não pega" | só os campos de física são sobrescritos, com aviso no log e no manual |
| Passo de Python no CI | pipeline mais longo/frágil | stdlib pura, sem rede, tempo-alvo < 30 s, tolerância explícita nos comparadores |
| Refatorar `Wind.modelFor` para `(seed, dimensão)` | regressão de paridade cliente/servidor | teste de paridade por dimensão + `check_mirror` nas tabelas afetadas |

---

## 6. Ordem de execução

Cada entrega termina com CI verde e um commit descritivo.

| # | entrega | pronto quando | depende de |
| --- | --- | --- | --- |
| D1 | Semente e config sincronizadas + autoridade do cliente (F3.1, F3.2) | `/wind get` do cliente e do servidor iguais na mesma posição e tick | — |
| D2 | Espaço de ids (F3.5) + persistência (F3.6) + testes de ida-e-volta | restart no meio de um `MAJOR` preserva idade, posição e tipo; ids nunca colidem | D1 |
| D3 | Lista completa ao entrar + escopo por dimensão (F3.3, F3.4) | entrar no meio da tempestade mostra o HUD correto; Nether limpo | D2 |
| D4 | Paridade travada no CI (F2) | mudar constante no Java quebra o CI | — (paralelo) |
| D5 | Config: comentários, `sanitize`, GUI (F6) | config gerada comentada; valor absurdo corrigido com log | — (paralelo) |
| D6 | Comandos de inspeção + `check_field.py` + roteiro de playtest (F1) | CSV de jogo comparado ao espelho com tolerância 1e-6 | D1 (para o campo ser o real) |
| D7 | Proteção de destruição + modo aviso + estatísticas (F4) | obsidiana/TE intactos sob `MAJOR`; `warnOnly` não muda bloco | D3 |
| D8 | HUD de aviso, barômetro, idioma (F5) | ETA dentro de 5%; barômetro com tendência correta; idiomas completos | D6 |
| D9 | Desempenho medido e alívio de alocação (F7) | `/wind perf` dentro do orçamento; zero alocação por frame no céu | D3 |
| D10 | Manual, changelog, relatório, versão 1.3.0 (F9) | leitor novo instala e usa sem abrir o código | todas |

---

## 7. Arquivos afetados (mapa de mudança)

| área | arquivos |
| --- | --- |
| Rede | `integration/network/PacketWorldSync.java` (novo), `StormNetwork.java`, `PacketStormSync.java` |
| Semente/consistência | `api/Wind.java` (`setSyncedSeed`, chave por `(seed, dimensão)`), `integration/WindEngine.java`, `integration/WindConfig.java` |
| Autoridade e ids | `core/StormField.java` (`setAuthoritative`, dedup por id, id dinâmico negativo) |
| Persistência | `integration/DynamicWindsData.java` (novo), `DynamicWinds.java` (eventos) |
| Destruição | `integration/DestructionHandler.java`, `integration/commands/WindCommand.java`, `WindConfig` |
| Jogador | `client/hud/StormAlertHud.java` (novo), `client/ClientEvents.java`, `content/items/ItemBarometer.java` (novo), `content/ContentRegistry.java`, `tools/gen_textures.py` (ícone do barômetro) |
| Idioma | `assets/dynamicwinds/lang/{pt_BR,en_US}.lang`, `core/StormKind.java` |
| Desempenho | `client/storm/StormRenderer.java`, `client/particles/*`, `integration/HeightmapCache.java` |
| Ferramentas | `tools/check_mirror.py` (novo), `tools/check_field.py` (novo), `.github/workflows/build.yml` |
| Testes | `src/test/java/com/dynamicwinds/core/*` (6 classes novas) |
| Docs | `MANUAL.md`, `CHANGELOG.md`, `docs/PLAYTEST.md`, `docs/CONFIG.md`, `README.md`, `RELATORIO_ENTREGA.md`, `CONTEXT_SESSION.md` |

---

## 8. Definição de pronto

- [ ] CI verde com build, 39+16 testes, `check_mirror`, `check_seams` e `preview_storm --check`.
- [ ] `/wind get` cliente = servidor, `/wind perf` dentro do orçamento, `/wind destruction stats` coerente.
- [ ] Restart preserva tempestade natural e invocada; Nether sem tempestade por padrão.
- [ ] Nove itens do playtest com veredicto registrado e capturas.
- [ ] Config comentada, validada e documentada; GUI com descrição por categoria.
- [ ] `MANUAL.md` e `CHANGELOG.md` publicados; versão 1.3.0 nos dois lugares.
- [ ] Nenhuma afirmação no README sem verificação correspondente (a fase começa corrigindo
      uma: "cliente e servidor calculam o mesmo" só passa a ser verdade depois de D1).
