/**
 * ============================================================================
 *  evento musical: uma nota tocada
 * ============================================================================
 *
 * papel no projeto:
 *   uma melodia (de treino ou composta) e uma lista de EventoMusical
 *   cada evento diz qual nota tocar e por quanto tempo
 *
 *   - nota:    numero midi da nota (ex.: 60 = do4, 67 = sol4), veja a classe Nota
 *   - duracao: medida em tempos (batidas):
 *                1.0 = seminima, 0.5 = colcheia, 2.0 = minima, 1.5 = seminima pontuada
 *              quanto dura 1 tempo em segundos depende do andamento (bpm):
 *                segundos = 60 / bpm, ex.: a 120 bpm, 1 tempo = 0,5 segundo
 *
 * o que e um "record":
 *   e um tipo especial do java feito so para guardar dados que nao mudam
 *   escrevendo so "record EventoMusical(int nota, double duracao)" o java cria
 *   sozinho: o construtor, os metodos nota() e duracao() para ler os valores,
 *   e os metodos equals e hash code (para comparar dois eventos)
 *   depois de criado, um evento nunca muda (e imutavel)
 * ============================================================================
 */
public record EventoMusical(int nota, double duracao) {
    //nota    -> guarda o numero midi da nota (inteiro, ex.: 67)
    //duracao -> guarda quantos tempos a nota dura (numero com virgula, ex.: 0.5)

    /**
     * texto no mesmo formato usado para digitar melodias, ex.: "c4:1" ou "d4:0.5"
     * (na tela a letra da nota aparece maiuscula)
     * e usado na aba "musica gerada" para mostrar a musica no formato de entrada
     * mostra "c4:1" em vez de "c4:1.0" quando a duracao e um numero inteiro
     *
     * @return o texto "nome da nota:duracao"
     */
    @Override //@Override avisa que estamos substituindo o toString padrao do java
    public String toString() {
        //se a duracao nao tem parte quebrada (ex.: 2.0), Math.floor(2.0) da 2.0 e os dois sao iguais
        //"condicao ? a : b" e um if curto: se a condicao for verdadeira usa a, senao usa b
        //(int) duracao corta a parte quebrada, entao 2.0 vira 2 e o texto fica "2"
        String d = (duracao == Math.floor(duracao)) ? String.valueOf((int) duracao) : String.valueOf(duracao);
        //Nota.nome transforma o numero midi no nome em letra (ex.: 60 -> "c4") e junta com ":" e a duracao
        return Nota.nome(nota) + ":" + d;
    }
}
