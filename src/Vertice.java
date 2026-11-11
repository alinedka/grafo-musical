import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ============================================================================
 *  VÉRTICE DO GRAFO
 * ============================================================================
 *
 * PAPEL NO PROJETO:
 *   Cada bolinha que aparece no desenho do grafo é um objeto desta classe
 *
 * O QUE UM VÉRTICE REPRESENTA:
 *   Um ESTADO musical, ou seja, "as últimas notas que foram tocadas"
 *   Quantas notas entram no estado depende da MEMÓRIA do grafo:
 *
 *     - Memória 1: o estado tem 1 nota.  Vértice = "Sol4"
 *     - Memória 2: o estado tem 2 notas. Vértice = "[Sol4 Lá4]"
 *                  (quer dizer: "acabei de tocar Sol e depois Lá")
 *     - Memória 4: o estado tem 4 notas. Vértice = "[Dó4 Sol4 Sol4 Lá4]"
 *
 *   A nota que de fato SOA quando o passeio chega neste vértice
 *   é sempre a ÚLTIMA nota do estado (método getNota())
 *
 * LISTA DE ADJACÊNCIA:
 *   Cada vértice guarda a lista das arestas (setas) que SAEM dele
 *   Juntando as listas de todos os vértices, temos a LISTA DE ADJACÊNCIA
 *   do grafo inteiro, que é a forma como o grafo fica guardado na memória
 *
 *   Exemplo (Brilha Brilha, memória 1):
 *     Sol4 -> [ Sol4 (peso 4), Lá4 (peso 2), Fá4 (peso 4) ]
 * ============================================================================
 */
public class Vertice {

    //As notas (em número MIDI) que formam o estado deste vértice
    //Ex.: memória 2, estado [67, 69] = [Sol4, Lá4]
    //É "final" porque o estado de um vértice nunca muda depois de criado
    private final List<Integer> estado;

    //LISTA DE ADJACÊNCIA deste vértice: todas as arestas que saem dele
    //Usamos ArrayList porque cada nota se liga a poucas outras (grafo esparso),
    //então percorrer a lista é rápido e não desperdiça memória
    private final List<Aresta> adjacentes = new ArrayList<>();

    //Soma dos pesos de todas as arestas de saída
    //Guardamos pronta para não precisar somar toda vez que formos sortear
    //ou calcular uma probabilidade (probabilidade = peso / pesoTotalSaida)
    private int pesoTotalSaida;

    /**
     * Cria um vértice para o estado informado
     * List.copyOf faz uma cópia que não pode ser alterada, garantindo
     * que ninguém de fora mude o estado deste vértice sem querer
     */
    public Vertice(List<Integer> estado) {
        this.estado = List.copyOf(estado);
    }

    /**
     * Registra a transição "deste vértice para o vértice destino"
     * É chamado durante o TREINAMENTO, uma vez para cada par de estados
     * seguidos na melodia
     *
     *   - Se a aresta ainda NÃO existe: cria uma aresta nova (peso começa em 0)
     *     e coloca na lista de adjacência
     *   - Em seguida (existindo ou não antes): soma 1 no peso da aresta
     *
     * Exemplo: na Brilha Brilha, "Sol -> Fá" acontece 4 vezes,
     * então este método é chamado 4 vezes e a aresta termina com peso 4
     *
     * @param destino o vértice para onde a música foi
     * @param duracao duração da nota de chegada nesta ocorrência (o ritmo)
     */
    public void adicionarTransicao(Vertice destino, double duracao) {
        Aresta aresta = buscarAresta(destino);   //já existe uma seta para esse destino?
        if (aresta == null) {
            aresta = new Aresta(this, destino);  //não existe: cria a seta
            adjacentes.add(aresta);              //e adiciona na lista de adjacência
        }
        aresta.registrarOcorrencia(duracao);     //peso + 1 e guarda a duração
        pesoTotalSaida++;                        //o total de saída também aumenta 1
    }

    /**
     * Procura, na lista de adjacência, a aresta que vai até o destino informado
     * Retorna null se ainda não existe seta deste vértice para lá
     *
     * Comparamos com "==" (mesmo objeto) porque cada estado tem
     * um único objeto Vertice no grafo
     */
    public Aresta buscarAresta(Vertice destino) {
        for (Aresta a : adjacentes) {
            if (a.getDestino() == destino) {
                return a;
            }
        }
        return null;
    }

    /**
     * Probabilidade de o passeio seguir pela aresta informada, entre 0.0 e 1.0
     *
     *   probabilidade = peso da aresta / soma dos pesos de todas as saídas
     *
     * Exemplo: Sol -> Fá tem peso 4 e o Sol tem total 10, então 4 / 10 = 0.40 (40%)
     * O "(double)" é necessário: sem ele, 4 / 10 entre inteiros daria 0
     */
    public double probabilidade(Aresta aresta) {
        if (pesoTotalSaida == 0) {
            return 0.0; //vértice sem saídas: evita divisão por zero
        }
        return (double) aresta.getPeso() / pesoTotalSaida;
    }

    /**
     * A nota que SOA quando o passeio chega neste vértice: a última do estado
     * Ex.: vértice [Sol4 Lá4] toca o Lá4
     */
    public int getNota() {
        return estado.get(estado.size() - 1);
    }

    public List<Integer> getEstado() {
        return estado;
    }

    /**
     * Devolve a lista de adjacência "somente leitura", para que outras classes
     * possam percorrê-la, mas não possam adicionar ou remover arestas por fora
     */
    public List<Aresta> getAdjacentes() {
        return Collections.unmodifiableList(adjacentes);
    }

    public int getPesoTotalSaida() {
        return pesoTotalSaida;
    }

    /**
     * GRAU DE SAÍDA = quantidade de arestas diferentes que saem deste vértice
     *
     *   - Grau 1: só existe um caminho (seta de 100%, cinza no desenho)
     *   - Grau maior que 1: é um PONTO DE ESCOLHA, onde o passeio sorteia
     *     (setas roxas no desenho)
     */
    public int getGrauSaida() {
        return adjacentes.size();
    }

    /** Nome curto, usado no cabeçalho da matriz. Ex.: "G4" ou "C4-G4" */
    public String rotuloCurto() {
        List<String> nomes = new ArrayList<>();
        for (int nota : estado) {
            nomes.add(Nota.nome(nota));
        }
        return String.join("-", nomes);
    }

    /**
     * Nome do vértice em português, usado na lista de adjacência e na tela
     * Ex.: "Sol4" (memória 1) ou "[Dó4 Sol4]" (memória 2 ou mais)
     */
    @Override
    public String toString() {
        if (estado.size() == 1) {
            return Nota.nomePt(estado.get(0));
        }
        List<String> nomes = new ArrayList<>();
        for (int nota : estado) {
            nomes.add(Nota.nomePt(nota));
        }
        return "[" + String.join(" ", nomes) + "]";
    }
}
