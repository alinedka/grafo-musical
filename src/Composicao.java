import java.util.List;

/**
 * ============================================================================
 *  COMPOSIÇÃO: o resultado do passeio aleatório
 * ============================================================================
 *
 * PAPEL NO PROJETO:
 *   É o que o método GrafoMusical.compor() devolve. Junta duas listas
 *   do mesmo tamanho, posição a posição:
 *
 *   - musica:  as notas geradas, ou seja, o que vai ser TOCADO
 *   - caminho: o vértice do grafo em que o passeio estava em cada nota
 *              É usado para acender (em laranja) o vértice certo no desenho
 *              enquanto a música toca
 *
 *   Exemplo:
 *     musica  = [ Dó4:1,   Sol4:1,   Fá4:1   ]
 *     caminho = [ vértice Dó4, vértice Sol4, vértice Fá4 ]
 *
 *   Com memória maior que 1, as primeiras posições de "caminho" podem ser null:
 *   são as notas tocadas antes de existir notas suficientes para formar um estado
 * ============================================================================
 */
public record Composicao(List<EventoMusical> musica, List<Vertice> caminho) {
}
