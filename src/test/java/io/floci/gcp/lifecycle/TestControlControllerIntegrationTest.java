package io.floci.gcp.lifecycle;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestControlControllerIntegrationTest {

    @Test
    void advancesTheTestOnlyVirtualClock() {
        String advanced = given().queryParam("seconds", 60).when().post("/_floci-gcp/test/time/advance")
                .then().statusCode(200).extract().path("now");

        String after = given().when().get("/_floci-gcp/test/time")
                .then().statusCode(200).extract().path("now");
        assertEquals(advanced, after);
    }

    @Test
    void armsOnlyWhitelistedFaults() {
        given().queryParam("operation", "scheduler.dispatch").when().post("/_floci-gcp/test/time/faults/arm")
                .then().statusCode(200);
        given().queryParam("operation", "unsupported").when().post("/_floci-gcp/test/time/faults/arm")
                .then().statusCode(400);
    }
}
