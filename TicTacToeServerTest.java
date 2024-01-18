// Example : TicTacToeServerTest.java
// Tests the TicTacToeServer.
import javax.swing.JFrame;

public class TicTacToeServerTest {
    public static void main(String args[]) {
        TicTacToeServer application = new TicTacToeServer();
        application.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        application.setResizable(false);
        application.execute();
    } // end main
} // end class TicTacToeServerTest