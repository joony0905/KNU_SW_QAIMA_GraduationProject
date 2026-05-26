ALTER TABLE users
    ADD COLUMN investment_level VARCHAR(20) NOT NULL DEFAULT 'BEGINNER' AFTER glossary_hover;

UPDATE users
SET investment_level = CASE experience
    WHEN '초급자' THEN 'BEGINNER'
    WHEN '중급자' THEN 'INTERMEDIATE'
    WHEN '고급자' THEN 'ADVANCED'
    WHEN '전문가' THEN 'EXPERT'
    ELSE investment_level
END;
