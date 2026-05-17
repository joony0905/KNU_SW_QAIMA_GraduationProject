ALTER TABLE short_selling
    MODIFY short_volume_total DECIMAL(24,6) NULL,
    MODIFY short_volume_uptick_applied DECIMAL(24,6) NULL,
    MODIFY short_volume_uptick_exempt DECIMAL(24,6) NULL,
    MODIFY total_volume DECIMAL(24,6) NULL;
