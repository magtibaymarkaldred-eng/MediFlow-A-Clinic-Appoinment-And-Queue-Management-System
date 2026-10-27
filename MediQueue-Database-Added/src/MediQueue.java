package mediqueue;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;

import mediqueue.MediQueueData.Account;
import mediqueue.MediQueueData.Appt;
import mediqueue.MediQueueData.Doctor;
import mediqueue.MediQueueData.Pat;
import mediqueue.MediQueueData.QEntry;
import mediqueue.MediQueueData.Sched;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static mediqueue.MediQueueData.*;

/**
 * MediQueue - Clinic Appointment & Queue System. Plain Java (Swing), separated into application and data files, no third-party libraries, Java 17.
 *   Compile:  javac -d build src/MediQueue.java src/MediQueueData.java      Run:  java -cp build mediqueue.MediQueue
 *   
 * Patients can register their own accounts. Admin credentials are restricted.
 * Data is saved next to the program in mediqueue-data.txt and mediqueue-users.txt.
 */
public class MediQueue extends JFrame {

    // =====================================================================
    //  THEME
    // =====================================================================
    static final Color NAVY1 = new Color(0x0B2350), NAVY2 = new Color(0x12326F), BLUE = new Color(0x2563EB);
    static final Color BG1 = new Color(0xDBEAFE), BG2 = new Color(0xD1FAE5), TEXT = new Color(0x1E293B);
    static final Color MUTED = new Color(0x64748B), LINE = new Color(0xE8EDF5), INPUT = new Color(0xCBD5E1);
    static final Color RED = new Color(0xDC2626), GREEN = new Color(0x22B573), PURPLE = new Color(0x8B5CF6);
    static final String FAMILY = pickFamily();

    static String pickFamily() {
        try {
            Set<String> have = new HashSet<>(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String f : new String[]{"Segoe UI", "SF Pro Text", "Helvetica Neue", "Ubuntu", "Noto Sans", "Open Sans"}) if (have.contains(f)) return f;
        } catch (Exception ignored) { }
        return Font.SANS_SERIF;
    }
    static Font font(int style, int size) { return new Font(FAMILY, style, size); }
    static Border pad(int t, int l, int b, int r) { return BorderFactory.createEmptyBorder(t, l, b, r); }
    static Border inputBorder() { return BorderFactory.createCompoundBorder(new LineBorder(INPUT, 1, true), pad(7, 10, 7, 10)); }
    static Color shade(Color c, double f) { return new Color((int) (c.getRed() * f), (int) (c.getGreen() * f), (int) (c.getBlue() * f), c.getAlpha()); }
    static void aa(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    // =====================================================================
    //  CUSTOM COMPONENTS
    // =====================================================================
    static class RoundButton extends JButton {
        private Color bg, fg;
        private boolean hover;
        RoundButton(String text, Color bg, Color fg) {
            super(text);
            this.bg = bg; this.fg = fg;
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false); setOpaque(false);
            setFont(font(Font.BOLD, 13)); setBorder(pad(9, 18, 9, 18));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }
        RoundButton mini() { setFont(font(Font.BOLD, 11)); setBorder(pad(5, 11, 5, 11)); return this; }
        RoundButton onClick(Runnable r) { addActionListener(e -> r.run()); return this; }
        void setColors(Color b, Color f) { bg = b; fg = f; repaint(); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); aa(g);
            Color fill = !isEnabled() ? new Color(0xF1F5F9) : getModel().isPressed() ? shade(bg, .85) : hover ? shade(bg, .92) : bg;
            g.setColor(fill); g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g.setFont(getFont()); FontMetrics fm = g.getFontMetrics();
            g.setColor(isEnabled() ? fg : new Color(0xCBD5E1));
            g.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    static class RoundedPanel extends JPanel {
        private final int arc; private final Color fill, line;
        RoundedPanel(int arc, Color fill, Color line) { this.arc = arc; this.fill = fill; this.line = line; setOpaque(false); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); aa(g);
            g.setColor(fill); g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            if (line != null) { g.setColor(line); g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc); }
            g.dispose();
        }
    }

    static class GradientPanel extends JPanel {
        private final Color a, b; private final boolean diagonal;
        GradientPanel(Color a, Color b, boolean diagonal) { this.a = a; this.b = b; this.diagonal = diagonal; }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setPaint(diagonal ? new GradientPaint(0, 0, a, getWidth(), getHeight(), b) : new GradientPaint(0, 0, a, 0, getHeight(), b));
            g.fillRect(0, 0, getWidth(), getHeight()); g.dispose();
        }
    }

    static class Badge extends JLabel {
        private final Color bg;
        Badge(String text) {
            super(text, SwingConstants.CENTER);
            Color f; Color b;
            switch (text) {
                case "In Consultation": case "Confirmed": case "Available": f = new Color(0x15803D); b = new Color(0xDCFCE7); break;
                case "Waiting": f = new Color(0xB45309); b = new Color(0xFEF3C7); break;
                case "Next": case "Upcoming": f = new Color(0x1D4ED8); b = new Color(0xDBEAFE); break;
                case "Scheduled": f = new Color(0x4338CA); b = new Color(0xE0E7FF); break;
                case "Cancelled": case "Not Available": f = new Color(0xB91C1C); b = new Color(0xFEE2E2); break;
                default: f = new Color(0x475569); b = new Color(0xE2E8F0);
            }
            bg = b; setForeground(f); setFont(font(Font.BOLD, 11)); setBorder(pad(3, 11, 3, 11)); setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); aa(g);
            g.setColor(bg); g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight()); g.dispose();
            super.paintComponent(g0);
        }
    }

    /** Icons drawn with shapes on a 24x24 grid. */
    static class Ico implements Icon {
        private final String kind; private final Color color; private final int size;
        Ico(String kind, Color color, int size) { this.kind = kind; this.color = color; this.size = size; }
        public int getIconWidth() { return size; }
        public int getIconHeight() { return size; }
        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create(); aa(g);
            g.translate(x, y); g.scale(size / 24.0, size / 24.0);
            g.setColor(color); g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (kind) {
                case "home": { Path2D p = new Path2D.Double(); p.moveTo(3, 11); p.lineTo(12, 3); p.lineTo(21, 11); g.draw(p);
                    g.drawLine(5, 10, 5, 20); g.drawLine(19, 10, 19, 20); g.drawLine(5, 20, 19, 20); g.drawLine(10, 20, 10, 14); g.drawLine(14, 14, 14, 20); g.drawLine(10, 14, 14, 14); break; }
                case "cal": g.drawRoundRect(3, 5, 18, 16, 4, 4); g.drawLine(3, 10, 21, 10); g.drawLine(8, 3, 8, 7); g.drawLine(16, 3, 16, 7); break;
                case "list": for (int i = 0; i < 3; i++) { g.drawLine(9, 7 + i * 5, 20, 7 + i * 5); g.drawLine(4, 7 + i * 5, 4, 7 + i * 5); } break;
                case "users": g.drawOval(4, 4, 8, 8); g.draw(new Arc2D.Double(1.5, 14, 13, 13, 0, 180, Arc2D.OPEN)); g.drawOval(15, 6, 6, 6); g.draw(new Arc2D.Double(15, 15, 8, 8, 0, 180, Arc2D.OPEN)); break;
                case "user": g.drawOval(8, 3, 8, 8); g.draw(new Arc2D.Double(4, 14, 16, 16, 0, 180, Arc2D.OPEN)); break;
                case "clock": g.drawOval(3, 3, 18, 18); g.drawLine(12, 7, 12, 12); g.drawLine(12, 12, 16, 14); break;
                case "chart": g.drawLine(6, 20, 6, 13); g.drawLine(12, 20, 12, 5); g.drawLine(18, 20, 18, 10); break;
                case "out": { Path2D p = new Path2D.Double(); p.moveTo(10, 4); p.lineTo(5, 4); p.lineTo(5, 20); p.lineTo(10, 20); g.draw(p);
                    g.drawLine(10, 12, 21, 12); g.drawLine(17, 8, 21, 12); g.drawLine(17, 16, 21, 12); break; }
                case "bell": g.draw(new Arc2D.Double(6, 4, 12, 14, 0, 180, Arc2D.OPEN)); g.drawLine(6, 11, 5, 17); g.drawLine(18, 11, 19, 17); g.drawLine(4, 17, 20, 17); g.drawLine(10, 20, 14, 20); break;
                case "card": g.drawRoundRect(2, 5, 20, 14, 3, 3); g.drawLine(2, 10, 22, 10); break;
                case "next": g.drawLine(9, 5, 16, 12); g.drawLine(16, 12, 9, 19); break;
                case "hash": g.drawLine(9, 4, 7, 20); g.drawLine(17, 4, 15, 20); g.drawLine(4, 9, 20, 9); g.drawLine(4, 15, 20, 15); break;
                default: g.drawLine(12, 5, 12, 19); g.drawLine(5, 12, 19, 12);   // plus
            }
            g.dispose();
        }
    }

    static class Avatar extends JComponent {
        private final String initials; private final int size;
        Avatar(String name, int size) {
            StringBuilder sb = new StringBuilder();
            for (String w : name.split(" ")) if (!w.isEmpty() && sb.length() < 2) sb.append(Character.toUpperCase(w.charAt(0)));
            initials = sb.toString(); this.size = size; setPreferredSize(new Dimension(size, size));
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); aa(g);
            g.setPaint(new GradientPaint(0, 0, new Color(0x60A5FA), size, size, BLUE)); g.fillOval(0, 0, size, size);
            g.setColor(Color.WHITE); g.setFont(font(Font.BOLD, size / 3)); FontMetrics fm = g.getFontMetrics();
            g.drawString(initials, (size - fm.stringWidth(initials)) / 2, (size - fm.getHeight()) / 2 + fm.getAscent()); g.dispose();
        }
    }

    static class NavButton extends JButton {
        private final String icon; private boolean on, hover;
        NavButton(String text, String icon) {
            super(text); this.icon = icon;
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false); setOpaque(false);
            setFont(font(Font.PLAIN, 13)); setHorizontalAlignment(SwingConstants.LEFT); setBorder(pad(11, 44, 11, 10));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }
        NavButton selected(boolean b) { on = b; return this; }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); aa(g);
            if (on) { g.setColor(new Color(0x2F6BFF)); g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12); }
            else if (hover) { g.setColor(new Color(255, 255, 255, 28)); g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12); }
            Color c = on ? Color.WHITE : new Color(0xCBD8F5);
            new Ico(icon, c, 18).paintIcon(this, g, 14, (getHeight() - 18) / 2);
            g.setFont(getFont()); g.setColor(c); FontMetrics fm = g.getFontMetrics();
            g.drawString(getText(), 44, (getHeight() - fm.getHeight()) / 2 + fm.getAscent()); g.dispose();
        }
    }

    static class ToastPane extends JComponent {
        private String text = ""; private Color accent = GREEN; private float alpha; private long started; private javax.swing.Timer timer;
        ToastPane() { setOpaque(false); }
        @Override public boolean contains(int x, int y) { return false; }   // clicks pass through
        void show(String msg, Color c) {
            text = msg; accent = c; started = System.currentTimeMillis();
            if (timer != null) timer.stop();
            timer = new javax.swing.Timer(15, e -> {
                long t = System.currentTimeMillis() - started;
                alpha = t < 250 ? t / 250f : t < 3000 ? 1f : t < 3400 ? 1f - (t - 3000) / 400f : 0f;
                if (t >= 3400) timer.stop();
                repaint();
            });
            timer.start();
        }
        @Override protected void paintComponent(Graphics g0) {
            if (alpha <= 0f) return;
            Graphics2D g = (Graphics2D) g0.create(); aa(g);
            g.setFont(font(Font.BOLD, 13)); FontMetrics fm = g.getFontMetrics();
            int w = fm.stringWidth(text) + 66, h = 46, x = (getWidth() - w) / 2, y = getHeight() - 100 + Math.round((1f - alpha) * 14);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, alpha)));
            g.setColor(new Color(0, 0, 0, 45)); g.fillRoundRect(x + 2, y + 5, w, h, 26, 26);
            g.setColor(new Color(0x0F172A)); g.fillRoundRect(x, y, w, h, 26, 26);
            g.setColor(accent); g.fillOval(x + 20, y + h / 2 - 6, 12, 12);
            g.setColor(Color.WHITE); g.drawString(text, x + 44, y + (h - fm.getHeight()) / 2 + fm.getAscent()); g.dispose();
        }
    }

    /** Scroll content that always matches the window width (no sideways scrolling). */
    static class PageBox extends JPanel implements Scrollable {
        PageBox() { super(new BorderLayout()); setOpaque(false); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 18; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 120; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** Responsive grid: as many equal columns as fit (each at least minW wide), wrapping onto new rows. */
    static class FlowGrid implements LayoutManager {
        private final int gap, minW; private int lastCols = -1;
        FlowGrid(int gap, int minW) { this.gap = gap; this.minW = minW; }
        private int colsFor(int width, int n) { return Math.max(1, Math.min(n, (width + gap) / (minW + gap))); }
        private int rowHeight(Container p, int from, int cols) {
            int h = 0;
            for (int j = from; j < Math.min(p.getComponentCount(), from + cols); j++) h = Math.max(h, p.getComponent(j).getPreferredSize().height);
            return h;
        }
        public Dimension preferredLayoutSize(Container p) {
            int w = p.getWidth() > 0 ? p.getWidth() : 1000, n = p.getComponentCount(), cols = colsFor(w, n), h = 0;
            for (int i = 0; i < n; i += cols) h += rowHeight(p, i, cols) + (i > 0 ? gap : 0);
            return new Dimension(w, h);
        }
        public Dimension minimumLayoutSize(Container p) { return new Dimension(minW, 10); }
        public void layoutContainer(Container p) {
            int w = p.getWidth(), n = p.getComponentCount(); if (n == 0) return;
            int cols = colsFor(w, n), cw = (w - gap * (cols - 1)) / cols, y = 0;
            for (int i = 0; i < n; i += cols) {
                int rh = rowHeight(p, i, cols);
                for (int j = i; j < Math.min(n, i + cols); j++) p.getComponent(j).setBounds((j - i) * (cw + gap), y, cw, rh);
                y += rh + gap;
            }
            if (cols != lastCols) { lastCols = cols; SwingUtilities.invokeLater(p::revalidate); }
        }
        public void addLayoutComponent(String name, Component c) { }
        public void removeLayoutComponent(Component c) { }
    }

    // ---------- tiny layout helpers ----------
    static JLabel label(String text, int style, int size, Color c) { JLabel l = new JLabel(text); l.setFont(font(style, size)); l.setForeground(c); return l; }
    static RoundButton btn(String text, Color bg, Color fg, Runnable r) { return new RoundButton(text, bg, fg).onClick(r); }
    static JPanel grid(int gap, int minW, JComponent... items) {
        JPanel p = new JPanel(new FlowGrid(gap, minW)); p.setOpaque(false);
        for (JComponent c : items) p.add(c);
        return p;
    }
    static JPanel stack(int gap, List<? extends JComponent> items) {
        JPanel p = new JPanel(new GridBagLayout()); p.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL; g.anchor = GridBagConstraints.NORTH;
        for (int i = 0; i < items.size(); i++) { g.gridy = i; g.insets = new Insets(i == 0 ? 0 : gap, 0, 0, 0); p.add(items.get(i), g); }
        GridBagConstraints f = new GridBagConstraints(); f.gridx = 0; f.gridy = items.size(); f.weighty = 1;
        JPanel filler = new JPanel(); filler.setOpaque(false); p.add(filler, f);   // keeps everything at the top
        return p;
    }
    static JPanel stack(int gap, JComponent... items) { return stack(gap, Arrays.asList(items)); }
    static JPanel cols(int gap, JComponent... items) {
        JPanel p = new JPanel(new GridLayout(1, items.length, gap, 0)); p.setOpaque(false);
        for (JComponent c : items) p.add(c);
        return p;
    }
    static JPanel top(JComponent c) { JPanel p = new JPanel(new BorderLayout()); p.setOpaque(false); p.add(c, BorderLayout.NORTH); return p; }
    static JPanel narrow(JComponent c, int w) {
        c.setPreferredSize(new Dimension(w, c.getPreferredSize().height));
        JPanel p = new JPanel(new BorderLayout()); p.setOpaque(false); p.add(c, BorderLayout.WEST); return p;
    }
    static JPanel flow(int gap, JComponent... items) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, gap, 0)); p.setOpaque(false);
        for (JComponent c : items) p.add(c);
        return p;
    }
    static JTextField field(String text) { JTextField f = new JTextField(text); f.setFont(font(Font.PLAIN, 13)); f.setBorder(inputBorder()); return f; }
    static JPasswordField pwField() { JPasswordField f = new JPasswordField(); f.setFont(font(Font.PLAIN, 13)); f.setBorder(inputBorder()); return f; }
    static <T> JComboBox<T> combo(T[] items) { JComboBox<T> c = new JComboBox<>(items); c.setFont(font(Font.PLAIN, 13)); c.setBackground(Color.WHITE); c.setPreferredSize(new Dimension(180, 38)); return c; }
    static JComponent labeled(String caption, JComponent c) { return stack(4, label(caption, Font.BOLD, 12, MUTED), c); }
    static JPanel form(JComponent... rows) { return stack(12, rows); }

    /** A white rounded card with a title and an optional "View All" link or note. */
    JComponent card(String title, String go, String note, JComponent body) {
        RoundedPanel c = new RoundedPanel(16, Color.WHITE, LINE);
        c.setLayout(new BorderLayout(0, 12)); c.setBorder(pad(16, 16, 16, 16));
        JPanel head = new JPanel(new BorderLayout()); head.setOpaque(false);
        head.add(label(title, Font.BOLD, 15, TEXT), BorderLayout.WEST);
        if (go != null) {
            JButton l = new JButton("View All"); l.setFont(font(Font.BOLD, 12)); l.setForeground(BLUE);
            l.setBorderPainted(false); l.setContentAreaFilled(false); l.setFocusPainted(false); l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            l.addActionListener(e -> navigate(go)); head.add(l, BorderLayout.EAST);
        } else if (note != null) head.add(label(note, Font.PLAIN, 11, MUTED), BorderLayout.EAST);
        c.add(head, BorderLayout.NORTH); c.add(body, BorderLayout.CENTER);
        return c;
    }

    /** Table built from labels, badges and buttons so every cell can hold any component. */
    static JComponent table(String[] heads, List<Object[]> rows, int highlight, double... weights) {
        JPanel t = new JPanel(new GridBagLayout()); t.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints(); g.fill = GridBagConstraints.BOTH; g.anchor = GridBagConstraints.WEST;
        int r = 0, n = heads.length;
        for (int c = 0; c < n; c++) { g.gridx = c; g.gridy = r; g.weightx = weights.length > c ? weights[c] : 1; t.add(cell(label(heads[c], Font.BOLD, 11, MUTED), null), g); }
        r++;
        for (int i = 0; i < rows.size(); i++) {
            JPanel sep = new JPanel(); sep.setBackground(LINE); sep.setPreferredSize(new Dimension(1, 1));
            g.fill = GridBagConstraints.HORIZONTAL; g.gridx = 0; g.gridy = r++; g.gridwidth = n; g.weightx = 1; t.add(sep, g); g.gridwidth = 1; g.fill = GridBagConstraints.BOTH;
            Color bg = i == highlight ? new Color(0xEEF4FF) : null;
            for (int c = 0; c < n; c++) {
                Object o = rows.get(i)[c];
                JComponent comp = o instanceof JComponent ? (JComponent) o : label(String.valueOf(o), i == highlight ? Font.BOLD : Font.PLAIN, 12, TEXT);
                g.gridx = c; g.gridy = r; g.weightx = weights.length > c ? weights[c] : 1; t.add(cell(comp, bg), g);
            }
            r++;
        }
        if (rows.isEmpty()) { g.gridx = 0; g.gridy = r; g.gridwidth = n; t.add(cell(label("Nothing here yet.", Font.PLAIN, 12, MUTED), null), g); }
        return t;
    }
    static JComponent cell(JComponent c, Color bg) {
        JPanel p = new JPanel(new BorderLayout()); p.setBorder(pad(8, 6, 8, 6)); p.add(c, BorderLayout.WEST);
        if (bg != null) { p.setOpaque(true); p.setBackground(bg); } else p.setOpaque(false);
        return p;
    }
    static List<Object[]> rows(Object[]... r) { return new ArrayList<>(Arrays.asList(r)); }

    // =====================================================================
    //  WINDOW STATE
    // =====================================================================
    private final CardLayout rootCards = new CardLayout();
    private final JPanel root = new JPanel(rootCards);
    private final GradientPanel shell = new GradientPanel(BG1, BG2, true);
    private final ToastPane toast = new ToastPane();
    private Account user;
    private String page = "home", sdoc = DOCTORS[0].name, query = "";
    private YearMonth calMonth = YearMonth.now();
    private JPanel patientTable;            // patients table, refreshed while typing in the search box
    private JComboBox<String> bookDoc, bookDate, bookTime;

    // auth screen widgets
    private String loginRole = "patient";
    private final JTextField loginUser = field(""), regName = field(""), regContact = field(""), regUser = field(""), fUser = field(""), fContact = field("");
    private final JPasswordField loginPass = pwField(), regPass = pwField(), regPass2 = pwField(), fPass = pwField(), fPass2 = pwField();
    private final JLabel loginErr = errLabel(), regErr = errLabel(), fErr = errLabel(), demoHint = label("", Font.PLAIN, 11, MUTED);
    private final CardLayout authCards = new CardLayout();
    private final JPanel authViews = new JPanel(authCards);
    private RoundButton roleP, roleA;
    private JButton regLink;

    static JLabel errLabel() { return label(" ", Font.PLAIN, 12, RED); }

    public MediQueue() {
        super("MediQueue - Clinic Appointment & Queue System");
        shell.setLayout(new BorderLayout());
        root.add(buildAuth(), "auth");
        root.add(shell, "app");
        setContentPane(root);
        setGlassPane(toast); toast.setVisible(true);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1240, 800); setMinimumSize(new Dimension(1000, 680)); setLocationRelativeTo(null);
        rootCards.show(root, "auth");
    }

    // =====================================================================
    //  LOGIN / REGISTER / FORGOT PASSWORD
    // =====================================================================
    private JComponent buildAuth() {
        GradientPanel bg = new GradientPanel(NAVY1, new Color(0x0EA5A4), true);
        bg.setLayout(new GridBagLayout());
        RoundedPanel card = new RoundedPanel(20, Color.WHITE, null);
        card.setLayout(new BorderLayout(0, 8)); card.setBorder(pad(26, 28, 22, 28));
        JLabel logo = new JLabel(new Ico("plus", Color.WHITE, 30)) {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create(); aa(g);
                g.setPaint(new GradientPaint(0, 0, new Color(0x2DD4BF), getWidth(), getHeight(), new Color(0x0EA5E9)));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18); g.dispose(); super.paintComponent(g0);
            }
        };
        logo.setHorizontalAlignment(SwingConstants.CENTER); logo.setPreferredSize(new Dimension(54, 54));
        JPanel logoWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0)); logoWrap.setOpaque(false); logoWrap.add(logo);
        JLabel t = label("MediQueue", Font.BOLD, 24, TEXT); t.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel s = label("Clinic Appointment & Queue System", Font.PLAIN, 12, MUTED); s.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(stack(4, logoWrap, t, s), BorderLayout.NORTH);

        authViews.setOpaque(false);
        authViews.add(buildLoginView(), "login"); authViews.add(buildRegisterView(), "register"); authViews.add(buildForgotView(), "forgot");
        card.add(authViews, BorderLayout.CENTER);
        card.setPreferredSize(new Dimension(410, 580));
        bg.add(card);
        return bg;
    }

    private JButton link(String text, Runnable r) {
        JButton b = new JButton(text); b.setFont(font(Font.BOLD, 12)); b.setForeground(BLUE);
        b.setBorderPainted(false); b.setContentAreaFilled(false); b.setFocusPainted(false); b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> r.run()); return b;
    }
    private void showAuth(String view) { loginErr.setText(" "); regErr.setText(" "); fErr.setText(" "); authCards.show(authViews, view); }

    private JComponent buildLoginView() {
        roleP = btn("Patient", BLUE, Color.WHITE, () -> setLoginRole("patient"));
        roleA = btn("Admin", new Color(0xF1F5F9), MUTED, () -> setLoginRole("admin"));
        JPanel roles = new JPanel(new GridLayout(1, 2, 6, 0)); roles.setOpaque(false); roles.add(roleP); roles.add(roleA);
        regLink = link("New patient? Register", () -> showAuth("register"));
        JPanel links = new JPanel(new BorderLayout()); links.setOpaque(false);
        links.add(link("Forgot password?", () -> showAuth("forgot")), BorderLayout.WEST); links.add(regLink, BorderLayout.EAST);
        loginUser.addActionListener(e -> doLogin()); loginPass.addActionListener(e -> doLogin());
        JPanel p = stack(12, roles, labeled("Username", loginUser), labeled("Password", loginPass), loginErr,
                btn("Sign In", BLUE, Color.WHITE, this::doLogin), links, demoHint);
        demoHint.setHorizontalAlignment(SwingConstants.CENTER);
        setLoginRole("patient");
        return top(p);
    }
    private void setLoginRole(String r) {
        loginRole = r; loginErr.setText(" ");
        roleP.setColors(r.equals("patient") ? BLUE : new Color(0xF1F5F9), r.equals("patient") ? Color.WHITE : MUTED);
        roleA.setColors(r.equals("admin") ? BLUE : new Color(0xF1F5F9), r.equals("admin") ? Color.WHITE : MUTED);
        regLink.setVisible(r.equals("patient"));
        demoHint.setText(r.equals("admin") ? "Authorized clinic staff only." : "Sign in with your registered patient account.");
    }
    private void doLogin() {
        Account a = ACCOUNTS.get(loginUser.getText().trim().toLowerCase());
        String pw = new String(loginPass.getPassword());
        boolean ok = a != null && a.role.equals(loginRole) && sameHash(a.hash, hash(pw, a.salt));
        if (!ok) { loginErr.setText("Invalid username or password."); loginPass.setText(""); return; }
        loginUser.setText(""); loginPass.setText(""); loginErr.setText(" ");
        startSession(a);
    }

    private JComponent buildRegisterView() {
        regPass2.addActionListener(e -> doRegister());
        JPanel p = stack(9, label("Create a patient account", Font.BOLD, 15, TEXT), labeled("Full name", regName), labeled("Contact number", regContact),
                labeled("Username", regUser), labeled("Password (6+ characters)", regPass), labeled("Confirm password", regPass2), regErr,
                btn("Create Account", BLUE, Color.WHITE, this::doRegister), flow(0, link("< Back to sign in", () -> showAuth("login"))));
        return top(p);
    }
    private void doRegister() {
        String name = clean(regName.getText(), 60), contact = clean(regContact.getText(), 20), user = regUser.getText().trim();
        String pw = new String(regPass.getPassword()), pw2 = new String(regPass2.getPassword());
        String problem = null;
        if (name.length() < 2) problem = "Please enter your full name.";
        else if (!contact.matches("[0-9+\\-\\s]{7,15}")) problem = "Enter a valid contact number (7-15 digits).";
        else if (!user.matches("[A-Za-z0-9_.]{3,20}")) problem = "Username: 3-20 letters, numbers, . or _";
        else if (pw.length() < 6) problem = "Password must be at least 6 characters.";
        else if (!pw.equals(pw2)) problem = "Passwords do not match.";
        else if (ACCOUNTS.containsKey(user.toLowerCase())) problem = "That username is already taken.";
        else for (Account x : ACCOUNTS.values()) if (x.name.equalsIgnoreCase(name)) problem = "A patient with that name is already registered.";
        if (problem != null) { regErr.setText(problem); return; }
        Account a = makeAccount(user, "patient", name, contact, pw);
        ACCOUNTS.put(user.toLowerCase(), a); saveAccounts();
        for (JTextField f : new JTextField[]{regName, regContact, regUser, regPass, regPass2}) f.setText("");
        regErr.setText(" ");
        startSession(a);
    }

    private JComponent buildForgotView() {
        fPass2.addActionListener(e -> doReset());
        JPanel p = stack(9, label("Reset your password", Font.BOLD, 15, TEXT),
                label("<html>Enter your username and the contact number<br>you registered with.</html>", Font.PLAIN, 12, MUTED),
                labeled("Username", fUser), labeled("Registered contact number", fContact), labeled("New password (6+ characters)", fPass),
                labeled("Confirm new password", fPass2), fErr, btn("Reset Password", BLUE, Color.WHITE, this::doReset),
                flow(0, link("< Back to sign in", () -> showAuth("login"))));
        return top(p);
    }
    private void doReset() {
        Account a = ACCOUNTS.get(fUser.getText().trim().toLowerCase());
        String digits = fContact.getText().replaceAll("[^0-9+]", ""), pw = new String(fPass.getPassword());
        if (a == null || !a.role.equals("patient") || a.contact.isEmpty() || !a.contact.replaceAll("[^0-9+]", "").equals(digits)) {
            fErr.setText("We could not verify that username and number."); return;
        }
        if (pw.length() < 6) { fErr.setText("Password must be at least 6 characters."); return; }
        if (!pw.equals(new String(fPass2.getPassword()))) { fErr.setText("Passwords do not match."); return; }
        ACCOUNTS.put(a.username.toLowerCase(), makeAccount(a.username, a.role, a.name, a.contact, pw)); saveAccounts();
        loginUser.setText(a.username);
        for (JTextField f : new JTextField[]{fUser, fContact, fPass, fPass2}) f.setText("");
        setLoginRole("patient"); showAuth("login");
        toast.show("Password updated. You can now sign in.", GREEN);
    }

    private void startSession(Account a) {
        user = a; page = "home"; query = ""; calMonth = YearMonth.now(); sdoc = DOCTORS[0].name;
        if (a.role.equals("patient") && !hasPatient(a.name)) {
            PATS.add(pat(nextPatId(), a.name, "", "", a.contact, "-")); saveData();
        }
        render(); rootCards.show(root, "app");
        toast.show("Welcome, " + a.name.split(" ")[0] + "!", GREEN);
    }
    private static boolean hasPatient(String name) { for (Pat p : PATS) if (p.name.equals(name)) return true; return false; }
    private void logout() {
        user = null; showAuth("login"); setLoginRole("patient"); rootCards.show(root, "auth");
        toast.show("You have been logged out.", GREEN);
    }

    // =====================================================================
    //  APP SHELL: sidebar + header + page body
    // =====================================================================
    private boolean admin() { return user != null && user.role.equals("admin"); }
    private String me() { return user.name; }

    private static final String[][] NAV_PATIENT = {{"home", "Home", "home"}, {"book", "Book Appointment", "cal"}, {"appointments", "My Appointments", "list"}, {"queue", "Queue Status", "users"}, {"profile", "Profile", "user"}};
    private static final String[][] NAV_ADMIN = {{"home", "Dashboard", "home"}, {"appointments", "Appointments", "cal"}, {"queue", "Queue", "list"}, {"patients", "Patients", "users"}, {"doctors", "Doctors", "user"}, {"schedules", "Schedules", "clock"}, {"reports", "Reports", "chart"}};

    void navigate(String p) { page = p; render(); }

    void render() {
        shell.removeAll();
        shell.add(buildSidebar(), BorderLayout.WEST);
        JPanel main = new JPanel(new BorderLayout()); main.setOpaque(false);
        main.add(buildHeader(), BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane();
        sp.setBorder(null); sp.setOpaque(false); sp.getViewport().setOpaque(false);
        sp.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE); sp.getVerticalScrollBar().setUnitIncrement(18);
        PageBox box = new PageBox(); box.setBorder(pad(0, 26, 20, 26)); box.add(top(buildBody()));
        sp.setViewportView(box);
        main.add(sp, BorderLayout.CENTER);
        shell.add(main, BorderLayout.CENTER);
        shell.revalidate(); shell.repaint();
    }

    private JComponent buildSidebar() {
        GradientPanel side = new GradientPanel(NAVY1, NAVY2, false);
        side.setLayout(new BorderLayout()); side.setPreferredSize(new Dimension(220, 100)); side.setBorder(pad(18, 12, 16, 12));
        JPanel logo = new JPanel(new BorderLayout(10, 0)); logo.setOpaque(false); logo.setBorder(pad(0, 6, 20, 0));
        JLabel mark = new JLabel(new Ico("plus", Color.WHITE, 22)) {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create(); aa(g);
                g.setPaint(new GradientPaint(0, 0, new Color(0x2DD4BF), getWidth(), getHeight(), new Color(0x0EA5E9)));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12); g.dispose(); super.paintComponent(g0);
            }
        };
        mark.setHorizontalAlignment(SwingConstants.CENTER); mark.setPreferredSize(new Dimension(38, 38));
        logo.add(mark, BorderLayout.WEST);
        logo.add(stack(0, label("MediQueue", Font.BOLD, 18, Color.WHITE),
                label("<html>Clinic Appointment &amp;<br>Queue System</html>", Font.PLAIN, 9, new Color(0x9FB6E6))), BorderLayout.CENTER);
        side.add(logo, BorderLayout.NORTH);

        List<JComponent> items = new ArrayList<>();
        for (String[] n : admin() ? NAV_ADMIN : NAV_PATIENT) {
            final String key = n[0];
            NavButton b = new NavButton(n[1], n[2]).selected(key.equals(page));
            b.addActionListener(e -> navigate(key)); items.add(b);
        }
        side.add(top(stack(4, items)), BorderLayout.CENTER);

        JPanel foot = new JPanel(new BorderLayout(10, 0)); foot.setOpaque(false); foot.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(255, 255, 255, 40)), pad(12, 6, 0, 0)));
        foot.add(new Avatar(me(), 34), BorderLayout.WEST);
        foot.add(stack(0, label(me().split(" ")[0], Font.BOLD, 13, Color.WHITE), label(admin() ? "Clinic Staff" : "Patient", Font.PLAIN, 11, new Color(0x9FB6E6))), BorderLayout.CENTER);
        JButton out = new JButton(new Ico("out", new Color(0xCBD8F5), 20)); out.setToolTipText("Logout");
        out.setBorderPainted(false); out.setContentAreaFilled(false); out.setFocusPainted(false); out.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        out.addActionListener(e -> logout()); foot.add(out, BorderLayout.EAST);
        side.add(foot, BorderLayout.SOUTH);
        return side;
    }

    private String pageLabel() {
        for (String[] n : admin() ? NAV_ADMIN : NAV_PATIENT) if (n[0].equals(page)) return n[1];
        return "";
    }

    private JComponent buildHeader() {
        JPanel h = new JPanel(new BorderLayout(14, 0)); h.setOpaque(false); h.setBorder(pad(20, 26, 16, 26));
        String title, sub;
        if (admin()) { title = page.equals("home") ? "Admin Dashboard" : pageLabel(); sub = "Manage appointments, queues, patients, and doctor schedules."; }
        else if (page.equals("home")) {
            int hr = LocalTime.now().getHour();
            title = (hr < 12 ? "Good morning" : hr < 18 ? "Good afternoon" : "Good evening") + ", " + me().split(" ")[0] + "!";
            sub = "Take care of your health. We're here for you.";
        } else { title = pageLabel(); sub = "MediQueue patient portal"; }
        h.add(stack(3, label(title, Font.BOLD, 22, TEXT), label(sub, Font.PLAIN, 13, MUTED)), BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0)); right.setOpaque(false);
        if (admin()) {
            JLabel chip = label("  " + LocalDate.now().format(DATE_FMT) + "  ", Font.PLAIN, 12, MUTED);
            chip.setIcon(new Ico("cal", MUTED, 16)); chip.setBorder(BorderFactory.createCompoundBorder(new LineBorder(LINE, 1, true), pad(7, 8, 7, 8)));
            chip.setOpaque(true); chip.setBackground(Color.WHITE); right.add(chip);
        } else right.add(new JLabel(new Ico("bell", MUTED, 22)));
        right.add(new Avatar(me(), 38));
        right.add(stack(0, label(me(), Font.BOLD, 13, TEXT), label(admin() ? "Clinic Staff" : "Patient", Font.PLAIN, 11, MUTED)));
        h.add(right, BorderLayout.EAST);
        return h;
    }

    private JComponent buildBody() {
        if (!admin()) {
            switch (page) {
                case "home": return patientHome();
                case "book": return narrow(wBook(), 560);
                case "appointments": return narrow(wMyAppts(), 820);
                case "queue": return narrow(wQueue(), 700);
                default: return narrow(wProfile(), 520);
            }
        }
        switch (page) {
            case "home": return stack(16, adminStats(), grid(16, 340, wToday(), wQueueOverview(), wQuick()), grid(16, 340, wDocs(), wRecent(), wSummary()));
            case "appointments": return wAppMgmt();
            case "queue": return stack(16, counters(), wQueueMgmt());
            case "patients": return wPatients();
            case "doctors": return wDocs();
            case "schedules": return wSchedMgmt();
            default: return stack(16, adminStats(), narrow(wSummary(), 520));
        }
    }

    // =====================================================================
    //  PATIENT PAGES
    // =====================================================================
    private List<Appt> mine() { List<Appt> l = new ArrayList<>(); for (Appt a : sortedAppts(APPTS)) if (a.patient.equals(me())) l.add(a); return l; }
    private int myQueueNo() { for (int i = 0; i < QUEUE.size(); i++) if (QUEUE.get(i).name.equals(me())) return i + 1; return 0; }

    JComponent stat(Color color, String icon, String label, String value, String sub, boolean chev) {
        RoundedPanel c = new RoundedPanel(16, Color.WHITE, LINE); c.setLayout(new BorderLayout(14, 0)); c.setBorder(pad(14, 16, 14, 16));
        RoundedPanel ico = new RoundedPanel(16, color, null); ico.setLayout(new GridBagLayout()); ico.setPreferredSize(new Dimension(52, 52));
        ico.add(new JLabel(new Ico(icon, Color.WHITE, 24)));
        c.add(ico, BorderLayout.WEST);
        c.add(stack(1, label(label, Font.PLAIN, 12, MUTED), label(value, Font.BOLD, 22, TEXT), label(sub, Font.PLAIN, 11, new Color(0x16A34A))), BorderLayout.CENTER);
        if (chev) c.add(new JLabel(new Ico("next", new Color(0x94A3B8), 18)), BorderLayout.EAST);
        return c;
    }

    private JComponent patientHome() {
        Appt up = null;
        for (Appt a : mine()) if (live(a) && !a.date.isBefore(LocalDate.now())) { up = a; break; }
        int qn = myQueueNo();
        JComponent stats = grid(16, 250, stat(new Color(0x3B82F6), "cal", "Upcoming Appointment", up == null ? "None" : up.date.format(DATE_FMT), up == null ? "Book one today" : up.doc, true),
                stat(GREEN, "users", "Current Queue", "# " + (qn == 0 ? "-" : String.valueOf(qn)), "OPD - General Medicine", true),
                stat(PURPLE, "card", "Reservation Fee", "\u20B1100.00", "(per appointment)", true));
        return stack(16, stats, grid(16, 340, wMyAppts(), wQueue(), wSchedule()));
    }

    private JComponent apptItem(Appt a) {
        RoundedPanel item = new RoundedPanel(12, Color.WHITE, LINE); item.setLayout(new BorderLayout(12, 0)); item.setBorder(pad(10, 10, 10, 10));
        RoundedPanel date = new RoundedPanel(10, new Color(0xEAF1FF), null); date.setLayout(new GridLayout(2, 1)); date.setBorder(pad(5, 10, 5, 10));
        JLabel mo = label(a.date.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(), Font.BOLD, 10, BLUE);
        JLabel dy = label(String.format("%02d", a.date.getDayOfMonth()), Font.BOLD, 17, BLUE);
        mo.setHorizontalAlignment(SwingConstants.CENTER); dy.setHorizontalAlignment(SwingConstants.CENTER); date.add(mo); date.add(dy);
        item.add(date, BorderLayout.WEST);
        item.add(stack(1, label(a.doc, Font.BOLD, 13, TEXT), label(spec(a.doc), Font.PLAIN, 12, MUTED), label(a.time.format(TIME_FMT), Font.PLAIN, 12, MUTED)), BorderLayout.CENTER);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6)); right.setOpaque(false); right.add(new Badge(a.status));
        if (live(a)) right.add(btn("Cancel", new Color(0xEF4444), Color.WHITE, () -> cancelDialog(a)).mini());
        item.add(right, BorderLayout.EAST);
        return item;
    }

    private JComponent wMyAppts() {
        List<JComponent> items = new ArrayList<>();
        for (Appt a : mine()) items.add(apptItem(a));
        if (items.isEmpty()) items.add(label("No appointments yet.", Font.PLAIN, 13, MUTED));
        return card("My Appointments", page.equals("appointments") ? null : "appointments", null, stack(8, items));
    }

    private JComponent wQueue() {
        int qn = myQueueNo();
        RoundedPanel box = new RoundedPanel(12, new Color(0xE9FAF1), new Color(0xC7EFD9)); box.setLayout(new BorderLayout(14, 0)); box.setBorder(pad(10, 12, 10, 12));
        RoundedPanel ico = new RoundedPanel(14, GREEN, null); ico.setLayout(new GridBagLayout()); ico.setPreferredSize(new Dimension(46, 46)); ico.add(new JLabel(new Ico("users", Color.WHITE, 22)));
        box.add(ico, BorderLayout.WEST);
        box.add(stack(0, label("Your Queue Number", Font.PLAIN, 11, new Color(0x15803D)), label("# " + (qn == 0 ? "-" : String.valueOf(qn)), Font.BOLD, 26, new Color(0x15803D)),
                label("OPD - General Medicine", Font.PLAIN, 11, MUTED)), BorderLayout.CENTER);
        List<Object[]> r = new ArrayList<>();
        for (int i = 0; i < QUEUE.size(); i++) r.add(new Object[]{String.valueOf(i + 1), QUEUE.get(i).name, new Badge(QUEUE.get(i).status)});
        return card("Queue Status", null, "Last updated: " + LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)),
                stack(12, box, table(new String[]{"No.", "Patient Name", "Status"}, r, qn - 1, 0.2, 1.5, 1)));
    }

    private JComponent wSchedule() {
        JPanel head = new JPanel(new BorderLayout()); head.setOpaque(false);
        head.add(btn("<", new Color(0xF1F5F9), TEXT, () -> { calMonth = calMonth.minusMonths(1); render(); }).mini(), BorderLayout.WEST);
        JLabel t = label(calMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + calMonth.getYear(), Font.BOLD, 13, TEXT); t.setHorizontalAlignment(SwingConstants.CENTER);
        head.add(t, BorderLayout.CENTER);
        head.add(btn(">", new Color(0xF1F5F9), TEXT, () -> { calMonth = calMonth.plusMonths(1); render(); }).mini(), BorderLayout.EAST);
        Set<LocalDate> marks = new HashSet<>(); LocalDate next = null;
        for (Appt a : mine()) if (live(a)) { marks.add(a.date); if (next == null && !a.date.isBefore(LocalDate.now())) next = a.date; }
        final LocalDate sel = next;
        JPanel grid = new JPanel(new GridLayout(0, 7, 3, 3)); grid.setOpaque(false);
        for (String d : new String[]{"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"}) { JLabel l = label(d, Font.BOLD, 10, MUTED); l.setHorizontalAlignment(SwingConstants.CENTER); grid.add(l); }
        for (int i = 0; i < calMonth.atDay(1).getDayOfWeek().getValue() % 7; i++) grid.add(new JLabel());
        for (int d = 1; d <= calMonth.lengthOfMonth(); d++) {
            final LocalDate date = calMonth.atDay(d);
            JLabel l = new JLabel(String.valueOf(d), SwingConstants.CENTER) {
                @Override protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create(); aa(g);
                    if (date.equals(sel)) { g.setColor(BLUE); g.fillOval(0, 0, getWidth(), getHeight()); setForeground(Color.WHITE); }
                    else if (marks.contains(date)) { g.setColor(new Color(0xE0ECFF)); g.fillOval(0, 0, getWidth(), getHeight()); setForeground(BLUE); }
                    else setForeground(TEXT);
                    g.dispose(); super.paintComponent(g0);
                }
            };
            l.setFont(font(Font.PLAIN, 12)); l.setBorder(pad(6, 0, 6, 0)); grid.add(l);
        }
        JPanel actions = stack(8, btn("Book Appointment", BLUE, Color.WHITE, () -> navigate("book")), btn("View Queue", new Color(0xEAF1FF), BLUE, () -> navigate("queue")));
        return card("My Schedule", null, null, stack(12, head, grid, label("Quick Actions", Font.BOLD, 13, TEXT), actions));
    }

    private JComponent wBook() {
        List<String> docs = new ArrayList<>(); for (Doctor d : DOCTORS) docs.add(d.name + " - " + d.spec);
        bookDoc = combo(docs.toArray(new String[0]));
        List<String> days = new ArrayList<>();
        for (int i = 0; i < 60; i++) { LocalDate d = LocalDate.now().plusDays(i); if (d.getDayOfWeek() != DayOfWeek.SUNDAY) days.add(d.format(DAY_FMT)); }
        bookDate = combo(days.toArray(new String[0]));
        List<String> times = new ArrayList<>(); for (LocalTime t : allSlots()) times.add(t.format(TIME_FMT));
        bookTime = combo(times.toArray(new String[0]));
        JPanel f = form(labeled("Doctor", bookDoc), labeled("Date (clinic is closed on Sundays)", bookDate), labeled("Time slot", bookTime),
                btn("Book Appointment", BLUE, Color.WHITE, this::book));
        return card("Book Appointment", null, null, f);
    }

    private void book() {
        Doctor d = DOCTORS[bookDoc.getSelectedIndex()];
        LocalDate date = LocalDate.now(); int idx = bookDate.getSelectedIndex(), seen = -1;
        for (int i = 0; i < 60; i++) { LocalDate x = LocalDate.now().plusDays(i); if (x.getDayOfWeek() != DayOfWeek.SUNDAY && ++seen == idx) { date = x; break; } }
        LocalTime time = allSlots().get(bookTime.getSelectedIndex());
        if (date.equals(LocalDate.now()) && !time.isAfter(LocalTime.now())) { toast.show("That time has already passed today.", RED); return; }
        for (Appt a : APPTS) if (a.doc.equals(d.name) && a.date.equals(date) && a.time.equals(time) && !a.status.equals("Cancelled")) { toast.show("That slot is already booked. Pick another time.", RED); return; }
        for (Sched s : SCHEDS) if (s.doc.equals(d.name) && s.date.equals(date) && !s.ok) { toast.show(d.name + " is not available on that date.", RED); return; }
        APPTS.add(appt(me(), d.name, date, time.toString(), "Scheduled")); saveData();
        navigate("appointments");
        toast.show("Booked with " + d.name + " on " + date.format(DATE_FMT) + " at " + time.format(TIME_FMT), GREEN);
    }

    private JComponent wProfile() {
        Pat p = null; for (Pat x : PATS) if (x.name.equals(me())) p = x;
        List<JComponent> rows = new ArrayList<>();
        String[][] kv = {{"Name", me()}, {"Patient ID", p == null ? "-" : p.id}, {"Contact", p == null || p.contact.isEmpty() ? "-" : p.contact}, {"Reservation fee", "\u20B1100.00 per appointment"}};
        for (String[] r : kv) { JPanel row = new JPanel(new BorderLayout()); row.setOpaque(false); row.setBorder(pad(8, 0, 8, 0)); row.add(label(r[0], Font.PLAIN, 13, MUTED), BorderLayout.WEST); row.add(label(r[1], Font.BOLD, 13, TEXT), BorderLayout.EAST); rows.add(row); }
        return card("Profile", null, null, stack(0, rows));
    }

    // =====================================================================
    //  ADMIN PAGES
    // =====================================================================
    private JComponent adminStats() {
        int cur = 0; for (int i = 0; i < QUEUE.size(); i++) if (QUEUE.get(i).status.equals("In Consultation")) cur = i + 1;
        int duty = 0;
        for (Doctor d : DOCTORS) for (Sched s : SCHEDS) if (s.doc.equals(d.name) && s.date.equals(LocalDate.now()) && s.ok) { duty++; break; }
        return grid(16, 205, stat(new Color(0x3B82F6), "cal", "Total Appointments", String.valueOf(APPTS.size()), "All bookings", false),
                stat(GREEN, "users", "Total Patients", String.valueOf(PATS.size()), "Registered", false),
                stat(PURPLE, "hash", "Current Queue", cur == 0 ? "-" : "# " + cur, "OPD - General Medicine", false),
                stat(new Color(0xF43F5E), "user", "Doctors On Duty", duty + " / " + DOCTORS.length, "Today", false));
    }

    private JComponent wToday() {
        List<Object[]> r = new ArrayList<>();
        for (Appt a : sortedAppts(APPTS)) if (a.date.equals(LocalDate.now())) r.add(new Object[]{a.time.format(TIME_FMT), a.patient, a.doc, new Badge(a.status)});
        return card("Today's Appointments", "appointments", null, table(new String[]{"Time", "Patient", "Doctor", "Status"}, r, -1, 1, 1.4, 1.4, 1.2));
    }
    private JComponent wQueueOverview() {
        List<Object[]> r = new ArrayList<>();
        for (int i = 0; i < QUEUE.size(); i++) r.add(new Object[]{String.format("Q%03d", i + 1), QUEUE.get(i).name, new Badge(QUEUE.get(i).status)});
        return card("Queue Overview", "queue", null, table(new String[]{"Queue No.", "Patient", "Status"}, r, -1, 0.8, 1.4, 1.2));
    }
    private JComponent wQuick() {
        return card("Quick Actions", null, null, stack(8, btn("Manage Appointments", BLUE, Color.WHITE, () -> navigate("appointments")),
                btn("Manage Schedule", new Color(0xEAF1FF), BLUE, () -> navigate("schedules")), btn("Manage Patients", new Color(0xEAF1FF), BLUE, () -> navigate("patients")),
                btn("Manage Queue", new Color(0xEAF1FF), BLUE, () -> navigate("queue"))));
    }
    private JComponent wDocs() {
        List<Object[]> r = new ArrayList<>(); for (Doctor d : DOCTORS) r.add(new Object[]{d.name, d.spec, d.hours});
        return card("Doctor Schedule", page.equals("home") ? "doctors" : null, null, table(new String[]{"Doctor", "Specialization", "Available Today"}, r, -1, 1.3, 1.2, 1.3));
    }
    private JComponent wRecent() {
        List<Object[]> r = new ArrayList<>(); for (int i = 0; i < Math.min(5, PATS.size()); i++) { Pat p = PATS.get(i); r.add(new Object[]{p.name, p.contact, p.visit}); }
        return card("Recent Patients", "patients", null, table(new String[]{"Patient", "Contact No.", "Last Visit"}, r, -1, 1.3, 1.2, 1));
    }
    private JComponent wSummary() {
        int active = 0, cancelled = 0; for (Appt a : APPTS) { if (live(a)) active++; if (a.status.equals("Cancelled")) cancelled++; }
        Object[][] rows = {{"Active Appointments", active, new Color(0x3B82F6)}, {"In Queue", count("Waiting", "Next"), GREEN}, {"Completed Today", count("Completed"), PURPLE}, {"Cancelled", cancelled, new Color(0xF43F5E)}};
        List<JComponent> items = new ArrayList<>();
        for (Object[] r : rows) {
            JPanel row = new JPanel(new BorderLayout(10, 0)); row.setOpaque(false); row.setBorder(pad(9, 0, 9, 0));
            final Color c = (Color) r[2];
            JComponent dot = new JComponent() { protected void paintComponent(Graphics g) { Graphics2D g2 = (Graphics2D) g.create(); aa(g2); g2.setColor(c); g2.fillOval(0, 4, 10, 10); g2.dispose(); } };
            dot.setPreferredSize(new Dimension(12, 18));
            row.add(dot, BorderLayout.WEST); row.add(label((String) r[0], Font.PLAIN, 13, TEXT), BorderLayout.CENTER); row.add(label(String.valueOf(r[1]), Font.BOLD, 13, TEXT), BorderLayout.EAST);
            items.add(row);
        }
        return card("System Summary", null, null, stack(0, items));
    }

    private JComponent wAppMgmt() {
        List<Object[]> r = new ArrayList<>();
        for (Appt a : sortedAppts(APPTS)) {
            JPanel acts = flow(6);
            if (a.status.equals("Scheduled") || a.status.equals("Upcoming")) acts.add(btn("Confirm", GREEN, Color.WHITE, () -> { a.status = "Confirmed"; saveData(); render(); toast.show("Appointment confirmed.", GREEN); }).mini());
            if (live(a)) acts.add(btn("Cancel", new Color(0xEF4444), Color.WHITE, () -> cancelDialog(a)).mini());
            r.add(new Object[]{a.id, a.patient, a.doc, a.date.format(DATE_FMT), a.time.format(TIME_FMT), new Badge(a.status), acts});
        }
        return card("Upcoming Appointments", null, null, table(new String[]{"ID", "Patient Name", "Doctor", "Date", "Time", "Status", "Actions"}, r, -1, .6, 1.4, 1.3, 1, .9, 1.1, 1.4));
    }

    private JComponent counters() {
        String[][] c = {{"Waiting", String.valueOf(count("Waiting", "Next")), "22B573"}, {"In Consultation", String.valueOf(count("In Consultation")), "3B82F6"}, {"Completed", String.valueOf(count("Completed")), "64748B"}};
        JComponent[] boxes = new JComponent[3];
        for (int i = 0; i < 3; i++) {
            RoundedPanel b = new RoundedPanel(14, new Color(Integer.parseInt(c[i][2], 16)), null); b.setLayout(new GridLayout(2, 1)); b.setBorder(pad(14, 14, 14, 14));
            JLabel v = label(c[i][1], Font.BOLD, 28, Color.WHITE), l = label(c[i][0], Font.PLAIN, 12, Color.WHITE);
            v.setHorizontalAlignment(SwingConstants.CENTER); l.setHorizontalAlignment(SwingConstants.CENTER); b.add(v); b.add(l); boxes[i] = b;
        }
        return cols(16, boxes);
    }

    private JComponent wQueueMgmt() {
        List<Object[]> r = new ArrayList<>();
        for (int i = 0; i < QUEUE.size(); i++) {
            final QEntry q = QUEUE.get(i); JPanel acts = flow(6);
            if (q.status.equals("In Consultation")) acts.add(btn("Complete", GREEN, Color.WHITE, () -> { q.status = "Completed"; saveData(); render(); }).mini());
            else if (!q.status.equals("Completed")) acts.add(btn("Call", BLUE, Color.WHITE, () -> { q.status = "In Consultation"; saveData(); render(); }).mini());
            r.add(new Object[]{String.format("Q%03d", i + 1), q.name, q.doc, new Badge(q.status), acts});
        }
        JPanel bar = flow(0, btn("Call Next Patient", BLUE, Color.WHITE, () -> {
            for (QEntry q : QUEUE) if (q.status.equals("Next") || q.status.equals("Waiting")) {
                q.status = "In Consultation"; saveData(); render(); toast.show("Now calling " + q.name, GREEN); return;
            }
            toast.show("No patients are waiting.", RED);
        }));
        return card("Patient Queue", null, null, stack(12, bar, table(new String[]{"Queue No.", "Patient Name", "Doctor", "Status", "Action"}, r, -1, .8, 1.5, 1.4, 1.2, 1)));
    }

    private JComponent wPatients() {
        JTextField search = field(query); search.setPreferredSize(new Dimension(300, 38));
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void upd() { query = search.getText(); fillPatientTable(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { upd(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { upd(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { upd(); }
        });
        patientTable = new JPanel(new BorderLayout()); patientTable.setOpaque(false);
        fillPatientTable();
        JPanel bar = new JPanel(new BorderLayout(10, 0)); bar.setOpaque(false);
        bar.add(search, BorderLayout.WEST); bar.add(flow(0, btn("Add Patient", BLUE, Color.WHITE, () -> patientDialog(null))), BorderLayout.EAST);
        return card("Patient Information & Database", null, null, stack(12, bar, patientTable));
    }
    private void fillPatientTable() {
        List<Object[]> r = new ArrayList<>(); String q = query.trim().toLowerCase();
        for (Pat p : PATS) {
            if (!q.isEmpty() && !(p.name + " " + p.id).toLowerCase().contains(q)) continue;
            JPanel acts = flow(6, btn("Edit", BLUE, Color.WHITE, () -> patientDialog(p)).mini(), btn("Delete", new Color(0xEF4444), Color.WHITE, () -> {
                if (ask("Delete Patient", "Delete the record of " + p.name + "?")) { PATS.remove(p); saveData(); render(); toast.show("Patient deleted.", RED); }
            }).mini());
            r.add(new Object[]{p.id, p.name, p.age.isEmpty() ? "-" : p.age, p.gender.isEmpty() ? "-" : p.gender, p.contact, acts});
        }
        patientTable.removeAll(); patientTable.add(table(new String[]{"Patient ID", "Name", "Age", "Gender", "Contact", "Actions"}, r, -1, .7, 1.5, .5, .8, 1.2, 1.2));
        patientTable.revalidate(); patientTable.repaint();
    }

    private JComponent wSchedMgmt() {
        String[] names = new String[DOCTORS.length]; for (int i = 0; i < names.length; i++) names[i] = DOCTORS[i].name;
        JComboBox<String> sel = combo(names); sel.setSelectedItem(sdoc);
        JPanel bar = flow(10, sel, btn("Load Schedule", BLUE, Color.WHITE, () -> { sdoc = (String) sel.getSelectedItem(); render(); }),
                btn("Add Schedule", new Color(0xEAF1FF), BLUE, () -> { sdoc = (String) sel.getSelectedItem(); scheduleDialog(); }));
        List<Sched> mine = new ArrayList<>(); for (Sched s : SCHEDS) if (s.doc.equals(sdoc)) mine.add(s);
        Collections.sort(mine, new Comparator<Sched>() { public int compare(Sched a, Sched b) { return a.date.compareTo(b.date); } });
        List<Object[]> r = new ArrayList<>();
        for (Sched s : mine) {
            JPanel acts = flow(6, btn(s.ok ? "Mark Off" : "Mark On", BLUE, Color.WHITE, () -> { s.ok = !s.ok; saveData(); render(); }).mini(),
                    btn("Remove", new Color(0xEF4444), Color.WHITE, () -> { SCHEDS.remove(s); saveData(); render(); }).mini());
            r.add(new Object[]{s.date.format(DATE_FMT), s.date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH), s.slots, new Badge(s.ok ? "Available" : "Not Available"), acts});
        }
        return card("Doctor Schedule Management", null, null, stack(12, bar, table(new String[]{"Date", "Day", "Available Time Slots", "Status", "Actions"}, r, -1, 1, 1, 1.4, 1, 1.5)));
    }

    // =====================================================================
    //  DIALOGS
    // =====================================================================
    private JDialog dialog(String title, JComponent content, int width, int height, JComponent south) {
        JDialog d = new JDialog(this, title, true);
        JPanel p = new JPanel(new BorderLayout()); p.setBackground(Color.WHITE); p.setBorder(pad(20, 22, 20, 22));
        p.add(content, BorderLayout.CENTER);
        if (south != null) { JPanel s = new JPanel(new BorderLayout()); s.setOpaque(false); s.setBorder(pad(14, 0, 0, 0)); s.add(south); p.add(s, BorderLayout.SOUTH); }
        d.setContentPane(p); d.setSize(width, height); d.setResizable(false); d.setLocationRelativeTo(this);
        return d;
    }
    private static JPanel buttonRow(JComponent a, JComponent b) {
        JPanel acts = new JPanel(new GridLayout(1, 2, 10, 0)); acts.setOpaque(false); acts.add(a); acts.add(b); return acts;
    }

    private boolean ask(String title, String message) {
        final boolean[] yes = {false};
        final JDialog[] d = new JDialog[1];
        JPanel buttons = buttonRow(btn("No", new Color(0xE2E8F0), TEXT, () -> d[0].dispose()), btn("Yes", RED, Color.WHITE, () -> { yes[0] = true; d[0].dispose(); }));
        d[0] = dialog(title, stack(10, label(title, Font.BOLD, 16, TEXT), label(message, Font.PLAIN, 13, MUTED)), 380, 200, buttons);
        d[0].setVisible(true);
        return yes[0];
    }

    /** Cancel Appointment: details + warning, then a Confirm Cancellation question. */
    private void cancelDialog(Appt a) {
        String[][] kv = {{"Appointment ID", a.id}, {"Patient Name", a.patient}, {"Doctor", a.doc}, {"Date", a.date.format(DATE_FMT)}, {"Time", a.time.format(TIME_FMT)}};
        List<JComponent> rows = new ArrayList<>();
        rows.add(label("Cancel Appointment", Font.BOLD, 18, TEXT));
        rows.add(label("Appointment Details", Font.BOLD, 13, MUTED));
        for (String[] r : kv) { JPanel row = new JPanel(new BorderLayout()); row.setOpaque(false); row.setBorder(pad(6, 0, 6, 0)); row.add(label(r[0], Font.PLAIN, 13, MUTED), BorderLayout.WEST); row.add(label(r[1], Font.BOLD, 13, TEXT), BorderLayout.EAST); rows.add(row); }
        JPanel st = new JPanel(new BorderLayout()); st.setOpaque(false); st.add(label("Status", Font.PLAIN, 13, MUTED), BorderLayout.WEST); st.add(flow(0, new Badge(a.status)), BorderLayout.EAST); rows.add(st);
        RoundedPanel warn = new RoundedPanel(10, new Color(0xFEF2F2), null); warn.setLayout(new BorderLayout()); warn.setBorder(pad(10, 12, 10, 12));
        warn.add(label("<html>Are you sure you want to cancel this appointment?<br>This action cannot be undone.</html>", Font.PLAIN, 12, RED)); rows.add(warn);
        final JDialog[] d = new JDialog[1];
        JPanel buttons = buttonRow(btn("Cancel Appointment", RED, Color.WHITE, () -> {
            if (ask("Confirm Cancellation", "Do you want to cancel this appointment?")) {
                a.status = "Cancelled"; saveData(); d[0].dispose(); render(); toast.show("Appointment " + a.id + " has been cancelled.", RED);
            }
        }), btn("Back", new Color(0xE2E8F0), TEXT, () -> d[0].dispose()));
        d[0] = dialog("Cancel Appointment", stack(8, rows), 440, 480, buttons);
        d[0].setVisible(true);
    }

    private void patientDialog(Pat p) {
        JTextField name = field(p == null ? "" : p.name), age = field(p == null ? "" : p.age), contact = field(p == null ? "" : p.contact);
        JComboBox<String> gender = combo(new String[]{"Female", "Male", "Other"}); if (p != null && !p.gender.isEmpty()) gender.setSelectedItem(p.gender);
        JLabel err = errLabel();
        JPanel f = form(label(p == null ? "Add Patient" : "Edit Patient", Font.BOLD, 18, TEXT), labeled("Full name", name), labeled("Age", age), labeled("Gender", gender), labeled("Contact number", contact), err);
        final JDialog[] d = new JDialog[1];
        JPanel buttons = buttonRow(btn("Save", BLUE, Color.WHITE, () -> {
            String n = clean(name.getText(), 60), c = clean(contact.getText(), 20), a = age.getText().trim();
            if (n.isEmpty()) { err.setText("Please enter the patient's name."); return; }
            if (!a.matches("\\d{1,3}") || Integer.parseInt(a) > 120) { err.setText("Please enter a valid age (0-120)."); return; }
            if (!c.matches("[0-9+\\-\\s]{7,15}")) { err.setText("Please enter a valid contact number."); return; }
            Pat t = p == null ? pat(nextPatId(), n, a, "", c, "-") : p;
            t.name = n; t.age = a; t.contact = c; t.gender = (String) gender.getSelectedItem();
            if (p == null) PATS.add(t);
            saveData(); d[0].dispose(); render(); toast.show("Patient saved.", GREEN);
        }), btn("Cancel", new Color(0xE2E8F0), TEXT, () -> d[0].dispose()));
        d[0] = dialog("Patient", f, 400, 540, buttons);
        d[0].setVisible(true);
    }

    private void scheduleDialog() {
        List<String> days = new ArrayList<>(); List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < 60; i++) { LocalDate d = LocalDate.now().plusDays(i); days.add(d.format(DAY_FMT)); dates.add(d); }
        JComboBox<String> date = combo(days.toArray(new String[0]));
        JComboBox<String> slots = combo(new String[]{"9:00 AM - 12:00 PM", "9:00 AM - 3:00 PM", "10:00 AM - 2:00 PM", "1:00 PM - 5:00 PM"});
        JTextField doc = field(sdoc); doc.setEditable(false);
        JPanel f = form(label("Add Schedule", Font.BOLD, 18, TEXT), labeled("Doctor", doc), labeled("Date", date), labeled("Available time", slots));
        final JDialog[] d = new JDialog[1];
        JPanel buttons = buttonRow(btn("Add Schedule", BLUE, Color.WHITE, () -> {
            SCHEDS.add(sched(sdoc, dates.get(date.getSelectedIndex()), (String) slots.getSelectedItem(), true));
            saveData(); d[0].dispose(); render(); toast.show("Schedule added.", GREEN);
        }), btn("Cancel", new Color(0xE2E8F0), TEXT, () -> d[0].dispose()));
        d[0] = dialog("Add Schedule", f, 400, 420, buttons);
        d[0].setVisible(true);
    }

    // =====================================================================
    //  MAIN
    // =====================================================================
    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) { }
        for (Object key : Collections.list(UIManager.getDefaults().keys())) {
            if (UIManager.get(key) instanceof javax.swing.plaf.FontUIResource) UIManager.put(key, new javax.swing.plaf.FontUIResource(font(Font.PLAIN, 13)));
        }
        MediQueueData.loadAccounts();
        MediQueueData.loadData();
        SwingUtilities.invokeLater(() -> new MediQueue().setVisible(true));
    }
}