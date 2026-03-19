-- 크루 검색 기능: category, visibility 컬럼 추가
ALTER TABLE crews ADD COLUMN category VARCHAR(20) NULL;
ALTER TABLE crews ADD COLUMN visibility VARCHAR(10) NOT NULL DEFAULT 'PRIVATE';

-- 크루 검색용 부분 인덱스
CREATE INDEX idx_crew_search ON crews(visibility, status, created_at DESC) WHERE visibility = 'PUBLIC';
