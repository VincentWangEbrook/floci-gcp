package io.floci.gcp.services.scheduler;

import com.sun.net.httpserver.HttpServer;
import io.floci.gcp.services.scheduler.model.StoredJob;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScheduleInvokerTest {

    @Test
    void forwardsTheSchedulerOidcPrincipalOnlyToTheLocalProxy() throws Exception {
        AtomicReference<String> principal = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/cron/purge", exchange -> {
            principal.set(exchange.getRequestHeaders().getFirst("X-Floci-Local-Oidc-Service-Account"));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            StoredJob job = new StoredJob();
            job.setName("projects/p1/locations/us-east1/jobs/purge");
            job.setTargetType("HTTP");
            job.setHttpMethod("POST");
            job.setHttpUri("http://127.0.0.1:" + server.getAddress().getPort() + "/internal/cron/purge");
            job.setOidcServiceAccountEmail("scheduler@p1.iam.gserviceaccount.com");

            ScheduleInvoker.InvokeResult result = new ScheduleInvoker(null, true).invoke(job);

            assertEquals(0, result.code());
            assertEquals("scheduler@p1.iam.gserviceaccount.com", principal.get());
        } finally {
            server.stop(0);
        }
    }
}
