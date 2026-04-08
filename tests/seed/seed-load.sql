-- Seed data: 5 customers, 7 accounts, balance history, 3 transactions
-- Idempotent: uses ON CONFLICT DO NOTHING

-- Customers
INSERT INTO customer_svc.customer (id, tax_id, first_name, last_name, email, phone, status, created_at)
VALUES
  ('a1b2c3d4-1111-4000-8000-000000000001', '8471926350', 'Anna', 'Kovács', 'kovacs.anna@example.hu', '+36301234567', 'ACTIVE', NOW()),
  ('a1b2c3d4-1111-4000-8000-000000000002', '3928174650', 'Péter', 'Szabó', 'szabo.peter@example.hu', '+36209876543', 'ACTIVE', NOW()),
  ('a1b2c3d4-1111-4000-8000-000000000003', '6150483927', 'Eszter', 'Tóth', 'toth.eszter@example.hu', '+36701112233', 'ACTIVE', NOW()),
  ('a1b2c3d4-1111-4000-8000-000000000004', '2749381056', 'Gábor', 'Nagy', 'nagy.gabor@example.hu', '+36304445566', 'ACTIVE', NOW()),
  ('a1b2c3d4-1111-4000-8000-000000000005', '5083617294', 'Katalin', 'Horváth', 'horvath.katalin@example.hu', '+36207778899', 'ACTIVE', NOW())
ON CONFLICT (id) DO NOTHING;

-- Accounts
INSERT INTO account_svc.account (id, account_number, customer_id, account_type, balance, currency, status, created_at)
VALUES
  ('b1b2c3d4-2222-4000-8000-000000000001', 'HU42117730161110180000000001', 'a1b2c3d4-1111-4000-8000-000000000001', 'CHECKING', 1500000.00, 'HUF', 'ACTIVE', NOW()),
  ('b1b2c3d4-2222-4000-8000-000000000002', 'HU42117730161110180000000006', 'a1b2c3d4-1111-4000-8000-000000000001', 'SAVINGS',    2500.00, 'EUR', 'ACTIVE', NOW()),
  ('b1b2c3d4-2222-4000-8000-000000000003', 'HU42117730161110180000000002', 'a1b2c3d4-1111-4000-8000-000000000002', 'CHECKING',  850000.00, 'HUF', 'ACTIVE', NOW()),
  ('b1b2c3d4-2222-4000-8000-000000000004', 'HU42117730161110180000000003', 'a1b2c3d4-1111-4000-8000-000000000003', 'CHECKING',    3200.00, 'EUR', 'ACTIVE', NOW()),
  ('b1b2c3d4-2222-4000-8000-000000000005', 'HU42117730161110180000000004', 'a1b2c3d4-1111-4000-8000-000000000004', 'CHECKING', 2300000.00, 'HUF', 'ACTIVE', NOW()),
  ('b1b2c3d4-2222-4000-8000-000000000006', 'HU42117730161110180000000007', 'a1b2c3d4-1111-4000-8000-000000000004', 'SAVINGS',    5000.00, 'USD', 'ACTIVE', NOW()),
  ('b1b2c3d4-2222-4000-8000-000000000007', 'HU42117730161110180000000005', 'a1b2c3d4-1111-4000-8000-000000000005', 'SAVINGS',  750000.00, 'HUF', 'ACTIVE', NOW())
ON CONFLICT (id) DO NOTHING;

-- Balance history: opening deposits + transfer movements
-- Opening deposits (account opening balance for each account)
INSERT INTO account_svc.balance_history (account_id, old_balance, new_balance, change_amount, reason, created_at)
VALUES
  ('b1b2c3d4-2222-4000-8000-000000000001', 0.00, 1525000.00, 1525000.00, 'Számla nyitás', NOW() - INTERVAL '30 days'),
  ('b1b2c3d4-2222-4000-8000-000000000002', 0.00,    2400.00,    2400.00, 'Számla nyitás', NOW() - INTERVAL '30 days'),
  ('b1b2c3d4-2222-4000-8000-000000000003', 0.00,  825000.00,  825000.00, 'Számla nyitás', NOW() - INTERVAL '30 days'),
  ('b1b2c3d4-2222-4000-8000-000000000004', 0.00,    3300.00,    3300.00, 'Számla nyitás', NOW() - INTERVAL '30 days'),
  ('b1b2c3d4-2222-4000-8000-000000000005', 0.00, 2350000.00, 2350000.00, 'Számla nyitás', NOW() - INTERVAL '30 days'),
  ('b1b2c3d4-2222-4000-8000-000000000006', 0.00,    5000.00,    5000.00, 'Számla nyitás', NOW() - INTERVAL '30 days'),
  ('b1b2c3d4-2222-4000-8000-000000000007', 0.00,  700000.00,  700000.00, 'Számla nyitás', NOW() - INTERVAL '30 days');

-- Transfer debit/credit entries (matching the transactions below)
-- tx-001: Anna (001) → Péter (003), 25,000 HUF
INSERT INTO account_svc.balance_history (account_id, old_balance, new_balance, change_amount, reason, created_at)
VALUES
  ('b1b2c3d4-2222-4000-8000-000000000001', 1525000.00, 1500000.00, -25000.00, 'Transfer d1d2d3d4-4444-4000-8000-000000000001', NOW() - INTERVAL '7 days'),
  ('b1b2c3d4-2222-4000-8000-000000000003',  825000.00,  850000.00,  25000.00, 'Transfer d1d2d3d4-4444-4000-8000-000000000001', NOW() - INTERVAL '7 days');

-- tx-002: Eszter (004) → Anna (002), 100 EUR
INSERT INTO account_svc.balance_history (account_id, old_balance, new_balance, change_amount, reason, created_at)
VALUES
  ('b1b2c3d4-2222-4000-8000-000000000004', 3300.00, 3200.00, -100.00, 'Transfer d1d2d3d4-4444-4000-8000-000000000002', NOW() - INTERVAL '5 days'),
  ('b1b2c3d4-2222-4000-8000-000000000002', 2400.00, 2500.00,  100.00, 'Transfer d1d2d3d4-4444-4000-8000-000000000002', NOW() - INTERVAL '5 days');

-- tx-003: Gábor (005) → Katalin (007), 50,000 HUF
INSERT INTO account_svc.balance_history (account_id, old_balance, new_balance, change_amount, reason, created_at)
VALUES
  ('b1b2c3d4-2222-4000-8000-000000000005', 2350000.00, 2300000.00, -50000.00, 'Transfer d1d2d3d4-4444-4000-8000-000000000003', NOW() - INTERVAL '3 days'),
  ('b1b2c3d4-2222-4000-8000-000000000007',  700000.00,  750000.00,  50000.00, 'Transfer d1d2d3d4-4444-4000-8000-000000000003', NOW() - INTERVAL '3 days');

-- Transactions
INSERT INTO tx_svc.transaction (id, transaction_ref, from_account_id, to_account_id, amount, currency, status, created_at, completed_at)
VALUES
  ('c1b2c3d4-3333-4000-8000-000000000001', 'd1d2d3d4-4444-4000-8000-000000000001', 'b1b2c3d4-2222-4000-8000-000000000001', 'b1b2c3d4-2222-4000-8000-000000000003', 25000.00, 'HUF', 'COMPLETED', NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days'),
  ('c1b2c3d4-3333-4000-8000-000000000002', 'd1d2d3d4-4444-4000-8000-000000000002', 'b1b2c3d4-2222-4000-8000-000000000004', 'b1b2c3d4-2222-4000-8000-000000000002',   100.00, 'EUR', 'COMPLETED', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'),
  ('c1b2c3d4-3333-4000-8000-000000000003', 'd1d2d3d4-4444-4000-8000-000000000003', 'b1b2c3d4-2222-4000-8000-000000000005', 'b1b2c3d4-2222-4000-8000-000000000007', 50000.00, 'HUF', 'COMPLETED', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days')
ON CONFLICT (id) DO NOTHING;
