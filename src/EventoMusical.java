/**
 * ============================================================================
 *  EVENTO MUSICAL: uma nota tocada
 * ============================================================================
 *
 * PAPEL NO PROJETO:
 *   Uma melodia (de treino ou composta) é uma lista de EventoMusical
 *   Cada evento diz QUAL nota tocar e POR QUANTO TEMPO
 *
 *   - nota:    número MIDI da nota (ex.: 60 = Dó4, 67 = Sol4). Veja a classe Nota
 *   - duracao: medida em tempos (batidas):
 *                1.0 = semínima, 0.5 = colcheia, 2.0 = mínima, 1.5 = semínima pontuada
 *              Quanto dura 1 tempo em segundos depende do andamento (BPM):
 *                segundos = 60 / BPM. Ex.: a 120 BPM, 1 tempo = 0,5 segundo
 *
 * "record" é um tipo do Java que cria automaticamente o construtor,
 * os métodos nota() e duracao(), equals e hashCode. Serve para guardar dados
 * que não mudam, como aqui
 * ============================================================================
 */
public record EventoMusical(int nota, double duracao) {

    /**
     * Texto no mesmo formato usado para digitar melodias, ex.: "C4:1" ou "D4:0.5"
     * Mostra "C4:1" em vez de "C4:1.0" quando a duração é um número inteiro
     */
    @Override
    public String toString() {
        String d = (duracao == Math.floor(duracao)) ? String.valueOf((int) duracao) : String.valueOf(duracao);
        return Nota.nome(nota) + ":" + d;
    }
}
