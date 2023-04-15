import java.io.IOException;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Timer;
import java.util.TimerTask;
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

    ClientUDP(){
        p = new Person();
    }

    public void intializeConnection() throws Exception{
        socket = new DatagramSocket();
        socket.setSoTimeout(2000);
        byte[] buffer = new byte[1024];
        DatagramPacket receivePacket;
        packet = new DatagramPacket(buffer, buffer.length);
        packet.setAddress(InetAddress.getByName("localhost"));
        packet.setPort(9876);
    }


    public void getClientID() throws IOException {
        String message = "Request client ID";
        messageSender(message);

        // receive client ID
        message = messageReceiver();
        int clientId = Integer.parseInt(message.split(" ")[4]);
        p.setID(clientId);
        System.out.println("Received client ID: " + clientId);


    }

    public String[] getActiveClientsList() throws IOException {
        String message = "Request active clients:"+p.getID();
        messageSender(message);
        // receive list of active clients
        message = messageReceiver();
        String[] clientIds = message.split("\n");
       // System.out.println("Active clients:\n" + String.join("\n", clientIds));
        return clientIds;
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
            this.establishConnection(challengerAddress,challengerPort);
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

    private void establishConnection( InetAddress challengerAddress, int challengerPort) throws Exception {
//        InetSocketAddress ownAddress = new InetSocketAddress("localhost", 9877);
//        socket.bind(ownAddress);
        System.out.println("Establishing connection with client at port " + challengerPort);
        InetSocketAddress peerAddress = new InetSocketAddress(challengerAddress, challengerPort);
        byte[] buffer = ("Connected to client ").getBytes();
        packet = new DatagramPacket(buffer, buffer.length, peerAddress);
        socket.send(packet);
       // socket.close();
        }

}


class gameUI {
    private JFrame frame;
    private JList<String> clientList;

    private String challengedClientId;
    private ClientUDP client;

    public gameUI() {

        client = new ClientUDP();

    }

    public void messageParser(String Message){
        if(Message.startsWith("Challenged received from")){
            int challengerId = Integer.parseInt(Message.split(":")[1]);
           System.out.println( displayChallengeAcceptor(challengerId));
        }
    }


    public void display() throws Exception {
        client.intializeConnection();
        client.getClientID();

        frame = new JFrame("Multiplayer Tic Tac Toe");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 300);

        clientList = new JList<>();
        clientList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);


        // Initialize the button and set its action listener
        JButton button = new JButton("Select Fruit");
        JLabel label1 = new JLabel("Challenge sent!");
        label1.setForeground(Color.GREEN);

        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Get the selected fruit and store it in the selectedFruit variable
                challengedClientId = clientList.getSelectedValue();
                if(challengedClientId!=null){
                    challengedClientId = challengedClientId.split(" ")[1];
                    try {
                        client.challengeClient(Integer.parseInt(challengedClientId));
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                label1.setVisible(true);

                // Close the current frame and display the selected fruit in a new frame
               // frame.dispose();
               // displaySelectedFruit();
            }
        });


        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                try {
                    String[] updatedClients = client.getActiveClientsList();
                    if(clientList.getModel().getSize()!=updatedClients.length){
                        clientList.setListData(updatedClients);
                        clientList.revalidate();
                        clientList.repaint();
                    }
                    label1.setVisible(false);
                    String message = client.messageReceiver();
                    if(message.length()>0) messageParser(message);
                } catch (Exception e) {

                }
            }
        };
        timer.schedule(task, 0, 2000);



        // Add the list and button to the frame
        frame.setLayout(new BorderLayout());
        frame.add(new JScrollPane(clientList), BorderLayout.CENTER);
        frame.add(button, BorderLayout.SOUTH);
        frame.add(label1,BorderLayout.NORTH);


        // Display the frame
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.setResizable(false);
    }

    private boolean displayChallengeAcceptor(int challengerID) {
        // Initialize the frame for displaying the selected fruit
        JFrame challengeFrame = new JFrame("Accept Challenge");
        challengeFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        challengeFrame.setSize(200, 100);
        challengeFrame.setLocationRelativeTo(null);
        challengeFrame.setResizable(false);
        JLabel label = new JLabel("Accept Challenge From Client "+challengerID, SwingConstants.CENTER);
        challengeFrame.add(label, BorderLayout.CENTER);
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        JButton yesButton = new JButton("Yes");
        JButton noButton = new JButton("No");
        buttonPanel.add(yesButton);
        buttonPanel.add(noButton);
        challengeFrame.add(buttonPanel, BorderLayout.SOUTH);
        AtomicBoolean acceptFlag = new AtomicBoolean(false);

        yesButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("Yes");
                acceptFlag.set(true);
                challengeFrame.dispose(); // Close the frame
            }
        });

        noButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("No");
                challengeFrame.dispose(); // Close the frame
            }
        });

        challengeFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                super.windowClosed(e);
                acceptFlag.set(false);
            }
        });

        // Display the frame
        challengeFrame.setVisible(true);

        return acceptFlag.get();
    }
}


public class Client {

    public static void main(String[] args) throws Exception {
//        ClientUDP c = new ClientUDP();
//        c.intializeConnection();
//        c.getClientID();
//        c.getActiveClientsList();
//        c.challengeClient(1);
//        c.acceptChallenge();
        gameUI g = new gameUI();
        g.display();

       // c.disconnectClient();
    }
}