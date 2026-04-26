package com.pharmax.controller;

import com.pharmax.model.User;
import com.pharmax.model.UserRole;
import com.pharmax.service.AuthService;
import com.pharmax.util.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class LoginController {
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);
    
    @FXML private VBox loginContainer;
    @FXML private Label titleLabel;
    @FXML private FlowPane userBubblesPane;
    @FXML private HBox selectedUserBox;
    @FXML private Label selectedUserIcon;
    @FXML private Label selectedUserLabel;
    @FXML private Label selectedUserRoleLabel;
    @FXML private VBox pinBox;
    @FXML private TextField usernameField;
    @FXML private VBox usernameBox;
    @FXML private PasswordField pinField;
    @FXML private CheckBox rememberCheckBox;
    @FXML private Button loginButton;
    @FXML private Button switchUserButton;
    @FXML private Label errorLabel;
    @FXML private Label statusLabel;
    
    private final AuthService authService = new AuthService();
    private Runnable onLoginSuccess;
    private User selectedUser;
    private VBox selectedBubbleNode;
    
    private static final String[] BUBBLE_COLORS = {
        "#2563eb", "#7c3aed", "#059669", "#d97706", "#dc2626", "#0891b2", "#4f46e5", "#be185d"
    };
    
    @FXML
    private void initialize() {
        loadUserBubbles();
        
        // Enter key triggers login
        pinField.setOnAction(e -> handleLogin());
        usernameField.setOnAction(e -> {
            if (!usernameField.getText().isEmpty()) {
                pinField.requestFocus();
            }
        });
        
        // Auto-select last remembered user
        String lastUsername = SessionManager.getInstance().getLastUsername();
        if (lastUsername != null && !lastUsername.isEmpty()) {
            Optional<User> userOpt = authService.getUserByUsername(lastUsername);
            userOpt.ifPresent(this::selectUser);
        }
    }
    
    private void loadUserBubbles() {
        userBubblesPane.getChildren().clear();
        List<User> activeUsers = authService.getActiveUsers();
        
        for (int i = 0; i < activeUsers.size(); i++) {
            User user = activeUsers.get(i);
            String color = BUBBLE_COLORS[i % BUBBLE_COLORS.length];
            VBox bubble = createUserBubble(user, color);
            userBubblesPane.getChildren().add(bubble);
        }
    }
    
    private VBox createUserBubble(User user, String color) {
        // Avatar circle
        Circle circle = new Circle(32);
        circle.setFill(Color.web(color));
        circle.setEffect(new DropShadow(8, Color.web(color, 0.4)));
        
        // Initials text
        String initials = getInitials(user.getDisplayName());
        Text initialsText = new Text(initials);
        initialsText.setFill(Color.WHITE);
        initialsText.setFont(Font.font("System", FontWeight.BOLD, 18));
        
        // Stack circle + initials
        javafx.scene.layout.StackPane avatar = new javafx.scene.layout.StackPane(circle, initialsText);
        avatar.setMinSize(64, 64);
        avatar.setMaxSize(64, 64);
        
        // Name label
        Label nameLabel = new Label(user.getDisplayName());
        nameLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -fx-text-main; -fx-font-weight: bold;");
        nameLabel.setMaxWidth(80);
        nameLabel.setAlignment(Pos.CENTER);
        nameLabel.setWrapText(true);
        
        // Role label (larger to emphasize role)
        Label roleLabel = new Label(user.getRole().getDisplayName());
        String roleColor = user.getRole() == UserRole.ADMIN ? "-fx-danger-text" : "-fx-text-muted";
        roleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + roleColor + "; -fx-font-weight: bold;");
        
        // Container
        VBox bubbleBox = new VBox(5);
        bubbleBox.setAlignment(Pos.TOP_CENTER);
        bubbleBox.setStyle("-fx-padding: 8; -fx-background-radius: 12; -fx-cursor: hand;");
        bubbleBox.setPrefWidth(90);
        bubbleBox.getChildren().addAll(avatar, nameLabel, roleLabel);
        bubbleBox.setCursor(Cursor.HAND);
        
        // Hover effect
        bubbleBox.setOnMouseEntered(e -> {
            if (bubbleBox != selectedBubbleNode) {
                bubbleBox.setStyle("-fx-padding: 8; -fx-background-radius: 12; -fx-background-color: -fx-accent-bg; -fx-cursor: hand;");
            }
            circle.setScaleX(1.1);
            circle.setScaleY(1.1);
        });
        bubbleBox.setOnMouseExited(e -> {
            if (bubbleBox != selectedBubbleNode) {
                bubbleBox.setStyle("-fx-padding: 8; -fx-background-radius: 12; -fx-cursor: hand;");
            }
            circle.setScaleX(1.0);
            circle.setScaleY(1.0);
        });
        
        // Click to select
        bubbleBox.setOnMouseClicked(e -> {
            selectUser(user);
            highlightBubble(bubbleBox, color);
        });
        
        // Store user reference
        bubbleBox.setUserData(user);
        
        return bubbleBox;
    }
    
    private String getInitials(String displayName) {
        if (displayName == null || displayName.isEmpty()) return "?";
        String[] parts = displayName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length()));
        }
        return String.valueOf(parts[0].charAt(0)) + parts[parts.length - 1].charAt(0);
    }
    
    private void highlightBubble(VBox bubbleBox, String color) {
        // Reset all bubbles
        for (javafx.scene.Node node : userBubblesPane.getChildren()) {
            if (node instanceof VBox) {
                ((VBox) node).setStyle("-fx-padding: 8; -fx-background-radius: 12; -fx-cursor: hand;");
            }
        }
        // Highlight selected
        bubbleBox.setStyle("-fx-padding: 8; -fx-background-radius: 12; -fx-background-color: -fx-accent-bg; -fx-border-color: " + color + "; -fx-border-width: 2; -fx-border-radius: 10; -fx-cursor: hand;");
        selectedBubbleNode = bubbleBox;
    }
    
    private void selectUser(User user) {
        selectedUser = user;
        clearError();
        
        // Show selected user info
        selectedUserIcon.setText(getInitials(user.getDisplayName()));
        selectedUserIcon.setStyle("-fx-font-size: 20px; -fx-min-width: 48; -fx-min-height: 48; -fx-max-width: 48; -fx-max-height: 48; " +
                "-fx-alignment: CENTER; -fx-background-radius: 24; -fx-background-color: -fx-accent-color; -fx-text-fill: white; -fx-font-weight: bold;");
        selectedUserLabel.setText(user.getDisplayName());
        selectedUserRoleLabel.setText(user.getRole().getDisplayName());
        
        selectedUserBox.setVisible(true);
        selectedUserBox.setManaged(true);
        
        // Show PIN field and login button
        pinBox.setVisible(true);
        pinBox.setManaged(true);
        loginButton.setVisible(true);
        loginButton.setManaged(true);
        switchUserButton.setVisible(false);
        switchUserButton.setManaged(false);
        
        // Hide username field
        usernameBox.setVisible(false);
        usernameBox.setManaged(false);
        
        titleLabel.setText("مرحباً بعودتك");
        
        pinField.clear();
        Platform.runLater(() -> pinField.requestFocus());
        
        // Highlight the correct bubble
        int idx = 0;
        for (javafx.scene.Node node : userBubblesPane.getChildren()) {
            if (node instanceof VBox && node.getUserData() == user) {
                String color = BUBBLE_COLORS[idx % BUBBLE_COLORS.length];
                highlightBubble((VBox) node, color);
                break;
            }
            idx++;
        }
    }
    
    @FXML
    private void handleLogin() {
        clearError();
        
        String username;
        if (selectedUser != null) {
            username = selectedUser.getUsername();
        } else {
            username = usernameField.getText().trim();
        }
        String pin = pinField.getText();
        
        if (username.isEmpty()) {
            showError("الرجاء اختيار مستخدم أو إدخال اسم المستخدم");
            return;
        }
        
        if (pin.isEmpty()) {
            showError("الرجاء إدخال الرمز");
            return;
        }
        
        // Attempt authentication
        loginButton.setDisable(true);
        statusLabel.setText("جاري التحقق...");
        
        Optional<User> result = authService.authenticate(username, pin);
        
        if (result.isPresent()) {
            User user = result.get();
            boolean remember = rememberCheckBox.isSelected();
            
            SessionManager.getInstance().startSession(user, remember);
            statusLabel.setText("تم تسجيل الدخول بنجاح!");
            
            logger.info("Login successful for user: {}", username);
            
            if (onLoginSuccess != null) {
                Platform.runLater(onLoginSuccess);
            }
        } else {
            loginButton.setDisable(false);
            statusLabel.setText("");
            
            // Check why login failed
            Optional<User> userCheck = authService.getUserByUsername(username);
            if (userCheck.isEmpty()) {
                showError("اسم المستخدم غير موجود");
            } else if (!userCheck.get().isActive()) {
                showError("الحساب غير مفعّل");
            } else {
                showError("الرمز غير صحيح");
            }
            
            pinField.clear();
            pinField.requestFocus();
        }
    }
    
    @FXML
    private void handleSwitchUser() {
        selectedUser = null;
        selectedBubbleNode = null;
        
        // Reset all bubbles
        for (javafx.scene.Node node : userBubblesPane.getChildren()) {
            if (node instanceof VBox) {
                ((VBox) node).setStyle("-fx-padding: 8; -fx-background-radius: 12; -fx-cursor: hand;");
            }
        }
        
        selectedUserBox.setVisible(false);
        selectedUserBox.setManaged(false);
        pinBox.setVisible(false);
        pinBox.setManaged(false);
        loginButton.setVisible(false);
        loginButton.setManaged(false);
        switchUserButton.setVisible(false);
        switchUserButton.setManaged(false);
        usernameBox.setVisible(false);
        usernameBox.setManaged(false);
        
        titleLabel.setText("اختر حسابك");
        pinField.clear();
        clearError();
        statusLabel.setText("");
    }
    
    @FXML
    private void handleExit() {
        Platform.exit();
        System.exit(0);
    }
    
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
    
    private void clearError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
    
    public void setOnLoginSuccess(Runnable callback) {
        this.onLoginSuccess = callback;
    }
}
