package com.zhikeban.bishe.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikeban.bishe.domain.entity.ReviewTask;
import com.zhikeban.bishe.domain.entity.WrongBook;
import com.zhikeban.bishe.domain.entity.WrongKp;
import com.zhikeban.bishe.mapper.ReviewTaskMapper;
import com.zhikeban.bishe.mapper.WrongBookMapper;
import com.zhikeban.bishe.mapper.WrongKpMapper;
import com.zhikeban.bishe.web.ApiException;
import com.zhikeban.bishe.web.dto.WrongBookDtos.WrongCommand;
import com.zhikeban.bishe.web.dto.WrongBookDtos.WrongQuery;
import com.zhikeban.bishe.web.dto.WrongBookDtos.WrongUpdateCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 错题本服务（`SRS` `FR-5-01`／`FR-5-03`／`FR-5-06`／`FR-5-14`；`API-36`~`API-39`；`§ENG-014` `W` 组）。
 *
 * <p>★★ **去重取甲**（`§ENG-014` `A-1`）：`QUESTION`／`AI` 来源按
 * `(user_id, course_id, source_type, source_id)` 复用**未删除行**，命中即返回既有 `id`；
 * `MANUAL` 来源**允许同题多条**（详情见 {@link WrongBook} 的类注释）。
 *
 * <p>★★ **新增是四表事务**（详设 §3.5 设计决定 1）：`wrong_book`（`TB-15`）→ `wrong_kp`（`TB-16`）
 * → 六条 `review_task`（`TB-17`）→ 知识点掌握度回写（聚合计算，不落表）。三者要么全成、要么全不成，
 * 否则会出现"有错题无复习计划"或"有计划无错题"。
 *
 * <p>★ **权限**（`§ENG-014` `P` 组）：每个按 `id` 的操作都**同时按 `user_id` 过滤**
 * （`update`／`archive`），列表只返回本人；新增前必须通过 {@link CourseService#isMember}。
 */
@Service
public class WrongBookService {

    /** 支持的来源类型（`schema.sql` 的注释取值：QUESTION、AI、MANUAL）。 */
    private static final Set<String> SOURCE_TYPES = Set.of("QUESTION", "AI", "MANUAL");

    /** 单题最多关联的知识点数（`SRS` `FR-5-03`：1~3 个）。 */
    private static final int MAX_KP = 3;

    private final WrongBookMapper wrongBookMapper;
    private final WrongKpMapper wrongKpMapper;
    private final ReviewTaskMapper reviewTaskMapper;
    private final CourseService courseService;
    private final ReviewNodePolicy reviewNodePolicy;
    private final Clock clock;

    private final MasteryCalculator masteryCalculator = new MasteryCalculator();

    public WrongBookService(WrongBookMapper wrongBookMapper, WrongKpMapper wrongKpMapper,
                            ReviewTaskMapper reviewTaskMapper, CourseService courseService,
                            ReviewNodePolicy reviewNodePolicy, Clock clock) {
        this.wrongBookMapper = wrongBookMapper;
        this.wrongKpMapper = wrongKpMapper;
        this.reviewTaskMapper = reviewTaskMapper;
        this.courseService = courseService;
        this.reviewNodePolicy = reviewNodePolicy;
        this.clock = clock;
    }

    /* ───────────────────────── 新增（API-36）───────────────────────── */

    /**
     * 新增错题（三种来源：问答 / AI 解析 / 手动录入），并生成六条复习任务。
     *
     * <p>★ 去重口径：`QUESTION`／`AI` 且 `sourceId` 非空时，先按四元组查未删除行，命中即**复用**；
     * `MANUAL` 放行（同一个学生可以手动录同题多条）。
     *
     * @param userId 当前登录学生（由 `@AuthenticationPrincipal` 注入，**不信任请求体**）
     * @param cmd    入参
     * @return 错题标识（既有或新建）
     */
    @Transactional
    public Long add(Long userId, WrongCommand cmd) {
        if (cmd == null) {
            throw ApiException.badRequest("错题内容不能为空");
        }
        if (cmd.courseId() == null) {
            throw ApiException.badRequest("courseId 不能为空");
        }
        if (cmd.questionContent() == null || cmd.questionContent().isBlank()) {
            throw ApiException.badRequest("题目内容不能为空");
        }
        if (cmd.kpIds() == null || cmd.kpIds().isEmpty()) {
            throw ApiException.badRequest("至少关联 1 个知识点");
        }
        if (cmd.kpIds().size() > MAX_KP) {
            throw ApiException.badRequest("单题最多关联 " + MAX_KP + " 个知识点");
        }
        if (!courseService.isMember(cmd.courseId(), userId)) {
            throw ApiException.forbidden("只有课程成员才能把题目加入错题本：courseId=" + cmd.courseId());
        }

        String sourceType = cmd.sourceType() == null || cmd.sourceType().isBlank()
                ? "MANUAL" : cmd.sourceType().trim().toUpperCase();
        if (!SOURCE_TYPES.contains(sourceType)) {
            throw ApiException.badRequest("sourceType 只能是 QUESTION／AI／MANUAL：" + cmd.sourceType());
        }
        if (!"MANUAL".equals(sourceType) && cmd.sourceId() == null) {
            throw ApiException.badRequest(sourceType + " 来源必须给 sourceId");
        }

        if (!"MANUAL".equals(sourceType) && cmd.sourceId() != null) {
            WrongBook existing = wrongBookMapper.selectOne(Wrappers.<WrongBook>lambdaQuery()
                    .eq(WrongBook::getUserId, userId)
                    .eq(WrongBook::getCourseId, cmd.courseId())
                    .eq(WrongBook::getSourceType, sourceType)
                    .eq(WrongBook::getSourceId, cmd.sourceId()));
            if (existing != null) {
                return existing.getId();
            }
        }

        String mark = cmd.masteryMark() == null ? "未掌握" : cmd.masteryMark().trim();
        BigDecimal initialScore;
        if ("已掌握".equals(mark) || "MASTERED".equalsIgnoreCase(mark)) {
            initialScore = new BigDecimal("80");
        } else if ("模糊".equals(mark) || "FUZZY".equalsIgnoreCase(mark)) {
            initialScore = new BigDecimal("50");
        } else {
            initialScore = new BigDecimal("0");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        WrongBook book = new WrongBook();
        book.setUserId(userId);
        book.setCourseId(cmd.courseId());
        book.setSourceType(sourceType);
        book.setSourceId(cmd.sourceId());
        book.setQuestionContent(cmd.questionContent().trim());
        book.setMyAnswer(cmd.myAnswer());
        book.setRightAnswer(cmd.rightAnswer());
        book.setErrorReason(cmd.errorReason());
        book.setMasteryScore(initialScore);
        book.setMasteryLevel(masteryCalculator.level(initialScore));
        book.setStatus(0);
        book.setCreatedAt(now);
        book.setUpdatedAt(now);
        book.setDeleted(0);
        wrongBookMapper.insert(book);

        for (Long kpId : cmd.kpIds()) {
            if (kpId == null) {
                continue;
            }
            WrongKp link = new WrongKp();
            link.setWrongId(book.getId());
            link.setKpId(kpId);
            link.setCreatedAt(now);
            wrongKpMapper.insert(link);
        }

        List<ReviewTask> tasks = reviewNodePolicy.plan(LocalDate.now(clock), book.getId(), userId,
                ReviewNodePolicy.DEFAULT_NODES);
        for (ReviewTask task : tasks) {
            reviewTaskMapper.insert(task);
        }
        return book.getId();
    }

    /* ───────────────────────── 列表（API-37）───────────────────────── */

    /**
     * 错题列表（`FR-5-06`）：默认**掌握度升序 + 创建时间降序**（薄弱项优先）。
     *
     * <p>★ 只返回**本人**的行（`P5`）：查询必带 `user_id`。
     *
     * @param userId 当前登录学生
     * @param q      筛选条件（可为 null）
     * @return 错题列表
     */
    public List<WrongBook> list(Long userId, WrongQuery q) {
        WrongQuery query = q == null ? new WrongQuery(null, null, null) : q;
        String keyword = query.keyword() == null ? null : query.keyword().trim();
        return wrongBookMapper.selectList(Wrappers.<WrongBook>lambdaQuery()
                .eq(WrongBook::getUserId, userId)
                .eq(query.courseId() != null, WrongBook::getCourseId, query.courseId())
                .eq(query.status() != null, WrongBook::getStatus, query.status())
                .like(keyword != null && !keyword.isEmpty(), WrongBook::getQuestionContent, keyword)
                .orderByAsc(WrongBook::getMasteryScore)
                .orderByDesc(WrongBook::getCreatedAt));
    }

    /* ───────────────────────── 更新（API-38）───────────────────────── */

    /**
     * 更新错题（错误原因 / 手动掌握标记 / 知识点），**只允许本人**。
     *
     * <p>★ `P2`：查询同时带 `id` 与 `user_id` ⇒ 拿别人的错题 `id` 也只会得到 404（不泄露存在性）。
     *
     * @param wrongId 错题标识
     * @param userId  当前登录学生
     * @param cmd     入参（null 字段表示不改）
     */
    @Transactional
    public void update(Long wrongId, Long userId, WrongUpdateCommand cmd) {
        WrongBook book = wrongBookMapper.selectOne(Wrappers.<WrongBook>lambdaQuery()
                .eq(WrongBook::getId, wrongId)
                .eq(WrongBook::getUserId, userId));
        if (book == null) {
            throw ApiException.notFound("错题不存在或不属于当前用户：" + wrongId);
        }
        if (cmd == null) {
            return;
        }
        if (cmd.errorReason() != null) {
            book.setErrorReason(cmd.errorReason());
        }
        if (cmd.masteryMark() != null) {
            String mark = cmd.masteryMark().trim();
            BigDecimal score;
            if ("已掌握".equals(mark) || "MASTERED".equalsIgnoreCase(mark)) {
                score = new BigDecimal("80");
            } else if ("模糊".equals(mark) || "FUZZY".equalsIgnoreCase(mark)) {
                score = new BigDecimal("50");
            } else {
                score = new BigDecimal("0");
            }
            book.setMasteryScore(score);
            book.setMasteryLevel(masteryCalculator.level(score));
        }
        if (cmd.kpIds() != null) {
            if (cmd.kpIds().isEmpty() || cmd.kpIds().size() > MAX_KP) {
                throw ApiException.badRequest("知识点个数必须是 1~" + MAX_KP + " 个");
            }
            wrongKpMapper.delete(Wrappers.<WrongKp>lambdaQuery()
                    .eq(WrongKp::getWrongId, wrongId));
            for (Long kpId : cmd.kpIds()) {
                if (kpId == null) {
                    continue;
                }
                WrongKp link = new WrongKp();
                link.setWrongId(wrongId);
                link.setKpId(kpId);
                wrongKpMapper.insert(link);
            }
        }
        book.setUpdatedAt(LocalDateTime.now(clock));
        wrongBookMapper.updateById(book);
    }

    /* ───────────────────────── 归档（API-39）───────────────────────── */

    /**
     * 归档错题（`FR-5-14`）：错题置**归档态**（`status = 2`），并把该题**未完成**的复习任务全部关闭
     * （`status = 2`）—— 保证"今日复习"页不再出现已归档题（详设 §3.5 设计决定 4）。
     *
     * <p>★ `P2`：查询同时带 `id` 与 `user_id`；`W4`：两处落点（错题 + 未完成任务）都在本方法内。
     *
     * @param wrongId 错题标识
     * @param userId  当前登录学生
     */
    @Transactional
    public void archive(Long wrongId, Long userId) {
        WrongBook book = wrongBookMapper.selectOne(Wrappers.<WrongBook>lambdaQuery()
                .eq(WrongBook::getId, wrongId)
                .eq(WrongBook::getUserId, userId));
        if (book == null) {
            throw ApiException.notFound("错题不存在或不属于当前用户：" + wrongId);
        }
        book.setStatus(2);
        book.setUpdatedAt(LocalDateTime.now(clock));
        wrongBookMapper.updateById(book);

        reviewTaskMapper.update(null, Wrappers.<ReviewTask>lambdaUpdate()
                .eq(ReviewTask::getWrongId, wrongId)
                .eq(ReviewTask::getStatus, 0)
                .set(ReviewTask::getStatus, 2));
    }
}
