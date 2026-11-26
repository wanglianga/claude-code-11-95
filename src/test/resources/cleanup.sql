-- 每个测试方法前清理业务数据，保留参考数据（海区/物种/季节/渔具/港口/船只）
TRUNCATE voyage_events, quota_ledger, weigh_tickets, unload_records, notifications,
         appointments, disputes, case_notes, regulatory_receipts, voyages,
         permit_items, permits, permit_application_items, permit_applications
         RESTART IDENTITY CASCADE;
UPDATE quota_accounts SET reserved_kg = 0, used_kg = 0;
