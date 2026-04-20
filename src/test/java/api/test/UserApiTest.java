package api.test;

import api.base.BaseApiTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@Epic("API Testing")
@Feature("User Endpoints")
public class UserApiTest extends BaseApiTest {
    static final int STATUS_CODE_200 = 200;
    static final int STATUS_CODE_201 = 201;
    static final int STATUS_CODE_404 = 404;

    @Story("Get list of users")
    @Test
    public void getUsers_returns200() {
        given(requestSpec)
                .when()
                .get("/users?page=2")
                .then()
                .statusCode(STATUS_CODE_200);
    }

    @Story("Get single user with correct Id")
    @Test
    public void getSingleUser_returnsCorrectId() {
        given(requestSpec)
                .when()
                .get("/users/2")
                .then()
                .statusCode(STATUS_CODE_200)
                .body("id", equalTo(2))
                .body("name", notNullValue())
                .body("email", notNullValue());
    }

    @Story("Create user")
    @Test
    public void createUser_returns201() {
        Map<String, Object> bodyParams = new HashMap<>();
        bodyParams.put("name", "Jishu");
        bodyParams.put("job", "QA Engineer");

        given(requestSpec)
                .body(bodyParams)
                .when()
                .post("/users")
                .then()
                .statusCode(STATUS_CODE_201)
                .body("name", equalTo("Jishu"));
    }

    @Story("Verify 404 when user not found")
    @Test
    public void getNonExistentUser_returns404() {
        given(requestSpec)
                .when()
                .get("/users/9999")
                .then()
                .statusCode(STATUS_CODE_404);
    }

    @Story("Verify threshold response time")
    @Test
    public void getUsers_respondsWithinThreshold() {
        given(requestSpec)
                .when()
                .get("/users?page=2")
                .then()
                .statusCode(STATUS_CODE_200)
                .time(lessThan(3000L));
    }
}