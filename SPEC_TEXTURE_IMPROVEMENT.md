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

- [ ] Blocos renderizam com textura nas 6 faces no jogo
- [ ] UV mapping correto (não esticado/espelhado errado)
- [ ] Wind vane gira visualmente
- [ ] Windsock mostra inflação variável
- [ ] Anemômetro renderiza (se implementado)
- [ ] Compilação CI passa
- [ ] Texturas têm detalhe aceitável (não pixelado demais)

## Limitações

- Validação visual só possível dentro do Minecraft 1.7.10 (não disponível neste ambiente)
- Texturas geradas programicamente têm limite de qualidade
- Sem acesso a ferramentas de imagem avançadas
