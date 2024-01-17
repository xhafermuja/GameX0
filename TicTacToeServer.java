import java.awt.BorderLayout;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.util.Formatter;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class TicTacToeServer extends JFrame {
    private String[] board = new String[9]; // tic-tac-toe board
    private JTextArea outputArea; // for outputting moves
    private Player[] players; // array of Players
    private ServerSocket server; // server socket to connect with clients
    private int currentPlayer; // keeps track of the player with the current move
    private final static int PLAYER_X = 0; // constant for the first player
    private final static int PLAYER_O = 1; // constant for the second player
    private final static String[] MARKS = {"X", "O"}; // array of marks
    private ExecutorService runGame; // will run players
    private Lock gameLock; // to lock the game for synchronization
    private Condition otherPlayerConnected; // to wait for the other player
    private Condition otherPlayerTurn; // to wait for the other player's turn
    private boolean gameOver = false;

    // set up tic-tac-toe server and GUI that displays messages
    public TicTacToeServer() {
        super("Tic-Tac-Toe Server"); // set the title of the window

        // create ExecutorService with a thread for each player
        runGame = Executors.newFixedThreadPool(2);
        gameLock = new ReentrantLock(); // create a lock for the game

        // condition variable for both players being connected
        otherPlayerConnected = gameLock.newCondition();

        // condition variable for the other player's turn
        otherPlayerTurn = gameLock.newCondition();

        for (int i = 0; i < 9; i++)
            board[i] = new String(""); // create the tic-tac-toe board
        players = new Player[2]; // create an array of players
        currentPlayer = PLAYER_X; // set the current player to the first player

        try {
            server = new ServerSocket(12345, 2); // set up ServerSocket
        } catch (IOException ioException) {
            ioException.printStackTrace();
            System.exit(1);
        }

        outputArea = new JTextArea(); // create JTextArea for output
        add(outputArea, BorderLayout.CENTER);
        outputArea.setText("Server awaiting connections\n");

        setSize(300, 300); // set the size of the window
        setVisible(true); // show the window
    }

    // wait for two connections so the game can be played
    public void execute() {
        // wait for each client to connect
        for (int i = 0; i < players.length; i++) {
            try // wait for connection, create Player, start runnable
            {
                players[i] = new Player(server.accept(), i);
                runGame.execute(players[i]); // execute player runnable
            } catch (IOException ioException) {
                ioException.printStackTrace();
                System.exit(1);
            }
        }

        gameLock.lock(); // lock the game to signal player X's thread
        try {
            players[PLAYER_X].setSuspended(false); // resume player X
            otherPlayerConnected.signal(); // wake up player X's thread
        } finally {
            gameLock.unlock(); // unlock the game after signaling player X
        }
    }

    // display a message in outputArea
    private void displayMessage(final String messageToDisplay) {
        // display the message from the event-dispatch thread of execution
        SwingUtilities.invokeLater(() -> outputArea.append(messageToDisplay));
    }

    // determine if the move is valid
    public boolean validateAndMove(int location, int player) {
        // while not the current player, must wait for the turn
        while (player != currentPlayer) {
            gameLock.lock(); // lock the game to wait for the other player to go
            try {
                otherPlayerTurn.await(); // wait for the player's turn
            } catch (InterruptedException exception) {
                exception.printStackTrace();
            } finally {
                gameLock.unlock(); // unlock the game after waiting
            }
        }

        // if the location is not occupied, make a move
        if (!isOccupied(location)) {
            board[location] = MARKS[currentPlayer]; // set the move on the board
            currentPlayer = (currentPlayer + 1) % 2; // change the player

            // let the new current player know that the move occurred
            players[currentPlayer].otherPlayerMoved(location);

            gameLock.lock(); // lock the game to signal the other player to go
            try {
                otherPlayerTurn.signal(); // signal the other player to continue
            } finally {
                gameLock.unlock(); // unlock the game after signaling
            }

            return true; // notify the player that the move was valid
        } else // the move was not valid
            return false; // notify the player that the move was invalid
    }

    // determine whether the location is occupied
    public boolean isOccupied(int location) {
        return !board[location].isEmpty();
    }

    // place code in this method to determine whether the game is over
    public boolean isGameOver() {
        boolean winnerX = isWinner(MARKS[PLAYER_X]);
        boolean winnerO = isWinner(MARKS[PLAYER_O]);
        boolean boardFull = isBoardFull();

        if (winnerX || winnerO || boardFull) {
            gameOver = true;
            displayMessage("Game Over!\n");
            if (winnerX) {
                displayMessage("Player X wins!\n");
            } else if (winnerO) {
                displayMessage("Player O wins!\n");
            } else {
                displayMessage("It's a tie!\n");
            }
        }

        return gameOver;
    }

    // check if the board is full (a tie)
    private boolean isBoardFull() {
        for (int i = 0; i < 9; i++) {
            if (board[i].isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // check if there is a winner
    private boolean isWinner(String mark) {
        // Check rows
        for (int i = 0; i < 9; i += 3) {
            if (board[i].equals(mark) && board[i + 1].equals(mark) && board[i + 2].equals(mark)) {
                return true;
            }
        }

        // Check columns
        for (int i = 0; i < 3; i++) {
            if (board[i].equals(mark) && board[i + 3].equals(mark) && board[i + 6].equals(mark)) {
                return true;
            }
        }

        // Check diagonals
        if (board[0].equals(mark) && board[4].equals(mark) && board[8].equals(mark)) {
            return true;
        }
        if (board[2].equals(mark) && board[4].equals(mark) && board[6].equals(mark)) {
            return true;
        }

        return false;
    }

    // private inner class Player manages each Player as a runnable
    private class Player implements Runnable {
        private Socket connection; // connection to the client
        private Scanner input; // input from the client
        private Formatter output; // output to the client
        private int playerNumber; // tracks which player this is
        private String mark; // mark for this player
        private boolean suspended = true; // whether the thread is suspended

        // set up Player thread
        public Player(Socket socket, int number) {
            playerNumber = number; // store this player's number
            mark = MARKS[playerNumber]; // specify the player's mark
            connection = socket; // store the socket for the client

            try // obtain streams from Socket
            {
                input = new Scanner(connection.getInputStream());
                output = new Formatter(connection.getOutputStream());

            } catch (IOException ioException) {
                ioException.printStackTrace();
                System.exit(1);
            }
        }

        // send a message that the other player moved
        public void otherPlayerMoved(int location) {
            output.format("Opponent moved\n");
            output.format("%d\n", location); // send the location of the move
            output.flush(); // flush output
        }

        // control thread's execution
        public void run() {
            // send the client its mark (X or O), process messages from the client
            try {
                displayMessage("Player " + mark + " connected\n");
                output.format("%s\n", mark); // send the player's mark
                output.flush(); // flush output

                // if player X, wait for another player to arrive
                if (playerNumber == PLAYER_X) {
                    output.format("%s\n%s", "Player X connected",
                            "Waiting for another player\n");
                    output.flush(); // flush output

                    gameLock.lock(); // lock the game to wait for the second player
                    try {
                        while (suspended) {
                            otherPlayerConnected.await(); // wait for player O
                        }

                    } catch (InterruptedException exception) {
                        exception.printStackTrace();
                    } finally {
                        gameLock.unlock(); // unlock the game after the second player
                    }

                    // send a message that the other player connected
                    output.format("Other player connected. Your move.\n");
                    output.flush(); // flush output

                } else {
                    output.format("Player O connected, please wait\n");
                    output.flush(); // flush output
                }

                // while the game is not over
                while (!isGameOver()) {
                    int location = 0; // initialize the move location

                    if (input.hasNext())
                        location = input.nextInt(); // get the move location

                    // check for a valid move
                    if (validateAndMove(location, playerNumber)) {
                        displayMessage("\nlocation: " + location);
                        output.format("Valid move.\n"); // notify the client
                        output.flush(); // flush output
                    } else {
                        output.format("Invalid move, try again\n");
                        output.flush(); // flush output
                    }
                }
            } finally {
                try {
                    connection.close(); // close the connection to the client
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                    System.exit(1);
                }
            }
        }

        // set whether the thread is suspended
        public void setSuspended(boolean status) {
            suspended = status; // set the value of suspended
        }
    } // end class Player

    // Main method to run the server
    public static void main(String[] args) {
        TicTacToeServer server = new TicTacToeServer();
        server.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        server.execute(); // run the server application
    }
}
