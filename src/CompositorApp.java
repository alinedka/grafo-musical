import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import javax.sound.midi.MidiUnavailableException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * ============================================================================
 *  COMPOSITOR APP: a janela principal (e o método main)
 * ============================================================================
 *
 * PAPEL NO PROJETO:
 *   É por aqui que o programa começa (método main). Esta classe monta a tela
 *   com Swing e LIGA AS PARTES do projeto:
 *     - GrafoMusical: o grafo (treinar e compor)
 *     - PainelGrafo:  o desenho do grafo
 *     - TocadorMidi:  o som
 *     - Melodias:     as músicas de exemplo
 *
 * FLUXO DE USO:
 *   1. Escolher (ou digitar) uma melodia e a memória, e clicar em "Treinar grafo"
 *      -> o GrafoMusical monta o grafo e o PainelGrafo desenha
 *   2. Clicar em "Compor e tocar"
 *      -> o GrafoMusical faz o passeio aleatório, o TocadorMidi toca a música
 *         e o PainelGrafo acende cada vértice no momento em que ele soa
 *
 * ORGANIZAÇÃO DA TELA:
 *   - Esquerda: controles ("1. Treinar o grafo" e "2. Compor")
 *   - Centro:   o desenho do grafo (PainelGrafo)
 *   - Embaixo:  abas "Lista de adjacência", "Matriz de probabilidades" e "Música gerada"
 *   - Rodapé:   barra de status (mensagens do que está acontecendo)
 * ============================================================================
 */
public class CompositorApp extends JFrame {

    /**
     * Um instrumento da lista: o nome que aparece na tela e o número dele
     * no padrão General MIDI (o número que o sintetizador entende)
     */
    private record Instrumento(String nome, int programa) {
        @Override
        public String toString() {
            return nome;
        }
    }

    private static final Instrumento[] INSTRUMENTOS = {
        new Instrumento("Piano", 0),
        new Instrumento("Vibrafone", 11),
        new Instrumento("Marimba", 12),
        new Instrumento("Violão", 24),
        new Instrumento("Violino", 40),
        new Instrumento("Flauta", 73),
    };

    //------------------------------------------------------------ AS PARTES DO PROJETO

    private final GrafoMusical grafo = new GrafoMusical();  //o grafo (a estrutura de dados)
    private final PainelGrafo painel = new PainelGrafo();   //o desenho do grafo
    private TocadorMidi tocador;                            //o som (null se o áudio não abrir)
    private Thread threadReproducao;                        //thread que está tocando agora (se houver)
    private List<EventoMusical> melodiaTreinada = new ArrayList<>();   //última melodia usada no treino
    private List<EventoMusical> ultimaComposicao = new ArrayList<>();  //última música composta (para salvar)

    //Andamento atual. "volatile" garante que a thread que toca a música
    //enxergue na hora quando o usuário mexe no controle de velocidade
    private volatile int bpm = 110;

    //------------------------------------------------------------ COMPONENTES DA TELA
    private final JComboBox<String> comboMelodias = new JComboBox<>(Melodias.EXEMPLOS.keySet().toArray(new String[0]));
    private final JTextArea campoMelodia = new JTextArea(6, 22);
    private final JCheckBox chkAcumular = new JCheckBox("Somar ao grafo atual");
    private final JComboBox<String> comboMemoria = new JComboBox<>(new String[] {
        "1 nota  (mais criativo, pouco parecido)",
        "2 notas",
        "3 notas",
        "4 notas (recomendado)",
        "5 notas",
    });
    //Quantidade de notas: começa em 40, mínimo 4, máximo 400, de 4 em 4
    private final JSpinner spinnerNotas = new JSpinner(new SpinnerNumberModel(40, 4, 400, 4));
    //Andamento: de 50 a 220 BPM
    private final JSlider sliderBpm = new JSlider(50, 220, bpm);
    private final JLabel lblBpm = new JLabel();
    private final JComboBox<Instrumento> comboInstrumento = new JComboBox<>(INSTRUMENTOS);
    private final JTextArea areaAdjacencia = criarAreaTexto();
    private final JTextArea areaMatriz = criarAreaTexto();
    private final JTextArea areaMusica = criarAreaTexto();
    private final JLabel lblStatus = new JLabel(" ");

    /** Cria a janela: abre o som, monta a tela e já treina a primeira música */
    public CompositorApp() {
        super("Compositor Musical com Grafos");      //título da janela
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //Tenta abrir o sintetizador. Se o computador não tiver áudio,
        //o programa continua funcionando, só que sem som
        try {
            tocador = new TocadorMidi();
        } catch (MidiUnavailableException e) {
            JOptionPane.showMessageDialog(this, "Não foi possível abrir o sintetizador MIDI:\n" + e.getMessage(),
                    "Erro de áudio", JOptionPane.ERROR_MESSAGE);
        }

        montarInterface();

        //Começa com memória 4 e a primeira melodia já treinada, para a tela não ficar vazia
        comboMemoria.setSelectedIndex(3);
        campoMelodia.setText(Melodias.EXEMPLOS.values().iterator().next());
        treinar();

        pack();
        setLocationRelativeTo(null);
    }

    /**
     * Monta todos os componentes da tela e liga cada botão à sua ação
     * BoxLayout empilha os controles da barra lateral de cima para baixo
     */
    private void montarInterface() {
        //---------- Barra lateral ----------
        JPanel lateral = new JPanel();
        lateral.setLayout(new BoxLayout(lateral, BoxLayout.Y_AXIS));
        lateral.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        lateral.setPreferredSize(new Dimension(290, 0));

        lateral.add(titulo("1. Treinar o grafo"));
        lateral.add(rotulo("Melodia de exemplo:"));
        lateral.add(alinhar(comboMelodias));
        lateral.add(Box.createVerticalStrut(6));
        lateral.add(rotulo("Notas (NOTA:DURAÇÃO) — pode editar:"));
        campoMelodia.setLineWrap(true);
        campoMelodia.setWrapStyleWord(true);
        campoMelodia.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        lateral.add(alinhar(new JScrollPane(campoMelodia)));
        lateral.add(alinhar(chkAcumular));
        lateral.add(Box.createVerticalStrut(4));
        lateral.add(rotulo("Memória do grafo (notas por vértice):"));
        lateral.add(alinhar(comboMemoria));
        lateral.add(Box.createVerticalStrut(6));
        JButton btnTreinar = botao("Treinar grafo");
        lateral.add(alinhar(btnTreinar));

        lateral.add(Box.createVerticalStrut(18));
        lateral.add(titulo("2. Compor"));
        lateral.add(rotulo("Quantidade de notas:"));
        lateral.add(alinhar(spinnerNotas));
        lateral.add(Box.createVerticalStrut(6));
        lateral.add(alinhar(lblBpm));
        lateral.add(alinhar(sliderBpm));
        lateral.add(rotulo("Instrumento:"));
        lateral.add(alinhar(comboInstrumento));
        lateral.add(Box.createVerticalStrut(10));

        JButton btnCompor = botao("♪  Compor e tocar");
        btnCompor.setFont(btnCompor.getFont().deriveFont(Font.BOLD, 16f));
        btnCompor.setBackground(new Color(255, 140, 40));
        btnCompor.setPreferredSize(new Dimension(260, 48));
        btnCompor.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        lateral.add(alinhar(btnCompor));
        lateral.add(Box.createVerticalStrut(6));
        JButton btnOriginal = botao("Tocar melodia original");
        lateral.add(alinhar(btnOriginal));
        lateral.add(Box.createVerticalStrut(6));
        JButton btnParar = botao("■  Parar");
        lateral.add(alinhar(btnParar));
        lateral.add(Box.createVerticalStrut(6));
        JButton btnSalvar = botao("Salvar composição (.mid)");
        lateral.add(alinhar(btnSalvar));
        lateral.add(Box.createVerticalGlue());

        //---------- Abas de texto ----------
        JTabbedPane abas = new JTabbedPane();
        abas.addTab("Lista de adjacência", new JScrollPane(areaAdjacencia));
        areaMatriz.setLineWrap(false); //matriz larga: rolagem horizontal em vez de quebrar linha
        abas.addTab("Matriz de probabilidades", new JScrollPane(areaMatriz));
        abas.addTab("Música gerada", new JScrollPane(areaMusica));
        abas.setPreferredSize(new Dimension(720, 200));

        //Divide o centro em duas partes: o desenho em cima (75%) e as abas embaixo
        JSplitPane divisao = new JSplitPane(JSplitPane.VERTICAL_SPLIT, painel, abas);
        divisao.setResizeWeight(0.75);

        lblStatus.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));

        setLayout(new BorderLayout());
        add(lateral, BorderLayout.WEST);
        add(divisao, BorderLayout.CENTER);
        add(lblStatus, BorderLayout.SOUTH);

        //---------- Eventos ----------
        //Cada "addActionListener" diz o que fazer quando o usuário clica ou escolhe algo
        //"e -> treinar()" é uma lambda: um jeito curto de escrever "ao clicar, chame treinar()"

        //Escolheu uma música na lista: coloca as notas dela no campo de texto
        comboMelodias.addActionListener(e ->
                campoMelodia.setText(Melodias.EXEMPLOS.get((String) comboMelodias.getSelectedItem())));
        btnTreinar.addActionListener(e -> treinar());
        btnCompor.addActionListener(e -> comporETocar());
        btnOriginal.addActionListener(e ->
                tocar(melodiaTreinada, grafo.caminhoDaMelodia(melodiaTreinada), "melodia original"));
        comboMemoria.addActionListener(e -> mudarMemoria());
        btnParar.addActionListener(e -> pararReproducao());
        btnSalvar.addActionListener(e -> salvar());

        sliderBpm.addChangeListener(e -> atualizarBpm());
        atualizarBpm();
        comboInstrumento.addActionListener(e -> aplicarInstrumento());
    }

    //================= Ações =================

    /**
     * Botão "Treinar grafo": lê a melodia do campo de texto e MONTA O GRAFO com ela
     *
     *   1. Converte o texto em notas (Melodias.ler)
     *   2. Se "Somar ao grafo atual" NÃO estiver marcado, apaga o grafo antigo
     *   3. Treina o grafo (GrafoMusical.treinar)
     *   4. Atualiza o desenho e as abas
     */
    private void treinar() {
        pararReproducao();
        List<EventoMusical> melodia;
        try {
            melodia = Melodias.ler(campoMelodia.getText()); //texto -> lista de notas
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Melodia inválida", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (melodia.size() < 2) {
            JOptionPane.showMessageDialog(this, "Digite pelo menos duas notas.", "Melodia inválida",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!chkAcumular.isSelected()) {
            grafo.limpar();          //começa um grafo novo, do zero
        }
        grafo.treinar(melodia);      //aqui o grafo é montado (vértices e arestas)
        melodiaTreinada = melodia;

        atualizarTelaDoGrafo();
        lblStatus.setText(String.format("Grafo treinado com %d notas (memória %d): %d vértices e %d arestas.",
                melodia.size(), grafo.getMemoria(), grafo.numeroVertices(), grafo.numeroArestas()));
    }

    /**
     * Lista "Memória do grafo": reconstrói o grafo com outra memória,
     * usando as mesmas melodias já treinadas
     */
    private void mudarMemoria() {
        pararReproducao();
        //O item 0 da lista é "1 nota", o item 1 é "2 notas"... por isso o "+ 1"
        grafo.setMemoria(comboMemoria.getSelectedIndex() + 1);
        atualizarTelaDoGrafo();
        lblStatus.setText(String.format("Grafo reconstruído com memória %d: %d vértices e %d arestas.",
                grafo.getMemoria(), grafo.numeroVertices(), grafo.numeroArestas()));
    }

    /** Redesenha o grafo e atualiza as abas de lista de adjacência e matriz */
    private void atualizarTelaDoGrafo() {
        painel.setGrafo(grafo);
        areaAdjacencia.setText(grafo.descreverListaAdjacencia());
        areaAdjacencia.setCaretPosition(0);
        areaMatriz.setText(grafo.descreverMatriz());
        areaMatriz.setCaretPosition(0);
        //A composição anterior não vale mais para o grafo novo
        ultimaComposicao = new ArrayList<>();
        areaMusica.setText("Clique em \"Compor e tocar\" para gerar uma música com este grafo.");
    }

    /**
     * Botão "Compor e tocar":
     *   1. Faz o PASSEIO ALEATÓRIO no grafo (GrafoMusical.compor)
     *   2. Escreve o caminho na aba "Música gerada"
     *   3. Toca a música, acendendo cada vértice no desenho
     */
    private void comporETocar() {
        if (grafo.estaVazio()) {
            JOptionPane.showMessageDialog(this, "Treine o grafo primeiro.");
            return;
        }
        //Aqui acontece a composição: o passeio aleatório pelo grafo
        Composicao composicao = grafo.compor((Integer) spinnerNotas.getValue());
        ultimaComposicao = composicao.musica();

        //Monta o texto da aba: "Dó4 → Sol4 → Fá4 ..." (cada "→" é uma aresta sorteada)
        StringJoiner caminho = new StringJoiner(" → ");
        for (EventoMusical ev : ultimaComposicao) {
            caminho.add(Nota.nomePt(ev.nota()));
        }
        //E a mesma música no formato de entrada: "C4:1 G4:1 F4:1 ..."
        StringJoiner formato = new StringJoiner(" ");
        for (EventoMusical ev : ultimaComposicao) {
            formato.add(ev.toString());
        }
        areaMusica.setText("PASSEIO ALEATÓRIO NO GRAFO (" + ultimaComposicao.size() + " notas)\n\n"
                + caminho + "\n\nNo formato de entrada (dá para copiar e treinar com ela):\n" + formato);
        areaMusica.setCaretPosition(0);

        tocar(ultimaComposicao, composicao.caminho(), "composição");
    }

    /**
     * Toca uma lista de notas numa THREAD SEPARADA, para não travar a janela
     * "caminho" diz qual vértice acender no grafo a cada nota (pode ter null)
     *
     * Por que outra thread? O TocadorMidi fica esperando (sleep) enquanto cada
     * nota soa. Se isso rodasse na thread da tela, a janela ficaria congelada
     * até a música acabar, e nem o botão "Parar" funcionaria
     */
    private void tocar(List<EventoMusical> musica, List<Vertice> caminho, String descricao) {
        if (tocador == null || musica.isEmpty()) {
            return;
        }
        pararReproducao();      //se já estava tocando algo, para antes
        aplicarInstrumento();

        threadReproducao = new Thread(() -> {
            //A cada nota, o tocador avisa o índice dela. A tela só pode ser alterada
            //pela thread do Swing, por isso usamos SwingUtilities.invokeLater
            tocador.tocar(musica, () -> bpm, indice -> SwingUtilities.invokeLater(() -> {
                Vertice atual = caminho.get(indice);
                Vertice anterior = indice > 0 ? caminho.get(indice - 1) : null;
                painel.destacar(anterior, atual);
                lblStatus.setText(String.format("Tocando %s: nota %d de %d — %s",
                        descricao, indice + 1, musica.size(), Nota.nomePt(musica.get(indice).nota())));
            }));
            //A música acabou: apaga os destaques
            SwingUtilities.invokeLater(() -> {
                painel.limparDestaque();
                lblStatus.setText("Pronto.");
            });
        }, "reproducao-midi");
        threadReproducao.setDaemon(true);   //"daemon": não impede o programa de fechar
        threadReproducao.start();
    }

    /**
     * Botão "Parar": interrompe a thread que está tocando
     * O interrupt() acorda o sleep do TocadorMidi, que então para a música
     */
    private void pararReproducao() {
        if (threadReproducao != null && threadReproducao.isAlive()) {
            threadReproducao.interrupt();
            try {
                threadReproducao.join(500);   //espera até meio segundo a thread terminar
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        painel.limparDestaque();
    }

    /**
     * Botão "Salvar composição (.mid)": abre a janela de escolher arquivo
     * e grava a última composição como arquivo MIDI
     */
    private void salvar() {
        if (ultimaComposicao.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Componha uma música primeiro.");
            return;
        }
        JFileChooser escolhedor = new JFileChooser();
        escolhedor.setFileFilter(new FileNameExtensionFilter("Arquivo MIDI (*.mid)", "mid"));
        escolhedor.setSelectedFile(new File("composicao.mid"));
        if (escolhedor.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File arquivo = escolhedor.getSelectedFile();
        if (!arquivo.getName().toLowerCase().endsWith(".mid")) {
            arquivo = new File(arquivo.getParentFile(), arquivo.getName() + ".mid");
        }
        try {
            Instrumento inst = (Instrumento) comboInstrumento.getSelectedItem();
            TocadorMidi.salvarArquivo(ultimaComposicao, bpm, inst.programa(), arquivo);
            lblStatus.setText("Composição salva em " + arquivo.getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro ao salvar: " + ex.getMessage(), "Erro",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Controle de andamento: guarda o novo BPM (a música tocando já usa na próxima nota) */
    private void atualizarBpm() {
        bpm = sliderBpm.getValue();
        lblBpm.setText("Andamento: " + bpm + " BPM");
    }

    /** Manda para o tocador o instrumento escolhido na lista */
    private void aplicarInstrumento() {
        if (tocador != null) {
            tocador.setInstrumento(((Instrumento) comboInstrumento.getSelectedItem()).programa());
        }
    }

    //================= Auxiliares de interface =================
    //Métodos pequenos que criam componentes já configurados,
    //para não repetir o mesmo código várias vezes em montarInterface()

    private static JTextArea criarAreaTexto() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private static JLabel titulo(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 15f));
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JLabel rotulo(String texto) {
        JLabel l = new JLabel(texto);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JButton botao(String texto) {
        JButton b = new JButton(texto);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        return b;
    }

    /** Alinha o componente à esquerda e faz ele ocupar a largura toda da barra lateral */
    private static <T extends javax.swing.JComponent> T alinhar(T componente) {
        componente.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (!(componente instanceof JScrollPane) && !(componente instanceof JButton)) {
            componente.setMaximumSize(new Dimension(Integer.MAX_VALUE, componente.getPreferredSize().height));
        }
        return componente;
    }

    /**
     * PONTO DE PARTIDA DO PROGRAMA
     * Usa o visual do sistema operacional e abre a janela
     * invokeLater cria a janela na thread do Swing, que é a forma correta em Java
     */
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            //se não der, usa o visual padrão do Java
        }
        SwingUtilities.invokeLater(() -> new CompositorApp().setVisible(true));
    }
}
