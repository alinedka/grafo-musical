/**
 * ============================================================================
 *  nota: conversao entre nome da nota e numero midi
 * ============================================================================
 *
 * papel no projeto:
 *   dentro do programa, toda nota e um numero (padrao midi), porque numero e
 *   facil de comparar, guardar e mandar para o sintetizador
 *   para as pessoas, mostramos o nome ("c4", "sol4"), e esta classe faz a ponte
 *   entre os dois jeitos de escrever a mesma nota
 *
 * padrao midi:
 *   cada nota e um numero de 0 a 127, e subir 1 = subir meio tom (uma tecla do piano)
 *   exemplos: c4 (do central) = 60, d4 = 62, g4 = 67, a4 (la 440 hz) = 69
 *   cada oitava tem 12 notas, entao c5 = 60 + 12 = 72
 *
 * formato aceito: letra (a ate g) + acidentes opcionais (# ou b) + oitava
 *   exemplos: "c4", "f#3", "bb4", "eb4"
 *   notacao americana: c = do, d = re, e = mi, f = fa, g = sol, a = la, b = si
 *
 * "final" na classe quer dizer que ninguem pode criar uma classe filha dela
 * ============================================================================
 */
public final class Nota {

    //nomes das 12 notas de uma oitava, na ordem, a partir do do
    //a posicao no vetor e o "resto da divisao por 12" do numero midi (0 = c, 7 = g...)
    //as notas pretas do piano estao escritas com bemol (b),
    //ex.: bb = si bemol (mesmo som que a#)
    //"static final" = constante: existe uma so para a classe toda e nunca muda
    private static final String[] NOMES = {"C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"};
    //os mesmos 12 nomes, so que em portugues, na mesma ordem do vetor de cima
    private static final String[] NOMES_PT = {"Dó", "Réb", "Ré", "Mib", "Mi", "Fá", "Solb", "Sol", "Láb", "Lá", "Sib", "Si"};

    //distancia em semitons (meios tons) de cada letra ate o do, na ordem a, b, c, d, e, f, g
    //ex.: a (la) esta 9 semitons acima do do, g (sol) esta 7
    //a ordem e alfabetica para dar para achar a posicao fazendo "letra - 'A'"
    private static final int[] SEMITONS = {9, 11, 0, 2, 4, 5, 7};

    //construtor privado: ninguem de fora consegue fazer "new Nota()"
    private Nota() {
        //classe utilitaria (so tem metodos static): nao deve ser instanciada
    }

    /**
     * converte um texto como "c4" ou "bb3" no numero midi correspondente
     * e chamado pela classe Melodias quando ela le o texto de uma musica
     *
     * conta: midi = (oitava + 1) * 12 + semitons da letra (+1 para #, -1 para b)
     * exemplo: "g4" -> (4 + 1) * 12 + 7 = 67
     *          "bb3" -> (3 + 1) * 12 + 11 - 1 = 58
     *
     * @param texto a nota escrita, ex.: "g4"
     * @return o numero midi da nota, de 0 a 127
     */
    public static int paraMidi(String texto) {
        //trim tira espacos do comeco e do fim, ex.: " g4 " vira "g4"
        String t = texto.trim();
        //se nao sobrou nada, nao tem nota para converter
        if (t.isEmpty()) {
            //throw "lanca" um erro: o metodo para aqui e a tela mostra a mensagem
            throw new IllegalArgumentException("Nota vazia");
        }

        //1) a letra da nota (a ate g)
        //charAt(0) pega o primeiro caractere, toUpperCase deixa maiusculo ("g" vira "G")
        char letra = Character.toUpperCase(t.charAt(0));
        //se a letra nao estiver entre a e g, nao e uma nota valida
        if (letra < 'A' || letra > 'G') {
            throw new IllegalArgumentException("Nota inválida: " + texto);
        }
        //em java, letras sao numeros por dentro, entao da para fazer conta com elas
        int semitom = SEMITONS[letra - 'A']; //'A' - 'A' = 0, 'B' - 'A' = 1, ...

        //2) os acidentes: # sobe meio tom, b desce meio tom
        //i guarda a posicao do caractere que estamos lendo (a letra ja foi, entao comeca em 1)
        int i = 1;
        //enquanto ainda houver caractere e ele for # ou b, continua lendo
        //(da para ter mais de um, ex.: "c##4", por isso e um while e nao um if)
        while (i < t.length() && (t.charAt(i) == '#' || t.charAt(i) == 'b')) {
            //"condicao ? a : b" e um if curto: se for #, soma 1, se nao (b), soma -1
            semitom += (t.charAt(i) == '#') ? 1 : -1;
            i++; //vai para o proximo caractere
        }

        //3) a oitava: o numero que sobrou no final
        //guarda a oitava lida do texto, ex.: 4 em "g4"
        int oitava;
        //try/catch: "tenta" fazer algo e, se der erro, cai no catch em vez de travar o programa
        try {
            //substring(i) pega o texto do i ate o fim, parseInt transforma "4" no numero 4
            oitava = Integer.parseInt(t.substring(i));
        } catch (NumberFormatException e) {
            //caiu aqui se o final nao era um numero, ex.: "gx"
            throw new IllegalArgumentException("Oitava inválida na nota: " + texto);
        }

        //a conta que transforma oitava + semitom no numero midi
        int midi = (oitava + 1) * 12 + semitom;
        //o padrao midi so aceita de 0 a 127
        if (midi < 0 || midi > 127) {
            throw new IllegalArgumentException("Nota fora do alcance MIDI: " + texto);
        }
        return midi; //devolve o numero para quem chamou
    }

    /**
     * nome no padrao americano, ex.: 67 -> "g4", 70 -> "bb4"
     * e usado no cabecalho da matriz de probabilidades (nome curto)
     *   midi % 12      = qual das 12 notas (0 = c, 7 = g...), % e o resto da divisao
     *   midi / 12 - 1  = a oitava (divisao de inteiros joga fora a parte quebrada)
     *
     * @param midi o numero midi da nota
     * @return o nome com a oitava, ex.: "g4"
     */
    public static String nome(int midi) {
        return NOMES[midi % 12] + (midi / 12 - 1);
    }

    /**
     * nome em portugues, ex.: 67 -> "sol4", 70 -> "sib4"
     * e usado na lista de adjacencia, no desenho do grafo e na musica gerada
     * a conta e a mesma do metodo nome, so muda o vetor de nomes
     *
     * @param midi o numero midi da nota
     * @return o nome em portugues com a oitava, ex.: "sol4"
     */
    public static String nomePt(int midi) {
        return NOMES_PT[midi % 12] + (midi / 12 - 1);
    }

    /**
     * classe da nota (0 = do, 1 = reb, ..., 11 = si), ignorando a oitava
     * e usado no desenho para dar a mesma cor a todos os dos, a todos os sois etc
     * ex.: do4 (60) e do5 (72) dao os dois 0, entao ficam com a mesma cor
     *
     * @param midi o numero midi da nota
     * @return um numero de 0 a 11
     */
    public static int classe(int midi) {
        return midi % 12;
    }
}
