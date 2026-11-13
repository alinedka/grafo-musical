import java.util.ArrayList; //lista que cresce sozinha conforme adicionamos itens
import java.util.List;      //o "tipo" lista, que ArrayList implementa
import java.util.Random;    //gerador de numeros aleatorios (usado no sorteio do ritmo)

/**
 * ============================================================================
 *  aresta do grafo
 * ============================================================================
 *
 * papel no projeto:
 *   cada seta que aparece no desenho do grafo e um objeto desta classe
 *
 * o que uma aresta representa:
 *   a transicao "depois do vertice origem, a musica foi para o vertice destino"
 *
 *   - e dirigida: tem sentido. do -> sol nao e a mesma aresta que sol -> do,
 *     porque na musica a ordem das notas importa
 *
 *   - e ponderada: tem um peso, que conta quantas vezes essa transicao
 *     apareceu nas melodias de treino
 *     exemplo: se na musica "sol -> fa" aparece 4 vezes, o peso e 4
 *
 * de peso para probabilidade (calculado na classe Vertice):
 *   probabilidade = peso desta aresta / soma dos pesos de todas as arestas
 *                   que saem do mesmo vertice de origem
 *   exemplo: sol -> fa tem peso 4 e o sol tem total 10, entao 4 / 10 = 40%
 *
 * ritmo:
 *   alem do peso, a aresta guarda a duracao que a nota de chegada teve
 *   em cada vez que essa transicao aconteceu. assim, quando o passeio usa
 *   esta aresta, a nota nova sai com um ritmo que existia na musica original
 *
 * quem cria as arestas:
 *   so o metodo adicionarTransicao da classe Vertice, durante o treino,
 *   na primeira vez que a musica passa de uma nota para outra
 * ============================================================================
 */
public class Aresta {

    //"private" = so esta classe mexe direto nesses atributos
    //"final" = depois de preenchido no construtor, nunca mais muda

    private final Vertice origem;   //de onde a seta sai (ex.: o vertice sol4)
    private final Vertice destino;  //para onde a seta aponta (ex.: o vertice fa4)
    private int peso;               //quantas vezes essa transicao aconteceu no treino (nao e final: vai subindo)

    //duracoes da nota de chegada, uma por ocorrencia (em tempos: 1.0 = 1 tempo, 2.0 = 2 tempos)
    //ex.: se "sol -> fa" aconteceu 4 vezes, sempre com fa de 1 tempo,
    //esta lista fica [1.0, 1.0, 1.0, 1.0]
    //o tamanho desta lista e sempre igual ao peso, porque cada ocorrencia guarda uma duracao
    private final List<Double> duracoes = new ArrayList<>();

    /**
     * construtor: cria a aresta ainda com peso 0
     * o peso sobe em registrarOcorrencia(), que e chamado logo em seguida
     * pelo Vertice, entao na pratica toda aresta termina com peso 1 ou mais
     *
     * @param origem  o vertice de onde a seta sai
     * @param destino o vertice para onde a seta vai
     */
    public Aresta(Vertice origem, Vertice destino) {
        this.origem = origem;   //"this.origem" e o atributo; "origem" sozinho e o parametro recebido
        this.destino = destino; //guarda para onde a seta aponta
        this.peso = 0;          //ainda nenhuma ocorrencia contada
    }

    /**
     * chamado (pelo Vertice) a cada vez que a transicao e encontrada no treino:
     * soma 1 no peso e guarda a duracao da nota de chegada
     *
     * exemplo: na brilha brilha, "sol -> fa" aparece 4 vezes, entao este metodo
     * roda 4 vezes para essa aresta e o peso termina em 4
     *
     * @param duracaoDaNotaDeChegada quanto tempo durou a nota de destino dessa vez
     */
    public void registrarOcorrencia(double duracaoDaNotaDeChegada) {
        peso++;                                //"++" soma 1: mais uma vez que essa passagem aconteceu
        duracoes.add(duracaoDaNotaDeChegada);  //guarda o ritmo desta ocorrencia no fim da lista
    }

    /**
     * segundo sorteio de cada passo da composicao: o ritmo da proxima nota
     * (o primeiro sorteio e o da seta, feito em GrafoMusical.sortearAresta)
     *
     * sorteia uma posicao da lista de duracoes. como cada ocorrencia foi guardada
     * uma vez, a duracao que mais apareceu na musica tem mais chance de sair
     * ex.: lista [1.0, 1.0, 1.0, 2.0] -> 1 tempo sai 3 em 4 vezes (75%), 2 tempos 25%
     * se a lista tiver um valor so, o ritmo sai sempre igual
     *
     * @param random o gerador de numeros aleatorios do grafo
     * @return a duracao sorteada, em tempos
     */
    public double sortearDuracao(Random random) {
        //teste de seguranca: se a lista estiver vazia (nao deveria acontecer,
        //pois toda aresta recebe ao menos uma ocorrencia), usa 1 tempo
        if (duracoes.isEmpty()) {
            return 1.0;
        }
        //random.nextInt(tamanho) da uma posicao de 0 ate tamanho-1
        //e duracoes.get(posicao) pega a duracao guardada nessa posicao
        return duracoes.get(random.nextInt(duracoes.size()));
    }

    //------------------------------------------------------------ getters
    //"getters" sao metodos que so devolvem o valor de um atributo, para que
    //outras classes possam ler sem conseguir alterar

    /** devolve o vertice de onde a seta sai */
    public Vertice getOrigem() {
        return origem;
    }

    /** devolve o vertice para onde a seta aponta (o passeio anda para ele) */
    public Vertice getDestino() {
        return destino;
    }

    /** devolve o peso: quantas vezes essa transicao aconteceu no treino */
    public int getPeso() {
        return peso;
    }
}
