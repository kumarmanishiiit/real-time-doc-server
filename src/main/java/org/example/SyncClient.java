package org.example;

import com.iiith.assignment.model.sync.Client;
import com.iiith.assignment.model.sync.ContentChange;
import com.iiith.assignment.model.sync.ContentChanges;
import com.iiith.assignment.model.sync.SyncServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SyncClient {


    static List<String> currentContent =  new ArrayList<>();

    public static void main(String[] args) throws InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9090)
                .usePlaintext()
                .build();
        SyncServiceGrpc.SyncServiceStub asyncStub = SyncServiceGrpc.newStub(channel);

        CountDownLatch latch = new CountDownLatch(1);

        // StreamObserver for receiving responses from the server
        StreamObserver<ContentChanges> responseObserver = new StreamObserver<ContentChanges>() {
            @Override
            public void onNext(ContentChanges contentChanges) {
                System.out.println("Received from server: " + contentChanges.getContentChangeCount());
                List<ContentChange> contentChange = contentChanges.getContentChangeList();
                System.out.println("Size of the content is: "+ contentChanges.getContentChangeList().size());
                System.out.println("Message from server: "+ contentChange.get(0).getContent());
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("Server has completed sending messages.");
                latch.countDown();
            }
        };

        Client client = Client.newBuilder().setClientId("clientID").build();

        // StreamObserver for sending messages to the server
        asyncStub.registerClient(client, responseObserver);
        // Simulate a delay
        while (true) {
            Thread.sleep(1000);
        }
        // Wait for the server to finish
//        latch.await(3, TimeUnit.SECONDS);
//        channel.shutdown();
    }
}
