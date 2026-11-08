package com.ureclive.urec_live_backend.rag;

import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Bounded response bodies and an end-to-end timeout, including body delivery. */
@Component
public class LiveHttp {
    public record Response(int status, Map<String, List<String>> headers, String body) {
        String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                    .flatMap(e -> e.getValue().stream()).findFirst().orElse("");
        }
    }
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public Response request(URI uri, String body, Map<String, String> headers, long deadline, int cap) throws Exception {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw new TimeoutException();
        var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofNanos(remaining));
        headers.forEach(builder::header);
        if (body == null) builder.GET(); else builder.POST(HttpRequest.BodyPublishers.ofString(body));
        var future = client.sendAsync(builder.build(), info -> new LimitedBody(cap));
        try {
            var response = future.get(remaining, TimeUnit.NANOSECONDS);
            return new Response(response.statusCode(), response.headers().map(), response.body());
        } finally { if (!future.isDone()) future.cancel(true); }
    }

    static long within(long deadline, int seconds) {
        return Math.min(deadline, System.nanoTime() + Duration.ofSeconds(seconds).toNanos());
    }

    private static class LimitedBody implements HttpResponse.BodySubscriber<String> {
        private final HttpResponse.BodySubscriber<String> delegate = HttpResponse.BodySubscribers.ofString(java.nio.charset.StandardCharsets.UTF_8);
        private final int cap;
        private int size;
        private Flow.Subscription subscription;
        LimitedBody(int cap) { this.cap = cap; }
        public CompletionStage<String> getBody() { return delegate.getBody(); }
        public void onSubscribe(Flow.Subscription s) { subscription = s; delegate.onSubscribe(s); }
        public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) size += buffer.remaining();
            if (size > cap) {
                subscription.cancel();
                delegate.onError(new IllegalStateException("Response size limit exceeded"));
            } else delegate.onNext(buffers);
        }
        public void onError(Throwable t) { delegate.onError(t); }
        public void onComplete() { delegate.onComplete(); }
    }
}
