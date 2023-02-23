package com.github.ingvord.axsis;

import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Span;
import co.elastic.apm.attach.ElasticApmAttacher;
import com.github.ingvord.axsis.message.AxsisMoveAction;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import magix.SseMagixClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.InboundSseEvent;
import java.io.StringBufferInputStream;
import java.util.Optional;

public class AxsisObserver {
    static {
        ElasticApmAttacher.attach();
    }

    private static Optional<AxsisMoveAction> tryAxsisMoveAction(InboundSseEvent inboundSseEvent){
        try {
            return Optional.of(inboundSseEvent.readData(AxsisMoveAction.class, MediaType.APPLICATION_JSON_TYPE));
        }catch (ProcessingException e){
            return Optional.empty();
        }

    }

    public static void main(String[] args) {
        ResteasyJackson2Provider jacksonProvider = new ResteasyJackson2Provider();

        Client client = ResteasyClientBuilder.newClient().register(jacksonProvider);

        var moveAction = PublishSubject.<AxsisMoveAction>create();

        var positionAction = PublishSubject.create();

        var doneAction = PublishSubject.create();

        moveAction.subscribe(axsisMoveAction -> {
            var txn = ElasticApm.startTransaction();
            txn.setName("move");
            txn.setFrameworkName("axsis-magix");
        });

        try(var magix = new SseMagixClient("http://" + System.getenv("MAGIX_HOST"), client)){
            magix.connect();

            magix.observe("axsis-xes")
                    .observeOn(Schedulers.io())
                    .doOnNext(inboundSseEvent -> System.out.println(inboundSseEvent.readData()))
                    .blockingSubscribe(inboundSseEvent ->
                    {
                        var optionalAxsisMove = tryAxsisMoveAction(inboundSseEvent);
                        if(optionalAxsisMove.isPresent())
                            moveAction.onNext(optionalAxsisMove.get());
                    });
        } catch (Exception e){
            throw new RuntimeException(e);
        }
    }

}
