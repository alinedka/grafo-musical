//imports: dizem ao java de quais bibliotecas vem cada classe que usamos neste arquivo
//java.awt: classes basicas de tela (cores, fontes, tamanhos, layouts)
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
//java.io.File: representa um arquivo no computador (usado para salvar o .mid)
import java.io.File;
//java.util: listas e utilitarios
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

//erro que acontece quando o computador nao consegue abrir o som midi
import javax.sound.midi.MidiUnavailableException;
//javax.swing: a biblioteca de janelas, botoes e campos de texto do java
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
 *  compositor app: a janela principal (e o metodo main)
 * ============================================================================
 *
 * papel no projeto:
 *   e por aqui que o programa comeca (metodo main), esta classe monta a tela
 *   com swing e liga as partes do projeto:
 *     - GrafoMusical: o grafo (treinar e compor)
 *     - PainelGrafo:  o desenho do grafo
 *     - TocadorMidi:  o som
 *     - Melodias:     as musicas de exemplo
 *
 * fluxo de uso:
 *   1. escolher (ou digitar) uma melodia e a memoria, e clicar em "treinar grafo"
 *      -> o GrafoMusical monta o grafo e o PainelGrafo desenha
 *   2. clicar em "compor e tocar"
 *      -> o GrafoMusical faz o passeio aleatorio, o TocadorMidi toca a musica
 *         e o PainelGrafo acende cada vertice no momento em que ele soa
 *
 * organizacao da tela:
 *   - esquerda: controles ("1. treinar o grafo" e "2. compor")
 *   - centro:   o desenho do grafo (PainelGrafo)
 *   - embaixo:  abas "lista de adjacencia", "matriz de probabilidades" e "musica gerada"
 *   - rodape:   barra de status (mensagens do que esta acontecendo)
 *
 * "extends JFrame" quer dizer que esta classe e uma janela:
 *   ela herda tudo que uma janela do swing ja sabe fazer (titulo, fechar, tamanho...)
 * ============================================================================
 */
public class CompositorApp extends JFrame {

    /**
     * um instrumento da lista: o nome que aparece na tela e o numero dele
     * no padrao general midi (o numero que o sintetizador entende)
     *
     * record: tipo do java so para guardar dados, cria sozinho o construtor
     * e os metodos nome() e programa()
     * private: so esta classe enxerga o Instrumento
     */
    private record Instrumento(String nome, int programa) {
        //o JComboBox mostra na tela o que o toString devolve
        //por isso devolvemos so o nome (ex.: "Piano") em vez do texto padrao do record
        @Override
        public String toString() {
            return nome;
        }
    }

    //lista fixa dos instrumentos que aparecem na caixa "instrumento"
    //static: pertence a classe, existe uma copia so, e nao uma por janela
    //final: depois de criado, nao pode ser trocado por outro vetor
    //o numero e o codigo general midi: 0 piano, 11 vibrafone, 12 marimba, 24 violao,
    //40 violino, 73 flauta
    private static final Instrumento[] INSTRUMENTOS = {
        new Instrumento("Piano", 0),
        new Instrumento("Vibrafone", 11),
        new Instrumento("Marimba", 12),
        new Instrumento("Violão", 24),
        new Instrumento("Violino", 40),
        new Instrumento("Flauta", 73),
    };

    //------------------------------------------------------------ as partes do projeto

    private final GrafoMusical grafo = new GrafoMusical();  //o grafo (a estrutura de dados)
    private final PainelGrafo painel = new PainelGrafo();   //o desenho do grafo
    private TocadorMidi tocador;                            //o som (null se o audio nao abrir)
    private Thread threadReproducao;                        //thread que esta tocando agora (se houver)
    private List<EventoMusical> melodiaTreinada = new ArrayList<>();   //ultima melodia usada no treino
    private List<EventoMusical> ultimaComposicao = new ArrayList<>();  //ultima musica composta (para salvar)

    //andamento atual em bpm (batidas por minuto), comeca em 110
    //"volatile" garante que a thread que toca a musica
    //enxergue na hora quando o usuario mexe no controle de velocidade
    private volatile int bpm = 110;

    //------------------------------------------------------------ componentes da tela
    //cada atributo abaixo e uma pecinha da janela
    //sao "final" porque cada pecinha e criada uma vez so e nunca trocada por outra

    //caixa de selecao com os nomes das musicas de exemplo (as chaves do mapa Melodias.EXEMPLOS)
    private final JComboBox<String> comboMelodias = new JComboBox<>(Melodias.EXEMPLOS.keySet().toArray(new String[0]));
    //campo de texto onde ficam as notas da melodia (6 linhas, 22 colunas), o usuario pode editar
    private final JTextArea campoMelodia = new JTextArea(6, 22);
    //caixinha de marcar: se marcada, o treino soma a melodia nova ao grafo que ja existe
    private final JCheckBox chkAcumular = new JCheckBox("Somar ao grafo atual");
    //caixa de selecao da memoria: o item 0 e memoria 1, o item 1 e memoria 2, e assim por diante
    private final JComboBox<String> comboMemoria = new JComboBox<>(new String[] {
        "1 nota  (mais criativo, pouco parecido)",
        "2 notas",
        "3 notas",
        "4 notas (recomendado)",
        "5 notas",
    });
    //quantidade de notas: comeca em 40, minimo 4, maximo 400, de 4 em 4
    //JSpinner e o campo de numero com setinhas para cima e para baixo
    private final JSpinner spinnerNotas = new JSpinner(new SpinnerNumberModel(40, 4, 400, 4));
    //andamento: barra deslizante de 50 a 220 bpm, comecando no valor de bpm (110)
    private final JSlider sliderBpm = new JSlider(50, 220, bpm);
    //texto acima da barra, mostra "andamento: 110 bpm" (atualizado em atualizarBpm)
    private final JLabel lblBpm = new JLabel();
    //caixa de selecao do instrumento, usando o vetor INSTRUMENTOS
    private final JComboBox<Instrumento> comboInstrumento = new JComboBox<>(INSTRUMENTOS);
    //as tres areas de texto das abas de baixo (so leitura, criadas por criarAreaTexto)
    private final JTextArea areaAdjacencia = criarAreaTexto(); //aba "lista de adjacencia"
    private final JTextArea areaMatriz = criarAreaTexto();     //aba "matriz de probabilidades"
    private final JTextArea areaMusica = criarAreaTexto();     //aba "musica gerada"
    //barra de status no rodape (comeca com um espaco para ja ter altura)
    private final JLabel lblStatus = new JLabel(" ");

    /**
     * construtor: roda uma vez, quando a janela e criada (no main)
     * abre o som, monta a tela e ja treina a primeira musica
     */
    public CompositorApp() {
        super("Compositor Musical com Grafos");      //titulo da janela (super chama o construtor do JFrame)
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); //clicar no x fecha o programa inteiro

        //tenta abrir o sintetizador, se o computador nao tiver audio,
        //o programa continua funcionando, so que sem som
        //try/catch: "tente fazer isso; se der o erro MidiUnavailableException, faca aquilo"
        try {
            tocador = new TocadorMidi();
        } catch (MidiUnavailableException e) {
            //mostra uma janelinha de erro, e tocador continua null (sem som)
            JOptionPane.showMessageDialog(this, "Não foi possível abrir o sintetizador MIDI:\n" + e.getMessage(),
                    "Erro de áudio", JOptionPane.ERROR_MESSAGE);
        }

        montarInterface(); //cria e posiciona todos os botoes, campos e abas

        //comeca com memoria 4 e a primeira melodia ja treinada, para a tela nao ficar vazia
        comboMemoria.setSelectedIndex(3); //item 3 = "4 notas" (a contagem comeca em 0)
        //pega o texto (as notas) da primeira musica de exemplo e coloca no campo
        campoMelodia.setText(Melodias.EXEMPLOS.values().iterator().next());
        treinar(); //monta o grafo com essa musica

        pack();                       //ajusta o tamanho da janela ao tamanho dos componentes
        setLocationRelativeTo(null);  //null = centraliza a janela na tela
    }

    /**
     * monta todos os componentes da tela e liga cada botao a sua acao
     * e chamado uma vez, pelo construtor
     *
     * layout managers (quem decide onde cada pecinha fica):
     *   - BoxLayout empilha os controles da barra lateral de cima para baixo
     *   - BorderLayout divide a janela em regioes: WEST (esquerda), CENTER e SOUTH (rodape)
     */
    private void montarInterface() {
        //---------- barra lateral ----------
        JPanel lateral = new JPanel(); //JPanel e uma "caixa" que agrupa outros componentes
        lateral.setLayout(new BoxLayout(lateral, BoxLayout.Y_AXIS)); //Y_AXIS = um embaixo do outro
        lateral.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12)); //margem de 12 pixels
        lateral.setPreferredSize(new Dimension(290, 0)); //largura de 290 pixels

        //parte 1 da barra: treinar o grafo
        lateral.add(titulo("1. Treinar o grafo"));
        lateral.add(rotulo("Melodia de exemplo:"));
        lateral.add(alinhar(comboMelodias));
        lateral.add(Box.createVerticalStrut(6)); //espaco vazio de 6 pixels entre os componentes
        lateral.add(rotulo("Notas (NOTA:DURAÇÃO) — pode editar:"));
        campoMelodia.setLineWrap(true);       //quebra a linha quando o texto chega na borda
        campoMelodia.setWrapStyleWord(true);  //quebra entre palavras, sem cortar uma nota no meio
        campoMelodia.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12)); //fonte de largura fixa
        lateral.add(alinhar(new JScrollPane(campoMelodia))); //JScrollPane da barra de rolagem ao campo
        lateral.add(alinhar(chkAcumular));
        lateral.add(Box.createVerticalStrut(4));
        lateral.add(rotulo("Memória do grafo (notas por vértice):"));
        lateral.add(alinhar(comboMemoria));
        lateral.add(Box.createVerticalStrut(6));
        JButton btnTreinar = botao("Treinar grafo"); //botao que chama treinar()
        lateral.add(alinhar(btnTreinar));

        //parte 2 da barra: compor
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

        //botao principal, maior e laranja para chamar atencao
        JButton btnCompor = botao("♪  Compor e tocar");
        btnCompor.setFont(btnCompor.getFont().deriveFont(Font.BOLD, 16f)); //negrito, tamanho 16
        btnCompor.setBackground(new Color(255, 140, 40)); //cor laranja (vermelho, verde, azul)
        btnCompor.setPreferredSize(new Dimension(260, 48));
        btnCompor.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48)); //largura toda, 48 de altura
        lateral.add(alinhar(btnCompor));
        lateral.add(Box.createVerticalStrut(6));
        JButton btnOriginal = botao("Tocar melodia original"); //toca a musica usada no treino
        lateral.add(alinhar(btnOriginal));
        lateral.add(Box.createVerticalStrut(6));
        JButton btnParar = botao("■  Parar"); //interrompe a musica que esta tocando
        lateral.add(alinhar(btnParar));
        lateral.add(Box.createVerticalStrut(6));
        JButton btnSalvar = botao("Salvar composição (.mid)"); //grava a ultima composicao em arquivo
        lateral.add(alinhar(btnSalvar));
        lateral.add(Box.createVerticalGlue()); //"cola" elastica: empurra tudo para cima

        //---------- abas de texto ----------
        JTabbedPane abas = new JTabbedPane(); //componente com abas clicaveis
        abas.addTab("Lista de adjacência", new JScrollPane(areaAdjacencia));
        areaMatriz.setLineWrap(false); //matriz larga: rolagem horizontal em vez de quebrar linha
        abas.addTab("Matriz de probabilidades", new JScrollPane(areaMatriz));
        abas.addTab("Música gerada", new JScrollPane(areaMusica));
        abas.setPreferredSize(new Dimension(720, 200)); //tamanho inicial da area das abas

        //divide o centro em duas partes: o desenho em cima (75%) e as abas embaixo
        //o usuario pode arrastar a divisoria entre as duas
        JSplitPane divisao = new JSplitPane(JSplitPane.VERTICAL_SPLIT, painel, abas);
        divisao.setResizeWeight(0.75); //ao aumentar a janela, 75% do espaco extra vai para o desenho

        lblStatus.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10)); //margem da barra de status

        //coloca as tres grandes partes na janela
        setLayout(new BorderLayout());
        add(lateral, BorderLayout.WEST);     //barra lateral na esquerda
        add(divisao, BorderLayout.CENTER);   //desenho e abas no centro
        add(lblStatus, BorderLayout.SOUTH);  //status no rodape

        //---------- eventos ----------
        //cada "addActionListener" diz o que fazer quando o usuario clica ou escolhe algo
        //o action listener e quem "escuta" o clique e roda o codigo quando ele acontece
        //"e -> treinar()" e uma lambda: um jeito curto de escrever "ao clicar, chame treinar()"
        //o "e" e o evento (informacoes do clique), que aqui nao usamos

        //escolheu uma musica na lista: coloca as notas dela no campo de texto
        //(busca no mapa Melodias.EXEMPLOS o texto da musica pelo nome escolhido)
        comboMelodias.addActionListener(e ->
                campoMelodia.setText(Melodias.EXEMPLOS.get((String) comboMelodias.getSelectedItem())));
        btnTreinar.addActionListener(e -> treinar());        //botao treinar grafo
        btnCompor.addActionListener(e -> comporETocar());    //botao compor e tocar
        //botao tocar melodia original: toca a melodia do treino, acendendo os vertices dela
        btnOriginal.addActionListener(e ->
                tocar(melodiaTreinada, grafo.caminhoDaMelodia(melodiaTreinada), "melodia original"));
        comboMemoria.addActionListener(e -> mudarMemoria()); //trocou a memoria: reconstroi o grafo
        btnParar.addActionListener(e -> pararReproducao());  //botao parar
        btnSalvar.addActionListener(e -> salvar());          //botao salvar

        //a barra de andamento usa um change listener: avisa a cada movimento da barra
        sliderBpm.addChangeListener(e -> atualizarBpm());
        atualizarBpm(); //chama uma vez agora so para escrever o texto inicial do andamento
        comboInstrumento.addActionListener(e -> aplicarInstrumento()); //trocou o instrumento
    }

    //================= acoes =================
    //metodos chamados quando o usuario clica nos botoes

    /**
     * botao "treinar grafo": le a melodia do campo de texto e monta o grafo com ela
     *
     *   1. converte o texto em notas (Melodias.ler)
     *   2. se "somar ao grafo atual" nao estiver marcado, apaga o grafo antigo
     *   3. treina o grafo (GrafoMusical.treinar)
     *   4. atualiza o desenho e as abas
     */
    private void treinar() {
        pararReproducao(); //se alguma musica estiver tocando, para antes de mexer no grafo
        List<EventoMusical> melodia; //vai guardar as notas lidas do campo de texto
        try {
            melodia = Melodias.ler(campoMelodia.getText()); //texto -> lista de notas
        } catch (IllegalArgumentException ex) {
            //o texto tinha alguma nota escrita errado: mostra o aviso e sai do metodo
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Melodia inválida", JOptionPane.WARNING_MESSAGE);
            return; //return encerra o metodo aqui, sem treinar
        }
        //com menos de 2 notas nao existe nenhuma seta (nao tem "depois de x veio y")
        if (melodia.size() < 2) {
            JOptionPane.showMessageDialog(this, "Digite pelo menos duas notas.", "Melodia inválida",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        //"!" quer dizer "nao": se a caixinha somar ao grafo atual nao estiver marcada...
        if (!chkAcumular.isSelected()) {
            grafo.limpar();          //comeca um grafo novo, do zero
        }
        grafo.treinar(melodia);      //aqui o grafo e montado (vertices e arestas)
        melodiaTreinada = melodia;   //guarda para o botao "tocar melodia original"

        atualizarTelaDoGrafo(); //redesenha o grafo e atualiza as abas
        //escreve no rodape um resumo; cada %d e trocado por um numero, na ordem
        lblStatus.setText(String.format("Grafo treinado com %d notas (memória %d): %d vértices e %d arestas.",
                melodia.size(), grafo.getMemoria(), grafo.numeroVertices(), grafo.numeroArestas()));
    }

    /**
     * lista "memoria do grafo": reconstroi o grafo com outra memoria,
     * usando as mesmas melodias ja treinadas
     * e chamado sempre que o usuario escolhe outro item na caixa de memoria
     */
    private void mudarMemoria() {
        pararReproducao();
        //o item 0 da lista e "1 nota", o item 1 e "2 notas"... por isso o "+ 1"
        grafo.setMemoria(comboMemoria.getSelectedIndex() + 1);
        atualizarTelaDoGrafo();
        lblStatus.setText(String.format("Grafo reconstruído com memória %d: %d vértices e %d arestas.",
                grafo.getMemoria(), grafo.numeroVertices(), grafo.numeroArestas()));
    }

    /**
     * redesenha o grafo e atualiza as abas de lista de adjacencia e matriz
     * e chamado depois de treinar e depois de mudar a memoria
     */
    private void atualizarTelaDoGrafo() {
        painel.setGrafo(grafo); //manda o grafo novo para o desenho
        areaAdjacencia.setText(grafo.descreverListaAdjacencia()); //texto da lista de adjacencia
        areaAdjacencia.setCaretPosition(0); //volta a rolagem para o comeco do texto
        areaMatriz.setText(grafo.descreverMatriz()); //texto da matriz de probabilidades
        areaMatriz.setCaretPosition(0);
        //a composicao anterior nao vale mais para o grafo novo
        ultimaComposicao = new ArrayList<>(); //lista vazia: nao ha nada para salvar
        areaMusica.setText("Clique em \"Compor e tocar\" para gerar uma música com este grafo.");
    }

    /**
     * botao "compor e tocar":
     *   1. faz o passeio aleatorio no grafo (GrafoMusical.compor)
     *   2. escreve o caminho na aba "musica gerada"
     *   3. toca a musica, acendendo cada vertice no desenho
     */
    private void comporETocar() {
        //sem grafo treinado nao ha por onde passear
        if (grafo.estaVazio()) {
            JOptionPane.showMessageDialog(this, "Treine o grafo primeiro.");
            return;
        }
        //aqui acontece a composicao: o passeio aleatorio pelo grafo
        //(Integer) converte o valor do campo de numero para inteiro (a quantidade de notas pedida)
        Composicao composicao = grafo.compor((Integer) spinnerNotas.getValue());
        ultimaComposicao = composicao.musica(); //guarda as notas para tocar e para salvar

        //monta o texto da aba: "do4 → sol4 → fa4 ..." (cada "→" e uma aresta sorteada)
        //StringJoiner junta varios textos colocando o separador " → " entre eles
        StringJoiner caminho = new StringJoiner(" → ");
        //for each: para cada nota (ev) da musica composta, na ordem...
        for (EventoMusical ev : ultimaComposicao) {
            caminho.add(Nota.nomePt(ev.nota())); //...adiciona o nome dela em portugues
        }
        //e a mesma musica no formato de entrada: "c4:1 g4:1 f4:1 ..." (separado por espaco)
        StringJoiner formato = new StringJoiner(" ");
        for (EventoMusical ev : ultimaComposicao) {
            formato.add(ev.toString()); //toString do EventoMusical devolve "nota:duracao"
        }
        //escreve tudo na aba "musica gerada" ("\n" e uma quebra de linha)
        areaMusica.setText("PASSEIO ALEATÓRIO NO GRAFO (" + ultimaComposicao.size() + " notas)\n\n"
                + caminho + "\n\nNo formato de entrada (dá para copiar e treinar com ela):\n" + formato);
        areaMusica.setCaretPosition(0);

        //toca a musica, passando tambem o caminho para acender os vertices
        tocar(ultimaComposicao, composicao.caminho(), "composição");
    }

    /**
     * toca uma lista de notas numa thread separada, para nao travar a janela
     * "caminho" diz qual vertice acender no grafo a cada nota (pode ter null)
     *
     * o que e uma thread: uma "linha de execucao", um pedaco do programa
     * que roda ao mesmo tempo que o resto
     *
     * por que outra thread? o TocadorMidi fica esperando (sleep) enquanto cada
     * nota soa, se isso rodasse na thread da tela, a janela ficaria congelada
     * ate a musica acabar, e nem o botao "parar" funcionaria
     *
     * @param musica    as notas que vao tocar
     * @param caminho   o vertice de cada nota (para acender no desenho)
     * @param descricao texto que aparece na barra de status (ex.: "composicao")
     */
    private void tocar(List<EventoMusical> musica, List<Vertice> caminho, String descricao) {
        //sem som (tocador null) ou sem notas: nao ha o que tocar
        //"||" quer dizer "ou"
        if (tocador == null || musica.isEmpty()) {
            return;
        }
        pararReproducao();      //se ja estava tocando algo, para antes
        aplicarInstrumento();   //garante que o instrumento escolhido esta valendo

        //cria a thread; o codigo dentro de "() -> { ... }" e o que ela vai executar
        threadReproducao = new Thread(() -> {
            //a cada nota, o tocador avisa o indice dela, a tela so pode ser alterada
            //pela thread do swing, por isso usamos SwingUtilities.invokeLater
            //("invokeLater" = "swing, rode isto na sua thread assim que puder")
            //"() -> bpm" entrega o andamento atual sempre que o tocador pedir,
            //assim mexer na barra muda a velocidade no meio da musica
            tocador.tocar(musica, () -> bpm, indice -> SwingUtilities.invokeLater(() -> {
                Vertice atual = caminho.get(indice); //vertice da nota que comecou a soar agora
                //vertice da nota anterior (se for a primeira nota, nao existe anterior: null)
                Vertice anterior = indice > 0 ? caminho.get(indice - 1) : null;
                painel.destacar(anterior, atual); //acende o vertice atual e a seta usada
                //mostra no rodape qual nota esta tocando (indice + 1 porque o indice comeca em 0)
                lblStatus.setText(String.format("Tocando %s: nota %d de %d — %s",
                        descricao, indice + 1, musica.size(), Nota.nomePt(musica.get(indice).nota())));
            }));
            //a musica acabou (ou foi parada): apaga os destaques
            SwingUtilities.invokeLater(() -> {
                painel.limparDestaque();
                lblStatus.setText("Pronto.");
            });
        }, "reproducao-midi"); //nome da thread (ajuda a identificar se der erro)
        threadReproducao.setDaemon(true);   //"daemon": nao impede o programa de fechar
        threadReproducao.start();           //start comeca a rodar a thread de fato
    }

    /**
     * botao "parar": interrompe a thread que esta tocando
     * o interrupt() acorda o sleep do TocadorMidi, que entao para a musica
     * tambem e chamado antes de treinar, de compor e de trocar a memoria
     */
    private void pararReproducao() {
        //so faz algo se existe uma thread e ela ainda esta viva (tocando)
        //"&&" quer dizer "e": as duas condicoes precisam ser verdadeiras
        if (threadReproducao != null && threadReproducao.isAlive()) {
            threadReproducao.interrupt(); //pede para a thread parar
            try {
                threadReproducao.join(500);   //espera ate meio segundo a thread terminar
            } catch (InterruptedException ignored) {
                //se esta espera for interrompida, marcamos de novo a interrupcao
                //(e a forma recomendada em java de nao "perder" esse aviso)
                Thread.currentThread().interrupt();
            }
        }
        painel.limparDestaque(); //apaga o vertice laranja do desenho
    }

    /**
     * botao "salvar composicao (.mid)": abre a janela de escolher arquivo
     * e grava a ultima composicao como arquivo midi
     */
    private void salvar() {
        //nada composto ainda: avisa e sai
        if (ultimaComposicao.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Componha uma música primeiro.");
            return;
        }
        JFileChooser escolhedor = new JFileChooser(); //a janela padrao de "salvar como"
        //mostra so arquivos .mid na janela
        escolhedor.setFileFilter(new FileNameExtensionFilter("Arquivo MIDI (*.mid)", "mid"));
        escolhedor.setSelectedFile(new File("composicao.mid")); //nome sugerido
        //showSaveDialog abre a janela e espera; se o usuario cancelar, sai do metodo
        if (escolhedor.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File arquivo = escolhedor.getSelectedFile(); //o arquivo que o usuario escolheu
        //se o nome nao termina em ".mid", acrescenta ".mid" no fim
        if (!arquivo.getName().toLowerCase().endsWith(".mid")) {
            arquivo = new File(arquivo.getParentFile(), arquivo.getName() + ".mid");
        }
        try {
            //pega o instrumento escolhido para gravar junto no arquivo
            Instrumento inst = (Instrumento) comboInstrumento.getSelectedItem();
            //grava as notas, o andamento e o instrumento no arquivo
            TocadorMidi.salvarArquivo(ultimaComposicao, bpm, inst.programa(), arquivo);
            lblStatus.setText("Composição salva em " + arquivo.getAbsolutePath());
        } catch (Exception ex) {
            //qualquer erro ao gravar (pasta sem permissao, disco cheio...): mostra a mensagem
            JOptionPane.showMessageDialog(this, "Erro ao salvar: " + ex.getMessage(), "Erro",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * controle de andamento: guarda o novo bpm (a musica tocando ja usa na proxima nota)
     * e chamado toda vez que a barra deslizante se move
     */
    private void atualizarBpm() {
        bpm = sliderBpm.getValue();                   //le o valor atual da barra
        lblBpm.setText("Andamento: " + bpm + " BPM"); //atualiza o texto acima da barra
    }

    /**
     * manda para o tocador o instrumento escolhido na lista
     * e chamado quando o usuario troca o instrumento e antes de cada musica tocar
     */
    private void aplicarInstrumento() {
        //se nao ha som (tocador null), nao faz nada
        if (tocador != null) {
            //pega o item escolhido, le o numero general midi dele e manda para o tocador
            tocador.setInstrumento(((Instrumento) comboInstrumento.getSelectedItem()).programa());
        }
    }

    //================= auxiliares de interface =================
    //metodos pequenos que criam componentes ja configurados,
    //para nao repetir o mesmo codigo varias vezes em montarInterface()
    //sao static porque nao dependem de nenhum atributo da janela

    /** cria uma area de texto so de leitura, com fonte de largura fixa (usada nas abas) */
    private static JTextArea criarAreaTexto() {
        JTextArea area = new JTextArea();
        area.setEditable(false);  //o usuario nao consegue digitar nela
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13)); //letras alinhadas como tabela
        area.setLineWrap(true);      //quebra a linha na borda
        area.setWrapStyleWord(true); //quebra entre palavras
        return area;
    }

    /** cria um titulo de secao (ex.: "1. treinar o grafo"), em negrito e maior */
    private static JLabel titulo(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 15f)); //negrito, tamanho 15
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0)); //6 pixels de espaco embaixo
        l.setAlignmentX(Component.LEFT_ALIGNMENT); //alinhado a esquerda
        return l;
    }

    /** cria um texto simples (rotulo) alinhado a esquerda, que fica acima de cada campo */
    private static JLabel rotulo(String texto) {
        JLabel l = new JLabel(texto);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    /** cria um botao que pode esticar na largura, com no maximo 32 pixels de altura */
    private static JButton botao(String texto) {
        JButton b = new JButton(texto);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        return b;
    }

    /**
     * alinha o componente a esquerda e faz ele ocupar a largura toda da barra lateral
     * "<T extends javax.swing.JComponent>" e um generico: o metodo aceita qualquer
     * componente do swing e devolve o mesmo tipo que recebeu
     */
    private static <T extends javax.swing.JComponent> T alinhar(T componente) {
        componente.setAlignmentX(Component.LEFT_ALIGNMENT);
        //instanceof pergunta "este componente e do tipo x?"
        //campos com rolagem e botoes ja cuidam do proprio tamanho, entao ficam de fora
        if (!(componente instanceof JScrollPane) && !(componente instanceof JButton)) {
            //largura maxima infinita (estica) e altura igual a preferida (nao estica para baixo)
            componente.setMaximumSize(new Dimension(Integer.MAX_VALUE, componente.getPreferredSize().height));
        }
        return componente;
    }

    /**
     * ponto de partida do programa
     * o java procura este metodo "public static void main" para comecar a rodar
     * usa o visual do sistema operacional e abre a janela
     * invokeLater cria a janela na thread do swing, que e a forma correta em java
     */
    public static void main(String[] args) {
        try {
            //deixa os botoes e campos com a cara do windows (ou do sistema em uso)
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            //se nao der, usa o visual padrao do java
        }
        //cria a janela e torna ela visivel na tela
        SwingUtilities.invokeLater(() -> new CompositorApp().setVisible(true));
    }
}
