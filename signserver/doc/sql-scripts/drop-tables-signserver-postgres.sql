-- Dropping tables for SignServer 3.5.x on Postgres
-- Version: $Id: drop-tables-signserver-postgres.sql 8929 2017-12-20 15:42:29Z vinays $


--
-- Drop table `AuditRecordData`
--
DROP TABLE IF EXISTS auditrecorddata;

--
-- Drop table `GlobalConfigData`
--
DROP TABLE IF EXISTS globalconfigdata;

--
-- Drop table `signerconfigdata`
--
DROP TABLE IF EXISTS signerconfigdata;

--
-- Drop table `KeyUsageCounter`
--
DROP TABLE IF EXISTS keyusagecounter;


--
-- Drop table `ArchiveData`
--
DROP TABLE IF EXISTS archivedata;

--
-- Drop table `KeyData`
--
DROP TABLE IF EXISTS KeyData;


--
-- Drop table `SEQUENCE`
--
DROP SEQUENCE IF EXISTS hibernate_sequence;


-- End
