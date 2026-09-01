package io.floci.gcp.lifecycle;

import io.floci.gcp.config.EmulatorConfig;
import io.floci.gcp.core.common.EmulatorClock;
import io.floci.gcp.core.common.TestFaultInjector;
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
    private final TestFaultInjector faults;

    @Inject
    public TestControlController(EmulatorConfig config, EmulatorClock clock, TestFaultInjector faults) {
        this.config = config;
        this.clock = clock;
        this.faults = faults;
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

    @POST
    @Path("/faults/arm")
    public Response armFault(@QueryParam("operation") String operation, @QueryParam("message") String message) {
        if (!enabled()) return Response.status(Response.Status.NOT_FOUND).build();
        try {
            faults.arm(operation, message);
            return Response.ok(Map.of("operation", operation)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
        }
    }

    private boolean enabled() {
        return config.testControl().enabled();
    }
}
