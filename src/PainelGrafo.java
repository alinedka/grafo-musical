//imports do java.awt: ferramentas de desenho 2d (cores, linhas, fontes, formas)
import java.awt.BasicStroke;     //grossura e estilo da linha (continua, tracejada...)
import java.awt.Color;           //uma cor (vermelho, verde, azul e transparencia)
import java.awt.Dimension;       //largura x altura
import java.awt.Font;            //fonte das letras
import java.awt.FontMetrics;     //medidas de um texto (largura, altura) numa fonte
import java.awt.Graphics;        //o "pincel" basico que o java entrega para desenhar
import java.awt.Graphics2D;      //o pincel mais completo (curvas, transparencia, suavizacao)
import java.awt.Point;           //um ponto (x, y) com numeros inteiros, ex.: posicao do mouse
import java.awt.RenderingHints;  //opcoes de qualidade do desenho (antialiasing)
import java.awt.event.MouseAdapter; //"ouvinte" de eventos do mouse
import java.awt.event.MouseEvent;   //um evento do mouse (mexeu, clicou...)
import java.awt.geom.Ellipse2D;     //circulo ou elipse
import java.awt.geom.Path2D;        //forma livre feita de linhas (usada na ponta da seta)
import java.awt.geom.Point2D;       //um ponto (x, y) com numeros decimais
import java.awt.geom.QuadCurve2D;   //curva com um ponto de controle (curva de bezier quadratica)
import java.awt.geom.RoundRectangle2D; //retangulo com cantos arredondados
//imports do java.util: estruturas de dados
import java.util.ArrayDeque;   //fila dupla (da para colocar e tirar dos dois lados)
import java.util.ArrayList;    //lista que cresce sozinha
import java.util.Deque;        //tipo "fila dupla"
import java.util.HashMap;      //mapa chave -> valor rapido
import java.util.List;         //tipo "lista"
import java.util.Map;          //tipo "mapa"

import javax.swing.JPanel;     //um "quadro" do swing onde podemos desenhar

/**
 * ============================================================================
 *  painel do grafo: o desenho do grafo na tela
 * ============================================================================
 *
 * papel no projeto:
 *   desenha os vertices (bolinhas) e as arestas (setas) do GrafoMusical,
 *   e anima o passeio enquanto a musica toca, e a parte visual do grafo:
 *   ele nao muda o grafo, so le e desenha
 *
 * o que aparece na tela:
 *  - os vertices ficam distribuidos numa elipse, na ordem em que aparecem na melodia
 *  - cada vertice mostra a nota que soa (grande) e a nota anterior (pequena, em cima)
 *  - a cor da bolinha depende da nota (todo do tem a mesma cor, todo sol outra...),
 *    so para diferenciar as notas, nao tem significado de grafo
 *    do = vermelho, re = amarelo, mi = verde, fa = verde-azulado, sol = azul, la = roxo
 *
 * significado das cores das setas e destaques:
 *  - cinza:   transicao certa (100%), o vertice so tem uma saida
 *  - roxo:    ponto de escolha, vertice com mais de uma saida, ali o passeio
 *             sorteia o caminho, e por isso as setas mostram a porcentagem
 *             o vertice ganha um anel roxo tracejado
 *  - laranja: o vertice que esta tocando agora, as ultimas notas deixam um
 *             "rastro" laranja que vai apagando, mostrando o caminho do passeio
 *  - azul:    as setas do vertice que esta com o mouse em cima
 *
 * como o desenho funciona (swing):
 *   o java chama paintComponent() sempre que a tela precisa ser redesenhada
 *   quando algo muda (nota nova tocando, mouse em outro vertice), chamamos
 *   repaint() para pedir um redesenho
 *
 * "extends JPanel": esta classe e um JPanel (herda tudo dele) e so muda o desenho
 * ============================================================================
 */
public class PainelGrafo extends JPanel {

    //------------------------------------------------------------ constantes
    //"static final" = constante: valor fixo, igual para o programa todo

    private static final int RAIO_MAXIMO = 28;       //tamanho maximo de uma bolinha (em pixels)
    private static final int TAMANHO_RASTRO = 8;     //quantas notas anteriores ficam com o rastro laranja
    //new Color(vermelho, verde, azul) com valores de 0 a 255, o 4º numero (se houver) e a transparencia
    private static final Color FUNDO = new Color(24, 26, 34);                 //fundo escuro
    private static final Color COR_ATIVA = new Color(255, 140, 40);           //laranja: tocando agora
    private static final Color COR_FOCO = new Color(110, 190, 255);           //azul: mouse em cima
    private static final Color COR_ESCOLHA = new Color(190, 130, 255);        //roxo: ponto de escolha
    private static final Color COR_CERTA = new Color(150, 150, 170, 120);     //cinza: seta de 100% (meio transparente)

    //------------------------------------------------------------ estado do desenho
    //variaveis que guardam "o que esta acontecendo" no desenho agora

    private GrafoMusical grafo;                                     //o grafo que sera desenhado
    private final Map<Vertice, Point2D> posicoes = new HashMap<>(); //posicao (x, y) de cada vertice na tela
    private double raio = RAIO_MAXIMO;   //tamanho atual das bolinhas, diminui quando ha muitos vertices
    private Vertice verticeAtual;        //vertice tocando agora (fica laranja)
    private Vertice verticeAnterior;     //vertice tocado antes (a seta entre os dois fica laranja)
    private Vertice verticeSobMouse;     //vertice com o mouse em cima (setas ficam azuis)
    private final Deque<Vertice> rastro = new ArrayDeque<>(); //ultimos vertices tocados, do mais novo ao mais antigo

    /**
     * rotulo de porcentagem a ser escrito depois de tudo, para ficar por cima dos vertices
     * "record" e um jeito curto do java de criar uma classe que so guarda dados:
     * o texto ("40%"), a posicao x e y e a cor da borda
     */
    private record Rotulo(String texto, double x, double y, Color cor) {
    }

    //lista dos rotulos que ainda precisam ser escritos no fim do desenho
    private final List<Rotulo> rotulosPendentes = new ArrayList<>();

    /**
     * construtor: prepara o painel quando ele e criado (uma vez, pela janela principal)
     */
    public PainelGrafo() {
        setBackground(FUNDO);                        //cor de fundo do painel
        setPreferredSize(new Dimension(760, 580));   //tamanho inicial sugerido (largura x altura)

        //sempre que o mouse se mexe, ve se ele esta em cima de algum vertice
        //se mudou de vertice, redesenha para mostrar as setas dele em azul
        //"new MouseAdapter() { ... }" cria na hora um objeto que reage ao mouse
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                //descobre qual vertice esta embaixo do mouse (ou null se nenhum)
                Vertice v = verticeNaPosicao(e.getPoint());
                //so redesenha se o mouse entrou em outro vertice (economiza trabalho)
                if (v != verticeSobMouse) {
                    verticeSobMouse = v;
                    repaint();
                }
            }
        });
    }

    /**
     * define qual grafo desenhar (chamado depois de treinar ou trocar a memoria)
     *
     * @param grafo o grafo que passa a ser desenhado
     */
    public void setGrafo(GrafoMusical grafo) {
        this.grafo = grafo;      //"this.grafo" e o atributo, "grafo" sozinho e o parametro
        verticeSobMouse = null;  //o grafo mudou, entao nenhum vertice esta com o mouse em cima
        limparDestaque();        //apaga laranja e rastro do grafo antigo
    }

    /**
     * destaca o vertice que esta tocando e a aresta usada para chegar ate ele
     * e chamado a cada nota da musica, e e isso que faz o passeio "andar" na tela
     *
     * @param anterior o vertice da nota anterior (ou null)
     * @param atual    o vertice da nota que comecou a tocar agora (ou null)
     */
    public void destacar(Vertice anterior, Vertice atual) {
        verticeAnterior = anterior;
        verticeAtual = atual;
        //null acontece nas notas iniciais (memoria > 1), que nao tem vertice
        if (atual != null) {
            rastro.addFirst(atual);                   //a nota nova entra no comeco do rastro
            //enquanto o rastro tiver mais notas que o limite, tira a mais antiga
            while (rastro.size() > TAMANHO_RASTRO) {
                rastro.removeLast();                  //a mais antiga sai do rastro
            }
        }
        repaint(); //pede para o swing redesenhar a tela
    }

    /** apaga todos os destaques (fim da musica ou botao "parar") */
    public void limparDestaque() {
        verticeAnterior = null;
        verticeAtual = null;
        rastro.clear(); //esvazia o rastro laranja
        repaint();
    }

    /**
     * um vertice e ponto de escolha quando tem mais de uma aresta de saida
     *
     * @param v o vertice a conferir
     * @return true se ali o passeio precisa sortear entre mais de um caminho
     */
    private static boolean pontoDeEscolha(Vertice v) {
        return v.getGrauSaida() > 1;
    }

    /**
     * distribui os vertices igualmente numa elipse que ocupa o painel todo
     * cada vertice i fica num angulo de (360 graus / n) * i, comecando no topo
     * com x = centro + raioX * cos(angulo) e y = centro + raioY * sin(angulo)
     * (cos e sin, cosseno e seno, transformam um angulo num ponto da volta do circulo)
     * e chamado a cada desenho, porque o tamanho da janela pode ter mudado
     */
    private void calcularPosicoes() {
        posicoes.clear(); //apaga as posicoes antigas
        //copia os vertices para uma lista, assim cada um tem um numero (indice) i
        List<Vertice> lista = new ArrayList<>(grafo.getVertices());
        int n = lista.size();            //quantos vertices existem
        double cx = getWidth() / 2.0;    //x do centro do painel
        double cy = getHeight() / 2.0;   //y do centro do painel
        //margens para caber os lacos e os rotulos de porcentagem nas bordas
        double raioX = getWidth() / 2.0 - RAIO_MAXIMO - 70;  //metade da largura da elipse
        double raioY = getHeight() / 2.0 - RAIO_MAXIMO - 64; //metade da altura da elipse

        //tamanho de cada vertice: o maximo que cabe sem um encostar no outro
        //perimetro aproximado da elipse, dividido pelo numero de vertices
        double perimetro = Math.PI * (raioX + raioY);
        //Math.min garante no maximo RAIO_MAXIMO, Math.max garante no minimo 10 pixels
        raio = Math.max(10, Math.min(RAIO_MAXIMO, perimetro / n * 0.42));

        //para cada vertice, calcula o angulo e a posicao na elipse
        for (int i = 0; i < n; i++) {
            //-pi/2 = comeca no topo, 2 * pi = uma volta inteira, dividida em n partes
            double angulo = -Math.PI / 2 + 2 * Math.PI * i / n;
            //guarda no mapa: vertice -> ponto (x, y) calculado
            posicoes.put(lista.get(i), new Point2D.Double(
                    cx + raioX * Math.cos(angulo),
                    cy + raioY * Math.sin(angulo)));
        }
    }

    /**
     * descobre qual vertice esta no ponto do mouse (ou null se nenhum)
     *
     * @param p a posicao do mouse
     * @return o vertice embaixo do mouse, ou null
     */
    private Vertice verticeNaPosicao(Point p) {
        //percorre cada par (vertice, posicao) guardado no mapa
        for (Map.Entry<Vertice, Point2D> e : posicoes.entrySet()) {
            //se a distancia do mouse ate o centro da bolinha e menor que o raio, esta dentro dela
            if (e.getValue().distance(p) <= raio) {
                return e.getKey(); //achou: devolve o vertice
            }
        }
        return null; //o mouse nao esta em cima de nenhum vertice
    }

    /** devolve o ponto central do painel (metade da largura, metade da altura) */
    private Point2D centro() {
        return new Point2D.Double(getWidth() / 2.0, getHeight() / 2.0);
    }

    /**
     * desenha o grafo inteiro, o java chama este metodo sozinho sempre que a
     * tela precisa ser redesenhada (ou quando chamamos repaint())
     *
     * ordem do desenho (o que e desenhado depois fica por cima):
     *   1. arestas comuns (cinzas)
     *   2. arestas importantes (roxas, azuis, laranja)
     *   3. rastro laranja
     *   4. vertices (bolinhas)
     *   5. rotulos de porcentagem
     *   6. legenda
     *
     * @param g0 o "pincel" que o java entrega para desenhar
     */
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);  //deixa o JPanel pintar o fundo primeiro
        //cria uma copia do pincel como Graphics2D, que tem mais recursos
        Graphics2D g = (Graphics2D) g0.create();
        //antialiasing: deixa as linhas e os textos com bordas suaves
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        //se ainda nao existe grafo treinado, so escreve uma mensagem no meio da tela
        if (grafo == null || grafo.estaVazio()) {
            g.setColor(Color.LIGHT_GRAY);
            g.setFont(getFont().deriveFont(18f)); //mesma fonte, tamanho 18
            String msg = "Treine o grafo com uma melodia para começar";
            int w = g.getFontMetrics().stringWidth(msg); //largura do texto em pixels
            //(largura do painel - largura do texto) / 2 = x para o texto ficar centralizado
            g.drawString(msg, (getWidth() - w) / 2, getHeight() / 2);
            g.dispose(); //libera a copia do pincel
            return;      //sai do metodo, nao tem mais nada para desenhar
        }

        calcularPosicoes(); //descobre onde fica cada bolinha
        //vertice cujas probabilidades de saida serao destacadas em azul
        //se o mouse esta em cima de um vertice, e ele, se nao, e o que esta tocando
        Vertice foco = (verticeSobMouse != null) ? verticeSobMouse : verticeAtual;

        //1ª passada: arestas comuns (ficam por baixo)
        //2ª passada: arestas de escolha, em foco ou ativas (ficam por cima)
        for (int passada = 0; passada < 2; passada++) {
            //percorre todos os vertices do grafo
            for (Vertice v : grafo.getVertices()) {
                boolean escolha = pontoDeEscolha(v); //este vertice tem mais de uma saida?
                //percorre a lista de adjacencia do vertice: cada seta que sai dele
                for (Aresta a : v.getAdjacentes()) {
                    Vertice destino = a.getDestino(); //para onde a seta aponta
                    //ativa = e a seta que o passeio acabou de usar (anterior -> atual)
                    boolean ativa = v == verticeAnterior && destino == verticeAtual;
                    boolean doFoco = v == foco;       //a seta sai do vertice em foco?
                    boolean importante = ativa || doFoco || escolha;
                    //na passada 0 pula as importantes, na passada 1 pula as comuns
                    if ((passada == 0) == importante) {
                        continue;
                    }
                    //escolhe a cor e a grossura da seta
                    //a grossura acompanha a probabilidade: seta mais grossa = mais chance
                    double p = v.probabilidade(a); //chance desta seta, de 0.0 a 1.0
                    Color cor;      //cor que a seta vai ter
                    float largura;  //grossura da linha em pixels
                    if (ativa) {
                        cor = COR_ATIVA;       //laranja: caminho que acabou de ser usado
                        largura = 4.5f;
                    } else if (doFoco) {
                        cor = COR_FOCO;        //azul: mouse em cima
                        largura = 1.5f + 5f * (float) p;
                    } else if (escolha) {
                        cor = COR_ESCOLHA;     //roxo: ponto de escolha
                        largura = 1.5f + 5f * (float) p;
                    } else {
                        cor = COR_CERTA;       //cinza: so existe esse caminho (100%)
                        largura = 2f;
                    }
                    //porcentagem: sempre nos pontos de escolha e no vertice em foco
                    //"%.0f%%" = numero sem casas decimais seguido do simbolo %, ex.: "40%"
                    String rotulo = (doFoco || escolha) ? String.format("%.0f%%", p * 100) : null;
                    Color corRotulo = doFoco ? COR_FOCO : COR_ESCOLHA;
                    desenharAresta(g, posicoes.get(v), posicoes.get(destino), cor, largura, rotulo, corRotulo);
                }
            }
        }

        desenharRastro(g); //brilho laranja das ultimas notas
        //desenha cada vertice por cima das setas
        for (Vertice v : grafo.getVertices()) {
            desenharVertice(g, v);
        }

        //rotulos de porcentagem por ultimo, por cima de tudo
        for (Rotulo r : rotulosPendentes) {
            desenharRotulo(g, r.texto(), r.x(), r.y(), r.cor());
        }
        rotulosPendentes.clear(); //esvazia a lista para o proximo desenho

        desenharLegenda(g); //texto do canto superior esquerdo
        g.dispose();        //libera a copia do pincel
    }

    /**
     * brilho laranja nos ultimos vertices tocados, mais fraco nos mais antigos
     *
     * @param g o pincel
     */
    private void desenharRastro(Graphics2D g) {
        int i = 0; //posicao no rastro: 0 = mais novo, 7 = mais antigo
        //percorre o rastro do mais novo para o mais antigo
        for (Vertice v : rastro) {
            if (i > 0) { //o atual e desenhado em desenharVertice
                Point2D p = posicoes.get(v); //onde a bolinha esta
                if (p != null) {
                    //alfa = transparencia: quanto mais antigo (i maior), mais apagado
                    int alfa = (int) (150.0 * (TAMANHO_RASTRO - i) / TAMANHO_RASTRO);
                    g.setColor(new Color(255, 150, 50, alfa));
                    double r = raio * 1.35; //um pouco maior que a bolinha, para aparecer em volta
                    //Ellipse2D recebe o canto de cima a esquerda (x - r, y - r) e o tamanho (2r)
                    g.fill(new Ellipse2D.Double(p.getX() - r, p.getY() - r, 2 * r, 2 * r));
                }
            }
            i++;
        }
    }

    /**
     * desenha uma aresta (seta) da origem "o" ate o destino "d"
     *   - se origem == destino, e um laco (nota repetida): desenha um circulinho
     *   - senao, desenha uma curva com uma ponta de seta no destino
     *     (a ponta mostra o sentido da aresta, porque o grafo e dirigido)
     *
     * @param g         o pincel
     * @param o         posicao da origem
     * @param d         posicao do destino
     * @param cor       cor da seta
     * @param largura   grossura da linha
     * @param rotulo    texto da porcentagem (ou null para nao escrever)
     * @param corRotulo cor da borda da caixinha da porcentagem
     */
    private void desenharAresta(Graphics2D g, Point2D o, Point2D d, Color cor, float largura,
                                String rotulo, Color corRotulo) {
        g.setColor(cor);
        //linha com a grossura escolhida e pontas arredondadas
        g.setStroke(new BasicStroke(largura, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Point2D c = centro();

        //laco (o vertice aponta para ele mesmo, ex.: mi -> mi): pequeno circulo para fora
        if (o.equals(d)) {
            //(dx, dy) = direcao do centro do painel ate o vertice, o laco fica para "fora"
            double dx = o.getX() - c.getX();
            double dy = o.getY() - c.getY();
            //hypot = tamanho da direcao (teorema de pitagoras), max(1, ...) evita dividir por 0
            double len = Math.max(1, Math.hypot(dx, dy));
            double r = raio * 0.7; //raio do circulinho do laco
            //centro do laco: anda a partir do vertice na direcao de fora
            //dx / len e dy / len deixam a direcao com tamanho 1
            double lx = o.getX() + dx / len * (raio + r * 0.6);
            double ly = o.getY() + dy / len * (raio + r * 0.6);
            g.draw(new Ellipse2D.Double(lx - r, ly - r, 2 * r, 2 * r));
            //se tem porcentagem, guarda para escrever um pouco mais para fora do laco
            if (rotulo != null) {
                rotulosPendentes.add(new Rotulo(rotulo, lx + dx / len * (r + 14), ly + dy / len * (r + 14), corRotulo));
            }
            return; //laco pronto, nao precisa de ponta de seta
        }

        //aresta curva: o ponto de controle fica deslocado para o lado,
        //assim as arestas a->b e b->a nao ficam uma em cima da outra
        double dx = d.getX() - o.getX();  //quanto andar em x da origem ate o destino
        double dy = d.getY() - o.getY();  //quanto andar em y
        double dist = Math.hypot(dx, dy); //distancia entre os dois vertices
        double ux = dx / dist;            //direcao com tamanho 1 (vetor unitario), parte x
        double uy = dy / dist;            //parte y
        double curvatura = Math.min(45, dist * 0.18); //quanto a curva "entorta", no maximo 45 pixels
        //ponto de controle: meio do caminho, empurrado para o lado (perpendicular: -uy, ux)
        double ctrlX = (o.getX() + d.getX()) / 2 - uy * curvatura;
        double ctrlY = (o.getY() + d.getY()) / 2 + ux * curvatura;

        //comeca e termina na borda dos circulos, nao no centro
        //(sx, sy) = direcao da origem ate o ponto de controle
        double sx = ctrlX - o.getX();
        double sy = ctrlY - o.getY();
        double sl = Math.hypot(sx, sy);
        double iniX = o.getX() + sx / sl * raio; //ponto de saida na borda da bolinha de origem
        double iniY = o.getY() + sy / sl * raio;

        //(tx, ty) = direcao em que a curva chega no destino (do controle ate o destino)
        double tx = d.getX() - ctrlX;
        double ty = d.getY() - ctrlY;
        double tl = Math.hypot(tx, ty);
        tx /= tl; //deixa com tamanho 1
        ty /= tl;
        //ponta da seta: na borda da bolinha de destino (com 2 pixels de folga)
        double pontaX = d.getX() - tx * (raio + 2);
        double pontaY = d.getY() - ty * (raio + 2);
        double tamSeta = 7 + largura * 1.3; //seta mais grossa = ponta maior
        //a linha termina um pouco antes da ponta, para nao aparecer por cima do triangulo
        double baseX = pontaX - tx * tamSeta * 0.8;
        double baseY = pontaY - ty * tamSeta * 0.8;

        //desenha a curva: comeca em ini, puxada pelo controle, termina em base
        g.draw(new QuadCurve2D.Double(iniX, iniY, ctrlX, ctrlY, baseX, baseY));

        //ponta da seta (triangulo)
        //um vertice e a ponta, os outros dois ficam para tras e para os lados
        Path2D seta = new Path2D.Double();
        seta.moveTo(pontaX, pontaY); //comeca na ponta
        seta.lineTo(pontaX - tx * tamSeta - ty * tamSeta * 0.5, pontaY - ty * tamSeta + tx * tamSeta * 0.5);
        seta.lineTo(pontaX - tx * tamSeta + ty * tamSeta * 0.5, pontaY - ty * tamSeta - tx * tamSeta * 0.5);
        seta.closePath(); //fecha o triangulo voltando para a ponta
        g.fill(seta);     //pinta o triangulo por dentro

        //se tem porcentagem, guarda para escrever no meio da curva
        if (rotulo != null) {
            //ponto do meio da curva de bezier quadratica (t = 0.5)
            //formula: 0.25 * inicio + 0.5 * controle + 0.25 * fim
            double mx = 0.25 * iniX + 0.5 * ctrlX + 0.25 * baseX;
            double my = 0.25 * iniY + 0.5 * ctrlY + 0.25 * baseY;
            rotulosPendentes.add(new Rotulo(rotulo, mx, my, corRotulo));
        }
    }

    /**
     * desenha um vertice: a bolinha colorida, o brilho laranja (se estiver tocando),
     * a borda branca (se for o inicio), o anel roxo (se for ponto de escolha)
     * e o nome da nota
     *
     * @param g o pincel
     * @param v o vertice a desenhar
     */
    private void desenharVertice(Graphics2D g, Vertice v) {
        Point2D p = posicoes.get(v);                      //onde a bolinha fica
        boolean tocando = v == verticeAtual;              //e a nota que esta soando agora?
        boolean inicial = v == grafo.getVerticeInicial(); //e onde a musica comeca?

        //cor de acordo com a nota que soa (do, re, mi... cada uma com um tom)
        //o circulo de cores (hsb) e dividido em 12 partes, uma para cada nota:
        //do = vermelho, re = amarelo, mi = verde, fa = verde-azulado, sol = azul, la = roxo
        //hsb = matiz (a cor), saturacao (0.5 = cor suave) e brilho (0.95 = bem claro)
        Color cor = Color.getHSBColor(Nota.classe(v.getNota()) / 12f, 0.5f, 0.95f);

        if (tocando) {
            //"brilho" em volta do vertice que esta soando
            //3 circulos laranja transparentes, um maior que o outro, dao o efeito de luz
            for (int i = 3; i >= 1; i--) {
                g.setColor(new Color(255, 160, 60, 45));
                double r = raio + i * raio * 0.27;
                g.fill(new Ellipse2D.Double(p.getX() - r, p.getY() - r, 2 * r, 2 * r));
            }
        }

        //se esta tocando, a bolinha fica 15% maior
        double r = tocando ? raio * 1.15 : raio;
        g.setColor(tocando ? COR_ATIVA : cor); //tocando = laranja, senao a cor da nota
        g.fill(new Ellipse2D.Double(p.getX() - r, p.getY() - r, 2 * r, 2 * r));
        //borda: grossa e branca no vertice inicial, fina e escura nos outros
        g.setStroke(new BasicStroke(inicial ? 3.5f : 1.5f));
        g.setColor(inicial ? Color.WHITE : FUNDO.brighter());
        g.draw(new Ellipse2D.Double(p.getX() - r, p.getY() - r, 2 * r, 2 * r));

        //ponto de escolha: anel tracejado em volta
        if (pontoDeEscolha(v)) {
            g.setColor(COR_ESCOLHA);
            //{4f, 3f} = tracejado: 4 pixels de linha, 3 pixels de espaco
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                    new float[] {4f, 3f}, 0f));
            double ra = r + 5; //o anel fica 5 pixels para fora da bolinha
            g.draw(new Ellipse2D.Double(p.getX() - ra, p.getY() - ra, 2 * ra, 2 * ra));
        }

        desenharNomes(g, v, p, r); //escreve o nome da nota dentro da bolinha

        //no vertice inicial, escreve a palavra "inicio" perto dele
        if (inicial) {
            g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(Color.WHITE);
            String txt = "início";
            //escreve do lado de dentro da elipse, para nao ficar embaixo de um laco
            //(dx, dy) = direcao do vertice ate o centro do painel
            Point2D c = centro();
            double dx = c.getX() - p.getX();
            double dy = c.getY() - p.getY();
            double len = Math.max(1, Math.hypot(dx, dy));
            //anda 16 pixels alem da borda da bolinha, em direcao ao centro
            double tx = p.getX() + dx / len * (r + 16);
            double ty = p.getY() + dy / len * (r + 16);
            //centraliza o texto nesse ponto
            g.drawString(txt, (float) (tx - fm.stringWidth(txt) / 2.0), (float) (ty + fm.getAscent() / 2.0 - 2));
        }
    }

    /**
     * escreve dentro do circulo a nota que soa (grande, em negrito) e,
     * com memoria maior que 1, a nota anterior (pequena, em cima)
     * o vertice completo aparece na legenda ao passar o mouse
     *
     * @param g o pincel
     * @param v o vertice
     * @param p o centro da bolinha
     * @param r o raio da bolinha
     */
    private void desenharNomes(Graphics2D g, Vertice v, Point2D p, double r) {
        List<Integer> estado = v.getEstado();          //as notas que formam o vertice
        String principal = Nota.nomePt(v.getNota());   //a nota que soa (ultima do estado)
        String contexto = null;                        //a nota anterior (so com memoria > 1)
        if (estado.size() > 1) {
            //pega a penultima nota do estado, com memoria 3 ou mais coloca "…" antes
            //para indicar que tem mais notas para tras
            contexto = (estado.size() > 2 ? "…" : "") + Nota.nomePt(estado.get(estado.size() - 2));
        }

        //diminui a fonte ate o texto caber dentro do circulo
        float tamanho = 15f; //comeca com fonte 15
        //enquanto a fonte for maior que 7, testa se cabe
        while (tamanho > 7f) {
            g.setFont(getFont().deriveFont(Font.BOLD, tamanho));
            FontMetrics fm = g.getFontMetrics();
            //com contexto sao duas linhas, entao a altura e quase o dobro
            double altura = fm.getAscent() * (contexto == null ? 1 : 1.8);
            //se a largura e a altura cabem na bolinha, para de diminuir
            if (fm.stringWidth(principal) <= r * 1.7 && altura <= r * 1.5) {
                break; //break = sai do while
            }
            tamanho -= 0.5f; //nao coube: diminui meio ponto e tenta de novo
        }

        //fonte da nota principal (negrito) e do contexto (75% do tamanho, normal)
        Font fontePrincipal = getFont().deriveFont(Font.BOLD, tamanho);
        Font fonteContexto = getFont().deriveFont(Font.PLAIN, tamanho * 0.75f);
        //pega as medidas de cada fonte para centralizar os textos
        g.setFont(fontePrincipal);
        FontMetrics fmP = g.getFontMetrics();
        g.setFont(fonteContexto);
        FontMetrics fmC = g.getFontMetrics();

        Color corTexto = new Color(20, 20, 30); //texto quase preto
        if (contexto == null) {
            //memoria 1: so uma linha, centralizada na bolinha
            g.setFont(fontePrincipal);
            g.setColor(corTexto);
            g.drawString(principal, (float) (p.getX() - fmP.stringWidth(principal) / 2.0),
                    (float) (p.getY() + fmP.getAscent() / 2.0 - 2));
        } else {
            //memoria maior: duas linhas, contexto em cima (mais clarinho) e nota principal embaixo
            double alturaTotal = fmC.getAscent() + fmP.getAscent();
            double topo = p.getY() - alturaTotal / 2.0; //onde comeca o bloco de texto
            g.setFont(fonteContexto);
            g.setColor(new Color(20, 20, 30, 150)); //mesma cor, meio transparente
            g.drawString(contexto, (float) (p.getX() - fmC.stringWidth(contexto) / 2.0),
                    (float) (topo + fmC.getAscent() - 1));
            g.setFont(fontePrincipal);
            g.setColor(corTexto);
            g.drawString(principal, (float) (p.getX() - fmP.stringWidth(principal) / 2.0),
                    (float) (topo + alturaTotal));
        }
    }

    /**
     * desenha a caixinha com a porcentagem de uma aresta (ex.: "40%")
     *
     * @param g     o pincel
     * @param texto o texto da porcentagem
     * @param x     centro da caixinha em x
     * @param y     centro da caixinha em y
     * @param borda cor da borda (roxo ou azul)
     */
    private void desenharRotulo(Graphics2D g, String texto, double x, double y, Color borda) {
        g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        FontMetrics fm = g.getFontMetrics();
        double w = fm.stringWidth(texto) + 10; //largura da caixa: texto + 10 pixels de folga
        double h = fm.getHeight() + 2;         //altura da caixa
        //retangulo de cantos arredondados centralizado em (x, y)
        RoundRectangle2D caixa = new RoundRectangle2D.Double(x - w / 2, y - h / 2, w, h, 10, 10);
        g.setColor(new Color(15, 15, 22, 230)); //fundo escuro quase opaco
        g.fill(caixa);
        g.setColor(borda);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(caixa);                          //borda colorida
        g.setColor(Color.WHITE);
        g.drawString(texto, (float) (x - fm.stringWidth(texto) / 2.0), (float) (y + fm.getAscent() / 2.0 - 2));
    }

    /**
     * desenha a legenda do canto superior esquerdo: memoria, numero de vertices,
     * de arestas e de pontos de escolha, o significado das cores e o vertice
     * com o mouse em cima
     *
     * @param g o pincel
     */
    private void desenharLegenda(Graphics2D g) {
        //conta quantos vertices sao pontos de escolha (tem mais de uma saida)
        int escolhas = 0;
        for (Vertice v : grafo.getVertices()) {
            if (pontoDeEscolha(v)) {
                escolhas++; //soma 1 no contador
            }
        }
        //legenda empilhada no canto superior esquerdo, longe dos vertices
        g.setFont(getFont().deriveFont(Font.PLAIN, 12f));
        int x = 12;     //distancia da borda esquerda
        int y = 20;     //altura da primeira linha
        int passo = 16; //espaco entre uma linha e outra
        g.setColor(new Color(200, 200, 215));
        //%d e trocado pelos numeros na ordem: memoria, vertices, arestas
        g.drawString(String.format("Memória: %d   Vértices: %d   Arestas: %d",
                grafo.getMemoria(), grafo.numeroVertices(), grafo.numeroArestas()), x, y);
        y += passo; //desce uma linha
        g.drawString("Pontos de escolha: " + escolhas, x, y);
        y += passo + 4;
        //as linhas abaixo explicam as cores, cada uma escrita na propria cor
        g.setColor(COR_CERTA.brighter());
        g.drawString("━ caminho certo (100%)", x, y);
        y += passo;
        g.setColor(COR_ESCOLHA);
        g.drawString("━ sorteio (ponto de escolha)", x, y);
        y += passo;
        g.setColor(COR_ATIVA);
        g.drawString("● tocando agora", x, y);
        y += passo + 4;

        //ultima linha: mostra o vertice completo se o mouse estiver em cima de algum
        if (verticeSobMouse != null) {
            g.setColor(COR_FOCO);
            g.drawString("Vértice: " + verticeSobMouse, x, y);
        } else {
            g.setColor(new Color(150, 150, 165));
            g.drawString("Passe o mouse num vértice", x, y);
        }
    }
}
