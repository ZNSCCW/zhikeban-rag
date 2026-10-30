package com.zhikeban.bishe.service;

import com.zhikeban.bishe.domain.entity.Material;
import com.zhikeban.bishe.mapper.MaterialMapper;
import com.zhikeban.bishe.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `MaterialService` 单测（`ENG-013`）—— 只登记元数据：**不做上传落地 / 不解析 / 不切片 / 不向量化**；
 * ★ `storage_path` 是内部存储键 ⇒ **出参前一律置空**。
 */
class MaterialServiceTest {

    private MaterialMapper materialMapper;
    private MaterialService service;

    @BeforeEach
    void setUp() {
        materialMapper = mock(MaterialMapper.class);
        service = new MaterialService(materialMapper);
    }

    private static Material material(long id, String name, String storagePath) {
        Material m = new Material();
        m.setId(id);
        m.setCourseId(2L);
        m.setName(name);
        m.setType("PDF");
        m.setStoragePath(storagePath);
        return m;
    }

    @Test
    void listHidesStoragePathOfEveryRow() {
        when(materialMapper.selectList(any())).thenReturn(List.of(
                material(1L, "甲.pdf", "local://material/a"),
                material(2L, "乙.pdf", "local://material/b")));

        List<Material> rows = service.list(2L);

        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(m -> assertThat(m.getStoragePath()).isNull());
        assertThat(service.list(null)).hasSize(2);
    }

    @Test
    void detailHidesStoragePathAndThrows404WhenMissing() {
        when(materialMapper.selectById(1L)).thenReturn(material(1L, "甲.pdf", "local://material/a"));
        assertThat(service.detail(1L).getStoragePath()).isNull();

        when(materialMapper.selectById(9L)).thenReturn(null);
        assertThatThrownBy(() -> service.detail(9L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
    }

    @Test
    void createValidatesCourseIdAndName() {
        Material noCourse = new Material();
        noCourse.setName("甲.pdf");
        assertThatThrownBy(() -> service.create(noCourse, 1L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(400));

        Material noName = new Material();
        noName.setCourseId(2L);
        assertThatThrownBy(() -> service.create(noName, 1L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
    }

    @Test
    void createFillsDefaultsAndNeverReturnsTheStoragePath() {
        Material req = new Material();
        req.setCourseId(2L);
        req.setName("甲.pdf");
        // ★ `create` 会先 insert、再把同一个对象的 storage_path 抹掉 ⇒ capture 拿到的是**活引用**
        //   ⇒ 这里在 insert 那一刻抄一份路径，才能验"默认存储键确实生成了"
        List<String> pathAtInsert = new ArrayList<>();
        when(materialMapper.insert(any(Material.class))).thenAnswer(inv -> {
            pathAtInsert.add(((Material) inv.getArgument(0)).getStoragePath());
            return 1;
        });

        Material created = service.create(req, 7L);

        ArgumentCaptor<Material> captor = ArgumentCaptor.forClass(Material.class);
        verify(materialMapper).insert(captor.capture());
        Material saved = captor.getValue();

        assertThat(pathAtInsert).hasSize(1);
        assertThat(pathAtInsert.get(0)).startsWith("local://material/");
        assertThat(saved.getType()).isEqualTo("PDF");
        assertThat(saved.getSize()).isZero();
        assertThat(saved.getUploaderId()).isEqualTo(7L);
        assertThat(saved.getParseStatus()).isZero();
        assertThat(saved.getDownloadCount()).isZero();
        assertThat(saved.getDeleted()).isZero();
        assertThat(created.getStoragePath()).isNull();
    }

    @Test
    void createKeepsExplicitMetadata() {
        Material req = new Material();
        req.setCourseId(2L);
        req.setName("甲.docx");
        req.setType("DOCX");
        req.setSize(999L);
        req.setStoragePath("local://explicit");
        req.setChapter("第2章");
        req.setTags("重点");
        req.setUploaderId(3L);
        List<String> pathAtInsert = new ArrayList<>();
        when(materialMapper.insert(any(Material.class))).thenAnswer(inv -> {
            pathAtInsert.add(((Material) inv.getArgument(0)).getStoragePath());
            return 1;
        });

        service.create(req, 7L);

        ArgumentCaptor<Material> captor = ArgumentCaptor.forClass(Material.class);
        verify(materialMapper).insert(captor.capture());
        Material saved = captor.getValue();
        assertThat(pathAtInsert).containsExactly("local://explicit");
        assertThat(saved.getType()).isEqualTo("DOCX");
        assertThat(saved.getSize()).isEqualTo(999L);
        assertThat(saved.getChapter()).isEqualTo("第2章");
        assertThat(saved.getTags()).isEqualTo("重点");
        assertThat(saved.getUploaderId()).isEqualTo(3L);
        assertThat(saved.getStoragePath()).isNull();   // 出参前已抹掉
    }

    @Test
    void updateOnlyTouchesTheWhitelistedFields() {
        when(materialMapper.selectById(1L)).thenReturn(material(1L, "甲.pdf", "local://material/a"));
        Material req = new Material();
        req.setName("新名字");
        req.setType("DOCX");
        req.setCourseId(999L);       // 不在白名单 ⇒ 忽略

        Material updated = service.update(1L, req);

        assertThat(updated.getName()).isEqualTo("新名字");
        assertThat(updated.getType()).isEqualTo("DOCX");
        assertThat(updated.getCourseId()).isEqualTo(2L);
        assertThat(updated.getStoragePath()).isNull();
        verify(materialMapper).updateById(updated);
    }

    @Test
    void updateThrows404WhenMaterialMissing() {
        when(materialMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(9L, new Material()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
    }

    @Test
    void deleteRequiresTheMaterialToExist() {
        when(materialMapper.selectById(9L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(9L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(404));

        when(materialMapper.selectById(1L)).thenReturn(material(1L, "甲.pdf", "local://material/a"));
        service.delete(1L);
        verify(materialMapper).deleteById(1L);
    }
}
