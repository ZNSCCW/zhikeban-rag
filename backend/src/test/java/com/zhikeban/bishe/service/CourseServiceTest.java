package com.zhikeban.bishe.service;

import com.zhikeban.bishe.domain.entity.Course;
import com.zhikeban.bishe.mapper.CourseMapper;
import com.zhikeban.bishe.mapper.CourseMemberMapper;
import com.zhikeban.bishe.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `CourseService` 单测（`ENG-013`）—— 最小 CRUD ＋ ★★ **`isMember`：全系统唯一成员判定入口**
 * （RAG 权限过滤的权威判据来源 ⇒ 越权防线只有一份实现）。
 */
class CourseServiceTest {

    private static final String INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private CourseMapper courseMapper;
    private CourseMemberMapper courseMemberMapper;
    private CourseService service;

    @BeforeEach
    void setUp() {
        courseMapper = mock(CourseMapper.class);
        courseMemberMapper = mock(CourseMemberMapper.class);
        service = new CourseService(courseMapper, courseMemberMapper);
    }

    private static Course course(long id, long teacherId) {
        Course c = new Course();
        c.setId(id);
        c.setName("数据结构");
        c.setTeacherId(teacherId);
        return c;
    }

    @Test
    void listDelegatesToMapper() {
        when(courseMapper.selectList(any())).thenReturn(List.of(course(1L, 5L)));

        assertThat(service.list(5L)).extracting(Course::getId).containsExactly(1L);
        assertThat(service.list(null)).hasSize(1);
    }

    @Test
    void detailThrows404WhenMissing() {
        when(courseMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(9L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
    }

    @Test
    void createRejectsBlankName() {
        Course req = new Course();
        req.setName("   ");

        assertThatThrownBy(() -> service.create(req, 1L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
    }

    @Test
    void createGeneratesInviteCodeAndFillsDefaults() {
        Course req = new Course();
        req.setName("数据结构");

        Course created = service.create(req, 7L);

        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
        verify(courseMapper).insert(captor.capture());
        Course saved = captor.getValue();

        assertThat(saved.getName()).isEqualTo("数据结构");
        assertThat(saved.getTeacherId()).isEqualTo(7L);              // 未指定 ⇒ 用操作者
        assertThat(saved.getSemester()).isEqualTo("未指定学期");
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getDeleted()).isZero();
        assertThat(saved.getInviteCode()).hasSize(8);
        assertThat(saved.getInviteCode().chars()
                .allMatch(ch -> INVITE_ALPHABET.indexOf(ch) >= 0)).isTrue();
        assertThat(created).isSameAs(saved);
    }

    @Test
    void createKeepsExplicitValues() {
        Course req = new Course();
        req.setName("操作系统");
        req.setTeacherId(3L);
        req.setSemester("2025-2026-2");
        req.setInviteCode("EXPLICIT");
        req.setStatus(0);

        service.create(req, 7L);

        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
        verify(courseMapper).insert(captor.capture());
        Course saved = captor.getValue();
        assertThat(saved.getTeacherId()).isEqualTo(3L);
        assertThat(saved.getSemester()).isEqualTo("2025-2026-2");
        assertThat(saved.getInviteCode()).isEqualTo("EXPLICIT");
        assertThat(saved.getStatus()).isZero();
    }

    @Test
    void updateOnlyTouchesTheWhitelistedFields() {
        when(courseMapper.selectById(1L)).thenReturn(course(1L, 5L));
        Course req = new Course();
        req.setName("新名字");
        req.setTeacherId(999L);      // 不在白名单 ⇒ 必须忽略
        req.setInviteCode("HACKED");

        Course updated = service.update(1L, req);

        assertThat(updated.getName()).isEqualTo("新名字");
        assertThat(updated.getTeacherId()).isEqualTo(5L);
        assertThat(updated.getInviteCode()).isNull();
        verify(courseMapper).updateById(updated);
    }

    @Test
    void updateThrows404WhenCourseMissing() {
        when(courseMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(9L, new Course()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
    }

    @Test
    void deleteRequiresTheCourseToExist() {
        when(courseMapper.selectById(9L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(9L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(404));

        when(courseMapper.selectById(1L)).thenReturn(course(1L, 5L));
        service.delete(1L);
        verify(courseMapper).deleteById(1L);
    }

    @Test
    void isMemberReturnsFalseForNullArgumentsWithoutQuerying() {
        assertThat(service.isMember(null, 1L)).isFalse();
        assertThat(service.isMember(1L, null)).isFalse();
    }

    @Test
    void theTeacherIsAlwaysAMember() {
        when(courseMapper.selectById(1L)).thenReturn(course(1L, 5L));
        when(courseMemberMapper.selectCount(any())).thenReturn(0L);

        assertThat(service.isMember(1L, 5L)).isTrue();
    }

    @Test
    void aNonTeacherMemberIsRecognisedByCourseMemberRow() {
        when(courseMapper.selectById(1L)).thenReturn(course(1L, 5L));
        when(courseMemberMapper.selectCount(any())).thenReturn(1L);

        assertThat(service.isMember(1L, 8L)).isTrue();
    }

    @Test
    void nonMemberAndMissingCourseAreNotConfirmedAsMembers() {
        when(courseMapper.selectById(1L)).thenReturn(course(1L, 5L));
        when(courseMemberMapper.selectCount(any())).thenReturn(0L);
        assertThat(service.isMember(1L, 8L)).isFalse();

        when(courseMapper.selectById(2L)).thenReturn(null);
        when(courseMemberMapper.selectCount(any())).thenReturn(null);
        assertThat(service.isMember(2L, 8L)).isFalse();
    }
}
