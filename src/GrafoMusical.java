import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

/**
 * ============================================================================
 *  GRAFO MUSICAL: o coração do projeto
 * ============================================================================
 *
 * TIPO DE GRAFO:
 *   GRAFO DIRIGIDO E PONDERADO, com laços e ciclos
 *     - Vértices (classe Vertice): os estados, isto é, as últimas N notas tocadas
 *     - Arestas  (classe Aresta):  "do estado A a música foi para o estado B",
 *                                  com peso = quantas vezes isso aconteceu
 *   Como a próxima nota é sorteada só com base no estado atual, este grafo
 *   também é uma CADEIA DE MARKOV
 *
 * MEMÓRIA (ordem da cadeia de Markov):
 *   N = quantas notas formam cada vértice
 *     - Memória 1: o vértice é uma nota só. O grafo "esquece" tudo, menos a
 *       última nota, então a música gerada é criativa, mas pouco parecida
 *       com a original (tem muitos pontos de escolha)
 *     - Memória 4: o vértice são as 4 últimas notas. O grafo sabe em que parte
 *       da música está, quase todas as setas viram 100% e a música gerada fica
 *       bem parecida com a original
 *
 * REPRESENTAÇÃO: LISTA DE ADJACÊNCIA
 *   O mapa "vertices" encontra cada vértice pelo seu estado, e cada vértice
 *   guarda a lista das arestas que saem dele (Vertice.adjacentes)
 *   Escolhemos lista e não matriz porque o grafo é ESPARSO (cada nota se liga
 *   a poucas outras) e a operação mais usada é "ver os vizinhos do vértice atual"
 *
 * AS TRÊS OPERAÇÕES PRINCIPAIS:
 *   1. treinar()        -> MONTA o grafo a partir de uma melodia
 *   2. compor()         -> PASSEIO ALEATÓRIO PONDERADO: anda pelo grafo
 *                          sorteando as arestas e gera uma música nova
 *   3. buscaEmLargura() -> BFS: descobre quais vértices são alcançáveis
 * ============================================================================
 */
public class GrafoMusical {

    //---------------------------------------------------------------- ATRIBUTOS

    //Todos os vértices do grafo. A CHAVE é o estado em texto (ex.: "[60, 67]")
    //e o VALOR é o objeto Vertice. Assim, achar o vértice de um estado é rápido
    //LinkedHashMap mantém os vértices na ordem em que apareceram na melodia,
    //o que deixa a lista de adjacência e o desenho na ordem da música
    private final Map<String, Vertice> vertices = new LinkedHashMap<>();

    //Melodias já usadas no treino. Guardamos para poder reconstruir o grafo
    //quando o usuário troca a memória (setMemoria)
    private final List<List<EventoMusical>> melodiasTreinadas = new ArrayList<>();

    private int memoria = 1;                              //quantas notas formam um vértice
    private Vertice verticeInicial;                       //onde o passeio começa (1º estado da 1ª melodia)
    private double duracaoInicial = 1.0;                  //duração da nota do vértice inicial
    private List<EventoMusical> notasIniciais = List.of(); //notas que vêm antes do 1º estado (memória > 1)
    private Vertice verticeFinal;                         //último estado da 1ª melodia: onde a música "termina"
    private int notaFinal = -1;                           //última nota da 1ª melodia
    private final Random random = new Random();           //gerador de números aleatórios do sorteio

    //---------------------------------------------------------------- CONTROLE

    /** Apaga todos os vértices, arestas e melodias treinadas (grafo vazio) */
    public void limpar() {
        vertices.clear();
        melodiasTreinadas.clear();
        verticeInicial = null;
        duracaoInicial = 1.0;
        notasIniciais = List.of();
        verticeFinal = null;
        notaFinal = -1;
    }

    /**
     * Muda a memória do grafo e o RECONSTRÓI do zero com as mesmas melodias
     * Precisa reconstruir porque, com outra memória, os vértices são outros
     * (ex.: "Sol4" na memória 1 vira vários vértices "[... Sol4]" na memória 4)
     */
    public void setMemoria(int novaMemoria) {
        if (novaMemoria < 1) {
            throw new IllegalArgumentException("A memória deve ser pelo menos 1");
        }
        memoria = novaMemoria;
        List<List<EventoMusical>> copia = new ArrayList<>(melodiasTreinadas); //guarda antes de limpar
        limpar();
        for (List<EventoMusical> melodia : copia) {
            treinar(melodia);
        }
    }

    public int getMemoria() {
        return memoria;
    }

    //---------------------------------------------------------------- AUXILIARES

    /** Transforma um estado em texto para usar como chave do mapa. Ex.: [60, 67] -> "[60, 67]" */
    private static String chave(List<Integer> estado) {
        return estado.toString();
    }

    /**
     * Devolve o vértice do estado informado. Se ele ainda não existe, cria
     * computeIfAbsent faz as duas coisas numa linha só: procura no mapa e,
     * se não achar, cria um Vertice novo e já guarda no mapa
     * Isso garante que cada estado tem UM ÚNICO vértice no grafo
     */
    private Vertice obterOuCriarVertice(List<Integer> estado) {
        return vertices.computeIfAbsent(chave(estado), c -> new Vertice(estado));
    }

    /**
     * Monta o estado que TERMINA na posição "fim" da melodia,
     * ou seja, as "memoria" notas que vão até a posição fim
     *
     * Exemplo com memória 3 e melodia [Dó, Dó, Sol, Sol, Lá]:
     *   estadoTerminandoEm(melodia, 3) = [Dó, Sol, Sol]   (posições 1, 2 e 3)
     *
     * Math.floorMod trata a melodia como CIRCULAR: se a conta der uma posição
     * negativa, ela "dá a volta" e pega notas do final da melodia
     */
    private List<Integer> estadoTerminandoEm(List<EventoMusical> melodia, int fim) {
        int n = melodia.size();
        List<Integer> estado = new ArrayList<>(memoria);
        for (int j = memoria - 1; j >= 0; j--) {          //da nota mais antiga para a mais recente
            estado.add(melodia.get(Math.floorMod(fim - j, n)).nota());
        }
        return estado;
    }

    //============================================================ 1. TREINAR

    /**
     * TREINAMENTO: percorre a melodia e liga cada estado ao estado seguinte,
     * MONTANDO O GRAFO
     *
     * Exemplo com memória 1, melodia "Dó Ré Mi Dó Ré":
     *   arestas Dó->Ré (peso 2), Ré->Mi (peso 1), Mi->Dó (peso 1)
     *   (e Ré->Dó, que liga o fim ao começo, porque a melodia é circular)
     *
     * Exemplo com memória 2, mesma melodia:
     *   [Dó Ré]->[Ré Mi], [Ré Mi]->[Mi Dó], [Mi Dó]->[Dó Ré]
     *
     * A melodia é tratada como CIRCULAR: depois da última nota ela recomeça
     * Assim TODO vértice tem pelo menos uma aresta de saída e o passeio
     * aleatório nunca fica "preso" num vértice sem saída
     *
     * Cada aresta também guarda a duração da nota de chegada (o ritmo)
     */
    public void treinar(List<EventoMusical> melodia) {
        if (melodia.size() < 2) {
            return; //com menos de 2 notas não existe nenhuma transição
        }
        melodiasTreinadas.add(List.copyOf(melodia));
        int n = melodia.size();

        //PASSO 1: cria (ou encontra) o vértice de cada posição da melodia
        //estados[i] = o estado formado pelas notas que terminam na posição i + memoria - 1
        //Se o mesmo estado aparece duas vezes na música, obterOuCriarVertice
        //devolve o MESMO vértice, e é assim que as repetições se juntam
        Vertice[] estados = new Vertice[n];
        for (int i = 0; i < n; i++) {
            estados[i] = obterOuCriarVertice(estadoTerminandoEm(melodia, i + memoria - 1));
        }

        //PASSO 2: liga cada estado ao seguinte (cria a aresta ou soma 1 no peso)
        //O "% n" faz o último estado ligar de volta ao primeiro (ciclo)
        for (int i = 0; i < n; i++) {
            int proximo = (i + 1) % n;
            //duração da nota que soa ao chegar no próximo estado (a última nota dele)
            double duracaoChegada = melodia.get((proximo + memoria - 1) % n).duracao();
            estados[i].adicionarTransicao(estados[proximo], duracaoChegada);
        }

        //PASSO 3: só na primeira melodia, guarda onde a música começa e termina
        if (verticeInicial == null) {
            verticeInicial = estados[0];
            duracaoInicial = melodia.get((memoria - 1) % n).duracao();
            //O estado que termina na última nota é o "final" da música
            //O compor() tenta terminar nele, para a música não parar no meio de uma frase
            verticeFinal = vertices.get(chave(estadoTerminandoEm(melodia, n - 1)));
            notaFinal = melodia.get(n - 1).nota();
            //Com memória > 1, o primeiro vértice já "contém" notas anteriores
            //(ex.: memória 4, o 1º vértice tem as notas 1, 2, 3 e 4)
            //As notas 1 a 3 precisam ser tocadas antes, no começo da composição
            List<EventoMusical> iniciais = new ArrayList<>();
            for (int j = 0; j < memoria - 1; j++) {
                iniciais.add(melodia.get(j % n));
            }
            notasIniciais = iniciais;
        }
    }

    //============================================================ 2. COMPOR

    /**
     * SORTEIO pelo MÉTODO DA ROLETA: escolhe por qual aresta seguir
     * Cada aresta ocupa uma "fatia" de tamanho igual ao seu peso
     *
     * Exemplo: vértice Sol com arestas Sol (peso 4), Lá (peso 2), Fá (peso 4)
     *   Total = 10, então sorteamos um número de 0 a 9:
     *
     *     número:   0 1 2 3 | 4 5 | 6 7 8 9      (na ordem da lista)
     *     aresta:   Sol     | Lá  | Fá
     *
     *   Passo a passo com o sorteio = 5:
     *     Sol: acumulado = 4.  5 < 4? não, continua
     *     Lá:  acumulado = 6.  5 < 6? SIM -> escolhe Lá
     *
     *   Quem tem mais peso ocupa mais números, e por isso tem mais chance
     */
    public Aresta sortearAresta(Vertice atual) {
        int sorteio = random.nextInt(atual.getPesoTotalSaida()); //número de 0 até total-1
        int acumulado = 0;
        for (Aresta aresta : atual.getAdjacentes()) {
            acumulado += aresta.getPeso();   //onde termina a "fatia" desta aresta
            if (sorteio < acumulado) {
                return aresta;               //o número caiu na fatia desta aresta
            }
        }
        //Só chegaria aqui se o vértice não tivesse arestas, o que o treino circular impede
        throw new IllegalStateException("Vértice sem arestas de saída: " + atual);
    }

    /**
     * COMPOSIÇÃO: PASSEIO ALEATÓRIO PONDERADO pelo grafo
     *
     *   1. Começa no vértice inicial
     *   2. Toca a nota do vértice atual
     *   3. Sorteia uma aresta de saída (sortearAresta) e vai para o destino dela
     *   4. Repete
     *
     * A sequência de vértices visitados É a música nova. Como cada passo é
     * sorteado, cada composição sai diferente, mas só usa transições que
     * existiam na música original (por isso mantém o "estilo" dela)
     *
     * @param quantidadeNotas quantos passos o passeio deve dar (no mínimo)
     */
    public Composicao compor(int quantidadeNotas) {
        List<EventoMusical> musica = new ArrayList<>();  //as notas que serão tocadas
        List<Vertice> caminho = new ArrayList<>();       //o vértice de cada nota (para o desenho)
        if (vertices.isEmpty()) {
            return new Composicao(musica, caminho);      //grafo vazio: nada para compor
        }

        //Notas anteriores ao primeiro estado (só existem com memória > 1)
        //Não têm vértice próprio no caminho, por isso entra null
        for (EventoMusical ev : notasIniciais) {
            musica.add(ev);
            caminho.add(null);
        }

        Vertice atual = verticeInicial;    //o passeio começa no início da música
        double duracao = duracaoInicial;
        int passos = 0;

        while (true) {
            //Toca a nota do vértice atual
            musica.add(new EventoMusical(atual.getNota(), duracao));
            caminho.add(atual);
            passos++;

            //CONDIÇÃO DE PARADA: depois de dar a quantidade de passos pedida,
            //continua até chegar ao vértice final da melodia original, para a
            //música terminar "resolvida". Para nunca virar um laço infinito,
            //há um limite de 64 passos extras
            if (passos >= quantidadeNotas
                    && (atual == verticeFinal || passos >= quantidadeNotas + 64)) {
                break;
            }

            //Sorteia a aresta e anda pelo grafo
            Aresta aresta = sortearAresta(atual);     //qual seta seguir (roleta)
            duracao = aresta.sortearDuracao(random);  //o ritmo da próxima nota
            atual = aresta.getDestino();              //anda para o vértice de destino
        }

        //Se bateu o limite sem chegar ao vértice final, termina pelo menos na nota final
        if (caminho.get(caminho.size() - 1) != verticeFinal) {
            musica.add(new EventoMusical(notaFinal, 2.0));
            caminho.add(null);
        }

        //A última nota soa um pouco mais longa (pelo menos 2 tempos), para encerrar
        int ultima = musica.size() - 1;
        musica.set(ultima, new EventoMusical(musica.get(ultima).nota(), Math.max(2.0, musica.get(ultima).duracao())));
        return new Composicao(musica, caminho);
    }

    /**
     * Para cada nota de uma melodia, informa em qual vértice do grafo ela está
     * É usado para animar o desenho quando tocamos a MELODIA ORIGINAL:
     * assim dá para ver que a música original também é um caminho no grafo
     */
    public List<Vertice> caminhoDaMelodia(List<EventoMusical> melodia) {
        List<Vertice> caminho = new ArrayList<>();
        for (int i = 0; i < melodia.size(); i++) {
            if (i < memoria - 1) {
                caminho.add(null); //ainda não há notas suficientes para formar um estado
            } else {
                caminho.add(vertices.get(chave(estadoTerminandoEm(melodia, i))));
            }
        }
        return caminho;
    }

    //============================================================ 3. BUSCA EM LARGURA

    /**
     * BUSCA EM LARGURA (BFS, Breadth-First Search) a partir de um vértice
     * Retorna os vértices ALCANÇÁVEIS, na ordem em que foram visitados
     *
     * Como funciona:
     *   - Usa uma FILA (o primeiro que entra é o primeiro que sai)
     *   - Começa colocando a origem na fila
     *   - Repete: tira um vértice da fila, anota como visitado e coloca na fila
     *     todos os vizinhos dele que ainda não foram vistos
     *   - Assim os vértices são visitados "por camadas": primeiro os vizinhos
     *     da origem, depois os vizinhos dos vizinhos, e assim por diante
     *
     * No projeto: mostra que, saindo do início, o passeio consegue chegar
     * em todos os vértices (o resultado aparece no fim da aba "Lista de adjacência")
     */
    public List<Vertice> buscaEmLargura(Vertice origem) {
        List<Vertice> ordemVisita = new ArrayList<>();
        if (origem == null) {
            return ordemVisita;
        }
        Set<Vertice> visitados = new HashSet<>();  //para não visitar o mesmo vértice duas vezes
        Queue<Vertice> fila = new ArrayDeque<>();  //a fila da BFS

        visitados.add(origem);
        fila.add(origem);

        while (!fila.isEmpty()) {
            Vertice v = fila.poll();               //tira o primeiro da fila
            ordemVisita.add(v);
            for (Aresta aresta : v.getAdjacentes()) {
                Vertice vizinho = aresta.getDestino();
                if (visitados.add(vizinho)) {      //add retorna false se já tinha sido visitado
                    fila.add(vizinho);             //vizinho novo: entra no fim da fila
                }
            }
        }
        return ordemVisita;
    }

    //============================================================ TEXTOS DAS ABAS

    /**
     * Monta o texto da aba "Lista de adjacência": para cada vértice, as arestas
     * que saem dele com a probabilidade e o peso. No final, o resultado da BFS
     *
     * Exemplo de linha:
     *   Sol4 -> Sol4 40% (peso 4)  |  Lá4 20% (peso 2)  |  Fá4 40% (peso 4)
     */
    public String descreverListaAdjacencia() {
        StringBuilder sb = new StringBuilder();
        sb.append("LISTA DE ADJACÊNCIA — memória ").append(memoria)
          .append(" (vértice -> próximos vértices possíveis e probabilidade)\n\n");

        //Descobre o nome de vértice mais comprido, para alinhar as colunas
        int largura = 0;
        for (Vertice v : vertices.values()) {
            largura = Math.max(largura, v.toString().length());
        }
        for (Vertice v : vertices.values()) {
            sb.append(String.format("%-" + largura + "s", v)).append(" -> ");
            List<String> partes = new ArrayList<>();
            for (Aresta a : v.getAdjacentes()) {
                partes.add(String.format("%s %.0f%% (peso %d)", a.getDestino(), v.probabilidade(a) * 100, a.getPeso()));
            }
            sb.append(String.join("  |  ", partes)).append('\n');
        }

        sb.append("\nBUSCA EM LARGURA a partir de ").append(verticeInicial).append(": ");
        List<String> nomes = new ArrayList<>();
        for (Vertice v : buscaEmLargura(verticeInicial)) {
            nomes.add(v.toString());
        }
        sb.append(String.join(", ", nomes));
        sb.append(String.format("%n(%d de %d vértices alcançáveis)%n", nomes.size(), vertices.size()));
        return sb.toString();
    }

    /**
     * Monta o texto da aba "Matriz de probabilidades" (MATRIZ DE ADJACÊNCIA)
     *   - Linha  = vértice atual
     *   - Coluna = próximo vértice
     *   - Célula = probabilidade de ir da linha para a coluna ("." = não existe aresta)
     * Cada linha soma 1.00 (100%). A matriz é só para visualização: o grafo
     * fica guardado como lista de adjacência
     */
    public String descreverMatriz() {
        int largura = 5;
        for (Vertice v : vertices.values()) {
            largura = Math.max(largura, v.rotuloCurto().length() + 1);
        }
        String col = "%" + largura + "s";

        StringBuilder sb = new StringBuilder();
        sb.append("MATRIZ DE PROBABILIDADES (linha = vértice atual, coluna = próximo vértice)\n\n");
        //Cabeçalho com o nome de cada coluna
        sb.append(String.format("%-" + largura + "s", ""));
        for (Vertice coluna : vertices.values()) {
            sb.append(String.format(col, coluna.rotuloCurto()));
        }
        sb.append('\n');
        //Uma linha para cada vértice; uma célula para cada possível destino
        for (Vertice linha : vertices.values()) {
            sb.append(String.format("%-" + largura + "s", linha.rotuloCurto()));
            for (Vertice coluna : vertices.values()) {
                Aresta a = linha.buscarAresta(coluna);
                sb.append(String.format(col, a == null ? "." : String.format("%.2f", linha.probabilidade(a))));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    //---------------------------------------------------------------- CONSULTAS

    public Collection<Vertice> getVertices() {
        return vertices.values();
    }

    public Vertice getVerticeInicial() {
        return verticeInicial;
    }

    public boolean estaVazio() {
        return vertices.isEmpty();
    }

    /** Quantidade de vértices (bolinhas) do grafo */
    public int numeroVertices() {
        return vertices.size();
    }

    /** Quantidade de arestas (setas): a soma do grau de saída de todos os vértices */
    public int numeroArestas() {
        int total = 0;
        for (Vertice v : vertices.values()) {
            total += v.getGrauSaida();
        }
        return total;
    }
}
