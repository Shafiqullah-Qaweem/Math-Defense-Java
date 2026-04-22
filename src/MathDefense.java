
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public class MathDefense extends JFrame {

    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale", "2.0"); 
        SwingUtilities.invokeLater(() -> {
            MathDefense game = new MathDefense();
            game.setVisible(true);
        });
    }
    private static Color hex(String hex) {
        hex = hex.trim();

        if (!hex.startsWith("#") || hex.length() != 7) {
            throw new IllegalArgumentException("Invalid hex color: " + hex);
        }

        return new Color(
                Integer.parseInt(hex.substring(1, 3), 16),
                Integer.parseInt(hex.substring(3, 5), 16),
                Integer.parseInt(hex.substring(5, 7), 16)
        );
    }


    public MathDefense() {
        setTitle("Math Defense: Final Project");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setUndecorated(true);

        GamePanel panel = new GamePanel();
        add(panel);

        panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "exit");
        panel.getActionMap().put("exit", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { System.exit(0); }
        });
    }

    static class GamePanel extends JPanel implements ActionListener, MouseListener, MouseMotionListener {

        Timer timer;
        Random rand = new Random();

        int score = 0;
        int health = 100;
        int totalBoxesSolved = 0;
        int waveNumber = 1;
        boolean isBossWave = false;
        boolean gameOver = false;

        double maxTime = 45 * 60;
        double currentTime = maxTime;

        ArrayList<Ball> balls = new ArrayList<>();
        ArrayList<Box> boxes = new ArrayList<>();
        ArrayList<Effect> effects = new ArrayList<>();

        Ball draggedBall = null;
        int redFlashTimer = 0;

        int dragOffsetX, dragOffsetY;

        public GamePanel() {
            setFocusable(true);
            addMouseListener(this);
            addMouseMotionListener(this);

            startNormalWave();

            timer = new Timer(16, this);
            timer.start();
        }




        void startNormalWave() {
            balls.clear();
            boxes.clear();
            isBossWave = false;

            double seconds = Math.max(30, 45 - (waveNumber * 2));
            maxTime = seconds * 60;
            currentTime = maxTime;

            int w = Toolkit.getDefaultToolkit().getScreenSize().width;
            int h = Toolkit.getDefaultToolkit().getScreenSize().height;
            int boxW = 250;
            int gap = (w - (boxW * 3)) / 4;
            int y = h - 300 ;

            int scale = (waveNumber - 1) * 5;

            boxes.add(new Box(gap, y, 1 + scale, 15 + scale, "Small"));
            boxes.add(new Box(gap*2 + boxW, y, 10 + scale, 35 + scale, "Medium"));
            boxes.add(new Box(gap*3 + boxW*2, y, 25 + scale, 50 + scale, "Large"));
        }

        void startBossWave() {
            balls.clear();
            boxes.clear();
            isBossWave = true;
            maxTime = 90 * 60;
            currentTime = maxTime;

            int w = getWidth();
            int h = getHeight();

            int bossMin = 20 + (waveNumber * 2);
            int bossMax = 55 + (waveNumber * 2);

            Box boss = new Box(w/2 - 200, h - 400, bossMin, bossMax, "BOSS");
            boss.w = 400;
            boss.h = 300;
            boss.maxSlots = 10;

            int average = (bossMin + bossMax) / 2;
            boss.generateTarget(average * 10);

            boxes.add(boss);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (gameOver) { repaint(); return; }

            double timeDecay = 1 + (waveNumber * 0.5);
            currentTime -= timeDecay;

            if (currentTime <= 0) {

                gameOver = true;

            }

            if (redFlashTimer > 0) redFlashTimer -= 20;

            boolean pending = true;
            for(Box b : boxes) if(!b.isSolved) pending = false;

            if (!pending) {
                int maxBalls = isBossWave ? 7 : 5;
                if (balls.size() < maxBalls && rand.nextInt(100) < 3) {
                    spawnBall();
                }
            }
            
            Iterator<Ball> it = balls.iterator();

            while (it.hasNext()) {
                Ball b = it.next();

                if (b != draggedBall) {
                    b.y += (3.5 + (waveNumber * 0.5));
                }

                if (b.y > getHeight()) {
                    if (!b.isHealth && checkIfBallWasUseful(b)) {
                        damagePlayer(5);

                        spawnEffect((int) b.x, getHeight() - 50, "MISSED!", new Color(255,0,0));

                    }
                    it.remove();
                }
            }


            for(int i=0; i<effects.size(); i++) {
                Effect ef = effects.get(i);
                ef.update();
                if(ef.isDead()) { effects.remove(i); i--; }
            }

            checkWaveCompletion();
            repaint();
        }

        void spawnBall() {
            int width = getWidth();
            if (width <= 100) {
                width = Toolkit.getDefaultToolkit().getScreenSize().width;
            }

            int x = rand.nextInt(Math.max(1, width - 100));

            if (rand.nextInt(100) < 5) {
                Ball h = new Ball(x, -60, 0);
                h.isHealth = true;
                balls.add(h);
                return;
            }

            int val = 1;

            ArrayList<Box> active = new ArrayList<>();
            for (Box b : boxes) if (!b.isSolved) active.add(b);

            if (active.isEmpty()) return;
            Box target = active.get(rand.nextInt(active.size()));

            int needed = target.targetSum - target.currentSum;
            int slotsLeft = target.maxSlots - target.ballsInside.size();

            if (slotsLeft == 1) {
                if (rand.nextBoolean()) val = needed;
                else val = rand.nextInt((target.maxRange - target.minRange) + 1) + target.minRange;
            } else {
                int maxSafe = needed - ((slotsLeft - 1) * target.minRange);
                int realMax = Math.min(target.maxRange, maxSafe);
                int realMin = target.minRange;


                if (realMax < realMin) val = realMin;

                else val = rand.nextInt((realMax - realMin) + 1) + realMin;
            }

            if (val < 1) val = 1;
            balls.add(new Ball(x, -60, val));
        }

        boolean checkIfBallWasUseful(Ball b) {
            for (Box box : boxes) {
                if (!box.isSolved && box.checkMath(b.value).equals("OK")) return true;
            }
            return false;
        }

        void damagePlayer(int amt) {
            health -= amt;
            redFlashTimer = 100;
            if (health <= 0) {
                health = 0;
                gameOver = true;
            }
        }

        void spawnEffect(int x, int y, String msg, Color c) {
            effects.add(new Effect(x, y, msg, c));
        }

        void checkWaveCompletion() {
            boolean allSolved = true;
            for (Box b : boxes) if (!b.isSolved) allSolved = false;

            if (allSolved) {
                score += 1000;

                if (isBossWave) {
                    spawnEffect(getWidth()/2, getHeight()/2, "BOSS DEFEATED!", Color.CYAN);
                    health = Math.min(100, health + 50);
                    waveNumber++;
                    startNormalWave();
                } else {
                    if (totalBoxesSolved > 0 && totalBoxesSolved % 6 == 0) {
                        spawnEffect(getWidth()/2, getHeight()/2, "BOSS INCOMING!", Color.RED);
                        startBossWave();
                    } else {
                        waveNumber++;
                        startNormalWave();
                    }
                }
            }
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (gameOver) {
                score=0; health=100; waveNumber=1; totalBoxesSolved=0; gameOver=false;
                startNormalWave();
                return;
            }

            Iterator<Ball> it = balls.iterator();
            
            while (it.hasNext()) {
                Ball b = it.next();

                if (b.getBounds().contains(e.getPoint())) {

                    if (b.isHealth) {
                        health = Math.min(100, health + 15);
                        spawnEffect((int) b.x, (int) b.y, "+15 HP", Color.GREEN);
                        it.remove();
                        return;
                    }

                    draggedBall = b;
                    dragOffsetX = e.getX() - (int) b.x;
                    dragOffsetY = e.getY() - (int) b.y;
                    break;
                }
            }

        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (draggedBall != null) {
                boolean consumed = false;
                for (Box box : boxes) {
                    if (box.contains((int)draggedBall.x, (int)draggedBall.y)) {

                        if (box.isSolved) continue;

                        String result = box.checkMath(draggedBall.value);

                        if (result.equals("OK")) {

                            box.addBall(draggedBall.value);
                            score += 100;
                            spawnEffect(box.x+50, box.y, "+100", Color.YELLOW);
                            consumed = true;

                            if (box.isComplete()) {
                                score += 500;
                                box.isSolved = true;
                                totalBoxesSolved++;
                                spawnEffect(box.x+50, box.y-30, "SOLVED!", Color.GREEN);
                                checkWaveCompletion();
                            }
                        } else {
                            damagePlayer(10);
                            spawnEffect(box.x+20, box.y, result, Color.RED);
                            consumed = true;
                        }
                        break;
                    }
                }
                if (consumed) balls.remove(draggedBall);
                draggedBall = null;
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (draggedBall != null) {
                draggedBall.x = e.getX() - dragOffsetX;
                draggedBall.y = e.getY() - dragOffsetY;
            }
        }

        public void mouseClicked(MouseEvent e){} public void mouseEntered(MouseEvent e){} public void mouseExited(MouseEvent e){} public void mouseMoved(MouseEvent e){}

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            g.setColor(hex("#2D2C29"));
            g.fillRect(0, 0, getWidth(), getHeight());

            if (redFlashTimer > 0) {
                g.setColor(new Color(255, 0, 0, redFlashTimer));
                g.fillRect(0, 0, getWidth(), getHeight());
            }

            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 24));
            g.drawString("Score: " + score, 30, 50);

            g.setColor(hex("#52ACCB"));
            g.drawString("Wave: " + waveNumber, 30, 110);

            if (isBossWave) {
                g.setColor(hex("#B53911"));
                g.drawString("BOSS FIGHT!", 30, 80);
            } else {
                g.setColor(hex("#9CA3AF"));
                int left = 6 - (totalBoxesSolved % 6);
                g.drawString("Next Boss in: " + left + " boxes", 30, 80);
            }

            int barW = 400; int barX = (getWidth()-barW)/2;
            g.setColor(hex("#7E99B0")); g.fillRect(barX, 20, barW, 20);
            g.setColor(hex("#4F83B0")); g.fillRect(barX, 20, (int)((currentTime/maxTime)*barW), 20);
            g.drawString("TIME", barX+barW+10, 38);

            g.setColor(health > 50 ? hex("#AAC658") :(((health <= 50) && (health > 15 ))? hex("#FBDC88")  : hex("#CE5C38")));
            g.fillRect(barX, 50, barW, 20);
            g.setColor(health > 50 ? hex("#819844") :(((health <= 50) && (health > 15 ))? hex("#C2A74C")  : hex("#B53911"))); g.fillRect(barX, 50, (int)((health/100.0)*barW), 20);
            g.setColor(Color.WHITE);
            g.drawString("HP", barX+barW+10, 68);

            for (Box b : boxes) b.draw(g);
            for (Ball b : balls) b.draw(g);
            for (Effect ef : effects) ef.draw(g);

            if (gameOver) {
                g.setColor(new Color(0,0,0,220));
                g.fillRect(0,0,getWidth(), getHeight());
                g.setColor(Color.RED);
                g.setFont(new Font("Arial", Font.BOLD, 80));
                String msg = "GAME OVER";
                int w = g.getFontMetrics().stringWidth(msg);
                g.drawString(msg, (getWidth()-w)/2, getHeight()/2 - 20);

                g.setColor(Color.WHITE);
                g.setFont(new Font("Arial", Font.BOLD, 30));
                String sub = "Final Score: " + score;
                w = g.getFontMetrics().stringWidth(sub);
                g.drawString(sub, (getWidth()-w)/2, getHeight()/2 + 40);

                g.setColor(Color.YELLOW);
                String sub2 = "Click Anywhere to Restart";
                w = g.getFontMetrics().stringWidth(sub2);
                g.drawString(sub2, (getWidth()-w)/2, getHeight()/2 + 90);
            }
        }
    }

    static class Ball {
        double x, y;
        int value;
        boolean isHealth = false;
        int size = 60;
        public Ball(int x, int y, int v) { this.x=x; this.y=y; this.value=v; }
        public Rectangle getBounds() { return new Rectangle((int)x, (int)y, size, size); }
        public void draw(Graphics g) {
            if (isHealth) {
                g.setColor(hex("#819844"));
                g.fillOval((int)x, (int)y, size, size);
                g.setColor(Color.WHITE);
                g.setFont(new Font("Arial", Font.BOLD, 15));
                g.drawString("+15 HP", (int)x+4, (int)(y+size/2+5));
            } else {
                g.setColor(hex("#DF814D"));
                g.fillOval((int)x, (int)y, size, size);
                g.setColor(Color.white);

                g.setFont(new Font("Arial", Font.BOLD, 24));
                g.drawString((value<10)? " "+value :""+value, (int)x+16, (int)y+38);
            }
        }
    }

    static class Box {
        int x, y, w, h;
        int minRange, maxRange, maxSlots = 3;
        int targetSum, currentSum = 0;
        String label;
        boolean isSolved = false;
        ArrayList<Integer> ballsInside = new ArrayList<>();
        Random r = new Random();

        public Box(int x, int y, int min, int max, String lbl) {
            this.x=x; this.y=y; this.w=250; this.h=200;
            this.minRange=min; this.maxRange=max;
            this.label=lbl;
            generateTarget(0);
        }

        void generateTarget(int forcedSum) {
            currentSum = 0; ballsInside.clear(); isSolved=false;
            if (forcedSum > 0) targetSum = forcedSum;
            else {
                targetSum = 0;
                for(int i=0; i<maxSlots; i++) targetSum += (r.nextInt(maxRange - minRange + 1) + minRange);
            }
        }

        String checkMath(int val) {
            if (ballsInside.size() >= maxSlots) return "FULL!";

            if (val < minRange) return "NOT IN RANGE";
            if (val > maxRange) return "NOT IN RANGE";
            if (currentSum + val > targetSum) return "TOO BIG";

            int slotsRemaining = maxSlots - (ballsInside.size() + 1);
            if (slotsRemaining > 0) {
                int sumNeededLater = targetSum - (currentSum + val);
                int minPossible = slotsRemaining * minRange;
                int maxPossible = slotsRemaining * maxRange;

                if (sumNeededLater < minPossible) return "TOO BIG";
                if (sumNeededLater > maxPossible) return "TOO SMALL";
            } else {
                if (currentSum + val != targetSum) {
                    if (currentSum + val < targetSum) return "TOO SMALL";
                    if (currentSum + val > targetSum) return "TOO BIG";
                }
            }
            return "OK";
        }

        boolean canAccept(int val) {
            return checkMath(val).equals("OK");
        }

        void addBall(int val) { ballsInside.add(val); currentSum += val; }

        void reset() { ballsInside.clear(); currentSum = 0; }
        boolean isComplete() { return ballsInside.size() == maxSlots && currentSum == targetSum; }
        boolean contains(int mx, int my) { return mx>x && mx<x+w && my>y && my<y+h; }



        void draw(Graphics g) {
            if (isSolved) g.setColor(hex("#708317"));
            else if (label.equals("BOSS")) g.setColor(hex("#B53911"));
            else g.setColor(hex("#D74631"));

            g.fillRect(x, y, w, h);
            g.setColor(Color.WHITE);


            g.setFont(new Font("Arial", Font.BOLD, 18));
            Graphics2D g2 = (Graphics2D) g;
            FontMetrics fm1 = g2.getFontMetrics();
            String text1 = label + " [" + minRange + "-" + maxRange + "]";
            int textWidth = fm1.stringWidth(text1);
            int textHeight = fm1.getAscent();
            int text1X = x + (w - textWidth) / 2;
            g.drawString( text1, text1X, y+45);
            g.setFont(new Font("Arial", Font.BOLD, 40));
            Graphics2D g3 = (Graphics2D) g;
            FontMetrics fm2 = g3.getFontMetrics();
            String text2 = currentSum + " / " + targetSum;
            int text2Width = fm2.stringWidth(text2);
            int text2Height = fm2.getAscent();
            int text2X = x + (w - text2Width) / 2;
            g.drawString(text2, text2X, y+100);

            int startX = label.equals("BOSS")? x + 80 : x+55;
            for(int i=0; i<maxSlots; i++) {
                int dx = startX + (i%5)*50;
                int startY = y + 100 + (i/5)*50;
                int dy = startY+20;
                g.setColor(Color.DARK_GRAY); g.fillOval(dx, dy, 40, 40);
                if (i < ballsInside.size()) {
                    g.setColor(Color.WHITE); g.fillOval(dx, dy, 40, 40);
                    g.setColor(Color.BLACK); g.setFont(new Font("Arial", Font.BOLD, 14));
                    g.drawString(""+ballsInside.get(i), dx+10, dy+25);
                }
            }
        }
    }

    static class Effect {
        int x, y, life=255;
        String msg; Color c;
        public Effect(int x, int y, String m, Color c) { this.x=x; this.y=y; this.msg=m; this.c=c; }
        public void update() { y-=2; life -= 13;}
        public boolean isDead() {return life <= 0;}
        public void draw(Graphics g) {
            if(life>0) {
                Color faded = new Color(
                        c.getRed(),
                        c.getGreen(),
                        c.getBlue(),
                        life          // 👈 alpha
                );
                g.setColor(faded); g.setFont(new Font("Arial", Font.BOLD, 20));
                g.drawString(msg, x, y);
            }
        }
    }
}
