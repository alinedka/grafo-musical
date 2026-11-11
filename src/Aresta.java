import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * ============================================================================
 *  ARESTA DO GRAFO
 * ============================================================================
 *
 * PAPEL NO PROJETO:
 *   Cada seta que aparece no desenho do grafo é um objeto desta classe
 *
 * O QUE UMA ARESTA REPRESENTA:
 *   A transição "depois do vértice ORIGEM, a música foi para o vértice DESTINO"
 *
 *   - É DIRIGIDA: tem sentido. Dó -> Sol não é a mesma aresta que Sol -> Dó,
 *     porque na música a ordem das notas importa
 *
 *   - É PONDERADA: tem um PESO, que conta quantas vezes essa transição
 *     apareceu nas melodias de treino
 *     Exemplo: se na música "Sol -> Fá" aparece 4 vezes, o peso é 4
 *
 * DE PESO PARA PROBABILIDADE (calculado na classe Vertice):
 *   probabilidade = peso desta aresta / soma dos pesos de todas as arestas
 *                   que saem do mesmo vértice de origem
 *
 * RITMO:
 *   Além do peso, a aresta guarda a DURAÇÃO que a nota de chegada teve
 *   em cada vez que essa transição aconteceu. Assim, quando o passeio usa
 *   esta aresta, a nota nova sai com um ritmo que existia na música original
 * ============================================================================
 */
public class Aresta {

    private final Vertice origem;   //de onde a seta sai
    private final Vertice destino;  //para onde a seta aponta
    private int peso;               //quantas vezes essa transição aconteceu no treino

    //Durações da nota de chegada, uma por ocorrência
    //Ex.: se "Sol -> Fá" aconteceu 4 vezes, sempre com Fá de 1 tempo,
    //esta lista fica [1.0, 1.0, 1.0, 1.0]
    private final List<Double> duracoes = new ArrayList<>();

    /** Cria a aresta ainda com peso 0. O peso sobe em registrarOcorrencia() */
    public Aresta(Vertice origem, Vertice destino) {
        this.origem = origem;
        this.destino = destino;
        this.peso = 0;
    }

    /**
     * Chamado (pelo Vertice) a cada vez que a transição é encontrada no treino:
     * soma 1 no peso e guarda a duração da nota de chegada
     */
    public void registrarOcorrencia(double duracaoDaNotaDeChegada) {
        peso++;
        duracoes.add(duracaoDaNotaDeChegada);
    }

    /**
     * Sorteia uma das durações guardadas. Como cada ocorrência foi guardada
     * uma vez, a duração mais comum na música tem mais chance de sair
     * Se a lista estiver vazia (não deveria acontecer), usa 1 tempo
     */
    public double sortearDuracao(Random random) {
        if (duracoes.isEmpty()) {
            return 1.0;
        }
        return duracoes.get(random.nextInt(duracoes.size()));
    }

    public Vertice getOrigem() {
        return origem;
    }

    public Vertice getDestino() {
        return destino;
    }

    public int getPeso() {
        return peso;
    }
}
