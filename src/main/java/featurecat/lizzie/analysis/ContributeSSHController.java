package featurecat.lizzie.analysis;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.util.Utils;
import org.json.JSONException;

/** SSH transport for a remote KataGo contribute process. */
public class ContributeSSHController extends SshEngineController {
  private final ContributeEngine owner;

  public ContributeSSHController(ContributeEngine owner, String ip, String port) {
    super(ip, port);
    this.owner = owner;
  }

  @Override
  protected void onAuthenticationRejected() {
    Utils.showMsg(Lizzie.resourceBundle.getString("SSHController.connectFailed"));
  }

  @Override
  protected void onConnectionFailed(String message) {
    try {
      owner.tryToDignostic(
          Lizzie.resourceBundle.getString("SSHController.engineFailed") + ": " + message);
    } catch (JSONException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void close() {
    owner.javaSSHClosed = true;
    super.close();
  }
}
