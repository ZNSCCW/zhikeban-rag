package com.zhikeban.bishe.domain.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 实体读写单测（`ENG-013`）—— 7 张表的实体是 `MyBatis-Plus` 的映射对象，
 * 字段与 `schema.sql` 一一对应；这里逐字段验证 **getter/setter 往返**（防止字段被复制粘贴串名）。
 */
class EntityMappingTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 10, 12, 0);

    @Test
    void userRoundTripsAllColumns() {
        User e = new User();
        e.setId(1L);
        e.setUsername("alice");
        e.setPassword("$2a$hash");
        e.setNickname("小艾");
        e.setAvatar("a.png");
        e.setEmail("a@example.com");
        e.setCollege("软件学院");
        e.setMajor("软件工程");
        e.setStatus(1);
        e.setCreatedAt(NOW);
        e.setUpdatedAt(NOW);
        e.setDeleted(0);

        assertThat(e.getId()).isEqualTo(1L);
        assertThat(e.getUsername()).isEqualTo("alice");
        assertThat(e.getPassword()).isEqualTo("$2a$hash");
        assertThat(e.getNickname()).isEqualTo("小艾");
        assertThat(e.getAvatar()).isEqualTo("a.png");
        assertThat(e.getEmail()).isEqualTo("a@example.com");
        assertThat(e.getCollege()).isEqualTo("软件学院");
        assertThat(e.getMajor()).isEqualTo("软件工程");
        assertThat(e.getStatus()).isEqualTo(1);
        assertThat(e.getCreatedAt()).isEqualTo(NOW);
        assertThat(e.getUpdatedAt()).isEqualTo(NOW);
        assertThat(e.getDeleted()).isZero();
    }

    @Test
    void courseRoundTripsAllColumns() {
        Course e = new Course();
        e.setId(2L);
        e.setName("数据结构");
        e.setTeacherId(1L);
        e.setSemester("2026-2027-1");
        e.setCredit(new BigDecimal("3.5"));
        e.setCover("c.png");
        e.setInviteCode("ABCD2345");
        e.setInviteExpireAt(NOW);
        e.setStatus(1);
        e.setCreatedAt(NOW);
        e.setUpdatedAt(NOW);
        e.setDeleted(0);

        assertThat(e.getId()).isEqualTo(2L);
        assertThat(e.getName()).isEqualTo("数据结构");
        assertThat(e.getTeacherId()).isEqualTo(1L);
        assertThat(e.getSemester()).isEqualTo("2026-2027-1");
        assertThat(e.getCredit()).isEqualByComparingTo("3.5");
        assertThat(e.getCover()).isEqualTo("c.png");
        assertThat(e.getInviteCode()).isEqualTo("ABCD2345");
        assertThat(e.getInviteExpireAt()).isEqualTo(NOW);
        assertThat(e.getStatus()).isEqualTo(1);
        assertThat(e.getCreatedAt()).isEqualTo(NOW);
        assertThat(e.getUpdatedAt()).isEqualTo(NOW);
        assertThat(e.getDeleted()).isZero();
    }

    @Test
    void courseMemberRoundTripsAllColumns() {
        CourseMember e = new CourseMember();
        e.setId(3L);
        e.setCourseId(2L);
        e.setUserId(1L);
        e.setRoleInCourse("STUDENT");
        e.setJoinedAt(NOW);
        e.setCreatedAt(NOW);
        e.setUpdatedAt(NOW);
        e.setDeleted(0);

        assertThat(e.getId()).isEqualTo(3L);
        assertThat(e.getCourseId()).isEqualTo(2L);
        assertThat(e.getUserId()).isEqualTo(1L);
        assertThat(e.getRoleInCourse()).isEqualTo("STUDENT");
        assertThat(e.getJoinedAt()).isEqualTo(NOW);
        assertThat(e.getCreatedAt()).isEqualTo(NOW);
        assertThat(e.getUpdatedAt()).isEqualTo(NOW);
        assertThat(e.getDeleted()).isZero();
    }

    @Test
    void materialRoundTripsAllColumns() {
        Material e = new Material();
        e.setId(4L);
        e.setCourseId(2L);
        e.setName("课件.pdf");
        e.setType("PDF");
        e.setSize(2048L);
        e.setStoragePath("local://material/x");
        e.setChapter("第1章");
        e.setTags("基础,重点");
        e.setUploaderId(1L);
        e.setParseStatus(1);
        e.setDownloadCount(3);
        e.setCreatedAt(NOW);
        e.setUpdatedAt(NOW);
        e.setDeleted(0);

        assertThat(e.getId()).isEqualTo(4L);
        assertThat(e.getCourseId()).isEqualTo(2L);
        assertThat(e.getName()).isEqualTo("课件.pdf");
        assertThat(e.getType()).isEqualTo("PDF");
        assertThat(e.getSize()).isEqualTo(2048L);
        assertThat(e.getStoragePath()).isEqualTo("local://material/x");
        assertThat(e.getChapter()).isEqualTo("第1章");
        assertThat(e.getTags()).isEqualTo("基础,重点");
        assertThat(e.getUploaderId()).isEqualTo(1L);
        assertThat(e.getParseStatus()).isEqualTo(1);
        assertThat(e.getDownloadCount()).isEqualTo(3);
        assertThat(e.getCreatedAt()).isEqualTo(NOW);
        assertThat(e.getUpdatedAt()).isEqualTo(NOW);
        assertThat(e.getDeleted()).isZero();
    }

    @Test
    void materialChunkRoundTripsAllColumns() {
        MaterialChunk e = new MaterialChunk();
        e.setId(5L);
        e.setMaterialId(4L);
        e.setChunkIndex(0);
        e.setContent("片段正文");
        e.setPageNo(3);
        e.setChapter("第1章");
        e.setVectorId("vec-1");
        e.setCreatedAt(NOW);
        e.setUpdatedAt(NOW);
        e.setDeleted(0);

        assertThat(e.getId()).isEqualTo(5L);
        assertThat(e.getMaterialId()).isEqualTo(4L);
        assertThat(e.getChunkIndex()).isZero();
        assertThat(e.getContent()).isEqualTo("片段正文");
        assertThat(e.getPageNo()).isEqualTo(3);
        assertThat(e.getChapter()).isEqualTo("第1章");
        assertThat(e.getVectorId()).isEqualTo("vec-1");
        assertThat(e.getCreatedAt()).isEqualTo(NOW);
        assertThat(e.getUpdatedAt()).isEqualTo(NOW);
        assertThat(e.getDeleted()).isZero();
    }

    @Test
    void questionRoundTripsAllColumns() {
        Question e = new Question();
        e.setId(6L);
        e.setCourseId(2L);
        e.setAuthorId(1L);
        e.setTitle("标题至少五个字");
        e.setContent("正文至少十个字的内容");
        e.setImages("1.png");
        e.setStatus(1);
        e.setIsTop(0);
        e.setIsEssence(1);
        e.setViewCount(9);
        e.setAnswerCount(2);
        e.setAcceptedAnswerId(7L);
        e.setCreatedAt(NOW);
        e.setUpdatedAt(NOW);
        e.setDeleted(0);

        assertThat(e.getId()).isEqualTo(6L);
        assertThat(e.getCourseId()).isEqualTo(2L);
        assertThat(e.getAuthorId()).isEqualTo(1L);
        assertThat(e.getTitle()).isEqualTo("标题至少五个字");
        assertThat(e.getContent()).isEqualTo("正文至少十个字的内容");
        assertThat(e.getImages()).isEqualTo("1.png");
        assertThat(e.getStatus()).isEqualTo(1);
        assertThat(e.getIsTop()).isZero();
        assertThat(e.getIsEssence()).isEqualTo(1);
        assertThat(e.getViewCount()).isEqualTo(9);
        assertThat(e.getAnswerCount()).isEqualTo(2);
        assertThat(e.getAcceptedAnswerId()).isEqualTo(7L);
        assertThat(e.getCreatedAt()).isEqualTo(NOW);
        assertThat(e.getUpdatedAt()).isEqualTo(NOW);
        assertThat(e.getDeleted()).isZero();
    }

    @Test
    void answerRoundTripsAllColumns() {
        Answer e = new Answer();
        e.setId(7L);
        e.setQuestionId(6L);
        e.setAuthorId(1L);
        e.setContent("回复内容");
        e.setImages("2.png");
        e.setIsAccepted(1);
        e.setVoteUp(4);
        e.setVoteDown(1);
        e.setCreatedAt(NOW);
        e.setUpdatedAt(NOW);
        e.setDeleted(0);

        assertThat(e.getId()).isEqualTo(7L);
        assertThat(e.getQuestionId()).isEqualTo(6L);
        assertThat(e.getAuthorId()).isEqualTo(1L);
        assertThat(e.getContent()).isEqualTo("回复内容");
        assertThat(e.getImages()).isEqualTo("2.png");
        assertThat(e.getIsAccepted()).isEqualTo(1);
        assertThat(e.getVoteUp()).isEqualTo(4);
        assertThat(e.getVoteDown()).isEqualTo(1);
        assertThat(e.getCreatedAt()).isEqualTo(NOW);
        assertThat(e.getUpdatedAt()).isEqualTo(NOW);
        assertThat(e.getDeleted()).isZero();
    }
}
