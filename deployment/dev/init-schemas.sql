-- Sémák létrehozása a 3 service számára
CREATE SCHEMA IF NOT EXISTS customer_svc;
CREATE SCHEMA IF NOT EXISTS account_svc;
CREATE SCHEMA IF NOT EXISTS tx_svc;

-- Jogosultságok (dev: demo user, container: bankadmin user)
DO $$
BEGIN
    -- Dev mód
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'demo') THEN
        GRANT ALL ON SCHEMA customer_svc TO demo;
        GRANT ALL ON SCHEMA account_svc TO demo;
        GRANT ALL ON SCHEMA tx_svc TO demo;
    END IF;
    -- Container mód
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'bankadmin') THEN
        GRANT ALL ON SCHEMA customer_svc TO bankadmin;
        GRANT ALL ON SCHEMA account_svc TO bankadmin;
        GRANT ALL ON SCHEMA tx_svc TO bankadmin;
    END IF;
END
$$;
