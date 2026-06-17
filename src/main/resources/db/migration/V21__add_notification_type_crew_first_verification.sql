-- notifications.type CHECK 제약에 CREW_FIRST_VERIFICATION 추가
-- CHECK는 부분 수정 불가 → 기존 제약 DROP 후 신값 포함하여 재생성 (V13 선례)
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;

ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN (
        'VERIFICATION_APPROVED', 'VERIFICATION_REJECTED',
        'CHALLENGE_SUCCESS', 'CHALLENGE_FAILED',
        'CREW_INVITE',
        'REPORT_RECEIVED', 'REVIEW_COMPLETED',
        'UPLOAD_COMPLETED',
        'CREW_STARTED', 'REMINDER',
        'CREW_FIRST_VERIFICATION'
    ));
