package io.floci.gcp.lifecycle;

import io.floci.gcp.config.EmulatorConfig;
import io.floci.gcp.core.common.EmulatorClock;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Duration;
import java.util.Map;

/** Test-harness controls. Every endpoint is unavailable unless test-control is explicitly enabled. */
@Path("/_floci-gcp/test/time")
@Produces(MediaType.APPLICATION_JSON)
public class TestControlController {

    private final EmulatorConfig config;
    private final EmulatorClock clock;

    @Inject
    public TestControlController(EmulatorConfig config, EmulatorClock clock) {
        this.config = config;
        this.clock = clock;
    }

    @GET
    public Response currentTime() {
        if (!enabled()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(Map.of("now", clock.instant().toString())).build();
    }

    @POST
    @Path("/advance")
    public Response advance(@QueryParam("seconds") long seconds) {
        if (!enabled()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (seconds < 0) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "seconds must be non-negative")).build();
        }
        return Response.ok(Map.of("now", clock.advance(Duration.ofSeconds(seconds)).toString())).build();
    }

    private boolean enabled() {
        return config.testControl().enabled();
    }
}
