import java.io.IOException;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

class Person {
    private int ID;

    Person(){
        this.ID = -1;
    }
    Person(int id){
        this.ID = id;
    }
    public void setID(int id){
        this.ID = id;
    }
    public int getID(){
        return this.ID;
    }
}


class ClientUDP{
    private static Person p;
    private static DatagramPacket packet;
    private static DatagramSocket socket;
    private static InetSocketAddress challengerSocketAddress=null;

    ClientUDP(){
        p = new Person();
    }

    public void initializeConnection() throws Exception{
        socket = new DatagramSocket();
        socket.setSoTimeout(2000);
        byte[] buffer = new byte[1024];
        packet = new DatagramPacket(buffer, buffer.length);
        packet.setAddress(InetAddress.getByName("localhost"));
        packet.setPort(9876);
    }


    public int getClientID() throws IOException {
        String message = "Request client ID";
        messageSender(message);

        // receive client ID
        message = messageReceiver();
        int clientId = Integer.parseInt(message.split(" ")[4]);
        p.setID(clientId);
        System.out.println("Received client ID: " + clientId);
        return clientId;
    }

    public String[] getActiveClientsList() throws IOException {
        String message = "Request active clients:"+p.getID();
        messageSender(message);
        message = messageReceiver();
        String[] clientIds = message.split("\n");
       // System.out.println("Active clients:\n" + String.join("\n", clientIds));
        return clientIds;
    }


    public int getPersonId(){
        return p.getID();
    }

    public void disconnectClient() throws IOException {
        String message = "Disconnect client " + p.getID();
        messageSender(message);
        socket.close();
    }
    public void challengeClient(int clientId) throws IOException {
        String message = "Challenge client:" + p.getID() + ":" + clientId;
        messageSender(message);
    }

    public void acceptChallenge(int challengerId) throws Exception {
        messageSender("Challenge accepted:"+challengerId+":"+p.getID());
        String message = messageReceiver();
        InetAddress challengerAddress =  InetAddress.getByName(message.split("//")[0].replace("/",""));
        int challengerPort = Integer.parseInt(message.split("//")[1]) ;
        challengerSocketAddress = new InetSocketAddress(challengerAddress,challengerPort);
        this.transferP2PMessage("Initialize new connection"+ "/127.0.0.1" + "//" + socket.getLocalPort());
    }

    public String messageReceiver() throws IOException {
        byte[] receiveData = new byte[1024];
        String message;
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        socket.receive(receivePacket);
        message = new String(receivePacket.getData()).trim();
        return message;
    }

    public void messageSender(String message) throws IOException {
        byte[] request;
        request = message.getBytes();
        packet.setData(request);
        packet.setLength(request.length);
        socket.send(packet);
    }
    public void setChallengerSocketAddress(InetSocketAddress socketAddress){
        challengerSocketAddress = socketAddress;
    }

    public InetSocketAddress getChallengerSocketAddress(){
        return challengerSocketAddress;
    }

    public void transferP2PMessage( String message) throws Exception {
//        InetSocketAddress ownAddress = new InetSocketAddress("localhost", 9877);
//        socket.bind(ownAddress);
        //System.out.println("Establishing connection with client at port " + peerAddress.getPort());
        byte[] buffer = message.getBytes();
        packet = new DatagramPacket(buffer, buffer.length, challengerSocketAddress);
        socket.send(packet);
       // socket.close();
        }
}


class gameUI {
    private JFrame frame;
    private JList<String> clientList;
    private String challengedClientId;
    private ClientUDP client;
    JButton[] buttons;
    JLabel titleLabel, turnLabel;
    private boolean playerTurn;


    public gameUI() {
        client = new ClientUDP();
    }

    public void messageParser(String Message) throws Exception {
        if (Message.startsWith("Challenged received from")) {
            int challengerId = Integer.parseInt(Message.split(":")[1]);
           if(displayChallengeAcceptor(challengerId)){
                client.acceptChallenge(challengerId);
           }
        }
        else if(Message.startsWith("Initialize new connection")){
            InetAddress receiverAddress = InetAddress.getByName(Message.split("/")[1].split("//")[0]);
            int receiverPort = Integer.parseInt(Message.split("//")[1]);
            client.setChallengerSocketAddress(new InetSocketAddress(receiverAddress,receiverPort));
            client.transferP2PMessage("Connection established");
            frame.dispose();
            this.displayGrid();
        }
        else if (Message.startsWith("Connection established")){
            frame.dispose();
            this.displayGrid();
        }
        else if(Message.startsWith("O") || Message.startsWith("X")){
            String selectedTile = Message.split(":")[0];
            int index = Integer.parseInt(Message.split(":")[1]);
            buttons[index].setText(selectedTile);
            if (selectedTile.equals("X")) {
                turnLabel.setText("Player 2's turn");
            } else {
                turnLabel.setText("Player 1's turn");
            }
            checkWin(selectedTile);
            setPlayerTurn(true);
        }
    }

    public void display() throws Exception {
        client.initializeConnection();
        frame = new JFrame(String.valueOf("Client" + client.getClientID()));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 300);

        clientList = new JList<>();
        clientList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);


        // Initialize the button and set its action listener
        JButton button = new JButton("Send Challenge");
        JLabel label1 = new JLabel("Challenge sent!");
        label1.setForeground(Color.GREEN);

        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                challengedClientId = clientList.getSelectedValue();
                if (challengedClientId != null) {
                    challengedClientId = challengedClientId.split(" ")[1];
                    try {
                        client.challengeClient(Integer.parseInt(challengedClientId));
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                label1.setVisible(true);
            }
        });


        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                try {
                    if(client.getChallengerSocketAddress()==null){
                        String[] updatedClients = client.getActiveClientsList();
                        if (clientList.getModel().getSize() != updatedClients.length || clientList.getModel().getSize()==1) {
                            clientList.setListData(updatedClients);
                            clientList.revalidate();
                            clientList.repaint();
                        }
                        label1.setVisible(false);
                    }
                    String message = client.messageReceiver();
                    if (message.length() > 0) messageParser(message);
                } catch (Exception e) {

                }
            }
        };
        timer.schedule(task, 0, 2000);


        // Add the list and button to the frame
        frame.setLayout(new BorderLayout());
        frame.add(new JScrollPane(clientList), BorderLayout.CENTER);
        frame.add(button, BorderLayout.SOUTH);
        frame.add(label1, BorderLayout.NORTH);


        // Display the frame
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.setResizable(false);
    }

    public void displayGrid() {
        JFrame frame1 = new JFrame("Tic Tac Toe");
        frame1.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame1.setSize(300, 300);

        // Create the labels
        titleLabel = new JLabel("Tic Tac Toe", SwingConstants.CENTER);
        turnLabel = new JLabel("Player 1's turn", SwingConstants.CENTER);

        // Create the buttons
        buttons = new JButton[9];
        for (int i = 0; i < buttons.length; i++) {
            buttons[i] = new JButton("");
            buttons[i].addActionListener(new ButtonListener(i));
        }

        // Add the components to the frame
        frame1.add(titleLabel, BorderLayout.NORTH);
        JPanel buttonPanel = new JPanel(new GridLayout(3, 3));
        for (int i = 0; i < buttons.length; i++) {
            buttonPanel.add(buttons[i]);
        }
        frame1.add(buttonPanel, BorderLayout.CENTER);
        frame1.add(turnLabel, BorderLayout.SOUTH);

        // Initialize the game
        playerTurn = true;
        frame1.setLocationRelativeTo(null);
        frame1.setVisible(true);
    }

    public void setPlayerTurn(boolean turn){
        playerTurn =turn;
    }
    public void takeTurn(int index) throws Exception {
        if(turnLabel.getText().equals("Player 1's turn")){
            buttons[index].setText("X");
            client.transferP2PMessage("X:"+index);
            checkWin("X");
            turnLabel.setText("Player 2's turn");
        }
        else{
            buttons[index].setText("O");
            client.transferP2PMessage("O:"+index);
            checkWin("O");
            turnLabel.setText("Player 1's turn");
        }
        setPlayerTurn(false);
    }
    private void checkWin(String player) throws IOException {
        // Check for horizontal win
        for (int i = 0; i < 9; i += 3) {
            if (buttons[i].getText().equals(player) && buttons[i + 1].getText().equals(player) && buttons[i + 2].getText().equals(player)) {
                win(player);
                return;
            }
        }
        // Check for vertical win
        for (int i = 0; i < 3; i++) {
            if (buttons[i].getText().equals(player) && buttons[i + 3].getText().equals(player) && buttons[i + 6].getText().equals(player)) {
                win(player);
                return;
            }
        }
        // Check for diagonal win
        if (buttons[0].getText().equals(player) && buttons[4].getText().equals(player) && buttons[8].getText().equals(player)) {
            win(player);
            return;
        }
        if (buttons[2].getText().equals(player) && buttons[4].getText().equals(player) && buttons[6].getText().equals(player)) {
            win(player);
            return;
        }

        // Check for draw
        boolean draw = true;
        for (int i = 0; i < 9; i++) {
            if (buttons[i].getText().equals("")) {
                draw = false;
                break;
            }
        }
        if (draw) {
            draw();
        }
    }
    private void win(String player) throws IOException {
        for (int i = 0; i < 9; i++) {
            buttons[i].setEnabled(false);
        }
        JOptionPane.showMessageDialog(frame, player + " wins!", "Winner", JOptionPane.INFORMATION_MESSAGE);
        if(turnLabel.getText().equals("Player 1's turn"))
            client.messageSender("Client " +challengedClientId+ "won against Client "+client.getPersonId());
        else
            client.messageSender("Client " +client.getPersonId()+ "won against Client "+challengedClientId);
    }
    private void draw() throws IOException {
        JOptionPane.showMessageDialog(frame, "It's a draw!", "Draw", JOptionPane.INFORMATION_MESSAGE);
        client.messageSender("Client " +client.getPersonId()+ "and Client "+challengedClientId +" drawn!");
    }

    private class ButtonListener implements ActionListener {
        private int index;
        public ButtonListener(int index) {
            this.index = index;
        }
        public void actionPerformed(ActionEvent e) {
            if (buttons[index].getText().equals("")) {
                if (playerTurn) {
                    try {
                        takeTurn(index);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }
//                else {
//                    buttons[index].setText("O");
//                    checkWin("O");
//                    turnLabel.setText("Client 1's turn");
//                }
            }
        }
    }

//    public void displayGameGrid() throws IOException {
//        JFrame frame1 = new JFrame("Input Frame");
//        frame1.setLayout(new FlowLayout());
//        frame1.setSize(400, 100);
//        frame1.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        JTextField inputField = new JTextField(20);
//        JButton button = new JButton("Print");
//        button.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                try {
//                    String text = inputField.getText();
//                    client.transferP2PMessage(text);
//                } catch (Exception ex) {
//                    throw new RuntimeException(ex);
//                }
//            }
//        });
//
//        frame1.add(inputField);
//        frame1.add(button);
//        frame1.setLocationRelativeTo(null);
//        frame1.setVisible(true);
//        frame1.setResizable(false);
//    }

    private boolean displayChallengeAcceptor(int challengerID) throws InterruptedException {
        JFrame challengeFrame = new JFrame("Accept Challenge");
        challengeFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        challengeFrame.setSize(200, 100);
        challengeFrame.setLocationRelativeTo(null);
        challengeFrame.setResizable(false);
        JLabel label = new JLabel("Accept Challenge From Client " + challengerID, SwingConstants.CENTER);
        challengeFrame.add(label, BorderLayout.CENTER);
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        JButton yesButton = new JButton("Yes");
        JButton noButton = new JButton("No");
        buttonPanel.add(yesButton);
        buttonPanel.add(noButton);
        challengeFrame.add(buttonPanel, BorderLayout.SOUTH);
        AtomicBoolean acceptFlag = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        yesButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                acceptFlag.set(true);
                latch.countDown();
                challengeFrame.dispose(); // Close the frame
            }
        });

        noButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                acceptFlag.set(false);
                latch.countDown();
                challengeFrame.dispose(); // Close the frame
            }
        });

        challengeFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                super.windowClosed(e);
                acceptFlag.set(false);
                latch.countDown();
            }
        });

        // Display the frame
        challengeFrame.setVisible(true);
        latch.await(); // Wait for the user to click on either the "Yes" or "No" button
        return acceptFlag.get();
    }
}


public class Client {

    public static void main(String[] args) throws Exception   {
        gameUI g = new gameUI();
        g.display();


       // c.disconnectClient();
    }
}