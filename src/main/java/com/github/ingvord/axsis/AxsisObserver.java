package com.github.ingvord.axsis;

import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Span;
import co.elastic.apm.api.Transaction;
import co.elastic.apm.attach.ElasticApmAttacher;
import com.github.ingvord.axsis.message.AxsisMoveMessage;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observables.GroupedObservable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import magix.SseMagixClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;

import javax.ws.rs.client.Client;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.github.ingvord.axsis.message.AxsisMoveMessage.AxsisAction.move;
import static com.github.ingvord.axsis.message.AxsisMoveMessage.AxsisAction.done;
import static com.github.ingvord.axsis.message.AxsisMoveMessage.AxsisAction.error;

public class AxsisObserver {

    public static final String CHANNEL = "axsis-xes";

    static {
        ElasticApmAttacher.attach();
    }

    private static final Map<Long, Transaction> txnRepository = new ConcurrentHashMap<>();
    private static final Map<Long, Span> spanRepository = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        ResteasyJackson2Provider jacksonProvider = new ResteasyJackson2Provider();

        Client client = ResteasyClientBuilder.newClient().register(jacksonProvider);

        var moveAction = PublishSubject.<AxsisMoveMessage>create();

        var positionAction = PublishSubject.<AxsisMoveMessage>create();

//        var doneAction = PublishSubject.<AxsisDoneMessage>create();
//
//        var errorAction = PublishSubject.<AxsisDoneMessage>create();
//
//        moveAction.subscribe(axsisMoveAction -> {
//            var txn = ElasticApm.startTransaction()
//                    .setName("move");
//            txnRepository.put(axsisMoveAction.id, txn);
//        });
//
//        positionAction.subscribe(axsisPositionAction -> {
//        });
//
//        doneAction.subscribe(doneActionMessage -> {
//            txnRepository.get(doneActionMessage.parentId).end();
//        });
//
//        errorAction.subscribe(errorActionMessage -> {
//            txnRepository.get(errorActionMessage.parentId)
//                    .captureException(new Exception(errorActionMessage.payload.get("error").asText("Unknown error!")));
//        });

        try(var magix = new SseMagixClient("http://" + System.getenv("MAGIX_HOST"), client)){
            magix.connect();

            var txnStart = magix.observe(CHANNEL)
                    .flatMap(inboundSseEvent -> {
                        var optionalAxsisMessage = Helpers.tryDecode(inboundSseEvent, AxsisMoveMessage.class);
                        if(optionalAxsisMessage.isPresent()){
                            return Observable.just(optionalAxsisMessage.get());
                        }
                        return Observable.error(new IllegalArgumentException("unsupported message"));
                    })
                    .filter(axsisMoveMessage -> Objects.nonNull(axsisMoveMessage.action))
                    .filter(axsisMoveMessage -> axsisMoveMessage.action == move)
                    .doOnNext(axsisMoveMessage -> System.out.println("Got start"));


            var txnBody = magix.observe(CHANNEL)
                    .flatMap(inboundSseEvent -> {
                        var optionalAxsisMessage = Helpers.tryDecode(inboundSseEvent, AxsisMoveMessage.class);
                        if(optionalAxsisMessage.isPresent()){
                            return Observable.just(optionalAxsisMessage.get());
                        }
                        return Observable.error(new IllegalArgumentException("unsupported message"));
                    })
                    .groupBy(AxsisMoveMessage::getParentId)
                    .filter(group -> group.getKey()!=0L);



            var txnEnd = magix.observe(CHANNEL)
                    .doOnSubscribe(disposable -> System.out.println("txnEnd subscribed"))
                    .doOnNext(inboundSseEvent -> System.out.println("txnEnd"))
                    .flatMap(inboundSseEvent -> {
                        var optionalAxsisMessage = Helpers.tryDecode(inboundSseEvent, AxsisMoveMessage.class);
                        if(optionalAxsisMessage.isPresent()){
                            return Observable.just(optionalAxsisMessage.get());
                        }
                        return Observable.error(new IllegalArgumentException("unsupported message"));
                    })
                    .filter(axsisMoveMessage -> Objects.nonNull(axsisMoveMessage.action))
                    .filter(axsisMoveMessage -> axsisMoveMessage.action == AxsisMoveMessage.AxsisAction.done || axsisMoveMessage.action == AxsisMoveMessage.AxsisAction.error);


            magix.observe(CHANNEL)
                    .subscribeOn(Schedulers.io())
                    .doOnNext(inboundSseEvent -> System.out.println(inboundSseEvent.readData()))
                    .flatMap(inboundSseEvent -> {
                        var optionalAxsisMessage = Helpers.tryDecode(inboundSseEvent, AxsisMoveMessage.class);
                        if(optionalAxsisMessage.isPresent()){
                            return Observable.just(optionalAxsisMessage.get());
                        }
                        return Observable.error(new IllegalArgumentException("Unsupported message"));
                    })
                    .retry()
                    .buffer(txnStart, v -> txnEnd)
                    .blockingSubscribe(group ->
                    {
                        var txn = ElasticApm.startTransaction();
                        txn.setName("move");
                        Observable.fromIterable(group)
                                .subscribe(message -> {
                                            switch (message.action) {
                                                case move:
                                                    txn.setStartTimestamp(TimeUnit.MICROSECONDS.convert(message.id, TimeUnit.MILLISECONDS));
                                                    break;
                                                case done:
                                                    txn.end(TimeUnit.MICROSECONDS.convert(message.id, TimeUnit.NANOSECONDS));
                                                    break;
                                                case error:
                                                    txn.captureException(new Exception(message.payload.get("error").asText()));
                                                    break;
                                            }
                                        }
                                );
                    });
        } catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    public static record AxsisMoveTransaction(long id, List<AxsisMoveMessage> messageChain){
    }

}
