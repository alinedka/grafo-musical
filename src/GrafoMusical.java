import java.util.ArrayDeque;     //fila rapida, usada na busca em largura
import java.util.ArrayList;      //lista que cresce sozinha
import java.util.Collection;     //"colecao" generica de objetos (usada em getVertices)
import java.util.HashSet;        //conjunto sem repeticao, usado para marcar os visitados da bfs
import java.util.LinkedHashMap;  //mapa (chave -> valor) que lembra a ordem de insercao
import java.util.List;           //o "tipo" lista
import java.util.Map;            //o "tipo" mapa
import java.util.Queue;          //o "tipo" fila (primeiro que entra e o primeiro que sai)
import java.util.Random;         //gerador de numeros aleatorios (os sorteios)
import java.util.Set;            //o "tipo" conjunto

/**
 * ============================================================================
 *  grafo musical: o coracao do projeto
 * ============================================================================
 *
 * tipo de grafo:
 *   grafo dirigido e ponderado, com lacos e ciclos
 *     - vertices (classe Vertice): os estados, isto e, as ultimas n notas tocadas
 *     - arestas  (classe Aresta):  "do estado a a musica foi para o estado b",
 *                                  com peso = quantas vezes isso aconteceu
 *     - laco: seta que sai e volta para a mesma nota (ex.: sol -> sol, nota repetida)
 *     - ciclo: caminho que volta para uma nota ja visitada
 *   como a proxima nota e sorteada so com base no estado atual, este grafo
 *   tambem e uma cadeia de markov
 *
 * memoria (ordem da cadeia de markov):
 *   n = quantas notas formam cada vertice (a "janelinha" que desliza pela musica)
 *     - memoria 1: o vertice e uma nota so. o grafo "esquece" tudo, menos a
 *       ultima nota, entao a musica gerada e criativa, mas pouco parecida
 *       com a original (tem muitos pontos de escolha)
 *       brilha brilha: 6 vertices e 14 arestas
 *     - memoria 4: o vertice sao as 4 ultimas notas. o grafo sabe em que parte
 *       da musica esta, quase todas as setas viram 100% e a musica gerada fica
 *       bem parecida com a original
 *       brilha brilha: 24 vertices, 27 arestas e so 2 pontos de escolha
 *
 * representacao: lista de adjacencia
 *   o mapa "vertices" encontra cada vertice pelo seu estado, e cada vertice
 *   guarda a lista das arestas que saem dele (Vertice.adjacentes)
 *   escolhemos lista e nao matriz porque o grafo e esparso (cada nota se liga
 *   a poucas outras) e a operacao mais usada e "ver os vizinhos do vertice atual"
 *   ex.: na memoria 1 a matriz teria 6 x 6 = 36 celulas, mas so 14 setas existem
 *
 * as tres operacoes principais:
 *   1. treinar()        -> monta o grafo a partir de uma melodia
 *   2. compor()         -> passeio aleatorio ponderado: anda pelo grafo
 *                          sorteando as arestas e gera uma musica nova
 *   3. buscaEmLargura() -> bfs: descobre quais vertices sao alcancaveis
 * ============================================================================
 */
public class GrafoMusical {

    //---------------------------------------------------------------- atributos
    //atributos sao as variaveis que pertencem ao grafo e duram enquanto ele existir

    //todos os vertices do grafo. a chave e o estado em texto (ex.: "[60, 67]")
    //e o valor e o objeto Vertice. assim, achar o vertice de um estado e rapido
    //LinkedHashMap mantem os vertices na ordem em que apareceram na melodia,
    //o que deixa a lista de adjacencia e o desenho na ordem da musica
    private final Map<String, Vertice> vertices = new LinkedHashMap<>();

    //melodias ja usadas no treino. guardamos para poder reconstruir o grafo
    //quando o usuario troca a memoria (setMemoria)
    //e uma lista de listas: cada item e uma melodia inteira (lista de notas)
    private final List<List<EventoMusical>> melodiasTreinadas = new ArrayList<>();

    private int memoria = 1;                              //quantas notas formam um vertice (tamanho da janelinha)
    private Vertice verticeInicial;                       //onde o passeio comeca (1o estado da 1a melodia)
    private double duracaoInicial = 1.0;                  //duracao da nota do vertice inicial (em tempos)
    private List<EventoMusical> notasIniciais = List.of(); //notas que vem antes do 1o estado (memoria > 1); List.of() = lista vazia
    private Vertice verticeFinal;                         //ultimo estado da 1a melodia: onde a musica "termina"
    private int notaFinal = -1;                           //ultima nota da 1a melodia (-1 = ainda nao definida)
    private final Random random = new Random();           //gerador de numeros aleatorios dos sorteios

    //---------------------------------------------------------------- controle

    /**
     * apaga todos os vertices, arestas e melodias treinadas (grafo vazio)
     * volta todos os atributos para o valor do comeco
     */
    public void limpar() {
        vertices.clear();          //remove todos os vertices (e junto as setas, que ficam dentro deles)
        melodiasTreinadas.clear(); //esquece as melodias usadas no treino
        verticeInicial = null;     //null = "nenhum vertice"
        duracaoInicial = 1.0;
        notasIniciais = List.of(); //lista vazia
        verticeFinal = null;
        notaFinal = -1;
    }

    /**
     * muda a memoria do grafo e o reconstroi do zero com as mesmas melodias
     * precisa reconstruir porque, com outra memoria, os vertices sao outros
     * (ex.: "sol4" na memoria 1 vira varios vertices "[... sol4]" na memoria 4)
     *
     * @param novaMemoria quantas notas cada vertice vai ter (1 a 5 na tela)
     */
    public void setMemoria(int novaMemoria) {
        //memoria menor que 1 nao faz sentido (vertice sem nota): avisa o erro
        if (novaMemoria < 1) {
            throw new IllegalArgumentException("A memória deve ser pelo menos 1");
        }
        memoria = novaMemoria;
        List<List<EventoMusical>> copia = new ArrayList<>(melodiasTreinadas); //guarda antes de limpar
        limpar(); //apaga o grafo antigo (inclusive melodiasTreinadas, por isso a copia acima)
        //treina de novo cada melodia guardada, agora com a memoria nova
        for (List<EventoMusical> melodia : copia) {
            treinar(melodia);
        }
    }

    /** devolve a memoria atual (quantas notas formam um vertice) */
    public int getMemoria() {
        return memoria;
    }

    //---------------------------------------------------------------- auxiliares

    /**
     * transforma um estado em texto para usar como chave do mapa. ex.: [60, 67] -> "[60, 67]"
     * "static" = o metodo nao depende de nenhum grafo especifico, so do estado recebido
     */
    private static String chave(List<Integer> estado) {
        return estado.toString(); //o toString de uma lista ja gera o texto com colchetes
    }

    /**
     * devolve o vertice do estado informado. se ele ainda nao existe, cria
     * computeIfAbsent faz as duas coisas numa linha so: procura no mapa e,
     * se nao achar, cria um Vertice novo e ja guarda no mapa
     * isso garante que cada estado tem um unico vertice no grafo
     * (ex.: as 10 vezes que o sol aparece caem todas no mesmo vertice sol4)
     *
     * "c -> new Vertice(estado)" e uma lambda: uma funcao curta que so roda
     * quando a chave nao foi encontrada
     */
    private Vertice obterOuCriarVertice(List<Integer> estado) {
        return vertices.computeIfAbsent(chave(estado), c -> new Vertice(estado));
    }

    /**
     * monta o estado que termina na posicao "fim" da melodia,
     * ou seja, as "memoria" notas que vao ate a posicao fim (a janelinha)
     *
     * exemplo com memoria 3 e melodia [do, do, sol, sol, la]:
     *   estadoTerminandoEm(melodia, 3) = [do, sol, sol]   (posicoes 1, 2 e 3)
     *
     * Math.floorMod trata a melodia como circular: se a conta der uma posicao
     * negativa, ela "da a volta" e pega notas do final da melodia
     * ex.: com 42 notas, floorMod(-1, 42) = 41 (a ultima nota)
     *
     * @param melodia a musica inteira
     * @param fim     a posicao da ultima nota da janelinha
     * @return a lista com as notas (numeros midi) do estado
     */
    private List<Integer> estadoTerminandoEm(List<EventoMusical> melodia, int fim) {
        int n = melodia.size();                            //quantas notas a melodia tem
        List<Integer> estado = new ArrayList<>(memoria);   //lista vazia com espaco para "memoria" notas
        //j comeca em memoria-1 e desce ate 0, entao a posicao fim-j vai
        //da nota mais antiga ate a mais recente (a propria posicao fim)
        //ex.: memoria 3 e fim 3 -> posicoes 1, 2, 3
        for (int j = memoria - 1; j >= 0; j--) {          //da nota mais antiga para a mais recente
            estado.add(melodia.get(Math.floorMod(fim - j, n)).nota()); //pega a nota dessa posicao (so o numero midi)
        }
        return estado;
    }

    //============================================================ 1. treinar

    /**
     * treinamento: percorre a melodia e liga cada estado ao estado seguinte,
     * montando o grafo
     *
     * a base de tudo e a musica original: so existe seta entre dois estados
     * que vieram um depois do outro na melodia
     *
     * exemplo com memoria 1, melodia "do re mi do re":
     *   arestas do->re (peso 2), re->mi (peso 1), mi->do (peso 1)
     *   (e re->do, que liga o fim ao comeco, porque a melodia e circular)
     *
     * exemplo com memoria 2, mesma melodia:
     *   [do re]->[re mi], [re mi]->[mi do], [mi do]->[do re]
     *
     * a melodia e tratada como circular: depois da ultima nota ela recomeca
     * assim todo vertice tem pelo menos uma aresta de saida e o passeio
     * aleatorio nunca fica "preso" num vertice sem saida
     *
     * cada aresta tambem guarda a duracao da nota de chegada (o ritmo)
     *
     * @param melodia a lista de notas (com duracao) da musica de exemplo
     */
    public void treinar(List<EventoMusical> melodia) {
        if (melodia.size() < 2) {
            return; //com menos de 2 notas nao existe nenhuma transicao: sai sem fazer nada
        }
        melodiasTreinadas.add(List.copyOf(melodia)); //guarda uma copia para o setMemoria poder retreinar
        int n = melodia.size();                      //quantidade de notas (brilha brilha: 42)

        //passo 1: cria (ou encontra) o vertice de cada posicao da melodia
        //estados[i] = o estado formado pelas notas que terminam na posicao i + memoria - 1
        //se o mesmo estado aparece duas vezes na musica, obterOuCriarVertice
        //devolve o mesmo vertice, e e assim que as repeticoes se juntam
        //"Vertice[]" e um vetor (array) de tamanho fixo n
        Vertice[] estados = new Vertice[n];
        //percorre todas as posicoes da melodia, de 0 ate n-1, e para quando i chega em n
        for (int i = 0; i < n; i++) {
            estados[i] = obterOuCriarVertice(estadoTerminandoEm(melodia, i + memoria - 1));
        }

        //passo 2: liga cada estado ao seguinte (cria a aresta ou soma 1 no peso)
        //o "% n" (resto da divisao) faz o ultimo estado ligar de volta ao primeiro (ciclo)
        //ex.: com n = 42, quando i = 41 o proximo e (41 + 1) % 42 = 0
        //o laco passa uma vez por cada estado e termina quando a musica acaba (i chega em n)
        for (int i = 0; i < n; i++) {
            int proximo = (i + 1) % n; //posicao do estado que vem depois
            //duracao da nota que soa ao chegar no proximo estado (a ultima nota dele)
            double duracaoChegada = melodia.get((proximo + memoria - 1) % n).duracao();
            estados[i].adicionarTransicao(estados[proximo], duracaoChegada); //cria a seta ou soma 1 no peso
        }

        //passo 3: so na primeira melodia, guarda onde a musica comeca e termina
        //(se verticeInicial ja tem valor, outra melodia ja foi treinada antes)
        if (verticeInicial == null) {
            verticeInicial = estados[0];                             //o passeio sempre comeca aqui
            duracaoInicial = melodia.get((memoria - 1) % n).duracao(); //ritmo da 1a nota tocada pelo passeio
            //o estado que termina na ultima nota e o "final" da musica
            //o compor() tenta terminar nele, para a musica nao parar no meio de uma frase
            verticeFinal = vertices.get(chave(estadoTerminandoEm(melodia, n - 1)));
            notaFinal = melodia.get(n - 1).nota(); //a ultima nota, usada se o compor bater o limite
            //com memoria > 1, o primeiro vertice ja "contem" notas anteriores
            //(ex.: memoria 4, o 1o vertice tem as notas 1, 2, 3 e 4, mas so toca a 4)
            //as notas 1 a 3 precisam ser tocadas antes, no comeco da composicao,
            //senao elas sumiriam da musica
            List<EventoMusical> iniciais = new ArrayList<>();
            //copia as memoria-1 primeiras notas (memoria 1: o laco nao roda nenhuma vez)
            for (int j = 0; j < memoria - 1; j++) {
                iniciais.add(melodia.get(j % n));
            }
            notasIniciais = iniciais;
        }
    }

    //============================================================ 2. compor

    /**
     * sorteio pelo metodo da roleta: escolhe por qual aresta seguir
     * cada aresta ocupa uma "fatia" de tamanho igual ao seu peso
     *
     * exemplo: vertice sol com arestas sol (peso 4), la (peso 2), fa (peso 4)
     *   total = 10, entao sorteamos um numero de 0 a 9 (um dado de 10 lados):
     *
     *     numero:   0 1 2 3 | 4 5 | 6 7 8 9      (na ordem da lista)
     *     aresta:   sol     | la  | fa
     *
     *   passo a passo com o sorteio = 5:
     *     sol: acumulado = 4.  5 < 4? nao, continua
     *     la:  acumulado = 6.  5 < 6? sim -> escolhe la
     *
     *   quem tem mais peso ocupa mais numeros, e por isso tem mais chance
     *
     * o tamanho do dado muda conforme a nota: e sempre o pesoTotalSaida dela
     * (ex.: no do da brilha brilha, total 6, sorteio de 0 a 5)
     *
     * @param atual o vertice onde o passeio esta agora
     * @return a aresta (seta) sorteada
     */
    public Aresta sortearAresta(Vertice atual) {
        //random.nextInt(x) devolve um numero aleatorio de 0 ate x-1, todos com a mesma chance
        int sorteio = random.nextInt(atual.getPesoTotalSaida()); //numero de 0 ate total-1
        int acumulado = 0; //onde termina a fatia atual; comeca em 0 (nenhuma fatia somada ainda)
        //percorre as setas na ordem da lista de adjacencia, somando os pesos
        //para assim que o numero sorteado cair dentro de uma fatia (return)
        for (Aresta aresta : atual.getAdjacentes()) {
            acumulado += aresta.getPeso();   //onde termina a "fatia" desta aresta ("+=" soma e guarda)
            if (sorteio < acumulado) {       //o numero esta antes do fim desta fatia?
                return aresta;               //o numero caiu na fatia desta aresta
            }
        }
        //so chegaria aqui se o vertice nao tivesse arestas, o que o treino circular impede
        throw new IllegalStateException("Vértice sem arestas de saída: " + atual);
    }

    /**
     * composicao: passeio aleatorio ponderado pelo grafo
     *
     *   1. comeca no vertice inicial
     *   2. toca a nota do vertice atual
     *   3. sorteia uma aresta de saida (sortearAresta) e vai para o destino dela
     *   4. repete
     *
     * a cada passo sao 2 sorteios: qual seta seguir e o ritmo da proxima nota
     *
     * a sequencia de vertices visitados e a musica nova. como cada passo e
     * sorteado, cada composicao sai diferente, mas so usa transicoes que
     * existiam na musica original (por isso mantem o "estilo" dela)
     * o grafo define as possibilidades; o sorteio decide o caminho
     *
     * @param quantidadeNotas quantos passos o passeio deve dar (no minimo)
     * @return a composicao: as notas e o vertice de cada nota (para o desenho)
     */
    public Composicao compor(int quantidadeNotas) {
        List<EventoMusical> musica = new ArrayList<>();  //as notas que serao tocadas
        List<Vertice> caminho = new ArrayList<>();       //o vertice de cada nota (para o desenho)
        if (vertices.isEmpty()) {
            return new Composicao(musica, caminho);      //grafo vazio: nada para compor
        }

        //notas anteriores ao primeiro estado (so existem com memoria > 1)
        //sao copiadas da musica original, sem sorteio
        //nao tem vertice proprio no caminho, por isso entra null
        for (EventoMusical ev : notasIniciais) { //percorre cada nota inicial guardada no treino
            musica.add(ev);
            caminho.add(null);
        }

        Vertice atual = verticeInicial;    //o passeio comeca no inicio da musica
        double duracao = duracaoInicial;   //a 1a nota nao e sorteada: usa o ritmo original
        int passos = 0;                    //quantas notas o passeio ja tocou

        //"while (true)" repete para sempre; quem encerra o laco e o "break" la dentro
        while (true) {
            //toca a nota do vertice atual
            musica.add(new EventoMusical(atual.getNota(), duracao)); //a ultima nota do estado, com o ritmo sorteado
            caminho.add(atual);                                      //anota o vertice para a animacao
            passos++;                                                //mais uma nota tocada

            //condicao de parada: depois de dar a quantidade de passos pedida,
            //continua ate chegar ao vertice final da melodia original, para a
            //musica terminar "resolvida". para nunca virar um laco infinito,
            //ha um limite de 64 passos extras
            //"&&" = "e" (as duas partes precisam ser verdade); "||" = "ou"
            //ex.: pediu 40 e na 40a nota nao esta no final -> continua sorteando
            //(por isso 40 notas pedidas podem virar 48)
            if (passos >= quantidadeNotas
                    && (atual == verticeFinal || passos >= quantidadeNotas + 64)) {
                break; //sai do while
            }

            //sorteia a aresta e anda pelo grafo
            Aresta aresta = sortearAresta(atual);     //qual seta seguir (roleta)
            duracao = aresta.sortearDuracao(random);  //o ritmo da proxima nota
            atual = aresta.getDestino();              //anda para o vertice de destino
        }

        //se bateu o limite sem chegar ao vertice final, termina pelo menos na nota final
        //(caminho.size() - 1 e a posicao do ultimo item da lista)
        if (caminho.get(caminho.size() - 1) != verticeFinal) {
            musica.add(new EventoMusical(notaFinal, 2.0)); //acrescenta a nota final com 2 tempos
            caminho.add(null);                             //ela nao veio de um vertice do passeio
        }

        //a ultima nota soa um pouco mais longa (pelo menos 2 tempos), para encerrar
        //Math.max devolve o maior dos dois valores: se ja durava mais que 2, mantem
        int ultima = musica.size() - 1; //posicao da ultima nota
        musica.set(ultima, new EventoMusical(musica.get(ultima).nota(), Math.max(2.0, musica.get(ultima).duracao())));
        return new Composicao(musica, caminho);
    }

    /**
     * para cada nota de uma melodia, informa em qual vertice do grafo ela esta
     * e usado para animar o desenho quando tocamos a melodia original:
     * assim da para ver que a musica original tambem e um caminho no grafo
     *
     * @param melodia a musica original
     * @return o vertice de cada nota (null nas primeiras notas, quando memoria > 1)
     */
    public List<Vertice> caminhoDaMelodia(List<EventoMusical> melodia) {
        List<Vertice> caminho = new ArrayList<>();
        //percorre cada posicao da melodia, do inicio ao fim
        for (int i = 0; i < melodia.size(); i++) {
            if (i < memoria - 1) {
                caminho.add(null); //ainda nao ha notas suficientes para formar um estado
            } else {
                caminho.add(vertices.get(chave(estadoTerminandoEm(melodia, i)))); //vertice da janelinha que termina aqui
            }
        }
        return caminho;
    }

    //============================================================ 3. busca em largura

    /**
     * busca em largura (bfs, breadth-first search) a partir de um vertice
     * retorna os vertices alcancaveis, na ordem em que foram visitados
     *
     * como funciona:
     *   - usa uma fila (o primeiro que entra e o primeiro que sai, como fila de banco)
     *   - comeca colocando a origem na fila
     *   - repete: tira um vertice da fila, anota como visitado e coloca na fila
     *     todos os vizinhos dele que ainda nao foram vistos
     *   - assim os vertices sao visitados "por camadas": primeiro os vizinhos
     *     da origem, depois os vizinhos dos vizinhos, e assim por diante
     *
     * exemplo (brilha brilha, memoria 1, saindo do do4):
     *   camada 0: do4 | camada 1: sol4 | camada 2: la4, fa4 | camada 3: mi4 | camada 4: re4
     *   ordem de visita: do4, sol4, la4, fa4, mi4, re4 (todas as 6 notas)
     *
     * no projeto: mostra que, saindo do inicio, o passeio consegue chegar
     * em todos os vertices (o resultado aparece no fim da aba "lista de adjacencia")
     *
     * @param origem o vertice de onde a busca comeca
     * @return os vertices alcancaveis, na ordem da visita
     */
    public List<Vertice> buscaEmLargura(Vertice origem) {
        List<Vertice> ordemVisita = new ArrayList<>(); //resultado: ordem em que os vertices foram visitados
        if (origem == null) {
            return ordemVisita; //grafo ainda nao treinado: devolve a lista vazia
        }
        Set<Vertice> visitados = new HashSet<>();  //para nao visitar o mesmo vertice duas vezes
        Queue<Vertice> fila = new ArrayDeque<>();  //a fila da bfs

        visitados.add(origem); //a origem ja conta como vista
        fila.add(origem);      //e e a primeira a entrar na fila

        //repete enquanto ainda houver vertice esperando na fila
        //para quando a fila esvazia: ai nao ha mais nenhum vertice novo para alcancar
        while (!fila.isEmpty()) {
            Vertice v = fila.poll();               //tira o primeiro da fila (poll = tira e devolve)
            ordemVisita.add(v);                    //anota a visita
            //olha cada seta que sai de v para descobrir os vizinhos
            for (Aresta aresta : v.getAdjacentes()) {
                Vertice vizinho = aresta.getDestino();
                //Set.add devolve true se o item era novo e false se ja estava no conjunto
                //entao este if testa e marca como visitado ao mesmo tempo
                if (visitados.add(vizinho)) {      //add retorna false se ja tinha sido visitado
                    fila.add(vizinho);             //vizinho novo: entra no fim da fila
                }
            }
        }
        return ordemVisita;
    }

    //============================================================ textos das abas

    /**
     * monta o texto da aba "lista de adjacencia": para cada vertice, as arestas
     * que saem dele com a probabilidade e o peso. no final, o resultado da bfs
     *
     * exemplo de linha:
     *   sol4 -> sol4 40% (peso 4)  |  la4 20% (peso 2)  |  fa4 40% (peso 4)
     *
     * StringBuilder e um "montador de texto": vamos juntando pedacos com append
     * e no fim transformamos tudo em uma String so (toString)
     */
    public String descreverListaAdjacencia() {
        StringBuilder sb = new StringBuilder();
        sb.append("LISTA DE ADJACÊNCIA — memória ").append(memoria)
          .append(" (vértice -> próximos vértices possíveis e probabilidade)\n\n");

        //descobre o nome de vertice mais comprido, para alinhar as colunas
        int largura = 0;
        for (Vertice v : vertices.values()) { //percorre todos os vertices do grafo
            largura = Math.max(largura, v.toString().length()); //guarda o maior tamanho visto ate agora
        }
        //uma linha de texto para cada vertice
        for (Vertice v : vertices.values()) {
            //"%-" + largura + "s" escreve o nome e completa com espacos ate a largura (alinhamento)
            sb.append(String.format("%-" + largura + "s", v)).append(" -> ");
            List<String> partes = new ArrayList<>(); //o texto de cada seta deste vertice
            //percorre as setas que saem de v (a lista de adjacencia dele)
            for (Aresta a : v.getAdjacentes()) {
                //"%.0f%%" = probabilidade sem casas decimais seguida do simbolo de porcentagem
                partes.add(String.format("%s %.0f%% (peso %d)", a.getDestino(), v.probabilidade(a) * 100, a.getPeso()));
            }
            sb.append(String.join("  |  ", partes)).append('\n'); //junta as setas com " | " e pula linha
        }

        //no fim da aba, mostra a ordem de visita da bfs a partir do vertice inicial
        sb.append("\nBUSCA EM LARGURA a partir de ").append(verticeInicial).append(": ");
        List<String> nomes = new ArrayList<>();
        for (Vertice v : buscaEmLargura(verticeInicial)) { //cada vertice alcancado, na ordem da visita
            nomes.add(v.toString());
        }
        sb.append(String.join(", ", nomes));
        //mostra quantos foram alcancados de quantos existem (ex.: 6 de 6)
        sb.append(String.format("%n(%d de %d vértices alcançáveis)%n", nomes.size(), vertices.size()));
        return sb.toString();
    }

    /**
     * monta o texto da aba "matriz de probabilidades" (matriz de adjacencia)
     *   - linha  = vertice atual
     *   - coluna = proximo vertice
     *   - celula = probabilidade de ir da linha para a coluna ("." = nao existe aresta)
     * cada linha soma 1.00 (100%). a matriz e so para visualizacao: o grafo
     * fica guardado como lista de adjacencia
     * ex.: memoria 1 na brilha brilha: 6 x 6 = 36 celulas, so 14 preenchidas
     */
    public String descreverMatriz() {
        int largura = 5; //largura minima de cada coluna, em caracteres
        //aumenta a largura se algum rotulo for maior (memoria alta tem rotulos longos)
        for (Vertice v : vertices.values()) {
            largura = Math.max(largura, v.rotuloCurto().length() + 1);
        }
        String col = "%" + largura + "s"; //formato de uma coluna alinhada a direita

        StringBuilder sb = new StringBuilder();
        sb.append("MATRIZ DE PROBABILIDADES (linha = vértice atual, coluna = próximo vértice)\n\n");
        //cabecalho com o nome de cada coluna
        sb.append(String.format("%-" + largura + "s", "")); //espaco vazio no canto de cima
        for (Vertice coluna : vertices.values()) {
            sb.append(String.format(col, coluna.rotuloCurto()));
        }
        sb.append('\n');
        //uma linha para cada vertice; uma celula para cada possivel destino
        //sao dois for, um dentro do outro: linhas x colunas = todas as celulas
        for (Vertice linha : vertices.values()) {
            sb.append(String.format("%-" + largura + "s", linha.rotuloCurto())); //nome da linha
            for (Vertice coluna : vertices.values()) {
                Aresta a = linha.buscarAresta(coluna); //existe seta da linha para a coluna?
                //"condicao ? x : y" e um if curto: sem seta escreve ".", com seta escreve a probabilidade
                sb.append(String.format(col, a == null ? "." : String.format("%.2f", linha.probabilidade(a))));
            }
            sb.append('\n'); //fim da linha
        }
        return sb.toString();
    }

    //---------------------------------------------------------------- consultas

    /** devolve todos os vertices (usado pelo desenho do grafo) */
    public Collection<Vertice> getVertices() {
        return vertices.values();
    }

    /** devolve o vertice onde o passeio comeca */
    public Vertice getVerticeInicial() {
        return verticeInicial;
    }

    /** true se o grafo ainda nao tem nenhum vertice (nao foi treinado) */
    public boolean estaVazio() {
        return vertices.isEmpty();
    }

    /** quantidade de vertices (bolinhas) do grafo */
    public int numeroVertices() {
        return vertices.size();
    }

    /** quantidade de arestas (setas): a soma do grau de saida de todos os vertices */
    public int numeroArestas() {
        int total = 0;
        //percorre os vertices somando quantas setas saem de cada um
        for (Vertice v : vertices.values()) {
            total += v.getGrauSaida();
        }
        return total;
    }
}
