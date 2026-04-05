# Seed Data

Deterministic test data with fixed UUIDs for reproducible environments.

## Contents

- `seed-data.json` — 5 customers, 7 accounts, 3 transactions with fixed UUIDs

## UUID Convention

| Entity | UUID prefix | Example |
|---|---|---|
| Customer | `a1b2c3d4-1111-4000-8000-` | `a1b2c3d4-1111-4000-8000-000000000001` |
| Account | `b1b2c3d4-2222-4000-8000-` | `b1b2c3d4-2222-4000-8000-000000000001` |
| Transaction | `c1b2c3d4-3333-4000-8000-` | `c1b2c3d4-3333-4000-8000-000000000001` |

## Usage

```bash
# Load seed data (idempotent — skips existing records)
./bank-demo-ctl seed local-manual
./bank-demo-ctl seed local-docker
./bank-demo-ctl seed redhat-sandbox

# Remove seed data
./bank-demo-ctl seed local-manual --clean
```

## Test Data Overview

| Customer | Accounts | Currencies |
|---|---|---|
| Anna Kovacs | HUF checking (1.5M), EUR savings (2,500) | HUF, EUR |
| Peter Szabo | HUF checking (850K) | HUF |
| Eszter Toth | EUR checking (3,200) | EUR |
| Gabor Nagy | HUF checking (2.3M), USD savings (5,000) | HUF, USD |
| Katalin Horvath | HUF savings (750K) | HUF |

Transactions: Anna->Peter 25K HUF, Eszter->Anna 100 EUR, Gabor->Katalin 50K HUF
