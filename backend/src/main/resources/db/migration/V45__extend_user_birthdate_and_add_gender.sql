ALTER TABLE users
    MODIFY birthdate VARCHAR(7) NULL,
    ADD COLUMN gender VARCHAR(10) NULL AFTER birthdate;

UPDATE users
SET gender = CASE
    WHEN SUBSTRING(birthdate, 7, 1) IN ('1', '3') THEN 'male'
    WHEN SUBSTRING(birthdate, 7, 1) IN ('2', '4') THEN 'female'
    ELSE gender
END
WHERE gender IS NULL;
