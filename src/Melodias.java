//imports: trazem classes prontas do java que vamos usar neste arquivo
import java.util.ArrayList;     //lista que cresce sozinha
import java.util.LinkedHashMap; //mapa (chave -> valor) que lembra a ordem de insercao
import java.util.List;          //tipo "lista" generico
import java.util.Map;           //tipo "mapa" generico

/**
 * ============================================================================
 *  melodias: as musicas de exemplo e o leitor do formato de texto
 * ============================================================================
 *
 * papel no projeto:
 *   guarda as musicas que aparecem na lista "melodia de exemplo" da tela
 *   e transforma o texto digitado em uma lista de EventoMusical,
 *   que e o que o GrafoMusical usa para treinar
 *
 * formato do texto: nota:duracao separados por espaco
 *   - nota: como em Nota.paraMidi, ex.: c4, g4, bb3
 *   - duracao: em tempos (1 = seminima, 0.5 = colcheia, 2 = minima)
 *     se for omitida, vale 1
 *   - a barra "|" separa compassos, ela e ignorada e serve so para facilitar a leitura
 *
 *   exemplo: "c4:1 d4:0.5 e4:0.5 | f4:2"
 *     = do de 1 tempo, re de meio tempo, mi de meio tempo e fa de 2 tempos
 *
 * as partituras foram transcritas a mao para este formato
 * ============================================================================
 */
public final class Melodias {

    //guarda todas as musicas de exemplo: nome da musica -> texto da melodia
    //linkedhashmap mantem a ordem em que as musicas foram colocadas (e a ordem da lista na tela)
    //"public static final": qualquer classe pode ler (a tela usa para montar a lista)
    public static final Map<String, String> EXEMPLOS = new LinkedHashMap<>();

    //bloco "static": roda uma vez so, quando a classe e usada pela primeira vez,
    //e preenche o mapa de exemplos
    static {
        //musica inteira (dominio publico)
        //put(chave, valor) guarda no mapa: o nome e a chave, o texto das notas e o valor
        //sem ":duracao" a nota vale 1 tempo, "g4:2" e um sol de 2 tempos
        //o texto foi quebrado em varias linhas com "+" so para caber na tela
        EXEMPLOS.put("Brilha, Brilha Estrelinha",
                "C4 C4 G4 G4 | A4 A4 G4:2 | F4 F4 E4 E4 | D4 D4 C4:2 | "
              + "G4 G4 F4 F4 | E4 E4 D4:2 | G4 G4 F4 F4 | E4 E4 D4:2 | "
              + "C4 C4 G4 G4 | A4 A4 G4:2 | F4 F4 E4 E4 | D4 D4 C4:2");

        //trecho da melodia (compassos 5 a 12 da partitura), em si bemol menor
        //as pausas viraram notas mais longas, pois o programa nao trabalha com pausas
        //aqui quase toda nota tem duracao 0.5 (colcheia), por isso aparece ":0.5"
        EXEMPLOS.put("Sua Amiga Eu Vou Pegar",
                "Bb3:0.5 C4:0.5 Db4:0.5 Eb4:0.5 F4:2 | F4:0.5 Eb4:0.5 Db4:0.5 C4:0.5 Bb3:2 | "
              + "Bb3:0.5 C4:0.5 Db4:0.5 Gb4:0.5 F4:2 | F4:0.5 Eb4:0.5 F4:0.5 Gb4:0.5 F4:0.5 Eb4:0.5 Db4:0.5 C4:0.5 | "
              + "Bb3:0.5 C4:0.5 Db4:0.5 Eb4:0.5 F4:2 | F4:0.5 Eb4:0.5 Db4:0.5 C4:0.5 Bb3:2 | "
              + "Bb3:0.5 C4:0.5 Db4:0.5 Gb4:0.5 F4:2 | F4:0.5 Eb4:0.5 F4:0.5 Gb4:0.5 F4:0.5 Eb4:0.5 Db4:0.5 C4:0.5 | "
              + "Bb3:2");

        //gancho "ma-lan-dra-men-te" (compassos 1 e 11) + refrao (compassos 12 a 17),
        //a partir da partitura para flauta, as partes faladas (rap) ficaram de fora,
        //e as pausas viraram notas mais longas
        EXEMPLOS.put("Malandramente",
                "A4:0.5 F5:1 E5:0.5 D5:2.5 | F5:1 E5:0.5 D5:1 | A4:3 A4:0.5 F5:0.5 | "
              + "F5:0.5 E5:0.5 E5:0.5 D5:0.5 D5:0.5 C5:0.5 D5:1 | A4:2.5 A4:0.5 F5:0.5 F5:0.5 | "
              + "E5:0.5 E5:0.5 D5:0.5 D5:0.5 C5:0.5 D5:1 A4:2.5 | A4:0.5 A4:0.5 F5:0.5 F5:0.5 | "
              + "E5:0.5 E5:0.5 D5:0.5 D5:0.5 C5:0.5 D5:1 A4:0.5 | F5:1 E5:0.5 D5:2.5");

        //musica inteira (dominio publico)
        EXEMPLOS.put("Frei João (Frère Jacques)",
                "C4 D4 E4 C4 | C4 D4 E4 C4 | E4 F4 G4:2 | E4 F4 G4:2 | "
              + "G4:0.5 A4:0.5 G4:0.5 F4:0.5 E4 C4 | G4:0.5 A4:0.5 G4:0.5 F4:0.5 E4 C4 | "
              + "C4 G3 C4:2 | C4 G3 C4:2");

        //exemplo mais simples possivel: cada nota aparece uma vez,
        //entao o grafo vira um unico ciclo, sem nenhum ponto de escolha
        //(do re mi fa sol la si do, e depois volta para o comeco)
        EXEMPLOS.put("Escala de Dó (teste simples)",
                "C4 D4 E4 F4 G4 A4 B4 C5:2");
    }

    //construtor privado: ninguem de fora consegue fazer "new Melodias()"
    private Melodias() {
        //classe utilitaria: nao deve ser instanciada
    }

    /**
     * converte o texto de uma melodia em uma lista de eventos musicais
     * e chamado pela tela quando a pessoa clica em "treinar grafo"
     *
     * exemplo: "c4 g4:2" -> [EventoMusical(60, 1.0), EventoMusical(67, 2.0)]
     *
     * @param texto a melodia escrita no formato nota:duracao
     * @return a lista de notas, na ordem em que aparecem no texto
     * @throws IllegalArgumentException se alguma nota ou duracao estiver errada
     *         (a tela mostra a mensagem para o usuario corrigir)
     */
    public static List<EventoMusical> ler(String texto) {
        //lista que vai sendo preenchida com cada nota lida, comeca vazia
        List<EventoMusical> melodia = new ArrayList<>();

        //quebra o texto em pedacos ("tokens") usando como separador
        //qualquer sequencia de espacos, quebras de linha, virgulas ou "|"
        //"[\\s,|]+" e uma expressao regular: \\s = espaco, + = um ou mais seguidos
        //o for passa por cada pedaco, um de cada vez, ex.: "c4", depois "g4:2"...
        for (String token : texto.split("[\\s,|]+")) {
            //pedaco vazio (pode acontecer no comeco do texto): pula para o proximo
            if (token.isBlank()) {
                continue; //continue = ignora o resto desta volta e vai para a proxima
            }
            //"g4:2" -> partes[0] = "g4" (nota), partes[1] = "2" (duracao)
            String[] partes = token.split(":");
            //converte o nome da nota no numero midi (ex.: "g4" -> 67)
            int nota = Nota.paraMidi(partes[0]);
            double duracao = 1.0; //valor padrao quando a duracao nao e escrita
            //se tem mais de uma parte, a pessoa escreveu a duracao depois dos ":"
            if (partes.length > 1) {
                //tenta transformar o texto da duracao em numero com casas decimais
                try {
                    duracao = Double.parseDouble(partes[1]);
                } catch (NumberFormatException e) {
                    //caiu aqui se nao era numero, ex.: "g4:x"
                    throw new IllegalArgumentException("Duração inválida em: " + token);
                }
                //duracao zero ou negativa nao faz sentido em musica
                if (duracao <= 0) {
                    throw new IllegalArgumentException("Duração deve ser positiva em: " + token);
                }
            }
            //junta nota + duracao num EventoMusical e coloca no fim da lista
            melodia.add(new EventoMusical(nota, duracao));
        }
        return melodia; //devolve a melodia pronta para o treino
    }
}
