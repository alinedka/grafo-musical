/**
 * ============================================================================
 *  NOTA: conversão entre nome da nota e número MIDI
 * ============================================================================
 *
 * PAPEL NO PROJETO:
 *   Dentro do programa, toda nota é um NÚMERO (padrão MIDI), porque número é
 *   fácil de comparar, guardar e mandar para o sintetizador
 *   Para as pessoas, mostramos o NOME ("C4", "Sol4"). Esta classe faz a ponte
 *
 * PADRÃO MIDI:
 *   Cada nota é um número de 0 a 127. Subir 1 = subir meio tom
 *   Exemplos: C4 (Dó central) = 60, D4 = 62, G4 = 67, A4 (Lá 440 Hz) = 69
 *   Cada oitava tem 12 notas, então C5 = 60 + 12 = 72
 *
 * FORMATO ACEITO: letra (A-G) + acidentes opcionais (# ou b) + oitava
 *   Exemplos: "C4", "F#3", "Bb4", "Eb4"
 *   Notação americana: C=Dó, D=Ré, E=Mi, F=Fá, G=Sol, A=Lá, B=Si
 * ============================================================================
 */
public final class Nota {

    //Nomes das 12 notas de uma oitava, na ordem, a partir do Dó
    //As notas pretas do piano estão escritas com bemol (b),
    //ex.: Bb = Si bemol (mesmo som que A#)
    private static final String[] NOMES = {"C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"};
    private static final String[] NOMES_PT = {"Dó", "Réb", "Ré", "Mib", "Mi", "Fá", "Solb", "Sol", "Láb", "Lá", "Sib", "Si"};

    //Distância em semitons de cada letra até o Dó, na ordem A, B, C, D, E, F, G
    //Ex.: A (Lá) está 9 semitons acima do Dó; G (Sol) está 7
    private static final int[] SEMITONS = {9, 11, 0, 2, 4, 5, 7};

    private Nota() {
        //classe utilitária (só tem métodos static): não deve ser instanciada
    }

    /**
     * Converte um texto como "C4" ou "Bb3" no número MIDI correspondente
     *
     * Conta: midi = (oitava + 1) * 12 + semitons da letra (+1 para #, -1 para b)
     * Exemplo: "G4" -> (4 + 1) * 12 + 7 = 67
     *          "Bb3" -> (3 + 1) * 12 + 11 - 1 = 58
     */
    public static int paraMidi(String texto) {
        String t = texto.trim();
        if (t.isEmpty()) {
            throw new IllegalArgumentException("Nota vazia");
        }

        //1) A letra da nota (A a G)
        char letra = Character.toUpperCase(t.charAt(0));
        if (letra < 'A' || letra > 'G') {
            throw new IllegalArgumentException("Nota inválida: " + texto);
        }
        int semitom = SEMITONS[letra - 'A']; //'A' - 'A' = 0, 'B' - 'A' = 1, ...

        //2) Os acidentes: # sobe meio tom, b desce meio tom
        int i = 1;
        while (i < t.length() && (t.charAt(i) == '#' || t.charAt(i) == 'b')) {
            semitom += (t.charAt(i) == '#') ? 1 : -1;
            i++;
        }

        //3) A oitava: o número que sobrou no final
        int oitava;
        try {
            oitava = Integer.parseInt(t.substring(i));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Oitava inválida na nota: " + texto);
        }

        int midi = (oitava + 1) * 12 + semitom;
        if (midi < 0 || midi > 127) {
            throw new IllegalArgumentException("Nota fora do alcance MIDI: " + texto);
        }
        return midi;
    }

    /**
     * Nome no padrão americano, ex.: 67 -> "G4", 70 -> "Bb4"
     *   midi % 12      = qual das 12 notas (0 = C, 7 = G...)
     *   midi / 12 - 1  = a oitava
     */
    public static String nome(int midi) {
        return NOMES[midi % 12] + (midi / 12 - 1);
    }

    /** Nome em português, ex.: 67 -> "Sol4", 70 -> "Sib4" */
    public static String nomePt(int midi) {
        return NOMES_PT[midi % 12] + (midi / 12 - 1);
    }

    /**
     * Classe da nota (0 = Dó, 1 = Réb, ..., 11 = Si), ignorando a oitava
     * Usado no desenho para dar a mesma cor a todos os Dós, a todos os Sóis etc
     */
    public static int classe(int midi) {
        return midi % 12;
    }
}
