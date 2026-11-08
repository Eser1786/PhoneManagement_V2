package com.phonestore.gui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.function.Consumer;

public final class SceneNavigator {

    private static final String FXML_DIR = "/fxml/";

    private static Stage primaryStage;
    private static Pane contentArea; // vùng nội dung của MainLayout

    private SceneNavigator() {}

    /** Gọi 1 lần trong App.start(). */
    public static void init(Stage stage) {
        primaryStage = stage;
    }

    /** MainLayoutController gọi trong initialize() để đăng ký vùng nội dung. */
    public static void setContentArea(Pane area) {
        contentArea = area;
    }

    /** Đổi toàn bộ cửa sổ (Login <-> MainLayout). */
    public static void switchScene(String fxml, String title) {
        switchScene(fxml, title, null);
    }

    /** Như trên, kèm callback để truyền dữ liệu vào controller đích. */
    public static <T> void switchScene(String fxml, String title, Consumer<T> onLoaded) {
        contentArea = null; // phải reset TRƯỚC khi load, vì MainLayout sẽ tự đăng ký lại
        FXMLLoader loader = load(fxml);
        Parent root = loader.getRoot();

        if (onLoaded != null) {
            T controller = loader.getController();
            onLoaded.accept(controller);
        }

        Scene scene = primaryStage.getScene();
        if (scene == null) {
            primaryStage.setScene(new Scene(root));
        } else {
            scene.setRoot(root);
        }
        primaryStage.setTitle(title);
        primaryStage.sizeToScene();
        primaryStage.centerOnScreen();
    }

    /** Đổi nội dung bên trong MainLayout. */
    public static void loadContent(String fxml) {
        loadContent(fxml, null);
    }

    public static <T> T loadContent(String fxml, Consumer<T> onLoaded) {
        if (contentArea == null) {
            throw new IllegalStateException(
                "Chưa có MainLayout: hãy gọi setContentArea() trong MainLayoutController.");
        }
        FXMLLoader loader = load(fxml);
        Parent root = loader.getRoot();
        T controller = loader.getController();
        if (onLoaded != null) {
            onLoaded.accept(controller);
        }
        contentArea.getChildren().setAll(root);
        return controller;
    }

    private static FXMLLoader load(String fxml) {
        URL url = SceneNavigator.class.getResource(FXML_DIR + fxml);
        if (url == null) {
            throw new IllegalArgumentException("Không tìm thấy FXML: " + FXML_DIR + fxml);
        }
        FXMLLoader loader = new FXMLLoader(url);
        try {
            loader.load();
        } catch (IOException e) {
            throw new IllegalStateException("Lỗi nạp " + fxml, e);
        }
        return loader;
    }
}