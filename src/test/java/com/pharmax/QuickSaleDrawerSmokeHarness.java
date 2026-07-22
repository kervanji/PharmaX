package com.pharmax;

import com.pharmax.database.DatabaseManager;
import com.pharmax.model.User;
import com.pharmax.model.UserRole;
import com.pharmax.util.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.File;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class QuickSaleDrawerSmokeHarness {
    private QuickSaleDrawerSmokeHarness() {}

    public static void main(String[] args) throws Exception {
        DatabaseManager.initialize();
        SessionManager.getInstance().startSession(new User("smoke-admin", "Smoke Admin", "x", UserRole.ADMIN), false);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                FXMLLoader saleLoader = new FXMLLoader(QuickSaleDrawerSmokeHarness.class.getResource("/views/SaleForm.fxml"));
                Parent sale = saleLoader.load();
                Parent manager = FXMLLoader.load(QuickSaleDrawerSmokeHarness.class.getResource("/views/QuickSaleManager.fxml"));
                Parent product = FXMLLoader.load(QuickSaleDrawerSmokeHarness.class.getResource("/views/ProductForm.fxml"));
                require(saleLoader.getNamespace().get("inlineQuickSaleButtonsPane") != null,
                        "Inline quick-sale product buttons pane was not restored");
                require(saleLoader.getNamespace().get("quickSaleDrawer") != null,
                        "Quick-sale side drawer was not restored");
                require(saleLoader.getNamespace().get("quickSaleDrawerToggle") != null,
                        "Quick-sale side drawer toggle was not restored");
                require(manager != null, "Quick sale manager was not created");
                require(product != null, "Product form with unlimited stock option was not created");
                if (args.length > 0) {
                    Stage stage = new Stage();
                    Scene scene = new Scene(sale, 1366, 768);
                    scene.getStylesheets().add(QuickSaleDrawerSmokeHarness.class.getResource("/styles/theme.css").toExternalForm());
                    stage.setScene(scene);
                    stage.show();
                    sale.applyCss(); sale.layout();
                    sale.applyCss(); sale.layout();
                    WritableImage image = sale.snapshot(null, null);
                    ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", new File(args[0]));
                    stage.hide();
                }
                System.out.println("QUICK_SALE_FXML_SMOKE_OK");
            } catch (Throwable e) {
                failure.set(e);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("JavaFX smoke test timed out");
        Platform.runLater(Platform::exit);
        DatabaseManager.shutdown();
        SessionManager.getInstance().endSession();
        if (failure.get() != null) throw new RuntimeException(failure.get());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
