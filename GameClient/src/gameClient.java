import java.io.*;
import java.net.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.stage.Stage;

public class gameClient extends Application implements TicTacToeConstants {
	private boolean myTurn = false;
	// X and O tokens for players
	private char myToken = ' ';
	private char otherToken = ' ';

	// Create and initialise cells
	private Cell[][] cell = new Cell[3][3];

	// Create and initialise name
	private Label lblTitle = new Label();

	// Create and initialise a status 
	private Label lblStatus = new Label();

	// Create variables for selected cell from the rows and columns
	private int rowSelected;
	private int columnSelected;

	// Input and output streams between server
	private DataInputStream fromServer;
	private DataOutputStream toServer;

	// continue playing
	private boolean continueToPlay = true;

	// Wait for the player to mark a cell
	private boolean waiting = true;

	// Host name or ip
	private String host = "localhost";
	
	
	
	
	
	@Override // Override the start method in the Application class
	public void start(Stage primaryStage) {
		// color for the background
		String style = "-fx-background-color: rgba(0, 255, 0, 0.5);";
		// Pane to hold cell
		GridPane pane = new GridPane();
		for (int i = 0; i < 3; i++)
			for (int j = 0; j < 3; j++)
				pane.add(cell[i][j] = new Cell(i, j), j, i);

		BorderPane borderPane = new BorderPane();
		borderPane.setTop(lblTitle);
		borderPane.setCenter(pane);
		borderPane.setBottom(lblStatus);
		// set the background of the borderPane
		borderPane.setStyle(style);

		// Create a scene and place it in the stage
		Scene scene = new Scene(borderPane, 320, 350);
		primaryStage.setTitle("TicTacToeClient"); // Set the stage title
		primaryStage.setScene(scene); // Place the scene in the stage
		primaryStage.show(); // Display the stage

		
		connectToServer();
	}

	private void connectToServer() {
		try {
			// Create a socket to connect to the server
			@SuppressWarnings("resource") // Suppresses resource leak warning
			Socket socket = new Socket(host, 8022);

			// Create an input stream to receive data from the server
			fromServer = new DataInputStream(socket.getInputStream());

			// Create an output stream to send data to the server
			toServer = new DataOutputStream(socket.getOutputStream());
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		// Control the game on a separate thread
		new Thread(() -> {
			try {
				// Get notification from the server
				int player = fromServer.readInt();

				//Sets token for player1
				if (player == PLAYER1) {
					myToken = 'X';
					otherToken = 'O';
					Platform.runLater(() -> {
						lblTitle.setText("Player 1 with token 'X'");
						lblStatus.setText("Waiting for player 2 to join");
					});
					// Receive startup notification from the server
					fromServer.readInt(); // Whatever read is ignored

					// The other player has joined
					Platform.runLater(() -> lblStatus.setText("Player 2 has joined. I start first"));

					// It is my turn
					myTurn = true;
					//Set token for player2
				} else if (player == PLAYER2) {
					myToken = 'O';
					otherToken = 'X';
					Platform.runLater(() -> {
						lblTitle.setText("Player 2 with token 'O'");
						lblStatus.setText("Waiting for player 1 to move");
					});
				}

				// Continue to play
				while (continueToPlay) {
					if (player == PLAYER1) {
						waitForPlayerAction(); // Wait for player 1 to move
						sendMove(); // Send the move to the server
						receiveInfoFromServer(); // Receive info from the server
					} else if (player == PLAYER2) {
						receiveInfoFromServer(); // Receive info from the server
						waitForPlayerAction(); // Wait for player 2 to move
						sendMove(); // Send player 2's move to the server
					}
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}).start();
	}

	// Wait for the player to mark a cell
	private void waitForPlayerAction() throws InterruptedException {
		while (waiting) {
			Thread.sleep(100);
		}

		waiting = true;
	}

	// Send this player's move to the server 
	private void sendMove() throws IOException {
		toServer.writeInt(rowSelected); // Send the selected row
		toServer.writeInt(columnSelected); // Send the selected column
	}

	// Receive info from the server
	private void receiveInfoFromServer() throws IOException {
		// Receive game status
		int status = fromServer.readInt();

		if (status == PLAYER1_WON) {
			// Player 1 won, stop playing
			continueToPlay = false;
			if (myToken == 'X') {
				Platform.runLater(() -> lblStatus.setText("I won! (X)"));
			} else if (myToken == 'O') {
				Platform.runLater(() -> lblStatus.setText("Player 1 (X) has won!"));
				receiveMove();
			}
		} else if (status == PLAYER2_WON) {
			// Player 2 won, stop playing
			continueToPlay = false;
			if (myToken == 'O') {
				Platform.runLater(() -> lblStatus.setText("I won! (O)"));
			} else if (myToken == 'X') {
				Platform.runLater(() -> lblStatus.setText("Player 2 (O) has won!"));
				receiveMove();
			}
		} else if (status == DRAW) {
			// No winner, game is over
			continueToPlay = false;
			Platform.runLater(() -> lblStatus.setText("Game is over, no winner!"));

			if (myToken == 'O') {
				receiveMove();
			}
		} else {
			receiveMove();
			Platform.runLater(() -> lblStatus.setText("My turn"));
			myTurn = true; // It is my turn
		}
	}

	private void receiveMove() throws IOException {
		// Get the other player's move
		int row = fromServer.readInt();
		int column = fromServer.readInt();
		Platform.runLater(() -> cell[row][column].setToken(otherToken));
	}

	// An inner class for a cell
	public class Cell extends Pane {
		// Indicate the row and column of this cell in the board
		private int row;
		private int column;

		// Token used for this cell
		private char token = ' ';

		public Cell(int row, int column) {
			this.row = row;
			this.column = column;
			this.setPrefSize(2000, 2000); // What happens without this?
			setStyle("-fx-border-color: black"); // Set cell's border
			this.setOnMouseClicked(e -> handleMouseClick());
		}

		// Return token
		public char getToken() {
			return token;
		}

		// Set a new token
		public void setToken(char c) {
			token = c;
			repaint();
		}

		// Repaint facilitates X and O tokens on pane in regards to which players turn it is and draws the respective token
		protected void repaint() {
			// randomize red, blue and stroke width that will be used for the tokens
			int red = (int) (Math.random()*255)+1;
			int green = 0;
			int blue = (int) (Math.random()*255)+1;
			int strokeWidth = (int)(Math.random()*8)+1;
			if (token == 'X') {
				Line line1 = new Line(10, 10, this.getWidth() - 10, this.getHeight() - 10);
				line1.endXProperty().bind(this.widthProperty().subtract(10));
				line1.endYProperty().bind(this.heightProperty().subtract(10));
				Line line2 = new Line(10, this.getHeight() - 10, this.getWidth() - 10, 10);
				line2.startYProperty().bind(this.heightProperty().subtract(10));
				line2.endXProperty().bind(this.widthProperty().subtract(10));
				
				//setting the color and stroke of the X
				line1.setStroke(Color.rgb(red, green, blue));
				line1.setStrokeWidth(strokeWidth);
				line2.setStroke(Color.rgb(red, green, blue));
				line2.setStrokeWidth(strokeWidth);
				
				this.getChildren().addAll(line1, line2); // Add the lines to the pane
				
			} else if (token == 'O') {		
				Ellipse ellipse = new Ellipse(this.getWidth() / 2, this.getHeight() / 2, this.getWidth() / 2 - 10,
						this.getHeight() / 2 - 10);
				ellipse.centerXProperty().bind(this.widthProperty().divide(2));
				ellipse.centerYProperty().bind(this.heightProperty().divide(2));
				ellipse.radiusXProperty().bind(this.widthProperty().divide(2).subtract(10));
				ellipse.radiusYProperty().bind(this.heightProperty().divide(2).subtract(10));
				//setting the color of the stroke of the eclipse
				ellipse.setStroke(Color.rgb(red, green, blue));
				//changing the stroke of the eclipse
				ellipse.setStrokeWidth(strokeWidth);
				// setting the filling of the eclipse to be transparent
				ellipse.setFill(Color.TRANSPARENT);

				getChildren().add(ellipse); // Add the ellipse to the pane
			}
		}

		// Handle a mouse click event
		private void handleMouseClick() {
			// If cell is not occupied and the player has the turn
			if (token == ' ' && myTurn) {
				setToken(myToken); // Set the player's token in the cell
				myTurn = false;
				rowSelected = row;
				columnSelected = column;
				lblStatus.setText("Waiting for the other player to move");
				waiting = false; // Just completed a successful move
			}
		}
	}
}