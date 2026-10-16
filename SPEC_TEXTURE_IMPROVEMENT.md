# Spec: Melhoria de Texturas UV do DynamicWinds

## Contexto Atual

O DynamicWinds é um mod para Minecraft 1.7.10 que implementa um sistema de vento dinâmico determinístico. Recentemente, foram adicionados 3 blocos com TESRs personalizados:

- **Wind Vane** (cata-vento): gira conforme direção do vento
- **Windsock** (manga de vento): infla conforme intensidade do vento
- **Anemometer** (anemômetro): emite sinal redstone proporcional à velocidade

As texturas são renderizadas via TESR com GL11 direto e UV mapping básico. O build CI passa (JDK 25 + Gradle 9.2.0 + RFG 2.0.2).

## Estado das Texturas Atuais

### Texturas PNG criadas
- `src/main/resources/assets/dynamicwinds/textures/blocks/wind_vane.png` (64x64, cinza metálico com seta)
- `src/main/resources/assets/dynamicwinds/textures/blocks/windsock.png` (64x64, laranja manga)
- `src/main/resources/assets/dynamicwinds/textures/blocks/anemometer.png` (64x64, base + copos vermelhos)

### Implementação de renderização (TESR)
- `TESRWindVane.java`: rotação conforme `vane.angle`, box com UV mapping
- `TESRWindsock.java`: inflação conforme `ws.inflation`, box com UV mapping
- `TESRAnemometer.java`: **NÃO IMPLEMENTADO** (ainda não existe)

### Quality do UV Mapping atual
- Todas as faces usam UV [0,0] a [1,1] (textura completa em cada face)
- Sem consideração de orientação correta para cada face
- Texturas muito simples (geradas programaticamente, baixa resolução)

## Spec de Melhoria

### 1. Qualidade das Texturas

#### 1.1 Resolução
- Aumentar de 64x64 para **128x128** (ou manter 64x64 com detalhe melhor)
- Usar imagen mais sofisticada ou gerar com padrão mais detalhado

#### 1.2 Detalhe Visual

**Wind Vane:**
- Base: cilindro metálico com brilho/especular
- Seta/arrow: metal escuro apontando para cima (direção default)
- Centro/hub: disco com detalhe

**Windsock:**
- Mangueira conica alongada
- Gradientes de cor (laranja para mais claro/neto)
- Detalhe de borda/frange na ponta aberta
- Visual de "inflação" - a textura deve accommodar deformação

**Anemometer:**
- Poste central vertical
- 3 copos em forma de "T" ou semicírculo, em posições equidistantes
- Centro com detalhe

### 2. UV Mapping Correto por Face

Atualmente todas as faces usam o mesmo UV. O correto seria:

Para um box dado por (x1,y1,z1)-(x2,y2,z2):
- **Face frontal (z+)**: UV depende da orientação desejada
- **Face traseira (z-)**: espelhada horizontalmente vs frontal
- **Topo (y+)**: UV diferente para não parecer repetido
- **Fundo (y-)**: similar ao topo
- **Lado esquerdo (x-)**: UV específico
- **Lado direito (x+)**: espelhado vs esquerdo

Exemplo para textura de 1 bloco (UV 0..1 em cada face):
```
Face front (z+):  u: x1->x2 (0->1), v: y1->y2 (0->1)
Face back (z-):   u: x2->x1 (1->0), v: y1->y2 (0->1)   // espelhado
Face top (y+):    u: x1->x2, v: z1->z2
...
```

### 3. Textura de Blocos Minecraft 1.7.10

No 1.7.10, blocos usam geralmente texturas de 16x16 com pistons/repetição. Para blocos customizados:
- Opção A: textura única de 64x64 ou 128x128 mapeada em todas as faces
- Opção B: textura de 16x16 repetida (mais Minecraft-like)
- Opção C: modelo JSON com elementos (cubos) e múltiplas texturas

Para os blocos do DynamicWinds, **Opção A** é adequada (blocos detailados).

### 4. Implementação Técnica

#### 4.1 Gerar texturas de melhor qualidade

Opções:
1. **Gerar via Python com mais detalhe**: usar PIL/Pillow para criar texturas com gradientes, brilhos, detalhes
2. **Usar imagens externas**: buscar texturas free de blocks/metallic/cones
3. **Manter 64x64 com melhor design**: focar na arte, não resolução

Recomendação: gerar 128x128 com detalhe via Python, ou usar:image de alta qualidade.

#### 4.2 UV Mapping aprimorado

Refatorar `renderTexturedBox` para acceptar parâmetros de UV por face ou calcular UV corretos baseado em orientação.

Código atual (simplificado):
```java
private void renderTexturedBox(float x1, float y1, float z1, float x2, float y2, float z2) {
    GL11.glBegin(GL11.GL_QUADS);
    // cada face com GL11.glTexCoord2f e GL11.glVertex3f
    ...
    GL11.glEnd();
}
```

Melhoria: calcular UV baseado na posição do vértice relativa ao centro do box.

#### 4.3 Anemômetro TESR

Implementar `TESRAnemometer.java` que renderiza:
- Poste central
- 3 copos em órbita (ou fixos em direções cardinais)

### 5. Testes e Validação

No Minecraft 1.7.10 (validação manual necessária):
- Blocos aparecem com textura correta em todas as faces
- Wind vane gira suavemente
- Windsock infla/desinfla visivelmente
- Anemômetro renderiza

No CI:
- Compilação passa
- Texturas estão em `src/main/resources/...` e são encontradas pelo Minecraft

## Arquivos para Modificar

1. `src/main/resources/assets/dynamicwinds/textures/blocks/wind_vane.png`
2. `src/main/resources/assets/dynamicwinds/textures/blocks/windsock.png`
3. `src/main/resources/assets/dynamicwinds/textures/blocks/anemometer.png`
4. `src/main/java/com/dynamicwinds/client/render/tesr/TESRWindVane.java`
5. `src/main/java/com/dynamicwinds/client/render/tesr/TESRWindsock.java`
6. `src/main/java/com/dynamicwinds/client/render/tesr/TESRAnemometer.java` (novo)
7. `src/main/java/com/dynamicwinds/content/blocks/BlockAnemometer.java` (se necesita TESR registration)

## Critérios de Aceitação

- [x] Blocos renderizam com textura nas 6 faces — UV por face implementado e travado por teste (winding validado)
- [x] UV mapping correto (não esticado/espelhado errado) — cada face usa sua célula do atlas
- [x] Wind vane gira visualmente — rotação por `renderAngle(partialTicks)`
- [x] Windsock mostra inflação variável — seção dos anéis + queda da ponta
- [x] Anemômetro renderiza — `TESRAnemometer` implementado
- [x] Compilação CI passa
- [x] Texturas têm detalhe aceitável (não pixelado demais) — atlas 128x128 com células de 32px

## Limitações

- Validação visual só possível dentro do Minecraft 1.7.10 (não disponível neste ambiente)
- Texturas geradas programicamente têm limite de qualidade
- Sem acesso a ferramentas de imagem avançadas

## Status da implementação

Implementado sobre o commit `50e0a19`. O que mudou em relação ao plano original
está registrado abaixo, inclusive onde a abordagem foi diferente do previsto.

### 1. Qualidade das texturas — feito

- Texturas de bloco são **atlas 4x4 de 128x128** (células de 32px), e não uma
  arte única de 64/128px. O atlas resolve o problema de "todas as faces usam a
  textura inteira": cada face escolhe sua célula (poste, seta, cauda, manga,
  conchas, mostrador, parafuso, ferrugem...).
- As texturas são geradas por `tools/gen_textures.py`, em **Python puro**
  (sem Pillow/numpy), com hashing determinístico em vez de `random()`: rodar o
  script reproduz byte a byte o mesmo PNG. Isso evita a etapa manual de editar
  imagens e deixa a arte revisável em diff.
- `tools/preview_textures.py` monta `docs/textures_preview.png` com a grade das
  células, para revisar a arte sem abrir o jogo.

### 2. UV mapping correto por face — feito

- `BoxMesh` descreve os 6 quads com winding anti-horário visto de fora;
  `AtlasCells` converte (coluna, linha) do PNG para UV (com a inversão de `v`,
  já que a linha 0 fica no topo da imagem e `v = 0` na base da textura);
  `TextureAtlas` emite a geometria.
- Achado durante a implementação: a versão anterior tinha **4 das 6 faces com
  winding invertido** (normal para dentro do cubo), então eram descartadas pelo
  `GL_CULL_FACE` e o bloco aparecia vazado de alguns lados. Isso não aparece na
  compilação; `BoxMeshTest` agora trava o winding no CI.
- Faces escuras claras por orientação (`FACE_SHADE`) e brilho por nível de luz
  do bloco, para o cubo não ficar chapado.

### 3. TESRs — feito

- `AbstractBlockTESR` centraliza matriz, estado de OpenGL (blend, alpha test,
  `texEnv`) e brilho.
- `TESRWindVane`: base, poste, luva, eixo de bronze e conjunto girável (seta +
  cauda + duas penas laterais).
- `TESRWindsock`: deixou de ser placeholder (eram três caixas fixas). Agora são
  quatro anéis de tecido com bandas alternadas, seção transversal que cresce com
  a inflação e ponta que cai quando o vento está fraco.
- `TESRAnemometer`: novo — o anemômetro emitia redstone mas não existia
  renderizador, então era invisível no mundo.

### 4. Correções necessárias fora do escopo da spec

Sem estas, o resto não apareceria no jogo:

- **Registro de TESR**: o código usava `Field.get(null)` em
  `mapSpecialRenderers`, que é campo de **instância** no 1.7.10 — lançava NPE, a
  exceção era engolida e nenhum TESR era registrado. Além disso o nome do campo
  é ofuscado no jar de produção, então reflexão por nome nunca funcionaria.
  Passei a usar `ClientRegistry` (API do FML, remapeada no reobf) e mantive a
  reflexão como rede de segurança, corrigida para campo de instância.
- **Icône de item**: os blocos passaram a usar um tipo de render próprio, sem
  formato de cubo, então o `ItemBlock` deixa de ser renderizado em 3D e precisa
  de sprite 2D próprio (`*_item.png`).
- **Blocos não sólidos**: `isOpaqueCube = false`, `renderAsNormalBlock = false`,
  hitbox reduzida, som e aba criativa definidos. Sem isso o TESR apareceria
  dentro de um cubo de textura esticada.
- **Modelos JSON**: mantidos como estão. Com o tipo de render próprio eles não
  são usados no mundo; opcionalmente podem virar ícone de inventário em um
  resource pack, mas não são caminho crítico.

### 5. Partículas — feito

As 10 texturas foram refeitas em paleta de Minecraft (folha lanceolada com
nervura, floco de 6 pontas, gota, respingo, redemoinho em espiral, risco de
rajada, graveto, speck de poeira, pólen e funil do tornado com alpha).


## Atualização: texturas das tempestades

A expansão de tempestades acrescentou três texturas ao mesmo gerador
(`tools/gen_textures.py`), mantendo a regra de que `python3 tools/gen_textures.py` recria
exatamente os mesmos bytes:

- `items/catastrophe_staff.png` (16x16) — haste de obsidiana com enrolado no cabo e orbe
  com espiral de raio crescente. A primeira tentativa usava anéis concêntricos e lia como
  alvo de tiro; a espiral é o que faz o olho humano ler "vórtice" em 16 pixels.
- `environment/storm_clouds.png` (128x128) — teto de nuvem. Precisa ser **repetível**: é
  desenhado em várias repetições sobre o mesmo anel, e ruído comum não repete, então
  apareceriam emendas retas atravessando o céu. O gerador ganhou `_tileable_noise` (value
  noise periódico) e `_fbm` (soma de oitavas com período calculado por oitava).
- `environment/storm_wall.png` (64x64) — parede do olho / cortina de chuva, densa embaixo e
  rala em cima, com ruído esticado na vertical para virar raja de chuva em vez de mancha.

Duas partículas novas seguem a convenção existente, e agora **com** registro de ícone:
`storm_rain.png` (8x16) e `spray.png` (8x8). O registro é obrigatório porque no 1.7.10 uma
partícula com `getFXLayer() == 1` é desenhada com o atlas de itens; sem
`setParticleIcon` o jogo sorteia um sprite qualquer e a textura nunca aparece no jogo.

`tools/preview_textures.py` ganhou uma faixa de céu que mostra o cajado ampliado, o teto de
nuvem e a parede do olho (a parede sobre fundo escuro, que é como ela aparece em uso).
