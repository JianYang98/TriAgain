-- notifications 타입 체크 제약조건에 CREW_STARTED, REMINDER 추가
ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN (
        'VERIFICATION_APPROVED', 'VERIFICATION_REJECTED',
        'CHALLENGE_SUCCESS', 'CHALLENGE_FAILED',
        'CREW_INVITE',
        'REPORT_RECEIVED', 'REVIEW_COMPLETED',
        'UPLOAD_COMPLETED',
        'CREW_STARTED', 'REMINDER'
    ));
