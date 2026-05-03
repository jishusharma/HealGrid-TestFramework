package api.service;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

public class UserApi {

    private final RequestSpecification spec;

    public UserApi(RequestSpecification spec) {
        this.spec = spec;
    }

    public Response getUsers(int page) {
        return given(spec).when().get("/users?page=" + page);
    }

    public Response getUserById(int id) {
        return given(spec).when().get("/users/" + id);
    }

    public Response createUser(String name, String job) {
        String body = String.format("{\"name\":\"%s\",\"job\":\"%s\"}", name, job);
        return given(spec).body(body).when().post("/users");
    }
}