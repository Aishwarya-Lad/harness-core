-- Copyright 2021 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

BEGIN;
ALTER TABLE pipeline_execution_summary_ci ADD COLUMN IF NOT EXISTS ciLicenseType text NULL;
ALTER TABLE pipeline_execution_summary_ci ADD COLUMN IF NOT EXISTS ciEditionType text NULL;
COMMIT;
