package featurecat.lizzie.gui;

import featurecat.lizzie.Lizzie;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

public class RightClickMenu2 extends JPopupMenu {
  //	public static int mousex;
  //	public static int mousey;
  public static int[] coords;
  // private JFontMenuItem insertmode;
  private JFontMenuItem switchone;
  private JFontMenuItem deleteone;
  private JFontMenuItem previousMove;
  private JPopupMenu.Separator sep1;
  private JPopupMenu.Separator sep2;
  private JFontMenuItem moveStone;
  private JFontMenuItem findStone;
  private JFontMenuItem review;
  private final ResourceBundle resourceBundle = Lizzie.resourceBundle;
  private boolean isFromIndependent;

  public RightClickMenu2() {

    PopupMenuListener listener =
        new PopupMenuListener() {
          public void popupMenuCanceled(PopupMenuEvent e) {}

          public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            //				if (Lizzie.leelaz.isPondering() && featurecat.lizzie.gui.RightClickMenu.isforcing)
            // {
            //					if (featurecat.lizzie.gui.RightClickMenu.isforcing) {
            //						Lizzie.leelaz.analyzeAvoid("allow", Lizzie.board.getcurrentturn(),
            //								featurecat.lizzie.gui.RightClickMenu.allowcoords, 1);
            //					} else {
            //						Lizzie.leelaz.analyzeAvoid("avoid", Lizzie.board.getcurrentturn(),
            //								featurecat.lizzie.gui.RightClickMenu.avoidcoords, 30);
            //					}
            //				}
            //				if (Lizzie.leelaz.isPondering() &&
            // !featurecat.lizzie.gui.RightClickMenu.isforcing) {
            //					Lizzie.leelaz.ponder();
            //				}
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
            BoardMenuStyle.install(RightClickMenu2.this);
            Lizzie.frame.isShowingRightMenu = true;
            if (Lizzie.frame.isPlayingAgainstLeelaz || Lizzie.frame.isAnaPlayingAgainstLeelaz) {
              previousMove.setText(resourceBundle.getString("RightClickMenu.regretOne")); // ("悔棋");
              switchone.setVisible(false);
              deleteone.setVisible(false);
              moveStone.setVisible(false);
              findStone.setVisible(false);
              review.setVisible(false);
              sep1.setVisible(false);
              sep2.setVisible(false);
            } else {
              previousMove.setText(
                  resourceBundle.getString("RightClickMenu.previousMove")); // ("回退一手");
              review.setVisible(true);
              switchone.setVisible(true);
              deleteone.setVisible(true);
              moveStone.setVisible(true);
              findStone.setVisible(true);
              sep1.setVisible(true);
              sep2.setVisible(true);
            }
          }
        };

    this.addPopupMenuListener(listener);
    findStone = new JFontMenuItem(resourceBundle.getString("RightClickMenu2.findStone"));
    findStone.setIcon(BoardMenuGlyph.JUMP.icon());
    switchone = new JFontMenuItem(resourceBundle.getString("RightClickMenu2.switchone"));
    switchone.setIcon(BoardMenuGlyph.SWAP_COLOR.icon());
    deleteone = new JFontMenuItem(resourceBundle.getString("RightClickMenu2.deleteone"));
    deleteone.setIcon(BoardMenuGlyph.CLEAR.icon());
    previousMove = new JFontMenuItem(resourceBundle.getString("RightClickMenu.previousMove"));
    previousMove.setIcon(BoardMenuGlyph.UNDO.icon());
    moveStone = new JFontMenuItem(resourceBundle.getString("RightClickMenu2.moveStone"));
    moveStone.setIcon(BoardMenuGlyph.MOVE.icon());
    review = new JFontMenuItem(resourceBundle.getString("RightClickMenu2.review"));
    review.setIcon(BoardMenuGlyph.REVIEW.icon());
    // Toolbar separators drew boxes here; the menus share one hairline.
    sep1 = BoardMenuStyle.separator();
    sep2 = BoardMenuStyle.separator();
    this.add(moveStone);
    this.add(switchone);
    this.add(deleteone);
    this.add(sep1);
    this.add(review);
    this.add(sep2);
    this.add(previousMove);
    this.add(findStone);
    BoardMenuStyle.install(this);
    findStone.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            Lizzie.board.findMove(coords);
            Lizzie.frame.refresh();
          }
        });
    previousMove.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            Lizzie.frame.undoForRightClick();
          }
        });

    switchone.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            switchone();
          }
        });

    deleteone.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            deleteone();
          }
        });

    moveStone.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (isFromIndependent) Lizzie.frame.independentMainBoard.setDragStartInfo(coords, true);
            else Lizzie.frame.setDragStartInfo(coords, true);
          }
        });

    review.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (Lizzie.board.hasStoneAt(coords)) {
              Lizzie.board.setPressStoneInfo(coords, true);
            }
          }
        });
  }

  private void switchone() {
    Lizzie.board.editMove(coords, true, false);
  }

  private void deleteone() {
    Lizzie.board.editMove(coords, false, true);
  }

  public void setFromIndependent(boolean isFromIndependent) {
    this.isFromIndependent = isFromIndependent;
  }

  public void setCoords(int[] coords) {
    // TODO Auto-generated method stub
    RightClickMenu2.coords = coords;
  }
  //	public void Store(int x, int y) {
  //		mousex = x;
  //		mousey = y;
  //	}
}
