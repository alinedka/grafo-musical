# Grafo Musical: Compositor Musical com Grafos

Projeto da disciplina de Estrutura de Dados (ADO), Centro Universitário Senac.

**Grupo:** Aline Douaki, Caíque Gandra Costa e Kayque de Sousa Cazzoto

## Sobre o projeto

Programa em Java que **aprende uma música**, transforma essa música num **grafo dirigido e ponderado** e usa esse grafo para **compor músicas novas**.

- **Vértices:** as notas (ou as últimas N notas, conforme a "memória" escolhida)
- **Arestas:** "depois desta nota veio aquela", com **peso** = quantas vezes isso aconteceu na música
- **Treinar:** percorre a música e monta o grafo, guardado como **lista de adjacência**
- **Compor:** faz um **passeio aleatório ponderado** pelo grafo, sorteando cada próxima nota pelo método da roleta
- **Busca em largura (BFS):** confere quais notas podem ser alcançadas a partir do início

A tela mostra o grafo desenhado e animado enquanto a música toca, além da lista de adjacência, da matriz de probabilidades e da música gerada. O som usa a biblioteca `javax.sound.midi`, que já vem com o Java, então nenhuma biblioteca externa é necessária.

## Requisitos

- **Java 17 ou superior** (JDK, que inclui o `javac`)

Para conferir se está instalado, abra o terminal e digite:

```
java -version
javac -version
```

## Como executar

### Windows

Dê dois cliques no arquivo **`executar.bat`**. Ele compila o projeto e abre o programa.

### Pelo terminal (Windows, Linux ou macOS)

Na pasta do projeto:

```
javac -encoding UTF-8 -d out src/*.java
java -cp out CompositorApp
```

## Como usar

1. **Treinar o grafo:** escolha uma música de exemplo e a memória (de 1 a 5 notas) e clique em **Treinar grafo**.
2. **Compor:** clique em **Compor e tocar**. O programa sorteia um caminho pelo grafo e toca a música nova.
3. Também dá para **tocar a melodia original**, trocar o **instrumento** e o **andamento**, e **salvar a composição** em arquivo `.mid`.

Dica: com **memória 1** o grafo fica pequeno e cheio de sorteios, então a música sai criativa. Com **memória 4** a música sai bem parecida com a original.

## Estrutura do código

| Arquivo | O que faz |
|---|---|
| `GrafoMusical.java` | O grafo: treinar, compor (passeio aleatório) e busca em largura |
| `Vertice.java` | Vértice: as notas do estado e a lista de arestas de saída |
| `Aresta.java` | Aresta: destino, peso e ritmo |
| `Nota.java`, `EventoMusical.java`, `Composicao.java` | Notas, durações e o resultado da composição |
| `Melodias.java` | Músicas de exemplo e leitura do formato `NOTA:DURAÇÃO` |
| `TocadorMidi.java` | Toca o som e salva o arquivo `.mid` |
| `PainelGrafo.java` | Desenho do grafo na tela |
| `CompositorApp.java` | Janela principal (Swing) e método `main` |
