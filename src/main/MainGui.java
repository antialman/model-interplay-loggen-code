package main;

import controller.LogGenViewController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class MainGui extends Application {
	
	private static Scene scene;
	
	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		String fxmlPath = "fxml/LogGenView.fxml";
		FXMLLoader fxmlLoader = new FXMLLoader(MainGui.class.getClassLoader().getResource(fxmlPath));
		Parent parent = fxmlLoader.load();
		((LogGenViewController)fxmlLoader.getController()).setStage(primaryStage);
		scene = new Scene(parent);
		scene.getStylesheets().add("css/main.css");
		
		primaryStage.setTitle("model-interplay-monitor");
		primaryStage.setScene(scene);
		//Setting minimum window size to 800*600px
		primaryStage.setMinWidth(800);
		primaryStage.setMinHeight(600);
		primaryStage.setMaximized(true);
		primaryStage.show();
		
		primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
			@Override
			public void handle(WindowEvent t) {
				Platform.exit();
				System.exit(0);
			}
		});
	}

}
