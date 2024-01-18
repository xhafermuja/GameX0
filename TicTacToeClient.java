// Example : TicTacToeClient.java
// Client that let a user play Tic-Tac-Toe with another across a network.
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.Socket;
import java.net.InetAddress;
import java.io.IOException;
import javax.swing.*;
import java.util.Formatter;
import java.util.Scanner;
import java.util.concurrent.*;

public class TicTacToeClient extends JFrame implements Runnable {
    private JTextField idField; // textfield to display player's mark
    private JTextArea displayArea; // JTextArea to display output
    private JPanel boardPanel; // panel for tic-tac-toe board
    private JPanel panel2; // panel to hold board
    private Square board[][]; // tic-tac-toe board
    private Square currentSquare; // current square
    private Socket connection; // connection to server
    private Scanner input; // input from server
    private Formatter output; // output to server
    private String ticTacToeHost; // host name for server
    private String myMark; // this client's mark
    private boolean myTurn; // determines which client's turn it is
    private final String X_MARK = "X"; // mark for first client
    private final String O_MARK = "O"; // mark for second client

    private String finalPlayerName; // Declare finalPlayerName as a class variable

    private int playerTimer = 60;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> timerHandle;
    private JLabel timerLabel;


    // set up user-interface and board
    public TicTacToeClient(String host) {
        ticTacToeHost = host; // set name of server
        displayArea = new JTextArea(4, 30); // set up JTextArea
        displayArea.setEditable(false);
        add(new JScrollPane(displayArea), BorderLayout.SOUTH);

        boardPanel = new JPanel(); // set up panel for squares in board
        boardPanel.setLayout(new GridLayout(3, 3, 0,0 ));

        board = new Square[3][3]; // create board

        // Create and initialize the timer label
        timerLabel = new JLabel("Time left: " + playerTimer);
        add(timerLabel, BorderLayout.NORTH);

        // Prompt the user for their name using JOptionPane
        String playerName = JOptionPane.showInputDialog("Enter your name:");
        if (playerName == null || playerName.trim().isEmpty()) {
            playerName = "Player"; // Default name if the user cancels or enters an empty string
        }

        finalPlayerName = playerName;

        displayArea.append("Welcome, " + finalPlayerName + "!\n");

        // loop over the rows in the board
        for (int row =0 ; row < board.length; row++) {
            // loop over the columns in the board
            for (int column =0 ; column < board[row].length; column++) {
                // create square
                board[row][column] = new Square("", row *3 +column);
                boardPanel.add(board[row][column]); // add square
            } // end inner for
        } // end outer for

        idField = new JTextField(); // set up textfield
        idField.setEditable(false);
        add(idField, BorderLayout.NORTH);

        panel2 = new JPanel(); // set up panel to contain boardPanel
        panel2.add(boardPanel, BorderLayout.CENTER); // add board panel
        panel2.add(timerLabel, BorderLayout.NORTH); // add timerLabel to the top of the panel
        add(panel2, BorderLayout.CENTER); // add container panel


        setSize(400, 350); // set size of window
        setLocationRelativeTo(null);// center the window on the screen
        setVisible(true); // show window

        // Initialize the scheduler
        scheduler = Executors.newScheduledThreadPool(1);

        startClient();
    } // end TicTacToeClient constructor

    // start the client thread
    public void startClient() {
        try // connect to server, get streams and start outputThread
        {
            // make connection to server
            connection = new Socket(
                    InetAddress.getByName(ticTacToeHost), 12345);
            // Start the timer for the current player
            startTimer();

            // get streams for input and output
            input = new Scanner(connection.getInputStream());
            output = new Formatter(connection.getOutputStream());
        } // end try
        catch (IOException ioException) {
            ioException.printStackTrace();
        } // end catch

        // create and start worker thread for this client
        ExecutorService worker = Executors.newFixedThreadPool(1);
        worker.execute(this); // execute client
    } // end method startClient

    // start the timer for the current player
    // start the timer for the current player
    // start the timer for the current player
    private void startTimer() {
        timerHandle = scheduler.scheduleAtFixedRate(() -> {
            SwingUtilities.invokeLater(() -> {
                if (myTurn) {
                    playerTimer--;
                    timerLabel.setText("Time left: " + playerTimer);

                    if (playerTimer <= 0) {
                        // Player has run out of time, handle accordingly
                        handlePlayerTimeout();
                    }
                }
            });
        }, 0, 1, TimeUnit.SECONDS);
    }




    // Method to handle player timeout
    private void handlePlayerTimeout() {
        // Stop the timer
        timerHandle.cancel(true);

        // Display a message or take appropriate action
        JOptionPane.showMessageDialog(this, "You have run out of time. You lose!");

        // Close the connection or perform any necessary actions
        closeConnection();

        // Optionally, you may want to exit the application or perform other cleanup
        System.exit(0);
    }

    // Close the connection and cleanup resources
    private void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // control thread that allows continuous update of displayArea
    public void run() {
        myMark = input.nextLine(); // get player's mark (X or O)

        SwingUtilities.invokeLater(
                new Runnable() {
                    public void run() {
                        // display player's mark
                        idField.setText("You are player \"" + myMark + "\"" + " , username: " + finalPlayerName);
                    } // end method run
                } // end anonymous inner class
        ); // end call to SwingUtilities.invokeLater

        myTurn = (myMark.equals(X_MARK)); // determine if client's turn

        // receive messages sent to client and output them
        while (true) {
            if (input.hasNextLine())
                processMessage(input.nextLine());
        } // end while
    } // end method run

    // process messages received by client
    private void processMessage(String message) {
        // valid move occurred
        if (message.equals("Valid move.")) {
            displayMessage("Valid move, please wait.\n");
            setMark(currentSquare, myMark); // set mark in square
        } // end if
        else if (message.equals("Invalid move, try again")) {
            displayMessage(message + "\n"); // display invalid move
            myTurn = true; // still this client's turn
        } // end else if
        else if (message.equals("Opponent moved")) {
            int location = input.nextInt(); // get move location
            input.nextLine(); // skip newline after int location
            int row = location / 3; // calculate row
            int column = location % 3; // calculate column

            setMark(board[row][column],
                    (myMark.equals(X_MARK) ? O_MARK : X_MARK)); // mark move
            displayMessage("Opponent moved. Your turn.\n");
            myTurn = true; // now this client's turn
        } // end else if
        else
            displayMessage(message + "\n"); // display the message
    } // end method processMessage



    // manipulate outputArea in event-dispatch thread
    private void displayMessage(final String messageToDisplay) {
        SwingUtilities.invokeLater(
                new Runnable() {
                    public void run() {
                        displayArea.append(messageToDisplay); // updates output
                    } // end method run
                } // end inner class
        ); // end call to SwingUtilities.invokeLater
    } // end method displayMessage

    // utility method to set mark on board in event-dispatch thread
    private void setMark(final Square squareToMark, final String mark) {
        SwingUtilities.invokeLater(
                new Runnable() {
                    public void run() {
                        squareToMark.setMark(mark); // set mark in square
                    } // end method run
                } // end anonymous inner class
        ); // end call to SwingUtilities.invokeLater
    } // end method setMark

    // send message to server indicating clicked square
    public void sendClickedSquare(int location) {
        // if it is my turn
        if (myTurn) {
            output.format("%d\n", location); // send location to server
            output.flush();
            myTurn = false; // not my turn anymore
        } // end if
    } // end method sendClickedSquare

    // set current Square
    public void setCurrentSquare(Square square) {
        currentSquare = square; // set current square to argument
    } // end method setCurrentSquare

    // private inner class for the squares on the board
    private class Square extends JPanel {
        private String mark; // mark to be drawn in this square
        private int location; // location of square

        public Square(String squareMark, int squareLocation) {
            mark = squareMark; // set mark for this square
            location = squareLocation; // set location of this square

            addMouseListener(
                    new MouseAdapter() {
                        public void mouseReleased(MouseEvent e) {
                            setCurrentSquare(Square.this); // set current square

                            // send location of this square
                            sendClickedSquare(getSquareLocation());
                        } // end method mouseReleased
                    } // end anonymous inner class
            ); // end call to addMouseListener
        } // end Square constructor

        // return preferred size of Square
        public Dimension getPreferredSize() {
            return new Dimension(70,70 ); // return preferred size
        } // end method getPreferredSize

        // return minimum size of Square
        public Dimension getMinimumSize() {
            return getPreferredSize(); // return preferred size
        } // end method getMinimumSize

        // set mark for Square
        public void setMark(String newMark) {
            mark = newMark; // set mark of square
            repaint(); // repaint square
        } // end method setMark

        // return Square location
        public int getSquareLocation() {
            return location; // return location of square
        } // end method getSquareLocation

        // draw Square
        public void paintComponent(Graphics g) {
            super.paintComponent(g);

            g.drawRect(0, 0, 67, 67); // draw larger square
            if (mark.equals(X_MARK)) {
                g.setColor(Color.RED); // Set color to red for "X"
            } else if(mark.equals(O_MARK)) {
                g.setColor(Color.BLUE); // Set color to black for "O"
            }
            g.setFont(new Font("Arial", Font.PLAIN, 40)); // set font size to 40
            g.drawString(mark, 20, 50); // draw mark with updated font size
        } // end method paintComponent

    } // end inner-class Square
} // end class TicTacToeClient