import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ============================================================================
 *  MELODIAS: as músicas de exemplo e o leitor do formato de texto
 * ============================================================================
 *
 * PAPEL NO PROJETO:
 *   Guarda as músicas que aparecem na lista "Melodia de exemplo" da tela
 *   e transforma o texto digitado em uma lista de EventoMusical,
 *   que é o que o GrafoMusical usa para treinar
 *
 * FORMATO DO TEXTO: NOTA:DURAÇÃO separados por espaço
 *   - NOTA: como em Nota.paraMidi, ex.: C4, G4, Bb3
 *   - DURAÇÃO: em tempos (1 = semínima, 0.5 = colcheia, 2 = mínima)
 *     Se for omitida, vale 1
 *   - A barra "|" separa compassos. Ela é ignorada, serve só para facilitar a leitura
 *
 *   Exemplo: "C4:1 D4:0.5 E4:0.5 | F4:2"
 *
 * As partituras foram transcritas à mão para este formato
 * ============================================================================
 */
public final class Melodias {

    //Nome da música -> texto da melodia
    //LinkedHashMap mantém a ordem em que as músicas foram colocadas (é a ordem da lista na tela)
    public static final Map<String, String> EXEMPLOS = new LinkedHashMap<>();

    //Bloco "static": roda uma vez só, quando a classe é usada pela primeira vez,
    //e preenche o mapa de exemplos
    static {
        //Música inteira (domínio público)
        EXEMPLOS.put("Brilha, Brilha Estrelinha",
                "C4 C4 G4 G4 | A4 A4 G4:2 | F4 F4 E4 E4 | D4 D4 C4:2 | "
              + "G4 G4 F4 F4 | E4 E4 D4:2 | G4 G4 F4 F4 | E4 E4 D4:2 | "
              + "C4 C4 G4 G4 | A4 A4 G4:2 | F4 F4 E4 E4 | D4 D4 C4:2");

        //Trecho da melodia (compassos 5 a 12 da partitura), em Si bemol menor
        //As pausas viraram notas mais longas, pois o programa não trabalha com pausas
        EXEMPLOS.put("Sua Amiga Eu Vou Pegar",
                "Bb3:0.5 C4:0.5 Db4:0.5 Eb4:0.5 F4:2 | F4:0.5 Eb4:0.5 Db4:0.5 C4:0.5 Bb3:2 | "
              + "Bb3:0.5 C4:0.5 Db4:0.5 Gb4:0.5 F4:2 | F4:0.5 Eb4:0.5 F4:0.5 Gb4:0.5 F4:0.5 Eb4:0.5 Db4:0.5 C4:0.5 | "
              + "Bb3:0.5 C4:0.5 Db4:0.5 Eb4:0.5 F4:2 | F4:0.5 Eb4:0.5 Db4:0.5 C4:0.5 Bb3:2 | "
              + "Bb3:0.5 C4:0.5 Db4:0.5 Gb4:0.5 F4:2 | F4:0.5 Eb4:0.5 F4:0.5 Gb4:0.5 F4:0.5 Eb4:0.5 Db4:0.5 C4:0.5 | "
              + "Bb3:2");

        //Gancho "ma-lan-dra-men-te" (compassos 1 e 11) + refrão (compassos 12 a 17),
        //a partir da partitura para flauta. As partes faladas (rap) ficaram de fora,
        //e as pausas viraram notas mais longas
        EXEMPLOS.put("Malandramente",
                "A4:0.5 F5:1 E5:0.5 D5:2.5 | F5:1 E5:0.5 D5:1 | A4:3 A4:0.5 F5:0.5 | "
              + "F5:0.5 E5:0.5 E5:0.5 D5:0.5 D5:0.5 C5:0.5 D5:1 | A4:2.5 A4:0.5 F5:0.5 F5:0.5 | "
              + "E5:0.5 E5:0.5 D5:0.5 D5:0.5 C5:0.5 D5:1 A4:2.5 | A4:0.5 A4:0.5 F5:0.5 F5:0.5 | "
              + "E5:0.5 E5:0.5 D5:0.5 D5:0.5 C5:0.5 D5:1 A4:0.5 | F5:1 E5:0.5 D5:2.5");

        //Música inteira (domínio público)
        EXEMPLOS.put("Frei João (Frère Jacques)",
                "C4 D4 E4 C4 | C4 D4 E4 C4 | E4 F4 G4:2 | E4 F4 G4:2 | "
              + "G4:0.5 A4:0.5 G4:0.5 F4:0.5 E4 C4 | G4:0.5 A4:0.5 G4:0.5 F4:0.5 E4 C4 | "
              + "C4 G3 C4:2 | C4 G3 C4:2");

        //Exemplo mais simples possível: cada nota aparece uma vez,
        //então o grafo vira um único ciclo, sem nenhum ponto de escolha
        EXEMPLOS.put("Escala de Dó (teste simples)",
                "C4 D4 E4 F4 G4 A4 B4 C5:2");
    }

    private Melodias() {
        //classe utilitária: não deve ser instanciada
    }

    /**
     * Converte o texto de uma melodia em uma lista de eventos musicais
     *
     * Exemplo: "C4 G4:2" -> [EventoMusical(60, 1.0), EventoMusical(67, 2.0)]
     *
     * @throws IllegalArgumentException se alguma nota ou duração estiver errada
     *         (a tela mostra a mensagem para o usuário corrigir)
     */
    public static List<EventoMusical> ler(String texto) {
        List<EventoMusical> melodia = new ArrayList<>();

        //Quebra o texto em pedaços ("tokens") usando como separador
        //qualquer sequência de espaços, quebras de linha, vírgulas ou "|"
        for (String token : texto.split("[\\s,|]+")) {
            if (token.isBlank()) {
                continue;
            }
            //"G4:2" -> partes[0] = "G4" (nota), partes[1] = "2" (duração)
            String[] partes = token.split(":");
            int nota = Nota.paraMidi(partes[0]);
            double duracao = 1.0; //valor padrão quando a duração não é escrita
            if (partes.length > 1) {
                try {
                    duracao = Double.parseDouble(partes[1]);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Duração inválida em: " + token);
                }
                if (duracao <= 0) {
                    throw new IllegalArgumentException("Duração deve ser positiva em: " + token);
                }
            }
            melodia.add(new EventoMusical(nota, duracao));
        }
        return melodia;
    }
}
