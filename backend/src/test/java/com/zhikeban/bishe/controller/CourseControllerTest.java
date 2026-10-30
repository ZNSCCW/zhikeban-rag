package com.zhikeban.bishe.controller;

import com.zhikeban.bishe.domain.entity.Course;
import com.zhikeban.bishe.security.LoginUser;
import com.zhikeban.bishe.service.CourseService;
import com.zhikeban.bishe.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `CourseController` 单测（`ENG-013`）—— 列表 / 新建（需登录）/ 详情 / 改（PUT 与 PATCH 同义）/ 删除。
 */
class CourseControllerTest {

    private CourseService courseService;
    private CourseController controller;

    @BeforeEach
    void setUp() {
        courseService = mock(CourseService.class);
        controller = new CourseController(courseService);
    }

    private static Course course(long id) {
        Course c = new Course();
        c.setId(id);
        c.setName("数据结构");
        return c;
    }

    @Test
    void listAndDetailDelegate() {
        when(courseService.list(5L)).thenReturn(List.of(course(1L)));
        assertThat(controller.list(5L)).hasSize(1);

        when(courseService.detail(1L)).thenReturn(course(1L));
        assertThat(controller.detail(1L).getId()).isEqualTo(1L);
    }

    @Test
    void createRequiresAuthentication() {
        assertThatThrownBy(() -> controller.create(new Course(), null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(401));
    }

    @Test
    void createUsesTheAuthenticatedUserIdAsOperator() {
        Course req = course(0L);
        when(courseService.create(req, 7L)).thenReturn(course(1L));

        assertThat(controller.create(req, new LoginUser(7L, "user-7")).getId()).isEqualTo(1L);
        verify(courseService).create(req, 7L);
    }

    @Test
    void putAndPatchAreBothAcceptedAliases() {
        Course req = course(0L);
        when(courseService.update(1L, req)).thenReturn(course(1L));

        controller.update(1L, req);
        controller.patch(1L, req);

        verify(courseService, org.mockito.Mockito.times(2)).update(1L, req);
    }

    @Test
    void deleteReturnsTheDeletedId() {
        Map<String, Object> body = controller.delete(1L);

        verify(courseService).delete(1L);
        assertThat(body).containsEntry("deleted", true).containsEntry("id", 1L);
    }
}
