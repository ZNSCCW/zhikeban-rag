package com.zhikeban.bishe.controller;

import com.zhikeban.bishe.domain.entity.Material;
import com.zhikeban.bishe.security.LoginUser;
import com.zhikeban.bishe.service.MaterialService;
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
 * `MaterialController` 单测（`ENG-013`）—— 列表 / 新建（需登录）/ 详情 / 改 / 删除（**只登记元数据**）。
 */
class MaterialControllerTest {

    private MaterialService materialService;
    private MaterialController controller;

    @BeforeEach
    void setUp() {
        materialService = mock(MaterialService.class);
        controller = new MaterialController(materialService);
    }

    private static Material material(long id) {
        Material m = new Material();
        m.setId(id);
        m.setName("甲.pdf");
        return m;
    }

    @Test
    void listAndDetailDelegate() {
        when(materialService.list(2L)).thenReturn(List.of(material(1L)));
        assertThat(controller.list(2L)).hasSize(1);

        when(materialService.detail(1L)).thenReturn(material(1L));
        assertThat(controller.detail(1L).getId()).isEqualTo(1L);
    }

    @Test
    void createRequiresAuthentication() {
        assertThatThrownBy(() -> controller.create(new Material(), null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(401));
    }

    @Test
    void createUsesTheAuthenticatedUserIdAsUploader() {
        Material req = material(0L);
        when(materialService.create(req, 7L)).thenReturn(material(1L));

        assertThat(controller.create(req, new LoginUser(7L, "user-7")).getId()).isEqualTo(1L);
        verify(materialService).create(req, 7L);
    }

    @Test
    void updateAndDeleteDelegate() {
        Material req = material(0L);
        when(materialService.update(1L, req)).thenReturn(material(1L));
        assertThat(controller.update(1L, req).getId()).isEqualTo(1L);

        Map<String, Object> body = controller.delete(1L);
        verify(materialService).delete(1L);
        assertThat(body).containsEntry("deleted", true).containsEntry("id", 1L);
    }
}
