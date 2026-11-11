import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

/**
 * ============================================================================
 *  PAINEL DO GRAFO: o desenho do grafo na tela
 * ============================================================================
 *
 * PAPEL NO PROJETO:
 *   Desenha os vértices (bolinhas) e as arestas (setas) do GrafoMusical,
 *   e anima o passeio enquanto a música toca. É a parte VISUAL do grafo:
 *   ele não muda o grafo, só lê e desenha
 *
 * O QUE APARECE NA TELA:
 *  - Os vértices ficam distribuídos numa elipse, na ordem em que aparecem na melodia
 *  - Cada vértice mostra a nota que soa (grande) e a nota anterior (pequena, em cima)
 *  - A COR DA BOLINHA depende da nota (todo Dó tem a mesma cor, todo Sol outra...),
 *    só para diferenciar as notas. Não tem significado de grafo
 *
 * SIGNIFICADO DAS CORES DAS SETAS E DESTAQUES:
 *  - CINZA:   transição certa (100%), o vértice só tem uma saída
 *  - ROXO:    PONTO DE ESCOLHA, vértice com mais de uma saída; ali o passeio
 *             sorteia o caminho, e por isso as setas mostram a porcentagem
 *             O vértice ganha um anel roxo tracejado
 *  - LARANJA: o vértice que está tocando agora. As últimas notas deixam um
 *             "rastro" laranja que vai apagando, mostrando o caminho do passeio
 *  - AZUL:    as setas do vértice que está com o mouse em cima
 *
 * COMO O DESENHO FUNCIONA (Swing):
 *   O Java chama paintComponent() sempre que a tela precisa ser redesenhada
 *   Quando algo muda (nota nova tocando, mouse em outro vértice), chamamos
 *   repaint() para pedir um redesenho
 * ============================================================================
 */
public class PainelGrafo extends JPanel {

    //------------------------------------------------------------ CONSTANTES

    private static final int RAIO_MAXIMO = 28;       //tamanho máximo de uma bolinha (em pixels)
    private static final int TAMANHO_RASTRO = 8;     //quantas notas anteriores ficam com o rastro laranja
    private static final Color FUNDO = new Color(24, 26, 34);                 //fundo escuro
    private static final Color COR_ATIVA = new Color(255, 140, 40);           //laranja: tocando agora
    private static final Color COR_FOCO = new Color(110, 190, 255);           //azul: mouse em cima
    private static final Color COR_ESCOLHA = new Color(190, 130, 255);        //roxo: ponto de escolha
    private static final Color COR_CERTA = new Color(150, 150, 170, 120);     //cinza: seta de 100%

    //------------------------------------------------------------ ESTADO DO DESENHO

    private GrafoMusical grafo;                                     //o grafo que será desenhado
    private final Map<Vertice, Point2D> posicoes = new HashMap<>(); //posição (x, y) de cada vértice na tela
    private double raio = RAIO_MAXIMO;   //tamanho atual das bolinhas; diminui quando há muitos vértices
    private Vertice verticeAtual;        //vértice tocando agora (fica laranja)
    private Vertice verticeAnterior;     //vértice tocado antes (a seta entre os dois fica laranja)
    private Vertice verticeSobMouse;     //vértice com o mouse em cima (setas ficam azuis)
    private final Deque<Vertice> rastro = new ArrayDeque<>(); //últimos vértices tocados, do mais novo ao mais antigo

    /** Rótulo de porcentagem a ser escrito depois de tudo, para ficar por cima dos vértices */
    private record Rotulo(String texto, double x, double y, Color cor) {
    }

    private final List<Rotulo> rotulosPendentes = new ArrayList<>();

    public PainelGrafo() {
        setBackground(FUNDO);
        setPreferredSize(new Dimension(760, 580));

        //Sempre que o mouse se mexe, vê se ele está em cima de algum vértice
        //Se mudou de vértice, redesenha para mostrar as setas dele em azul
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Vertice v = verticeNaPosicao(e.getPoint());
                if (v != verticeSobMouse) {
                    verticeSobMouse = v;
                    repaint();
                }
            }
        });
    }

    /** Define qual grafo desenhar (chamado depois de treinar ou trocar a memória) */
    public void setGrafo(GrafoMusical grafo) {
        this.grafo = grafo;
        verticeSobMouse = null;
        limparDestaque();
    }

    /**
     * Destaca o vértice que está tocando e a aresta usada para chegar até ele
     * É chamado a cada nota da música, e é isso que faz o passeio "andar" na tela
     */
    public void destacar(Vertice anterior, Vertice atual) {
        verticeAnterior = anterior;
        verticeAtual = atual;
        if (atual != null) {
            rastro.addFirst(atual);                   //a nota nova entra no começo do rastro
            while (rastro.size() > TAMANHO_RASTRO) {
                rastro.removeLast();                  //a mais antiga sai do rastro
            }
        }
        repaint(); //pede para o Swing redesenhar a tela
    }

    /** Apaga todos os destaques (fim da música ou botão "Parar") */
    public void limparDestaque() {
        verticeAnterior = null;
        verticeAtual = null;
        rastro.clear();
        repaint();
    }

    /** Um vértice é ponto de escolha quando tem mais de uma aresta de saída */
    private static boolean pontoDeEscolha(Vertice v) {
        return v.getGrauSaida() > 1;
    }

    /**
     * Distribui os vértices igualmente numa elipse que ocupa o painel todo
     * Cada vértice i fica num ângulo de (360° / n) * i, começando no topo
     * Com x = centro + raioX * cos(ângulo) e y = centro + raioY * sin(ângulo)
     */
    private void calcularPosicoes() {
        posicoes.clear();
        List<Vertice> lista = new ArrayList<>(grafo.getVertices());
        int n = lista.size();
        double cx = getWidth() / 2.0;
        double cy = getHeight() / 2.0;
        //Margens para caber os laços e os rótulos de porcentagem nas bordas
        double raioX = getWidth() / 2.0 - RAIO_MAXIMO - 70;
        double raioY = getHeight() / 2.0 - RAIO_MAXIMO - 64;

        //Tamanho de cada vértice: o máximo que cabe sem um encostar no outro
        double perimetro = Math.PI * (raioX + raioY);
        raio = Math.max(10, Math.min(RAIO_MAXIMO, perimetro / n * 0.42));

        for (int i = 0; i < n; i++) {
            double angulo = -Math.PI / 2 + 2 * Math.PI * i / n;
            posicoes.put(lista.get(i), new Point2D.Double(
                    cx + raioX * Math.cos(angulo),
                    cy + raioY * Math.sin(angulo)));
        }
    }

    /** Descobre qual vértice está no ponto do mouse (ou null se nenhum) */
    private Vertice verticeNaPosicao(Point p) {
        for (Map.Entry<Vertice, Point2D> e : posicoes.entrySet()) {
            if (e.getValue().distance(p) <= raio) {
                return e.getKey();
            }
        }
        return null;
    }

    private Point2D centro() {
        return new Point2D.Double(getWidth() / 2.0, getHeight() / 2.0);
    }

    /**
     * DESENHA O GRAFO INTEIRO. O Java chama este método sozinho sempre que a
     * tela precisa ser redesenhada (ou quando chamamos repaint())
     *
     * Ordem do desenho (o que é desenhado depois fica por cima):
     *   1. arestas comuns (cinzas)
     *   2. arestas importantes (roxas, azuis, laranja)
     *   3. rastro laranja
     *   4. vértices (bolinhas)
     *   5. rótulos de porcentagem
     *   6. legenda
     */
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        //Antialiasing: deixa as linhas e os textos com bordas suaves
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (grafo == null || grafo.estaVazio()) {
            g.setColor(Color.LIGHT_GRAY);
            g.setFont(getFont().deriveFont(18f));
            String msg = "Treine o grafo com uma melodia para começar";
            int w = g.getFontMetrics().stringWidth(msg);
            g.drawString(msg, (getWidth() - w) / 2, getHeight() / 2);
            g.dispose();
            return;
        }

        calcularPosicoes();
        //Vértice cujas probabilidades de saída serão destacadas em azul
        Vertice foco = (verticeSobMouse != null) ? verticeSobMouse : verticeAtual;

        //1ª passada: arestas comuns (ficam por baixo)
        //2ª passada: arestas de escolha, em foco ou ativas (ficam por cima)
        for (int passada = 0; passada < 2; passada++) {
            for (Vertice v : grafo.getVertices()) {
                boolean escolha = pontoDeEscolha(v);
                for (Aresta a : v.getAdjacentes()) {
                    Vertice destino = a.getDestino();
                    boolean ativa = v == verticeAnterior && destino == verticeAtual;
                    boolean doFoco = v == foco;
                    boolean importante = ativa || doFoco || escolha;
                    if ((passada == 0) == importante) {
                        continue;
                    }
                    //Escolhe a cor e a grossura da seta
                    //A grossura acompanha a probabilidade: seta mais grossa = mais chance
                    double p = v.probabilidade(a);
                    Color cor;
                    float largura;
                    if (ativa) {
                        cor = COR_ATIVA;
                        largura = 4.5f;
                    } else if (doFoco) {
                        cor = COR_FOCO;
                        largura = 1.5f + 5f * (float) p;
                    } else if (escolha) {
                        cor = COR_ESCOLHA;
                        largura = 1.5f + 5f * (float) p;
                    } else {
                        cor = COR_CERTA;
                        largura = 2f;
                    }
                    //Porcentagem: sempre nos pontos de escolha e no vértice em foco
                    String rotulo = (doFoco || escolha) ? String.format("%.0f%%", p * 100) : null;
                    Color corRotulo = doFoco ? COR_FOCO : COR_ESCOLHA;
                    desenharAresta(g, posicoes.get(v), posicoes.get(destino), cor, largura, rotulo, corRotulo);
                }
            }
        }

        desenharRastro(g);
        for (Vertice v : grafo.getVertices()) {
            desenharVertice(g, v);
        }

        //Rótulos de porcentagem por último, por cima de tudo
        for (Rotulo r : rotulosPendentes) {
            desenharRotulo(g, r.texto(), r.x(), r.y(), r.cor());
        }
        rotulosPendentes.clear();

        desenharLegenda(g);
        g.dispose();
    }

    /** Brilho laranja nos últimos vértices tocados, mais fraco nos mais antigos */
    private void desenharRastro(Graphics2D g) {
        int i = 0;
        for (Vertice v : rastro) {
            if (i > 0) { //o atual é desenhado em desenharVertice
                Point2D p = posicoes.get(v);
                if (p != null) {
                    int alfa = (int) (150.0 * (TAMANHO_RASTRO - i) / TAMANHO_RASTRO);
                    g.setColor(new Color(255, 150, 50, alfa));
                    double r = raio * 1.35;
                    g.fill(new Ellipse2D.Double(p.getX() - r, p.getY() - r, 2 * r, 2 * r));
                }
            }
            i++;
        }
    }

    /**
     * Desenha UMA aresta (seta) da origem "o" até o destino "d"
     *   - Se origem == destino, é um LAÇO (nota repetida): desenha um circulinho
     *   - Senão, desenha uma curva com uma ponta de seta no destino
     *     (a ponta mostra o SENTIDO da aresta, porque o grafo é dirigido)
     */
    private void desenharAresta(Graphics2D g, Point2D o, Point2D d, Color cor, float largura,
                                String rotulo, Color corRotulo) {
        g.setColor(cor);
        g.setStroke(new BasicStroke(largura, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Point2D c = centro();

        //LAÇO (o vértice aponta para ele mesmo, ex.: Mi -> Mi): pequeno círculo para fora
        if (o.equals(d)) {
            double dx = o.getX() - c.getX();
            double dy = o.getY() - c.getY();
            double len = Math.max(1, Math.hypot(dx, dy));
            double r = raio * 0.7;
            double lx = o.getX() + dx / len * (raio + r * 0.6);
            double ly = o.getY() + dy / len * (raio + r * 0.6);
            g.draw(new Ellipse2D.Double(lx - r, ly - r, 2 * r, 2 * r));
            if (rotulo != null) {
                rotulosPendentes.add(new Rotulo(rotulo, lx + dx / len * (r + 14), ly + dy / len * (r + 14), corRotulo));
            }
            return;
        }

        //Aresta curva: o ponto de controle fica deslocado para o lado,
        //assim as arestas A->B e B->A não ficam uma em cima da outra
        double dx = d.getX() - o.getX();
        double dy = d.getY() - o.getY();
        double dist = Math.hypot(dx, dy);
        double ux = dx / dist;
        double uy = dy / dist;
        double curvatura = Math.min(45, dist * 0.18);
        double ctrlX = (o.getX() + d.getX()) / 2 - uy * curvatura;
        double ctrlY = (o.getY() + d.getY()) / 2 + ux * curvatura;

        //Começa e termina na borda dos círculos, não no centro
        double sx = ctrlX - o.getX();
        double sy = ctrlY - o.getY();
        double sl = Math.hypot(sx, sy);
        double iniX = o.getX() + sx / sl * raio;
        double iniY = o.getY() + sy / sl * raio;

        double tx = d.getX() - ctrlX;
        double ty = d.getY() - ctrlY;
        double tl = Math.hypot(tx, ty);
        tx /= tl;
        ty /= tl;
        double pontaX = d.getX() - tx * (raio + 2);
        double pontaY = d.getY() - ty * (raio + 2);
        double tamSeta = 7 + largura * 1.3;
        double baseX = pontaX - tx * tamSeta * 0.8;
        double baseY = pontaY - ty * tamSeta * 0.8;

        g.draw(new QuadCurve2D.Double(iniX, iniY, ctrlX, ctrlY, baseX, baseY));

        //Ponta da seta (triângulo)
        Path2D seta = new Path2D.Double();
        seta.moveTo(pontaX, pontaY);
        seta.lineTo(pontaX - tx * tamSeta - ty * tamSeta * 0.5, pontaY - ty * tamSeta + tx * tamSeta * 0.5);
        seta.lineTo(pontaX - tx * tamSeta + ty * tamSeta * 0.5, pontaY - ty * tamSeta - tx * tamSeta * 0.5);
        seta.closePath();
        g.fill(seta);

        if (rotulo != null) {
            //Ponto do meio da curva de Bézier quadrática (t = 0.5)
            double mx = 0.25 * iniX + 0.5 * ctrlX + 0.25 * baseX;
            double my = 0.25 * iniY + 0.5 * ctrlY + 0.25 * baseY;
            rotulosPendentes.add(new Rotulo(rotulo, mx, my, corRotulo));
        }
    }

    /**
     * Desenha UM vértice: a bolinha colorida, o brilho laranja (se estiver tocando),
     * a borda branca (se for o início), o anel roxo (se for ponto de escolha)
     * e o nome da nota
     */
    private void desenharVertice(Graphics2D g, Vertice v) {
        Point2D p = posicoes.get(v);
        boolean tocando = v == verticeAtual;
        boolean inicial = v == grafo.getVerticeInicial();

        //Cor de acordo com a nota que soa (Dó, Ré, Mi... cada uma com um tom)
        //O círculo de cores (HSB) é dividido em 12 partes, uma para cada nota:
        //Dó = vermelho, Ré = amarelo, Mi = verde, Sol = azul, Lá = roxo...
        Color cor = Color.getHSBColor(Nota.classe(v.getNota()) / 12f, 0.5f, 0.95f);

        if (tocando) {
            //"Brilho" em volta do vértice que está soando
            for (int i = 3; i >= 1; i--) {
                g.setColor(new Color(255, 160, 60, 45));
                double r = raio + i * raio * 0.27;
                g.fill(new Ellipse2D.Double(p.getX() - r, p.getY() - r, 2 * r, 2 * r));
            }
        }

        double r = tocando ? raio * 1.15 : raio;
        g.setColor(tocando ? COR_ATIVA : cor);
        g.fill(new Ellipse2D.Double(p.getX() - r, p.getY() - r, 2 * r, 2 * r));
        g.setStroke(new BasicStroke(inicial ? 3.5f : 1.5f));
        g.setColor(inicial ? Color.WHITE : FUNDO.brighter());
        g.draw(new Ellipse2D.Double(p.getX() - r, p.getY() - r, 2 * r, 2 * r));

        //Ponto de escolha: anel tracejado em volta
        if (pontoDeEscolha(v)) {
            g.setColor(COR_ESCOLHA);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                    new float[] {4f, 3f}, 0f));
            double ra = r + 5;
            g.draw(new Ellipse2D.Double(p.getX() - ra, p.getY() - ra, 2 * ra, 2 * ra));
        }

        desenharNomes(g, v, p, r);

        if (inicial) {
            g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(Color.WHITE);
            String txt = "início";
            //Escreve do lado de dentro da elipse, para não ficar embaixo de um laço
            Point2D c = centro();
            double dx = c.getX() - p.getX();
            double dy = c.getY() - p.getY();
            double len = Math.max(1, Math.hypot(dx, dy));
            double tx = p.getX() + dx / len * (r + 16);
            double ty = p.getY() + dy / len * (r + 16);
            g.drawString(txt, (float) (tx - fm.stringWidth(txt) / 2.0), (float) (ty + fm.getAscent() / 2.0 - 2));
        }
    }

    /**
     * Escreve dentro do círculo a nota que soa (grande, em negrito) e,
     * com memória maior que 1, a nota anterior (pequena, em cima)
     * O vértice completo aparece na legenda ao passar o mouse
     */
    private void desenharNomes(Graphics2D g, Vertice v, Point2D p, double r) {
        List<Integer> estado = v.getEstado();
        String principal = Nota.nomePt(v.getNota());
        String contexto = null;
        if (estado.size() > 1) {
            contexto = (estado.size() > 2 ? "…" : "") + Nota.nomePt(estado.get(estado.size() - 2));
        }

        //Diminui a fonte até o texto caber dentro do círculo
        float tamanho = 15f;
        while (tamanho > 7f) {
            g.setFont(getFont().deriveFont(Font.BOLD, tamanho));
            FontMetrics fm = g.getFontMetrics();
            double altura = fm.getAscent() * (contexto == null ? 1 : 1.8);
            if (fm.stringWidth(principal) <= r * 1.7 && altura <= r * 1.5) {
                break;
            }
            tamanho -= 0.5f;
        }

        Font fontePrincipal = getFont().deriveFont(Font.BOLD, tamanho);
        Font fonteContexto = getFont().deriveFont(Font.PLAIN, tamanho * 0.75f);
        g.setFont(fontePrincipal);
        FontMetrics fmP = g.getFontMetrics();
        g.setFont(fonteContexto);
        FontMetrics fmC = g.getFontMetrics();

        Color corTexto = new Color(20, 20, 30);
        if (contexto == null) {
            g.setFont(fontePrincipal);
            g.setColor(corTexto);
            g.drawString(principal, (float) (p.getX() - fmP.stringWidth(principal) / 2.0),
                    (float) (p.getY() + fmP.getAscent() / 2.0 - 2));
        } else {
            double alturaTotal = fmC.getAscent() + fmP.getAscent();
            double topo = p.getY() - alturaTotal / 2.0;
            g.setFont(fonteContexto);
            g.setColor(new Color(20, 20, 30, 150));
            g.drawString(contexto, (float) (p.getX() - fmC.stringWidth(contexto) / 2.0),
                    (float) (topo + fmC.getAscent() - 1));
            g.setFont(fontePrincipal);
            g.setColor(corTexto);
            g.drawString(principal, (float) (p.getX() - fmP.stringWidth(principal) / 2.0),
                    (float) (topo + alturaTotal));
        }
    }

    /** Desenha a caixinha com a porcentagem de uma aresta (ex.: "40%") */
    private void desenharRotulo(Graphics2D g, String texto, double x, double y, Color borda) {
        g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        FontMetrics fm = g.getFontMetrics();
        double w = fm.stringWidth(texto) + 10;
        double h = fm.getHeight() + 2;
        RoundRectangle2D caixa = new RoundRectangle2D.Double(x - w / 2, y - h / 2, w, h, 10, 10);
        g.setColor(new Color(15, 15, 22, 230));
        g.fill(caixa);
        g.setColor(borda);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(caixa);
        g.setColor(Color.WHITE);
        g.drawString(texto, (float) (x - fm.stringWidth(texto) / 2.0), (float) (y + fm.getAscent() / 2.0 - 2));
    }

    /**
     * Desenha a legenda do canto superior esquerdo: memória, número de vértices,
     * de arestas e de pontos de escolha, o significado das cores e o vértice
     * com o mouse em cima
     */
    private void desenharLegenda(Graphics2D g) {
        //Conta quantos vértices são pontos de escolha (têm mais de uma saída)
        int escolhas = 0;
        for (Vertice v : grafo.getVertices()) {
            if (pontoDeEscolha(v)) {
                escolhas++;
            }
        }
        //Legenda empilhada no canto superior esquerdo, longe dos vértices
        g.setFont(getFont().deriveFont(Font.PLAIN, 12f));
        int x = 12;
        int y = 20;
        int passo = 16;
        g.setColor(new Color(200, 200, 215));
        g.drawString(String.format("Memória: %d   Vértices: %d   Arestas: %d",
                grafo.getMemoria(), grafo.numeroVertices(), grafo.numeroArestas()), x, y);
        y += passo;
        g.drawString("Pontos de escolha: " + escolhas, x, y);
        y += passo + 4;
        g.setColor(COR_CERTA.brighter());
        g.drawString("━ caminho certo (100%)", x, y);
        y += passo;
        g.setColor(COR_ESCOLHA);
        g.drawString("━ sorteio (ponto de escolha)", x, y);
        y += passo;
        g.setColor(COR_ATIVA);
        g.drawString("● tocando agora", x, y);
        y += passo + 4;

        if (verticeSobMouse != null) {
            g.setColor(COR_FOCO);
            g.drawString("Vértice: " + verticeSobMouse, x, y);
        } else {
            g.setColor(new Color(150, 150, 165));
            g.drawString("Passe o mouse num vértice", x, y);
        }
    }
}
