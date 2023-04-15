import java.io.IOException;
import java.net.*;
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

    public void getActiveClientsList() throws IOException {
        String message = "Request active clients:"+p.getID();
        messageSender(message);

        // receive list of active clients
        message = messageReceiver();
        String[] clientIds = message.split("\n");
        System.out.println("Active clients:\n " + String.join("\n ", clientIds));
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

    public void acceptChallenge() throws Exception {
        String message = messageReceiver();
        if(message.startsWith("Challenged received from")){
            int challengerId = Integer.parseInt(message.split(":")[1]);
            messageSender("Challenge accepted:"+challengerId+":"+p.getID());
            message = messageReceiver();
            InetAddress challengerAddress =  InetAddress.getByName(message.split("//")[0].replace("/",""));
            int challengerPort = Integer.parseInt(message.split("//")[1]) ;
            this.establishConnection(challengerAddress,challengerPort);
        }
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


public class Client {

    public static void main(String[] args) throws Exception {
        ClientUDP c = new ClientUDP();
        c.intializeConnection();
        c.getClientID();
        c.getActiveClientsList();
        c.challengeClient(1);
        c.acceptChallenge();

       // c.disconnectClient();
    }
}