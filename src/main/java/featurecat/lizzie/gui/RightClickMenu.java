package featurecat.lizzie.gui;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.GameInfo;
import featurecat.lizzie.analysis.Leelaz;
import featurecat.lizzie.rules.Board;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

public class RightClickMenu extends JPopupMenu {
  static String trackingUnavailableKey(
      Leelaz.MoveFocusCapability capability, boolean paused, boolean tooltip) {
    if (capability == Leelaz.MoveFocusCapability.UNSUPPORTED) {
      return tooltip
          ? "RightClickMenu.trackPoint.upgradeTooltip"
          : "RightClickMenu.trackPoint.upgradeRequired";
    }
    if (paused && capability == Leelaz.MoveFocusCapability.SUPPORTED) {
      return tooltip
          ? "RightClickMenu.trackPoint.pausedTooltip"
          : "RightClickMenu.trackPoint.paused";
    }
    return tooltip
        ? "RightClickMenu.trackPoint.unavailableTooltip"
        : "RightClickMenu.trackPoint.requiresCapability";
  }

  //	public static int mousex;
  //	public static int mousey;
  public static int[] coords = LizzieFrame.outOfBoundCoordinate;
  private final ResourceBundle resourceBundle = Lizzie.resourceBundle;
  private JFontMenuItem findMove;
  private JFontMenuItem addblack;
  private JFontMenuItem addwhite;

  private JFontMenuItem priority;
  private JFontMenuItem clearPriority;
  private JFontMenuItem allow;
  private JFontMenuItem allow2;
  private JFontMenuItem allow3;
  private JFontMenuItem avoid;
  private JFontMenuItem previousMove;
  private JFontMenuItem addSuggestionAsBranch;
  private JFontMenuItem avoid2;
  private JFontMenuItem cancelavoid;
  private JFontMenuItem trackPoint;
  private JFontMenuItem untrackPoint;
  private JFontMenuItem clearAllTracked;
  private JFontMenuItem reedit;
  private JFontMenuItem cleanupedit;
  private JFontMenuItem cleanedittemp;
  JPopupMenu.Separator sep1 = BoardMenuStyle.separator();
  JPopupMenu.Separator sep = BoardMenuStyle.separator();
  private BoardRenderer previewOwner;

  public RightClickMenu() {

    PopupMenuListener listener =
        new PopupMenuListener() {
          public void popupMenuCanceled(PopupMenuEvent e) {}

          public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            SwingUtilities.invokeLater(
                () -> {
                  if (Lizzie.frame.isMouseOver) {
                    Lizzie.frame.mouseOverCoordinate = LizzieFrame.outOfBoundCoordinate;
                    Lizzie.frame.isMouseOver = false;
                    Lizzie.frame.clearMoved();
                  }
                  if (Lizzie.frame.independentMainBoard != null
                      && Lizzie.frame.independentMainBoard.isMouseOver) {
                    Lizzie.frame.independentMainBoard.mouseOverCoordinate =
                        LizzieFrame.outOfBoundCoordinate;
                    Lizzie.frame.independentMainBoard.isMouseOver = false;
                    Lizzie.frame.independentMainBoard.clearMoved();
                  }
                });
            Timer timer = new Timer();
            timer.schedule(
                new TimerTask() {
                  public void run() {
                    Lizzie.frame.isShowingRightMenu = false;
                    this.cancel();
                  }
                },
                200);
          }

          public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            refreshLook();
            boolean independent =
                Lizzie.frame.independentMainBoard != null
                    && (SwingUtilities.getWindowAncestor(getInvoker())
                            == Lizzie.frame.independentMainBoard
                        || Lizzie.config.isFloatBoardMode());
            previewOwner =
                independent
                    ? Lizzie.frame.independentMainBoard.boardRenderer
                    : LizzieFrame.boardRenderer;
            Lizzie.frame.isShowingRightMenu = true;
            addSuggestionAsBranch.setVisible(
                previewOwner.hasSelectedVariation() && !Lizzie.frame.isAnaPlayingAgainstLeelaz);

            if (Lizzie.board.boardstatbeforeedit == "") {
              cleanupedit.setVisible(false);
              if (Lizzie.board.boardstatafteredit == "") {
                cleanedittemp.setVisible(false);
              }
            } else {
              cleanupedit.setVisible(true);
              cleanedittemp.setVisible(true);
            }
            if (Lizzie.board.boardstatafteredit == "") {
              reedit.setVisible(false);
            } else {
              reedit.setVisible(true);
              cleanedittemp.setVisible(true);
            }
            if (Lizzie.frame.isPlayingAgainstLeelaz || Lizzie.frame.isAnaPlayingAgainstLeelaz) {
              priority.setVisible(false);
              clearPriority.setVisible(false);
              findMove.setVisible(false);
              addblack.setVisible(false);
              addwhite.setVisible(false);
              allow.setVisible(false);
              allow2.setVisible(false);
              avoid.setVisible(false);
              avoid2.setVisible(false);
              allow3.setVisible(false);
              cancelavoid.setVisible(false);
              trackPoint.setVisible(false);
              untrackPoint.setVisible(false);
              clearAllTracked.setVisible(false);
              sep.setVisible(false);
              sep1.setVisible(false);
              previousMove.setText(resourceBundle.getString("RightClickMenu.regretOne")); // ("悔棋");
            } else {
              previousMove.setText(
                  resourceBundle.getString("RightClickMenu.previousMove")); // ("回退一手");
              findMove.setVisible(true);
              addblack.setVisible(true);
              addwhite.setVisible(true);
              if (Lizzie.leelaz.isKatagoCustom) {
                priority.setVisible(true);
                clearPriority.setVisible(true);
                sep1.setVisible(true);
              } else {
                priority.setVisible(false);
                clearPriority.setVisible(false);
                sep1.setVisible(false);
              }
              sep.setVisible(true);
              allow.setVisible(true);
              avoid.setVisible(true);
              avoid2.setVisible(true);
              allow3.setVisible(false);
              if (LizzieFrame.allowcoords != "") {
                {
                  allow2.setVisible(true);
                  String[] params = LizzieFrame.allowcoords.trim().split(",");
                  if (params.length > 1) {
                    String coordsHere =
                        Board.convertCoordinatesToName(
                            RightClickMenu.coords[0], RightClickMenu.coords[1]);
                    for (String coords : params) {
                      if (coordsHere.equals(coords)) {
                        allow3.setVisible(true);
                        break;
                      }
                    }
                  }
                }
                if (LizzieFrame.avoidcoords != "") {
                  cancelavoid.setVisible(true);
                }
              } else {
                allow2.setVisible(false);
              }
              boolean canTrack = Lizzie.frame.canStartTrackingAnalysis();
              Leelaz engine = Lizzie.leelaz;
              boolean paused = !engine.isPondering();
              String coordName =
                  Board.convertCoordinatesToName(
                      RightClickMenu.coords[0], RightClickMenu.coords[1]);
              boolean isTracked = Lizzie.frame.isTrackingPoint(coordName);
              boolean hasTracked = Lizzie.frame.hasTrackingPoints();
              trackPoint.setVisible(!isTracked && Lizzie.board.iscoordsempty(coords[0], coords[1]));
              trackPoint.setEnabled(canTrack);
              trackPoint.setText(
                  resourceBundle.getString(
                      canTrack
                          ? "RightClickMenu.trackPoint"
                          : trackingUnavailableKey(engine.moveFocusCapability(), paused, false)));
              trackPoint.setToolTipText(
                  resourceBundle.getString(
                      canTrack
                          ? "RightClickMenu.trackPoint.tooltip"
                          : trackingUnavailableKey(engine.moveFocusCapability(), paused, true)));
              untrackPoint.setVisible(isTracked);
              clearAllTracked.setVisible(hasTracked);
            }
          }
          //	}
        };

    this.addPopupMenuListener(listener);
    // insertmode = new JFontMenuItem("进入插入棋子模式");
    // quitinsert = new JFontMenuItem("退出插入棋子模式");
    findMove = new JFontMenuItem(resourceBundle.getString("RightClickMenu.findMove"));
    findMove.setIcon(BoardMenuGlyph.FIND.icon());
    priority = new JFontMenuItem(resourceBundle.getString("RightClickMenu.priority"));
    priority.setIcon(BoardMenuGlyph.PRIORITY.icon());
    clearPriority = new JFontMenuItem(resourceBundle.getString("RightClickMenu.clearPriority"));
    clearPriority.setIcon(BoardMenuGlyph.CLEAR.icon());
    addSuggestionAsBranch =
        new JFontMenuItem(
            resourceBundle.getString("RightClickMenu.addSuggestionAsBranch")); // ("将变化图添加为分支");
    addSuggestionAsBranch.setIcon(BoardMenuGlyph.BRANCH.icon());
    addblack = new JFontMenuItem(resourceBundle.getString("RightClickMenu.addblack")); // ("插入黑子");
    addblack.setIcon(BoardMenuGlyph.BLACK_STONE.icon());
    addwhite = new JFontMenuItem(resourceBundle.getString("RightClickMenu.addwhite")); // ("插入白子");
    addwhite.setIcon(BoardMenuGlyph.WHITE_STONE.icon());
    allow = new JFontMenuItem(resourceBundle.getString("RightClickMenu.allow")); // ("只分析此点");
    allow.setIcon(BoardMenuGlyph.ANALYZE_ONLY.icon());
    allow2 = new JFontMenuItem(resourceBundle.getString("RightClickMenu.allow2")); // ("增加分析此点");
    allow2.setIcon(BoardMenuGlyph.ANALYZE_ADD.icon());
    allow3 = new JFontMenuItem(resourceBundle.getString("RightClickMenu.allow3")); // "去除分析此点");
    allow3.setIcon(BoardMenuGlyph.REMOVE.icon());
    avoid = new JFontMenuItem(resourceBundle.getString("RightClickMenu.avoid")); // ("不分析此点");
    avoid.setIcon(BoardMenuGlyph.AVOID.icon());
    // A plain item whose icon shows the state: a check box item would add a check column to the
    // whole menu, pushing every icon right.
    avoid2 = new JFontMenuItem(resourceBundle.getString("RightClickMenu.avoid2")); // ("持续分析/不分析");
    cancelavoid =
        new JFontMenuItem(resourceBundle.getString("RightClickMenu.cancelavoid")); // ("清除分析与不分析");
    cancelavoid.setIcon(BoardMenuGlyph.CLEAR.icon());
    trackPoint = new JFontMenuItem(resourceBundle.getString("RightClickMenu.trackPoint"));
    trackPoint.setIcon(BoardMenuGlyph.EVALUATE.icon());
    trackPoint.setToolTipText(resourceBundle.getString("RightClickMenu.trackPoint.tooltip"));
    untrackPoint = new JFontMenuItem(resourceBundle.getString("RightClickMenu.untrackPoint"));
    untrackPoint.setIcon(BoardMenuGlyph.REMOVE.icon());
    clearAllTracked = new JFontMenuItem(resourceBundle.getString("RightClickMenu.clearAllTracked"));
    clearAllTracked.setIcon(BoardMenuGlyph.CLEAR.icon());
    cleanedittemp =
        new JFontMenuItem(resourceBundle.getString("RightClickMenu.cleanedittemp")); // ("清除编辑缓存");
    cleanedittemp.setIcon(BoardMenuGlyph.CLEAR.icon());
    reedit = new JFontMenuItem(resourceBundle.getString("RightClickMenu.reedit")); // ("恢复到编辑前");
    reedit.setIcon(BoardMenuGlyph.REDO.icon());
    cleanupedit =
        new JFontMenuItem(resourceBundle.getString("RightClickMenu.cleanupedit")); // ("恢复到编辑后");
    cleanupedit.setIcon(BoardMenuGlyph.UNDO.icon());
    previousMove =
        new JFontMenuItem(resourceBundle.getString("RightClickMenu.previousMove")); // ("回退一手");
    previousMove.setIcon(BoardMenuGlyph.UNDO.icon());
    this.add(allow);
    this.add(allow2);
    this.add(allow3);
    this.add(avoid);
    this.add(cancelavoid);
    this.add(avoid2);
    this.add(trackPoint);
    this.add(untrackPoint);
    this.add(clearAllTracked);
    this.add(sep);
    this.add(priority);
    this.add(clearPriority);
    this.add(sep1);
    this.add(previousMove);
    this.add(findMove);
    this.add(addblack);
    this.add(addwhite);
    this.add(reedit);
    this.add(cleanupedit);
    this.add(cleanedittemp);
    this.add(addSuggestionAsBranch);
    refreshLook();

    addMouseListener(
        new MouseAdapter() {
          public void mouseExited(MouseEvent e) {
            //	                Lizzie.frame.mouseOverCoordinate = LizzieFrame.outOfBoundCoordinate;
            //	                Lizzie.frame.isMouseOver = false;
            //	                Lizzie.frame.clearMoved();
          }
        });
    priority.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (!Lizzie.leelaz.isPondering()) Lizzie.leelaz.togglePonder();
            String coord = Board.convertCoordinatesToName(coords[0], coords[1]);
            Lizzie.frame.priorityMoveCoords.add(coord);
            Lizzie.leelaz.sendCommand("setmaxpolicy " + coord + " 1.1");
          }
        });

    clearPriority.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            Lizzie.leelaz.sendCommand("clearpolicy");
            Lizzie.frame.priorityMoveCoords.clear();
          }
        });

    previousMove.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            Lizzie.frame.undoForRightClick();
          }
        });

    addSuggestionAsBranch.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (previewOwner != null) Lizzie.frame.addSuggestionAsBranch(previewOwner);
          }
        });

    cleanedittemp.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            cleanedittemp();
          }
        });

    reedit.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            GameInfo gameInfo = Lizzie.board.getHistory().getGameInfo();
            reedit();
            Lizzie.board.getHistory().setGameInfo(gameInfo);
            Lizzie.frame.refresh();
          }
        });

    cleanupedit.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            GameInfo gameInfo = Lizzie.board.getHistory().getGameInfo();
            cleanupedit();
            Lizzie.board.getHistory().setGameInfo(gameInfo);
            Lizzie.frame.refresh();
          }
        });

    allow2.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            allow2();
          }
        });

    allow3.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            allow3();
          }
        });

    findMove.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            Lizzie.board.findMove(coords);
            Lizzie.frame.refresh();
          }
        });

    addblack.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            GameInfo gameInfo = Lizzie.board.getHistory().getGameInfo();
            addblack();
            Lizzie.board.getHistory().setGameInfo(gameInfo);
          }
        });

    addwhite.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            GameInfo gameInfo = Lizzie.board.getHistory().getGameInfo();
            addwhite();
            Lizzie.board.getHistory().setGameInfo(gameInfo);
          }
        });

    allow.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            allow();
          }
        });

    avoid.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            avoid();
          }
        });

    avoid2.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            avoid2();
          }
        });

    cancelavoid.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            cancelavoid();
          }
        });

    trackPoint.addActionListener(e -> trackPointAction());
    untrackPoint.addActionListener(e -> untrackPointAction());
    clearAllTracked.addActionListener(e -> Lizzie.frame.clearTrackingPoints());
  }

  /** The menu's frame and the keep-forcing state icon, refreshed each time the menu opens. */
  void refreshLook() {
    avoid2.setIcon(
        (LizzieFrame.isKeepForcing ? BoardMenuGlyph.KEEP_ON : BoardMenuGlyph.KEEP_OFF).icon());
    BoardMenuStyle.install(this);
  }

  private void cleanupedit() {
    Lizzie.board.cleanedit();
  }

  private void cleanedittemp() {
    Lizzie.board.cleanedittemp();
  }

  private void reedit() {
    Lizzie.board.reedit();
  }

  private void addblack() {
    if (Lizzie.board.iscoordsempty(coords[0], coords[1])) {
      Lizzie.frame.insertMove(coords, true);
    }
  }

  private void cancelavoid() {
    LizzieFrame.allowcoords = "";
    LizzieFrame.avoidcoords = "";
    // move = 0;
    Lizzie.leelaz.ponder();
    Lizzie.board.clearBestMovesAfter(Lizzie.board.getHistory().getStart());
    LizzieFrame.boardRenderer.removeSelectedRect();
    Lizzie.frame.refresh();
  }

  private void trackPointAction() {
    if (!Lizzie.board.iscoordsempty(coords[0], coords[1])) return;
    String coordName = Board.convertCoordinatesToName(coords[0], coords[1]);
    Lizzie.frame.addTrackingPoint(coordName);
    Lizzie.frame.refresh();
  }

  private void untrackPointAction() {
    String coordName = Board.convertCoordinatesToName(coords[0], coords[1]);
    Lizzie.frame.removeTrackingPoint(coordName);
    Lizzie.frame.refresh();
  }

  private void addwhite() {
    if (Lizzie.board.iscoordsempty(coords[0], coords[1])) {
      Lizzie.frame.insertMove(coords, false);
    }
  }

  private void allow() {
    if (Lizzie.board.iscoordsempty(coords[0], coords[1])) {
      LizzieFrame.allowcoords = Board.convertCoordinatesToName(coords[0], coords[1]);
    }
    LizzieFrame.isforcing = true;
    LizzieFrame.isallow = true;
    LizzieFrame.isTempForcing = true;
    LizzieFrame.avoidcoords = "";
    Lizzie.leelaz.Pondering();
    Lizzie.leelaz.analyzeAvoid("allow", LizzieFrame.allowcoords, 1);
    Lizzie.board.clearBestMovesAfter(Lizzie.board.getHistory().getStart());

    LizzieFrame.boardRenderer.drawAllSelectedRectByCoords(true, LizzieFrame.allowcoords);
    Lizzie.frame.refresh();
  }

  private void allow2() {
    if (Lizzie.board.iscoordsempty(coords[0], coords[1])) {
      if (LizzieFrame.allowcoords != "") {
        LizzieFrame.allowcoords += "," + Board.convertCoordinatesToName(coords[0], coords[1]);
      } else {
        LizzieFrame.allowcoords = Board.convertCoordinatesToName(coords[0], coords[1]);
      }
    }
    LizzieFrame.isforcing = true;
    LizzieFrame.isallow = true;
    LizzieFrame.avoidcoords = "";
    Lizzie.leelaz.Pondering();
    Lizzie.leelaz.analyzeAvoid("allow", LizzieFrame.allowcoords, 1);
    Lizzie.board.clearBestMovesAfter(Lizzie.board.getHistory().getStart());
    LizzieFrame.boardRenderer.drawAllSelectedRectByCoords(true, LizzieFrame.allowcoords);
    Lizzie.frame.refresh();
  }

  private void allow3() {
    String newCoords = "";
    String[] params = LizzieFrame.allowcoords.trim().split(",");
    String coordsHere =
        Board.convertCoordinatesToName(RightClickMenu.coords[0], RightClickMenu.coords[1]);
    boolean first = true;
    for (String coords : params) {
      if (!coordsHere.equals(coords)) {
        if (first) newCoords = coords;
        else newCoords += "," + coords;
      }
    }
    LizzieFrame.allowcoords = newCoords;
    LizzieFrame.isforcing = true;
    LizzieFrame.isallow = true;
    LizzieFrame.avoidcoords = "";
    Lizzie.leelaz.Pondering();
    Lizzie.leelaz.analyzeAvoid("allow", LizzieFrame.allowcoords, 1);
    Lizzie.board.clearBestMovesAfter(Lizzie.board.getHistory().getStart());
    LizzieFrame.boardRenderer.drawAllSelectedRectByCoords(true, LizzieFrame.allowcoords);
    Lizzie.frame.refresh();
  }

  private void avoid() {
    LizzieFrame.isTempForcing = true;
    LizzieFrame.isforcing = true;
    LizzieFrame.isallow = false;
    if (Lizzie.board.iscoordsempty(coords[0], coords[1])) {
      if (LizzieFrame.avoidcoords != "") {
        LizzieFrame.avoidcoords += "," + Board.convertCoordinatesToName(coords[0], coords[1]);
      } else {
        LizzieFrame.avoidcoords = Board.convertCoordinatesToName(coords[0], coords[1]);
      }
    }
    Lizzie.leelaz.analyzeAvoid("avoid", LizzieFrame.avoidcoords, Lizzie.config.selectAvoidMoves);
    Lizzie.board.clearBestMovesAfter(Lizzie.board.getHistory().getStart());
    LizzieFrame.boardRenderer.drawAllSelectedRectByCoords(false, LizzieFrame.avoidcoords);
    Lizzie.frame.repaint();
  }

  private void avoid2() {
    LizzieFrame.isKeepForcing = !LizzieFrame.isKeepForcing;
  }

  public void setCoords(int[] coords) {
    RightClickMenu.coords = coords;
  }
}
