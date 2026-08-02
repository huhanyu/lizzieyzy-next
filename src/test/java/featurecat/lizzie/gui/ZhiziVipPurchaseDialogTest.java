package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class ZhiziVipPurchaseDialogTest {
  @Test
  void formatsIntegerFenWithoutFloatingPointRounding() {
    assertEquals("¥0.01", ZhiziVipPurchaseDialog.formatFen(1));
    assertEquals("¥30.00", ZhiziVipPurchaseDialog.formatFen(3000));
    assertEquals("¥280.00", ZhiziVipPurchaseDialog.formatFen(28000));
  }

  @Test
  void paymentQrPreservesTheOpaqueWechatPayloadExactly() throws Exception {
    String payload = "weixin://wxpay/bizpayurl?pr=opaque%2Bvalue&nonce=123";

    BufferedImage image = ZhiziVipPurchaseDialog.renderQr(payload, 276);
    Result decoded =
        new MultiFormatReader()
            .decode(
                new BinaryBitmap(
                    new HybridBinarizer(new BufferedImageLuminanceSource(image))));

    assertEquals(payload, decoded.getText());
  }
}
