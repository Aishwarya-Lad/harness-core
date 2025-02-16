-- Copyright 2020 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- CONFIGURATION TABLE START -----------
BEGIN;
DROP TABLE IF EXISTS CV_CONFIGURATIONS;

CREATE TABLE IF NOT EXISTS CV_CONFIGURATIONS (
    ACCOUNT_ID TEXT NOT NULL,
    VERIFICATION_PROVIDER_TYPE TEXT NOT NULL,
    NUM_OF_CONFIGS INTEGER NOT NULL,
    NUM_OF_ALERTS INTEGER NOT NULL,
	CREATION_TIME TIMESTAMP NOT NULL,
	PRIMARY KEY (ACCOUNT_ID, VERIFICATION_PROVIDER_TYPE, CREATION_TIME)
);
COMMIT;


SELECT CREATE_HYPERTABLE('CV_CONFIGURATIONS','creation_time');
BEGIN;
CREATE INDEX IF NOT EXISTS CV_CONFIGURATIONS_ACCOUNTID_INDEX ON CV_CONFIGURATIONS(ACCOUNT_ID,CREATION_TIME DESC);
CREATE INDEX IF NOT EXISTS CV_CONFIGURATIONS_VERIFICATION_PROVIDER_TYPE_INDEX ON CV_CONFIGURATIONS(VERIFICATION_PROVIDER_TYPE,CREATION_TIME DESC);
COMMIT;
