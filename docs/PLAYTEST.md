# Roteiro de playtest instrumentado — DynamicWinds 1.3.0

**Status:** roteiro preparado; os resultados e capturas precisam ser preenchidos por uma execução real no Minecraft 1.7.10. Não há capturas de jogo nesta entrega e nenhum item visual é declarado como aprovado sem essa execução.

## Preparação

1. Inicie um mundo de teste com o mod e habilite cheats/OP.
2. Faça backup do save antes de testar destruição.
3. Em `config/dynamicwinds.cfg`, use temporariamente `destructionWarnOnly=true` e `destructionBlocksPerTick=24`.
4. Deixe `debugHud=true`; carregue chunks abertos e um instrumento de cada tipo.
5. Registre versão do Minecraft/Forge, seed, dimensão, resolução, distância de renderização e idioma.
6. Use `/wind storm spawn major 180 1.0 maduro` para o cenário principal. Os aliases de fase aceitos são `genesis|genese`, `maduro|mature`, `maturo-dip|mature-dip` e `dissipacao|dissipation`.

## Itens e critérios objetivos

Para cada linha, registre **passou** ou **falhou + causa** e salve a captura indicada em `docs/playtest/`.

| # | Ensaio / como provocar | Aceite observável | Evidência |
|---|---|---|---|
| 1 | `/wind storm spawn hurricane 180 1 maduro`; observar céu de perto e a 160/420 blocos | teto em anel, olho central aberto, bandas visíveis; sem faces piscando ou lote OpenGL preso | `01-sky.png` |
| 2 | `/wind storm spawn vortice 60 1 maduro`; observar lateral em terreno plano | funil toca o solo, base mais estreita que o topo e inclina-se contra o deslocamento | `02-vortex.png` |
| 3 | Atravessar a parede e depois o olho da tempestade | névoa fecha na parede e abre no olho; sem tela permanentemente escura ao sair | `03-fog.png` |
| 4 | Atravessar olho, parede e bandas; conferir chuva vanilla e partículas próprias | chuva quase vertical no olho e inclinada na parede; sem partículas usando sprite aleatório | `04-rain.png` |
| 5 | Gerar vento forte e aguardar folha, poeira, pólen, redemoinho, rajada, detrito, tornado e spray | os oito ícones próprios aparecem; partículas limitadas sem suprimir chuva crítica | `05-particles.png` |
| 6 | Aguardar leitos e trovão; gravar ao menos 30 s | transições de loop sem estalo; clarão precede o trovão; áudio não continua após sair | `06-audio.png` + observação de áudio |
| 7 | Instalar catavento, manga e anemômetro perto do ponto de teste | direção/inflação/rotor coerentes com `/wind get`; redstone do anemômetro varia em 0..15 | `07-instruments.png` |
| 8 | Comparar `/wind get`, HUD de depuração e alerta; testar `en_US` e `pt_BR` | valores a uma casa decimal iguais cliente/servidor; alerta muda de aviso→alerta→emergência nos limites; idioma muda sem reiniciar | `08-hud.png` |
| 9 | Executar `/wind perf` antes e durante 2 sistemas visíveis; verificar warn-only e proteção de blocos | custos dentro do orçamento da spec; warn-only não altera blocos; obsidiana, bedrock e TEs intactos | `09-performance-protection.png` |

## Campo numérico

1. Execute `/wind dump 32 8` no servidor.
2. Copie o arquivo `dynamicwinds-dump.csv` indicado pelo comando para esta pasta de trabalho.
3. Rode `python3 tools/check_field.py dynamicwinds-dump.csv`.
4. Aceite somente código de saída 0: todas as amostras do campo da tempestade devem coincidir com o espelho fechado com tolerância relativa `1e-6`.

## Registro de resultados

| Data / build | Ambiente | Itens aprovados | Itens falhos / causa | Operador |
|---|---|---|---|---|
| PENDENTE — execução real necessária | Minecraft 1.7.10 / Forge — não executado nesta sandbox | nenhum declarado | 9 itens aguardam playtest | — |
