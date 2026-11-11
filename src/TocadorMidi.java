import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Track;

/**
 * ============================================================================
 *  TOCADOR MIDI: transforma as notas em som
 * ============================================================================
 *
 * PAPEL NO PROJETO:
 *   Recebe uma lista de EventoMusical (a melodia original ou a composição)
 *   e toca no alto-falante. Também consegue salvar a música em arquivo .mid
 *
 * COMO FUNCIONA:
 *   Usa o SINTETIZADOR MIDI que já vem com o Java (pacote javax.sound.midi),
 *   sem nenhuma biblioteca externa. O sintetizador é como um teclado virtual:
 *     - noteOn(nota, volume): "aperta a tecla" e a nota começa a soar;
 *     - noteOff(nota):        "solta a tecla" e a nota para
 *   Entre um e outro, o programa espera o tempo da duração da nota
 *
 * "implements AutoCloseable": permite fechar o sintetizador com close()
 * quando não for mais usado, liberando o dispositivo de áudio
 * ============================================================================
 */
public class TocadorMidi implements AutoCloseable {

    private final Synthesizer sintetizador; //o "teclado virtual" do Java
    private final MidiChannel canal;        //o canal por onde mandamos as notas (como um instrumento)
    private final Random random = new Random();

    /** Abre o sintetizador e pega o primeiro canal (canal 0) */
    public TocadorMidi() throws MidiUnavailableException {
        sintetizador = MidiSystem.getSynthesizer();
        sintetizador.open();
        canal = sintetizador.getChannels()[0];
    }

    /**
     * Troca o instrumento do canal
     * O número segue o padrão "General MIDI": 0 = piano, 24 = violão,
     * 40 = violino, 73 = flauta, e assim por diante
     */
    public void setInstrumento(int programa) {
        canal.programChange(programa);
    }

    /**
     * Toca a música nota por nota
     *
     * IMPORTANTE: este método "dorme" (Thread.sleep) enquanto cada nota soa,
     * então precisa rodar numa THREAD SEPARADA. Se rodasse na thread da tela,
     * a janela travaria até a música acabar. (Quem cria a thread é o CompositorApp.)
     *
     * @param musica      notas a tocar
     * @param bpm         fornece o andamento atual; é consultado a cada nota,
     *                    por isso dá para mudar a velocidade durante a música
     * @param aoTocarNota "aviso" chamado com o índice de cada nota no momento em
     *                    que ela começa a soar (o CompositorApp usa para acender
     *                    o vértice no desenho)
     */
    public void tocar(List<EventoMusical> musica, IntSupplier bpm, IntConsumer aoTocarNota) {
        try {
            for (int i = 0; i < musica.size(); i++) {
                EventoMusical evento = musica.get(i);
                aoTocarNota.accept(i);                 //avisa a tela: "a nota i começou"

                int volume = 80 + random.nextInt(20); //volume de 80 a 99: pequena variação para soar mais "humano"
                canal.noteOn(evento.nota(), volume);  //começa a nota

                //Quanto dura 1 tempo em milissegundos: 60.000 ms (1 minuto) / batidas por minuto
                //Ex.: 120 BPM -> 500 ms por tempo. Uma nota de 2 tempos dura 1000 ms
                long msPorTempo = 60_000L / bpm.getAsInt();
                Thread.sleep((long) (evento.duracao() * msPorTempo)); //espera a nota soar

                canal.noteOff(evento.nota());         //termina a nota
            }
        } catch (InterruptedException e) {
            //O usuário apertou "Parar": a thread foi interrompida no meio do sleep
            //Marcamos a interrupção de novo (boa prática em Java) e saímos do laço
            Thread.currentThread().interrupt();
        } finally {
            //Acontecendo o que for (fim normal ou "Parar"), desliga todas as notas
            //para nenhuma ficar soando para sempre
            canal.allNotesOff();
        }
    }

    /**
     * Grava a música em um arquivo .mid, que pode ser aberto em qualquer
     * player ou programa de música
     *
     * Um arquivo MIDI não guarda som gravado, e sim "instruções":
     * em que momento cada nota começa e termina. O tempo é contado em "ticks"
     */
    public static void salvarArquivo(List<EventoMusical> musica, int bpm, int instrumento, File arquivo)
            throws InvalidMidiDataException, IOException {
        final int resolucao = 480; //quantos "ticks" cabem em 1 tempo (semínima)
        Sequence sequencia = new Sequence(Sequence.PPQ, resolucao); //PPQ = pulsos por semínima
        Track trilha = sequencia.createTrack();

        //ANDAMENTO: o MIDI guarda em microssegundos por semínima, usando 3 bytes
        //Ex.: 120 BPM -> 60.000.000 / 120 = 500.000 microssegundos
        //Os ">>" separam o número em 3 bytes (do mais alto para o mais baixo)
        int microPorTempo = 60_000_000 / bpm;
        byte[] dadosTempo = {(byte) (microPorTempo >> 16), (byte) (microPorTempo >> 8), (byte) microPorTempo};
        trilha.add(new MidiEvent(new MetaMessage(0x51, dadosTempo, 3), 0)); //0x51 = código de "andamento"

        //INSTRUMENTO: escolhido logo no início (tick 0)
        trilha.add(new MidiEvent(new ShortMessage(ShortMessage.PROGRAM_CHANGE, 0, instrumento, 0), 0));

        //NOTAS: cada nota vira um NOTE_ON (começo) e um NOTE_OFF (fim)
        long tick = 0; //posição atual na música, em ticks
        for (EventoMusical evento : musica) {
            long duracao = Math.round(evento.duracao() * resolucao); //tempos -> ticks
            trilha.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, evento.nota(), 90), tick));
            trilha.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, evento.nota(), 0), tick + duracao));
            tick += duracao; //a próxima nota começa quando esta termina
        }
        MidiSystem.write(sequencia, 1, arquivo); //1 = arquivo MIDI do tipo 1
    }

    /** Fecha o sintetizador, liberando o dispositivo de áudio */
    @Override
    public void close() {
        sintetizador.close();
    }
}
