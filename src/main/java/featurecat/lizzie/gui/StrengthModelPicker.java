package featurecat.lizzie.gui;

import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;

/**
 * The model field of the strength dialog. It shows the chosen model by its everyday name and opens
 * a menu that says in one sentence what each model does, ending with "Import model…".
 */
final class StrengthModelPicker extends JButton {
  private static final long serialVersionUID = 1L;
  private final transient StrengthStyle style;
  private final transient ResourceBundle bundle;
  private final transient Consumer<PlayerStrengthEstimator.StrengthModel> onChoose;
  private final transient Runnable onImport;
  private final String caption;
  private transient PlayerStrengthEstimator.StrengthModel[] models;
  private transient PlayerStrengthEstimator.StrengthModel selected;
  private JPopupMenu menu;

  StrengthModelPicker(
      PlayerStrengthEstimator.StrengthModel[] models,
      PlayerStrengthEstimator.StrengthModel selected,
      ResourceBundle bundle,
      StrengthStyle style,
      Consumer<PlayerStrengthEstimator.StrengthModel> onChoose,
      Runnable onImport) {
    super(StrengthText.modelTitle(selected, bundle));
    this.style = style;
    this.bundle = bundle;
    this.onChoose = onChoose;
    this.onImport = onImport;
    this.models = models.clone();
    this.selected = selected;
    caption = bundle.getString("PlayerStrengthEstimate.model");
    setFont(style.font(Font.BOLD, 0));
    setContentAreaFilled(false);
    setBorderPainted(false);
    setFocusPainted(false);
    setOpaque(false);
    setRolloverEnabled(true);
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    AppleStyleSupport.preserveCustomButtonStyle(this);
    StrengthFocus.install(this);
    getAccessibleContext().setAccessibleName(caption);
    getAccessibleContext().setAccessibleDescription(getText());
    addActionListener(event -> showMenu());
    getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DOWN"), "strength-model-menu");
    getActionMap()
        .put(
            "strength-model-menu",
            new AbstractAction() {
              private static final long serialVersionUID = 1L;

              @Override
              public void actionPerformed(ActionEvent event) {
                showMenu();
              }
            });
  }

  PlayerStrengthEstimator.StrengthModel selectedModel() {
    return selected;
  }

  /** Shows {@code selected} among {@code models} without telling anyone a model was chosen. */
  void setModels(
      PlayerStrengthEstimator.StrengthModel[] models,
      PlayerStrengthEstimator.StrengthModel selected) {
    this.models = models.clone();
    this.selected = selected;
    setText(StrengthText.modelTitle(selected, bundle));
    getAccessibleContext().setAccessibleDescription(getText());
    revalidate();
    repaint();
  }

  /** Chooses {@code model} as the user does from the menu. */
  void choose(PlayerStrengthEstimator.StrengthModel model) {
    setModels(models, model);
    onChoose.accept(model);
  }

  boolean isMenuOpen() {
    return menu != null && menu.isVisible();
  }

  /** The menu of models, with the current one checked, then the import item. */
  JPopupMenu menu() {
    JPopupMenu popup = new JPopupMenu();
    popup.setBackground(style.surface);
    popup.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(style.border),
            BorderFactory.createEmptyBorder(style.px(5), style.px(5), style.px(5), style.px(5))));
    for (PlayerStrengthEstimator.StrengthModel model : models) {
      popup.add(new ModelItem(model, model.equals(selected)));
    }
    popup.add(new Separator());
    popup.add(new ImportItem());
    return popup;
  }

  /**
   * Where a menu of {@code size} opens: under the field and right-aligned with it, so that it stays
   * inside the dialog, whose right edge the field sits at.
   */
  Point menuLocation(Dimension size) {
    return new Point(getWidth() - size.width, getHeight() + style.px(4));
  }

  private void showMenu() {
    if (!isShowing()) {
      return;
    }
    menu = menu();
    Point location = menuLocation(menu.getPreferredSize());
    menu.show(this, location.x, location.y);
    for (MenuElement element : menu.getSubElements()) {
      if (element instanceof ModelItem item && item.current) {
        MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[] {menu, item});
      }
    }
  }

  private int badgeWidth(FontMetrics metrics, String badge) {
    return badge.isEmpty() ? 0 : metrics.stringWidth(badge) + style.px(12);
  }

  @Override
  public Dimension getPreferredSize() {
    FontMetrics captionMetrics = getFontMetrics(style.font(Font.PLAIN, 0));
    FontMetrics valueMetrics = getFontMetrics(getFont());
    FontMetrics badgeMetrics = getFontMetrics(style.font(Font.PLAIN, -2));
    int widest = 0;
    for (PlayerStrengthEstimator.StrengthModel model : models) {
      String badge = model.isHumanSl40() ? StrengthText.modelBadge(model, bundle) : "";
      widest =
          Math.max(
              widest,
              valueMetrics.stringWidth(StrengthText.modelTitle(model, bundle))
                  + (badge.isEmpty() ? 0 : style.px(6) + badgeWidth(badgeMetrics, badge)));
    }
    int width =
        style.px(12)
            + captionMetrics.stringWidth(caption)
            + style.px(10)
            + widest
            + style.px(12)
            + style.px(10)
            + style.px(12);
    return new Dimension(width, Math.max(style.px(34), valueMetrics.getHeight() + style.px(12)));
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  public Dimension getMaximumSize() {
    return getPreferredSize();
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = StrengthStyle.smooth(graphics);
    try {
      int width = getWidth();
      int height = getHeight();
      double arc = style.px(10);
      RoundRectangle2D shape =
          new RoundRectangle2D.Double(0.5, 0.5, width - 1, height - 1, arc, arc);
      boolean open = isMenuOpen();
      g.setColor(getModel().isRollover() || open ? style.sunken : style.surface);
      g.fill(shape);
      g.setColor(open ? style.accent : style.border);
      g.draw(shape);
      if (StrengthFocus.visible(this) && !open) {
        StrengthPaint.focusRing(
            g, style, new RoundRectangle2D.Double(1.5, 1.5, width - 3, height - 3, arc, arc));
      }
      Font captionFont = style.font(Font.PLAIN, 0);
      FontMetrics captionMetrics = g.getFontMetrics(captionFont);
      FontMetrics valueMetrics = g.getFontMetrics(getFont());
      int baseline =
          (height - valueMetrics.getAscent() - valueMetrics.getDescent()) / 2
              + valueMetrics.getAscent();
      int x = style.px(12);
      g.setFont(captionFont);
      g.setColor(style.secondaryText);
      g.drawString(caption, x, baseline);
      x += captionMetrics.stringWidth(caption) + style.px(10);
      int chevron = style.px(10);
      int right = width - chevron - style.px(24);
      String badge =
          selected != null && selected.isHumanSl40()
              ? StrengthText.modelBadge(selected, bundle)
              : "";
      int badgeWidth = badgeWidth(g.getFontMetrics(style.font(Font.PLAIN, -2)), badge);
      int titleWidth =
          StrengthPaint.text(
              g,
              getText(),
              getFont(),
              getFont().getSize() - 2,
              isEnabled() ? style.text : style.tertiaryText,
              x,
              baseline,
              right - x - (badgeWidth > 0 ? badgeWidth + style.px(6) : 0),
              StrengthPaint.LEFT);
      if (badgeWidth > 0) {
        paintBadge(g, badge, false, x + titleWidth + style.px(6), height / 2.0);
      }
      double chevronX = width - style.px(12) - chevron;
      double chevronY = height / 2.0 - chevron * 0.25;
      Path2D path = new Path2D.Double();
      path.moveTo(chevronX, chevronY);
      path.lineTo(chevronX + chevron / 2.0, chevronY + chevron / 2.0);
      path.lineTo(chevronX + chevron, chevronY);
      g.setColor(style.secondaryText);
      g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.draw(path);
    } finally {
      g.dispose();
    }
  }

  /** A small rounded label such as "Recommended", centred on {@code centerY}. */
  private void paintBadge(
      Graphics2D g, String text, boolean recommended, double x, double centerY) {
    Font font = style.font(Font.PLAIN, -2);
    FontMetrics metrics = g.getFontMetrics(font);
    double height = metrics.getHeight() + style.px(2);
    double width = badgeWidth(metrics, text);
    double top = centerY - height / 2.0;
    Color fill =
        recommended ? style.accentSoft : StrengthStyle.mix(style.caution, style.surface, 0.85);
    g.setColor(fill);
    g.fill(new RoundRectangle2D.Double(x, top, width, height, height, height));
    g.setFont(font);
    g.setColor(recommended ? style.accent : style.caution);
    g.drawString(
        text,
        (float) (x + style.px(6)),
        (float) (top + (height - metrics.getAscent() - metrics.getDescent()) / 2.0)
            + metrics.getAscent());
  }

  /** One model in the menu: its everyday name, a badge, its technical name and a sentence. */
  final class ModelItem extends JMenuItem {
    private static final long serialVersionUID = 1L;
    private final transient PlayerStrengthEstimator.StrengthModel model;
    private final boolean current;
    private final String description;
    private final String badge;
    private final String technical;

    private ModelItem(PlayerStrengthEstimator.StrengthModel model, boolean current) {
      super(StrengthText.modelTitle(model, bundle));
      this.model = model;
      this.current = current;
      description = StrengthText.modelDescription(model, bundle);
      badge = StrengthText.modelBadge(model, bundle);
      technical = StrengthText.modelName(model);
      setFont(style.font(Font.BOLD, 1));
      setOpaque(false);
      setBorder(BorderFactory.createEmptyBorder());
      setBorderPainted(false);
      getAccessibleContext().setAccessibleDescription(description);
      addActionListener(event -> choose(model));
    }

    PlayerStrengthEstimator.StrengthModel model() {
      return model;
    }

    boolean isCurrent() {
      return current;
    }

    String description() {
      return description;
    }

    private int textX() {
      return style.px(34);
    }

    private int textWidth() {
      return style.px(360);
    }

    private List<String> descriptionLines() {
      return StrengthPaint.wrap(
          description, getFontMetrics(style.font(Font.PLAIN, -1)), textWidth(), 3);
    }

    @Override
    public Dimension getPreferredSize() {
      FontMetrics title = getFontMetrics(getFont());
      FontMetrics small = getFontMetrics(style.font(Font.PLAIN, -1));
      int height =
          style.px(9) * 2
              + title.getHeight()
              + style.px(2)
              + descriptionLines().size() * small.getHeight();
      return new Dimension(textX() + textWidth() + style.px(14), height);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Graphics2D g = StrengthStyle.smooth(graphics);
      try {
        if (isArmed() || isSelected()) {
          g.setColor(style.dark ? style.raised : style.accentSoft);
          g.fill(
              new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), style.px(8), style.px(8)));
        }
        FontMetrics titleMetrics = g.getFontMetrics(getFont());
        int top = style.px(9);
        int titleBaseline = top + titleMetrics.getAscent();
        if (current) {
          double size = style.px(12);
          double x = style.px(12);
          double y = top + (titleMetrics.getHeight() - size) / 2.0;
          Path2D check = new Path2D.Double();
          check.moveTo(x + size * 0.1, y + size * 0.55);
          check.lineTo(x + size * 0.4, y + size * 0.82);
          check.lineTo(x + size * 0.92, y + size * 0.2);
          g.setColor(style.accent);
          g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g.draw(check);
        }
        Font small = style.font(Font.PLAIN, -1);
        FontMetrics smallMetrics = g.getFontMetrics(small);
        int techWidth = smallMetrics.stringWidth(technical);
        int right = textX() + textWidth();
        int titleWidth =
            StrengthPaint.text(
                g,
                getText(),
                getFont(),
                getFont().getSize() - 2,
                style.text,
                textX(),
                titleBaseline,
                textWidth() - techWidth - style.px(90),
                StrengthPaint.LEFT);
        if (!badge.isEmpty()) {
          paintBadge(
              g,
              badge,
              PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN.equals(model),
              textX() + titleWidth + style.px(8),
              top + titleMetrics.getHeight() / 2.0);
        }
        if (!technical.equals(getText())) {
          g.setFont(small);
          g.setColor(style.tertiaryText);
          g.drawString(technical, right - techWidth, titleBaseline);
        }
        g.setFont(small);
        g.setColor(style.secondaryText);
        int baseline = top + titleMetrics.getHeight() + style.px(2) + smallMetrics.getAscent();
        for (String line : descriptionLines()) {
          g.drawString(line, textX(), baseline);
          baseline += smallMetrics.getHeight();
        }
      } finally {
        g.dispose();
      }
    }
  }

  /** "Import model…" at the end of the menu. */
  final class ImportItem extends JMenuItem {
    private static final long serialVersionUID = 1L;

    private ImportItem() {
      super(bundle.getString("PlayerStrengthEstimate.model.import"));
      setFont(style.font(Font.PLAIN, 1));
      setOpaque(false);
      setBorder(BorderFactory.createEmptyBorder());
      setBorderPainted(false);
      setToolTipText(bundle.getString("PlayerStrengthEstimate.importModel.tooltip"));
      addActionListener(event -> onImport.run());
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(style.px(200), getFontMetrics(getFont()).getHeight() + style.px(16));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Graphics2D g = StrengthStyle.smooth(graphics);
      try {
        if (isArmed() || isSelected()) {
          g.setColor(style.dark ? style.raised : style.accentSoft);
          g.fill(
              new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), style.px(8), style.px(8)));
        }
        double size = style.px(13);
        double x = style.px(12);
        double y = (getHeight() - size) / 2.0;
        g.setColor(style.secondaryText);
        g.setStroke(
            new BasicStroke(
                (float) Math.max(1.4, size / 9.0), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D tray = new Path2D.Double();
        tray.moveTo(x + size * 0.08, y + size * 0.6);
        tray.lineTo(x + size * 0.08, y + size * 0.92);
        tray.lineTo(x + size * 0.92, y + size * 0.92);
        tray.lineTo(x + size * 0.92, y + size * 0.6);
        g.draw(tray);
        g.draw(new Line2D.Double(x + size / 2, y + size * 0.06, x + size / 2, y + size * 0.68));
        Path2D head = new Path2D.Double();
        head.moveTo(x + size * 0.27, y + size * 0.45);
        head.lineTo(x + size / 2, y + size * 0.68);
        head.lineTo(x + size * 0.73, y + size * 0.45);
        g.draw(head);
        FontMetrics metrics = g.getFontMetrics(getFont());
        g.setFont(getFont());
        g.setColor(style.text);
        g.drawString(
            getText(),
            style.px(34),
            (getHeight() - metrics.getAscent() - metrics.getDescent()) / 2 + metrics.getAscent());
      } finally {
        g.dispose();
      }
    }
  }

  /** A hairline between the models and the import item. */
  private final class Separator extends JComponent {
    private static final long serialVersionUID = 1L;

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(style.px(200), style.px(9));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      graphics.setColor(style.divider);
      graphics.fillRect(style.px(8), getHeight() / 2, getWidth() - style.px(16), 1);
    }
  }
}
