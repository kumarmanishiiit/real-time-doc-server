package org.example;

import com.iiith.assignment.model.sync.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.checkerframework.checker.units.qual.A;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SyncServer {

    private static Logger log = LoggerFactory.getLogger(SyncServer.class);

    static List<String> currentContent =  new ArrayList<>();

    // List to store all connected clients' StreamObservers
    private static final List<StreamObserver<ContentChanges>> clients = new ArrayList<>();


    public static void main(String[] args) throws IOException, InterruptedException {

        Server server = ServerBuilder.forPort(9090)
                .addService(new BidirectionalServiceImpl())
                .build();

        server.start();
        log.info("Server started on port 9090");
        server.awaitTermination();
    }

    // Broadcast the same message to all connected clients
    private static void broadcastMessage(List<ContentChange> contentChanges, String clientID) {

        // Build the response to broadcast
        ContentChanges response = ContentChanges.newBuilder()
                .addAllContentChange(contentChanges)
                .setClientId(clientID)
                .build();

        // Send the response to all connected clients
        synchronized (clients) {
            for (StreamObserver<ContentChanges> client : clients) {
                client.onNext(response);
            }
        }
    }

    static class BidirectionalServiceImpl extends SyncServiceGrpc.SyncServiceImplBase {

        @Override
        public void downloadFile(FileRequest request, StreamObserver<FileChunk> responseObserver) {
            String fileName = request.getFileName();
            File file = new File("server/cache/test.txt");

            if (!file.exists()) {
                log.error("File not found");
                responseObserver.onError(new RuntimeException("File not found"));
                return;
            }

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[1024]; // Send file in 1KB chunks
                int bytesRead;

                // Read a file and convert its contents to ByteString
                // ByteString byteString = ByteString.readFrom(Files.newInputStream(Paths.get("example.txt")));
                while ((bytesRead = fis.read(buffer)) != -1) {

                    FileChunk chunk = FileChunk.newBuilder()
                            .setContent(com.google.protobuf.ByteString.copyFrom(buffer, 0, bytesRead))
                            .setSize(bytesRead)
                            .build();

                    responseObserver.onNext(chunk);
                }
                responseObserver.onCompleted();
            } catch (IOException e) {
                log.error("Error {}", e.getMessage());
                responseObserver.onError(e);
            }
        }

        @Override
        public void syncFile(ContentChanges request, io.grpc.stub.StreamObserver<ContentChanges> responseObserver) {
            List<ContentChange> contentChanges = request.getContentChangeList();

            String clientID = request.getClientId();
            contentChanges.forEach(contentChange -> {
                currentContent.add(contentChange.getContent());
            });

            new Thread(() -> {
                broadcastMessage(contentChanges, clientID);
                try {
                    Thread.sleep(5000); // 5 seconds delay between broadcasts
                } catch (InterruptedException e) {
                    log.error("Error {}", e.getMessage());
                }
            }).start();
        }

        @Override
        public void registerClient(Client request, io.grpc.stub.StreamObserver<ContentChanges> responseObserver) {
            String requestData = request.getClientId();
            log.debug("Register request from client: {}", requestData);

            List<ContentChange> contentChangeList = new ArrayList<>();
            contentChangeList.add(ContentChange.newBuilder().setContent("Registered for file").setAction(ACTION.REGISTER).setLine(-1).build());
            ContentChanges contentChanges = ContentChanges.newBuilder().addAllContentChange(contentChangeList).build();
            // Server sends multiple responses (streaming)

            // Cast the StreamObserver to ServerCallStreamObserver
            ServerCallStreamObserver<ContentChanges> serverObserver =
                    (ServerCallStreamObserver<ContentChanges>) responseObserver;

            // Set a cancellation handler to handle client-side cancellation
            serverObserver.setOnCancelHandler(() -> {
                log.warn("Client cancelled the call. Removing it from observer list...");
                // Clean up resources or stop processing if necessary
                clients.remove(responseObserver);

                if (clients.isEmpty()) {
                    loadToFSFromCurrentContent();
                }
            });

            // Add the client StreamObserver to the list of clients
            synchronized (clients) {
                clients.add(responseObserver);
            }

            // Send each response back to the client
            responseObserver.onNext(contentChanges);

            // Simulate delay between responses
            try {
                Thread.sleep(1000); // 1 second delay between messages
            } catch (InterruptedException e) {
                log.error("Error {}", e.getMessage());
              }
        }

        public void loadToFSFromCurrentContent() {
            BufferedWriter bufferedWriter = null;
            try {
                bufferedWriter = new BufferedWriter(new FileWriter("cache/test.txt"));
                for (String line : currentContent) {
                    bufferedWriter.write(line);
                    bufferedWriter.write("\n");
                }
                bufferedWriter.close();
            } catch (IOException e) {
                log.error("Error {}", e.getMessage());
                throw new RuntimeException(e);
            }

        }

    }
}