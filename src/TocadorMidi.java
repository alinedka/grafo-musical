//imports: classes prontas do java usadas neste arquivo
import java.io.File;                    //representa um arquivo no computador (para salvar o .mid)
import java.io.IOException;             //erro de leitura/escrita de arquivo
import java.util.List;                  //tipo "lista"
import java.util.Random;                //gerador de numeros aleatorios
import java.util.function.IntConsumer;  //"funcao que recebe um int" (usada como aviso para a tela)
import java.util.function.IntSupplier;  //"funcao que devolve um int" (usada para ler o andamento)

//tudo de javax.sound.midi ja vem com o java: e a parte de som midi
import javax.sound.midi.InvalidMidiDataException; //erro quando uma mensagem midi esta errada
import javax.sound.midi.MetaMessage;              //mensagem especial do arquivo (ex.: andamento)
import javax.sound.midi.MidiChannel;              //um canal do sintetizador (toca um instrumento)
import javax.sound.midi.MidiEvent;                //uma mensagem + o momento (tick) em que acontece
import javax.sound.midi.MidiSystem;               //porta de entrada: da o sintetizador e grava arquivos
import javax.sound.midi.MidiUnavailableException; //erro quando o computador nao tem sintetizador
import javax.sound.midi.Sequence;                 //a musica inteira dentro de um arquivo midi
import javax.sound.midi.ShortMessage;             //mensagem curta: liga nota, desliga nota, troca instrumento
import javax.sound.midi.Synthesizer;              //o "teclado virtual" que gera o som
import javax.sound.midi.Track;                    //uma trilha da musica (lista de eventos)

/**
 * ============================================================================
 *  tocador midi: transforma as notas em som
 * ============================================================================
 *
 * papel no projeto:
 *   recebe uma lista de EventoMusical (a melodia original ou a composicao)
 *   e toca no alto-falante, tambem consegue salvar a musica em arquivo .mid
 *
 * como funciona:
 *   usa o sintetizador midi que ja vem com o java (pacote javax.sound.midi),
 *   sem nenhuma biblioteca externa, o sintetizador e como um teclado virtual:
 *     - noteOn(nota, volume): "aperta a tecla" e a nota comeca a soar
 *     - noteOff(nota):        "solta a tecla" e a nota para
 *   entre um e outro, o programa espera o tempo da duracao da nota
 *
 * "implements AutoCloseable": permite fechar o sintetizador com close()
 * quando nao for mais usado, liberando o dispositivo de audio
 * ============================================================================
 */
public class TocadorMidi implements AutoCloseable {

    //"final" = depois de preenchidos no construtor, estes atributos nunca trocam de objeto
    private final Synthesizer sintetizador; //o "teclado virtual" do java
    private final MidiChannel canal;        //o canal por onde mandamos as notas (como um instrumento)
    //gerador de numeros aleatorios, usado so para variar um pouco o volume de cada nota
    private final Random random = new Random();

    /**
     * abre o sintetizador e pega o primeiro canal (canal 0)
     * e chamado uma vez, quando a janela do programa e criada
     *
     * @throws MidiUnavailableException se o computador nao tiver sintetizador midi
     */
    public TocadorMidi() throws MidiUnavailableException {
        sintetizador = MidiSystem.getSynthesizer(); //pede ao java o sintetizador padrao
        sintetizador.open();                        //"liga" o sintetizador para poder tocar
        //um sintetizador tem 16 canais (0 a 15), usamos so o primeiro
        canal = sintetizador.getChannels()[0];
    }

    /**
     * troca o instrumento do canal
     * o numero segue o padrao "general midi": 0 = piano, 24 = violao,
     * 40 = violino, 73 = flauta, e assim por diante
     * e chamado quando a pessoa escolhe outro instrumento na tela
     *
     * @param programa o numero do instrumento no padrao general midi
     */
    public void setInstrumento(int programa) {
        //programChange e a mensagem midi de "trocar instrumento"
        canal.programChange(programa);
    }

    /**
     * toca a musica nota por nota
     *
     * importante: este metodo "dorme" (Thread.sleep) enquanto cada nota soa,
     * entao precisa rodar numa thread separada, se rodasse na thread da tela,
     * a janela travaria ate a musica acabar (quem cria a thread e o CompositorApp)
     * thread = uma "linha de execucao", e como ter duas tarefas rodando ao mesmo tempo
     *
     * @param musica      notas a tocar
     * @param bpm         fornece o andamento atual, e consultado a cada nota,
     *                    por isso da para mudar a velocidade durante a musica
     * @param aoTocarNota "aviso" chamado com o indice de cada nota no momento em
     *                    que ela comeca a soar (o CompositorApp usa para acender
     *                    o vertice no desenho)
     */
    public void tocar(List<EventoMusical> musica, IntSupplier bpm, IntConsumer aoTocarNota) {
        //try: se a pessoa apertar "parar", o sleep da erro e o codigo pula para o catch
        try {
            //laco que passa por todas as notas, da primeira (i = 0) ate a ultima
            for (int i = 0; i < musica.size(); i++) {
                //pega a nota da posicao i (numero midi + duracao)
                EventoMusical evento = musica.get(i);
                aoTocarNota.accept(i);                 //avisa a tela: "a nota i comecou"

                int volume = 80 + random.nextInt(20); //volume de 80 a 99: pequena variacao para soar mais "humano"
                //esse sorteio e so para o som, nao tem nada a ver com o grafo
                canal.noteOn(evento.nota(), volume);  //comeca a nota ("aperta a tecla")

                //quanto dura 1 tempo em milissegundos: 60.000 ms (1 minuto) / batidas por minuto
                //ex.: 120 bpm -> 500 ms por tempo, uma nota de 2 tempos dura 1000 ms
                //bpm.getAsInt() le o andamento escolhido na tela neste exato momento
                long msPorTempo = 60_000L / bpm.getAsInt();
                //Thread.sleep faz o programa esperar parado enquanto a nota soa
                Thread.sleep((long) (evento.duracao() * msPorTempo)); //espera a nota soar

                canal.noteOff(evento.nota());         //termina a nota ("solta a tecla")
            }
        } catch (InterruptedException e) {
            //o usuario apertou "parar": a thread foi interrompida no meio do sleep
            //marcamos a interrupcao de novo (boa pratica em java) e saimos do laco
            Thread.currentThread().interrupt();
        } finally {
            //acontecendo o que for (fim normal ou "parar"), desliga todas as notas
            //para nenhuma ficar soando para sempre
            //finally sempre roda, com erro ou sem erro
            canal.allNotesOff();
        }
    }

    /**
     * grava a musica em um arquivo .mid, que pode ser aberto em qualquer
     * player ou programa de musica
     * e chamado quando a pessoa clica em "salvar composicao"
     *
     * um arquivo midi nao guarda som gravado, e sim "instrucoes":
     * em que momento cada nota comeca e termina, o tempo e contado em "ticks"
     * (um tick e um pedacinho bem pequeno de tempo)
     *
     * @param musica      notas a gravar
     * @param bpm         andamento (batidas por minuto)
     * @param instrumento numero do instrumento no padrao general midi
     * @param arquivo     onde salvar no computador
     */
    public static void salvarArquivo(List<EventoMusical> musica, int bpm, int instrumento, File arquivo)
            throws InvalidMidiDataException, IOException {
        final int resolucao = 480; //quantos "ticks" cabem em 1 tempo (seminima)
        Sequence sequencia = new Sequence(Sequence.PPQ, resolucao); //PPQ = pulsos por seminima
        Track trilha = sequencia.createTrack(); //cria a trilha onde os eventos serao colocados

        //andamento: o midi guarda em microssegundos por seminima, usando 3 bytes
        //ex.: 120 bpm -> 60.000.000 / 120 = 500.000 microssegundos
        //os ">>" separam o numero em 3 bytes (do mais alto para o mais baixo)
        int microPorTempo = 60_000_000 / bpm;
        //vetor com os 3 bytes do andamento, (byte) corta o numero para caber em 1 byte
        byte[] dadosTempo = {(byte) (microPorTempo >> 16), (byte) (microPorTempo >> 8), (byte) microPorTempo};
        //o ultimo 0 e o tick: o andamento vale desde o comeco da musica
        trilha.add(new MidiEvent(new MetaMessage(0x51, dadosTempo, 3), 0)); //0x51 = codigo de "andamento"

        //instrumento: escolhido logo no inicio (tick 0), no canal 0
        trilha.add(new MidiEvent(new ShortMessage(ShortMessage.PROGRAM_CHANGE, 0, instrumento, 0), 0));

        //notas: cada nota vira um NOTE_ON (comeco) e um NOTE_OFF (fim)
        long tick = 0; //posicao atual na musica, em ticks
        //passa por cada nota da musica, na ordem
        for (EventoMusical evento : musica) {
            //Math.round arredonda para o inteiro mais proximo
            long duracao = Math.round(evento.duracao() * resolucao); //tempos -> ticks
            //liga a nota no tick atual com volume fixo 90 (no arquivo nao sorteamos volume)
            trilha.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, evento.nota(), 90), tick));
            //desliga a nota depois da duracao dela
            trilha.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, evento.nota(), 0), tick + duracao));
            tick += duracao; //a proxima nota comeca quando esta termina
        }
        MidiSystem.write(sequencia, 1, arquivo); //1 = arquivo midi do tipo 1
    }

    /**
     * fecha o sintetizador, liberando o dispositivo de audio
     * "@Override" avisa que este metodo vem da interface AutoCloseable
     */
    @Override
    public void close() {
        sintetizador.close();
    }
}
