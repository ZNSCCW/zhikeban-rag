package com.zhikeban.bishe.web;

import com.zhikeban.bishe.controller.AuthController;
import com.zhikeban.bishe.controller.CourseController;
import com.zhikeban.bishe.controller.HealthController;
import com.zhikeban.bishe.controller.QuestionController;
import com.zhikeban.bishe.domain.entity.Course;
import com.zhikeban.bishe.service.AuthService;
import com.zhikeban.bishe.service.CommunityService;
import com.zhikeban.bishe.service.CourseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web 层单测（`ENG-013`）—— `MockMvc` **独立运行**（不启 Spring 上下文、不连库）：
 * 真实 URL 映射、`@Valid` 参数校验、以及 `ApiExceptionHandler` 的**统一异常出口**形状。
 *
 * <p>★ 边界如实说明：独立运行的 `MockMvc` **没有装配 Spring Security 的过滤器链**，
 * 因此 `@AuthenticationPrincipal` 在这里取不到主体 —— **"未登录 ⇒ 401"那几条分支
 * 由各控制器的直接调用单测（`CourseControllerTest`／`MaterialControllerTest`／
 * `QuestionControllerTest`／`AuthControllerTest`）承担**，本类不假装覆盖它们。
 */
class ApiWebLayerMockMvcTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private static Course course(long id) {
        Course c = new Course();
        c.setId(id);
        c.setName("数据结构");
        c.setTeacherId(5L);
        return c;
    }

    @Test
    void healthEndpointAnswersTheSmokeScriptContract() throws Exception {
        String body = mvc(new HealthController())
                .perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("zhikeban-backend"))
                .andExpect(jsonPath("$.authenticated").value(false))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"status\":\"UP\"").contains("zhikeban-backend");
    }

    @Test
    void beanValidationFailureBecomesA400JsonBody() throws Exception {
        mvc(new AuthController(mock(AuthService.class)))
                .perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ab\",\"password\":\"pw123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(containsString("username")));
    }

    @Test
    void businessExceptionsKeepTheirOwnHttpStatus() throws Exception {
        CourseService courseService = mock(CourseService.class);
        when(courseService.detail(9L)).thenThrow(ApiException.notFound("课程不存在：9"));

        mvc(new CourseController(courseService))
                .perform(get("/api/courses/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void listEndpointSerialisesTheDomainObjects() throws Exception {
        CourseService courseService = mock(CourseService.class);
        when(courseService.list(null)).thenReturn(List.of(course(1L), course(2L)));

        mvc(new CourseController(courseService))
                .perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].teacherId").value(5));
    }

    @Test
    void missingAnswerIdIsRejectedBeforeAnythingElse() throws Exception {
        mvc(new QuestionController(mock(CommunityService.class)))
                .perform(post("/api/questions/1/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
