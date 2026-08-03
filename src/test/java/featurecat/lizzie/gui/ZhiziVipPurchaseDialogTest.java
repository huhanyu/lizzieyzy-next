package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import featurecat.lizzie.analysis.remote.ZhiziApiClient;
import featurecat.lizzie.analysis.remote.ZhiziApiException;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
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
            .decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image))));

    assertEquals(payload, decoded.getText());
  }

  @Test
  void paymentFlowRejectsDuplicateCreateAndTracksOneTopUpOrder() throws Exception {
    ZhiziVipPurchaseDialog.PaymentFlow flow = new ZhiziVipPurchaseDialog.PaymentFlow();
    ZhiziVipPurchaseDialog.PaymentChoice choice = ZhiziVipPurchaseDialog.PaymentChoice.topUp(1000L);

    assertTrue(flow.beginCreate(choice));
    assertFalse(flow.beginCreate(choice), "a second click must not create another order");
    flow.orderCreated(
        order("66a000000000000000000010", 1000L, ZhiziApiClient.PaymentStatus.PENDING));
    assertEquals(ZhiziVipPurchaseDialog.PaymentFlow.State.PENDING, flow.state());

    flow.orderUpdated(
        order("66a000000000000000000010", 1000L, ZhiziApiClient.PaymentStatus.SUCCESS));
    assertEquals(ZhiziVipPurchaseDialog.PaymentFlow.State.SUCCESS, flow.state());
    assertFalse(flow.beginCreate(choice));
  }

  @Test
  void paymentFlowRejectsChangedAmountAndOrderIdentity() throws Exception {
    ZhiziVipPurchaseDialog.PaymentFlow flow = new ZhiziVipPurchaseDialog.PaymentFlow();
    ZhiziVipPurchaseDialog.PaymentChoice choice = ZhiziVipPurchaseDialog.PaymentChoice.topUp(1000L);
    assertTrue(flow.beginCreate(choice));

    ZhiziApiException changedAmount =
        assertThrows(
            ZhiziApiException.class,
            () ->
                flow.orderCreated(
                    order(
                        "66a000000000000000000010", 3000L, ZhiziApiClient.PaymentStatus.PENDING)));
    assertEquals("invalid_response", changedAmount.errorKey());
    assertEquals(ZhiziApiException.Operation.CREATE_ORDER, changedAmount.operation());
    flow.createFailed();
    assertTrue(flow.isSelecting());

    assertTrue(flow.beginCreate(choice));
    flow.orderCreated(
        order("66a000000000000000000010", 1000L, ZhiziApiClient.PaymentStatus.PENDING));
    ZhiziApiException changedIdentity =
        assertThrows(
            ZhiziApiException.class,
            () ->
                flow.orderUpdated(
                    order(
                        "66a000000000000000000011", 1000L, ZhiziApiClient.PaymentStatus.SUCCESS)));
    assertEquals(ZhiziApiException.Operation.FETCH_ORDER, changedIdentity.operation());
  }

  @Test
  void closingPaymentFlowPreventsFurtherOrders() {
    ZhiziVipPurchaseDialog.PaymentFlow flow = new ZhiziVipPurchaseDialog.PaymentFlow();
    flow.close();

    assertEquals(ZhiziVipPurchaseDialog.PaymentFlow.State.CLOSED, flow.state());
    assertFalse(flow.beginCreate(ZhiziVipPurchaseDialog.PaymentChoice.topUp(1000L)));
  }

  private static ZhiziApiClient.PaymentOrder order(
      String id, long amountFen, ZhiziApiClient.PaymentStatus status) throws Exception {
    Constructor<ZhiziApiClient.PaymentOrder> constructor =
        ZhiziApiClient.PaymentOrder.class.getDeclaredConstructor(
            String.class,
            long.class,
            String.class,
            ZhiziApiClient.PaymentPurpose.class,
            ZhiziApiClient.PaymentStatus.class,
            String.class,
            java.time.Instant.class,
            java.time.Instant.class);
    constructor.setAccessible(true);
    return constructor.newInstance(
        id,
        amountFen,
        "",
        ZhiziApiClient.PaymentPurpose.BALANCE_TOP_UP,
        status,
        status == ZhiziApiClient.PaymentStatus.PENDING ? "weixin://wxpay/bizpayurl?pr=test" : "",
        null,
        null);
  }
}
