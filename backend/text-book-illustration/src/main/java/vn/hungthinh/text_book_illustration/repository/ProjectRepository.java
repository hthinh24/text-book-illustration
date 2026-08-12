package vn.hungthinh.text_book_illustration.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import vn.hungthinh.text_book_illustration.entity.*;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByUser(User user);

    // ------------------------------------------------------------------ //
    //  Claim queries                                                        //
    // ------------------------------------------------------------------ //

    /**
     * Claim the STYLE step. No prev-step check — STYLE is always the first step.
     * Succeeds only when step=STYLE AND step_status=PENDING.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Project p
            SET p.stepStatus = :running,
                p.startedAt  = :now
            WHERE p.id = :id
              AND p.step       = :style
              AND p.stepStatus = :pending
            """)
    int claimStyleStep(
            @Param("id") UUID id,
            @Param("now") Instant now,
            @Param("running") StepStatus running,
            @Param("pending") StepStatus pending,
            @Param("style") Step style
    );

    /**
     * Generic claim for CHARACTER / PORTRAIT / CHAPTER / ILLUSTRATION.
     * Succeeds when:
     *   - advancing from previous step  (prev step SUCCESS), OR
     *   - re-claiming after a retry     (this step PENDING)
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Project p
            SET p.stepStatus = :running,
                p.step       = :thisStep,
                p.startedAt  = :now
            WHERE p.id = :id
              AND (
                    (p.step = :prevStep
                     AND p.stepStatus = :success)
                 OR (p.step = :thisStep
                     AND p.stepStatus = :pending)
              )
            """)
    int claimStep(
            @Param("id") UUID id,
            @Param("prevStep") Step prevStep,
            @Param("thisStep") Step thisStep,
            @Param("now") Instant now,
            @Param("running") StepStatus running,
            @Param("success") StepStatus success,
            @Param("pending") StepStatus pending
    );

    // ------------------------------------------------------------------ //
    //  Finalize queries                                                     //
    // ------------------------------------------------------------------ //

    /**
     * Finalize a step as SUCCESS.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Project p
            SET p.stepStatus            = :success,
                p.previousInteractionId = :interactionId
            WHERE p.id = :id
              AND p.step       = :step
              AND p.stepStatus = :running
            """)
    int finalizeStepSuccess(
            @Param("id") UUID id,
            @Param("step") Step step,
            @Param("interactionId") String interactionId,
            @Param("success") StepStatus success,
            @Param("running") StepStatus running
    );

    @Modifying
    @Transactional
    @Query("""
            UPDATE Project p
            SET p.status = :success
            """)
    void completeProject(
            @Param("id") UUID id,
            @Param("success") ProjectStatus success
    );

    /**
     * Finalize a step as FAIL.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Project p
            SET p.stepStatus   = :fail,
                p.errorMessage = :errorMessage
            WHERE p.id = :id
              AND p.step       = :step
              AND p.stepStatus = :running
            """)
    int finalizeStepFail(
            @Param("id") UUID id,
            @Param("step") Step step,
            @Param("errorMessage") String errorMessage,
            @Param("fail") StepStatus fail,
            @Param("running") StepStatus running
    );

    // ------------------------------------------------------------------ //
    //  Retry queries                                                        //
    // ------------------------------------------------------------------ //

    /**
     * Retry after FAIL — reset step_status to PENDING and increment retry_count.
     * Enforces max retry count guard: only succeeds if retry_count <= maxRetryCount.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Project p
            SET p.stepStatus   = :pending,
                p.retryCount   = p.retryCount + 1,
                p.errorMessage = null
            WHERE p.id = :id
              AND p.step       = :step
              AND p.stepStatus = :fail
              AND p.retryCount <= :maxRetryCount
            """)
    int retryFailedStep(
            @Param("id") UUID id,
            @Param("step") Step step,
            @Param("maxRetryCount") int maxRetryCount,
            @Param("pending") StepStatus pending,
            @Param("fail") StepStatus fail
    );

    /**
     * Recover a stuck-RUNNING step — reset to PENDING only if started_at is
     * older than the provided timeout instant.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Project p
            SET p.stepStatus   = :pending,
                p.retryCount   = p.retryCount + 1,
                p.errorMessage = null
            WHERE p.id = :id
              AND p.step       = :step
              AND p.stepStatus = :running
              AND p.startedAt  < :timeoutBefore
            """)
    int recoverStuckStep(
            @Param("id") UUID id,
            @Param("step") Step step,
            @Param("timeoutBefore") Instant timeoutBefore,
            @Param("pending") StepStatus pending,
            @Param("running") StepStatus running
    );
}
