package ch.usi.inf.dslab.bftamcast.direct.client;

import ch.usi.inf.dslab.bftamcast.util.CLIParser;

import java.util.Random;
import java.util.UUID;

/**
 * @author Paulo Coelho - paulo.coelho@usi.ch
 */
public class ClientDirect {

    public static void main(String[] args) throws InterruptedException {
        CLIParser p = CLIParser.getClientParser(args);
        Random r = new Random();
        int totalTime = p.getDuration();
        int valueSize = p.getMsgSize();
        int clientCount = p.getClientCount();
        int idGroup = p.getGroup();
        int id = UUID.randomUUID().hashCode();
        int perc = p.getGlobalPercent();
        boolean ng = p.isNonGenuine();
        String treeConfigPath = p.getTreeConfig();
        ClientThreadDirect[] clients = new ClientThreadDirect[clientCount];
        Thread[] clientThreads = new Thread[clientCount];

        for (int i = 0; i < clientCount; i++) {
            System.out.println("Starting client " + (id + i));
            Thread.sleep(r.nextInt(600));
            clients[i] = new ClientThreadDirect(id + i, idGroup,true, totalTime, valueSize, perc, ng, treeConfigPath);
            clientThreads[i] = new Thread(clients[i]);
            clientThreads[i].start();
        }

        for (int i = 0; i < clientCount; i++) {
            clientThreads[i].join();
            System.out.println("Client " + i + " finished execution");

        }

    }

}