package com.github.ingvord.axsis;

import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Span;
import co.elastic.apm.api.Transaction;
import co.elastic.apm.attach.ElasticApmAttacher;
import com.github.ingvord.axsis.message.AxsisMoveMessage;
import com.github.ingvord.axsis.message.AxsisDoneMessage;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import magix.SseMagixClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;

import javax.ws.rs.client.Client;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AxsisObserver {
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

        var doneAction = PublishSubject.<AxsisDoneMessage>create();

        var errorAction = PublishSubject.<AxsisDoneMessage>create();

        moveAction.subscribe(axsisMoveAction -> {
            var txn = ElasticApm.startTransaction()
                    .setName("move");
            txnRepository.put(axsisMoveAction.id, txn);
        });

        positionAction.subscribe(axsisPositionAction -> {
        });

        doneAction.subscribe(doneActionMessage -> {
            txnRepository.get(doneActionMessage.parentId).end();
        });

        errorAction.subscribe(errorActionMessage -> {
            txnRepository.get(errorActionMessage.parentId)
                    .captureException(new Exception(errorActionMessage.payload.get("error").asText("Unknown error!")));
        });

        try(var magix = new SseMagixClient("http://" + System.getenv("MAGIX_HOST"), client)){
            magix.connect();

            magix.observe("axsis-xes")
                    .subscribeOn(Schedulers.io())
                    .doOnNext(inboundSseEvent -> System.out.println(inboundSseEvent.readData()))
                    .blockingSubscribe(inboundSseEvent ->
                    {
                        var optionalAxsisMessage = Helpers.tryDecode(inboundSseEvent, AxsisMoveMessage.class);
                        if(optionalAxsisMessage.isPresent()){
                            var axsisMessage = optionalAxsisMessage.get();
                            if(axsisMessage.target.equals("axsis") && axsisMessage.payload.action.equals("MOV"))
                                moveAction.onNext(axsisMessage);
                        }
                        var optionalAxsisDoneMessage = Helpers.tryDecode(inboundSseEvent, AxsisDoneMessage.class);
                        if(optionalAxsisDoneMessage.isPresent()){
                            var axsisMessage = optionalAxsisDoneMessage.get();
                            if(Optional.ofNullable(axsisMessage.action).orElse("").equals("done"))
                                doneAction.onNext(axsisMessage);
                            else if(Optional.ofNullable(axsisMessage.action).orElse("").equals("error"))
                                errorAction.onNext(axsisMessage);
                        }

                        //TODO report unknown message
                    });
        } catch (Exception e){
            throw new RuntimeException(e);
        }
    }

}
