package com.zhikeban.bishe.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * `ApiException` / `ApiExceptionHandler` 单测（`ENG-013`）—— **统一异常出口**：
 * 状态码原样透出、JSON 形状稳定、**不吞异常、不伪造成功**。
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(ResponseEntity<?> response) {
        return (Map<String, Object>) response.getBody();
    }

    @Test
    void factoryMethodsCarryTheRightHttpStatus() {
        assertThat(ApiException.badRequest("入参").getStatus()).isEqualTo(400);
        assertThat(ApiException.unauthorized("未认证").getStatus()).isEqualTo(401);
        assertThat(ApiException.forbidden("无权").getStatus()).isEqualTo(403);
        assertThat(ApiException.notFound("不存在").getStatus()).isEqualTo(404);
        assertThat(ApiException.conflict("冲突").getStatus()).isEqualTo(409);
        assertThat(ApiException.badRequest("入参")).hasMessage("入参");
    }

    @Test
    void apiExceptionBecomesAStableJsonBody() {
        ResponseEntity<Map<String, Object>> response = handler.handleApi(ApiException.conflict("用户名已被占用"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(body(response)).containsEntry("status", 409)
                .containsEntry("error", "Conflict")
                .containsEntry("message", "用户名已被占用");
        assertThat((String) body(response).get("timestamp")).isNotBlank();
    }

    @Test
    void beanValidationFailureReportsTheFirstFieldMessage() {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("registerRequest", "username", "用户名长度需 4~32")));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(body(response)).containsEntry("message", "username 用户名长度需 4~32")
                .containsEntry("error", "Bad Request");
    }

    @Test
    void beanValidationFailureWithoutFieldErrorsStillSaysSomething() {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of());
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        assertThat(body(handler.handleValidation(ex))).containsEntry("message", "入参校验失败");
    }
}
