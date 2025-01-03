import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main extends Application {
    private final ObservableList<File> fileList = FXCollections.observableArrayList();
    private final StringProperty currentPath = new SimpleStringProperty();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private WatchService watchService;
    private ListView<File> listView;

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        HBox topBar = new HBox(10);
        TextField pathDisplay = new TextField();
        pathDisplay.textProperty().bind(currentPath);
        pathDisplay.setEditable(false);
        pathDisplay.setPrefWidth(400);

        Button browseButton = new Button("Przeglądaj");
        browseButton.setOnAction(e -> browseFolders(stage));

        topBar.getChildren().addAll(pathDisplay, browseButton);
        root.setTop(topBar);

        listView = new ListView<>(fileList);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(File file, boolean empty) {
                super.updateItem(file, empty);
                setText(empty ? null : file.getName());
            }
        });

        listView.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getButton() == MouseButton.PRIMARY && mouseEvent.getClickCount() == 2) {
                File selectedFile = listView.getSelectionModel().getSelectedItem();
                if (selectedFile != null && selectedFile.isFile()) {
                    openFile(selectedFile);
                }
            }
        });

        root.setCenter(listView);

        Scene scene = new Scene(root, 600, 400);
        stage.setTitle("Reaktywny Menedżer Plików");
        stage.setScene(scene);
        stage.show();
    }

    private void browseFolders(Stage stage) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        File selectedDirectory = directoryChooser.showDialog(stage);

        if (selectedDirectory != null) {
            currentPath.set(selectedDirectory.getAbsolutePath());
            loadFiles(selectedDirectory);
            setupWatcher(selectedDirectory);
        }
    }

    private void loadFiles(File directory) {
        fileList.clear();
        File[] files = directory.listFiles();
        if (files != null) {
            fileList.addAll(files);
        }
    }

    private void setupWatcher(File directory) {
        try {
            if (watchService != null) {
                watchService.close();
            }
            watchService = FileSystems.getDefault().newWatchService();
            Path path = directory.toPath();
            path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);

            executorService.submit(() -> {
                try {
                    while (true) {
                        WatchKey key = watchService.take();
                        javafx.application.Platform.runLater(() -> loadFiles(directory));
                        key.reset();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openFile(File file) {
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(file);
            } catch (Exception e) {
                showErrorDialog("Nie można otworzyć pliku", "Wystąpił błąd podczas próby otwarcia pliku:\n" + file.getName());
                e.printStackTrace();
            }
        } else {
            showErrorDialog("Funkcja nieobsługiwana", "Twoja platforma nie obsługuje otwierania plików.");
        }
    }

    private void showErrorDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @Override
    public void stop() {
        try {
            if (watchService != null) {
                watchService.close();
            }
            executorService.shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}