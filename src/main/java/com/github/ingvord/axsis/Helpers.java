package com.github.ingvord.axsis;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.InboundSseEvent;
import java.util.Optional;

public class Helpers {
    public static <T> Optional<T> tryDecode(InboundSseEvent inboundSseEvent, Class<T> target){
        try {
            return Optional.of(inboundSseEvent.readData(target, MediaType.APPLICATION_JSON_TYPE));
        }catch (ProcessingException e){
            return Optional.empty();
        }

    }
}
