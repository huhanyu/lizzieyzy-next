package featurecat.lizzie.analysis;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.gui.LizzieFrame;
import featurecat.lizzie.util.Utils;
import org.json.JSONException;

/** SSH transport for a remote GTP engine. */
public class SSHController extends SshEngineController {
  private final Leelaz owner;

  public SSHController(Leelaz owner, String ip, String port) {
    super(ip, port);
    this.owner = owner;
  }

  @Override
  protected void onAuthenticationRejected() {
    owner.isLoaded = false;
    Utils.showMsg(Lizzie.resourceBundle.getString("SSHController.connectFailed"));
    LizzieFrame.openMoreEngineDialog();
  }

  @Override
  protected void onConnectionFailed(String message) {
    owner.isLoaded = false;
    try {
      owner.tryToDignostic(
          Lizzie.resourceBundle.getString("SSHController.engineFailed") + ": " + message, true);
      LizzieFrame.openMoreEngineDialog();
    } catch (JSONException e) {
      e.printStackTrace();
    }
  }
}
