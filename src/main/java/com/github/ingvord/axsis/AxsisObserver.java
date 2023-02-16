package com.github.ingvord.axsis;

import co.elastic.apm.attach.ElasticApmAttacher;
import io.reactivex.rxjava3.schedulers.Schedulers;
import magix.SseMagixClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;

import javax.ws.rs.client.Client;

public class AxsisObserver {
    private static final SseMagixClient MAGIX;

    static {
        ElasticApmAttacher.attach();
    }

    static {
        ResteasyJackson2Provider jacksonProvider = new ResteasyJackson2Provider();

        Client client = ResteasyClientBuilder.newClient().register(jacksonProvider);

        MAGIX = new SseMagixClient("http://" + System.getenv("MAGIX_HOST"), client);
    }

    public static void main(String[] args) throws InterruptedException {
        MAGIX.connect();

        var disposable = MAGIX.observe("axsis-xes")
                .observeOn(Schedulers.io())
                .subscribe(inboundSseEvent -> System.out.println(inboundSseEvent.readData()));


        Runtime.getRuntime().addShutdownHook(new Thread()
        {
            @Override
            public void run()
            {
                disposable.dispose();
            }
        });
//        Runtime.getRuntime().addShutdownHook(new Thread()
//        {
//            @Override
//            public void run()
//            {
//                MAGIX.close();
//            }
//        });

        while (true){
            Thread.sleep(Long.MAX_VALUE);
        }
    }

}
