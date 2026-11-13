import java.util.List; //List e a "lista" do java: uma sequencia de elementos em ordem

/**
 * ============================================================================
 *  composicao: o resultado do passeio aleatorio
 * ============================================================================
 *
 * papel no projeto:
 *   e o que o metodo GrafoMusical.compor() devolve, juntando duas listas
 *   do mesmo tamanho, posicao a posicao:
 *
 *   - musica:  as notas geradas, ou seja, o que vai ser tocado
 *   - caminho: o vertice do grafo em que o passeio estava em cada nota
 *              e usado para acender (em laranja) o vertice certo no desenho
 *              enquanto a musica toca
 *
 *   exemplo:
 *     musica  = [ do4:1,   sol4:1,   fa4:1   ]
 *     caminho = [ vertice do4, vertice sol4, vertice fa4 ]
 *
 *   a posicao 0 de "musica" combina com a posicao 0 de "caminho", a 1 com a 1, e assim por diante
 *
 *   com memoria maior que 1, as primeiras posicoes de "caminho" podem ser null
 *   (null quer dizer "nenhum vertice"): sao as notas iniciais, tocadas antes de
 *   existir notas suficientes para formar um estado
 *
 * por que "record":
 *   record e um tipo do java so para guardar dados; ele cria sozinho o construtor
 *   e os metodos musica() e caminho() para ler as duas listas
 * ============================================================================
 */
public record Composicao(List<EventoMusical> musica, List<Vertice> caminho) {
    //musica  -> lista de notas (EventoMusical) que o TocadorMidi vai tocar
    //caminho -> lista de vertices (Vertice) que o PainelGrafo vai acender, um por nota
}
