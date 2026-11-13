import java.util.ArrayList;   //lista que cresce sozinha
import java.util.Collections; //ferramentas para listas (usamos a versao "somente leitura")
import java.util.List;        //o "tipo" lista

/**
 * ============================================================================
 *  vertice do grafo
 * ============================================================================
 *
 * papel no projeto:
 *   cada bolinha que aparece no desenho do grafo e um objeto desta classe
 *
 * o que um vertice representa:
 *   um estado musical, ou seja, "as ultimas notas que foram tocadas"
 *   quantas notas entram no estado depende da memoria do grafo:
 *
 *     - memoria 1: o estado tem 1 nota.  vertice = "sol4"
 *     - memoria 2: o estado tem 2 notas. vertice = "[sol4 la4]"
 *                  (quer dizer: "acabei de tocar sol e depois la")
 *     - memoria 4: o estado tem 4 notas. vertice = "[do4 sol4 sol4 la4]"
 *
 *   pense no estado como uma janelinha que desliza pela musica: a cada passo
 *   ela perde a nota mais antiga e ganha uma nova no fim
 *
 *   a nota que de fato soa quando o passeio chega neste vertice
 *   e sempre a ultima nota do estado (metodo getNota()), porque as outras
 *   ja foram tocadas nos passos anteriores
 *
 * lista de adjacencia:
 *   cada vertice guarda a lista das arestas (setas) que saem dele
 *   juntando as listas de todos os vertices, temos a lista de adjacencia
 *   do grafo inteiro, que e a forma como o grafo fica guardado na memoria
 *
 *   exemplo (brilha brilha, memoria 1):
 *     sol4 -> [ sol4 (peso 4), la4 (peso 2), fa4 (peso 4) ]
 * ============================================================================
 */
public class Vertice {

    //as notas (em numero midi) que formam o estado deste vertice
    //numero midi e como o java identifica cada nota: 60 = do4, 62 = re4, 67 = sol4...
    //ex.: memoria 2, estado [67, 69] = [sol4, la4]
    //e "final" porque o estado de um vertice nunca muda depois de criado
    private final List<Integer> estado;

    //lista de adjacencia deste vertice: todas as arestas que saem dele
    //usamos ArrayList porque cada nota se liga a poucas outras (grafo esparso),
    //entao percorrer a lista e rapido e nao desperdica memoria
    //a ordem da lista e a ordem em que cada seta apareceu pela primeira vez na musica
    //(essa ordem define as "fatias" da roleta no sorteio)
    private final List<Aresta> adjacentes = new ArrayList<>();

    //soma dos pesos de todas as arestas de saida
    //nao e calculada no final: comeca em 0 e sobe 1 em cada adicionarTransicao
    //no fim do treino, e igual a quantas vezes esta nota apareceu na musica
    //ex.: o sol aparece 10 vezes na brilha brilha, entao pesoTotalSaida = 10
    //guardamos pronta para nao precisar somar toda vez que formos sortear
    //ou calcular uma probabilidade (probabilidade = peso / pesoTotalSaida)
    //e o "tamanho do dado" no sorteio: random.nextInt(pesoTotalSaida)
    private int pesoTotalSaida;

    /**
     * construtor: cria um vertice para o estado informado
     * e chamado pelo GrafoMusical (obterOuCriarVertice) na primeira vez
     * que um estado aparece na musica
     *
     * List.copyOf faz uma copia que nao pode ser alterada, garantindo
     * que ninguem de fora mude o estado deste vertice sem querer
     *
     * @param estado as notas (numeros midi) que formam este vertice
     */
    public Vertice(List<Integer> estado) {
        this.estado = List.copyOf(estado); //guarda uma copia protegida do estado
    }

    /**
     * registra a transicao "deste vertice para o vertice destino"
     * e chamado durante o treinamento, uma vez para cada par de estados
     * seguidos na melodia
     *
     *   - se a aresta ainda nao existe: cria uma aresta nova (peso comeca em 0)
     *     e coloca na lista de adjacencia
     *   - em seguida (existindo ou nao antes): soma 1 no peso da aresta
     *
     * a base para criar a seta e a propria musica: so existe seta entre duas
     * notas que apareceram uma depois da outra na melodia original
     *
     * exemplo: na brilha brilha, "sol -> fa" acontece 4 vezes,
     * entao este metodo e chamado 4 vezes; na 1a vez cria a seta,
     * nas outras 3 so soma no peso, que termina em 4
     *
     * @param destino o vertice para onde a musica foi
     * @param duracao duracao da nota de chegada nesta ocorrencia (o ritmo)
     */
    public void adicionarTransicao(Vertice destino, double duracao) {
        Aresta aresta = buscarAresta(destino);   //ja existe uma seta para esse destino?
        if (aresta == null) {                    //null = "nada": a seta ainda nao existe
            aresta = new Aresta(this, destino);  //cria a seta saindo "deste" vertice (this) para o destino
            adjacentes.add(aresta);              //e adiciona no fim da lista de adjacencia
        }
        aresta.registrarOcorrencia(duracao);     //peso + 1 e guarda a duracao
        pesoTotalSaida++;                        //o total de saida tambem aumenta 1
    }

    /**
     * procura, na lista de adjacencia, a aresta que vai ate o destino informado
     * retorna null se ainda nao existe seta deste vertice para la
     *
     * comparamos com "==" (mesmo objeto) porque cada estado tem
     * um unico objeto Vertice no grafo
     *
     * tambem e usado para montar a matriz de probabilidades (descreverMatriz)
     *
     * @param destino o vertice que queremos achar entre os vizinhos
     * @return a aresta ate o destino, ou null se ela nao existir
     */
    public Aresta buscarAresta(Vertice destino) {
        //percorre as setas que saem deste vertice, uma por uma
        //para assim que acha (return) ou quando a lista acaba
        for (Aresta a : adjacentes) {
            if (a.getDestino() == destino) { //esta seta aponta para o destino procurado?
                return a;                    //achou: devolve e sai do metodo na hora
            }
        }
        return null; //percorreu tudo e nao achou: a seta nao existe
    }

    /**
     * probabilidade de o passeio seguir pela aresta informada, entre 0.0 e 1.0
     *
     *   probabilidade = peso da aresta / soma dos pesos de todas as saidas
     *
     * exemplo: sol -> fa tem peso 4 e o sol tem total 10, entao 4 / 10 = 0.40 (40%)
     * o "(double)" e necessario: sem ele, 4 / 10 entre inteiros daria 0
     *
     * usado so para mostrar na tela (lista, matriz e desenho); o sorteio
     * em si usa os pesos inteiros
     *
     * @param aresta uma das setas que saem deste vertice
     * @return a chance de seguir por ela (0.40 = 40%)
     */
    public double probabilidade(Aresta aresta) {
        if (pesoTotalSaida == 0) {
            return 0.0; //vertice sem saidas: evita divisao por zero
        }
        return (double) aresta.getPeso() / pesoTotalSaida; //peso da seta dividido pelo total
    }

    /**
     * a nota que soa quando o passeio chega neste vertice: a ultima do estado
     * ex.: vertice [sol4 la4] toca o la4
     *
     * @return o numero midi da ultima nota do estado
     */
    public int getNota() {
        //as posicoes de uma lista comecam em 0, entao a ultima e tamanho - 1
        return estado.get(estado.size() - 1);
    }

    /** devolve todas as notas do estado (ex.: [60, 67] = [do4, sol4]) */
    public List<Integer> getEstado() {
        return estado;
    }

    /**
     * devolve a lista de adjacencia "somente leitura", para que outras classes
     * possam percorre-la, mas nao possam adicionar ou remover arestas por fora
     * (Collections.unmodifiableList da erro se alguem tentar mudar a lista)
     */
    public List<Aresta> getAdjacentes() {
        return Collections.unmodifiableList(adjacentes);
    }

    /** devolve a soma dos pesos das setas de saida (o "tamanho do dado" do sorteio) */
    public int getPesoTotalSaida() {
        return pesoTotalSaida;
    }

    /**
     * grau de saida = quantidade de arestas diferentes que saem deste vertice
     *
     *   - grau 1: so existe um caminho (seta de 100%, cinza no desenho)
     *   - grau maior que 1: e um ponto de escolha, onde o passeio sorteia
     *     (setas roxas no desenho)
     *
     * ex.: sol4 na memoria 1 tem grau 3 (vai para sol4, la4 e fa4)
     */
    public int getGrauSaida() {
        return adjacentes.size(); //cada item da lista e uma seta diferente
    }

    /**
     * nome curto, usado no cabecalho da matriz. ex.: g4 ou c4-g4
     * usa as letras do padrao internacional (c = do, d = re, e = mi, f = fa, g = sol, a = la)
     */
    public String rotuloCurto() {
        List<String> nomes = new ArrayList<>(); //vai guardar o nome de cada nota do estado
        //percorre cada nota do estado e transforma o numero midi em nome (67 -> g4)
        for (int nota : estado) {
            nomes.add(Nota.nome(nota));
        }
        return String.join("-", nomes); //junta os nomes com hifen: c4 e g4 viram c4-g4
    }

    /**
     * nome do vertice em portugues, usado na lista de adjacencia e na tela
     * ex.: sol4 (memoria 1) ou [do4 sol4] (memoria 2 ou mais)
     *
     * "@Override" avisa que estamos substituindo o toString padrao do java,
     * que e o metodo usado sempre que um objeto vira texto
     */
    @Override
    public String toString() {
        //memoria 1: o estado tem uma nota so, entao mostra so o nome dela
        if (estado.size() == 1) {
            return Nota.nomePt(estado.get(0));
        }
        //memoria 2 ou mais: mostra todas as notas entre colchetes
        List<String> nomes = new ArrayList<>();
        for (int nota : estado) { //percorre as notas do estado, da mais antiga para a mais recente
            nomes.add(Nota.nomePt(nota));
        }
        return "[" + String.join(" ", nomes) + "]";
    }
}
