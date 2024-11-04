import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class EZParkApp extends Application {

    private WebEngine webEngine;
    private TextField startZipField = new TextField();
    private TextField destZipField = new TextField();
    private ObservableList<String> eventList = FXCollections.observableArrayList("Hershey Park (17033)", "Penn State (16802)", "Pittsburgh (15106)"); // Sample events
    private UserEvents userEvents = new UserEvents(); // to store user's saved events
    private ListView<String> eventListView = new ListView<>(eventList); // Display available events
    private ListView<String> savedEventsListView = new ListView<>(); // Display saved events

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("EZ Park - Event Manager and Route Finder");

        // WebView for the map
        WebView webView = new WebView();
        webEngine = webView.getEngine();
        File mapFile = new File("src/map.html"); // Make sure this is set to the html file location
        webEngine.load(mapFile.toURI().toString());

        // Zip code inputs and Find Route button
        startZipField.setPromptText("Enter Start Zip Code");
        destZipField.setPromptText("Enter Destination Zip Code");
        Button findRouteButton = new Button("Find Route");
        findRouteButton.setOnAction(e -> findRoute());

        // saveEvent button
        Button saveEventButton = new Button("Save Event");
        saveEventButton.setOnAction(e -> saveSelectedEvent());

        // Button to show saved events
        Button showSavedEventsButton = new Button("Show Saved Events");
        showSavedEventsButton.setOnAction(e -> showSavedEvents());

        // Double-click listener to eventListView
        eventListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) {
                String selectedEvent = eventListView.getSelectionModel().getSelectedItem();
                if (selectedEvent != null) {
                    String zipCode = getZipCodeForEvent(selectedEvent);
                    centerMapOnZipCode(zipCode);
                }
            }
        });

        // Double-click listener to savedEventsListView
        savedEventsListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) {
                String selectedEvent = savedEventsListView.getSelectionModel().getSelectedItem();
                if (selectedEvent != null) {
                    String zipCode = getZipCodeForEvent(selectedEvent);
                    centerMapOnZipCode(zipCode);
                }
            }
        });

        // Organize Route and Event UI elements
        HBox routeBox = new HBox(10, startZipField, destZipField, findRouteButton);
        routeBox.setPadding(new Insets(10));

        VBox eventBox = new VBox(10, new Label("Available Events:"), eventListView, saveEventButton, showSavedEventsButton);
        eventBox.setPadding(new Insets(10));

        BorderPane layout = new BorderPane();
        layout.setTop(routeBox);
        layout.setLeft(eventBox);
        layout.setCenter(webView);

        Scene scene = new Scene(layout, 1000, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // Method to center map on a given zip code
    private void centerMapOnZipCode(String zipCode) {
        if (zipCode == null || zipCode.isEmpty()) {
            showError("Invalid zip code for the selected event.");
            return;
        }

        new Thread(() -> {
            double[] coordinates = getCoordinates(zipCode);
            if (coordinates != null) {
                Platform.runLater(() -> webEngine.executeScript("map.setView([" + coordinates[0] + ", " + coordinates[1] + "], 13);"));
            } else {
                showError("Could not find location for the zip code: " + zipCode);
            }
        }).start();
    }

    // Method to find the route between two zip codes
    private void findRoute() {
        String startZip = startZipField.getText();
        String destZip = destZipField.getText();

        if (startZip.isEmpty() || destZip.isEmpty()) {
            showError("Please enter both start and destination zip codes.");
            return;
        }

        // Fetch coordinates for start and destination zip codes
        new Thread(() -> {
            try {
                // Get coordinates for the start zip code
                double[] startCoords = getCoordinates(startZip);
                if (startCoords == null) {
                    showError("Start location not found. Please check the zip code.");
                    return;
                }

                // Get coordinates for the destination zip code
                double[] destCoords = getCoordinates(destZip);
                if (destCoords == null) {
                    showError("Destination location not found. Please check the zip code.");
                    return;
                }

                // Fetch and display the route
                getAndDisplayRoute(startCoords, destCoords);

            } catch (Exception e) {
                showError("Error finding route. Please try again.");
            }
        }).start();
    }

    // Method to get coordinates from a zip code
    private double[] getCoordinates(String zipCode) {
        try {
            String urlString = "https://nominatim.openstreetmap.org/search?postalcode=" + zipCode + "&country=USA&format=json";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // Parse JSON response
            JSONArray jsonArray = new JSONArray(response.toString());
            if (jsonArray.length() > 0) {
                JSONObject location = jsonArray.getJSONObject(0);
                double latitude = location.getDouble("lat");
                double longitude = location.getDouble("lon");
                return new double[]{latitude, longitude};
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null; // Return null if no coordinates found
    }

    // Helper to extract zip code from an event string, e.g., "Event A (94103)" -> "94103"
    private String getZipCodeForEvent(String event) {
        return event.replaceAll(".*\\((\\d{5})\\)", "$1");
    }

    // Helper method to show an error alert
    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // Inner class to manage user's saved events
    private static class UserEvents {
        private List<String> savedEvents = new ArrayList<>();

        public void addEvent(String event) {
            savedEvents.add(event);
        }

        public List<String> getSavedEvents() {
            return savedEvents;
        }
    }

    // Save the selected event from the event list
    private void saveSelectedEvent() {
        String selectedEvent = eventListView.getSelectionModel().getSelectedItem();
        if (selectedEvent != null) {
            userEvents.addEvent(selectedEvent);
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Event saved successfully!", ButtonType.OK);
            alert.showAndWait();
        } else {
            showError("Please select an event to save.");
        }
    }

    private void getAndDisplayRoute(double[] startCoords, double[] destCoords) {
        try {
            // OpenRouteService API
            String urlString = "https://api.openrouteservice.org/v2/directions/driving-car?api_key=5b3ce3597851110001cf624871a7d96a0c9144f49a2175ed9ac49b86&start="
                    + startCoords[1] + "," + startCoords[0] + "&end=" + destCoords[1] + "," + destCoords[0];
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // Parse JSON response to get the route coordinates
            JSONObject jsonResponse = new JSONObject(response.toString());
            JSONArray coordinates = jsonResponse.getJSONArray("features").getJSONObject(0)
                    .getJSONObject("geometry").getJSONArray("coordinates");

            // Display route on map
            Platform.runLater(() -> displayRoute(coordinates));

        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Error fetching route data.");
        }
    }

    // Method to show saved events in a new window
    private void showSavedEvents() {
        // Populate the savedEventsListView with saved events
        savedEventsListView.setItems(FXCollections.observableArrayList(userEvents.getSavedEvents()));

        // Display saved events in a new window
        Stage savedEventsStage = new Stage();
        savedEventsStage.setTitle("My Saved Events");
        BorderPane savedLayout = new BorderPane();
        savedLayout.setCenter(savedEventsListView);

        Scene savedScene = new Scene(savedLayout, 300, 400);
        savedEventsStage.setScene(savedScene);
        savedEventsStage.show();
    }

    private void displayRoute(JSONArray coordinates) {
        StringBuilder routeCoords = new StringBuilder("[");
        for (int i = 0; i < coordinates.length(); i++) {
            JSONArray point = coordinates.getJSONArray(i);
            routeCoords.append("[").append(point.getDouble(1)).append(", ").append(point.getDouble(0)).append("]");
            if (i < coordinates.length() - 1) {
                routeCoords.append(", ");
            }
        }
        routeCoords.append("]");

        // Execute JavaScript in WebView to plot the route
        webEngine.executeScript("plotRoute(" + routeCoords + ");");
    }


    public static void main(String[] args) {
        launch(args);
    }


}
