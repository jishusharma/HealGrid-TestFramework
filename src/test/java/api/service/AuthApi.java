package api.service;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

public class AuthApi {
    private final RequestSpecification reqspec;

    public AuthApi(RequestSpecification requestSpecification) {
        this.reqspec = requestSpecification;
    }

    public Response login(String email, String password) {
        return given(reqspec)
                .body("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .when()
                .post("/login");
    }

    public Response getUsers(String token, int page) {
        return given(reqspec)
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/users?page=" + page);
    }
}